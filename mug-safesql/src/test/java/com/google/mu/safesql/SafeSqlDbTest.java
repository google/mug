package com.google.mu.safesql;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
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
import org.junit.After;
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

  @After public void cleanDb() throws Exception {
    SafeSql.of("TRUNCATE TABLE ITEMS").update(connection());
  }

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

  @Test public void likeExpressionWithWildcardAndUnderscoreMixed() throws Exception {
    String title = "What's that?";
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), title)
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}_' and id = {id}", "at", testId()), "title"))
        .containsExactly(title);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}_' and id = {id}", "th", testId()), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}%' and id = {id}", "hat'", testId()), "title"))
        .containsExactly(title);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}%' and id = {id}", "What", testId()), "title"))
        .isEmpty();
  }

  @Test public void likeExpressionWithUnderscoreInSql() throws Exception {
    String title = "What's that?";
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), title)
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}_' and id = {id}", "hat's that", testId()), "title"))
        .containsExactly(title);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}_' and id = {id}", "'s that", testId()), "title"))
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

  @Test public void likeExpressionWithPrefixUnderscoreInSql() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}_' and id = {id}", "fo", testId()), "title"))
        .containsExactly("foo");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}_' and id = {id}", "f", testId()), "title"))
        .isEmpty();
  }

  @Test public void likeExpressionWithSuffixWildcardInSql() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '%{...}' and id = {id}", "o", testId()), "title"))
        .containsExactly("foo");
  }

  @Test public void likeExpressionWithSuffixUnderscoreInSql() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}' and id = {id}", "oo", testId()), "title"))
        .containsExactly("foo");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}' and id = {id}", "o", testId()), "title"))
        .isEmpty();
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
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '{...}_' and id = {id}", "%", testId()), "title"))
        .isEmpty();
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}' and id = {id}", "%", testId()), "title"))
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
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}' and id = {id}", "0%", testId()), "title"))
        .containsExactly("30%");
    assertThat(queryColumn(
            SafeSql.of("select title from ITEMS where title like '_{...}' and id = {id}", "0%%", testId()), "title"))
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

  @Test public void query_withResultType_parametersAnnotatedWithSqlName() throws Exception {
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

  @Test public void query_withResultType_superfluousColumnIgnored() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of(
                "insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select id, time, title, item_uuid from ITEMS where id = {id}", testId())
                .query(connection(), Item.class))
        .containsExactly(new Item(testId(), "bar", barTime.toInstant()));
  }

  @Test public void query_withResultType_toStringResults() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select title from ITEMS where id = {id}", testId())
                .query(connection(), String.class))
        .containsExactly("bar");
    assertThat(
            SafeSql.of("select NULL AS title from ITEMS where id = {id}", testId())
                .query(connection(), String.class))
        .containsExactly(null);
  }

  @Test public void query_withResultType_toBooleanResults() throws Exception {
    assertThat(
            SafeSql.of("select (1 = 1)").query(connection(), Boolean.class))
        .containsExactly(true);
    assertThat(
            SafeSql.of("select NULL").query(connection(), Boolean.class))
        .containsExactly(null);
  }

  @Test public void query_withResultType_toIntResults() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select id from ITEMS where id = {id}", testId())
                .query(connection(), Integer.class))
        .containsExactly(testId());
    assertThat(
            SafeSql.of("select NULL AS id").query(connection(), Integer.class))
        .containsExactly(null);
  }

  @Test public void query_withResultType_toShortResults() throws Exception {
    assertThat(SafeSql.of("select 123").query(connection(), Short.class))
        .containsExactly((short) 123);
    assertThat(SafeSql.of("select NULL").query(connection(), Short.class))
        .containsExactly(null);
  }

  @Test public void query_withResultType_toByteResults() throws Exception {
    assertThat(SafeSql.of("select 123").query(connection(), Byte.class))
        .containsExactly((byte) 123);
    assertThat(SafeSql.of("select NULL").query(connection(), Byte.class))
        .containsExactly(null);
  }

  @Test public void query_withResultType_toCharResults() throws Exception {
    assertThat(SafeSql.of("select 'a'").query(connection(), Character.class))
        .containsExactly('a');
    assertThat(SafeSql.of("select NULL").query(connection(), Character.class))
        .containsExactly(null);
  }

  @Test public void query_withResultType_toLongResults() throws Exception {
    assertThat(SafeSql.of("select 123").query(connection(), Long.class))
        .containsExactly(123L);
    assertThat(SafeSql.of("select NULL").query(connection(), Long.class))
        .containsExactly(null);
  }

  @Test public void query_withResultType_toLocalDateResults() throws Exception {
    assertThat(SafeSql.of("select CAST('2025-10-01' AS DATE)").query(connection(), LocalDate.class))
        .containsExactly(LocalDate.of(2025, 10, 1));
  }

  @Test public void query_withResultType_toLocalTimeResults() throws Exception {
    assertThat(SafeSql.of("select CAST('08:30:00' AS TIME)").query(connection(), LocalTime.class))
        .containsExactly(LocalTime.of(8, 30, 0));
  }

  @Test public void query_withResultType_toInstantResults() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select time from ITEMS where id = {id}", testId())
                .query(connection(), Instant.class))
        .containsExactly(barTime.toInstant());
  }

  @Test public void query_withResultType_toPrimitiveType_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> sql.query(connection(), int.class));
    assertThat(thrown).hasMessageThat().contains("int");
  }

  @Test public void query_withResultType_toVoidType_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> sql.query(connection(), Void.class));
    assertThat(thrown).hasMessageThat().contains("Void");
  }

  @Test public void query_withMaxRows() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("select title from (select '{v}' as title union all select 'b' as title) order by title", "a")
                .query(connection(), stmt -> stmt.setMaxRows(1), String.class))
        .containsExactly("a");
    assertThat(
            SafeSql.of("select title from (select '{v}' as title union all select 'b' as title) order by title", "c")
                .query(connection(), stmt -> stmt.setMaxRows(1), String.class))
        .containsExactly("b");
  }

  @Test public void queryLazily_withResultType_toZonedDateTimeResults() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select time from ITEMS where id = {id}", testId())
                .queryLazily(connection(), ZonedDateTime.class)
                .map(ZonedDateTime::toInstant)
                .collect(toImmutableList()))
        .containsExactly(barTime.toInstant());
    assertThat(
            SafeSql.of("select time from ITEMS where id = {id}", testId())
                .queryForOne(connection(), ZonedDateTime.class)
                .map(ZonedDateTime::toInstant))
        .hasValue(barTime.toInstant());
  }

  @Test public void queryLazily_withResultType_toOffsetDateTimeResults() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("select time from ITEMS where id = {id}", testId())
                .queryLazily(connection(), OffsetDateTime.class)
                .map(OffsetDateTime::toInstant)
                .collect(toImmutableList()))
        .containsExactly(barTime.toInstant());
    assertThat(
            SafeSql.of("select time from ITEMS where id = {id}", testId())
                .queryForOne(connection(), OffsetDateTime.class)
                .map(OffsetDateTime::toInstant))
        .hasValue(barTime.toInstant());
  }

  @Test public void query_withResultType_usingParameterNames() throws Exception {
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

  @Test public void query_withResultType_nullSupported() throws Exception {
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

  @Test public void query_withResultType_noNamedParameters_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id, time, title from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithNoNamedParams.class));
    assertThat(thrown).hasMessageThat().contains("arg0");
  }

  @Test public void query_withResultType_noAccessibleConstructor_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id, time, title from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), NoAccessibleConstructor.class));
    assertThat(thrown).hasMessageThat().contains("NoAccessibleConstructor");
  }

  @Test public void query_withResultType_ambiguousConstructors_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id, time, title from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithAmbiguousConstructors.class));
    assertThat(thrown).hasMessageThat().contains("Ambiguous");
  }

  @Test public void query_withResultType_duplicateColumnNames_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithDuplicateColumnNames.class));
    assertThat(thrown).hasMessageThat().contains("Duplicate");
    assertThat(thrown).hasMessageThat().contains("id");
  }

  @Test public void query_withResultType_emptyColumnName_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithEmptyColumnName.class));
    assertThat(thrown).hasMessageThat().contains("empty");
    assertThat(thrown).hasMessageThat().contains("WithEmptyColumnName");
  }

  @Test public void query_withResultType_blankColumnName_disallowed() throws Exception {
    SafeSql sql = SafeSql.of("select id from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> sql.query(connection(), WithBlankColumnName.class));
    assertThat(thrown).hasMessageThat().contains("blank");
    assertThat(thrown).hasMessageThat().contains("WithBlankColumnName");
  }

  @Test public void query_withResultType_duplicateQueryColumnNames_disallowed() throws Exception {
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

  @Test public void query_withResultBeanType_populated() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    ItemBean bean = new ItemBean();
    bean.setId(testId());
    bean.setItemUuid("uuid");
    bean.setTime(barTime.toInstant());
    bean.setTitle("bar");
    assertThat(
            SafeSql.of("select id, time, title, item_uuid from ITEMS where id = {id}", testId())
                .query(connection(), ItemBean.class))
        .containsExactly(bean);
    assertThat(
            SafeSql.of("select id, time, title, item_uuid from ITEMS where id = {id}", testId())
                .queryLazily(connection(), stmt -> stmt.setFetchSize(2), ItemBean.class)
                .collect(toList()))
        .containsExactly(bean);
  }

  @Test public void query_withResultBeanType_columnLabelInCamelCase() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    ItemBean bean = new ItemBean();
    bean.setId(testId());
    bean.setItemUuid("uuid");
    bean.setTime(barTime.toInstant());
    bean.setTitle("bar");
    assertThat(
            SafeSql.of("select id, time, title, item_uuid AS itemUuid from ITEMS where id = {id}", testId())
                .query(connection(), ItemBean.class))
        .containsExactly(bean);
    assertThat(
            SafeSql.of("select id, time, title, item_uuid AS ItemUuid from ITEMS where id = {id}", testId())
                .queryLazily(connection(), stmt -> stmt.setFetchSize(2), ItemBean.class)
                .collect(toList()))
        .containsExactly(bean);
  }

  @Test public void query_withResultBeanType_columnLabelWithSpace() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    ItemBean bean = new ItemBean();
    bean.setId(testId());
    bean.setItemUuid("uuid");
    bean.setTime(barTime.toInstant());
    bean.setTitle("bar");
    assertThat(
            SafeSql.of("select id, time, title, item_uuid AS \"item uuid\" from ITEMS where id = {id}", testId())
                .query(connection(), ItemBean.class))
        .containsExactly(bean);
    assertThat(
            SafeSql.of("select id, time, title, item_uuid AS \"Item  Uuid\" from ITEMS where id = {id}", testId())
                .queryLazily(connection(), stmt -> stmt.setFetchSize(2), ItemBean.class)
                .collect(toList()))
        .containsExactly(bean);
  }

  @Test public void query_withResultBeanType_columnsFewerThanProperties() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    ItemBean bean = new ItemBean();
    bean.setId(testId());
    bean.setTime(barTime.toInstant());
    bean.setTitle("bar");
    assertThat(
            SafeSql.of("select id, time, title from ITEMS where id = {id}", testId())
                .query(connection(), ItemBean.class))
        .containsExactly(bean);
    assertThat(
            SafeSql.of("select id, time, title from ITEMS where id = {id}", testId())
                .queryLazily(connection(), stmt -> stmt.setFetchSize(2), ItemBean.class)
                .collect(toList()))
        .containsExactly(bean);
  }

  @Test public void query_withResultBeanType_columnsMoreThanProperties() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    BaseItemBean bean = new BaseItemBean();
    bean.setId(testId());
    bean.setTime(barTime.toInstant());
    assertThat(
            SafeSql.of("select id, time, item_uuid from ITEMS where id = {id}", testId())
                .query(connection(), BaseItemBean.class))
        .containsExactly(bean);
    assertThat(
            SafeSql.of("select id, time, item_uuid from ITEMS where id = {id}", testId())
                .queryLazily(connection(), stmt -> stmt.setFetchSize(2), BaseItemBean.class)
                .collect(toList()))
        .containsExactly(bean);
  }

  @Test public void query_withResultBeanType_columnNotFoundInBean() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    SafeSql sql = SafeSql.of("select time from ITEMS where id = {id}", testId());
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> sql.query(connection(), AnotherItemBean.class));
    assertThat(thrown).hasMessageThat().contains("column TIME");
  }

  @Test public void query_withResultBeanType_propertyNotInColumns() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    SafeSql sql = SafeSql.of("select id, time, title from ITEMS where id = {id}", testId());
    Exception thrown = assertThrows(
        SQLException.class,
        () -> sql.query(connection(), AnotherItemBean.class));
    assertThat(thrown).hasMessageThat().contains("CREATION_TIME");
  }

  @Test public void query_withResultBeanType_nullPrimitiveColumn() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    BaseItemBean bean = new BaseItemBean();
    bean.setTime(barTime.toInstant());
    assertThat(
            SafeSql.of("select NULL AS id, time, item_uuid from ITEMS where id = {id}", testId())
                .query(connection(), BaseItemBean.class))
        .containsExactly(bean);
  }

  @Test public void query_withResultBeanType_nullNonPrimitiveColumn() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    BaseItemBean bean = new BaseItemBean();
    bean.setId(testId());
    assertThat(
            SafeSql.of("select id, NULL AS time, item_uuid from ITEMS where id = {id}", testId())
                .query(connection(), BaseItemBean.class))
        .containsExactly(bean);
  }

  @Test public void query_withResultBeanType_setterAnnotatedWithSqlName() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time, item_uuid) VALUES({id}, {title}, {time}, {uuid})",
                    testId(), "bar", barTime, "uuid")
                .update(connection()))
        .isEqualTo(1);
    AnnotatedItemBean bean = new AnnotatedItemBean();
    bean.setId(testId());
    bean.setCreationTime(barTime.toInstant());
    assertThat(
            SafeSql.of("select id, time, item_uuid from ITEMS where id = {id}", testId())
                .query(connection(), AnnotatedItemBean.class))
        .containsExactly(bean);
  }

  @Test public void query_withResultType_genericBean() throws Exception {
    StringBean bean = new StringBean();
    bean.setId(testId());
    bean.setData("foo");
    assertThat(
            SafeSql.of("select {id} AS id, 'foo' AS data", testId())
                .query(connection(), StringBean.class))
        .containsExactly(bean);
    assertThat(
        SafeSql.of("select {id} AS id, 'foo' AS data", testId())
            .queryLazily(connection(), StringBean.class)
            .collect(toList()))
        .containsExactly(bean);
    assertThat(
        SafeSql.of("select {id} AS id, 'foo' AS data", testId())
            .queryForOne(connection(), StringBean.class))
        .hasValue(bean);
  }

  @Test public void queryForOne_firstRowIsTurned() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId(), "bar", barTime)
                .update(connection()))
        .isEqualTo(1);
    assertThat(
            SafeSql.of("insert into ITEMS(id, title, time) VALUES({id}, {title}, {time})", testId() + 1, "bar2", barTime)
                .update(connection()))
      .isEqualTo(1);
    assertThat(
            SafeSql.of("select time from ITEMS where id IN ({id}, {id2}) order by id", testId(), /* id2 */ testId() + 1)
                .queryForOne(connection(), ZonedDateTime.class)
                .map(ZonedDateTime::toInstant))
        .hasValue(barTime.toInstant());
  }

  @Test public void queryForOne_noRowIsReturned() throws Exception {
    assertThat(
            SafeSql.of("select time from ITEMS where id IN ({id})", testId())
                .queryForOne(connection(), ZonedDateTime.class))
        .isEmpty();
  }

  @Test public void queryForOne_nullCausesNpe() throws Exception {
    SafeSql sql = SafeSql.of("select null AS id");
    assertThrows(NullPointerException.class, () -> sql.queryForOne(connection(), Long.class));
  }

  @Test public void queryLazily_withResultType_parametersAnnotatedWithSqlName() throws Exception {
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
            .queryLazily(connection(), stmt -> stmt.setFetchSize(2), Item.class)
            .collect(toList()))
    .containsExactly(new Item(testId(), "bar", barTime.toInstant()));
  }

  @Test public void queryLazily_withMaxRows() throws Exception {
    ZonedDateTime barTime = ZonedDateTime.of(2024, 11, 1, 10, 20, 30, 0, ZoneId.of("UTC"));
    assertThat(
            SafeSql.of("select title from (select '{v}' as title union all select 'b' as title) order by title", "a")
                .queryLazily(connection(), stmt -> stmt.setMaxRows(1), String.class)
                .collect(toList()))
        .containsExactly("a");
    assertThat(
            SafeSql.of("select title from (select '{v}' as title union all select 'b' as title) order by title", "c")
                .queryLazily(connection(), stmt -> stmt.setMaxRows(1), String.class)
                .collect(toList()))
        .containsExactly("b");
  }

  @Test public void prepareToQuery_withResultType() throws Exception {
    assertThat(
            SafeSql.of("insert into ITEMS(id, title) VALUES({id}, {title})", testId(), "foo")
                .update(connection()))
        .isEqualTo(1);
    StringFormat.Template<List<Item>> template = SafeSql.prepareToQuery(
        connection(),
        "select id, title from ITEMS where title = {...} and id in ({id})",
        Item.class);
    assertThat(template.with("foo", testId())).containsExactly(new Item(testId(), "foo"));
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

    Item(@SqlName("id") int id, @SqlName("title") String title) {
      this(id, title, null);
    }

    Item() {
      this(0, "", Instant.EPOCH);
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

  static class BaseItemBean {
    private int id;
    private Instant time;

    public void setId(int id) {
      this.id = id;
    }

    public void setTime(Instant time) {
      this.time = time;
    }

    @Override public boolean equals(Object that) {
      return that != null && toString().equals(that.toString());
    }

    @Override public int hashCode() {
      return toString().hashCode();
    }

    @Override public String toString() {
      return "id=" + id + ", time=" + time;
    }
  }

  static class ItemBean extends BaseItemBean {
    private String title;
    private String itemUuid;

    public void setTitle(String title) {
      this.title = title;
    }

    public void setItemUuid(String itemUuid) {
      this.itemUuid = itemUuid;
    }

    @Override public boolean equals(Object that) {
      return that != null && toString().equals(that.toString());
    }

    @Override public int hashCode() {
      return toString().hashCode();
    }

    @Override public String toString() {
      return super.toString() + ", title=" + title + ", itemUuid=" + itemUuid;
    }
  }

  static class AnotherItemBean {
    private int id;
    private Instant time;

    public void setId(int id) {
      this.id = id;
    }

    public void setCreationTime(Instant time) {
      this.time = time;
    }

    @Override public boolean equals(Object that) {
      return that != null && toString().equals(that.toString());
    }

    @Override public int hashCode() {
      return toString().hashCode();
    }

    @Override public String toString() {
      return "id=" + id + ", time=" + time;
    }
  }

  static class AnnotatedItemBean {
    private int id;
    private Instant time;

    public void setId(int id) {
      this.id = id;
    }

    @SqlName("time")
    public void setCreationTime(Instant time) {
      this.time = time;
    }

    @Override public boolean equals(Object that) {
      return that != null && toString().equals(that.toString());
    }

    @Override public int hashCode() {
      return toString().hashCode();
    }

    @Override public String toString() {
      return "id=" + id + ", time=" + time;
    }
  }

  static class GenericBean<T> {
    private int id;
    private T data;

    public void setId(int id) {
      this.id = id;
    }

    public void setData(T data) {
      this.data = data;
    }

    @Override public boolean equals(Object that) {
      return that != null && toString().equals(that.toString());
    }

    @Override public int hashCode() {
      return toString().hashCode();
    }

    @Override public String toString() {
      return "id=" + id + ", data=" + data;
    }
  }

  static class StringBean extends GenericBean<String> {}

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