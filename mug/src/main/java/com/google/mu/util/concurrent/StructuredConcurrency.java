/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.util.concurrent;

import static com.google.mu.util.concurrent.Parallelizer.virtualThreadParallelizer;
import static java.util.Objects.requireNonNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Convenient utilities to help with structured concurrency on top of an {@link ExecutorService}
 * (preferably with virtual threads). For example: <pre>{@code
 * usingVirtualThreads().concurrently(
 *     () -> ListFoos(),
 *     () -> listBars(),
 *     (foos, bars) -> ...);
 * }</pre>
 *
 * <p>If you need to customize the virtual threads or the executor, you can use any custom
 * ExecutorService like: <pre>{@code
 * ExecutorService executor = ...;
 * StructuredConcurrency.using(executor)
 *     .concurrently(...);
 * }</pre>
 *
 * <p>If using a virtual thread executor, it's safe to define StructuredConcurrency as
 * a static final constant because it's stateless as long as the executor is stateless.
 *
 * @since 8.0
 */
public final class StructuredConcurrency {
  private static final int MAX_CONCURRENCY = 100;  // sufficient for all overloads
  private final Parallelizer parallelizer;

  private StructuredConcurrency(Parallelizer parallelizer) {
    this.parallelizer = parallelizer;
  }

  /**
   * Returns an instance using the default virtual thread executor to run the concurrent operations.
   *
   * <p>Fails if runtime is lower than Java 21.
   */
  public static StructuredConcurrency usingVirtualThreads() {
    return new StructuredConcurrency(virtualThreadParallelizer(MAX_CONCURRENCY));
  }

  /**
   * Returns an instance using {@code executor} to run concurrent operations.
   * Note that if {@code executor} doesn't use virtual threads, it can cause throughput issues
   * by blocking in one of the platform threads.
   *
   * <p>Useful if your project needs specific setting (such as thread name) for the threads .
   * It's strongly recommended to use {@code Thread.ofVitual()} for the thread factory
   * because otherwise you risk blocking the platform threads.
   */
  public static StructuredConcurrency using(ExecutorService executor) {
    return new StructuredConcurrency(new Parallelizer(executor, MAX_CONCURRENCY));
  }


