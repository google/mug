package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.dbunit.DataSourceBasedDBTestCase;
import org.dbunit.operation.DatabaseOperation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SafeSqlDbTest extends DataSourceBasedDBTestCase {
  @Override
  protected DataSource getDataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL(
        "jdbc:h2:mem:default;MODE=LEGACY;DB_CLOSE_DELAY=-1;init=runscript from 'classpath:/com/google/mu/safesql/schema.sql'");
      dataSource.setUser("sa");
      dataSource.setPassword("sa");
      return dataSource;
  }

  @Override
  protected DatabaseOperation getSetUpOperation() {
      return DatabaseOperation.REFRESH;
  }

  @Override
  protected DatabaseOperation getTearDownOperation() {
      return DatabaseOperation.DELETE_ALL;
  }

  @Test public void roundtrip() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 40, ZoneId.of("UTC"));
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 1, "foo")))
        .isEqualTo(1);
    assertThat(update(SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", 2, "bar", barTime)))
        .isEqualTo(1);
    assertThat(queryColumn(SafeSql.of("select title from {tbl} where id = {id}", SafeSql.of("ITEMS"), 1), "title"))
        .containsExactly("foo");
    assertThat(queryColumn(SafeSql.of("select title from ITEMS where id = {id}", 2), "title"))
        .containsExactly("bar");
  }

  @Test public void nullParameter() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", 11, "foo", null)))
        .isEqualTo(1);
    assertThat(queryColumn(SafeSql.of("select time from ITEMS where id = {id}", 11), "time"))
        .containsExactly(null);
  }

  private int update(SafeSql sql) throws Exception {
    return sql.prepareStatement(connection()).executeUpdate();
  }

  private List<?> queryColumn(SafeSql sql, String column) throws Exception {
    List<Object> values = new ArrayList<>();
    try (ResultSet resultSet = sql.prepareStatement(connection()).executeQuery()) {
      while (resultSet.next()) {
        values.add(resultSet.getObject(column));
      }
    }
    return values;
  }

  private Connection connection() throws Exception {
    return getConnection().getConnection();
  }
}