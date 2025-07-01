package com.google.mu.util.stream.gatherers;

import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Gatherer;
import java.util.stream.Gatherer.Downstream;
import java.util.stream.Gatherer.Integrator;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.mu.util.concurrent.StructuredConcurrencyInterruptedException;

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

    class Window {
      private final Semaphore semaphore = new Semaphore(maxConcurrency);
      private final ConcurrentLinkedQueue<Result<R>> results = new ConcurrentLinkedQueue<>();
      // Only Error or RuntimeException
      private final ConcurrentLinkedQueue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
      boolean integrate(T element, Downstream<? super R> downstream) {
        if (!flush(downstream)) {
          return false;
        }
        acquireOrFail();
        Thread.ofVirtual().start(() -> {
          try {
            results.add(new Result<>(mapper.apply(element)));
          } catch (Throwable e) {
            exceptions.add(e);
          } finally {
            semaphore.release();
          }
        });
        return flush(downstream);
      }

      @CanIgnoreReturnValue
      boolean flush(Downstream<? super R> downstream) {
        propagateErrors();
        return whileNotNull(results::poll).allMatch(r -> downstream.push(r.value()));
      }

      void close(Downstream<? super R> downstream) {
        flush(downstream);  // Flush before blocking
        for (int inFlight = maxConcurrency - semaphore.drainPermits(); inFlight > 0; --inFlight) {
          acquireOrFail();
          flush(downstream);
        }
        flush(downstream);  // after drainPermits(), we have another happens-before
      }

      private void propagateErrors() {
        List<Throwable> thrown = whileNotNull(exceptions::poll).collect(toCollection(ArrayList::new));
        if (thrown.size() > 0) {
          Throwable first = thrown.get(0);
          UncheckedExecutionException executionException = new UncheckedExecutionException(first);
          thrown.stream().skip(1).forEach(executionException::addSuppressed);
          throw executionException;
        }
      }

      private void acquireOrFail() {
        try {
          semaphore.acquire();
        } catch (InterruptedException e) {
          throw new StructuredConcurrencyInterruptedException(e);
        }
      }
    }
    return Gatherer.ofSequential(
        Window::new,
        Integrator.<Window, T, R>ofGreedy(Window::integrate),
        Window::close);
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