  /**
   * Runs {@code a} and {@code b} concurrently in their own virtual threads. After all of the
   * concurrent operations return successfully, invoke the {@code join} function on the results in
   * the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.concurrently(
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (arm, leg) -> new Result(arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, R, X extends Throwable> R concurrently(
      Supplier<A> a, Supplier<B> b, Join2<? super A, ? super B, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    parallelizer.parallelize(Stream.of(toRun(a, r1), toRun(b, r2)));
    return join.join(r1.get(), r2.get());
  }

  /**
   * Runs {@code a}, {@code b} and {@code c} concurrently in their own virtual threads. After all of
   * the concurrent operations return successfully, invoke the {@code join} function on the results
   * in the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.concurrently(
   *   () -> fetchHead(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, arm, leg) -> new Result(head, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, C, R, X extends Throwable> R concurrently(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Join3<? super A, ? super B, ? super C, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    AtomicReference<C> r3 = new AtomicReference<>();
    parallelizer.parallelize(Stream.of(toRun(a, r1), toRun(b, r2), toRun(c, r3)));
    return join.join(r1.get(), r2.get(), r3.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c} and {@code d} concurrently in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function on
   * the results in the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.concurrently(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, shoulder, arm, leg) -> new Result(head, shoulder, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, C, D, R, X extends Throwable> R concurrently(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Join4<? super A, ? super B, ? super C, ? super D, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    AtomicReference<C> r3 = new AtomicReference<>();
    AtomicReference<D> r4 = new AtomicReference<>();
    parallelizer.parallelize(Stream.of(toRun(a, r1), toRun(b, r2), toRun(c, r3), toRun(d, r4)));
    return join.join(r1.get(), r2.get(), r3.get(), r4.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c}, {@code d} and {@code e} concurrently in their own virtual
   * threads. After all of the concurrent operations return successfully, invoke the {@code join}
   * function on the results in the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.concurrently(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   () -> fetchFeet(),
   *   (head, shoulder, arm, leg, feet) -> new Result(head, shoulder, arm, leg, feet));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, C, D, E, R, X extends Throwable> R concurrently(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Supplier<E> e,
      Join5<? super A, ? super B, ? super C, ? super D, ? super E, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    AtomicReference<C> r3 = new AtomicReference<>();
    AtomicReference<D> r4 = new AtomicReference<>();
    AtomicReference<E> r5 = new AtomicReference<>();
    parallelizer
        .parallelize(
            Stream.of(toRun(a, r1), toRun(b, r2), toRun(c, r3), toRun(d, r4), toRun(e, r5)));
    return join.join(r1.get(), r2.get(), r3.get(), r4.get(), r5.get());
  }

  /**
   * Runs {@code a} and {@code b} concurrently and <em>uninterruptibly</em> in their own virtual
   * threads. After all of the concurrent operations return successfully, invoke the {@code join}
   * function on the results in the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.uninterruptibly(
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (arm, leg) -> new Result(arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a, Supplier<B> b, Join2<? super A, ? super B, R, X> join)
      throws X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    parallelizer.parallelizeUninterruptibly(Stream.of(toRun(a, r1), toRun(b, r2)));
    return join.join(r1.get(), r2.get());
  }

  /**
   * Runs {@code a}, {@code b} and {@code c} concurrently and <em>uninterruptibly</em> in their own
   * virtual threads. After all of the concurrent operations return successfully, invoke the {@code
   * join} function on the results in the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, arm, leg) -> new Result(head, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, C, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Join3<? super A, ? super B, ? super C, R, X> join)
      throws X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    AtomicReference<C> r3 = new AtomicReference<>();
    parallelizer.parallelizeUninterruptibly(Stream.of(toRun(a, r1), toRun(b, r2), toRun(c, r3)));
    return join.join(r1.get(), r2.get(), r3.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c} and {@code d} concurrently and <em>uninterruptibly</em> in
   * their own virtual threads. After all of the concurrent operations return successfully, invoke
   * the {@code join} function on the results in the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, shoulder, arm, leg) -> new Result(head, shoulder, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, C, D, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Join4<? super A, ? super B, ? super C, ? super D, R, X> join)
      throws X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    AtomicReference<C> r3 = new AtomicReference<>();
    AtomicReference<D> r4 = new AtomicReference<>();
    parallelizer
        .parallelizeUninterruptibly(
            Stream.of(toRun(a, r1), toRun(b, r2), toRun(c, r3), toRun(d, r4)));
    return join.join(r1.get(), r2.get(), r3.get(), r4.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c}, {@code d} and {@code e} concurrently and
   * <em>uninterruptibly<em> in their own virtual threads. After all of the concurrent operations
   * return successfully, invoke the {@code join} function on the results in the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * StructuredConcurrency fanout = using(executor);
   * Result result = fanout.uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   () -> fetchFeet(),
   *   (head, shoulder, arm, leg, feet) -> new Result(head, shoulder, arm, leg, feet));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller of
   * this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if any
   *     concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public <A, B, C, D, E, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Supplier<E> e,
      Join5<? super A, ? super B, ? super C, ? super D, ? super E, R, X> join)
      throws X {
    requireNonNull(join);
    AtomicReference<A> r1 = new AtomicReference<>();
    AtomicReference<B> r2 = new AtomicReference<>();
    AtomicReference<C> r3 = new AtomicReference<>();
    AtomicReference<D> r4 = new AtomicReference<>();
    AtomicReference<E> r5 = new AtomicReference<>();
    parallelizer
        .parallelizeUninterruptibly(
            Stream.of(toRun(a, r1), toRun(b, r2), toRun(c, r3), toRun(d, r4), toRun(e, r5)));
    return join.join(r1.get(), r2.get(), r3.get(), r4.get(), r5.get());
  }

  /** Function to join two results from concurrent computation. */
  public interface Join2<A, B, R, X extends Throwable> {
    R join(A a, B b) throws X;
  }

  /** Function to join three results from concurrent computation. */
  public interface Join3<A, B, C, R, X extends Throwable> {
    R join(A a, B b, C c) throws X;
  }

  /** Function to join four results from concurrent computation. */
  public interface Join4<A, B, C, D, R, X extends Throwable> {
    R join(A a, B b, C c, D d) throws X;
  }

  /** Function to join five results from concurrent computation. */
  public interface Join5<A, B, C, D, E, R, X extends Throwable> {
    R join(A a, B b, C c, D d, E e) throws X;
  }

  private static <T> Runnable toRun(Supplier<T> supplier, AtomicReference<? super T> result) {
    requireNonNull(supplier);
    return () -> result.set(supplier.get());
  }
}
