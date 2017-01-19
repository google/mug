package org.mu.benchmarks;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.mu.util.Maybe;

import com.google.caliper.Benchmark;
import com.google.common.testing.SerializableTester;
import com.google.common.util.concurrent.Futures;

public class ExceptionWrappingBenchmark {

  @Benchmark
  void futuresGetChecked(int n) {
    IOException exception = new IOException();
    CompletableFuture<?> future = new CompletableFuture<>();
    future.completeExceptionally(exception);
    for (int i = 0; i < n; i++) {
      try {
        Futures.getChecked(future, IOException.class);
        throw new AssertionError();
      } catch (IOException expected) {}
    }
  }

  @Benchmark
  void maybeGet(int n) {
    IOException exception = new IOException();
    for (int i = 0; i < n; i++) {
      try {
        Maybe.except(exception).orElseThrow();
        throw new AssertionError();
      } catch (IOException expected) {}
    }
  }

  @Benchmark
  void reserializeException(int n) {
    IOException exception = new IOException();
    for (int i = 0; i < n; i++) {
      SerializableTester.reserialize(exception);
    }
  }

  @Benchmark
  void reserializeString(int n) {
    String string = new String("abc");
    for (int i = 0; i < n; i++) {
      SerializableTester.reserialize(string);
    }
  }

  @Benchmark
  void manualWrapper(int n) {
    IOException exception = new IOException();
    for (int i = 0; i < n; i++) {
      try {
        Maybe.except(exception).orElseThrow(IOException::new);
        throw new AssertionError();
      } catch (IOException expected) {}
    }
  }

  @Benchmark
  void noWrapper(int n) {
    IOException exception = new IOException();
    for (int i = 0; i < n; i++) {
      try {
        Maybe.except(exception).orElseThrow(e -> e);
        throw new AssertionError();
      } catch (IOException expected) {}
    }
  }
}
