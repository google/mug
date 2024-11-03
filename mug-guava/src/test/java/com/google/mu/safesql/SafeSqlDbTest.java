package com.google.mu.safesql;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.dbunit.DataSourceBasedDBTestCase;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.hash.Hashing;

@RunWith(JUnit4.class)
public class SafeSqlDbTest extends DataSourceBasedDBTestCase {
  @Rule public final TestName testName = new TestName();

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
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like {...} and id = {id}", "%o%", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithWildcardInSql() throws Exception {
    String title = "What's that?";
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), title)))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}%' and id = {id}", "'s", testId()), "title"))
        .containsExactly(title);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}%' and id = {id}", "at?", testId()), "title"))
        .containsExactly(title);
  }

  @Test public void withPercentCharacterValue() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "%")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{...}' and id = {id}", "%", testId()), "title"))
        .containsExactly("%");
  }

  @Test public void withBackslashCharacterValue() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "\\")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{...}' and id = {id}", "\\", testId()), "title"))
        .containsExactly("\\");
  }

  @Test public void likeExpressionWithPrefixWildcardInSql() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}%' and id = {id}", "fo", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithSuffixWildcardInSql() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "oo", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void quotedStringExpression() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{...}' and id = {id}", "foo", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithPercentValue_notFound() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}%' and id = {id}", "%", testId()), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "%", testId()), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}%' and id = {id}", "%", testId()), "title"))
        .isEmpty();
  }

  @Test public void likeExpressionWithPercentValue_found() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "30%")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "0%", testId()), "title"))
        .containsExactly("30%");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "3%%", testId()), "title"))
        .isEmpty();
  }

  @Test public void likeExpressionWithBackslashValue() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}%' and id = {id}", "\\", testId()), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "\\", testId()), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}%' and id = {id}", "\\", testId()), "title"))
        .isEmpty();
  }

  @Test public void literalBackslashMatches() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "a\\b")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}%' and id = {id}", "\\", testId()), "title"))
        .containsExactly("a\\b");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "\\b", testId()), "title"))
        .containsExactly("a\\b");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}%' and id = {id}", "a\\", testId()), "title"))
        .containsExactly("a\\b");
  }

  @Test public void withBacktickQuotedIdentifierParameter() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from `{table}` where id = {id}", "ITEMS", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void nullParameter() throws Exception {
    assertThat(
            update(SafeSql.of(
                "insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(),
                "foo", null)))
        .isEqualTo(1);
    assertThat(queryColumn(SafeSql.of("select time from ITEMS where id = {id}", testId()), "time"))
        .containsExactly((Object) null);
  }

  @Test public void inExpressionUsingList() throws Exception {
    assertThat(update(SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")))
        .isEqualTo(1);
    SafeSql query = SafeSql.of(
        "select title from ITEMS where title in ({...}) and id in ({id})",
        Stream.of("foo", "bar").map(SafeSql::ofParam).collect(toImmutableList()),
        Stream.of(testId(), null).map(SafeSql::ofParam).collect(toList()));
    assertThat(queryColumn(query, "title")).containsExactly("foo");
  }

  private int testId() {
    return Hashing.goodFastHash(32).hashString(testName.getMethodName(), StandardCharsets.UTF_8).asInt();
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