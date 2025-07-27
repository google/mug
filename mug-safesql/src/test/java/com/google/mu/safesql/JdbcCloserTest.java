package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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
  @Test public void attachTwice_noOpTheSecondTime() throws Exception {
    Stream<String> stream = Stream.of("foo", "bar");
    try (JdbcCloser closer = new JdbcCloser()) {
      closer.register(statement::close);
      stream = closer.attachTo(stream);
      closer.attachTo(Stream.empty());
    }
    verify(statement, never()).close();
    try (Stream<?> closeMe = stream) {}
    verify(statement).close();
  }

  @Test public void moreThanOneReousrces_allClosed() throws Exception {
    Connection connection = mock(Connection.class);
    ResultSet resultSet = mock(ResultSet.class);
    try (JdbcCloser closer = new JdbcCloser()) {
      closer.register(connection::close);
      closer.register(statement::close);
      closer.register(resultSet::close);
    }
    InOrder inOrder = Mockito.inOrder(connection, statement, resultSet);
    inOrder.verify(resultSet).close();
    inOrder.verify(statement).close();
    inOrder.verify(connection).close();
  }

  @Test public void moreThanOneReousrces_allClosed_despiteException() throws Exception {
    Connection connection = mock(Connection.class);
    ResultSet resultSet = mock(ResultSet.class);
    IllegalArgumentException connectionException = new IllegalArgumentException("connection");
    SQLException resultSetException = new SQLException("result set");
    Mockito.doThrow(connectionException).when(connection).close();
    Mockito.doThrow(resultSetException).when(resultSet).close();
    Exception thrown = assertThrows(
        UncheckedSqlException.class,
        () -> {
          try (JdbcCloser closer = new JdbcCloser()) {
            closer.register(connection::close);
            closer.register(statement::close);
            closer.register(resultSet::close);
          }
        });
    assertThat(thrown).hasCauseThat().isSameInstanceAs(resultSetException);
    assertThat(thrown.getCause().getSuppressed()).asList().containsExactly(connectionException);
    InOrder inOrder = Mockito.inOrder(connection, statement, resultSet);
    inOrder.verify(resultSet).close();
    inOrder.verify(statement).close();
    inOrder.verify(connection).close();
  }
}
