package org.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
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
import org.mockito.Mock;
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
    verify(action).run();
  }

  @Test public void actionFailsButRetryConfiguredForDifferentException() throws IOException {
    Retryer retryer = new Retryer()
        .upon(RuntimeException.class, asList(Delay.ofMillis(100)));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception);
    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
    verify(action).run();
  }

  @Test public void actionFailsWithUncheckedButRetryConfiguredForDifferentException()
      throws IOException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(Delay.ofMillis(100)));
    RuntimeException exception = new RuntimeException();
    when(action.run()).thenThrow(exception);
    assertException(RuntimeException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
    verify(action).run();
  }

  @Test public void actionFailsThenSucceedsAfterRetry() throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(Delay.ofMillis(100)));
    IOException exception = new IOException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(100);
  }

  @Test public void actionFailsWithUncheckedThenSucceedsAfterRetry()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(RuntimeException.class, asList(Delay.ofMillis(100)));
    RuntimeException exception = new RuntimeException();
    when(action.run()).thenThrow(exception).thenReturn("fixed");

    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(100);
  }

  @Test public void actionFailsWithErrorThenSucceedsAfterRetry()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(Error.class, asList(Delay.ofMillis(100)));
    Error exception = new Error();
    when(action.run()).thenThrow(exception).thenReturn("fixed");

    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(100);
  }

  @Test public void actionFailsEvenAfterRetry()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, Delay.ofMillis(100).exponentialBackoff(10, 2));
    IOException exception = new IOException();
    when(action.run())
        .thenThrow(new IOException()).thenThrow(new IOException()).thenThrow(exception);

    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
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
