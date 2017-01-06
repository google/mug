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
import static org.mu.util.Maybe.propagateIfUnchecked;

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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mu.function.CheckedSupplier;

/**
 * Immutable object that retries actions upon exceptions.
 *
 * <p>Backoff intervals are configured through chaining {@link #upon upon()} calls. It's critical
 * to use the new {@code Retryer} instances returned by {@code upon()}. Just remember
 * {@code Retryer} is <em>immutable</em>.
 *
 * <p>If the retried operation still fails after retry, the previous exceptions can be accessed
 * through {@link Throwable#getSuppressed()}.
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
    return upon(exceptionType, copyOf(delays));
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
    return upon(exceptionType, condition, copyOf(delays));
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
    List<Throwable> exceptions = new ArrayList<>();
    try {
      for (ExceptionPlan<Delay<?>> currentPlan = plan; ;) {
        try {
          return supplier.get();
        } catch (Throwable e) {
          exceptions.add(e);
          currentPlan = delay(e, currentPlan);
        }
      }
    } catch (Throwable e) {
      for (Throwable t : exceptions) addSuppressedTo(e, t);
      @SuppressWarnings("unchecked")  // Caller makes sure the exception is either E or unchecked.
      E checked = (E) propagateIfUnchecked(e);
      throw checked;
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
    return retryAsync(
        supplier.<CompletionStage<T>>map(CompletableFuture::completedFuture), executor);
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
   * Returns a new object that retries if the return value satisfies {@code condition}.
   * {@code delays} specify the backoffs between retries.
   */
  public <T> ForReturnValue<T> ifReturns(
      Predicate<T> condition, List<? extends Delay<? super T>> delays) {
    return new ForReturnValue<>(this, condition, delays);
  }

  /**
   * Returns a new object that retries if the return value satisfies {@code condition}.
   * {@code delays} specify the backoffs between retries.
   */
  public <T> ForReturnValue<T> ifReturns(
      Predicate<T> condition, Stream<? extends Delay<? super T>> delays) {
    List<? extends Delay<? super T>> delayList = copyOf(delays);
    return ifReturns(condition, delayList);
  }

  /**
   * Returns a new object that retries if the function returns {@code returnValue}.
   * 
   * @param returnValue The return value that triggers retry. Must not be {@code null}.
   *        To retry for null return value, use {@code r -> r == null}.
   * @param delays specify the backoffs between retries
   */
  public <T> ForReturnValue<T> uponReturn(
      T returnValue, Stream<? extends Delay<? super T>> delays) {
    return uponReturn(returnValue, copyOf(delays));
  }

  /**
   * Returns a new object that retries if the function returns {@code returnValue}.
   * 
   * @param returnValue The return value that triggers retry. Must not be {@code null}.
   *        To retry for null return value, use {@code r -> r == null}.
   * @param delays specify the backoffs between retries
   */
  public <T> ForReturnValue<T> uponReturn(
      T returnValue, List<? extends Delay<? super T>> delays) {
    requireNonNull(returnValue);
    return ifReturns(returnValue::equals, delays);
  }

  /** Retries based on return values. */
  public static final class ForReturnValue<T> {
    private final Retryer retryer;
    private final Predicate<? super T> condition;

    ForReturnValue(
        Retryer retryer,
        Predicate<? super T> condition, List<? extends Delay<? super T>> delays) {
      this.condition = requireNonNull(condition);
      this.retryer = retryer.upon(ThrownReturn.class, ThrownReturn.wrap(delays));
    }

    /**
     * Invokes and possibly retries {@code supplier} according to the retry
     * strategies specified with {@link #uponReturn uponReturn()}.
     * 
     * <p>This method blocks while waiting to retry. If interrupted, retry is canceled.
     *
     * <p>If {@code supplier} fails despite retrying, the return value from the most recent
     * invocation is returned.
     */
    public <R extends T, E extends Throwable> R retryBlockingly(
        CheckedSupplier<R, E> supplier) throws E {
      CheckedSupplier<R, E> wrapped = () -> retryer.retryBlockingly(supplier.map(this::wrap));
      return ThrownReturn.unwrap(wrapped);
    }

    /**
     * Invokes and possibly retries {@code supplier} according to the retry
     * strategies specified with {@link #uponReturn uponReturn()}.
     *
     * <p>The first invocation is done in the current thread. Unchecked exceptions thrown by
     * {@code supplier} directly are propagated. This is to avoid hiding programming errors.
     * Checked exceptions are reported through the returned {@link CompletionStage} so callers only
     * need to deal with them in one place.
     *
     * <p>Retries are scheduled and performed by {@code executor}.
     * 
     * <p>NOTE that if {@code executor.shutdownNow()} is called, the returned
     * {@link CompletionStage} will never be done.
     */
    public <R extends T, E extends Throwable> CompletionStage<R> retry(
        CheckedSupplier<? extends R, E> supplier,
        ScheduledExecutorService retryExecutor) {
      return ThrownReturn.unwrapAsync(() -> retryer.retry(supplier.map(this::wrap), retryExecutor));
    }

    /**
     * Invokes and possibly retries {@code asyncSupplier} according to the retry
     * strategies specified with {@link #uponReturn uponReturn()}.
     *
     * <p>The first invocation is done in the current thread. Unchecked exceptions thrown by
     * {@code asyncSupplier} directly are propagated. This is to avoid hiding programming errors.
     * Checked exceptions are reported through the returned {@link CompletionStage} so callers only
     * need to deal with them in one place.
     *
     * <p>Retries are scheduled and performed by {@code executor}.
     * 
     * <p>NOTE that if {@code executor.shutdownNow()} is called, the returned
     * {@link CompletionStage} will never be done.
     */
    public <R extends T, E extends Throwable> CompletionStage<R> retryAsync(
        CheckedSupplier<? extends CompletionStage<R>, E> asyncSupplier,
        ScheduledExecutorService retryExecutor) {
      return ThrownReturn.unwrapAsync(
          () -> retryer.retryAsync(() -> asyncSupplier.get().thenApply(this::wrap), retryExecutor));
    }

    private <R extends T> R wrap(R returnValue) {
      if (condition.test(returnValue)) throw new ThrownReturn(returnValue);
      return returnValue;
    }

    /**
     * This would have been static type safe if exception classes are allowed to be parameterized.
     * Failing that, we have to resort to old time Object.
     *
     * At call site, we always wrap and unwrap in the same function for the same T, so we are
     * safe.
     */
    @SuppressWarnings("serial")
    private static final class ThrownReturn extends Error {
      private static final boolean DISABLE_SUPPRESSION = false;
      private static final boolean NO_STACK_TRACE = false;
      private final Object returnValue;

      ThrownReturn(Object returnValue) {
        super("This should never escape!", null, DISABLE_SUPPRESSION, NO_STACK_TRACE);
        this.returnValue = returnValue;
      }
  
      static <T, E extends Throwable> T unwrap(CheckedSupplier<T, E> supplier) throws E {
        try {
          return supplier.get();
        } catch (ThrownReturn thrown) {
          return thrown.unsafeGet();
        }
      }
  
      static <T, E extends Throwable> CompletionStage<T> unwrapAsync(
          CheckedSupplier<? extends CompletionStage<T>, E> supplier) throws E {
        CompletionStage<T> stage = unwrap(supplier);
        return Maybe.catchException(ThrownReturn.class, stage)
            .thenApply(maybe -> maybe.<RuntimeException>orElse(ThrownReturn::unsafeGet));
      }
  
      static List<Delay<ThrownReturn>> wrap(List<? extends Delay<?>> delays) {
        requireNonNull(delays);
        return new AbstractList<Delay<ThrownReturn>>() {
          @Override public int size() {
            return delays.size();
          }
          @Override public Delay<ThrownReturn> get(int index) {
            return wrap(delays.get(index));
          }
        };
      }

      /** Exception cannot be parameterized. But we essentially use it as ThrownReturn<T>. */
      @SuppressWarnings("unchecked")
      private <T> T unsafeGet() {
        return (T) returnValue;
      }
  
      private static Delay<ThrownReturn> wrap(Delay<?> delay) {
        return new Delay<ThrownReturn>() {
          @Override public Duration duration() {
            return delay.duration();
          }
          @Override public void beforeDelay(ThrownReturn thrown) {
            delay.beforeDelay(thrown.unsafeGet());
          }
          @Override public void afterDelay(ThrownReturn thrown) {
            delay.afterDelay(thrown.unsafeGet());
          }
          @Override void interrupted(ThrownReturn thrown) {
            delay.interrupted(thrown.unsafeGet());
          }
        };
      }
    }
  }

  /**
   * Represents a delay interval between retry attempts for exceptional events of type {@code E}.
   */
  public static abstract class Delay<E> implements Comparable<Delay<E>> {

    /** Returns the delay interval. */
    public abstract Duration duration();

    /**
     * Shorthand for {@code of(Duration.ofMillis(millis))}.
     *
     * @param millis must not be negative
     */
    public static <E> Delay<E> ofMillis(long millis) {
      return of(Duration.ofMillis(millis));
    }

    /**
     * Returns a {@code Delay} of {@code duration}.
     *
     * @param duration must not be negative
     */
    public static <E> Delay<E> of(Duration duration) {
      requireNonNegative(duration);
      return new Delay<E>() {
        @Override public Duration duration() {
          return duration;
        }
      };
    }

    /**
     * Returns a view of {@code list} that while not modifiable, will become empty
     * when {@link #duration} has elapsed since the time the view was created as if another
     * thread had just concurrently removed all elements from it.
     *
     * <p>Useful for setting a retry deadline to avoid long response time. For example:
     *
     * <pre>{@code
     *   Delay<?> deadline = Delay.ofMillis(500);
     *   new Retryer()
     *       .upon(RpcException.class,
     *             deadline.timed(Delay.ofMillis(30).exponentialBackoff(2, 5), clock))
     *       .retry(this::getAccount, executor);
     * }</pre>
     *
     * <p>The returned {@code List} view's state is dependent on the current time.
     * Beware of copying the list, because when you do, time is frozen as far as the copy is
     * concerned. Passing the copy to {@link #upon upon()} no longer respects "timed" semantics.
     *
     * <p>Note that if the timed deadline <em>would have been</em> exceeded after the current
     * delay, that delay will be considered "removed" and hence cause the retry to stop.
     *
     * <p>{@code clock} is used to measure time.
     */
    public final <T extends Delay<?>> List<T> timed(List<T> list, Clock clock) {
      Instant until = clock.instant().plus(duration());
      requireNonNull(list);
      return new AbstractList<T>() {
        @Override public T get(int index) {
          T actual = list.get(index);
          if (clock.instant().plus(actual.duration()).isBefore(until)) return actual;
          throw new IndexOutOfBoundsException();
        }
        @Override public int size() {
          return clock.instant().isBefore(until) ? list.size() : 0;
        }
      };
    }

    /**
     * Returns a view of {@code list} that while not modifiable, will become empty
     * when {@link #duration} has elapsed since the time the view was created as if another
     * thread had just concurrently removed all elements from it.
     *
     * <p>Useful for setting a retry deadline to avoid long response time. For example:
     *
     * <pre>{@code
     *   Delay<?> deadline = Delay.ofMillis(500);
     *   new Retryer()
     *       .upon(RpcException.class, deadline.timed(Delay.ofMillis(30).exponentialBackoff(2, 5)))
     *       .retry(this::getAccount, executor);
     * }</pre>
     *
     * <p>The returned {@code List} view's state is dependent on the current time.
     * Beware of copying the list, because when you do, time is frozen as far as the copy is
     * concerned. Passing the copy to {@link #upon upon()} no longer respects "timed" semantics.
     *
     * <p>Note that if the timed deadline <em>would have been</em> exceeded after the current
     * delay, that delay will be considered "removed" and hence cause the retry to stop.
     */
    public final <T extends Delay<?>> List<T> timed(List<T> list) {
      return timed(list, Clock.systemUTC());
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
      checkSize(size);
      if (size == 0) return Collections.emptyList();
      return new AbstractList<Delay<E>>() {
        @Override public Delay<E> get(int index) {
          return multipliedBy(Math.pow(multiplier, checkIndex(index, size)));
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

    /**
     * Returns a fibonacci list of delays of {@code size}, as in {@code 1, 1, 2, 3, 5, 8, ...} with
     * {@code this} delay being the multiplier.
     */
    public final List<Delay<E>> fibonacci(int size) {
      checkSize(size);
      if (size == 0) return Collections.emptyList();
      return new AbstractList<Delay<E>>() {
        @Override public Delay<E> get(int index) {
          return ofMillis(Math.round(fib(checkIndex(index, size) + 1) * duration().toMillis()));
        }
        @Override public int size() {
          return size;
        }
      };
    }

    /** Called if {@code event} will be retried after the delay. */
    public void beforeDelay(E event) {
      logger.info(event + ": will retry after " + duration());
    }

    /** Called after the delay, immediately before the retry. */
    public void afterDelay(E event) {
      logger.info(event + ": " + duration() + " has passed. Retrying now...");
    }

    /** Called if delay for {@code event} is interrupted. */
    void interrupted(E event) {
      logger.info(event + ": interrupted while waiting to retry upon .");
      Thread.currentThread().interrupt();
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

    final void synchronously(E event) throws InterruptedException {
      beforeDelay(event);
      Thread.sleep(duration().toMillis());
      afterDelay(event);
    }

    final void asynchronously(
        E event, Failable continuation, Consumer<? super Throwable> exceptionHandler,
        ScheduledExecutorService executor) {
      beforeDelay(event);
      Failable afterDelay = () -> {
        afterDelay(event);
        continuation.run();
      };
      executor.schedule(
          () -> afterDelay.run(exceptionHandler), duration().toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Duration requireNonNegative(Duration duration) {
      if (duration.toMillis() < 0) {
        throw new IllegalArgumentException("Negative duration: " + duration);
      }
      return duration;
    }
  }
  
  static double fib(int n) {
    double phi = 1.6180339887;
    return (Math.pow(phi, n) - Math.pow(-phi, -n)) / (2 * phi - 1);
  }

  private static <E extends Throwable> ExceptionPlan<Delay<?>> delay(
      E exception, ExceptionPlan<Delay<?>> plan) throws E {
    ExceptionPlan.Execution<Delay<?>> execution = plan.execute(exception).get();
    @SuppressWarnings("unchecked")  // Applicable delays were from upon(), enforcing <? super E>
    Delay<? super E> delay = (Delay<? super E>) execution.strategy();
    try {
      delay.synchronously(exception);
    } catch (InterruptedException e) {
      delay.interrupted(exception);
      throw exception;
    }
    return execution.remainingExceptionPlan();
  }

  private <T> void invokeWithRetry(
      CheckedSupplier<? extends CompletionStage<T>, ?> supplier,
      ScheduledExecutorService retryExecutor,
      CompletableFuture<T> result) {
    try {
      CompletionStage<T> stage = supplier.get();
      stage.handle((v, e) -> {
        if (e == null) result.complete(v);
        else scheduleRetry(getInterestedException(e), retryExecutor, supplier, result);
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
    try {
      maybeRetry.ifPresent(execution -> {
        result.exceptionally(x -> {
          addSuppressedTo(x, e);
          return null;
        });
        @SuppressWarnings("unchecked")  // delay came from upon(), which enforces <? super E>.
        Delay<Throwable> delay = (Delay<Throwable>) execution.strategy();
        Retryer nextRound = new Retryer(execution.remainingExceptionPlan());
        Failable retry = () -> nextRound.invokeWithRetry(supplier, retryExecutor, result);
        delay.asynchronously(e, retry, result::completeExceptionally, retryExecutor);
      });
      maybeRetry.catching(result::completeExceptionally);
    } catch (Throwable unexpected) {
      addSuppressedTo(unexpected, e);
      throw unexpected;
    }
  }

  private static void addSuppressedTo(Throwable exception, Throwable suppressed) {
    if (suppressed instanceof ForReturnValue.ThrownReturn) return;
    if (exception != suppressed) {  // In case user code throws same exception again.
      exception.addSuppressed(suppressed);
    }
  }

  private static Throwable getInterestedException(Throwable exception) {
    if (exception instanceof CompletionException || exception instanceof ExecutionException) {
      return exception.getCause() == null ? exception : exception.getCause();
    }
    return exception;
  }

  private static <T> List<T> copyOf(Stream<? extends T> stream) {
    // Collectors.toList() doesn't guarantee thread-safety.
    Collector<T, ?, ArrayList<T>> collector = Collectors.toCollection(ArrayList::new);
    return stream.collect(collector);
  }

  private static void checkSize(int size) {
    if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
  }

  private static int checkIndex(int index, int size) {
    if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Invalid index: " + index);
    return index;
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
