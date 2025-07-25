package com.google.mu.util.concurrent24;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Gatherer;
import java.util.stream.Gatherer.Downstream;
import java.util.stream.Gatherer.Integrator;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import com.google.mu.util.Both;
import com.google.mu.util.stream.BiStream;

/**
 * A fixed concurrency limit for structured concurrent IO-intensive operations.
 *
 * <p>It enables the parallel transformation of input elements, guaranteeing that all concurrent
 * operations either complete and their results are gathered, or are fully cancelled and joined
 * upon interruption or exception.
 *
 * @since 9.2
 */
public final class BoundedConcurrency {
  private static final AtomicInteger defaultThreadCount = new AtomicInteger();
  private final int maxConcurrency;
  private final ThreadFactory threadFactory;

  private BoundedConcurrency(int maxConcurrency, ThreadFactory threadFactory) {
    checkArgument(maxConcurrency >= 1, "maxConcurrency must be greater than 0");
    this.maxConcurrency = maxConcurrency;
    this.threadFactory = requireNonNull(threadFactory);
  }

  /**
   * Returns a {@link BoundedConcurrency} using {@code maxConcurrency}.
   * Uses virtual threads to run concurrent work.
   *
   * @throws IllegalArgumentException if {@code maxConcurrency <= 0}
   */
  public static BoundedConcurrency withMaxConcurrency(int maxConcurrency) {
    return withMaxConcurrency(maxConcurrency, runnable -> {
      Thread thread = Thread.ofVirtual().unstarted(runnable);
      thread.setName("BoundedConcurrency thread #" + defaultThreadCount.getAndIncrement());
      return thread;
    });
  }

  /**
   * Returns a {@link BoundedConcurrency} using {@code maxConcurrency} and {@code threadFactor}.
   *
   * @throws IllegalArgumentException if {@code maxConcurrency <= 0}
   */
  public static BoundedConcurrency withMaxConcurrency(int maxConcurrency, ThreadFactory threadFactory) {
    return new BoundedConcurrency(maxConcurrency, threadFactory);
  }

  /**
   * Races {@code tasks} and returns the first success, then cancels the remaining.
   * Upon exception, the {@code isRecoverable} predicate is tested to check whether the
   * exception is recoverable (thus allowing the other tasks to continue to run).
   *
   * <p>When all tasks throw recoverable exceptions, or if any task failed with
   * unrecoverable exception, the recoverable exceptions are propagated as {@link
   * Throwable#addSuppressed suppressed}.
   *
   * @param maxConcurrency at most running this number of tasks concurrently
   * @param tasks at least one must be provided
   * @param isRecoverable tests whether an exception is recoverable so that the
   *     other tasks should continue running.
   * @throws IllegalArgumentException if {@code tasks} is empty
   * @throws NullPointerException if {@code tasks} or {@code isRecoverable} is null
   * @throws RuntimeException if all tasks failed, or any task failed with unrecoverable exception.
   */
  public <T> T race(
      Collection<? extends Callable<? extends T>> tasks,
      Predicate<? super Throwable> isRecoverable) {
    checkArgument(tasks.size() > 0, "At least one task should have been provided");
    requireNonNull(isRecoverable);
    ConcurrentLinkedQueue<Throwable> recoverable = new ConcurrentLinkedQueue<>();
    return tasks.stream()
        .gather(flatMapConcurrently(
            task -> {
              try {
                return Stream.of(new Success<T>(task.call()));
              } catch (Throwable e) {
                if (isRecoverable.test(e)) {
                  recoverable.add(e);
                  return Stream.empty();
                }
                return Stream.of(new Failure<T>(e));  // plumb it to the main thread to wrap
              }
            }))
        .map((Result<T> x) -> switch (x) {
          case Failure<T> failure ->
              throw new UncheckedExecutionException(failure.exception(), recoverable);
          case Success(T v) -> v;
        })
        .findAny()
        .orElseThrow(
            () -> new UncheckedExecutionException(recoverable.remove(), recoverable));
  }

