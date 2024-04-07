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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Convenient utilities to help with structured concurrency on top of virtual threads.
 *
 * <p>Requires Java 21+.
 *
 * @since 8.0
 */
public final class StructuredConcurrency {
  /**
   * Runs {@code a} and {@code b} concurrently in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function
   * on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = concurrently(
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (arm, leg) -> new Result(arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, R, X extends Throwable> R concurrently(
      Supplier<A> a, Supplier<B> b, Join2<? super A, ? super B, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    virtualThreadParallelizer(2)
        .parallelize(Stream.of(toRun(a, aResult), toRun(b, bResult)));
    return join.join(aResult.get(), bResult.get());
  }

  /**
   * Runs {@code a}, {@code b} and {@code c} concurrently in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function
   * on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = concurrently(
   *   () -> fetchHead(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, arm, leg) -> new Result(head, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, R, X extends Throwable> R concurrently(
      Supplier<A> a, Supplier<B> b, Supplier<C> c,
      Join3<? super A, ? super B, ? super C, R, X> join) throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    AtomicReference<C> cResult = new AtomicReference<>();
    virtualThreadParallelizer(3)
        .parallelize(Stream.of(toRun(a, aResult), toRun(b, bResult), toRun(c, cResult)));
    return join.join(aResult.get(), bResult.get(), cResult.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c} and {@code d} concurrently in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function
   * on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = concurrently(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, shoulder, arm, leg) -> new Result(head, shoulder, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, R, X extends Throwable> R concurrently(
      Supplier<A> a, Supplier<B> b, Supplier<C> c, Supplier<D> d,
      Join4<? super A, ? super B, ? super C, ? super D, R, X> join) throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    AtomicReference<C> cResult = new AtomicReference<>();
    AtomicReference<D> dResult = new AtomicReference<>();
    virtualThreadParallelizer(4)
        .parallelize(Stream.of(toRun(a, aResult), toRun(b, bResult), toRun(c, cResult)));
    return join.join(aResult.get(), bResult.get(), cResult.get(), dResult.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c}, {@code d} and {@code e} concurrently
   * in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function
   * on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = concurrently(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   () -> fetchFeet(),
   *   (head, shoulder, arm, leg, feet) -> new Result(head, shoulder, arm, leg, feet));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, E, R, X extends Throwable> R concurrently(
      Supplier<A> a, Supplier<B> b, Supplier<C> c, Supplier<D> d, Supplier<E> e,
      Join5<? super A, ? super B, ? super C, ? super D, ? super E, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    AtomicReference<C> cResult = new AtomicReference<>();
    AtomicReference<D> dResult = new AtomicReference<>();
    AtomicReference<E> eResult = new AtomicReference<>();
    virtualThreadParallelizer(5)
        .parallelize(
            Stream.of(toRun(a, aResult), toRun(b, bResult), toRun(c, cResult), toRun(e, eResult)));
    return join.join(aResult.get(), bResult.get(), cResult.get(), dResult.get(), eResult.get());
  }

  /**
   * Runs {@code a} and {@code b} concurrently and <em>uninterruptibly</em>
   * in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function
   * on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = uninterruptibly(
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (arm, leg) -> new Result(arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */

  public static <A, B, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a, Supplier<B> b, Join2<? super A, ? super B, R, X> join)
      throws X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    virtualThreadParallelizer(2)
        .parallelizeUninterruptibly(Stream.of(toRun(a, aResult), toRun(b, bResult)));
    return join.join(aResult.get(), bResult.get());
  }

  /**
   * Runs {@code a}, {@code b} and {@code c} concurrently and <em>uninterruptibly</em>
   * in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function
   * on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, arm, leg) -> new Result(head, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a, Supplier<B> b, Supplier<C> c,
      Join3<? super A, ? super B, ? super C, R, X> join) throws InterruptedException, X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    AtomicReference<C> cResult = new AtomicReference<>();
    virtualThreadParallelizer(3)
        .parallelizeUninterruptibly(
            Stream.of(toRun(a, aResult), toRun(b, bResult), toRun(c, cResult)));
    return join.join(aResult.get(), bResult.get(), cResult.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c} and {@code d} concurrently and <em>uninterruptibly</em>
   * in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join} function
   * on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, shoulder, arm, leg) -> new Result(head, shoulder, arm, leg));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a, Supplier<B> b, Supplier<C> c, Supplier<D> d,
      Join4<? super A, ? super B, ? super C, ? super D, R, X> join) throws X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    AtomicReference<C> cResult = new AtomicReference<>();
    AtomicReference<D> dResult = new AtomicReference<>();
    virtualThreadParallelizer(4)
        .parallelizeUninterruptibly(
            Stream.of(toRun(a, aResult), toRun(b, bResult), toRun(c, cResult)));
    return join.join(aResult.get(), bResult.get(), cResult.get(), dResult.get());
  }

  /**
   * Runs {@code a}, {@code b}, {@code c}, {@code d} and {@code e} concurrently and
   * <em>uninterruptibly<em> in their own virtual threads.
   * After all of the concurrent operations return successfully, invoke the {@code join}
   * function on the results in the caller's thread.
   *
   * <p>For example: <pre>{@code
   * Result result = uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   () -> fetchFeet(),
   *   (head, shoulder, arm, leg, feet) -> new Result(head, shoulder, arm, leg, feet));
   * }</pre>
   *
   * <p>Exceptions thrown by these concurrent suppliers are expected to be propagated through
   * exception tunneling (wrapped in a special unchecked exception) and handled by the caller
   * of this method.
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread
   *     if any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, E, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a, Supplier<B> b, Supplier<C> c, Supplier<D> d, Supplier<E> e,
      Join5<? super A, ? super B, ? super C, ? super D, ? super E, R, X> join)
      throws X {
    requireNonNull(join);
    AtomicReference<A> aResult = new AtomicReference<>();
    AtomicReference<B> bResult = new AtomicReference<>();
    AtomicReference<C> cResult = new AtomicReference<>();
    AtomicReference<D> dResult = new AtomicReference<>();
    AtomicReference<E> eResult = new AtomicReference<>();
    virtualThreadParallelizer(5)
        .parallelizeUninterruptibly(
            Stream.of(toRun(a, aResult), toRun(b, bResult), toRun(c, cResult), toRun(e, eResult)));
    return join.join(aResult.get(), bResult.get(), cResult.get(), dResult.get(), eResult.get());
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

  private StructuredConcurrency() {}
}
