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

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.concurrent.FutureAssertions.assertAfterCompleted;
import static com.google.mu.util.concurrent.FutureAssertions.assertCancelled;
import static com.google.mu.util.concurrent.FutureAssertions.assertPending;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.google.mu.util.concurrent.Retryer.Delay;

/** These tests run against real executor and real {@link Thread}. */
public class RetryerFunctionalTest {
  private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
  private Retryer retryer = new Retryer();
  @Mock private Action action;
  @Spy private BlockedAction blockedAction;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @After public void shutDownExecutor() {
    executor.shutdown();
    Thread.interrupted();  // clear interruption.
  }

  @Test public void retryBlockingly() throws Exception {
    Delay<Throwable> delay = Delay.ofMillis(1);
    upon(IOException.class, asList(delay));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
  }

  @Test public void retryBlockinglyWithZeroDelayIsOkayWithJdk() throws Exception {
    Delay<Throwable> delay = spy(ofSeconds(0));
    upon(IOException.class, asList(delay));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void retry() throws Exception {
    Delay<Throwable> delay = Delay.ofMillis(1);
    upon(IOException.class, asList(delay));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");
    assertAfterCompleted(retryer.retry(action::run, executor)).isEqualTo("fixed");
    verify(action, times(2)).run();
  }

  @Test public void retryWithZeroDelayIsOkayWithJdk() throws Exception {
    Delay<Throwable> delay = spy(ofSeconds(0));
    upon(IOException.class, asList(delay));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");
    assertAfterCompleted(retryer.retry(action::run, executor)).isEqualTo("fixed");
    verify(action, times(2)).run();
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void returnValueRetryBlockingly() throws Exception {
    Delay<String> delay = Delay.ofMillis(1);
    Retryer.ForReturnValue<String> forReturnValue = retryer.uponReturn("bad", asList(delay));
    when(action.run()).thenReturn("bad").thenReturn("fixed");
    assertThat(forReturnValue.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
  }

  @Test public void returnValueRetryAttemptCancelled() throws Exception {
    Delay<String> delay = spy(ofSeconds(1));
    Retryer.ForReturnValue<String> forReturnValue = retryer.uponReturn("bad", asList(delay));
    when(blockedAction.result()).thenReturn("bad").thenReturn("fixed");
    CompletionStage<String> stage =
        forReturnValue.retry(blockedAction::blockOnSecondTime, executor);
    assertPending(stage);
    blockedAction.readyToRetry.await();
    assertPending(stage);
    stage.toCompletableFuture().cancel(true);
    assertCancelled(stage);
    verify(blockedAction).result();
    verify(delay).beforeDelay("bad");
    verify(delay, never()).afterDelay(any(String.class));
  }

  @Test public void returnValueRetryBlockinglyWithZeroDelayIsOkayWithJdk() throws Exception {
    Delay<String> delay = spy(ofSeconds(0));
    Retryer.ForReturnValue<String> forReturnValue = retryer.uponReturn("bad", asList(delay));
    when(action.run()).thenReturn("bad").thenReturn("fixed");
    assertThat(forReturnValue.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    verify(delay).beforeDelay("bad");
    verify(delay).afterDelay("bad");
  }

  @Test public void returnValueRetry() throws Exception {
    Delay<String> delay = Delay.ofMillis(1);
    Retryer.ForReturnValue<String> forReturnValue = retryer.uponReturn("bad", asList(delay));
    when(action.run()).thenReturn("bad").thenReturn("fixed");
    assertAfterCompleted(forReturnValue.retry(action::run, executor)).isEqualTo("fixed");
    verify(action, times(2)).run();
  }

  @Test public void returnValueRetryWithZeroDelayIsOkayWithJdk() throws Exception {
    Delay<String> delay = spy(ofSeconds(0));
    Retryer.ForReturnValue<String> forReturnValue = retryer.uponReturn("bad", asList(delay));
    when(action.run()).thenReturn("bad").thenReturn("fixed");
    assertAfterCompleted(forReturnValue.retry(action::run, executor)).isEqualTo("fixed");
    verify(action, times(2)).run();
    verify(delay).beforeDelay("bad");
    verify(delay).afterDelay("bad");
  }

  @Test public void interruptedBeforeRetry() {
    Thread.currentThread().interrupt();
    CancellationException cancelled =
        assertThrows(CancellationException.class, () -> retryer.retry(this::hibernate, executor));
    assertThat(cancelled.getSuppressed()).isEmpty();
    assertThat(cancelled.getCause()).isInstanceOf(InterruptedException.class);
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test public void interruptedDespiteRetry() {
    upon(Exception.class, asList(Delay.ofMillis(0)));
    Thread.currentThread().interrupt();
    CancellationException cancelled =
        assertThrows(CancellationException.class, () -> retryer.retry(this::hibernate, executor));
    assertThat(cancelled.getCause()).isInstanceOf(InterruptedException.class);
    assertThat(cancelled.getSuppressed()).isEmpty();
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test public void interruptedDespiteRetryOnReturnvalue() {
    Retryer.ForReturnValue<String> forReturnValue =
        retryer.uponReturn("bad", asList(Delay.ofMillis(0)));
    Thread.currentThread().interrupt();
    CancellationException cancelled = assertThrows(
        CancellationException.class, () -> forReturnValue.retry(this::hibernate, executor));
    assertThat(cancelled.getCause()).isInstanceOf(InterruptedException.class);
    assertThat(cancelled.getSuppressed()).isEmpty();
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test public void interruptedDuringRetry() throws InterruptedException, IOException {
    IOException exception = new IOException();
    upon(Exception.class, asList(Delay.ofMillis(0), Delay.ofMillis(0)));
    when(blockedAction.result()).thenThrow(exception).thenReturn("fixed");
    CompletionStage<String> stage = retryer.retry(blockedAction::blockOnSecondTime, executor);
    blockedAction.retryStarted.await();
    blockedAction.interrupt();
    CancellationException cancelled =
        assertThrows(CancellationException.class, () -> stage.toCompletableFuture().get());
    assertThat(cancelled.getCause()).isInstanceOf(InterruptedException.class);
    assertThat(cancelled.getSuppressed()).asList().containsExactly(exception);
  }

  @Test public void interruptedDuringReturnValueRetryRetry()
      throws InterruptedException, IOException {
    Retryer.ForReturnValue<String> forReturnValue =
        retryer.uponReturn("bad", asList(Delay.ofMillis(0), Delay.ofMillis(0)));
    when(blockedAction.result()).thenReturn("bad").thenReturn("fixed");
    CompletionStage<String> stage =
        forReturnValue.retry(blockedAction::blockOnSecondTime, executor);
    blockedAction.retryStarted.await();
    blockedAction.interrupt();
    // Sadly cancellation from inner future doesn't propagate to outer.
    ExecutionException exception =
        assertThrows(ExecutionException.class, () -> stage.toCompletableFuture().get());
    assertThat(exception.getCause()).isInstanceOf(CancellationException.class);
    assertThat(exception.getCause().getCause()).isInstanceOf(InterruptedException.class);
    assertThat(exception.getCause().getSuppressed()).isEmpty();
  }

  private <E extends Throwable> void upon(
      Class<E> exceptionType, List<? extends Delay<? super E>> delays) {
    retryer = retryer.upon(exceptionType, delays);
  }

  private static <E> Delay<E> ofSeconds(long seconds) {
    return new SpyableDelay<>(Duration.ofSeconds(seconds));
  }

  private String hibernate() throws InterruptedException {
    new CountDownLatch(1).await();
    throw new AssertionError("Should have never reached here.");
  }

  static abstract class BlockedAction {
    final CountDownLatch readyToRetry = new CountDownLatch(1);
    final CountDownLatch retryStarted = new CountDownLatch(2);
    private final Semaphore blockOnSecondTime = new Semaphore(1);
    private volatile Thread blockingThread = null;

    final String blockOnSecondTime() throws InterruptedException, IOException {
      blockingThread = Thread.currentThread();
      retryStarted.countDown();
      blockOnSecondTime.acquire();
      readyToRetry.countDown();
      return result();
    }

    final void interrupt() {
      blockingThread.interrupt();
    }

    abstract String result() throws IOException;
  }

  private interface Action {
    String run() throws IOException;
    CompletionStage<String> runAsync() throws IOException;
  }
}
