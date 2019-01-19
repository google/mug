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
import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.truth.ThrowableSubject;
import com.google.common.truth.Truth;
import com.google.mu.util.concurrent.Retryer.Delay;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Thread.class, Retryer.Delay.class})
public class RetryerBlockingTest {

  @Mock private Action action;
  @Mock private Interruptible interruptible;
  private final Delay<Object> delay = Mockito.spy(new SpyableDelay<>(Duration.ofMillis(100)));

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
    PowerMockito.mockStatic(Thread.class);
  }

  @Before public void noMoreInteractions() {
    PowerMockito.verifyNoMoreInteractions(Thread.class);
  }

  @Test public void noRetryIfReturnValueIsGoodFirstTime() throws IOException {
    when(action.run()).thenReturn("good");
    Retryer retryer = new Retryer();
    assertThat(retryer.uponReturn("bad", asList(delay)).retryBlockingly(action::run))
        .isEqualTo("good");
    verify(action).run();
  }

  @Test public void exceptionFromBeforeDelayPropagatedDuringReturnValueRetry() throws IOException {
    Retryer.ForReturnValue<String> retryer = new Retryer().uponReturn("bad", asList(delay));
    RuntimeException unexpected = new RuntimeException();
    when(action.run()).thenReturn("bad");
    Mockito.doThrow(unexpected).when(delay).beforeDelay("bad");
    assertException(RuntimeException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(unexpected);
    assertThat(unexpected.getSuppressed()).isEmpty();
    verify(action).run();
    verify(delay).beforeDelay("bad");
    verify(delay, never()).afterDelay(any());
  }

  @Test public void exceptionFromAfterDelayPropgatedDuringReturnValueRetry()
      throws IOException, InterruptedException {
    Retryer.ForReturnValue<String> retryer = new Retryer().uponReturn("bad", asList(delay));
    RuntimeException unexpected = new RuntimeException();
    when(action.run()).thenReturn("bad");
    Mockito.doThrow(unexpected).when(delay).afterDelay("bad");
    assertException(RuntimeException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(unexpected);
    assertThat(unexpected.getSuppressed()).isEmpty();
    verify(action).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(delay.duration().toMillis());
    verify(delay).beforeDelay("bad");
    verify(delay).afterDelay("bad");
  }

  @Test public void returnValueChangesToExpectedAfterRetry()
      throws IOException, InterruptedException {
    Retryer.ForReturnValue<String> retryer = new Retryer().uponReturn("bad", asList(delay));
    when(action.run()).thenReturn("bad").thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(2)).run();
    PowerMockito.verifyStatic(only()); Thread.sleep(delay.duration().toMillis());
    verify(delay).beforeDelay("bad");
    verify(delay).afterDelay("bad");
  }

  @Test public void returnValueStillBadEvenAfterRetry()
      throws IOException, InterruptedException {
    Retryer.ForReturnValue<String> retryer =
        new Retryer().uponReturn("bad", Delay.ofMillis(100).exponentialBackoff(10, 2));
    when(action.run()).thenReturn("bad");

    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("bad");
    verify(action, times(3)).run();
    PowerMockito.verifyStatic(); Thread.sleep(100);
    PowerMockito.verifyStatic(); Thread.sleep(1000);
  }

  @Test public void interruptedDuringReturnValueRetry() throws IOException, InterruptedException {
    Retryer.ForReturnValue<String> retryer = new Retryer()
        .uponReturn("bad", Delay.ofMillis(100).exponentialBackoff(10, 1));
    when(action.run()).thenReturn("bad");
    PowerMockito.doThrow(new InterruptedException()).when(Thread.class); Thread.sleep(100);
    Thread thread = PowerMockito.mock(Thread.class);
    PowerMockito.doReturn(thread).when(Thread.class); Thread.currentThread();

    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("bad");
    verify(action).run();
    verify(thread).interrupt();
    PowerMockito.verifyStatic(); Thread.sleep(100);
  }

  @Test public void noRetryIfActionSucceedsFirstTime() throws IOException {
    when(action.run()).thenReturn("good");
    assertThat(new Retryer().retryBlockingly(action::run)).isEqualTo("good");
    verify(action).run();
  }

  @Test public void notIntrrupted() throws InterruptedException {
    when(interruptible.compute()).thenReturn("good");
    new Retryer().retryBlockingly(interruptible::compute);
    verify(interruptible).compute();
  }

  @Test public void interruptedExceptionNotRetried()
      throws InterruptedException {
    Retryer retryer = new Retryer().upon(Exception.class, asList(delay));
    InterruptedException exception = new InterruptedException();
    when(interruptible.compute()).thenThrow(exception);
    assertException(
            InterruptedException.class, () -> retryer.retryBlockingly(interruptible::compute))
        .isSameAs(exception);
    assertThat(exception.getSuppressed()).isEmpty();
    verify(delay, never()).beforeDelay(any());
    verify(delay, never()).afterDelay(any());
  }

  @Test public void interruptedTheSecondTimeNotRetriedButCarriesSuppressed()
      throws InterruptedException {
    Retryer retryer = new Retryer().upon(Exception.class, asList(delay, delay));
    RuntimeException exception1 = new RuntimeException();
    InterruptedException exception = new InterruptedException();
    when(interruptible.compute()).thenThrow(exception1).thenThrow(exception);
    assertException(
            InterruptedException.class, () -> retryer.retryBlockingly(interruptible::compute))
        .isSameAs(exception);
    assertThat(exception.getSuppressed()).asList().containsExactly(exception1);
    verify(delay).beforeDelay(exception1);
    verify(delay).afterDelay(exception1);
    verify(delay, never()).beforeDelay(exception);
    verify(delay, never()).afterDelay(exception);
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
    verify(delay, never()).beforeDelay(any());
    verify(delay, never()).afterDelay(any());
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
    verify(delay, never()).beforeDelay(any());
    verify(delay, never()).afterDelay(any());
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
    assertThat(unexpected.getSuppressed()).asList().containsExactly(exception);
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
    assertThat(unexpected.getSuppressed()).asList().containsExactly(exception);
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
    assertThat(exception.getSuppressed()).asList().containsExactly(exception1, exception2).inOrder();
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
    assertThat(exception2.getSuppressed()).asList().containsExactly(exception1).inOrder();
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

  @Test public void twoDifferentExceptionRulesRetriedToSuccess()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(delay))
        .upon(MyUncheckedException.class, asList(delay, delay));
    MyUncheckedException unchecked = new MyUncheckedException();
    IOException exception = new IOException();
    when(action.run())
        .thenThrow(unchecked).thenThrow(exception).thenThrow(unchecked).thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(4)).run();
    PowerMockito.verifyStatic(times(3)); Thread.sleep(delay.duration().toMillis());
    verify(delay, times(2)).beforeDelay(unchecked);
    verify(delay, times(2)).afterDelay(unchecked);
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void twoDifferentExceptionRulesRetrialFailed()
      throws IOException, InterruptedException {
    Retryer retryer = new Retryer()
        .upon(IOException.class, asList(delay))
        .upon(MyUncheckedException.class, asList(delay, delay));
    MyUncheckedException unchecked1 = new MyUncheckedException();
    IOException exception2 = new IOException();
    MyUncheckedException unchecked3 = new MyUncheckedException();
    IOException exception4 = new IOException();
    when(action.run())
        .thenThrow(unchecked1).thenThrow(exception2).thenThrow(unchecked3).thenThrow(exception4);
    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception4);
    assertThat(exception4.getSuppressed()).asList()
        .containsExactly(unchecked1, exception2, unchecked3).inOrder();
    verify(action, times(4)).run();
    PowerMockito.verifyStatic(times(3)); Thread.sleep(delay.duration().toMillis());
    verify(delay).beforeDelay(unchecked1);
    verify(delay).afterDelay(unchecked1);
    verify(delay).beforeDelay(exception2);
    verify(delay).afterDelay(exception2);
    verify(delay).beforeDelay(unchecked3);
    verify(delay).afterDelay(unchecked3);
  }

  @Test public void returnValueAndExceptionRetryToSuccess()
      throws IOException, InterruptedException {
    Retryer.ForReturnValue<String> retryer = new Retryer()
        .upon(IOException.class, asList(delay))
        .uponReturn("bad", asList(delay, delay));
    IOException exception = new IOException();
    when(action.run())
        .thenReturn("bad").thenThrow(exception).thenReturn("bad").thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("fixed");
    verify(action, times(4)).run();
    PowerMockito.verifyStatic(times(3)); Thread.sleep(delay.duration().toMillis());
    verify(delay, times(2)).beforeDelay("bad");
    verify(delay, times(2)).afterDelay("bad");
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void returnValueAndExceptionRetryStillReturnsBad()
      throws IOException, InterruptedException {
    Retryer.ForReturnValue<String> retryer = new Retryer()
        .upon(IOException.class, asList(delay))
        .uponReturn("bad", asList(delay, delay));
    IOException exception = new IOException();
    when(action.run())
        .thenReturn("bad").thenThrow(exception).thenReturn("bad").thenReturn("bad")
        .thenReturn("fixed");
    assertThat(retryer.retryBlockingly(action::run)).isEqualTo("bad");
    verify(action, times(4)).run();
    PowerMockito.verifyStatic(times(3)); Thread.sleep(delay.duration().toMillis());
    verify(delay, times(2)).beforeDelay("bad");
    verify(delay, times(2)).afterDelay("bad");
    verify(delay).beforeDelay(exception);
    verify(delay).afterDelay(exception);
  }

  @Test public void returnValueAndExceptionRetryStillThrows()
      throws IOException, InterruptedException {
    Retryer.ForReturnValue<String> retryer = new Retryer()
        .upon(IOException.class, asList(delay))
        .uponReturn("bad", asList(delay, delay));
    IOException exception1 = new IOException();
    IOException exception = new IOException();
    when(action.run())
        .thenReturn("bad").thenThrow(exception1).thenReturn("bad").thenThrow(exception)
        .thenReturn("fixed");
    assertException(IOException.class, () -> retryer.retryBlockingly(action::run))
        .isSameAs(exception);
    assertThat(exception.getSuppressed()).asList().containsExactly(exception1);
    assertThat(exception.getCause()).isNull();
    assertThat(exception1.getSuppressed()).isEmpty();
    assertThat(exception1.getCause()).isNull();
    verify(action, times(4)).run();
    PowerMockito.verifyStatic(times(3)); Thread.sleep(delay.duration().toMillis());
    verify(delay, times(2)).beforeDelay("bad");
    verify(delay, times(2)).afterDelay("bad");
    verify(delay).beforeDelay(exception1);
    verify(delay).afterDelay(exception1);
  }

  private static ThrowableSubject assertException(
      Class<? extends Throwable> exceptionType, Executable executable) {
    Throwable exception = Assertions.assertThrows(exceptionType, executable);
    return Truth.assertThat(exception);
  }

  private interface Action {
    String run() throws IOException;
  }

  private interface Interruptible {
    String compute() throws InterruptedException;
  }

  @SuppressWarnings("serial")
  private static class MyUncheckedException extends RuntimeException {}
}