  /**
   * Applies {@code work} on each input element concurrently and <em>lazily</em>.
   *
   * <p>At any given time, at most {@code maxConcurrency} concurrent work are running.
   *
   * <p>To avoid leaking virtual threads caused by upstream exceptions, input elements are
   * first computed and consumed into a List before any concurrent work is started (such that
   * if any upstream element throws, no virtual thread is ever started).
   * But the result {@link BiStream} is lazy: concurrent work only starts upon requested by
   * downstream. Specifically, if you short-circuit using {@link Stream#findAny} or
   * {@link BiStream#findAny}, at most {@code maxConcurrency} virtual threads will be started.
   *
   * <p>Compared to the {@link Gatherers#mapConcurrent} gatherer: <ul>
   * <li>{@code mapConcurrent()} only interrupts and joins the on-the-fly virtual threads upon
   * downstream exceptions, but is unable to interrupt or join when an <em>upstream </em> exception
   * is thrown; whereas {@code concurrently()} will always interrupt and join. As a result,
   * actions in the virtual threads <em>happens-before</em> actions after the collector returns or throws.
   * <li>{@code mapConcurrent()} guarantees encounter-order. If an input element takes
   * a long time or forever to process, it can potentially block or halt the program
   * when there are more than {@code maxConcurrency} elements following it, even if
   * {@code maxConcurrency - 1} virtual threads have already completed with the long-running task
   * being the only virtual thread still running. Whereas {@code concurrently()}
   * allows {@code maxConcurrency} virtual threads to run concurrently regardless of input order.
   * This means if you have a long-running virtual thread (for instance: a heart-beat worker or a
   * monitoring subtask etc.), it won't reduce throughput or starve the workers after it.
   * <li>If encounter order is important to you, consider using {@link BiStream#sorted} or
   * friends to re-introduce ordering.
   * </ul>
   *
   * <p>Compared to {@link com.google.mu.util.concurrent.Parallelizer#inParallel}: <ul>
   * <li>{@code inParallel()} fails fast. When an exception is thrown, it interrupts on-the-fly
   * threads, but doesn't wait for their completion. Thus there is no happens-before guarantee
   * when an exception is thrown. {@code concurrently()} on the other hand always joins the virtual
   * threads and guarantees strong <em>happens-before</em> relationship.
   * <li>The {@code inParallel()} collector blocks until all parallel tasks are complete before
   * returning; whereas {@code concurrently()} utilizes Java 24 Gatherer and will stream results
   * to the downstream as soon as one is computed.
   * <li>{@code inParallel()} can be used with any {@link java.util.concurrent.ExecutorService}
   * (for example you could use {@link java.util.concurrent.Executors#newCachedThreadPool} if
   * virtual threads isn't important to you); whereas {@code concurrently()} strictly runs one
   * task per thread, although you could use a customized {@link ThreadFactory}.
   * </ul>
   */
  public <I, O> Collector<I, ?, BiStream<I, O>> concurrently(
      Function<? super I, ? extends O> work) {
    requireNonNull(work);
    return Collectors.collectingAndThen(
        toList(),
        inputs -> BiStream.from(inputs.stream().gather(mapConcurrently(input -> Both.of(input, work.apply(input))))));
  }

  /**
   * Similar to {@link Gatherers#mapConcurrent}, runs {@code mapper} in virtual threads limited by
   * {@code maxConcurrency}. But maximizes concurrency without letting earlier slower operations
   * block later operations.
   *
   * <p>This gatherer doesn't guarantee encounter order; operations are allowed to race freely,
   * within the limit of {@code maxConcurrency}.
   */
  <T, R> Gatherer<T, ?, R> mapConcurrently(Function<? super T, ? extends R> mapper) {
    // Every methods of this class are called only by the main thread.
    class Window {
      private final Semaphore semaphore = new Semaphore(maxConcurrency);

      /** Only the main thread adds. Virtual threads may remove upon done to free space. */
      private final ConcurrentMap<Object, Thread> running = new ConcurrentHashMap<>();

      /** Main thread reads (consumes) results; virtual threads add upon success. */
      private final ConcurrentLinkedQueue<Success<R>> results = new ConcurrentLinkedQueue<>();

      /** Main thread reads (consumes) exceptions; virtual threads add upon failure. */
      private final ConcurrentLinkedQueue<Throwable> exceptions = new ConcurrentLinkedQueue<>();

      boolean integrate(T element, Downstream<? super R> downstream) {
        if (!flushOrStop(downstream)) { // push down available before potentially blocking.
          return false;
        }
        acquireWithInterruptionPropagation();
        if (!flushOrStop(downstream)) { // available concurrency. But downstream may not need more.
          // don't need to release on exception because integrate won't be called again.
          semaphore.release();
          return false;
        }
        Object key = new Object();
        Thread thread = threadFactory.newThread(() -> {
          try {
            results.add(new Success<>(mapper.apply(element)));
          } catch (Throwable e) {
            exceptions.add(e);
          } finally {
            running.remove(key);
            semaphore.release();
          }
        });
        thread.start();
        running.put(key, thread);
        return true;
      }

      void finish(Downstream<? super R> downstream) {
        int inFlight = maxConcurrency - semaphore.drainPermits();
        if (!flushOrStop(downstream)) {  // Flush after every happens-before point
          return;
        }
        for (; inFlight > 0; --inFlight) {
          acquireWithInterruptionPropagation();
          if (!flushOrStop(downstream)) {
            return;
          }
        }
      }

      private boolean flushOrStop(Downstream<? super R> downstream) {
        propagateExceptions();
        boolean accepted = false;
        try {
          accepted = whileNotNull(results::poll).allMatch(r -> downstream.push(r.value()));
        } finally {
          if (!accepted) {
            stop();  // Even at exception, we need to interrupt the virtual threads.
          }
        }
        return accepted;
      }

      private void propagateExceptions() {
        List<Throwable> thrown = whileNotNull(exceptions::poll).toList();
        if (thrown.isEmpty()) return;
        stop();
        throw new UncheckedExecutionException(
            thrown.get(0), thrown.subList(1, thrown.size()));
      }

      private void stop() {
        cancel();
        for (Thread thread : running.values()) {
          joinUninterruptibly(thread);
        }
      }

      private void cancel() {
        running.values().stream().forEach(Thread::interrupt);
      }

      /** Acquires a semaphore. If interrupted, propagate cancellation then retry. */
      private void acquireWithInterruptionPropagation() {
        if (Thread.currentThread().isInterrupted()) {
          cancel();
        }
        semaphore.acquireUninterruptibly(); // acquire even if interrupted
      }
    }
    return Gatherer.<T, Window, R>ofSequential(
        Window::new, Integrator.<Window, T, R>ofGreedy(Window::integrate), Window::finish);
  }

