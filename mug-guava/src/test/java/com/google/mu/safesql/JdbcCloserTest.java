package com.google.mu.safesql;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.Statement;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class JdbcCloserTest {
  @Mock private Statement statement;

  @Before public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test public void nothingRegistered() {
    try (JdbcCloser closer = new JdbcCloser()) {}
  }

  @SuppressWarnings("MustBeClosedChecker")
  @Test public void attachTo_nothingRegistered() {
    Stream<String> stream = Stream.of("foo", "bar");
    try (JdbcCloser closer = new JdbcCloser()) {
      stream = closer.attachTo(stream);
    }
    try (Stream<String> closeMe = stream) {}
  }

  @Test public void resourceClosed() throws Exception {
    try (JdbcCloser closer = new JdbcCloser()) {
      closer.register(statement::close);
    }
    verify(statement).close();
  }

  @SuppressWarnings("MustBeClosedChecker")
  @Test public void resourceAttached() throws Exception {
    Stream<String> stream = Stream.of("foo", "bar");
    try (JdbcCloser closer = new JdbcCloser()) {
      closer.register(statement::close);
      stream = closer.attachTo(stream);
    }
    verify(statement, never()).close();

    // Now close the stream
    try (Stream<?> closeMe = stream) {}
    verify(statement).close();
  }

  @SuppressWarnings("MustBeClosedChecker")
  @Test public void cannotAttachTwice() throws Exception {
    Stream<String> stream = Stream.of("foo", "bar");
    try (JdbcCloser closer = new JdbcCloser()) {
      closer.register(statement::close);
      stream = closer.attachTo(stream);
      assertThrows(IllegalStateException.class, () -> closer.attachTo(Stream.empty()));
    }
    verify(statement, never()).close();
    try (Stream<?> closeMe = stream) {}
    verify(statement).close();
  }
}
