package com.google.mu.util.concurrent;


import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.concurrent.Fanout.concurrently;
import static com.google.mu.util.concurrent.Fanout.uninterruptibly;
import static com.google.mu.util.concurrent.Fanout.withMaxConcurrency;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FanoutTest {
  @Test
  public void concurrently_twoOperations() {
    assertThat(concurrently(() -> "foo", () -> "bar", String::concat)).isEqualTo("foobar");
  }

  @Test
  public void concurrently_threeOperations() {
    assertThat(
            concurrently(
                () -> "a", () -> "b", () -> "c", (String a, String b, String c) -> a + b + c))
        .isEqualTo("abc");
  }

  @Test
  public void concurrently_fourOperations() {
    assertThat(
            concurrently(
                () -> "a",
                () -> "b",
                () -> "c",
                () -> "d",
                (String a, String b, String c, String d) -> a + b + c + d))
        .isEqualTo("abcd");
  }

  @Test
  public void concurrently_fiveOperations() {
    assertThat(
            concurrently(
                () -> "a",
                () -> "b",
                () -> "c",
                () -> "d",
                () -> "e",
                (String a, String b, String c, String d, String e) -> a + b + c + d + e))
        .isEqualTo("abcde");
  }

  @Test
  public void concurrently_sideEffectsAreSafe() {
    String[] arm = new String[1];
    String[] leg = new String[1];
    assertThat(
            concurrently(
                () -> {
                  arm[0] = "arm";
                  return 1;
                },
                () -> {
                  leg[0] = "leg";
                  return 2;
                },
                (Integer a, Integer b) -> a + b))
        .isEqualTo(3);
    assertThat(arm[0]).isEqualTo("arm");
    assertThat(leg[0]).isEqualTo("leg");
  }

  @Test
  public void concurrently_interruptionPropagated() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean operationInterrupted = new AtomicBoolean();
    AtomicBoolean interruptionSwallowed = new AtomicBoolean();
    AtomicBoolean interruptionPropagated = new AtomicBoolean();
    Thread thread = new Thread(() -> {
      try {
      concurrently(
          () -> {},
          () -> {
            try {
              latch.await();
            } catch (InterruptedException e) {
              operationInterrupted.set(true);
            }
          });
      } catch (StructuredConcurrencyInterruptedException e) {
        interruptionSwallowed.set(true);
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        interruptionPropagated.set(true);
      }
    });
    thread.start();
    Thread.sleep(100);
    assertThat(thread.isAlive()).isTrue();
    assertThat(thread.isInterrupted()).isFalse();
    thread.interrupt();
    thread.join();
    assertThat(operationInterrupted.get()).isTrue();
    assertThat(interruptionSwallowed.get()).isTrue();
    assertThat(interruptionPropagated.get()).isTrue();
  }

  @Test
  public void concurrently_twoTasks() {
    String[] results = new String[2];
    concurrently(() -> results[0] = "foo", () -> results[1] = "bar");
    assertThat(asList(results)).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void concurrently_threeTasks() {
    String[] results = new String[3];
    concurrently(() -> results[0] = "a", () -> results[1] = "b", () -> results[2] = "c");
    assertThat(asList(results)).containsExactly("a", "b", "c").inOrder();
  }

  @Test
  public void uninterruptibly_twoOperations() {
    assertThat(uninterruptibly(() -> "foo", () -> "bar", String::concat)).isEqualTo("foobar");
  }

  @Test
  public void uninterruptibly_threeOperations() {
    assertThat(
            uninterruptibly(
                () -> "a", () -> "b", () -> "c", (String a, String b, String c) -> a + b + c))
        .isEqualTo("abc");
  }

  @Test
  public void uninterruptibly_fourOperations() {
    assertThat(
            uninterruptibly(
                () -> "a",
                () -> "b",
                () -> "c",
                () -> "d",
                (String a, String b, String c, String d) -> a + b + c + d))
        .isEqualTo("abcd");
  }

  @Test
  public void uninterruptibly_fiveOperations() {
    assertThat(
            uninterruptibly(
                () -> "a",
                () -> "b",
                () -> "c",
                () -> "d",
                () -> "e",
                (String a, String b, String c, String d, String e) -> a + b + c + d + e))
        .isEqualTo("abcde");
  }

  @Test
  public void uninterruptibly_twoTasks() {
    String[] results = new String[2];
    uninterruptibly(() -> results[0] = "foo", () -> results[1] = "bar");
    assertThat(asList(results)).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void uninterruptibly_threeTasks() {
    String[] results = new String[3];
    uninterruptibly(() -> results[0] = "a", () -> results[1] = "b", () -> results[2] = "c");
    assertThat(asList(results)).containsExactly("a", "b", "c").inOrder();
  }

  @Test
  public void concurrently_firstOperationThrows_exceptionPropagated() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                concurrently(
                    () -> {
                      throw new IllegalStateException("bad");
                    },
                    () -> "bar",
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bad");
  }

  @Test
  public void concurrently_secondOperationThrows_exceptionPropagated() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                concurrently(
                    () -> "foo",
                    () -> {
                      throw new IllegalStateException("bar");
                    },
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bar");
  }

  @Test
  public void uninterruptibly_firstOperationThrows_exceptionPropagated() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                uninterruptibly(
                    () -> {
                      throw new IllegalStateException("bad");
                    },
                    () -> "bar",
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bad");
  }

  @Test
  public void uninterruptibly_secondOperationThrows_exceptionPropagated() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                uninterruptibly(
                    () -> "foo",
                    () -> {
                      throw new IllegalStateException("bar");
                    },
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bar");
  }

  @Test
  public void withMaxConcurrency_zeroConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> withMaxConcurrency(0));
  }

  @Test
  public void withMaxConcurrency_negativeConcurrencyDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> withMaxConcurrency(-1));
  }

  @Test
  public void withMaxConcurrency_inputSizeGreaterThanMaxConcurrency() {
    Map<Integer, String> results =
        Stream.of(1, 2, 3, 4, 5).collect(withMaxConcurrency(3).inParallel(Object::toString)).toMap();
    assertThat(results).containsExactly(1, "1", 2, "2", 3, "3", 4, "4", 5, "5").inOrder();
  }

  @Test
  public void withMaxConcurrency_inputSizeSmallerThanMaxConcurrency() {
    Map<Integer, String> results =
        Stream.of(1, 2).collect(withMaxConcurrency(3).inParallel(Object::toString)).toMap();
    assertThat(results).containsExactly(1, "1", 2, "2").inOrder();
  }

  @Test
  public void withMaxConcurrency_inputSizeEqualToMaxConcurrency() {
    Map<Integer, String> results =
        Stream.of(1, 2, 3).collect(withMaxConcurrency(3).inParallel(Object::toString)).toMap();
    assertThat(results).containsExactly(1, "1", 2, "2", 3, "3").inOrder();
  }
}