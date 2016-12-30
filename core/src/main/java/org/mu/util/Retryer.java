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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  private final ExceptionPlan<Delay<?>> plan;

  /** Constructs an empty {@code Retryer}. */
  public Retryer() {
    this(new ExceptionPlan<>());
  }

  private Retryer(ExceptionPlan<Delay<?>> plan) {
    this.plan = requireNonNull(plan);
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception satisfies
   * {@code condition}.
   */
  public final Retryer upon(
      Predicate<? super Throwable> condition, List<? extends Delay<Throwable>> delays) {
    return new Retryer(plan.upon(condition, delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception satisfies
   * {@code condition}.
   */
  public final Retryer upon(
      Predicate<? super Throwable> condition, Stream<? extends Delay<Throwable>> delays) {
    return upon(condition, delays.collect(Collectors.toCollection(ArrayList::new)));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType}.
   */
  public final <E extends Throwable> Retryer upon(
      Class<E> exceptionType, List<? extends Delay<? super E>> delays) {
    return new Retryer(plan.upon(exceptionType, delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType}.
   */
  public final <E extends Throwable> Retryer upon(
      Class<E> exceptionType, Stream<? extends Delay<? super E>> delays) {
    List<? extends Delay<? super E>> delayList =
        delays.collect(Collectors.toCollection(ArrayList::new));
    return upon(exceptionType, delayList);
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType} and satisfies {@code condition}.
   */
  public <E extends Throwable> Retryer upon(
      Class<E> exceptionType, Predicate<? super E> condition,
      List<? extends Delay<? super E>> delays) {
    return new Retryer(plan.upon(exceptionType, condition, delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType} and satisfies {@code condition}.
   */
  public <E extends Throwable> Retryer upon(
      Class<E> exceptionType, Predicate<? super E> condition,
      Stream<? extends Delay<? super E>> delays) {
    List<? extends Delay<? super E>> delayList =
        delays.collect(Collectors.toCollection(ArrayList::new));
    return upon(exceptionType, condition, delayList);
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
    for (ExceptionPlan<Delay<?>> currentPlan = plan; ;) {
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

  /**
   * Represents a delay interval between retry attempts for exceptions of type {@code E}.
   */
  public static abstract class Delay<E extends Throwable> implements Comparable<Delay<E>> {

    /** Returns the delay interval. */
    public abstract Duration duration();

    /**
     * Shorthand for {@code of(Duration.ofMillis(millis))}.
     *
     * @param millis must not be negative
     */
    public static <E extends Throwable> Delay<E> ofMillis(long millis) {
      return of(Duration.ofMillis(millis));
    }

    /**
     * Returns a {@code Delay} of {@code duration}.
     *
     * @param duration must not be negative
     */
    public static <E extends Throwable> Delay<E> of(Duration duration) {
      requireNonNegative(duration);
      return new Delay<E>() {
        @Override public Duration duration() {
          return duration;
        }
      };
    }

    /**
     * Returns a wrapper of {@code list} that while not modifiable, can suddenly become empty
     * when {@code totalDuration} has elapsed since the time the wrapper was created. {@code clock}
     * is used to measure time.
     */
    public static <T> List<T> timed(List<T> list, Duration totalDuration, Clock clock) {
      Instant until = clock.instant().plus(requireNonNegative(totalDuration));
      return guarded(list, () -> clock.instant().isBefore(until));
    }

    /**
     * Returns a wrapper of {@code list} that while not modifiable, can suddenly become empty
     * when {@code totalDuration} has elapsed since the time the wrapper was created.
     */
    public static <T> List<T> timed(List<T> list, Duration totalDuration) {
      return timed(list, totalDuration, Clock.systemUTC());
    }

    /**
     * Similar to {@link #timed timed()}, this method wraps {@code list} to make it empty when
     * {@code condition} becomes false.
     */
    public static <T> List<T> guarded(List<T> list, BooleanSupplier condition) {
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

    /**
     * Returns an immutable {@code List} of delays with {@code size}. The first delay
     * (if {@code size > 0}) is {@code this} and the following delays are exponentially
     * multiplied using {@code multiplier}.
     *
     * @param multiplier must be positive
     * @param size must not be negative
     */
    public final List<Delay<E>> exponentialBackoff(double multiplier, int size) {
      if (multiplier <= 0) throw new IllegalArgumentException("Invalid multiplier: " + multiplier);
      if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
      if (size == 0) return Collections.emptyList();
      return new AbstractList<Delay<E>>() {
        @Override public Delay<E> get(int index) {
          return multipliedBy(Math.pow(multiplier, index));
        }
        @Override public int size() {
          return size;
        }
      };
    }
 
    /**
     * Returns a new {@code Delay} with duration multiplied by {@code multiplier}.
     *
     * @param multiplier must not be negative
     */
    public final Delay<E> multipliedBy(double multiplier) {
      if (multiplier < 0) throw new IllegalArgumentException("Invalid multiplier: " + multiplier);
      double millis = duration().toMillis() * multiplier;
      return of(Duration.ofMillis(Math.round(Math.ceil(millis))));
    }
  
    /**
     * Returns a new {@code Delay} with some extra randomness.
     * To randomize a list of {@code Delay}s, for example:
     *
     * <pre>{@code
     *   Random random = new Random();
     *   List<Delay> randomized = Delay.ofMillis(100).exponentialBackoff(2, 5).stream()
     *       .map(d -> d.randomized(random, 0.5))
     *       .collect(toList());
     * }</pre>
     *
     * @param random random generator
     * @param randomness Must be in the range of [0, 1]. 0 means no randomness; and 1 means the
     *        delay randomly ranges from 0x to 2x.
     */
    public final Delay<E> randomized(Random random, double randomness) {
      if (randomness < 0 || randomness > 1) {
        throw new IllegalArgumentException("Randomness must be in range of [0, 1]: " + randomness);
      }
      if (randomness == 0) return this;
      return multipliedBy(1 + (random.nextDouble() - 0.5) * 2 * randomness);
    }

    /** Called if {@code exception} will be retried after the delay. */
    public void beforeDelay(E exception) {
      logger.info("Will retry for " + exception.getClass() + " after " + duration());
      
    }

    /** Called after the delay, immediately before the retry. */
    public void afterDelay(E exception) {
      logger.log(Level.INFO, "Retrying now after " + duration(), exception);
    }

    @Override public int compareTo(Delay<E> that) {
      return duration().compareTo(that.duration());
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof Delay) {
        Delay<?> that = (Delay<?>) obj;
        return duration().equals(that.duration());
      }
      return false;
    }

    @Override public int hashCode() {
      return duration().hashCode();
    }

    @Override public String toString() {
      return duration().toString();
    }

    private static Duration requireNonNegative(Duration duration) {
      if (duration.toMillis() < 0) {
        throw new IllegalArgumentException("Negative duration: " + duration);
      }
      return duration;
    }
  }

  private static <E extends Throwable> ExceptionPlan<Delay<?>> delay(
      E exception, ExceptionPlan<Delay<?>> plan) throws E {
    ExceptionPlan.Execution<Delay<?>> execution = plan.execute(exception).get();
    @SuppressWarnings("unchecked")  // Applicable delays were from upon(), enforcing <? super E>
    Delay<? super E> delay = (Delay<? super E>) execution.strategy();
    delay.beforeDelay(exception);
    try {
      Thread.sleep(delay.duration().toMillis());
    } catch (InterruptedException e) {
      logger.info("Interrupted while waiting to retry upon " + exception.getClass());
      Thread.currentThread().interrupt();
      throw exception;
    }
    delay.afterDelay(exception);
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
    Maybe<ExceptionPlan.Execution<Delay<?>>, ?> maybeRetry = plan.execute(e);
    maybeRetry.ifPresent(execution -> {
      @SuppressWarnings("unchecked")  // delay came from upon(), which enforces <? super E>.
      Delay<Throwable> delay = (Delay<Throwable>) execution.strategy();
      delay.beforeDelay(e);
      Failable retry = () -> {
        delay.afterDelay(e);
        new Retryer(execution.remainingExceptionPlan())
            .invokeWithRetry(supplier, retryExecutor, result);
      };
      retryExecutor.schedule(
          () -> retry.run(result::completeExceptionally),
          delay.duration().toMillis(), TimeUnit.MILLISECONDS);
    });
    maybeRetry.catching(result::completeExceptionally);
  }

  private static Throwable getInterestedException(Throwable exception) {
    if (exception instanceof CompletionException || exception instanceof ExecutionException) {
      return exception.getCause() == null ? exception : exception.getCause();
    }
    return exception;
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
