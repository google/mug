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
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '\\n'"));
  }

  @Test
  public void newLineEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("\n"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"\\n\""));
  }

  @Test
  public void newLineDisallowedWithinBackticks() {
    StringFormat.To<SafeQuery> template = template("SELECT * FROM `{tbl}`");
    assertThrows(IllegalArgumentException.class, () -> template.with(/* tbl */ "a\nb"));
  }

  @Test
  public void carriageReturnEscapedWithinSingleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = '{id}'").with("\r"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = '\\r'"));
  }

  @Test
  public void carriageReturnEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("\r"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"\\r\""));
  }

  @Test
  public void carriageReturnDisallowedWithinBackticks() {
    StringFormat.To<SafeQuery> template = template("SELECT * FROM `{tbl}`");
    assertThrows(IllegalArgumentException.class, () -> template.with(/* tbl */ "a\rb"));
  }

  @Test
  public void carriageReturnAndLineFeedEscapedWithinDoubleQuote() {
    assertThat(template("SELECT * FROM tbl WHERE id = \"{id}\"").with("a\r\nb"))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE id = \"a\\r\\nb\""));
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
  public void unicodeSmugglingInStringLiteralNotEffective() {
    SafeQuery query = template("'{id}'").with("ʻ OR TRUE OR ʼʼ=ʼ");
    assertThat(query.toString()).isEqualTo("'\\u02BB" + " OR TRUE OR \\u02BC\\u02BC=\\u02BC'");
  }

  @Test
  public void unicodeSmugglingInIdentifierNotEffective() {
    SafeQuery query = template("`{tbl}`").with("ʻ OR TRUE OR ʼʼ=ʼ");
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
