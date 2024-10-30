package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.dbunit.DataSourceBasedDBTestCase;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
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
  protected IDataSet getDataSet() throws Exception {
    return new FlatXmlDataSetBuilder().build(new ByteArrayInputStream(new byte[0]));
  }

  @Override
  protected DatabaseOperation getSetUpOperation() {
      return DatabaseOperation.REFRESH;
  }

  @Override
  protected DatabaseOperation getTearDownOperation() {
      return DatabaseOperation.TRUNCATE_TABLE;
  }

  @Test public void roundtrip() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 40, ZoneId.of("UTC"));
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 1, "foo")))
        .isEqualTo(1);
    assertThat(update(SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", 2, "bar", barTime)))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from {tbl} where id = {id}", /* tbl */ SafeSql.of("ITEMS"), 1), "title"))
        .containsExactly("foo");
    assertThat(queryColumn(SafeSql.of("select title from ITEMS where id = {id}", 2), "title"))
        .containsExactly("bar");
  }

  @Test public void likeExpressionWithWildcardInArg() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 202, "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like {t} and id = {id}", "%o%", 202), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithWildcardInSql() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 303, "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}%' and id = {id}", "o", 303), "title"))
        .containsExactly("foo");
  }

  @Test public void withPercentCharacterValue() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 304, "%")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{t}' and id = {id}", "%", 304), "title"))
        .containsExactly("%");
  }

  @Test public void withBackslashCharacterValue() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 305, "\\")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{t}' and id = {id}", "\\", 305), "title"))
        .containsExactly("\\");
  }

  @Test public void likeExpressionWithPrefixWildcardInSql() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 404, "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{t}%' and id = {id}", "fo", 404), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithSuffixWildcardInSql() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 505, "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}' and id = {id}", "oo", 505), "title"))
        .containsExactly("foo");
  }

  @Test public void quotedStringExpression() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 606, "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{t}' and id = {id}", "foo", 606), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithPercentValue() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 700, "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}%' and id = {id}", "%", 700), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}' and id = {id}", "%", 700), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{t}%' and id = {id}", "%", 700), "title"))
        .isEmpty();
  }

  @Test public void likeExpressionWithBackslashValue() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 701, "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}%' and id = {id}", "\\", 701), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}' and id = {id}", "\\", 701), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{t}%' and id = {id}", "\\", 701), "title"))
        .isEmpty();
  }
  @Test public void literalBackslashMatches() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 702, "a\\b")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}%' and id = {id}", "\\", 702), "title"))
        .containsExactly("a\\b");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{t}' and id = {id}", "\\b", 702), "title"))
        .containsExactly("a\\b");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{t}%' and id = {id}", "a\\", 702), "title"))
        .containsExactly("a\\b");
  }

  @Test public void nullParameter() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", 11, "foo", null)))
        .isEqualTo(1);
    assertThat(queryColumn(SafeSql.of("select time from ITEMS where id = {id}", 11), "time"))
        .containsExactly(null);
  }

  private int update(SafeSql sql) throws Exception {
    try (PreparedStatement statement = sql.prepareStatement(connection())) {
      return statement.executeUpdate();
    }
  }

  private List<?> queryColumn(SafeSql sql, String column) throws Exception {
    List<Object> values = new ArrayList<>();
    try (PreparedStatement statement = sql.prepareStatement(connection());
        ResultSet resultSet = statement.executeQuery()) {
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