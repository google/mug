package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mu.function.CheckedSupplier;

import com.google.common.truth.ThrowableSubject;
import com.google.common.truth.Truth;

@RunWith(JUnit4.class)
public class RetryerTest {

  @Spy private FakeClock clock;
  @Spy private FakeScheduledExecutorService executor;
  @Mock private Action action;
  private final Retryer.Builder builder = new Retryer.Builder();

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @After public void noMoreInteractions() {
    Mockito.verifyNoMoreInteractions(action);
  }

  @Test public void actionSucceedsFirstTime() throws Exception {
    when(action.run()).thenReturn("good");
    assertThat(retry(action::run).toCompletableFuture().get()).isEqualTo("good");
    verify(action).run();
  }

  @Test public void errorPropagated() throws Exception {
    Error error = new Error("test");
    builder.upon(IOException.class, asList(Duration.ofSeconds(1)));
    when(action.run()).thenThrow(error);
    assertException(Error.class, () -> retry(action::run)).isSameAs(error);
    verify(action).run();
  }

  @Test public void uncheckedExceptionPropagated() throws Exception {
    RuntimeException error = new RuntimeException("test");
    builder.upon(IOException.class, asList(Duration.ofSeconds(1)));
    when(action.run()).thenThrow(error);
    assertException(RuntimeException.class, () -> retry(action::run)).isSameAs(error);
    verify(action).run();
  }

  @Test public void actionFailedButNoRetry() throws Exception {
    IOException exception = new IOException("bad");
    when(action.run()).thenThrow(exception);
    assertCauseOf(ExecutionException.class, () -> retry(action::run).toCompletableFuture().get())
        .isSameAs(exception);
    verify(action).run();
  }

