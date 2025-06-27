package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.safesql.SafeSql.template;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class SafeSqlTest {
  @Test
  public void emptyTemplate() {
    assertThat(template("").with()).isEqualTo(SafeSql.of(""));
  }

  @Test
  public void emptySql() {
    assertThat(SafeSql.EMPTY.toString()).isEmpty();
    assertThat(SafeSql.EMPTY.debugString()).isEmpty();
  }

  @Test
  public void singleStringParameter() {
    SafeSql sql = SafeSql.of("select {str}", "foo");
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.debugString()).isEqualTo("select ? /* foo */");
  }

  @Test
  public void singleIntParameter() {
    SafeSql sql = SafeSql.of("select {i}", 123);
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.debugString()).isEqualTo("select ? /* 123 */");
  }

  @Test
  public void singleBoolParameter() {
    SafeSql sql = SafeSql.of("select {bool}", true);
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.debugString()).isEqualTo("select ? /* true */");
  }

  @Test
  public void conditionalOperator_evaluateToTrue() {
    boolean showsId = true;
    assertThat(SafeSql.of("SELECT {shows_id->id,} name FROM tbl", showsId))
        .isEqualTo(SafeSql.of("SELECT id, name FROM tbl"));
  }

  @Test
  public void conditionalOperator_evaluateToFalse() {
    boolean showsId = false;
    assertThat(SafeSql.of("SELECT {shows_id->id,} name FROM tbl", showsId))
        .isEqualTo(SafeSql.of("SELECT  name FROM tbl"));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void conditionalOperator_nonBooleanArg_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {shows_id->id,} name FROM tbl", SafeSql.of("showsId")));
    assertThat(thrown).hasMessageThat().contains("{shows_id->");
    assertThat(thrown).hasMessageThat().contains("SafeSql");
  }

  @Test
  public void conditionalOperator_nullArg_disallowed() {
    Boolean showsId = null;
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {shows_id->id,} name FROM tbl", showsId));
    assertThat(thrown).hasMessageThat().contains("{shows_id->");
    assertThat(thrown).hasMessageThat().contains("null");
  }

  @Test
  public void conditionalOperator_cannotBeBacktickQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT `{shows_id->id}` name FROM tbl", true));
    assertThat(thrown).hasMessageThat().contains("{shows_id->");
    assertThat(thrown).hasMessageThat().contains("backtick quoted");
  }

  @Test
  @SuppressWarnings("LabsStringFormatArgsCheck")
  public void optionalOperator_present() {
    Optional<Integer> id = Optional.of(123);
    assertThat(SafeSql.of("SELECT {id? -> id? AS id,} name FROM tbl", id))
        .isEqualTo(SafeSql.of("SELECT {id} AS id, name FROM tbl", id.get()));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_absent() {
    Optional<Integer> id = Optional.empty();
    assertThat(SafeSql.of("SELECT { id? -> id? AS id,} name FROM tbl", id))
        .isEqualTo(SafeSql.of("SELECT  name FROM tbl"));
  }

  @Test
  @SuppressWarnings("LabsStringFormatArgsCheck")
  public void optionalOperator_optionalParameterReferencedMultipleTimes() {
    Optional<String> id = Optional.of("myId");
    SafeSql sql = SafeSql.of("SELECT {id? -> id? AS id, UPPER('id?') AS title, } name FROM tbl", id);
    assertThat(sql)
        .isEqualTo(SafeSql.of("SELECT {id} AS id, UPPER('{id}') AS title, name FROM tbl", "myId", "myId"));
    assertThat(sql.debugString())
        .isEqualTo("SELECT ? /* myId */ AS id, UPPER(? /* myId */) AS title, name FROM tbl");
  }

  @Test
  @SuppressWarnings("LabsStringFormatArgsCheck")
  public void optionalOperator_withLikeOperator_present() {
    Optional<String> name = Optional.of("foo");
    SafeSql sql = SafeSql.of("SELECT * FROM tbl WHERE 1=1 {name? -> AND name LIKE '%name?%'}", name);
    assertThat(sql)
        .isEqualTo(SafeSql.of("SELECT * FROM tbl WHERE 1=1 AND name LIKE '%{name}%'", name.get()));
    assertThat(sql.debugString())
        .isEqualTo("SELECT * FROM tbl WHERE 1=1 AND name LIKE ? /* %foo% */ ESCAPE '^'");
  }

  @Test
  @SuppressWarnings("LabsStringFormatArgsCheck")
  public void optionalOperator_withLikeOperator_absent() {
    Optional<String> name = Optional.empty();
    assertThat(SafeSql.of("SELECT * FROM tbl WHERE 1=1 {name? -> AND name LIKE '%name?%'}", name))
        .isEqualTo(SafeSql.of("SELECT * FROM tbl WHERE 1=1 "));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_trailingSpaceAndNewlinesIgnored() {
    Optional<Integer> id = Optional.of(123);
    assertThat(SafeSql.of("SELECT {id? -> id? AS id, \n} name FROM tbl", id))
        .isEqualTo(SafeSql.of("SELECT {id} AS id, name FROM tbl", id.get()));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_parameterReferencedMoreThanOnce() {
    Optional<Integer> id = Optional.of(123);
    assertThat(
            SafeSql.of(
                "SELECT {id? -> IFNULL(id?, NULL, id?) AS id,} name FROM tbl", id))
        .isEqualTo(
            SafeSql.of(
                "SELECT IFNULL({id}, NULL, {id}) AS id, name FROM tbl", id.get(), id.get()));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_missingQuestionMark() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {id -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{id->}");
    assertThat(thrown).hasMessageThat().contains("followed by '?'");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_redundantQuestionMark() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {id?? -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{id??->}");
    assertThat(thrown).hasMessageThat().contains("followed by '?'");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_spaceInTheMiddleOfName() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {the id? -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{the id?->}");
    assertThat(thrown).hasMessageThat().contains("followed by '?'");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_typoInParameterName() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {id? -> bad_id? AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("bad_id?");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void optionalOperator_missingQuestionMarkInReference() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {id? -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{id?->}");
    assertThat(thrown).hasMessageThat().contains("at least once");
  }

  @Test
  public void backquotedEnumParameter() {
    SafeSql sql = SafeSql.of(
        "select `{column}` from Users",
        /* columns */ Pii.EMAIL);
    assertThat(sql.toString()).isEqualTo("select `email` from Users");
    assertThat(sql.debugString()).isEqualTo("select `email` from Users");
  }

  @Test
  public void listOfBackquotedStringParameters_singleParameter() {
    SafeSql sql = SafeSql.of(
        "select `{columns}` from tbl",
        /* columns */ asList("phone number"));
    assertThat(sql.toString()).isEqualTo("select `phone number` from tbl");
    assertThat(sql.debugString()).isEqualTo("select `phone number` from tbl");
  }

  @Test
  public void listOfBackquotedEnumParameters() {
    SafeSql sql = SafeSql.of(
        "select `{columns}` from Users",
        /* columns */ asList(Pii.values()));
    assertThat(sql.toString()).isEqualTo("select `ssn`, `email` from Users");
    assertThat(sql.debugString()).isEqualTo("select `ssn`, `email` from Users");
  }

  @Test
  public void listOfBackquotedStringParameters() {
    SafeSql sql = SafeSql.of(
        "select `{columns}` from tbl",
        /* columns */ asList("c1", "c2", "c3"));
    assertThat(sql.toString()).isEqualTo("select `c1`, `c2`, `c3` from tbl");
    assertThat(sql.debugString()).isEqualTo("select `c1`, `c2`, `c3` from tbl");
  }

  @Test
  public void emptyListOfBackquotedStringParameter_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () ->SafeSql.of("select `{columns}` from tbl", /* columns */ asList()));
    assertThat(thrown).hasMessageThat().contains("{columns} cannot be empty");
  }

  @Test
  public void listOfBackquotedStringParameters_withNullString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of(
            "select `{columns}` from tbl",
            /* columns */ asList("c1", null, "c3")));
    assertThat(thrown).hasMessageThat()
        .contains("{columns}[1] expected to be an identifier, but is null");
  }

  @Test
  public void listOfBackquotedStringParameters_withNonString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of(
            "select `{columns}` from tbl",
            /* columns */ asList("c1", "c2", 3)));
    assertThat(thrown)
        .hasMessageThat().contains("{columns}[2] expected to be String, but is class java.lang.Integer");
  }

  @Test
  public void listOfBackquotedStringParameters_placeholderWithQuestionMark() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select `{columns?}` from tbl", /* columns */ asList("c1")));
    assertThat(thrown).hasMessageThat().contains("'?'");
  }

  @Test
  public void listOfBackquotedStringParameters_withIllegalChars_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of(
            "select `{columns}` from tbl",
            /* columns */ asList("c1", "c2", "c3`")));
    assertThat(thrown).hasMessageThat().contains("{columns}[2]");
    assertThat(thrown).hasMessageThat().contains("c3`");
    assertThat(thrown).hasMessageThat().contains("illegal");
  }

  @Test
  public void listOfSafeSqlParameter() {
    SafeSql sql = SafeSql.of(
        "select {columns} from tbl",
        /* columns */ asList(SafeSql.of("c1"), SafeSql.of("c2")));
    assertThat(sql.toString()).isEqualTo("select c1, c2 from tbl");
    assertThat(sql.debugString()).isEqualTo("select c1, c2 from tbl");
  }

  @Test
  public void emptyListParameter_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () ->SafeSql.of("select {columns} from tbl", /* columns */ asList()));
    assertThat(thrown).hasMessageThat().contains("{columns} cannot be empty");
  }

  @Test
  public void listWithNullSafeSql_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of(
            "select {columns} from tbl",
            /* columns */ asList(SafeSql.of("c1"), null, SafeSql.of("c3"))));
    assertThat(thrown).hasMessageThat().contains("{columns}[1] expected to be SafeSql, but is null");
  }

  @Test
  public void listWithNonSafeSql_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of(
            "select {columns} from tbl",
            /* columns */ asList(SafeSql.of("c1"), SafeSql.of("c2"), "c3")));
    assertThat(thrown)
        .hasMessageThat().contains("{columns}[2] expected to be SafeSql, but is class java.lang.String");
  }

  @Test
  public void listParameter_placeholderWithQuestionMark() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select `{columns?}` from tbl", /* columns */ asList("c1")));
    assertThat(thrown).hasMessageThat().contains("'?'");
  }

  @Test
  public void singleNullParameter() {
    SafeSql sql = SafeSql.of("select {i}", /* i */ (Integer) null);
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.debugString()).isEqualTo("select ? /* null */");
  }

  @Test
  public void singleLikeParameterWithWildcardAtBothEnds() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}%'", "foo");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %foo% */ ESCAPE '^'");
  }

  @Test
  public void likeWithEscapeNotSuppoted_surroundedByPercentSign() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where name like '%{s}%' ESCAPE  '\'", "foo"));
    assertThat(thrown).hasMessageThat().contains("ESCAPE");
  }

  @Test
  public void likeWithEscapeNotSuppoted_precededByPercentSign() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where name like '%{s}' \n ESCAPE '\'", "foo"));
    assertThat(thrown).hasMessageThat().contains("ESCAPE");
  }

  @Test
  public void likeWithEscapeNotSuppoted_followedByPercentSign() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where name like '{s}%' ESCAPE '\'", "foo"));
    assertThat(thrown).hasMessageThat().contains("ESCAPE");
  }

  @Test
  public void literalPercentValueWithWildcardAtBothEnds() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}%'", "%");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %^%% */ ESCAPE '^'");
  }

  @Test
  public void literalBackslashValueWithWildcardAtBothEnds() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}%'", "\\");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %\\% */ ESCAPE '^'");
  }

  @Test
  public void literalCaretValueWithWildcardAtBothEnds() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}%'", "^");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %^^% */ ESCAPE '^'");
  }

  @Test
  public void literalSingleQuoteValueWithWildcardAtBothEnds() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}%'", "'");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %'% */ ESCAPE '^'");
  }

  @Test
  public void stringRequiredWhenWildcardsAtBothEnds() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where name like '%{s}%'", 1));
    assertThat(thrown).hasMessageThat().contains("String");
    assertThat(thrown).hasMessageThat().contains("{s}");
  }

  @Test
  public void singleLikeParameterWithWildcardAsPrefix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}'", "foo");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %foo */ ESCAPE '^'");
  }

  @Test
  public void literalPercentValueWithWildcardAtPrefix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}'", "%");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %^% */ ESCAPE '^'");
  }

  @Test
  public void literalBackslashValueWithWildcardAtPrefix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}'", "\\");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %\\ */ ESCAPE '^'");
  }

  @Test
  public void literalCaretValueWithWildcardAtPrefix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}'", "^");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %^^ */ ESCAPE '^'");
  }

  @Test
  public void literalSingleQuoteValueWithWildcardAtPrefix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '%{s}'", "'");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* %' */ ESCAPE '^'");
  }

  @Test
  public void stringRequiredWhenWildcardsAsPrefix() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where name like '%{s}'", 1));
    assertThat(thrown).hasMessageThat().contains("String");
    assertThat(thrown).hasMessageThat().contains("{s}");
  }

  @Test
  public void singleLikeParameterWithWildcardAsSuffix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '{s}%'", "foo");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* foo% */ ESCAPE '^'");
  }

  @Test
  public void literalPercentValueWithWildcardAtSuffix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '{s}%'", "%");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* ^%% */ ESCAPE '^'");
  }

  @Test
  public void literalBackslashValueWithWildcardAtSuffix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '{s}%'", "\\");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* \\% */ ESCAPE '^'");
  }

  @Test
  public void literalCaretValueWithWildcardAtSuffix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '{s}%'", "^");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* ^^% */ ESCAPE '^'");
  }

  @Test
  public void literalSingleQuoteValueWithWildcardAtSuffix() {
    SafeSql sql = SafeSql.of("select * from tbl where name like '{s}%'", "'");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name like ? ESCAPE '^'");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name like ? /* '% */ ESCAPE '^'");
  }

  @Test
  public void stringRequiredWhenWildcardsAsSuffix() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where name like '{s}%'", 1));
    assertThat(thrown).hasMessageThat().contains("String");
    assertThat(thrown).hasMessageThat().contains("{s}");
  }

  @Test
  public void stringParameterQuoted() {
    SafeSql sql = SafeSql.of("select * from tbl where name = '{s}'", "foo");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name = ?");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name = ? /* foo */");
  }

  @Test
  public void literalPercentValueQuoted() {
    SafeSql sql = SafeSql.of("select * from tbl where name = '{s}'", "%");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name = ?");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name = ? /* % */");
  }

  @Test
  public void literalBackslashValueQuoted() {
    SafeSql sql = SafeSql.of("select * from tbl where name = '{s}'", "\\");
    assertThat(sql.toString()).isEqualTo("select * from tbl where name = ?");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where name = ? /* \\ */");
  }

  @Test
  public void nonStringParameterQuoted_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where name like '{s}'", 1));
    assertThat(thrown).hasMessageThat().contains("String");
    assertThat(thrown).hasMessageThat().contains("'{s}'");
  }

  @Test
  public void doubleQuotedIdentifier_string() {
    SafeSql sql = SafeSql.of("select * from \"{tbl}\"", "Users");
    assertThat(sql.toString()).isEqualTo("select * from \"Users\"");
    assertThat(sql.debugString()).isEqualTo("select * from \"Users\"");
  }

  @Test
  public void doubleQuotedIdentifier_notString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from \"{tbl}\"", 1));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
  }

  @Test
  public void doubleQuotedIdentifier_emptyValue_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from \"{tbl}\"", ""));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("empty");
  }

  @Test
  public void doubleQuotedIdentifier_containsBacktick_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from \"{tbl}\"", "`a`b`"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a`b");
  }

  @Test
  public void doubleQuotedIdentifier_containsDoubleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from \"{tbl}\"", "a\"b"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a\"b");
  }

  @Test
  public void doubleQuotedIdentifier_containsBackslash_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from \"{tbl}\"", "a\\b"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a\\b");
  }

  @Test
  public void doubleQuotedIdentifier_containsSingleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from \"{tbl}\"", "a'b"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a'b");
  }

  @Test
  public void doubleQuotedIdentifier_containsNewLine_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from \"{tbl}\"", "a\nb"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a\nb");
  }

  @Test
  public void backquotedIdentifier_string() {
    SafeSql sql = SafeSql.of("select * from `{tbl}`", "Users");
    assertThat(sql.toString()).isEqualTo("select * from `Users`");
    assertThat(sql.debugString()).isEqualTo("select * from `Users`");
  }

  @Test
  public void backquotedIdentifier_notString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from `{tbl}`", 1));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
  }

  @Test
  public void backquotedIdentifier_emptyValue_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from `{tbl}`", ""));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("empty");
  }

  @Test
  public void backquotedIdentifier_containsBacktick_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from `{tbl}`", "`a`b`"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("`a`b`");
  }

  @Test
  public void backquotedIdentifier_containsBackslash_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from `{tbl}`", "a\\b"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a\\b");
  }

  @Test
  public void backquotedIdentifier_containsSingleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from `{tbl}`", "a'b"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a'b");
  }

  @Test
  public void backquotedIdentifier_containsDoubleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from `{tbl}`", "a\"b"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a\"b");
  }

  @Test
  public void backquotedIdentifier_containsNewLine_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> SafeSql.of("select * from `{tbl}`", "a\nb"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a\nb");
  }

  @Test
  public void safeSqlShouldNotBeSingleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT '{query}' WHERE TRUE", /* query */ SafeSql.of("1")));
    assertThat(thrown).hasMessageThat().contains("SafeSql should not be quoted: '{query}'");
  }

  @Test
  public void safeSqlShouldNotBeDoubleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT \"{query}\" WHERE TRUE", /* query */ SafeSql.of("1")));
    assertThat(thrown).hasMessageThat().contains("SafeSql should not be quoted: \"{query}\"");
  }

  @Test
  public void safeSqlListShouldNotBeSingleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT '{query}' WHERE TRUE", /* query */ asList(SafeSql.of("1"))));
    assertThat(thrown).hasMessageThat().contains("SafeSql should not be quoted: '{query}'");
  }

  @Test
  public void safeSqlListShouldNotBeDoubleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT \"{...}\" WHERE TRUE", asList(SafeSql.of("1"))));
    assertThat(thrown).hasMessageThat().contains("{...}[0] expected to be String");
  }

  @Test
  public void safeSqlListShouldNotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT `{query}` WHERE TRUE", /* query */ asList(SafeSql.of("1"))));
    assertThat(thrown).hasMessageThat().contains("{query}[0] expected to be String");
  }

  @Test
  public void safeSqlShouldNotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT `{query}` WHERE TRUE", /* query */ SafeSql.of("1")));
    assertThat(thrown).hasMessageThat().contains("SafeSql should not be backtick quoted: `{query}`");
  }

  @Test
  public void backquoteAndSingleQuoteMixed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT * FROM `{tbl}'", "jobs"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}'");
  }

  @Test
  public void singleQuoteAndBackquoteMixed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT * FROM '{tbl}`", "jobs"));
    assertThat(thrown).hasMessageThat().contains("'{tbl}`");
  }

  @Test
  public void missingOpeningQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT {tbl}'", "jobs"));
    assertThat(thrown).hasMessageThat().contains("{tbl}'");
  }

  @Test
  public void missingClosingQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT '{tbl}", "jobs"));
    assertThat(thrown).hasMessageThat().contains("'{tbl}");
  }

  @Test
  public void missingOpeningBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT * FROM {tbl}`", "jobs"));
    assertThat(thrown).hasMessageThat().contains("{tbl}`");
  }

  @Test
  public void missingClosingBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT * FROM `{tbl}", "jobs"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}");
  }

  @Test
  public void missingOpeningDoubleQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT {tbl}\"", "jobs"));
    assertThat(thrown).hasMessageThat().contains("{tbl}\"");
  }

  @Test
  public void missingClosingDoubleQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> SafeSql.of("SELECT \"{tbl}", "jobs"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}");
  }

  @Test
  public void listMissingOpeningQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT {id}'", asList(SafeSql.of("id"))));
    assertThat(thrown).hasMessageThat().contains("{id}'");
  }

  @Test
  public void listMissingClosingQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT '{id}", asList(SafeSql.of("id"))));
    assertThat(thrown).hasMessageThat().contains("'{id}");
  }

  @Test
  public void listMissingOpeningBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT * FROM {id}`", asList(SafeSql.of("id"))));
    assertThat(thrown).hasMessageThat().contains("{id}`");
  }

  @Test
  public void listMissingClosingBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> SafeSql.of("SELECT * FROM `{id}", asList(SafeSql.of("id"))));
    assertThat(thrown).hasMessageThat().contains("`{id}");
  }

  @Test
  public void twoParameters() {
    SafeSql sql =
        SafeSql.of("select {label} where id = {id}", /* label */ "foo", /* id */ 123);
    assertThat(sql.toString()).isEqualTo("select ? where id = ?");
    assertThat(sql.debugString()).isEqualTo("select ? /* foo */ where id = ? /* 123 */");
  }

  @Test
  public void parameterizeByTableName() {
    SafeSql sql =
        SafeSql.of("select * from {tbl} where id = {id}", /* tbl */ SafeSql.of("Users"), /* id */ 123);
    assertThat(sql.toString()).isEqualTo("select * from Users where id = ?");
    assertThat(sql.debugString()).isEqualTo("select * from Users where id = ? /* 123 */");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void twoParametersWithSameName() {
    SafeSql sql =
        SafeSql.of("select * where id = {id} and partner_id = {id}", 123, 456);
    assertThat(sql.toString()).isEqualTo("select * where id = ? and partner_id = ?");
    assertThat(sql.debugString()).isEqualTo("select * where id = ? /* 123 */ and partner_id = ? /* 456 */");
  }

  @Test
  public void twoParametersWithSameNameAndBothAreNulls() {
    SafeSql sql =
        SafeSql.of("select * where id = {id} and partner_id = {id}", /* id */ null, /* id */ null);
    assertThat(sql.toString()).isEqualTo("select * where id = ? and partner_id = ?");
    assertThat(sql.debugString()).isEqualTo("select * where id = ? /* null */ and partner_id = ? /* null */");
  }

  @Test
  public void paramNameWithQuestionMark_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * where id = {foo?}", 123));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void sqlWithQuestionMark() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * where id = ?"));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void singleStringParameterValueHasQuestionMark() {
    SafeSql sql = SafeSql.of("select {str}", "?");
    assertThat(sql.toString()).isEqualTo("select ?");
    assertThat(sql.debugString()).isEqualTo("select ? /* ? */");
  }

  @Test
  public void subqueryHasQuestionMark_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from {tbl}", /* tbl */ SafeSql.of("?")));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void inListOfParameters_withParameterValues() {
    SafeSql sql = SafeSql.of("select * from tbl where id in ({ids})", /* ids */ asList(1, 2, 3));
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (?, ?, ?)");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where id in (? /* 1 */, ? /* 2 */, ? /* 3 */)");
  }

  @Test
  public void inListOfParameters_withParameterValuesAndSubqueries() {
    SafeSql sql = SafeSql.of(
        "select * from tbl where id in ({ids})", /* ids */ asList(1, SafeSql.nonNegativeLiteral(2), 3));
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (?, 2, ?)");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where id in (? /* 1 */, 2, ? /* 3 */)");
  }

  @Test
  public void inListOfQuotedStringParameters() {
    SafeSql sql = SafeSql.of("select * from tbl where id in ('{ids}')", /* ids */ asList("foo", "bar"));
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (?, ?)");
    assertThat(sql.debugString()).isEqualTo("select * from tbl where id in (? /* foo */, ? /* bar */)");
  }

  @Test
  public void inListOfQuotedNonStringParameters_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () ->  SafeSql.of("select * from tbl where id in ('{ids}')", /* ids */ asList("foo", 2)));
    assertThat(thrown).hasMessageThat().contains("{ids}[1] expected to be String");
  }

  @Test
  public void inListOfQuotedStringParametersWithChars_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () ->  SafeSql.of("select * from tbl where id in ('%{ids}%')", /* ids */ asList("foo", "bar")));
    assertThat(thrown).hasMessageThat().contains("{ids}[0]");
  }

  @Test
  public void inListOfParameters_emptyList() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from tbl where id in ({ids})", /* ids */ asList()));
    assertThat(thrown).hasMessageThat().contains("{ids} cannot be empty list");
  }

  @Test
  public void when_conditionalIsFalse_returnsEmpty() {
    assertThat(SafeSql.when(false, "WHERE id = {id}", 1)).isEqualTo(SafeSql.EMPTY);
  }

  @Test
  public void when_conditionalIsTrue_returnsQuery() {
    assertThat(SafeSql.when(true, "WHERE id = {id}", 1))
        .isEqualTo(SafeSql.of("WHERE id = {id}", 1));
  }

  @Test
  public void postfixWhen_conditionalIsFalse_returnsEmpty() {
    assertThat(SafeSql.of("WHERE id = {id}", 1).when(false)).isEqualTo(SafeSql.EMPTY);
  }

  @Test
  public void postfixWhen_conditionalIsTrue_returnsQuery() {
    assertThat(SafeSql.of("WHERE id = {id}", 1).when(true))
        .isEqualTo(SafeSql.of("WHERE id = {id}", 1));
  }

  @Test
  public void optionally_optionalArgIsEmpty_returnsEmpty() {
    assertThat(SafeSql.optionally("WHERE id = {id}", /* id */ Optional.empty()))
        .isEqualTo(SafeSql.EMPTY);
  }

  @Test
  public void optionally_optionalArgIsPresent_returnsQuery() {
    assertThat(SafeSql.optionally("WHERE id = {id}", /* id */ Optional.of(1)))
        .isEqualTo(SafeSql.of("WHERE id = {id}", 1));
  }


  @Test
  public void joiningByStringConstant() {
    assertThat(Stream.of(SafeSql.of("a"), SafeSql.of("b")).collect(SafeSql.joining(" AND ")))
        .isEqualTo(SafeSql.of("a AND b"));
  }

  @Test
  public void joining_ignoresEmpty() {
    assertThat(Stream.of(SafeSql.of("a"), SafeSql.EMPTY).collect(SafeSql.joining(" AND ")))
        .isEqualTo(SafeSql.of("a"));
  }

  @Test
  public void andCollector_empty() {
    ImmutableList<SafeSql> queries = ImmutableList.of();
    assertThat(queries.stream().collect(SafeSql.and())).isEqualTo(SafeSql.of("(1 = 1)"));
  }

  @Test
  public void andCollector_singleCondition() {
    ImmutableList<SafeSql> queries = ImmutableList.of(SafeSql.of("a = 1"));
    assertThat(queries.stream().collect(SafeSql.and())).isEqualTo(SafeSql.of("(a = 1)"));
  }

  @Test
  public void andCollector_twoConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.of("a = 1"), SafeSql.of("b = 2 OR c = 3"));
    assertThat(queries.stream().collect(SafeSql.and()))
        .isEqualTo(SafeSql.of("(a = 1) AND (b = 2 OR c = 3)"));
  }

  @Test
  public void andCollector_threeConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = 1"), SafeSql.of("b = 2 OR c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.and()))
        .isEqualTo(SafeSql.of("(a = 1) AND (b = 2 OR c = 3) AND (d = 4)"));
  }

  @Test
  public void andCollector_threeConditionsWithParameters() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = {v1}", 1), SafeSql.of("b = {v2} OR c = {v3}", 2, 3), SafeSql.of("d = {v4}", 4));
    SafeSql sql = queries.stream().collect(SafeSql.and());
    assertThat(sql.toString()).isEqualTo("(a = ?) AND (b = ? OR c = ?) AND (d = ?)");
    assertThat(sql.debugString()).isEqualTo("(a = ? /* 1 */) AND (b = ? /* 2 */ OR c = ? /* 3 */) AND (d = ? /* 4 */)");
  }

  @Test
  public void andCollector_ignoresEmpty() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.EMPTY, SafeSql.of("b = 2 OR c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.and()))
        .isEqualTo(SafeSql.of("(b = 2 OR c = 3) AND (d = 4)"));
  }

  @Test
  public void orCollector_empty() {
    ImmutableList<SafeSql> queries = ImmutableList.of();
    assertThat(queries.stream().collect(SafeSql.or())).isEqualTo(SafeSql.of("(1 = 0)"));
  }

  @Test
  public void orCollector_singleCondition() {
    ImmutableList<SafeSql> queries = ImmutableList.of(SafeSql.of("a = 1"));
    assertThat(queries.stream().collect(SafeSql.or())).isEqualTo(SafeSql.of("(a = 1)"));
  }

  @Test
  public void orCollector_twoConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.of("a = 1"), SafeSql.of("b = 2 AND c = 3"));
    assertThat(queries.stream().collect(SafeSql.or()))
        .isEqualTo(SafeSql.of("(a = 1) OR (b = 2 AND c = 3)"));
  }

  @Test
  public void orCollector_threeConditions() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = 1"), SafeSql.of("b = 2 AND c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.or()))
        .isEqualTo(SafeSql.of("(a = 1) OR (b = 2 AND c = 3) OR (d = 4)"));
  }

  @Test
  public void orCollector_threeConditionsWithParameters() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(
            SafeSql.of("a = {v1}", 1), SafeSql.of("b = {v2} AND c = {v3}", 2, 3), SafeSql.of("d = {v4}", 4));
    SafeSql sql = queries.stream().collect(SafeSql.or());
    assertThat(sql.toString()).isEqualTo("(a = ?) OR (b = ? AND c = ?) OR (d = ?)");
    assertThat(sql.debugString()).isEqualTo("(a = ? /* 1 */) OR (b = ? /* 2 */ AND c = ? /* 3 */) OR (d = ? /* 4 */)");
  }

  @Test
  public void orCollector_ignoresEmpty() {
    ImmutableList<SafeSql> queries =
        ImmutableList.of(SafeSql.EMPTY, SafeSql.of("b = 2 AND c = 3"), SafeSql.of("d = 4"));
    assertThat(queries.stream().collect(SafeSql.or()))
        .isEqualTo(SafeSql.of("(b = 2 AND c = 3) OR (d = 4)"));
  }

  @Test
  public void namesInAnonymousSubqueriesAreIndependent() {
    SafeSql sql =
        Stream.of(1, 2, 3).map(id -> SafeSql.of("id = {id}", id)).collect(SafeSql.or());
    assertThat(sql.toString()).isEqualTo("(id = ?) OR (id = ?) OR (id = ?)");
    assertThat(sql.debugString()).isEqualTo("(id = ? /* 1 */) OR (id = ? /* 2 */) OR (id = ? /* 3 */)");
  }

  @Test
  public void namesInSubqueryAndParentQueryDontConflict() {
    SafeSql sql = SafeSql.of(
        "select * from ({tbl}) where id = {id}",
        SafeSql.of("select * from tbl where id = {id}", 1), /* id */ 2);
    assertThat(sql.toString()).isEqualTo("select * from (select * from tbl where id = ?) where id = ?");
    assertThat(sql.debugString())
        .isEqualTo("select * from (select * from tbl where id = ? /* 1 */) where id = ? /* 2 */");
  }

  @Test
  public void namesInSubqueriesDontConflict() {
    SafeSql sql = SafeSql.of(
        "select * from ({tbl1}), ({tbl2}) where id = {id}",
        /* tbl1 */ SafeSql.of("select * from tbl where id = {id}", 1),
        /* tbl2 */ SafeSql.of("select * from tbl where id = {id}", 2),
        /* id */ 3);
    assertThat(sql.toString()).isEqualTo("select * from (select * from tbl where id = ?), (select * from tbl where id = ?) where id = ?");
    assertThat(sql.debugString())
        .isEqualTo("select * from (select * from tbl where id = ? /* 1 */), (select * from tbl where id = ? /* 2 */) where id = ? /* 3 */");
  }

  @Test
  public void cannotUseSafeQueryAsSubquery() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * from {tbl}", new SafeQuery()));
    assertThat(thrown).hasMessageThat().contains("SafeQuery");
  }

  @Test
  public void optionalParameterDisallowed() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.of("select * where id = {id}", /* id */ Optional.of(1)));
    assertThat(thrown).hasMessageThat().contains("optionally()");
  }

  @Test
  public void nonNegative_maxValue_allowed() {
    SafeSql sql = SafeSql.nonNegativeLiteral(Integer.MAX_VALUE);
    assertThat(sql.toString()).isEqualTo(Long.toString(Integer.MAX_VALUE));
    assertThat(sql.debugString()).isEqualTo(Long.toString(Integer.MAX_VALUE));
  }

  @Test
  public void nonNegative_positive_allowed() {
    SafeSql sql = SafeSql.nonNegativeLiteral(123);
    assertThat(sql.toString()).isEqualTo("123");
    assertThat(sql.debugString()).isEqualTo("123");
  }

  @Test
  public void nonNegative_zero_allowed() {
    SafeSql sql = SafeSql.nonNegativeLiteral(0);
    assertThat(sql.toString()).isEqualTo("0");
    assertThat(sql.debugString()).isEqualTo("0");
  }

  @Test
  public void nonNegative_negativeNumber_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.nonNegativeLiteral(-1));
    assertThat(thrown).hasMessageThat().contains("negative number disallowed: -1");
  }

  @Test
  public void nonNegative_minValue_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> SafeSql.nonNegativeLiteral(Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains("negative number disallowed");
  }

  @Test
  public void orElse_empty_returnsFallbackQuery() {
    assertThat(SafeSql.EMPTY.orElse("WHERE id = {id}", 1))
        .isEqualTo(SafeSql.of("WHERE id = {id}", 1));
  }

  @Test
  public void orElse_nonEmpty_returnsTheMainlineQuery() {
    assertThat(SafeSql.of("select *").orElse("WHERE id = {id}", 1))
        .isEqualTo(SafeSql.of("select *"));
  }

  @Test
  public void accidentalBlockComment_disallowed() {
    SafeSql sub = SafeSql.of("*1");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> SafeSql.of("/{sub}", sub));
    assertThat(thrown).hasMessageThat().contains("/*1");
  }

  @Test
  public void accidentalLineComment_disallowed() {
    SafeSql sub = SafeSql.of("-1");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> SafeSql.of("-{sub}", sub));
    assertThat(thrown).hasMessageThat().contains("--1");
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(
            SafeSql.of("select * from tbl"),
            SafeSql.of("select * from {tbl}", SafeSql.of("tbl")))
        .addEqualityGroup(
            SafeSql.of("select id from tbl where id = {id}", 1),
            SafeSql.of("select id from tbl where id = {i}", 1))
        .addEqualityGroup(SafeSql.of("select id from tbl where id = 1"))
        .addEqualityGroup(SafeSql.of("select id from tbl where id = {id}", 2))
        .testEquals();
  }

  @Test
  public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(SafeSql.class);
    new NullPointerTester().testAllPublicInstanceMethods(SafeSql.of("select *"));
  }

  private enum Pii {
    SSN, EMAIL;

    @Override public String toString() {
      return Ascii.toLowerCase(name());
    }
  }

  private static class SafeQuery {}
}