  /**
   * Similar to {@link Gatherers#mapConcurrent}, runs {@code mapper} in virtual threads limited by
   * {@code maxConcurrency}. But maximizes concurrency without letting earlier slower operations
   * block later operations, as long as concurrency doesn't exceed {@code maxConcurrency}.
   *
   * <p>Each operation can return a stream of results. Each stream is fully consumed by the host
   * virtual thread before being passed to the downstream. This prevents any interfering side
   * effects: once the elements are consumed, they remain unchanged.
   *
   * <p>Allows you to implement race semantics. For example, if you have a list of candidate
   * backends and would want to send multiple rpcs and use whichever returns successfully first:
   *
   * <pre>{@code
   * List<Backend> backends = ...;
   * return backends.stream()
   *     .gather(flatMapConcurrent(
   *         maxConcurrency,
   *         backend -> {
   *           try {
   *             return Stream.of(backend.getResult());
   *           } catch (BackendException e) {
   *             // In real life, you may want to store the exceptions
   *             // and propagate them in case no backends succeed.
   *             log(e);
   *             return Stream.empty();
   *           }
   *         }))
   *     .findAny();
   * }</pre>
   *
   * <p>This is more suitable for production usage because you rarely want to blindly
   * swallow all exceptions. Things like NullPointerException, IllegalArgumentException,
   * StackOverflowError etc. should almost never be swallowed.
   *
   * @see {@link #race} for a more production-ready utility that allows you to control what
   *      exceptions are allowed to recover from, and eventually propagate them if all
   *      have failed or a non-recoverable exception is thrown (like IllegalArgumentException).
   */
  <T, R> Gatherer<T, ?, R> flatMapConcurrently(
      Function<? super T, ? extends Stream<? extends R>> mapper) {
    requireNonNull(mapper);
    return flattening(
        this.<T, List<R>>mapConcurrently(
            // Must fully consume the stream in the virtual thread
            input -> mapper.apply(input).collect(Collectors.toCollection(ArrayList::new))));
  }

  private static <T, A, R> Gatherer<T, ?, R> flattening(
      Gatherer<? super T, A, List<R>> upstream) {
    var integrator = upstream.integrator();
    var finisher = upstream.finisher();
    return Gatherer.of(
        upstream.initializer(),
        (a, elem, downstream) -> integrator.integrate(a, elem, pushingAll(downstream)),
        upstream.combiner(),
        (a, downstream) -> finisher.accept(a, pushingAll(downstream)));
  }

  private static <T> Gatherer.Downstream<List<T>> pushingAll(Downstream<? super T> downstream) {
    return results -> results.stream().allMatch(downstream::push);
  }

  private static void joinUninterruptibly(Thread thread) {
    boolean interrupted = false;
    try {
      for (; ;) {
        try {
          thread.join();
          return;
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  // Allows to store null in ConcurrentLinkedQueue, and immutable object to help publish.
  private sealed interface Result<R> permits Success, Failure {}
  private record Success<R>(R value) implements Result<R> {}
  private record Failure<R>(Throwable exception) implements Result<R> {}

  /** While we don't pull in Guava for its {@code UncheckedExecutionException}. */
  static class UncheckedExecutionException extends RuntimeException {
    UncheckedExecutionException(Throwable cause) {
      super(cause);
    }

    UncheckedExecutionException(Throwable cause, Iterable<? extends Throwable> suppressed) {
      super(cause);
      for (Throwable t : suppressed) {
        addSuppressed(t);
      }
    }

    private static final long serialVersionUID = 1L;
  }

  private static void checkArgument(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