  @Test public void actionFailedAndScheduledForRetry() throws Exception {
    builder.upon(IOException.class, asList(Duration.ofSeconds(1)));
    when(action.run()).thenThrow(new IOException());
    CompletionStage<String> stage = retry(action::run);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofMillis(999));
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    verify(action).run();
  }

  @Test public void actionFailedAndRetriedToSuccess() throws Exception {
    builder.upon(IOException.class, asList(Duration.ofSeconds(1)));
    when(action.run()).thenThrow(new IOException()).thenReturn("fixed");
    CompletionStage<String> stage = retry(action::run);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(1));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
    assertThat(stage.toCompletableFuture().get()).isEqualTo("fixed");
    verify(action, times(2)).run();
  }

  @Test public void errorRetried() throws Exception {
    builder.upon(MyError.class, asList(Duration.ofSeconds(1)));
    when(action.run()).thenThrow(new MyError("test")).thenReturn("fixed");
    CompletionStage<String> stage = retry(action::run);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(1));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
    assertThat(stage.toCompletableFuture().get()).isEqualTo("fixed");
    verify(action, times(2)).run();
  }

  @Test public void uncheckedExceptionRetried() throws Exception {
    builder.upon(RuntimeException.class, asList(Duration.ofSeconds(1)));
    when(action.run()).thenThrow(new RuntimeException("test")).thenReturn("fixed");
    CompletionStage<String> stage = retry(action::run);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(1));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
    assertThat(stage.toCompletableFuture().get()).isEqualTo("fixed");
    verify(action, times(2)).run();
  }

  @Test public void actionFailedAfterRetry() throws Exception {
    builder.upon(IOException.class, asList(Duration.ofSeconds(1)));
    IOException exception = new IOException("hopeless");
    when(action.run()).thenThrow(new IOException()).thenThrow(exception);
    CompletionStage<String> stage = retry(action::run);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(1));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, () -> stage.toCompletableFuture().get())
        .isSameAs(exception);
    verify(action, times(2)).run();
  }

  @Test public void retrialExceedsTime() throws Exception {
    builder.upon(
        IOException.class,
        Retryer.timed(
            Collections.nCopies(100, Duration.ofSeconds(1)), Duration.ofSeconds(3), clock));
    IOException exception = new IOException("hopeless");
    when(action.run()).thenThrow(new IOException()).thenThrow(exception);
    CompletionStage<String> stage = retry(action::run);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(2));
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(1));  // exceeds time
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, () -> stage.toCompletableFuture().get())
        .isSameAs(exception);
    verify(action, times(3)).run();  // Retry twice.
  }

  @Test public void asyncExceptionRetriedToSuccess() throws Exception {
    builder.upon(IOException.class, asList(Duration.ofSeconds(1)));
    when(action.runAsync())
        .thenReturn(exceptionally(new IOException()))
        .thenReturn(CompletableFuture.completedFuture("fixed"));
    CompletionStage<String> stage = retryAsync(action::runAsync);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(1));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isFalse();
    assertThat(stage.toCompletableFuture().get()).isEqualTo("fixed");
    verify(action, times(2)).runAsync();
  }

  @Test public void asyncFailedAfterRetry() throws Exception {
    builder.upon(IOException.class, asList(Duration.ofSeconds(1)));
    IOException exception = new IOException("hopeless");
    when(action.runAsync())
        .thenReturn(exceptionally(new IOException()))
        .thenReturn(exceptionally(exception));
    CompletionStage<String> stage = retryAsync(action::runAsync);
    assertThat(stage.toCompletableFuture().isDone()).isFalse();
    elapse(Duration.ofSeconds(1));
    assertThat(stage.toCompletableFuture().isDone()).isTrue();
    assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    assertCauseOf(ExecutionException.class, () -> stage.toCompletableFuture().get())
        .isSameAs(exception);
    verify(action, times(2)).runAsync();
  }

  @Test public void guardedList() {
    AtomicBoolean guard = new AtomicBoolean(true);
    List<Integer> list = Retryer.guarded(asList(1, 2), guard::get);
    assertThat(list).hasSize(2);
    assertThat(list).isNotEmpty();
    assertThat(list).containsExactly(1, 2);
    guard.set(false);
    assertThat(list).isEmpty();
    guard.set(true);
    assertThat(list).containsExactly(1, 2);
  }

  @Test public void testNulls() {
    assertThrows(NullPointerException.class, () -> builder.build().retry(null, executor));
    assertThrows(NullPointerException.class, () -> builder.build().retry(action::run, null));
    assertThrows(NullPointerException.class, () -> builder.build().retryBlockingly(null));
    assertThrows(NullPointerException.class, () -> builder.build().retryAsync(null, executor));
    assertThrows(
        NullPointerException.class, () -> builder.build().retryAsync(action::runAsync, null));
    assertThrows(NullPointerException.class, () -> Retryer.exponentialBackoff(null, 1, 1));
    assertThrows(NullPointerException.class, () -> Retryer.guarded(null, () -> true));
    assertThrows(NullPointerException.class, () -> Retryer.guarded(asList(), null));
    assertThrows(NullPointerException.class, () -> Retryer.timed(asList(), null));
    assertThrows(NullPointerException.class, () -> Retryer.timed(null, Duration.ofDays(1)));
    assertThrows(
        NullPointerException.class, () -> Retryer.timed(asList(), Duration.ofDays(1), null));
  }

  @Test public void testExponentialBackoff() {
    assertThat(Retryer.exponentialBackoff(Duration.ofDays(1), 2, 3))
        .containsExactly(Duration.ofDays(1), Duration.ofDays(2), Duration.ofDays(4))
        .inOrder();
    assertThat(Retryer.exponentialBackoff(Duration.ofDays(1), 1, 2))
        .containsExactly(Duration.ofDays(1), Duration.ofDays(1))
        .inOrder();
    assertThat(Retryer.exponentialBackoff(Duration.ofDays(1), 1, 0))
        .isEmpty();
    assertThrows(
        IllegalArgumentException.class,
        () -> Retryer.exponentialBackoff(Duration.ofDays(1), 0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> Retryer.exponentialBackoff(Duration.ofDays(1), -1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> Retryer.exponentialBackoff(Duration.ofDays(1), 2, -1));
  }

  @Test public void testFakeScheduledExecutorService_taskScheduledButNotRunYet() {
    Runnable runnable = mock(Runnable.class);
    executor.schedule(runnable, 2, TimeUnit.MILLISECONDS);
    elapse(Duration.ofMillis(1));
    Mockito.verifyZeroInteractions(runnable);
  }

  @Test public void testFakeScheduledExecutorService_taskScheduledAndRun() {
    Runnable runnable = mock(Runnable.class);
    executor.schedule(runnable, 2, TimeUnit.MILLISECONDS);
    elapse(Duration.ofMillis(2));
    verify(runnable).run();
    elapse(Duration.ofMillis(2));
    Mockito.verifyNoMoreInteractions(runnable);
  }

  private static CompletionStage<String> exceptionally(Throwable e) {
    CompletableFuture<String> future = new CompletableFuture<>();
    future.completeExceptionally(e);
    return future;
  }

  private <T> CompletionStage<T> retry(CheckedSupplier<T, ?> supplier) {
    return builder.build().retry(supplier, executor);
  }

  private <T> CompletionStage<T> retryAsync(
      CheckedSupplier<? extends CompletionStage<T>, ?> supplier) {
    return builder.build().retryAsync(supplier, executor);
  }

  private ThrowableSubject assertException(
      Class<? extends Throwable> exceptionType, Executable executable) {
    return Truth.assertThat(Assertions.assertThrows(exceptionType, executable));
  }

  private ThrowableSubject assertCauseOf(
      Class<? extends Throwable> exceptionType, Executable executable) {
    return assertThat(Assertions.assertThrows(exceptionType, executable).getCause());
  }

  private void elapse(Duration duration) {
    clock.elapse(duration);
    executor.tick();
  }

  abstract static class FakeClock extends Clock {
    private Instant now = Instant.ofEpochMilli(123456789L);

    @Override public Instant instant() {
      return now;
    }

    void elapse(Duration duration) {
      now = now.plus(duration);
    }
  }

  abstract class FakeScheduledExecutorService implements ScheduledExecutorService {

    private List<Schedule> schedules = new ArrayList<>();

    void tick() {
      Instant now = clock.instant();
      
      schedules.stream()
          .filter(s -> s.ready(now))
          // The commands can call schedule() to schedule another retry.
          // So if we don't make a copy, we get a ConcurrentModificationException.
          .collect(Collectors.toList())
          .forEach(s -> s.command.run());
      schedules = schedules.stream()
          .filter(s -> s.pending(now))
          .collect(Collectors.toCollection(ArrayList::new));
    }
  
    @Override public void execute(Runnable command) {
      schedule(command, 1, TimeUnit.MILLISECONDS);
    }

    @Override public ScheduledFuture<?> schedule(
        Runnable command, long delay, TimeUnit unit) {
      assertThat(unit).isEqualTo(TimeUnit.MILLISECONDS);
      schedules.add(new Schedule(clock.instant().plus(delay, ChronoUnit.MILLIS), command));
      return null;  // Retryer doesn't use the return.
    }
  }

  private static final class Schedule {
    private final Instant time;
    final Runnable command;

    Schedule(Instant time, Runnable command) {
      this.time = requireNonNull(time);
      this.command = requireNonNull(command);
    }

    boolean ready(Instant now) {
      return !pending(now);
    }

    boolean pending(Instant now) {
      return now.isBefore(time);
    }
  }

  private interface Action {
    String run() throws IOException;
    CompletionStage<String> runAsync() throws IOException;
  }

  @SuppressWarnings("serial")
  private static final class MyError extends Error {
    MyError(String message) {
      super(message);
    }
  }
}
