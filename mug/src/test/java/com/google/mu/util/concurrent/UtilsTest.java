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
import static com.google.mu.util.concurrent.FutureAssertions.assertCauseOf;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.testing.NullPointerTester;

public class UtilsTest {

  @Test public void testMapList_empty() {
    assertThat(Utils.mapList(asList(), Object::toString)).isEmpty();
  }

  @Test public void testMapList_nonEmpty() {
    assertThat(Utils.mapList(asList(1, 2), Object::toString)).containsExactly("1", "2").inOrder();
  }

  @Test public void testNulls() {
    for (Method method : Utils.class.getDeclaredMethods()) {
      if (method.isSynthetic()) continue;
      if (method.getName().equals("cast")) continue;
      if (method.getName().equals("checkState")) continue;
      new NullPointerTester().testMethod(null, method);
    }
  }

  @Test public void testTyped_notOfType() {
    StringCondition condition = Mockito.mock(StringCondition.class);
    assertThat(Utils.typed(String.class, condition::test).test(1)).isFalse();
    verify(condition, never()).test(any(String.class));
  }

  @Test public void testTyped_ofType_false() {
    StringCondition condition = Mockito.mock(StringCondition.class);
    when(condition.test("hi")).thenReturn(false);
    assertThat(Utils.typed(String.class, condition::test).test("hi")).isFalse();
    verify(condition).test("hi");
  }

  @Test public void testTyped_ofType_true() {
    StringCondition condition = Mockito.mock(StringCondition.class);
    when(condition.test("hi")).thenReturn(true);
    assertThat(Utils.typed(String.class, condition::test).test("hi")).isTrue();
    verify(condition).test("hi");
  }

  @Test public void testCast_notAnInstance() {
    assertThat(Utils.cast(1, String.class)).isEqualTo(Optional.empty());
  }

  @Test public void testCast_isAnInstance() {
    assertThat(Utils.cast("hi", String.class)).isEqualTo(Optional.of("hi"));
  }

  @Test public void testCast_null() {
    assertThat(Utils.cast(null, String.class)).isEqualTo(Optional.empty());
    assertThrows(NullPointerException.class, () -> Utils.cast("hi", null));
  }

  @Test public void testTyped_doesNotPassCondition() {
    assertThat(Utils.typed(String.class, x -> true).test(1)).isFalse();
  }

  @Test public void testIfCancelled_pending() {
    AtomicReference<CancellationException> cancelled = new AtomicReference<>();
    CompletableFuture<String> future = new CompletableFuture<>();
    Utils.ifCancelled(future, cancelled::set);
    assertThat(cancelled.get()).isNull();
  }

  @Test public void testIfCancelled_completed() {
    AtomicReference<CancellationException> cancelled = new AtomicReference<>();
    CompletableFuture<String> future = new CompletableFuture<>();
    future.complete("good");
    Utils.ifCancelled(future, cancelled::set);
    assertThat(cancelled.get()).isNull();
  }

