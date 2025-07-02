package com.google.mu.util.stream.gatherers;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Gatherer;
import java.util.stream.Gatherer.Downstream;
import java.util.stream.Gatherer.Integrator;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * More {@link Gatherer} implementations. Notably, {@link #mapConcurrently}
 * and {@link #flatMapConcurrently}.
 *
 * @since 9.0
 */
public final class MoreGatherers {
  /**
   * Similar to {@link Gatherers#mapConcurrent}, runs {@code mapper} in virtual threads limited by
   * {@code maxConcurrency}. But maximizes concurrency without letting earlier slower operations
   * block later operations, as long as concurrency doesn't exceed {@code maxConcurrency}.
   *
   * <p>This gatherer doesn't ensure encounter order. Instead, operations can race concurrently.
   */
  public static <T, R> Gatherer<T, ?, R> mapConcurrently(
      int maxConcurrency, Function<? super T, ? extends R> mapper) {
    requireNonNull(mapper);
    if (maxConcurrency < 1) {
      throw new IllegalArgumentException("maxConcurrency must be greater than 0");
    }

    // Every methods of this class are called only by the main thread.
    class Window {
      private final Semaphore semaphore = new Semaphore(maxConcurrency);

      /** Only the main thread adds. Virtual threads may remove upon done to free space. */
      private final ConcurrentMap<Object, Thread> running = new ConcurrentHashMap<>();

      /** Main thread reads (consumes) results; virtual threads add upon success. */
      private final ConcurrentLinkedQueue<Result<R>> results = new ConcurrentLinkedQueue<>();

      /** Main thread reads (consumes) exceptions; virtual threads add upon failure. */
      private final ConcurrentLinkedQueue<Throwable> exceptions = new ConcurrentLinkedQueue<>();

      boolean integrate(T element, Downstream<? super R> downstream) {
        if (!flush(downstream)) {
          return false;
        }
        acquireWithInterruptionPropagation();
        Object key = new Object();
        running.put(key, Thread.ofVirtual().start(() -> {
          try {
            results.add(new Result<>(mapper.apply(element)));
          } catch (Throwable e) {
            exceptions.add(e);
          } finally {
            running.remove(key);
            semaphore.release();
          }
        }));
        return flush(downstream);
      }

      @CanIgnoreReturnValue
      boolean flush(Downstream<? super R> downstream) {
        propagateErrors();
        return whileNotNull(results::poll).allMatch(r -> downstream.push(r.value()));
      }

      void finish(Downstream<? super R> downstream) {
        int inFlight = maxConcurrency - semaphore.drainPermits();
        flush(downstream);  // Flush after every happens-before point
        for (; inFlight > 0; --inFlight) {
          acquireWithInterruptionPropagation();
          flush(downstream);
        }
      }

      private void propagateErrors() {
        List<Throwable> thrown = whileNotNull(exceptions::poll).collect(toCollection(ArrayList::new));
        if (thrown.size() > 0) {
          propagateInterruption();
          for (Thread thread : running.values()) {
            joinUninterruptibly(thread);
          }
          Throwable first = thrown.get(0);
          UncheckedExecutionException executionException = new UncheckedExecutionException(first);
          thrown.stream().skip(1).forEach(executionException::addSuppressed);
          throw executionException;
        }
      }

      /** Acquires a semaphore. If interrupted, propagate cancellation then retry. */
      private void acquireWithInterruptionPropagation() {
        if (Thread.currentThread().isInterrupted()) {
          propagateInterruption();
        }
        semaphore.acquireUninterruptibly();
      }

      private void propagateInterruption() {
        running.values().stream().forEach(Thread::interrupt);
      }
    }
    return Gatherer.ofSequential(
        Window::new,
        Integrator.<Window, T, R>ofGreedy(Window::integrate),
        Window::finish);
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
   */
  public static <T, R> Gatherer<T, ?, R> flatMapConcurrently(
      int maxConcurrency, Function<? super T, ? extends Stream<? extends R>> mapper) {
    requireNonNull(mapper);
    return flattening(
        mapConcurrently(
            maxConcurrency,
            // Must fully consume the stream in the virtual thread
            input -> mapper.apply(input).collect(toCollection(ArrayList::new))));
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
  private static record Result<R>(R value) {}

  /** While we don't pull in Guava for its {@code UncheckedExecutionException}. */
  static class UncheckedExecutionException extends RuntimeException {
    UncheckedExecutionException(Throwable cause) {
      super(cause);
    }
    private static final long serialVersionUID = 1L;
  }

  private MoreGatherers() {}
}
