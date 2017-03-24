package com.google.mu.util.concurrent;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.ClassSanityTester;

@RunWith(JUnit4.class)
public class ParallelizerPreconditionsTest {
  private final ExecutorService threadPool = Executors.newCachedThreadPool();

  @Test public void testZeroMaxInFlight() {
    assertThrows(IllegalArgumentException.class, () -> new Parallelizer(threadPool, 0));
  }

  @Test public void testNegativeMaxInFlight() {
    assertThrows(IllegalArgumentException.class, () -> new Parallelizer(threadPool, -1));
  }

  @Test public void testZeroTimeout() {
    Parallelizer parallelizer = new Parallelizer(threadPool, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> parallelizer.parallelize(Stream.empty(), 0, TimeUnit.MILLISECONDS));
  }

  @Test public void testNegaiveTimeout() {
    Parallelizer parallelizer = new Parallelizer(threadPool, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> parallelizer.parallelize(Stream.empty(), -1, TimeUnit.MILLISECONDS));
  }

  @Test public void testNulls() {
    new ClassSanityTester().testNulls(Parallelizer.class);
    Parallelizer parallelizer = new Parallelizer(threadPool, 1);
    assertThrows(
        NullPointerException.class,
        () -> parallelizer.parallelize(nullTasks(), 1, TimeUnit.MILLISECONDS));
    assertThrows(NullPointerException.class, () -> parallelizer.parallelize(nullTasks()));
    assertThrows(
        NullPointerException.class, () -> parallelizer.parallelizeUninterruptibly(nullTasks()));
  }

  private static Stream<Runnable> nullTasks() {
    Runnable task = null;
    return Stream.of(task);
  }
}
