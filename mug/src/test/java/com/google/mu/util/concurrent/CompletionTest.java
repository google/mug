package com.google.mu.util.concurrent;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CompletionTest {
  @Test public void noTaskStarted() throws Exception {
    try (Completion completion = new Completion()) {}
  }

  @Test public void singleTask_succeeded() throws Exception {
    try (Completion completion = new Completion()) {
      completion.run(() -> {});
    }
  }

  @Test public void singleTask_failed() throws Exception {
    try {
      try (Completion completion = new Completion()) {
        completion.run(() -> {
          throw new RuntimeException("test");
        });
      }
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("test");
    }
  }

  @Test public void twoTasks_succeeded() throws Exception {
    AtomicBoolean done = new AtomicBoolean();
    try (Completion completion = new Completion()) {
      completion.run(() -> {});
      new Thread(completion.toRun(() -> {
        done.set(true);
      })).start();
    }
    assertThat(done.get()).isTrue();
  }

  @Test public void twoTasks_failed() throws Exception {
    AtomicBoolean done = new AtomicBoolean();
    try (Completion completion = new Completion()) {
      new Thread(completion.toRun(() -> {
        done.set(true);
      })).start();
      completion.run(() -> {
        throw new RuntimeException("test");
      });
    } catch (RuntimeException e) {
      assertThat(e).hasMessageThat().contains("test");
    }
    assertThat(done.get()).isTrue();
  }
}
