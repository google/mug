package com.google.mu.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Java8ExecutorPlugin extends StructuredConcurrencyExecutorPlugin {

  @Override protected ExecutorService createExecutor() {
    return Executors.newCachedThreadPool();
  }

  @Override protected Priority priority() {
    return Priority.APPLICATION_SPECIFIC;
  }
}
