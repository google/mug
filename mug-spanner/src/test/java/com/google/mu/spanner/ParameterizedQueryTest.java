package com.google.mu.spanner;

import static com.google.cloud.Timestamp.parseTimestamp;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.cloud.ByteArray;
import com.google.cloud.Date;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.Interval;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.Value;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;

@RunWith(JUnit4.class)
public class ParameterizedQueryTest {

  @Test
  public void emptyTemplate() {
    assertThat(ParameterizedQuery.of("")).isEqualTo(ParameterizedQuery.of(""));
  }

  @Test
  public void emptySql() {
    assertThat(ParameterizedQuery.EMPTY.toString()).isEmpty();
  }

  @Test
  public void singleStringParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of("select '{str}'", "foo");
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @str").bind("str").to("foo").build());
    assertThat(sql.toString()).isEqualTo("select @str /* foo */");
  }

  @Test
  public void singleIntParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", 123);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(123L).build());
  }

  @Test
  public void singleLongParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", 123L);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(123L).build());
  }

  @Test
  public void singleFloatParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", 1.5F);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(1.5F).build());
  }

  @Test
  public void singleDoubleParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", 1.5D);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(1.5D).build());
  }

  @Test
  public void singleBigDecimalParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ BigDecimal.valueOf(1.5));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(BigDecimal.valueOf(1.5)).build());
  }

  @Test
  public void singleInstantParameter() {
    Instant time = Instant.ofEpochSecond(12345, 678);
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ time);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(Timestamp.parseTimestamp(time.toString())).build());
  }

  @Test
  public void singleZonedDateTimeParameter() {
    Instant time = Instant.ofEpochSecond(12345, 678);
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ time.atZone(ZoneId.of("America/New_York")));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(Timestamp.parseTimestamp(time.toString())).build());
  }

  @Test
  public void singleOffsetDateTimeParameter() {
    Instant time = Instant.ofEpochSecond(12345, 678);
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ time.atOffset(ZoneOffset.ofHours(-7)));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(Timestamp.parseTimestamp(time.toString())).build());
  }

  @Test
  public void singleLocalDateParameter() {
    LocalDate date = LocalDate.of(1997, 1, 15);
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ date);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(Date.parseDate(date.toString())).build());
  }

  @Test
  public void singleBoolParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of("select {bool}", true);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @bool").bind("bool").to(true).build());
    assertThat(sql.toString()).isEqualTo("select @bool /* true */");
  }

  @Test
  public void singleUuidParameter() {
    UUID uuid = UUID.randomUUID();
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ uuid);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(uuid).build());
  }

  @Test
  public void singleByteArrayParameter() {
    ByteArray bytes = ByteArray.copyFrom(new byte[] {1, 3, 5});
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ bytes);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(bytes).build());
  }

  @Test
  public void singleIntervalParameter() {
    Interval interval = Interval.ofDays(3);
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ interval);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(interval).build());
  }

  @Test
  public void singleStructParameter() {
    Struct struct = Struct.newBuilder().add(Value.string("foo")).add(Value.bool(true)).build();
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ struct);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(struct).build());
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void singleLongArrayParameter() {
    long[] a = new long[] {1, 3, 5};
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ a);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").toInt64Array(a).build());
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void singleFloatArrayParameter() {
    float[] a = new float[] {1, 3, 5};
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ a);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").toFloat32Array(a).build());
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void singleDoubleArrayParameter() {
    double[] a = new double[] {1, 3, 5};
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ a);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").toFloat64Array(a).build());
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void singleBooleanArrayParameter() {
    boolean[] a = new boolean[] {true, false};
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ a);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").toBoolArray(a).build());
  }

  @Test
  public void singleValueParameter() {
    Value value = Value.timestamp(Timestamp.MAX_VALUE);
    ParameterizedQuery sql = ParameterizedQuery.of("select {i}", /* i */ value);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @i").bind("i").to(value).build());
  }

  @Test
  public void conditionalOperator_evaluateToTrue() {
    boolean showsId = true;
    assertThat(ParameterizedQuery.of("SELECT {shows_id->id,} name FROM tbl", showsId))
        .isEqualTo(ParameterizedQuery.of("SELECT id, name FROM tbl"));
  }

  @Test
  public void conditionalOperator_evaluateToFalse() {
    boolean showsId = false;
    assertThat(ParameterizedQuery.of("SELECT {shows_id->id,} name FROM tbl", showsId))
        .isEqualTo(ParameterizedQuery.of("SELECT  name FROM tbl"));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void conditionalOperator_nonBooleanArg_disallowed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {shows_id->id,} name FROM tbl", ParameterizedQuery.of("showsId")));
    assertThat(thrown).hasMessageThat().contains("{shows_id->");
    assertThat(thrown).hasMessageThat().contains("ParameterizedQuery");
  }

  @Test
  public void conditionalOperator_nullArg_disallowed() {
    Boolean showsId = null;
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {shows_id->id,} name FROM tbl", showsId));
    assertThat(thrown).hasMessageThat().contains("{shows_id->");
    assertThat(thrown).hasMessageThat().contains("null");
  }

  @Test
  public void conditionalOperator_cannotBeBacktickQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT `{shows_id->id}` name FROM tbl", true));
    assertThat(thrown).hasMessageThat().contains("{shows_id->");
    assertThat(thrown).hasMessageThat().contains("backtick quoted");
  }

  @Test
  public void conditionalOperator_questionMarkInParameterName() {
    boolean showsId = true;
    assertThat(ParameterizedQuery.of("SELECT {shows_id?->id,} name FROM tbl", showsId))
        .isEqualTo(ParameterizedQuery.of("SELECT id, name FROM tbl"));
  }

  @Test
  public void guardOperator_present() {
    Optional<Integer> id = Optional.of(123);
    assertThat(ParameterizedQuery.of("SELECT {id? -> id? AS id,} name FROM tbl", id))
        .isEqualTo(ParameterizedQuery.of("SELECT {id} AS id, name FROM tbl", id.get()));
  }

  @Test
  public void guardOperator_absent() {
    Optional<Integer> id = Optional.empty();
    assertThat(ParameterizedQuery.of("SELECT { id? -> id? AS id,} name FROM tbl", id))
        .isEqualTo(ParameterizedQuery.of("SELECT  name FROM tbl"));
  }

  @Test
  public void guardOperator_optionalParameterReferencedMultipleTimes() {
    Optional<String> id = Optional.of("myId");
    ParameterizedQuery sql = ParameterizedQuery.of("SELECT {id? -> id? AS id, UPPER('id?') AS title, } name FROM tbl", id);
    assertThat(sql)
        .isEqualTo(ParameterizedQuery.of("SELECT '{id}' AS id, UPPER('{id}') AS title, name FROM tbl", "myId", "myId"));
    assertThat(sql.toString())
        .isEqualTo("SELECT @id /* myId */ AS id, UPPER(@id_1 /* myId */) AS title, name FROM tbl");
  }

  @Test
  public void guardOperator_withLikeOperator_present() {
    Optional<String> name = Optional.of("%foo");
    ParameterizedQuery sql = ParameterizedQuery.of("SELECT * FROM tbl WHERE 1=1 {name? -> AND name LIKE 'name?'}", name);
    assertThat(sql)
        .isEqualTo(ParameterizedQuery.of("SELECT * FROM tbl WHERE 1=1 AND name LIKE '{name}'", name.get()));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("SELECT * FROM tbl WHERE 1=1 AND name LIKE @name")
            .bind("name").to(name.get())
            .build());
  }

  @Test
  public void guardOperator_withLikeOperator_absent() {
    Optional<String> name = Optional.empty();
    assertThat(ParameterizedQuery.of("SELECT * FROM tbl WHERE 1=1 {name? -> AND name LIKE '%name?%'}", name))
        .isEqualTo(ParameterizedQuery.of("SELECT * FROM tbl WHERE 1=1 "));
  }

  @Test
  public void guardOperator_trailingSpaceAndNewlinesIgnored() {
    Optional<Integer> id = Optional.of(123);
    assertThat(ParameterizedQuery.of("SELECT {id? -> id? AS id, \n} name FROM tbl", id))
        .isEqualTo(ParameterizedQuery.of("SELECT {id} AS id, name FROM tbl", id.get()));
  }

  @Test
  public void guardOperator_parameterReferencedMoreThanOnce() {
    Optional<Integer> id = Optional.of(123);
    assertThat(
            ParameterizedQuery.of(
                "SELECT {id? -> IFNULL(id?, NULL, id?) AS id,} name FROM tbl", id))
        .isEqualTo(
            ParameterizedQuery.of(
                "SELECT IFNULL({id}, NULL, {id}) AS id, name FROM tbl", id.get(), id.get()));
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void guardOperator_missingQuestionMark() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {id -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{id->}");
    assertThat(thrown).hasMessageThat().contains("followed by '?'");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void guardOperator_redundantQuestionMark() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {id?? -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{id??->}");
    assertThat(thrown).hasMessageThat().contains("followed by '?'");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void guardOperator_spaceInTheMiddleOfName() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {the id? -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{the id?->}");
    assertThat(thrown).hasMessageThat().contains("followed by '?'");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void guardOperator_typoInParameterName() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {id? -> bad_id? AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{id?->}");
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void guardOperator_missingQuestionMarkInReference() {
    Optional<Integer> id = Optional.empty();
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {id? -> id AS id,} name FROM tbl", id));
    assertThat(thrown).hasMessageThat().contains("{id?->}");
    assertThat(thrown).hasMessageThat().contains("at least once");
  }

  @Test
  public void guardOperator_nonEmpty() {
    ImmutableList<String> columns = ImmutableList.of("foo", "bar");
    assertThat(ParameterizedQuery.of("SELECT {columns? -> `columns?`, } name FROM tbl", columns))
        .isEqualTo(ParameterizedQuery.of("SELECT `{columns}`, name FROM tbl", columns));
  }

  @Test
  public void guardOperator_empty() {
    ImmutableList<String> columns = ImmutableList.of();
    assertThat(ParameterizedQuery.of("SELECT {columns? -> `columns?`, } name FROM tbl", columns))
        .isEqualTo(ParameterizedQuery.of("SELECT  name FROM tbl"));
  }

  @Test
  public void backquotedEnumParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select `{column}` from Users",
        /* columns */ Pii.EMAIL);
    assertThat(sql.toString()).isEqualTo("select `email` from Users");
  }

  @Test
  public void listOfBackquotedStringParameters_singleParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select `{columns}` from tbl",
        /* columns */ asList("phone number"));
    assertThat(sql.toString()).isEqualTo("select `phone number` from tbl");
  }

  @Test
  public void listOfBackquotedEnumParameters() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select `{columns}` from Users",
        /* columns */ asList(Pii.values()));
    assertThat(sql.toString()).isEqualTo("select `ssn`, `email` from Users");
  }

  @Test
  public void listOfBackquotedStringParameters() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select `{columns}` from tbl",
        /* columns */ asList("c1", "c2", "c3"));
    assertThat(sql.toString()).isEqualTo("select `c1`, `c2`, `c3` from tbl");
  }

  @Test
  public void emptyListOfBackquotedStringParameter_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () ->ParameterizedQuery.of("select `{columns}` from tbl", /* columns */ asList()));
    assertThat(thrown).hasMessageThat().contains("{columns}");
    assertThat(thrown).hasMessageThat().contains("empty");
  }

  @Test
  public void listOfBackquotedStringParameters_withNullString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of(
            "select `{columns}` from tbl",
            /* columns */ asList("c1", null, "c3")));
    assertThat(thrown).hasMessageThat()
        .contains("{columns}[1] expected to be an identifier, but is null");
  }

  @Test
  public void listOfBackquotedStringParameters_withNonString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of(
            "select `{columns}` from tbl",
            /* columns */ asList("c1", "c2", 3)));
    assertThat(thrown)
        .hasMessageThat().contains("{columns}[2] expected to be String, but is class java.lang.Integer");
  }

  @Test
  public void listOfBackquotedStringParameters_placeholderWithQuestionMark() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select `{columns?}` from tbl", /* columns */ asList("c1")));
    assertThat(thrown).hasMessageThat().contains("'?'");
  }

  @Test
  public void listOfBackquotedStringParameters_withIllegalChars_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of(
            "select `{columns}` from tbl",
            /* columns */ asList("c1", "c2", "c3`")));
    assertThat(thrown).hasMessageThat().contains("{columns}[2]");
    assertThat(thrown).hasMessageThat().contains("c3`");
    assertThat(thrown).hasMessageThat().contains("illegal");
  }

  @Test
  public void listOfParameterizedQueryParameter() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select {columns} from tbl",
        /* columns */ asList(ParameterizedQuery.of("c1"), ParameterizedQuery.of("c2")));
    assertThat(sql.toString()).isEqualTo("select c1, c2 from tbl");
  }

  @Test
  public void emptyListParameter_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () ->ParameterizedQuery.of("select {columns} from tbl", /* columns */ asList()));
    assertThat(thrown).hasMessageThat().contains("{columns}");
    assertThat(thrown).hasMessageThat().contains("empty");
  }

  @Test
  public void listWithNullParameterizedQuery_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of(
            "select {columns} from tbl",
            /* columns */ asList(ParameterizedQuery.of("c1"), null, ParameterizedQuery.of("c3"))));
    assertThat(thrown).hasMessageThat().contains("{columns}");
    assertThat(thrown).hasMessageThat().contains("ParameterizedQuery");
  }

  @Test
  public void listWithNonParameterizedQuery_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of(
            "select {columns} from tbl",
            /* columns */ asList(ParameterizedQuery.of("c1"), ParameterizedQuery.of("c2"), "c3")));
    assertThat(thrown).hasMessageThat().contains("ParameterizedQuery to Value");
    assertThat(thrown).hasMessageThat().contains("{columns}");
  }

  @Test
  public void listParameter_placeholderWithQuestionMark() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select `{columns?}` from tbl", /* columns */ asList("c1")));
    assertThat(thrown).hasMessageThat().contains("'?'");
  }

  @Test
  public void nullParameter_disallowed() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select {i}", /* i */ (Integer) null));
    assertThat(thrown).hasMessageThat().contains("Cannot infer type from null");
  }

  @Test
  public void stringParameterQuoted() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where name = '{s}'", "foo");
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where name = @s").bind("s").to("foo").build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where name = @s /* foo */");
  }

  @Test
  public void literalPercentValueQuoted() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where name = '{s}'", "%");
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where name = @s").bind("s").to("%").build());
  }

  @Test
  public void literalBackslashValueQuoted() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where name = '{s}'", "\\");
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where name = @s").bind("s").to("\\").build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where name = @s /* \\ */");
  }

  @Test
  public void nonStringParameterQuoted_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * from tbl where name like '{s}'", 1));
    assertThat(thrown).hasMessageThat().contains("String");
    assertThat(thrown).hasMessageThat().contains("'{s}'");
  }

  @Test
  public void doubleQuotedIdentifier_string() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from \"{tbl}\"", "Users");
    assertThat(sql.toString()).isEqualTo("select * from \"Users\"");
  }

  @Test
  public void doubleQuotedIdentifier_notString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from \"{tbl}\"", 1));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
  }

  @Test
  public void doubleQuotedIdentifier_emptyValue_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from \"{tbl}\"", ""));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("empty");
  }

  @Test
  public void doubleQuotedIdentifier_containsBacktick_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from \"{tbl}\"", "`a`b`"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a`b");
  }

  @Test
  public void doubleQuotedIdentifier_containsDoubleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from \"{tbl}\"", "a\"b"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a\"b");
  }

  @Test
  public void doubleQuotedIdentifier_containsBackslash_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from \"{tbl}\"", "a\\b"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a\\b");
  }

  @Test
  public void doubleQuotedIdentifier_containsSingleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from \"{tbl}\"", "a'b"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a'b");
  }

  @Test
  public void doubleQuotedIdentifier_containsNewLine_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from \"{tbl}\"", "a\nb"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}\"");
    assertThat(thrown).hasMessageThat().contains("a\nb");
  }

  @Test
  public void backquotedIdentifier_string() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from `{tbl}`", "Users");
    assertThat(sql.toString()).isEqualTo("select * from `Users`");
  }

  @Test
  public void backquotedIdentifier_notString_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from `{tbl}`", 1));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
  }

  @Test
  public void backquotedIdentifier_emptyValue_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from `{tbl}`", ""));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("empty");
  }

  @Test
  public void backquotedIdentifier_containsBacktick_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from `{tbl}`", "`a`b`"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("`a`b`");
  }

  @Test
  public void backquotedIdentifier_containsBackslash_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from `{tbl}`", "a\\b"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a\\b");
  }

  @Test
  public void backquotedIdentifier_containsSingleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from `{tbl}`", "a'b"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a'b");
  }

  @Test
  public void backquotedIdentifier_containsDoubleQuote_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from `{tbl}`", "a\"b"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a\"b");
  }

  @Test
  public void backquotedIdentifier_containsNewLine_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ParameterizedQuery.of("select * from `{tbl}`", "a\nb"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}`");
    assertThat(thrown).hasMessageThat().contains("a\nb");
  }

  @Test
  public void ParameterizedQueryShouldNotBeSingleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT '{query}' WHERE TRUE", /* query */ ParameterizedQuery.of("1")));
    assertThat(thrown).hasMessageThat().contains("ParameterizedQuery should not be quoted: '{query}'");
  }

  @Test
  public void ParameterizedQueryShouldNotBeDoubleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT \"{query}\" WHERE TRUE", /* query */ ParameterizedQuery.of("1")));
    assertThat(thrown).hasMessageThat().contains("ParameterizedQuery should not be quoted: \"{query}\"");
  }

  @Test
  public void ParameterizedQueryListShouldNotBeSingleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT '{query}' WHERE TRUE", /* query */ asList(ParameterizedQuery.of("1"))));
    assertThat(thrown).hasMessageThat().contains("{query}");
    assertThat(thrown).hasMessageThat().contains("String");
  }

  @Test
  public void ParameterizedQueryListShouldNotBeDoubleQuoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT \"{...}\" WHERE TRUE", asList(ParameterizedQuery.of("1"))));
    assertThat(thrown).hasMessageThat().contains("{...}[0] expected to be String");
  }

  @Test
  public void ParameterizedQueryListShouldNotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT `{query}` WHERE TRUE", /* query */ asList(ParameterizedQuery.of("1"))));
    assertThat(thrown).hasMessageThat().contains("{query}[0] expected to be String");
  }

  @Test
  public void ParameterizedQueryShouldNotBeBackquoted() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT `{query}` WHERE TRUE", /* query */ ParameterizedQuery.of("1")));
    assertThat(thrown).hasMessageThat().contains("ParameterizedQuery should not be backtick quoted: `{query}`");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void backquoteAndSingleQuoteMixed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT * FROM `{tbl}'", "jobs"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}'");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void singleQuoteAndBackquoteMixed() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT * FROM '{tbl}`", "jobs"));
    assertThat(thrown).hasMessageThat().contains("'{tbl}`");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void missingOpeningQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT {tbl}'", "jobs"));
    assertThat(thrown).hasMessageThat().contains("{tbl}'");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void missingClosingQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT '{tbl}", "jobs"));
    assertThat(thrown).hasMessageThat().contains("'{tbl}");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void missingOpeningBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT * FROM {tbl}`", "jobs"));
    assertThat(thrown).hasMessageThat().contains("{tbl}`");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void missingClosingBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT * FROM `{tbl}", "jobs"));
    assertThat(thrown).hasMessageThat().contains("`{tbl}");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void missingOpeningDoubleQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT {tbl}\"", "jobs"));
    assertThat(thrown).hasMessageThat().contains("{tbl}\"");
  }

  @Test
  @SuppressWarnings("TemplateStringArgsMustBeQuotedCheck")
  public void missingClosingDoubleQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> ParameterizedQuery.of("SELECT \"{tbl}", "jobs"));
    assertThat(thrown).hasMessageThat().contains("\"{tbl}");
  }

  @Test
  public void listMissingOpeningQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT {id}'", asList(ParameterizedQuery.of("id"))));
    assertThat(thrown).hasMessageThat().contains("{id}'");
  }

  @Test
  public void listMissingClosingQuote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT '{id}", asList(ParameterizedQuery.of("id"))));
    assertThat(thrown).hasMessageThat().contains("'{id}");
  }

  @Test
  public void listMissingOpeningBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT * FROM {id}`", asList(ParameterizedQuery.of("id"))));
    assertThat(thrown).hasMessageThat().contains("{id}`");
  }

  @Test
  public void listMissingClosingBackquote() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> ParameterizedQuery.of("SELECT * FROM `{id}", asList(ParameterizedQuery.of("id"))));
    assertThat(thrown).hasMessageThat().contains("`{id}");
  }

  @Test
  public void twoParameters_whitespaceSeparated() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select '{label}' where id = {id}", /* label */ "foo", /* id */ 123);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @label where id = @id")
            .bind("label").to("foo")
            .bind("id").to(123L)
            .build());
    assertThat(sql.toString()).isEqualTo("select @label /* foo */ where id = @id /* 123 */");
  }

  @Test
  public void twoParameters_connectedByOperator() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select label where size={a}-{b}", /* a */ 100, /* b */ 50);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select label where size=@a-@b")
            .bind("a").to(100)
            .bind("b").to(50)
            .build());
    assertThat(sql.toString()).isEqualTo("select label where size=@a /* 100 */-@b /* 50 */");
  }

  @Test
  public void twoParameters_followedByWord_whitespaceInsertedAfterParameterName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select label where {a}is null", /* a */ 100);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select label where @a is null")
            .bind("a").to(100)
            .build());
    assertThat(sql.toString()).isEqualTo("select label where @a /* 100 */is null");
  }

  @Test
  public void twoParameters_followedByNumber_whitespaceInsertedAfterParameterName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select label where {a}1", /* a */ 100);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select label where @a 1")
            .bind("a").to(100)
            .build());
    assertThat(sql.toString()).isEqualTo("select label where @a /* 100 */1");
  }

  @Test
  public void twoParameters_followedByUnderscore_whitespaceInsertedAfterParameterName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select label where {a}_1", /* a */ 100);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select label where @a _1")
            .bind("a").to(100)
            .build());
    assertThat(sql.toString()).isEqualTo("select label where @a /* 100 */_1");
  }

  @Test
  public void twoParameters_followedByDash_whitespaceInsertedAfterParameterName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select label where {a}-1", /* a */ 100);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select label where @a-1")
            .bind("a").to(100)
            .build());
    assertThat(sql.toString()).isEqualTo("select label where @a /* 100 */-1");
  }

  @Test
  public void twoParameters_followedByEqualSign_whitespaceInsertedAfterParameterName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select label where {a}=1", /* a */ 100);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select label where @a=1")
            .bind("a").to(100)
            .build());
    assertThat(sql.toString()).isEqualTo("select label where @a /* 100 */=1");
  }

  @Test
  public void twoParameters_followedByComma_whitespaceInsertedAfterParameterName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select label where {a},1", /* a */ 100);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select label where @a,1")
            .bind("a").to(100)
            .build());
    assertThat(sql.toString()).isEqualTo("select label where @a /* 100 */,1");
  }

  @Test
  public void parameterizeByTableName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select * from {tbl} where id = {id}", /* tbl */ ParameterizedQuery.of("Users"), /* id */ 123);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from Users where id = @id").bind("id").to(123L).build());
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void twoParametersWithSameName() {
    ParameterizedQuery sql =
        ParameterizedQuery.of("select * where id = {id} and partner_id = {id}", 123, 456);
    assertThat(sql.toString()).isEqualTo("select * where id = @id /* 123 */ and partner_id = @id_1 /* 456 */");
  }

  @Test
  public void paramNameWithQuestionMark_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * where id = {foo?}", 123));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void paramNameWithAtSign_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * where id = {foo@}", 123));
    assertThat(thrown).hasMessageThat().contains("instead of '@'");
  }

  @Test
  public void sqlWithQuestionMark() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * where id = ?"));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void sqlWithQuestionAtSign() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * where id = @"));
    assertThat(thrown).hasMessageThat().contains("instead of '@'");
  }

  @Test
  public void singleStringParameterValueHasQuestionMark() {
    ParameterizedQuery sql = ParameterizedQuery.of("select '{str}'", "?");
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @str").bind("str").to("?").build());
  }

  @Test
  public void singleStringParameterValueHasAtSign() {
    ParameterizedQuery sql = ParameterizedQuery.of("select '{str}'", "@");
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select @str").bind("str").to("@").build());
  }

  @Test
  public void subqueryHasQuestionMark_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * from {tbl}", /* tbl */ ParameterizedQuery.of("?")));
    assertThat(thrown).hasMessageThat().contains("instead of '?'");
  }

  @Test
  public void inListOfParameters_withBoolParameterValues() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where active in ({statuses})", /* statuses */ asList(true, false));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where active in (@statuses)")
            .bind("statuses").toBoolArray(asList(true, false))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where active in (@statuses /* [true,false] */)");
  }

  @Test
  public void inListOfParameters_withStringValues() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where id in ({ids})", /* ids */ asList("foo", "bar"));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toStringArray(asList("foo", "bar"))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (@ids /* [foo,bar] */)");
  }

  @Test
  public void inListOfParameters_withInt32ParameterValues() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where id in ({ids})", /* ids */ asList(1, 2, 3));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toInt64Array(asList(1L, 2L, 3L))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (@ids /* [1,2,3] */)");
  }

  @Test
  public void inListOfParameters_withInt64ParameterValues() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where id in ({ids})", /* ids */ asList(1L, 2L, 3L));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toInt64Array(asList(1L, 2L, 3L))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (@ids /* [1,2,3] */)");
  }

  @Test
  public void inListOfParameters_withFloatParameterValues() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where id in ({ids})", /* ids */ asList(1F, 2F));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toFloat32Array(asList(1F, 2F))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (@ids /* [1.0,2.0] */)");
  }

  @Test
  public void inListOfParameters_withDoubleParameterValues() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where id in ({ids})", /* ids */ asList(1D, 2D));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toFloat64Array(asList(1D, 2D))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (@ids /* [1.0,2.0] */)");
  }

  @Test
  public void inListOfParameters_withBigDecimalParameterValues() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where id in ({ids})",
        /* ids */ asList(BigDecimal.valueOf(1), BigDecimal.valueOf(2)));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toNumericArray(asList(BigDecimal.valueOf(1), BigDecimal.valueOf(2)))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (@ids /* [1,2] */)");
  }

  @Test
  public void inListOfParameters_withInstantValues() {
    Instant t1 = Instant.ofEpochSecond(10000);
    Instant t2 = Instant.ofEpochSecond(30000);
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where ts in ({times})", /* times */ asList(t1, t2));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where ts in (@times)")
            .bind("times").toTimestampArray(asList(parseTimestamp(t1.toString()), parseTimestamp(t2.toString())))
            .build());
  }

  @Test
  public void inListOfParameters_withZonedDateTimeValues() {
    Instant t1 = Instant.ofEpochSecond(10000);
    Instant t2 = Instant.ofEpochSecond(30000);
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where ts in ({times})",
        /* times */ asList(t1.atZone(ZoneId.of("UTC")), t2.atZone(ZoneId.of("America/New_York"))));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where ts in (@times)")
            .bind("times").toTimestampArray(asList(parseTimestamp(t1.toString()), parseTimestamp(t2.toString())))
            .build());
  }

  @Test
  public void inListOfParameters_withOffsetDateTimeValues() {
    Instant t1 = Instant.ofEpochSecond(10000);
    Instant t2 = Instant.ofEpochSecond(30000);
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where ts in ({times})",
        /* times */ asList(t1.atOffset(ZoneOffset.UTC), t2.atOffset(ZoneOffset.ofHours(7))));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where ts in (@times)")
            .bind("times").toTimestampArray(asList(parseTimestamp(t1.toString()), parseTimestamp(t2.toString())))
            .build());
  }

  @Test
  public void inListOfParameters_withLocalDateValues() {
    LocalDate day1 = LocalDate.of(1997, 1, 15);
    LocalDate day2 =  LocalDate.of(2025, 4, 1);
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where ts in ({days})",
        /* days */ asList(day1, day2));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where ts in (@days)")
            .bind("days").toDateArray(asList(Date.parseDate(day1.toString()), Date.parseDate(day2.toString())))
            .build());
  }

  @Test
  public void inListOfParameters_withUuidValues() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where id in ({ids})",
        /* ids */ asList(id1, id2));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toUuidArray(asList(id1, id2))
            .build());
  }

  @Test
  public void inListOfParameters_withByteArrayValues() {
    ByteArray bytes1 = ByteArray.copyFrom(new byte[] {1, 3, 5});
    ByteArray bytes2 = ByteArray.copyFrom(new byte[] {2, 4});
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where id in ({ids})",
        /* ids */ asList(bytes1, bytes2));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toBytesArray(asList(bytes1, bytes2))
            .build());
  }

  @Test
  public void inListOfParameters_withIntervalValues() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where id in ({ids})",
        /* ids */ asList(Interval.ofDays(1), Interval.ofMonths(2)));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toIntervalArray(asList(Interval.ofDays(1), Interval.ofMonths(2)))
            .build());
  }

  @Test
  public void inListOfParameters_withStructValues() {
    Struct struct1 = Struct.newBuilder().add(Value.string("foo")).build();
    Struct struct2 = Struct.newBuilder().add(Value.string("bar")).build();
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from tbl where id in ({ids})",
        /* ids */ asList(struct1, struct2));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toStructArray(struct1.getType(), asList(struct1, struct2))
            .build());
  }

  @Test
  public void inListOfParameters_hybridList_disallowed() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * from tbl where id in ({ids})", /* ids */ asList(1, 2L, 3L)));
    assertThat(thrown).hasMessageThat().contains("Long");
    assertThat(thrown).hasMessageThat().contains("Integer");
  }

  @Test
  public void placeholderMustHaveAlphaNames() {
    IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> ParameterizedQuery.of("{...}", 2));
    assertThat(thrown).hasMessageThat().contains("alpha character");
  }

  @Test
  public void inListOfParameters_mixParameterValuesWithSubqueries_disallowd() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of(
            "select * from tbl where id in ({ids})", /* ids */ asList(1, ParameterizedQuery.of("{num}", 2), 3)));
    assertThat(thrown).hasMessageThat().contains("Value for {ids}");
  }

  @Test
  public void inListOfQuotedStringParameters() {
    ParameterizedQuery sql = ParameterizedQuery.of("select * from tbl where id in ('{ids}')", /* ids */ asList("foo", "bar"));
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from tbl where id in (@ids)")
            .bind("ids").toStringArray(asList("foo", "bar"))
            .build());
    assertThat(sql.toString()).isEqualTo("select * from tbl where id in (@ids /* [foo,bar] */)");
  }

  @Test
  public void inListOfQuotedNonStringParameters_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () ->  ParameterizedQuery.of("select * from tbl where id in ('{ids}')", /* ids */ asList("foo", 2)));
    assertThat(thrown).hasMessageThat().contains("{ids}[1] expected to be String");
  }

  @Test
  public void inListOfParameters_emptyList() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * from tbl where id in ({ids})", /* ids */ asList()));
    assertThat(thrown).hasMessageThat().contains("{ids}");
    assertThat(thrown).hasMessageThat().contains("empty");
  }

  @Test
  public void when_conditionalIsFalse_returnsEmpty() {
    assertThat(ParameterizedQuery.when(false, "WHERE id = {id}", 1)).isEqualTo(ParameterizedQuery.EMPTY);
  }

  @Test
  public void when_conditionalIsTrue_returnsQuery() {
    assertThat(ParameterizedQuery.when(true, "WHERE id = {id}", 1))
        .isEqualTo(ParameterizedQuery.of("WHERE id = {id}", 1));
  }

  @Test
  public void postfixWhen_conditionalIsFalse_returnsEmpty() {
    assertThat(ParameterizedQuery.of("WHERE id = {id}", 1).when(false)).isEqualTo(ParameterizedQuery.EMPTY);
  }

  @Test
  public void postfixWhen_conditionalIsTrue_returnsQuery() {
    assertThat(ParameterizedQuery.of("WHERE id = {id}", 1).when(true))
        .isEqualTo(ParameterizedQuery.of("WHERE id = {id}", 1));
  }


  @Test
  public void joiningByStringConstant() {
    assertThat(Stream.of(ParameterizedQuery.of("a"), ParameterizedQuery.of("b")).collect(ParameterizedQuery.joining(" AND ")))
        .isEqualTo(ParameterizedQuery.of("a AND b"));
  }

  @Test
  public void joining_ignoresEmpty() {
    assertThat(Stream.of(ParameterizedQuery.of("a"), ParameterizedQuery.EMPTY).collect(ParameterizedQuery.joining(" AND ")))
        .isEqualTo(ParameterizedQuery.of("a"));
  }

  @Test
  public void andCollector_empty() {
    ImmutableList<ParameterizedQuery> queries = ImmutableList.of();
    assertThat(queries.stream().collect(ParameterizedQuery.and())).isEqualTo(ParameterizedQuery.of("(1 = 1)"));
  }

  @Test
  public void andCollector_singleCondition() {
    ImmutableList<ParameterizedQuery> queries = ImmutableList.of(ParameterizedQuery.of("a = 1"));
    assertThat(queries.stream().collect(ParameterizedQuery.and())).isEqualTo(ParameterizedQuery.of("(a = 1)"));
  }

  @Test
  public void andCollector_twoConditions() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(ParameterizedQuery.of("a = 1"), ParameterizedQuery.of("b = 2 OR c = 3"));
    assertThat(queries.stream().collect(ParameterizedQuery.and()))
        .isEqualTo(ParameterizedQuery.of("(a = 1) AND (b = 2 OR c = 3)"));
  }

  @Test
  public void andCollector_threeConditions() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(
            ParameterizedQuery.of("a = 1"), ParameterizedQuery.of("b = 2 OR c = 3"), ParameterizedQuery.of("d = 4"));
    assertThat(queries.stream().collect(ParameterizedQuery.and()))
        .isEqualTo(ParameterizedQuery.of("(a = 1) AND (b = 2 OR c = 3) AND (d = 4)"));
  }

  @Test
  public void andCollector_threeConditionsWithParameters() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(
            ParameterizedQuery.of("a = {v1}", 1), ParameterizedQuery.of("b = {v2} OR c = {v3}", 2, 3), ParameterizedQuery.of("d = {v4}", 4));
    ParameterizedQuery sql = queries.stream().collect(ParameterizedQuery.and());
    assertThat(sql.toString()).isEqualTo("(a = @v1 /* 1 */) AND (b = @v2 /* 2 */ OR c = @v3 /* 3 */) AND (d = @v4 /* 4 */)");
  }

  @Test
  public void andCollector_ignoresEmpty() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(ParameterizedQuery.EMPTY, ParameterizedQuery.of("b = 2 OR c = 3"), ParameterizedQuery.of("d = 4"));
    assertThat(queries.stream().collect(ParameterizedQuery.and()))
        .isEqualTo(ParameterizedQuery.of("(b = 2 OR c = 3) AND (d = 4)"));
  }

  @Test
  public void orCollector_empty() {
    ImmutableList<ParameterizedQuery> queries = ImmutableList.of();
    assertThat(queries.stream().collect(ParameterizedQuery.or())).isEqualTo(ParameterizedQuery.of("(1 = 0)"));
  }

  @Test
  public void orCollector_singleCondition() {
    ImmutableList<ParameterizedQuery> queries = ImmutableList.of(ParameterizedQuery.of("a = 1"));
    assertThat(queries.stream().collect(ParameterizedQuery.or())).isEqualTo(ParameterizedQuery.of("(a = 1)"));
  }

  @Test
  public void orCollector_twoConditions() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(ParameterizedQuery.of("a = 1"), ParameterizedQuery.of("b = 2 AND c = 3"));
    assertThat(queries.stream().collect(ParameterizedQuery.or()))
        .isEqualTo(ParameterizedQuery.of("(a = 1) OR (b = 2 AND c = 3)"));
  }

  @Test
  public void orCollector_threeConditions() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(
            ParameterizedQuery.of("a = 1"), ParameterizedQuery.of("b = 2 AND c = 3"), ParameterizedQuery.of("d = 4"));
    assertThat(queries.stream().collect(ParameterizedQuery.or()))
        .isEqualTo(ParameterizedQuery.of("(a = 1) OR (b = 2 AND c = 3) OR (d = 4)"));
  }

  @Test
  public void orCollector_threeConditionsWithParameters() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(
            ParameterizedQuery.of("a = {v1}", 1), ParameterizedQuery.of("b = {v2} AND c = {v3}", 2, 3), ParameterizedQuery.of("d = {v4}", 4));
    ParameterizedQuery sql = queries.stream().collect(ParameterizedQuery.or());
    assertThat(sql.toString()).isEqualTo("(a = @v1 /* 1 */) OR (b = @v2 /* 2 */ AND c = @v3 /* 3 */) OR (d = @v4 /* 4 */)");
  }

  @Test
  public void orCollector_ignoresEmpty() {
    ImmutableList<ParameterizedQuery> queries =
        ImmutableList.of(ParameterizedQuery.EMPTY, ParameterizedQuery.of("b = 2 AND c = 3"), ParameterizedQuery.of("d = 4"));
    assertThat(queries.stream().collect(ParameterizedQuery.or()))
        .isEqualTo(ParameterizedQuery.of("(b = 2 AND c = 3) OR (d = 4)"));
  }

  @Test
  public void namesInAnonymousSubqueriesAreIndependent() {
    ParameterizedQuery sql =
        Stream.of(1, 2, 3).map(id -> ParameterizedQuery.of("id = {id}", id)).collect(ParameterizedQuery.or());
    assertThat(sql.toString()).isEqualTo("(id = @id /* 1 */) OR (id = @id_1 /* 2 */) OR (id = @id_2 /* 3 */)");
  }

  @Test
  public void namesInSubqueryAndParentQueryDontConflict() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from ({tbl}) where id = {id}",
        ParameterizedQuery.of("select * from tbl where id = {id}", 1), /* id */ 2);
    assertThat(sql.statement())
        .isEqualTo(Statement.newBuilder("select * from (select * from tbl where id = @id) where id = @id_1")
            .bind("id").to(1L)
            .bind("id_1").to(2L)
            .build());
  }

  @Test
  public void namesInSubqueriesDontConflict() {
    ParameterizedQuery sql = ParameterizedQuery.of(
        "select * from ({tbl1}), ({tbl2}) where id = {id}",
        /* tbl1 */ ParameterizedQuery.of("select * from tbl where id = {id}", 1),
        /* tbl2 */ ParameterizedQuery.of("select * from tbl where id = {id}", 2),
        /* id */ 3);
    assertThat(sql.toString())
        .isEqualTo("select * from (select * from tbl where id = @id /* 1 */), (select * from tbl where id = @id_1 /* 2 */) where id = @id_2 /* 3 */");
  }

  @Test
  public void optionalParameterDisallowed() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ParameterizedQuery.of("select * where id = {id}", /* id */ Optional.of(1)));
    assertThat(thrown).hasMessageThat().contains("{id? -> ...}");
  }

  @Test
  public void orElse_empty_returnsFallbackQuery() {
    assertThat(ParameterizedQuery.EMPTY.orElse("WHERE id = {id}", 1))
        .isEqualTo(ParameterizedQuery.of("WHERE id = {id}", 1));
  }

  @Test
  public void orElse_nonEmpty_returnsTheMainlineQuery() {
    assertThat(ParameterizedQuery.of("select *").orElse("WHERE id = {id}", 1))
        .isEqualTo(ParameterizedQuery.of("select *"));
  }

  @Test
  public void accidentalBlockComment_disallowed() {
    ParameterizedQuery sub = ParameterizedQuery.of("*1");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> ParameterizedQuery.of("/{sub}", sub));
    assertThat(thrown).hasMessageThat().contains("/*1");
  }

  @Test
  public void accidentalLineComment_disallowed() {
    ParameterizedQuery sub = ParameterizedQuery.of("-1");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> ParameterizedQuery.of("-{sub}", sub));
    assertThat(thrown).hasMessageThat().contains("--1");
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(
            ParameterizedQuery.of("select * from tbl"),
            ParameterizedQuery.of("select * from {tbl}", ParameterizedQuery.of("tbl")))
        .addEqualityGroup(
            ParameterizedQuery.of("select id from tbl where id = {id}", 1))
        .addEqualityGroup(
            ParameterizedQuery.of("select id from tbl where id = {i}", 1))
        .addEqualityGroup(ParameterizedQuery.of("select id from tbl where id = 1"))
        .addEqualityGroup(ParameterizedQuery.of("select id from tbl where id = {id}", 2))
        .testEquals();
  }

  @Test
  public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(ParameterizedQuery.class);
    new NullPointerTester().testAllPublicInstanceMethods(ParameterizedQuery.of("select *"));
  }

  private enum Pii {
    SSN, EMAIL;

    @Override public String toString() {
      return Ascii.toLowerCase(name());
    }
  }

  @Test public void withTwoParameters() {
    long id = 123;
    String name = "%foo%";
    ParameterizedQuery query = ParameterizedQuery.of(
        "SELECT * FROM Users where id = {id} AND name LIKE '{name}'", id, name);
    assertThat(query.toString())
        .isEqualTo("SELECT * FROM Users where id = @id /* 123 */ AND name LIKE @name /* %foo% */");
  }

}
