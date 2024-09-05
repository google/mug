package com.google.mu.util.concurrent;

import static com.google.mu.util.stream.MoreCollectors.allMax;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Supports structured concurrency for the common case where all concurrent operations are required
 * (as if you are running them sequentially).
 *
 * <p>You can use either {@link #concurrently} or {@link #uninterruptibly} to fan out a few
 * concurrent operations, with a lambda to combine the results after the concurrent operations have
 * completed.
 *
 * <p>Any exception thrown by any of the concurrent operation will cancel all the other pending
 * operations and propagate back to the main thread.
 *
 * <p>If the main thread is interrupted (when you use {@code concurrently()} to allow interruption),
 * pending and currently running operations are canceled and the main thread will throw
 * InterruptedException. For example:
 *
 * <pre>{@code
 * import static com.google.mu.util.concurrent.Fanout.concurrently;
 *
 * return concurrently(
 *     () -> getProjectAncestry(...),
 *     () -> readJobTimeline(),
 *     (ancestry, timeline) -> ...);
 * }</pre>
 *
 * <p>Memory consistency effects: Actions before starting the concurrent operations (including
 * unsynchronized side effects) <i>happen-before</i> the concurrent operations running in the
 * virtual threads, which happen-before the join functions, which then happen-before the {@code
 * concurrently()} or {@code uninterruptibly()} method returns. As a result, thread-safe or
 * concurrent data structure isn't required to pass data between the caller and callee: they will
 * work the same way as if running in a single thread (except obviously there is no happens-before
 * relationship between the concurrent operations themselves).
 *
 * <p>By default, the JDK {@link Executors#newVirtualThreadPerTaskExecutor} is used to run all
 * structured concurrency tasks (thus requires Java 21 and virtual threads).
 * To use an alternative executor (say, you don't want to use virtual threads), implement a {@link
 * StructuredConcurrencyExecutorPlugin} and package it up for {@link ServiceLoader}. You could also
 * use Google <a href="http://github.com/google/auto/tree/main/service">@AutoService</a> to help
 * automate the generation of the META-INF/services files.
 *
 * @since 8.1
 */
public final class Fanout {
  private static final Logger logger = Logger.getLogger(Fanout.class.getName());
  private static final StructuredConcurrencyExecutorPlugin EXECUTOR_PLUGIN = loadExecutorPlugin();

  /**
   * Runs {@code a} and {@code b} concurrently in their own virtual threads. After all of the
   * concurrent operations return successfully, invoke the {@code join} function on the results in
   * the caller's thread.
   *
   * <p>For example:
   *
   * <pre>{@code
   * Result result = concurrently(
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (arm, leg) -> new Result(arm, leg));
   * }</pre>
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, R, X extends Throwable> R concurrently(
      Supplier<A> a, Supplier<B> b, Join2<? super A, ? super B, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    scope.run();
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
   * Result result = concurrently(
   *   () -> fetchHead(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, arm, leg) -> new Result(head, arm, leg));
   * }</pre>
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, R, X extends Throwable> R concurrently(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Join3<? super A, ? super B, ? super C, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    AtomicReference<C> r3 = scope.add(c);
    scope.run();
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
   * Result result = concurrently(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, shoulder, arm, leg) -> new Result(head, shoulder, arm, leg));
   * }</pre>
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, R, X extends Throwable> R concurrently(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Join4<? super A, ? super B, ? super C, ? super D, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    AtomicReference<C> r3 = scope.add(c);
    AtomicReference<D> r4 = scope.add(d);
    scope.run();
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
   * Result result = concurrently(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   () -> fetchFeet(),
   *   (head, shoulder, arm, leg, feet) -> new Result(head, shoulder, arm, leg, feet));
   * }</pre>
   *
   * @throws InterruptedException if the current thread is interrupted while waiting for the
   *     concurrent operations to complete. The unfinished concurrent operations will be canceled.
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, E, R, X extends Throwable> R concurrently(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Supplier<E> e,
      Join5<? super A, ? super B, ? super C, ? super D, ? super E, R, X> join)
      throws InterruptedException, X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    AtomicReference<C> r3 = scope.add(c);
    AtomicReference<D> r4 = scope.add(d);
    AtomicReference<E> r5 = scope.add(e);
    scope.run();
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
   * Result result = uninterruptibly(
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (arm, leg) -> new Result(arm, leg));
   * }</pre>
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a, Supplier<B> b, Join2<? super A, ? super B, R, X> join)
      throws X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    scope.runUninterruptibly();
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
   * Result result = uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, arm, leg) -> new Result(head, arm, leg));
   * }</pre>
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Join3<? super A, ? super B, ? super C, R, X> join)
      throws X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    AtomicReference<C> r3 = scope.add(c);
    scope.runUninterruptibly();
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
   * Result result = uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   (head, shoulder, arm, leg) -> new Result(head, shoulder, arm, leg));
   * }</pre>
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Join4<? super A, ? super B, ? super C, ? super D, R, X> join)
      throws X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    AtomicReference<C> r3 = scope.add(c);
    AtomicReference<D> r4 = scope.add(d);
    scope.runUninterruptibly();
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
   * Result result = uninterruptibly(
   *   () -> fetchHead(),
   *   () -> fetchShoulder(),
   *   () -> fetchArm(),
   *   () -> fetchLeg(),
   *   () -> fetchFeet(),
   *   (head, shoulder, arm, leg, feet) -> new Result(head, shoulder, arm, leg, feet));
   * }</pre>
   *
   * @throws RuntimeException wrapping the original exception from the virtual thread if
   *     any concurrent operation failed
   * @throws X thrown by the {@code join} function
   */
  public static <A, B, C, D, E, R, X extends Throwable> R uninterruptibly(
      Supplier<A> a,
      Supplier<B> b,
      Supplier<C> c,
      Supplier<D> d,
      Supplier<E> e,
      Join5<? super A, ? super B, ? super C, ? super D, ? super E, R, X> join)
      throws X {
    requireNonNull(join);
    Scope scope = new Scope();
    AtomicReference<A> r1 = scope.add(a);
    AtomicReference<B> r2 = scope.add(b);
    AtomicReference<C> r3 = scope.add(c);
    AtomicReference<D> r4 = scope.add(d);
    AtomicReference<E> r5 = scope.add(e);
    scope.runUninterruptibly();
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

  private static final class Scope {
    private static final ExecutorService executor = EXECUTOR_PLUGIN.createExecutor();
    private final Stream.Builder<Runnable> tasks = Stream.builder();

    <T> AtomicReference<T> add(Supplier<T> task) {
      requireNonNull(task);
      AtomicReference<T> result = new AtomicReference<>();
      tasks.add(() -> result.set(task.get()));
      return result;
    }

    void run() throws InterruptedException {
      parallelizer().parallelize(tasks.build());
    }

    void runUninterruptibly() {
      parallelizer().parallelizeUninterruptibly(tasks.build());
    }

    private static Parallelizer parallelizer() {
      int maxConcurrency = 100; // sufficient for all overloads
      return new Parallelizer(executor, maxConcurrency);
    }
  }

  private static StructuredConcurrencyExecutorPlugin loadExecutorPlugin() {
    List<StructuredConcurrencyExecutorPlugin> candidates =
        Utils.stream(ServiceLoader.load(StructuredConcurrencyExecutorPlugin.class))
            .collect(allMax(comparing(plugin -> plugin.priority()), toList()));
    if (candidates.isEmpty()) {
      logger.info("No StructuredConcurrencyExecutorPlugin found. Using default virtual threads.");
      return new StructuredConcurrencyExecutorPlugin() {
        @Override public ExecutorService createExecutor() {
          return Parallelizer.VirtualThread.executor;
        }
      };
    }
    StructuredConcurrencyExecutorPlugin plugin = candidates.get(0);
    Utils.checkState(
        candidates.size() == 1,
        "Only one StructuredConcurrencyExecutorPlugin can be specified (at priority %s); found: %s",
        plugin.priority(),
        candidates);
    logger.info(
        "Structured concurrency using " + plugin + " at priority " + plugin.priority());
    return plugin;
  }

  private Fanout() {}
}