  @Test public void testIfCancelled_exception() {
    AtomicReference<CancellationException> cancelled = new AtomicReference<>();
    CompletableFuture<String> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException());
    Utils.ifCancelled(future, cancelled::set);
    assertThat(cancelled.get()).isNull();
  }

  @Test public void testIfCancelled_cancellationException() {
    AtomicReference<CancellationException> cancelled = new AtomicReference<>();
    CompletableFuture<String> future = new CompletableFuture<>();
    CancellationException exception = new CancellationException();
    future.completeExceptionally(exception);
    Utils.ifCancelled(future, cancelled::set);
    assertThat(cancelled.get()).isSameInstanceAs(exception);
  }

  @Test public void testIfCancelled_cancelledWithInterruption() {
    AtomicReference<CancellationException> cancelled = new AtomicReference<>();
    CompletableFuture<String> future = new CompletableFuture<>();
    future.cancel(true);
    Utils.ifCancelled(future, cancelled::set);
    assertThat(cancelled.get()).isInstanceOf(CancellationException.class);
  }

  @Test public void testIfCancelled_cancelledWithoutInterruption() {
    AtomicReference<CancellationException> cancelled = new AtomicReference<>();
    CompletableFuture<String> future = new CompletableFuture<>();
    future.cancel(false);
    Utils.ifCancelled(future, cancelled::set);
    assertThat(cancelled.get()).isInstanceOf(CancellationException.class);
  }

  @Test public void testIfCancelled_callbackExceptionIgnored() {
    CompletableFuture<String> future = new CompletableFuture<>();
    future.cancel(false);
    Utils.ifCancelled(future, e -> {throw new NullPointerException();});
    assertThat(future.isCancelled()).isTrue();
  }

  @Test public void testPropagateCancellation_bothPending() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    assertThat(outer.isCancelled()).isFalse();
    assertThat(inner.isCancelled()).isFalse();
    assertThat(outer.isDone()).isFalse();
    assertThat(inner.isDone()).isFalse();
  }

  @Test public void testPropagateCancellation_cancellationWithInterruptionPropagated() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    outer.cancel(true);
    assertThat(outer.isCancelled()).isTrue();
    assertThat(inner.isCancelled()).isTrue();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isTrue();
    assertThrows(CancellationException.class, inner::get);
  }

  @Test public void testPropagateCancellation_cancellationWithoutInterruptionPropagated() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    outer.cancel(false);
    assertThat(outer.isCancelled()).isTrue();
    assertThat(inner.isCancelled()).isTrue();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isTrue();
    assertThrows(CancellationException.class, inner::get);
  }

  @Test public void testPropagateCancellation_completedResultNotPropagated() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    outer.complete("ok");
    assertThat(outer.isCancelled()).isFalse();
    assertThat(inner.isCancelled()).isFalse();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isFalse();
  }

  @Test public void testPropagateCancellation_exceptionalResultNotPropagated() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    outer.completeExceptionally(new IllegalArgumentException());
    assertThat(outer.isCancelled()).isFalse();
    assertThat(inner.isCancelled()).isFalse();
    assertThat(outer.isCompletedExceptionally()).isTrue();
    assertThat(inner.isCompletedExceptionally()).isFalse();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isFalse();
  }

  @Test public void testPropagateCancellation_innerAlreadyCancelled() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    inner.cancel(false);
    outer.cancel(false);
    assertThat(outer.isCancelled()).isTrue();
    assertThat(inner.isCancelled()).isTrue();
    assertThat(outer.isCompletedExceptionally()).isTrue();
    assertThat(inner.isCompletedExceptionally()).isTrue();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isTrue();
  }

  @Test public void testPropagateCancellation_innerAlreadyCompleted() throws Exception {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    inner.complete("inner");
    outer.cancel(false);
    assertThat(outer.isCancelled()).isTrue();
    assertThat(inner.isCancelled()).isFalse();
    assertThat(outer.isCompletedExceptionally()).isTrue();
    assertThat(inner.isCompletedExceptionally()).isFalse();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isTrue();
    assertThat(inner.get()).isEqualTo("inner");
  }

  @Test public void testPropagateCancellation_innerAlreadyFailed() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = new CompletableFuture<>();
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    IOException exception = new IOException();
    inner.completeExceptionally(exception);
    outer.cancel(false);
    assertThat(outer.isCancelled()).isTrue();
    assertThat(inner.isCancelled()).isFalse();
    assertThat(outer.isCompletedExceptionally()).isTrue();
    assertThat(inner.isCompletedExceptionally()).isTrue();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isTrue();
    assertCauseOf(ExecutionException.class, inner).isSameInstanceAs(exception);
  }

  @Test public void testPropagateCancellation_innerDoesNotSupportToCompletableFuture() {
    CompletableFuture<String> outer = new CompletableFuture<>();
    CompletableFuture<String> inner = Mockito.spy(new CompletableFuture<>());
    assertThat(Utils.propagateCancellation(outer, inner)).isSameInstanceAs(outer);
    Mockito.doThrow(new UnsupportedOperationException()).when(inner).toCompletableFuture();
    outer.cancel(false);
    assertThat(outer.isCancelled()).isTrue();
    assertThat(inner.isCancelled()).isFalse();
    assertThat(outer.isCompletedExceptionally()).isTrue();
    assertThat(inner.isCompletedExceptionally()).isFalse();
    assertThat(outer.isDone()).isTrue();
    assertThat(inner.isDone()).isFalse();
    assertThrows(CancellationException.class, outer::get);
  }

  private interface StringCondition {
    boolean test(String s);
  }
}
