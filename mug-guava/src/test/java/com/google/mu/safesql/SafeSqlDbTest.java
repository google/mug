package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
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

  @Test public void queryLazily() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 40, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "lazyfoo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId() + 1, "lazybar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumnStream(
            SafeSql.of("select title from {tbl} where id = {id}", /* tbl */ SafeSql.of("ITEMS"), testId()), "title"))
        .containsExactly("lazyfoo");
    assertThat(queryColumnStream(SafeSql.of("select title from ITEMS where id = {id}", testId() + 1), "title"))
        .containsExactly("lazybar");
    assertThat(queryColumnStream(SafeSql.of("select title from ITEMS"), "title"))
        .containsAtLeast("lazyfoo", "lazybar");
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

  @Test public void query_noParameter() throws Exception {
    assertThat(queryColumn(SafeSql.of("select count(*) as cnt from ITEMS"), "cnt")).hasSize(1);
  }

  @Test public void update_noParameter() throws Exception {
    assertThat(
            SafeSql.of("UPDATE ITEMS SET title = 'foo' where id = NULL").update(connection()))
        .isEqualTo(0);
  }

  @Test public void query_withTargetType_parametersAnnotatedWithSqlName() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select id, time, title from ITEMS where id = {id}", testId())
                .query(connection(), Item.class))
        .containsExactly(new Item(testId(), "bar", barTime.toInstant()));
  }

  @Test public void query_withTargetType_usingParameterNames() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select time, id, title from ITEMS where id = {id}", testId())
                .query(connection(), UnannotatedItem.class))
        .containsExactly(new UnannotatedItem(testId(), "bar", barTime.toInstant()));
  }

  @Test public void query_withTargetType_nullSupported() throws Exception {
    ZonedDateTime time = null;
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "bar")
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select id, time, title from ITEMS where id = {id}", testId())
                .query(connection(), Item.class))
        .containsExactly(new Item(testId(), "bar", null));
  }

  @Test public void queryLazily_withTargetType_parametersAnnotatedWithSqlName() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select id, time, title from ITEMS where id = {id}", testId())
                .queryLazily(connection(), Item.class)
                .collect(toList()))
        .containsExactly(new Item(testId(), "bar", barTime.toInstant()));
    assertThat(
        SafeSql.of("select id, time, title from ITEMS where id = {id}", testId())
            .queryLazily(connection(), 2, Item.class)
            .collect(toList()))
    .containsExactly(new Item(testId(), "bar", barTime.toInstant()));
  }

  @Test public void query_withTargetType_noNamedParameters_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id, time, title from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithNoNamedParams.class));
    assertThat(thrown).hasMessageThat().contains("arg0");
  }

  @Test public void query_withTargetType_noAccessibleConstructor_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id, time, title from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), NoAccessibleConstructor.class));
    assertThat(thrown).hasMessageThat().contains("NoAccessibleConstructor");
  }

  @Test public void query_withTargetType_ambiguousConstructors_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id, time, title from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithAmbiguousConstructors.class));
    assertThat(thrown).hasMessageThat().contains("Ambiguous");
  }

  @Test public void query_withTargetType_duplicateColumnNames_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithDuplicateColumnNames.class));
    assertThat(thrown).hasMessageThat().contains("Duplicate");
    assertThat(thrown).hasMessageThat().contains("id");
  }

  @Test public void query_withTargetType_emptyColumnName_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithEmptyColumnName.class));
    assertThat(thrown).hasMessageThat().contains("empty");
    assertThat(thrown).hasMessageThat().contains("WithEmptyColumnName");
  }

  @Test public void query_withTargetType_blankColumnName_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithBlankColumnName.class));
    assertThat(thrown).hasMessageThat().contains("blank");
    assertThat(thrown).hasMessageThat().contains("WithBlankColumnName");
  }

  @Test public void query_withTargetType_duplicateQueryColumnNames_disallowed() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    SafeSql sql = SafeSql.of("select id, id, time, title from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), Item.class));
    assertThat(thrown).hasMessageThat().contains("Duplicate column");
    assertThat(thrown).hasMessageThat().contains("ID at index 2");
  }

  static class Item {
    private final int id;
    private final String title;
    private final Instant time;

    Item(@SqlName("id") int id, @SqlName("title") String title, @SqlName("time") Instant t) {
      this.id = id;
      this.title = title;
      this.time = t;
    }

    @Override public boolean equals(Object that) {
      return that != null && toString().equals(that.toString());
    }

    @Override public int hashCode() {
      return toString().hashCode();
    }

    @Override public String toString() {
      return "id=" + id + ", title=" + title + ", time=" + time;
    }
  }

  static class UnannotatedItem extends Item {
    UnannotatedItem(int id, String title, Instant time) {
      super(id, title, time);
    }
  }

  static class WithNoNamedParams {
    WithNoNamedParams(int arg0, String arg1, Instant arg2) {}
  }

  static class NoAccessibleConstructor {
    private NoAccessibleConstructor(
        @SqlName("id") int id, @SqlName("title") String title, @SqlName("time") Instant t) {
    }
  }

  static class WithAmbiguousConstructors {
    WithAmbiguousConstructors(
        @SqlName("id") int id, @SqlName("title") String title, @SqlName("time") Instant t) {}

    WithAmbiguousConstructors(
        @SqlName("id") int id, @SqlName("time") Instant t,  @SqlName("title") String title) {}
  }

  static class WithDuplicateColumnNames {
    WithDuplicateColumnNames(@SqlName("id") int id, @SqlName("id") String id2) {}
  }

  static class WithEmptyColumnName {
    WithEmptyColumnName(@SqlName("") int id) {}
  }

  static class WithBlankColumnName {
    WithBlankColumnName(@SqlName(" ") int id) {}
  }

  private int testId() {
    return Hashing.goodFastHash(32).hashString(testName.getMethodName(), StandardCharsets.UTF_8).asInt();
  }

  private List<?> queryColumn(SafeSql sql, String column) throws Exception {
    return sql.query(connection(), resultSet -> resultSet.getObject(column));
  }

  private List<?> queryColumnStream(SafeSql sql, String column) throws Exception {
    try (Stream<?> stream = sql.queryLazily(connection(), resultSet -> resultSet.getObject(column))) {
      return stream.collect(Collectors.toList());
    }
  }

  private Connection connection() throws Exception {
    return getConnection().getConnection();
  }
}