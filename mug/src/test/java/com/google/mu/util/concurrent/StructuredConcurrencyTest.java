package com.google.mu.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.testing.ClassSanityTester;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StructuredConcurrencyTest {
  private ExecutorService executor;

  @Before public void startThreadPool() {
    executor = Executors.newCachedThreadPool();
  }

  @After public void shutdownThreadPool() {
    executor.shutdownNow();
  }

  @Test
  public void concurrently_twoOperations() throws InterruptedException {
    assertThat(fanout().concurrently(() -> "foo", () -> "bar", String::concat))
        .isEqualTo("foobar");
  }

  @Test
  public void concurrently_threeOperations() throws InterruptedException {
    assertThat(
            fanout().concurrently(
                () -> "a", () -> "b", () -> "c", (String a, String b, String c) -> a + b + c))
        .isEqualTo("abc");
  }

  @Test
  public void concurrently_fourOperations() throws InterruptedException {
    assertThat(
            fanout().concurrently(
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
            fanout().concurrently(
                () -> "a",
                () -> "b",
                () -> "c",
                () -> "d",
                () -> "e",
                (String a, String b, String c, String d, String e) -> a + b + c + d + e))
        .isEqualTo("abcde");
  }

  @Test
  public void uninterruptibly_twoOperations() {
    assertThat(fanout().uninterruptibly(() -> "foo", () -> "bar", String::concat))
        .isEqualTo("foobar");
  }

  @Test
  public void uninterruptibly_threeOperations() {
    assertThat(
            fanout().uninterruptibly(
                () -> "a", () -> "b", () -> "c", (String a, String b, String c) -> a + b + c))
        .isEqualTo("abc");
  }

  @Test
  public void uninterruptibly_fourOperations() {
    assertThat(
            fanout().uninterruptibly(
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
            fanout().uninterruptibly(
                () -> "a",
                () -> "b",
                () -> "c",
                () -> "d",
                () -> "e",
                (String a, String b, String c, String d, String e) -> a + b + c + d + e))
        .isEqualTo("abcde");
  }

  @Test
  public void concurrently_firstOperationThrows() throws InterruptedException {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                fanout().concurrently(
                    () -> {
                      throw new IllegalStateException("bad");
                    },
                    () -> "bar",
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bad");
  }

  @Test
  public void concurrently_secondOperationThrows() throws InterruptedException {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                fanout().concurrently(
                    () -> "foo",
                    () -> {
                      throw new IllegalStateException("bar");
                    },
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bar");
  }

  @Test
  public void uninterruptibly_firstOperationThrows() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                fanout().uninterruptibly(
                    () -> {
                      throw new IllegalStateException("bad");
                    },
                    () -> "bar",
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bad");
  }

  @Test
  public void uninterruptibly_secondOperationThrows() {
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                fanout().uninterruptibly(
                    () -> "foo",
                    () -> {
                      throw new IllegalStateException("bar");
                    },
                    (a, b) -> b));
    assertThat(thrown).hasMessageThat().contains("bar");
  }

  @Test
  public void concurrently_tunneledException() throws IOException, InterruptedException {
    try {
      Object unused =
          fanout().concurrently(
              () -> tunnel(() -> failToRead("badfile")), () -> "bar", String::concat);
      throw new AssertionError("should have failed");
    } catch (TunnelException e) {
      assertThat(e.getCauseAs(IOException.class)).hasMessageThat().contains("badfile");
    }
  }

  @Test
  public void uninterruptibly_tunneledException() throws IOException {
    try {
      Object unused =
          fanout().uninterruptibly(
              () -> tunnel(() -> failToRead("badfile")), () -> "bar", String::concat);
      throw new AssertionError("should have failed");
    } catch (TunnelException e) {
      assertThat(e.getCauseAs(IOException.class)).hasMessageThat().contains("badfile");
    }
  }

  @Test
  public void testNulls() {
    new ClassSanityTester().testNulls(StructuredConcurrency.class);
  }

  private StructuredConcurrency fanout() {
    return new StructuredConcurrency(executor);
  }

  private static String failToRead(String file) throws IOException {
    throw new IOException("deliberately fail to read " + file);
  }

  private static <T> T tunnel(Callable<T> callable) {
    try {
      return callable.call();
    } catch (Exception e) {
      throw new TunnelException(e);
    }
  }

  private static final class TunnelException extends RuntimeException {
    TunnelException(Throwable cause) {
      super(cause);
    }

    <X extends Throwable> X getCauseAs(Class<X> type) {
      assertThat(getCause()).isInstanceOf(type);
      return type.cast(getCause());
    }
  }
}
