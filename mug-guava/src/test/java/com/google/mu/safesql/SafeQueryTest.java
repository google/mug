package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.safesql.SafeQuery.template;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.StringFormat;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

/**
 * All the generated BigQuery queries were verified against BigQuery. If you change any of them,
 * make sure to verify them in BigQuery.
 */
@RunWith(TestParameterInjector.class)
public final class SafeQueryTest {

  @BeforeClass
  public static void setUpTrustedType() {
    System.setProperty("com.google.mu.safesql.SafeQuery.trusted_sql_type", TrustedSql.class.getName());
  }

  @Test
  public void emptyTemplate() {
    assertThat(template("").with()).isEqualTo(SafeQuery.of(""));
  }

  @Test
  public void singleQuoteEscapedWithinSingleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = '{id}'").with("'v'"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '\\'v\\''"));
  }

  @Test
  public void backslashEscapedWithinSingleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = '{id}'").with("\\2"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '\\\\2'"));
  }

  @Test
  public void doubleQuoteNotEscapedWithinSingleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = '{id}'").with("\"v\""))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '\"v\"'"));
  }

  @Test
  public void doubleQuoteEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("\"v\""))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"\\\"v\\\"\""));
  }

  @Test
  public void backslashEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("\\1"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"\\\\1\""));
  }

  @Test
  public void newLineEscapedWithinSingleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = '{id}'").with("\n"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '\\u000A'"));
  }

  @Test
  public void newLineEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("\n"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"\\u000A\""));
  }

  @Test
  public void newLineDisallowedWithinBackticks() {
    StringFormat.To<SafeQuery> template = template("SELECT * FROM `{tbl}`");
    assertThrows(IllegalArgumentException.class, () -> template.with(/* tbl */ "a\nb"));
  }

  @Test
  public void carriageReturnEscapedWithinSingleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = '{id}'").with("\r"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '\\u000D'"));
  }

  @Test
  public void carriageReturnEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("\r"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"\\u000D\""));
  }

  @Test
  public void carriageReturnDisallowedWithinBackticks() {
    StringFormat.To<SafeQuery> template = template("SELECT * FROM `{tbl}`");
    assertThrows(IllegalArgumentException.class, () -> template.with(/* tbl */ "a\rb"));
  }

  @Test
  public void carriageReturnAndLineFeedEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("a\r\nb"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"a\\u000D\\u000Ab\""));
  }

  @Test
  public void singleQuoteNotEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("'v'"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"'v'\""));
  }

  @Test
  public void nullPlaceholder() {
    assertThat(template("SELECT * FROM tbl WHERE id in ({v1}, {v2})").with(123, null))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id in (123, NULL)"));
  }

  @Test
  public void quotedNonNullPlaceholder() {
    assertThat(template("SELECT * FROM tbl WHERE id in ('{v1}', \"{v2}\")").with(123, 456))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id in ('123', \"456\")"));
  }

  @Test
  public void quotedNullPlaceholder() {
    assertThrows(
        NullPointerException.class,
        () -> template("SELECT * FROM tbl WHERE id in ('{v1}', '{v2}')").with(123, null));
  }

  @Test
  public void booleanPlaceholder() {
    assertThat(template("SELECT * FROM tbl WHERE status in ({v1}, {v2})").with(true, false))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE status in (TRUE, FALSE)"));
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void charPlaceholder_notAllowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT * FROM tbl WHERE id = {id}").with('\\'));
    assertThat(thrown).hasMessageThat().contains("'{id}'");
  }

  @Test
  public void numberPlaceholder() {
    assertThat(template("SELECT * FROM tbl WHERE id = {id}").with(123))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 123"));
  }

  @Test
  public void enumPlaceholder() {
    assertThat(template("SELECT * FROM `{tbl}`").with(/* tbl */ JobType.SCRIPT))
        .isEqualTo(SafeQuery.of("SELECT * FROM `SCRIPT`"));
  }

  @Test
  public void listOfColumnsPlaceholder() {
    assertThat(template("SELECT `{cols}` FROM tbl").with(/* cols */ asList("foo", "bar")))
        .isEqualTo(SafeQuery.of("SELECT `foo`, `bar` FROM tbl"));
  }

  @Test
  public void listOfColumnNamesFromEnumNamesPlaceholder() {
    assertThat(
            template("SELECT `{cols}` FROM tbl")
                .with(/* cols */ asList(JobType.SCRIPT, JobType.QUERY)))
        .isEqualTo(SafeQuery.of("SELECT `SCRIPT`, `QUERY` FROM tbl"));
  }

  @Test
  public void listOfUnquotedNumbersPlaceholder() {
    assertThat(template("SELECT * FROM tbl WHERE id in ({ids})").with(/* ids */ asList(1, 2)))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id in (1, 2)"));
  }

  @Test
  public void listOfUnquotedStringPlaceholder() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT ({expr}) FROM tbl").with(/* expr */ asList("foo", "bar")));
    assertThat(thrown).hasMessageThat().contains("string literals must be quoted like '{expr}'");
  }

  @Test
  public void listOfUnquotedCharacterPlaceholder() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT ({cols}) FROM tbl").with(/* cols */ asList('f', '"')));
    assertThat(thrown).hasMessageThat().contains("string literals must be quoted like '{cols}'");
  }

  @Test
  public void listOfSingleQuotedStringPlaceholder() {
    assertThat(template("SELECT '{exprs}' FROM tbl").with(/* exprs */ asList("foo", "bar's")))
        .isEqualTo(SafeQuery.of("SELECT 'foo', 'bar\\'s' FROM tbl"));
  }

  @Test
  public void listOfDoubleQuotedStringPlaceholder() {
    assertThat(template("SELECT \"{exprs}\" FROM tbl").with(/* exprs */ asList("foo", "bar\"s")))
        .isEqualTo(SafeQuery.of("SELECT \"foo\", \"bar\\\"s\" FROM tbl"));
  }

  @Test
  public void quotedNullListElement() {
    assertThrows(
        NullPointerException.class,
        () -> template("SELECT '{expr}' FROM tbl").with(/* expr */ asList("foo", null)));
  }

  @Test
  public void unquotedNullListElement() {
    assertThat(template("SELECT {expr} FROM tbl").with(/* expr */ asList(1, null)))
        .isEqualTo(SafeQuery.of("SELECT 1, NULL FROM tbl"));
  }

  @Test
  public void listOfNumbersPlaceholder() {
    assertThat(template("SELECT [{expr}] FROM tbl").with(/* expr */ asList(123, 1.2)))
        .isEqualTo(SafeQuery.of("SELECT [123, 1.2] FROM tbl"));
  }

  @Test
  public void listOfQuotedEnumsPlaceholder() {
    assertThat(template("SELECT ['{expr}'] FROM tbl").with(/* expr */ asList(JobType.SCRIPT)))
        .isEqualTo(SafeQuery.of("SELECT ['script'] FROM tbl"));
  }

  @Test
  public void listOfUnquotedEnumsPlaceholder() {
    assertThat(template("SELECT {expr} FROM tbl").with(/* expr */ asList(JobType.SCRIPT)))
        .isEqualTo(SafeQuery.of("SELECT SCRIPT FROM tbl"));
  }

  @Test
  public void listOfSafeBigQueriesPlaceholder() {
    assertThat(
            template("SELECT foo FROM {tbls}")
                .with(/* tbls */ asList(SafeQuery.of("a"), SafeQuery.of("b"))))
        .isEqualTo(SafeQuery.of("SELECT foo FROM a, b"));
  }

  @Test
  public void listOfSafeBigQueries_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                template("SELECT '{exprs}' FROM tbl")
                    .with(/* exprs */ asList(SafeQuery.of("select 123"))));
    assertThat(thrown).hasMessageThat().contains("SafeQuery should not be quoted: '{exprs}'");
  }

  @Test
  public void subQueryAllowed() {
    SafeQuery sub = template("SELECT '{name}'").with("foo's");
    assertThat(template("SELECT * from ({sub})").with(sub))
        .isEqualTo(SafeQuery.of("SELECT * from (SELECT 'foo\\'s')"));
  }

  @Test
  public void stringBackquoted() {
    assertThat(template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo"))
        .isEqualTo(SafeQuery.of("SELECT * from `foo` WHERE TRUE"));
  }

  @Test
  public void stringWithSpaceBackquoted() {
    assertThat(template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo"))
        .isEqualTo(SafeQuery.of("SELECT * from `foo` WHERE TRUE"));
  }

  @Test
  public void stringWithDashBackquoted() {
    assertThat(template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo-bar"))
        .isEqualTo(SafeQuery.of("SELECT * from `foo-bar` WHERE TRUE"));
  }

  @Test
  public void stringWithColonBackquoted() {
    assertThat(template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo:bar"))
        .isEqualTo(SafeQuery.of("SELECT * from `foo:bar` WHERE TRUE"));
  }

  @Test
  public void stringWithDotBackquoted() {
    assertThat(template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo.bar"))
        .isEqualTo(SafeQuery.of("SELECT * from `foo.bar` WHERE TRUE"));
  }

  @Test
  public void stringWithUnderscoreBackquoted() {
    assertThat(template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo_bar"))
        .isEqualTo(SafeQuery.of("SELECT * from `foo_bar` WHERE TRUE"));
  }

  @Test
  public void stringAlreadyBackquoted() {
    assertThat(template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "`foo`"))
        .isEqualTo(SafeQuery.of("SELECT * from `foo` WHERE TRUE"));
  }

  @Test
  public void stringWithLeadingBacktick_cannotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "`foo"));
    assertThat(thrown).hasMessageThat().contains("{tbl}");
  }

  @Test
  public void stringWithTrailingBacktick_cannotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo`"));
    assertThat(thrown).hasMessageThat().contains("{tbl}");
  }

  @Test
  public void stringWithQuoteInside_cannotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "foo's"));
    assertThat(thrown).hasMessageThat().contains("{tbl}");
  }

  @Test
  public void stringWithBacktickInside_cannotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "f`o`o"));
    assertThat(thrown).hasMessageThat().contains("{tbl}");
  }

  @Test
  public void stringWithSemicolonInside_cannotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT * from `{tbl}` WHERE TRUE").with(/* tbl */ "`tl;dr"));
    assertThat(thrown).hasMessageThat().contains("{tbl}");
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void safeQueryShouldNotBeSingleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT '{query}' WHERE TRUE").with(SafeQuery.of("1")));
    assertThat(thrown).hasMessageThat().contains("SafeQuery should not be quoted: '{query}'");
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void safeQueryShouldNotBeDoubleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> template("SELECT \"{query}\" WHERE TRUE").with(SafeQuery.of("1")));
    assertThat(thrown).hasMessageThat().contains("SafeQuery should not be quoted: \"{query}\"");
  }

  @Test
  public void localDateQuoted() {
    assertThat(template("SELECT '{date}' FROM tbl").with(LocalDate.of(2023, 1, 1)))
        .isEqualTo(SafeQuery.of("SELECT '2023-01-01' FROM tbl"));
  }

  @Test
  public void safeQueryBackquoted() {
    assertThat(template("SELECT * from `{tbl}`").with(/* tbl */ SafeQuery.of("foo")))
        .isEqualTo(SafeQuery.of("SELECT * from `foo`"));
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void subQueryDisallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> template("SELECT * FROM {table}").with("jobs"));
    assertThat(thrown).hasMessageThat().contains("string literals must be quoted like '{table}'");
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void stringLiteralDisallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> template("SELECT * FROM {table}").with("jobs"));
    assertThat(thrown).hasMessageThat().contains("string literals must be quoted like '{table}'");
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void backquoteAndSingleQuoteMixed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> template("SELECT * FROM `{tbl}'").with("jobs"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}'");
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void singleQuoteAndBackquoteMixed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> template("SELECT * FROM '{tbl}`").with("jobs"));
    assertThat(thrown).hasMessageThat().contains("'{tbl}`");
  }

  @SuppressWarnings("StringFormatPlaceholderNamesCheck")
  @Test
  public void badPlaceholderName() {
    assertThat(
            template("SELECT * FROM {table} WHERE id = {id?}")
                .with(/* table */ SafeQuery.of("jobs"), /* id */ SafeQuery.of("x")))
        .isEqualTo(SafeQuery.of("SELECT * FROM jobs WHERE id = x"));
  }

  @SuppressWarnings("StringFormatArgsCheck")
  @Test
  public void placeholderNameDoesNotMatch() {
    StringFormat.To<SafeQuery> template = template("SELECT * FROM {table} WHERE id = {id}");
    assertThat(template.with(/* table */ SafeQuery.of("jobs"), /* id */ SafeQuery.of("x")))
        .isEqualTo(SafeQuery.of("SELECT * FROM jobs WHERE id = x"));
  }

  @SuppressWarnings("StringFormatArgsCheck")
  @Test
  public void wrongNumberOfArgs() {
    assertThrows(
        IllegalArgumentException.class,
        () -> template("SELECT * FROM {table} WHERE id = {id}").with("jobs"));
  }

  @Test
  public void joiningByStringConstant() {
    assertThat(Stream.of(SafeQuery.of("a"), SafeQuery.of("b")).collect(SafeQuery.joining(" AND ")))
        .isEqualTo(SafeQuery.of("a AND b"));
  }

  @Test
  public void andCollector_empty() {
    ImmutableList<SafeQuery> queries = ImmutableList.of();
    assertThat(queries.stream().collect(SafeQuery.and())).isEqualTo(SafeQuery.of("TRUE"));
  }

  @Test
  public void andCollector_singleCondition() {
    ImmutableList<SafeQuery> queries = ImmutableList.of(SafeQuery.of("a = 1"));
    assertThat(queries.stream().collect(SafeQuery.and())).isEqualTo(SafeQuery.of("(a = 1)"));
  }

  @Test
  public void andCollector_twoConditions() {
    ImmutableList<SafeQuery> queries =
        ImmutableList.of(SafeQuery.of("a = 1"), SafeQuery.of("b = 2 OR c = 3"));
    assertThat(queries.stream().collect(SafeQuery.and()))
        .isEqualTo(SafeQuery.of("(a = 1) AND (b = 2 OR c = 3)"));
  }

  @Test
  public void andCollector_threeConditions() {
    ImmutableList<SafeQuery> queries =
        ImmutableList.of(
            SafeQuery.of("a = 1"), SafeQuery.of("b = 2 OR c = 3"), SafeQuery.of("d = 4"));
    assertThat(queries.stream().collect(SafeQuery.and()))
        .isEqualTo(SafeQuery.of("(a = 1) AND (b = 2 OR c = 3) AND (d = 4)"));
  }

  @Test
  public void orCollector_empty() {
    ImmutableList<SafeQuery> queries = ImmutableList.of();
    assertThat(queries.stream().collect(SafeQuery.or())).isEqualTo(SafeQuery.of("FALSE"));
  }

  @Test
  public void orCollector_singleCondition() {
    ImmutableList<SafeQuery> queries = ImmutableList.of(SafeQuery.of("a = 1"));
    assertThat(queries.stream().collect(SafeQuery.or())).isEqualTo(SafeQuery.of("(a = 1)"));
  }

  @Test
  public void orCollector_twoConditions() {
    ImmutableList<SafeQuery> queries =
        ImmutableList.of(SafeQuery.of("a = 1"), SafeQuery.of("b = 2 AND c = 3"));
    assertThat(queries.stream().collect(SafeQuery.or()))
        .isEqualTo(SafeQuery.of("(a = 1) OR (b = 2 AND c = 3)"));
  }

  @Test
  public void orCollector_threeConditions() {
    ImmutableList<SafeQuery> queries =
        ImmutableList.of(
            SafeQuery.of("a = 1"), SafeQuery.of("b = 2 AND c = 3"), SafeQuery.of("d = 4"));
    assertThat(queries.stream().collect(SafeQuery.or()))
        .isEqualTo(SafeQuery.of("(a = 1) OR (b = 2 AND c = 3) OR (d = 4)"));
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(SafeQuery.of("SELECT *"), SafeQuery.of("SELECT *"))
        .addEqualityGroup(SafeQuery.of("SELECT * FROM tbl"))
        .testEquals();
  }

  @Test
  public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(SafeQuery.class);
  }

  private enum JobType {
    SCRIPT,
    QUERY;

    @Override
    public String toString() {
      return Ascii.toLowerCase(name());
    }
  }

  @Test
  public void listOfTrustedSqlStringsPlaceholder() {
    assertThat(
            template("SELECT {exprs} FROM tbl")
                .with(/* exprs */ asList(new TrustedSql("123"))))
        .isEqualTo(SafeQuery.of("SELECT 123 FROM tbl"));
  }

  @Test
  public void listOfQuotedTrustedSqlStrings_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                template("SELECT '{exprs}' FROM tbl")
                    .with(/* exprs */ asList(new TrustedSql("123"))));
    assertThat(thrown)
        .hasMessageThat()
        .contains("TrustedSql should not be quoted: '{exprs}'");
  }

  @Test
  public void trustedSqlStringAllowed() {
    assertThat(
            template("SELECT * from ({sub}) WHERE TRUE")
                .with(/* sub */ new TrustedSql("SELECT 1")))
        .isEqualTo(SafeQuery.of("SELECT * from (SELECT 1) WHERE TRUE"));
  }

  @Test
  public void trustedSqlStringBackquoted() {
    assertThat(
            template("SELECT * from `{tbl}` WHERE TRUE")
                .with(/* tbl */ new TrustedSql("foo")))
        .isEqualTo(SafeQuery.of("SELECT * from `foo` WHERE TRUE"));
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void trustedSqlStringShouldNotBeSingleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                template("SELECT '{value}' WHERE TRUE")
                    .with(/* value */ new TrustedSql("1")));
    assertThat(thrown)
        .hasMessageThat()
        .contains("TrustedSql should not be quoted: '{value}'");
  }

  @Test
  @SuppressWarnings("SafeQueryArgsCheck")
  public void trustedSqlStringShouldNotBeDoubleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                template("SELECT \"{value}\" WHERE TRUE")
                    .with(/* value */ new TrustedSql("1")));
    assertThat(thrown)
        .hasMessageThat()
        .contains("TrustedSql should not be quoted: \"{value}\"");
  }

  @Test
  public void backspaceCharacterQuoted() {
    SafeQuery query = template("'{id}'").with("\b");
    assertThat(query.toString()).isEqualTo("'\\u0008'");
  }

  @Test
  public void nulCharacterQuoted() {
    SafeQuery query = template("'{id}'").with("\0");
    assertThat(query.toString()).isEqualTo("'\\u0000'");
  }

  @Test
  public void pageBreakCharacterQuoted() {
    SafeQuery query = template("'{id}'").with("\f");
    assertThat(query.toString()).isEqualTo("'\\u000C'");
  }

  @Test
  public void backspaceCharacterBacktickQuoted() {
    StringFormat.To<SafeQuery> query = template("`{name}`");
    assertThrows(IllegalArgumentException.class, () -> query.with(/* name */ "\b"));
  }

  @Test
  public void nulCharacterBacktickQuoted() {
    StringFormat.To<SafeQuery> query = template("`{name}`");
    assertThrows(IllegalArgumentException.class, () -> query.with(/* name */ "\0"));
  }

  @Test
  public void pageBreakCharacterBacktickQuoted() {
    StringFormat.To<SafeQuery> query = template("`{name}`");
    assertThrows(IllegalArgumentException.class, () -> query.with(/* name */ "\f"));
  }

  @Test
  public void unicodeSmugglingInStringLiteralNotEffective() {
    SafeQuery query = template("'{id}'").with("ʻ OR TRUE OR ʼʼ=ʼ");
    assertThat(query.toString()).isEqualTo("'\\u02BB" + " OR TRUE OR \\u02BC\\u02BC=\\u02BC'");
  }

  @Test
  public void unicodeSmugglingInIdentifierNotEffective() {
    SafeQuery query = template("`{tbl}`").with("ʻ OR TRUE OR ʼʼ=ʼ");
    assertThat(query.toString()).isEqualTo("`\\u02BB" + " OR TRUE OR \\u02BC\\u02BC=\\u02BC`");
  }

  @Test
  public void bytePositivePlaceholderValueFilled() {
    byte value = 1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1"));
  }

  @Test
  public void byteZeroPlaceholderValueFilled() {
    byte value = 0;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void byteNegativePlaceholderValueFilled() {
    byte value = -1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-1)"));
  }

  @Test
  public void byteMaxPlaceholderValueFilled() {
    byte value = Byte.MAX_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 127"));
  }

  @Test
  public void byteMinPlaceholderValueFilled() {
    byte value = Byte.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-128)"));
  }

  @Test
  public void byteMinPlaceholderValueQuotedFilled() {
    byte value = Byte.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = '{id}'", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '-128'"));
  }

  @Test
  public void shortPositivePlaceholderValueFilled() {
    short value = 1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1"));
  }

  @Test
  public void shortZeroPlaceholderValueFilled() {
    short value = 0;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void shortNegativePlaceholderValueFilled() {
    short value = -1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-1)"));
  }

  @Test
  public void shortMaxPlaceholderValueFilled() {
    short value = Short.MAX_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 32767"));
  }

  @Test
  public void shortMinPlaceholderValueFilled() {
    short value = Short.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-32768)"));
  }

  @Test
  public void shortMinPlaceholderValueQuotedFilled() {
    short value = Short.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = '{id}'", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '-32768'"));
  }

  @Test
  public void intPositivePlaceholderValueFilled() {
    int value = 1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1"));
  }

  @Test
  public void intZeroPlaceholderValueFilled() {
    int value = 0;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void intNegativePlaceholderValueFilled() {
    int value = -1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-1)"));
  }

  @Test
  public void intMaxPlaceholderValueFilled() {
    int value = Integer.MAX_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 2147483647"));
  }

  @Test
  public void intMinPlaceholderValueFilled() {
    int value = Integer.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-2147483648)"));
  }

  @Test
  public void intMinPlaceholderValueQuotedFilled() {
    int value = Integer.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = '{id}'", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '-2147483648'"));
  }

  @Test
  public void longPositivePlaceholderValueFilled() {
    int value = 1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1"));
  }

  @Test
  public void longZeroPlaceholderValueFilled() {
    long value = 0;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void longNegativePlaceholderValueFilled() {
    long value = -1;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-1)"));
  }

  @Test
  public void longMaxPlaceholderValueFilled() {
    long value = Long.MAX_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 9223372036854775807"));
  }

  @Test
  public void longMinPlaceholderValueFilled() {
    long value = Long.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-9223372036854775808)"));
  }

  @Test
  public void longMinPlaceholderValueQuotedFilled() {
    long value = Long.MIN_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = '{id}'", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '-9223372036854775808'"));
  }

  @Test
  public void doublePositivePlaceholderValueFilled() {
    double value = 1.5;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1.5"));
  }

  @Test
  public void doubleZeroPlaceholderValueFilled() {
    double value = 0;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void doubleNegativePlaceholderValueFilled() {
    double value = -1.5;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-1.5)"));
  }

  @Test
  public void doubleMaxPlaceholderValueFilled() {
    double value = Double.MAX_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value).toString())
        .startsWith("SELECT * FROM tbl WHERE id = 17976931348623157000000");
  }

  @Test
  public void doubleInfinitePlaceholderValue_disallowed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", Double.NEGATIVE_INFINITY));
    assertThrows(
        IllegalArgumentException.class,
        () -> SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", Double.POSITIVE_INFINITY));
  }

  @Test
  public void doubleNanPlaceholderValue_disallowed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", Double.NaN));
  }

  @Test
  public void floatPositivePlaceholderValueFilled() {
    float value = 1.5F;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1.5"));
  }

  @Test
  public void floatZeroPlaceholderValueFilled() {
    float value = 0;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void floatNegativePlaceholderValueFilled() {
    float value = -1.5F;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = (-1.5)"));
  }

  @Test
  public void floatMaxPlaceholderValueFilled() {
    float value = Float.MAX_VALUE;
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", value).toString())
        .startsWith("SELECT * FROM tbl WHERE id = 34028234663852886000000");
  }

  @Test
  public void floatInfinitePlaceholderValue_disallowed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", Float.NEGATIVE_INFINITY));
    assertThrows(
        IllegalArgumentException.class,
        () -> SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", Float.POSITIVE_INFINITY));
  }

  @Test
  public void floatNanPlaceholderValue_disallowed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", Float.NaN));
  }

  @Test
  public void unsignedLongPlaceholderValueFilled() {
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", UnsignedLong.ONE))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1"));
  }

  @Test
  public void unsignedLongZeroPlaceholderValueFilled() {
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", UnsignedLong.ZERO))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void unsignedLongMaxPlaceholderValueFilled() {
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", UnsignedLong.MAX_VALUE))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 18446744073709551615"));
  }

  @Test
  public void unsignedIntegerPlaceholderValueFilled() {
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", UnsignedInteger.ONE))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 1"));
  }

  @Test
  public void unsignedIntegerZeroPlaceholderValueFilled() {
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", UnsignedInteger.ZERO))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 0"));
  }

  @Test
  public void unsignedIntegerMaxPlaceholderValueFilled() {
    assertThat(SafeQuery.of("SELECT * FROM tbl WHERE id = {id}", UnsignedInteger.MAX_VALUE))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = 4294967295"));
  }

  @Test
  public void of_withArgs() {
    SafeQuery query = SafeQuery.of("`{tbl}`", "ʻ OR TRUE OR ʼʼ=ʼ");
    assertThat(query.toString()).isEqualTo("`\\u02BB" + " OR TRUE OR \\u02BC\\u02BC=\\u02BC`");
  }

  static final class TrustedSql {
    private final String sql;

    TrustedSql(String sql) {
      this.sql = sql;
    }

    @Override public String toString() {
      return sql;
    }
  }
}
