package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

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
import com.google.mu.util.StringFormat;

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
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", 1, "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", 2, "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from {tbl} where id = {id}", /* tbl */ SafeSql.of("ITEMS"), 1), "title"))
        .containsExactly("foo");
    assertThat(queryColumn(SafeSql.of("select title from ITEMS where id = {id}", 2), "title"))
        .containsExactly("bar");
  }

  @Test public void likeExpressionWithWildcardInArg() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like {...} and id = {id}", "%o%", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithWildcardInSql() throws Exception {
    String title = "What's that?";
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), title)
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}%' and id = {id}", "'s", testId()), "title"))
        .containsExactly(title);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}%' and id = {id}", "at?", testId()), "title"))
        .containsExactly(title);
  }

  @Test public void withPercentCharacterValue() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "%")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{...}' and id = {id}", "%", testId()), "title"))
        .containsExactly("%");
  }

  @Test public void withBackslashCharacterValue() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "\\")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{...}' and id = {id}", "\\", testId()), "title"))
        .containsExactly("\\");
  }

  @Test public void likeExpressionWithPrefixWildcardInSql() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}%' and id = {id}", "fo", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithSuffixWildcardInSql() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "oo", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void quotedStringExpression() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title = '{...}' and id = {id}", "foo", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithPercentValue_notFound() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
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
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "30%")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "0%", testId()), "title"))
        .containsExactly("30%");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "3%%", testId()), "title"))
        .isEmpty();
  }

  @Test public void likeExpressionWithBackslashValue() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
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
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "a\\b")
                .update(connection()))
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
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from `{table}` where id = {id}", "ITEMS", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void nullParameter() throws Exception {
    assertThat(
            SafeSql.of(
                "insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(),
                "foo", null).update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(SafeSql.of("select time from ITEMS where id = {id}", testId()), "time"))
        .containsExactly((Object) null);
  }

  @Test public void inExpressionUsingList() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    SafeSql query = SafeSql.of(
        "select title from ITEMS where title in ({...}) and id in ({id})",
        asList("foo", "bar"), asList(testId()));
    assertThat(queryColumn(query, "title")).containsExactly("foo");
  }

  @Test public void prepareToQuery_sameArgTypes() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    StringFormat.Template<List<String>> template = SafeSql.prepareToQuery(
        connection(),
        "select title from ITEMS where title = '{...}' and id in ({id})",
        resultSet -> resultSet.getString("title"));
    assertThat(template.with("foo", testId())).containsExactly("foo");
    assertThat(template.with("foo", testId())).containsExactly("foo");
    assertThat(template.with("bar", testId())).isEmpty();
  }

  @Test public void prepareToQuery_differentArgTypes() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    StringFormat.Template<List<String>> template = SafeSql.prepareToQuery(
        connection(),
        "select title from ITEMS where title = {...} and id in ({id})",
        resultSet -> resultSet.getString("title"));
    assertThat(template.with("foo", testId())).containsExactly("foo");
    assertThat(template.with(SafeSql.of("'foo'"), testId())).containsExactly("foo");
    assertThat(template.with("foo", testId())).containsExactly("foo");
    assertThat(template.with("bar", testId())).isEmpty();
    assertThat(template.with(SafeSql.of("'bar'"), testId())).isEmpty();
  }

  @Test public void prepareToUpdate_sameArgTypes() throws Exception {
    StringFormat.Template<Integer> insertUser =
        SafeSql.prepareToUpdate(connection(), "insert into ITEMS(id, title) VALUES({id}, {...})");
    assertThat(insertUser.with(testId(), "foo")).isEqualTo(1);
    assertThat(insertUser.with(testId() + 1, "bar")).isEqualTo(1);
    StringFormat.Template<List<String>> template = SafeSql.prepareToQuery(
        connection(),
        "select title from ITEMS where title = '{...}' and id in ({id})",
        resultSet -> resultSet.getString("title"));
    assertThat(template.with("foo", testId())).containsExactly("foo");
    assertThat(template.with("bar", testId() + 1)).containsExactly("bar");
  }

  @Test public void prepareToUpdate_differentArgTypes() throws Exception {
    StringFormat.Template<Integer> insertUser =
        SafeSql.prepareToUpdate(connection(), "insert into ITEMS(id, title) VALUES({id}, {...})");
    assertThat(insertUser.with(testId(), "foo")).isEqualTo(1);
    assertThat(insertUser.with(testId() + 1, SafeSql.of("'bar'"))).isEqualTo(1);
    StringFormat.Template<List<String>> template = SafeSql.prepareToQuery(
        connection(),
        "select title from ITEMS where title = '{...}' and id in ({id})",
        resultSet -> resultSet.getString("title"));
    assertThat(template.with("foo", testId())).containsExactly("foo");
    assertThat(template.with("bar", testId() + 1)).containsExactly("bar");
  }

  private int testId() {
    return Hashing.goodFastHash(32).hashString(testName.getMethodName(), StandardCharsets.UTF_8).asInt();
  }

  private List<?> queryColumn(SafeSql sql, String column) throws Exception {
    return sql.query(connection(), resultSet -> resultSet.getObject(column));
  }

  private Connection connection() throws Exception {
    return getConnection().getConnection();
  }
}