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

import static com.google.mu.util.concurrent.Utils.ifCancelled;
import static com.google.mu.util.concurrent.Utils.mapList;
import static com.google.mu.util.concurrent.Utils.propagateCancellation;
import static com.google.mu.util.concurrent.Utils.propagateIfUnchecked;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.mu.function.CheckedSupplier;
import com.google.mu.util.Maybe;

/**
 * Immutable object that retries actions upon exceptions.
 *
 * <p>Backoff intervals are configured through chaining {@link #upon upon()} calls. It's critical
 * to use the new {@code Retryer} instances returned by {@code upon()}. Just remember
 * {@code Retryer} is <em>immutable</em>.
 *
 * <p>If the retried operation still fails after retry, the previous exceptions can be accessed
 * through {@link Throwable#getSuppressed()}.
 *
 * @since 1.2
 */
public final class Retryer {

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
   *
   * <p>{@link InterruptedException} is always considered a request to stop retrying. Calling
   * {@code upon(InterruptedException.class, ...)} is illegal.
   */
  public final <E extends Throwable> Retryer upon(
      Class<E> exceptionType, List<? extends Delay<? super E>> delays) {
    return new Retryer(plan.upon(rejectInterruptedException(exceptionType), delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType}.
   *
   * <p>{@link InterruptedException} is always considered a request to stop retrying. Calling
   * {@code upon(InterruptedException.class, ...)} is illegal.
   */
  public final <E extends Throwable> Retryer upon(
      Class<E> exceptionType, Stream<? extends Delay<? super E>> delays) {
    return upon(exceptionType, copyOf(delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType} and satisfies {@code condition}.
   *
   * <p>{@link InterruptedException} is always considered a request to stop retrying. Calling
   * {@code upon(InterruptedException.class, ...)} is illegal.
   */
  public <E extends Throwable> Retryer upon(
      Class<E> exceptionType, Predicate<? super E> condition,
      List<? extends Delay<? super E>> delays) {
    return new Retryer(plan.upon(rejectInterruptedException(exceptionType), condition, delays));
  }

  /**
   * Returns a new {@code Retryer} that uses {@code delays} when an exception is instance of
   * {@code exceptionType} and satisfies {@code condition}.
   *
   * <p>{@link InterruptedException} is always considered a request to stop retrying. Calling
   * {@code upon(InterruptedException.class, ...)} is illegal.
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
          if (e instanceof InterruptedException) throw e;
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
   * <p>Canceling the returned future object will cancel currently pending retry attempts. Same
   * if {@code supplier} throws {@link InterruptedException}.
   *
   * <p>NOTE that if {@code executor.shutdownNow()} is called, the returned {@link CompletionStage}
   * will never be done.
   */
  public <T> CompletionStage<T> retry(
      CheckedSupplier<T, ?> supplier, ScheduledExecutorService executor) {
    return retryAsync(supplier.andThen(CompletableFuture::completedFuture), executor);
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
   * <p>Canceling the returned future object will cancel currently pending retry attempts. Same
   * if {@code supplier} throws {@link InterruptedException}.
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
    return ifReturns(condition, copyOf(delays));
  }

  /**
   * Returns a new object that retries if the function returns {@code returnValue}.
   *
   * @param returnValue The nullable return value that triggers retry
   * @param delays specify the backoffs between retries
   */
  public <T> ForReturnValue<T> uponReturn(
      T returnValue, Stream<? extends Delay<? super T>> delays) {
    return uponReturn(returnValue, copyOf(delays));
  }

  /**
   * Returns a new object that retries if the function returns {@code returnValue}.
   *
   * @param returnValue The nullable return value that triggers retry
   * @param delays specify the backoffs between retries
   */
  public <T> ForReturnValue<T> uponReturn(
      T returnValue, List<? extends Delay<? super T>> delays) {
    return ifReturns(r -> Objects.equals(r, returnValue), delays);
  }

  /** @deprecated please use {@link com.google.mu.util.concurrent.Retryer.ForReturnValue}. */
  @Deprecated public static final class ForReturnValue<T> {
    private final Retryer retryer;
    private final Predicate<? super T> condition;

    ForReturnValue(
        Retryer retryer,
        Predicate<? super T> condition, List<? extends Delay<? super T>> delays) {
      this.condition = requireNonNull(condition);
      this.retryer = retryer.upon(
          ThrownReturn.class,
          // Safe because it's essentially ThrownReturn<T> and Delay<? super T>.
          mapList(delays, d -> d.forEvents(ThrownReturn::unsafeGet)));
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
      return ThrownReturn.<R, E>unwrap(() -> retryer.retryBlockingly(supplier.andThen(this::wrap)));
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
     * <p>Canceling the returned future object will cancel currently pending retry attempts. Same
     * if {@code supplier} throws {@link InterruptedException}.
     *
     * <p>NOTE that if {@code executor.shutdownNow()} is called, the returned
     * {@link CompletionStage} will never be done.
     */
    public <R extends T, E extends Throwable> CompletionStage<R> retry(
        CheckedSupplier<? extends R, E> supplier,
        ScheduledExecutorService retryExecutor) {
      return ThrownReturn.unwrapAsync(() -> retryer.retry(supplier.andThen(this::wrap), retryExecutor));
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
     * <p>Canceling the returned future object will cancel currently pending retry attempts. Same
     * if {@code supplier} throws {@link InterruptedException}.
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
        CompletionStage<T> outer = Maybe.catchException(ThrownReturn.class, stage)
            .thenApply(maybe -> maybe.orElse(ThrownReturn::unsafeGet));
        propagateCancellation(outer, stage);
        return outer;
      }

      /** Exception cannot be parameterized. But we essentially use it as ThrownReturn<T>. */
      @SuppressWarnings("unchecked")
      private <T> T unsafeGet() {
        return (T) returnValue;
      }
    }
  }

  /** @deprecated please use {@link com.google.mu.util.concurrent.Retryer.ForReturnValue}. */
  @Deprecated public static abstract class Delay<E> implements Comparable<Delay<E>> {

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
      if (checkSize(size) == 0) return Collections.emptyList();
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
      return ofMillis(Math.round(Math.ceil(millis)));
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
      requireNonNull(random);
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
      if (checkSize(size) == 0) return Collections.emptyList();
      return new AbstractList<Delay<E>>() {
        @Override public Delay<E> get(int index) {
          return ofMillis(Math.round(fib(checkIndex(index, size) + 1) * duration().toMillis()));
        }
        @Override public int size() {
          return size;
        }
      };
    }

    /** Called if {@code event} will be retried after the delay. Logs the event by default. */
    public void beforeDelay(E event) {
      logger.info(event + ": will retry after " + duration());
    }

    /** Called after the delay, immediately before the retry. Logs the event by default. */
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
        E event, Failable retry, ScheduledExecutorService executor, CompletableFuture<?> result) {
      beforeDelay(event);
      Failable afterDelay = () -> {
        afterDelay(event);
        retry.run();
      };
      ScheduledFuture<?> scheduled = executor.schedule(
          () -> afterDelay.run(result::completeExceptionally),
          duration().toMillis(), TimeUnit.MILLISECONDS);
      ifCancelled(result, canceled -> {scheduled.cancel(true);});
    }

    /**
     * Returns an adapter of {@code this} as type {@code F}, which uses {@code eventTranslator} to
     * translate events to type {@code E} before accepting them.
     */
    final <F> Delay<F> forEvents(Function<F, ? extends E> eventTranslator) {
      requireNonNull(eventTranslator);
      Delay<E> delegate = this;
      return new Delay<F>() {
        @Override public Duration duration() {
          return delegate.duration();
        }
        @Override public void beforeDelay(F from) {
          delegate.beforeDelay(eventTranslator.apply(from));
        }
        @Override public void afterDelay(F from) {
          delegate.afterDelay(eventTranslator.apply(from));
        }
        @Override void interrupted(F from) {
          delegate.interrupted(eventTranslator.apply(from));
        }
      };
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
    ExceptionPlan.Execution<Delay<?>> execution = plan.execute(exception).orElseThrow(identity());
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
      CompletableFuture<T> future) {
    if (future.isDone()) return;  // like, canceled before retrying.
    try {
      CompletionStage<T> stage = supplier.get();
      stage.handle((v, e) -> {
        if (e == null) future.complete(v);
        else scheduleRetry(getInterestedException(e), retryExecutor, supplier, future);
        return null;
      });
    } catch (RuntimeException e) {
      retryIfCovered(e, retryExecutor, supplier, future);
    } catch (Error e) {
      retryIfCovered(e, retryExecutor, supplier, future);
    } catch (Throwable e) {
      if (e instanceof InterruptedException) {
        CancellationException cancelled = new CancellationException();
        cancelled.initCause(e);
        Thread.currentThread().interrupt();
        // Don't even attempt to retry, even if user explicitly asked to retry on Exception
        // This is because we treat InterruptedException specially as a signal to stop.
        throw cancelled;
      }
      scheduleRetry(e, retryExecutor, supplier, future);
    }
  }

  private <E extends Throwable, T> void retryIfCovered(
      E e, ScheduledExecutorService retryExecutor,
      CheckedSupplier<? extends CompletionStage<T>, ?> supplier, CompletableFuture<T> future)
          throws E {
    if (plan.covers(e)) {
      scheduleRetry(e, retryExecutor, supplier, future);
    } else {
      throw e;
    }
  }

  private <T> void scheduleRetry(
      Throwable e, ScheduledExecutorService retryExecutor,
      CheckedSupplier<? extends CompletionStage<T>, ?> supplier, CompletableFuture<T> future) {
    try {
      Maybe<ExceptionPlan.Execution<Delay<?>>, ?> maybeRetry = plan.execute(e);
      maybeRetry.ifPresent(execution -> {
        future.exceptionally(x -> {
          addSuppressedTo(x, e);
          return null;
        });
        if (future.isDone()) return;  // like, canceled immediately before scheduling.
        @SuppressWarnings("unchecked")  // delay came from upon(), which enforces <? super E>.
        Delay<Throwable> delay = (Delay<Throwable>) execution.strategy();
        Retryer nextRound = new Retryer(execution.remainingExceptionPlan());
        Failable retry = () -> nextRound.invokeWithRetry(supplier, retryExecutor, future);
        delay.asynchronously(e, retry, retryExecutor, future);
      });
      maybeRetry.catching(future::completeExceptionally);
    } catch (Throwable unexpected) {
      addSuppressedTo(unexpected, e);
      throw unexpected;
    }
  }

  private static <E extends Throwable> Class<E> rejectInterruptedException(Class<E> exceptionType) {
    if (InterruptedException.class.isAssignableFrom(exceptionType)) {
      throw new IllegalArgumentException("Cannot retry on InterruptedException.");
    }
    return exceptionType;
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
    return stream.collect(Collectors.toCollection(ArrayList::new));
  }

  private static int checkSize(int size) {
    if (size < 0) throw new IllegalArgumentException("Invalid size: " + size);
    return size;
  }

  private static int checkIndex(int index, int size) {
    if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Invalid index: " + index);
    return index;
  }

  @FunctionalInterface private interface Failable {
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
