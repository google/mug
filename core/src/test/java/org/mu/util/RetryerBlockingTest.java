package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mu.util.Retryer.Delay;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.truth.ThrowableSubject;
import com.google.common.truth.Truth;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Retryer.class, Thread.class})
public class RetryerBlockingTest {

  @Mock private Action action;
  private final Delay<Throwable> delay = Mockito.spy(Delay.ofMillis(100));

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
    PowerMockito.mockStatic(Thread.class);
  }

  @Before public void noMoreInteractions() {
    PowerMockito.verifyNoMoreInteractions(Thread.class);
  }

  @Test public void noRetryIfActionSucceedsFirstTime() throws IOException {
    when(action.run()).thenReturn("good");
    new Retryer().retryBlockingly(action::run);
    verify(action).run();
  }

  @Test public void actionFailsButRetryNotConfigured() throws IOException {
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception);
    assertException(IOException.class, () -> new Retryer().retryBlockingly(action::run))
        .isSameAs(exception);
    assertThat(exception.getSuppressed()).isEmpty();
    verify(action).run();
  }

  @Test public void actionFailsButRetryConfiguredForDifferentException() throws IOException {
    Retryer retryer = new Retryer()
        .upon(RuntimeException.class, asList(delay));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception);
    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
    assertThat(exception.getSuppressed()).isEmpty();
    verify(action).run();
    verify(delay, never()).beforeDelay(Matchers.<Throwable>any());
    verify(delay, never()).afterDelay(Matchers.<Throwable>any());
  }

  @Test public void actionFailsWithUncheckedButRetryConfiguredForDifferentException()
      throws IOException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(delay));
    RuntimeException exception = new RuntimeException();
    when(action.run()).thenThrow(exception);
    assertException(RuntimeException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
    assertThat(exception.getSuppressed()).isEmpty();
    verify(action).run();
    verify(delay, never()).beforeDelay(Matchers.<Throwable>any());
    verify(delay, never()).afterDelay(Matchers.<Throwable>any());
  }

  @Test public void exceptionFromBeforeDelayPropagated() throws IOException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(delay));
    RuntimeException unexpected = new RuntimeException();
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception);
    Mockito.doThrow(unexpected).when(delay).beforeDelay(exception);
    assertException(RuntimeException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(unexpected);
    assertThat(asList(unexpected.getSuppressed())).containsExactly(exception);
    verify(action).run();
    verify(delay).beforeDelay(exception);
    verify(delay, never()).afterDelay(exception);
  }

  @Test public void exceptionFromAfterDelayPropgated() throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(delay));
    RuntimeException unexpected = new RuntimeException();
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception);
    Mockito.doThrow(unexpected).when(delay).afterDelay(exception);
    assertException(RuntimeException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(unexpected);
    assertThat(asList(unexpected.getSuppressed())).containsExactly(exception);
    verify(action).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(delay.duration().toMillis());
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void actionFailsThenSucceedsAfterRetry() throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(delay));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(delay.duration().toMillis());
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void actionFailsWithUncheckedThenSucceedsAfterRetry()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(RuntimeException.class, asList(delay));
    RuntimeException exception = new RuntimeException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");

    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(delay.duration().toMillis());
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void actionFailsWithErrorThenSucceedsAfterRetry()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(Error.class, asList(delay));
    Error exception = new Error();
    when(action.run()).thenThrow(exception).thenReturn("fixed");

    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(delay.duration().toMillis());
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void actionFailsEvenAfterRetry()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, Delay.ofMillis(100).exponentialBackoff(10, 2));
    IOException exception1 = new IOException();
    IOException exception2 = new IOException();
    IOException exception = new IOException();
    when(action.run())
        .thenThrow(exception1).thenThrow(exception2).thenThrow(exception);

    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
    assertThat(asList(exception.getSuppressed())).containsExactly(exception1, exception2).inOrder();
    verify(action, times(3)).run();
    PowerMockito.verifyStatic(); Thread.sleep(100);
    PowerMockito.verifyStatic(); Thread.sleep(1000);
  }

  @Test public void sameExceptionNotAddedAsCause()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, Delay.ofMillis(100).exponentialBackoff(10, 2));
    IOException exception1 = new IOException();
    IOException exception2 = new IOException();
    when(action.run())
        .thenThrow(exception1).thenThrow(exception2).thenThrow(exception2);

    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception2);
    assertThat(asList(exception2.getSuppressed())).containsExactly(exception1).inOrder();
    verify(action, times(3)).run();
    PowerMockito.verifyStatic(); Thread.sleep(100);
    PowerMockito.verifyStatic(); Thread.sleep(1000);
  }

  @Test public void interruptedDuringRetry() throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, Delay.ofMillis(100).exponentialBackoff(10, 1));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception);
    PowerMockito.doThrow(new InterruptedException()).when(Thread.class); Thread.sleep(100);
    Thread thread = PowerMockito.mock(Thread.class);
    PowerMockito.doReturn(thread).when(Thread.class); Thread.currentThread();

    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
    assertThat(exception.getSuppressed()).isEmpty();
    verify(action).run();
    verify(thread).interrupt();
    PowerMockito.verifyStatic(); Thread.sleep(100);
  }

  private ThrowableSubject assertException(
      Class<? extends Throwable> exceptionType, Executable executable) {
    Throwable exception = Assertions.assertThrows(exceptionType, executable);
    return Truth.assertThat(exception);
  }

  private interface Action {
    String run() throws IOException;
  }
}
