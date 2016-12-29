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
package org.mu.util;

import static java.util.Objects.requireNonNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mu.function.CheckedSupplier;

/**
 * Immutable object that retries actions upon exceptions.
 *
 * <p>Backoff intervals are configured through chaining {@link #upon upon()} calls. It's critical
 * to use the new {@code Retryer} instances returned by {@code upon()}. Just remember
 * {@code Retryer} is <em>immutable</em>.
 */
public class Retryer {

  private static final Logger logger = Logger.getLogger(Retryer.class.getName());

  private final ExceptionPlan<Delay> plan;

  /** Constructs an empty {@code Retryer}. */
  public Retryer() {
    this(new ExceptionPlan<>());
  }

  private Retryer(ExceptionPlan<Delay> plan) {
    this.plan = requireNonNull(plan);
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception satisfies
   * {@code condition}.
   */
  public Retryer upon(Predicate<? super Throwable> condition, List<Delay> delays) {
    return new Retryer(plan.upon(condition, delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType}.
   */
  public Retryer upon(Class<? extends Throwable> exceptionType, List<Delay> delays) {
    return new Retryer(plan.upon(exceptionType, delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType} and satisfies {@code condition}.
   */
  public <E extends Throwable> Retryer upon(
      Class<E> exceptionType, Predicate<? super E> condition, List<Delay> delays) {
    return new Retryer(plan.upon(exceptionType, condition, delays));
  }

  /**
   * Invokes and possibly retries {@code supplier} upon exceptions, according to the retry
   * strategies specified with {@link #upon upon()}.
   * 
   * <p>This method blocks while waiting to retry. If interrupted, retry is canceled.
   *
   * <p>If {@code supplier} fails despite retrying, the exception from the most recent invocation
   * is propagated.
   */
  public <T, E extends Throwable> T retryBlockingly(CheckedSupplier<T, E> supplier) throws E {
    requireNonNull(supplier);
    for (ExceptionPlan<Delay> currentPlan = plan; ;) {
      try {
        return supplier.get();
      } catch (RuntimeException e) {
        currentPlan = delay(e, currentPlan);
      } catch (Error e) {
        currentPlan = delay(e, currentPlan);
      } catch (Throwable e) {
        @SuppressWarnings("unchecked")  // supplier can only throw unchecked or E.
        E checked = (E) e;
        currentPlan = delay(checked, currentPlan);
      }
    }
  }

  /**
   * Invokes and possibly retries {@code supplier} upon exceptions, according to the retry
   * strategies specified with {@link #upon upon()}.
   *
   * <p>The first invocation is done in the current thread. Unchecked exceptions thrown by
   * {@code supplier} directly are propagated unless explicitly configured to retry.
   * This is to avoid hiding programming errors.
   * Checked exceptions are reported through the returned {@link CompletionStage} so callers only
   * need to deal with them in one place.
   *
   * <p>Retries are scheduled and performed by {@code executor}.
   * 
   * <p>NOTE that if {@code executor.shutdownNow()} is called, the returned {@link CompletionStage}
   * will never be done.
   */
  public <T> CompletionStage<T> retry(
      CheckedSupplier<T, ?> supplier, ScheduledExecutorService executor) {
    return retryAsync(supplier.map(CompletableFuture::completedFuture), executor);
  }

  /**
   * Invokes and possibly retries {@code asyncSupplier} upon exceptions, according to the retry
   * strategies specified with {@link #upon upon()}.
   *
   * <p>The first invocation is done in the current thread. Unchecked exceptions thrown by
   * {@code asyncSupplier} directly are propagated unless explicitly configured to retry.
   * This is to avoid hiding programming errors.
   * Checked exceptions are reported through the returned {@link CompletionStage} so callers only
   * need to deal with them in one place.
   *
   * <p>Retries are scheduled and performed by {@code executor}.
   * 
   * <p>NOTE that if {@code executor.shutdownNow()} is called, the returned {@link CompletionStage}
   * will never be done.
   */
  public <T> CompletionStage<T> retryAsync(
      CheckedSupplier<? extends CompletionStage<T>, ?> asyncSupplier,
      ScheduledExecutorService executor) {
    requireNonNull(asyncSupplier);
    requireNonNull(executor);
    CompletableFuture<T> future = new CompletableFuture<>();
    invokeWithRetry(asyncSupplier, executor, future);
    return future;
  }

  /** Represents a single delay interval between attempts. */
  public interface Delay extends Comparable<Delay> {

    /** Shorthand for {@code new Delay(Duration.ofMillis(millis))}. */
    static Delay ofMillis(long millis) {
      return new DefaultDelay(Duration.ofMillis(millis));
    }

    /** Returns a {@code Delay} of {@code duration}. */
    static Delay of(Duration duration) {
      return new DefaultDelay(requireNonNull(duration));
    }

    /**
     * Returns an immutable {@code List} of durations with {@code size}. The first duration
     * (if {@code size > 0}) is {@code firstDelay} and the following durations are exponentially
     * multiplied using {@code multiplier}.
     */
    static List<Delay> exponentialBackoff(Duration firstDelay, double multiplier, int size) {
      requireNonNull(firstDelay);
      if (multiplier <= 0) throw new IllegalArgumentException("Invalid multiplier: " + multiplier);
      if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
      if (size == 0) return Collections.emptyList();
      return new AbstractList<Delay>() {
        @Override public Delay get(int index) {
          return new DefaultDelay(scale(firstDelay, Math.pow(multiplier, index)));
        }
        @Override public int size() {
          return size;
        }
      };
    }

    /**
     * Returns a wrapper of {@code delays} list that while not modifiable, can suddenly become empty
     * when {@code totalDuration} has elapsed since the time the wrapper was created. {@code clock}
     * is used to measure time.
     */
    static List<Delay> timed(List<Delay> delays, Duration totalDuration, Clock clock) {
      requireNonNull(clock);
      Instant until = clock.instant().plus(totalDuration);
      return guarded(delays, () -> clock.instant().isBefore(until));
    }

    /**
     * Returns a wrapper of {@code delays} list that while not modifiable, can suddenly become empty
     * when {@code totalDuration} has elapsed since the time the wrapper was created.
     */
    static List<Delay> timed(List<Delay> delays, Duration totalDuration) {
      return timed(delays, totalDuration, Clock.systemUTC());
    }

    /**
     * Similar to {@link #timed timed()}, this method wraps {@code list} to make it empty when
     * {@code condition} becomes false.
     */
    static <T> List<T> guarded(List<T> list, BooleanSupplier condition) {
      requireNonNull(list);
      requireNonNull(condition);
      return new AbstractList<T>() {
        @Override public T get(int index) {
          if (condition.getAsBoolean()) return list.get(index);
          throw new IndexOutOfBoundsException();
        }
        @Override public int size() {
          return condition.getAsBoolean() ? list.size() : 0;
        }
      };
    }

    /** Returns the delay interval. */
    Duration duration();

    @Override default int compareTo(Delay that) {
      return duration().compareTo(that.duration());
    }

    /** Called if {@code exception} will be retried after the delay. */
    default void beforeDelay(Throwable exception) {
      logger.info("Will retry for " + exception.getClass() + " after " + duration());
      
    }

    /** Called after the delay, immediately before the retry. */
    default void afterDelay(Throwable exception) {
      logger.log(Level.INFO, "Retrying now after " + duration(), exception);
    }
  }

  private static final class DefaultDelay implements Delay {

    private final Duration duration;

    DefaultDelay(Duration duration) {
      this.duration = duration;
    }

    @Override public final Duration duration() {
      return duration;
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof Delay) {
        Delay that = (Delay) obj;
        return duration.equals(that.duration());
      }
      return false;
    }

    @Override public int hashCode() {
      return duration.hashCode();
    }

    @Override public String toString() {
      return duration.toString();
    }
  }

  private static <E extends Throwable> ExceptionPlan<Delay> delay(
      E exception, ExceptionPlan<Delay> plan) throws E {
    ExceptionPlan.Execution<Delay> execution = plan.execute(exception).get();
    try {
      Thread.sleep(execution.strategy().duration().toMillis());
    } catch (InterruptedException e) {
      logger.info("Interrupted while waiting to retry upon " + exception.getClass());
      Thread.currentThread().interrupt();
      throw exception;
    }
    execution.strategy().afterDelay(exception);
    return execution.remainingExceptionPlan();
  }

  private <T> void invokeWithRetry(
      CheckedSupplier<? extends CompletionStage<T>, ?> supplier,
      ScheduledExecutorService retryExecutor,
      CompletableFuture<T> result) {
    try {
      CompletionStage<T> stage = supplier.get();
      stage.thenAccept(result::complete);
      stage.exceptionally(e -> {
        scheduleRetry(getInterestedException(e), retryExecutor, supplier, result);
        return null;
      });
    } catch (RuntimeException e) {
      retryIfCovered(e, retryExecutor, supplier, result);
    } catch (Error e) {
      retryIfCovered(e, retryExecutor, supplier, result);
    } catch (Throwable e) {
      scheduleRetry(e, retryExecutor, supplier, result);
    }
  }

  private <E extends Throwable, T> void retryIfCovered(
      E e, ScheduledExecutorService retryExecutor,
      CheckedSupplier<? extends CompletionStage<T>, ?> supplier, CompletableFuture<T> result)
      throws E {
    if (plan.covers(e)) {
      scheduleRetry(e, retryExecutor, supplier, result);
    } else {
      throw e;
    }
  }

  private <T> void scheduleRetry(
      Throwable e, ScheduledExecutorService retryExecutor,
      CheckedSupplier<? extends CompletionStage<T>, ?> supplier, CompletableFuture<T> result) {
    Maybe<ExceptionPlan.Execution<Delay>, ?> maybeRetry = plan.execute(e);
    maybeRetry.ifPresent(execution -> {
      execution.strategy().beforeDelay(e);
      Failable retry = () -> {
        execution.strategy().afterDelay(e);
        new Retryer(execution.remainingExceptionPlan())
            .invokeWithRetry(supplier, retryExecutor, result);
      };
      retryExecutor.schedule(
          () -> retry.run(result::completeExceptionally),
          execution.strategy().duration().toMillis(), TimeUnit.MILLISECONDS);
    });
    maybeRetry.catching(result::completeExceptionally);
  }

  private static Throwable getInterestedException(Throwable exception) {
    if (exception instanceof CompletionException || exception instanceof ExecutionException) {
      return exception.getCause() == null ? exception : exception.getCause();
    }
    return exception;
  }

  private static Duration scale(Duration duration, double multiplier) {
    double millis = duration.toMillis() * multiplier;
    return Duration.ofMillis(Math.round(millis));
  }

  @FunctionalInterface
  private interface Failable {
    void run() throws Throwable;

    default void run(Consumer<? super Throwable> exceptionHandler) {
      try {
        run();
      } catch (Throwable e) {
        exceptionHandler.accept(e);
      }
    }
  }
}
