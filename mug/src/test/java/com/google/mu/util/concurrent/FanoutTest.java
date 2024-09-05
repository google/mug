package com.google.mu.util.concurrent;


import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.util.concurrent.Fanout.concurrently;
import static com.google.mu.util.concurrent.Fanout.uninterruptibly;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FanoutTest {
  @Test
  public void concurrently_twoOperations() throws InterruptedException {
    assertThat(concurrently(() -> "foo", () -> "bar", String::concat)).isEqualTo("foobar");
  }

  @Test
  public void concurrently_threeOperations() throws InterruptedException {
    assertThat(
            concurrently(
                () -> "a", () -> "b", () -> "c", (String a, String b, String c) -> a + b + c))
        .isEqualTo("abc");
  }

  @Test
  public void concurrently_fourOperations() throws InterruptedException {
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
  public void concurrently_fiveOperations() throws InterruptedException {
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
  public void concurrently_sideEffectsAreSafe() throws InterruptedException {
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
  public void concurrently_firstOperationThrows_exceptionPropagated() throws InterruptedException {
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
  public void concurrently_secondOperationThrows_exceptionPropagated() throws InterruptedException {
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
}