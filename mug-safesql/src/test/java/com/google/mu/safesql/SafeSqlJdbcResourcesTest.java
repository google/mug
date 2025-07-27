package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class SafeSqlJdbcResourcesTest {
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private DataSource dataSource;
  @Mock private Connection connection;
  @Mock private Statement statement;
  @Mock private PreparedStatement preparedStatement;
  @Mock private ResultSet resultSet;
  private InOrder inOrder;

  @Before public void setUpMocks() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(any())).thenReturn(preparedStatement);
    when(connection.createStatement()).thenReturn(statement);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(statement.executeQuery(any())).thenReturn(resultSet);
    inOrder = Mockito.inOrder(connection, statement, preparedStatement, resultSet);
  }


  @Test public void query_noParameter_usesCreateStatement() throws SQLException {
    SafeSql.of("SELECT count(*) from Users").query(dataSource, Long.class);
    inOrder.verify(connection).createStatement();
    inOrder.verify(statement).executeQuery("SELECT count(*) from Users");
    inOrder.verify(resultSet).close();
    inOrder.verify(statement).close();
    inOrder.verify(connection).close();
  }


  @Test public void query_noParameter_usesPrepareStatement() throws SQLException {
    SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123).query(dataSource, Long.class);
    inOrder.verify(connection).prepareStatement("SELECT count(*) from Users WHERE id = ?");
    inOrder.verify(preparedStatement).setObject(1, 123);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
    inOrder.verify(connection).close();
  }


  @Test public void queryForOne_noParameter_usesCreateStatement() throws SQLException {
    SafeSql.of("SELECT count(*) from Users").queryForOne(dataSource, Long.class);
    inOrder.verify(connection).createStatement();
    inOrder.verify(statement).executeQuery("SELECT count(*) from Users");
    inOrder.verify(resultSet).close();
    inOrder.verify(statement).close();
    inOrder.verify(connection).close();
  }


  @Test public void queryForOne_noParameter_usesPrepareStatement() throws SQLException {
    SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123).queryForOne(dataSource, Long.class);
    inOrder.verify(connection).prepareStatement("SELECT count(*) from Users WHERE id = ?");
    inOrder.verify(preparedStatement).setObject(1, 123);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
    inOrder.verify(connection).close();
  }


  @Test public void queryLazily_noParameter_usesCreateStatement() throws SQLException {
    try (Stream<Long> stream = SafeSql.of("SELECT count(*) from Users").queryLazily(dataSource, Long.class)) {}
    inOrder.verify(connection).createStatement();
    inOrder.verify(statement).executeQuery("SELECT count(*) from Users");
    inOrder.verify(resultSet).close();
    inOrder.verify(statement).close();
    inOrder.verify(connection).close();
  }


  @Test public void queryLazily_noParameter_usesPrepareStatement() throws SQLException {
    try (Stream<Long> stream =
        SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123).queryLazily(dataSource, Long.class)) {}
    inOrder.verify(connection).prepareStatement("SELECT count(*) from Users WHERE id = ?");
    inOrder.verify(preparedStatement).setObject(1, 123);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
    inOrder.verify(connection).close();
  }


  @Test public void query_usingDataSource_uncheckedSqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(resultSet).next();
    UncheckedSqlException thrown =
        assertThrows(UncheckedSqlException.class, () -> sql.query(dataSource, Long.class));
    assertThat(thrown).hasCauseThat().isSameInstanceAs(exception);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
    inOrder.verify(connection).close();
  }


  @Test public void query_usingConnection_sqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(resultSet).next();
    Exception thrown =
        assertThrows(SQLException.class, () -> sql.query(connection, Long.class));
    assertThat(thrown).isSameInstanceAs(exception);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
  }


  @Test public void queryForOne_usingDataSource_uncheckedSqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(resultSet).next();
    UncheckedSqlException thrown =
        assertThrows(UncheckedSqlException.class, () -> sql.queryForOne(dataSource, Long.class));
    assertThat(thrown).hasCauseThat().isSameInstanceAs(exception);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
    inOrder.verify(connection).close();
  }


  @Test public void queryForOne_usingConnection_sqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(resultSet).next();
    Exception thrown =
        assertThrows(SQLException.class, () -> sql.queryForOne(connection, Long.class));
    assertThat(thrown).isSameInstanceAs(exception);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
  }


  @Test public void queryLazily_usingDataSource_uncheckedSqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(resultSet).next();
    UncheckedSqlException thrown = assertThrows(
        UncheckedSqlException.class,
        () -> {
          try (Stream<Long> stream = sql.queryLazily(dataSource, Long.class)) {
            long unused = stream.count();
          }
        });
    assertThat(thrown).hasCauseThat().isSameInstanceAs(exception);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
    inOrder.verify(connection).close();
  }


  @Test public void queryLazily_usingConnection_resultSetUncheckedSqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(resultSet).next();
    UncheckedSqlException thrown = assertThrows(
        UncheckedSqlException.class,
        () -> {
          try (Stream<Long> stream = sql.queryLazily(connection, Long.class)) {
            stream.forEach(i -> {});
          }
        });
    assertThat(thrown).hasCauseThat().isSameInstanceAs(exception);
    inOrder.verify(resultSet).close();
    inOrder.verify(preparedStatement).close();
  }


  @Test public void queryLazily_usingConnection_statementdSqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(preparedStatement).executeQuery();
    Exception thrown = assertThrows(
        SQLException.class,
        () -> {
          try (Stream<Long> stream = sql.queryLazily(connection, Long.class)) {
            long unused = stream.count();
          }
        });
    assertThat(thrown).isSameInstanceAs(exception);
    inOrder.verify(preparedStatement).close();
    inOrder.verify(resultSet, never()).close();
  }


  @Test public void queryLazily_usingConnection_connectionSqlExceptionPropagated() throws SQLException {
    SafeSql sql = SafeSql.of("SELECT count(*) from Users WHERE id = {id}", 123);
    SQLException exception = new SQLException("test");
    Mockito.doThrow(exception).when(connection).prepareStatement(any());
    Exception thrown = assertThrows(
        SQLException.class,
        () -> {
          try (Stream<Long> stream = sql.queryLazily(connection, Long.class)) {
            stream.forEach(i -> {});
          }
        });
    assertThat(thrown).isSameInstanceAs(exception);
    inOrder.verify(preparedStatement, never()).close();
    inOrder.verify(resultSet, never()).close();
  }
}
