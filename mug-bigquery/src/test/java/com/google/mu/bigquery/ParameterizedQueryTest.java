package com.google.mu.bigquery;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.bigquery.ParameterizedQuery.template;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.common.testing.EqualsTester;
import com.google.mu.util.StringFormat;

@RunWith(JUnit4.class)
public class ParameterizedQueryTest {
  @Test
  public void template_noArg() {
    assertThat(ParameterizedQuery.of("SELECT *").jobConfiguration())
        .isEqualTo(QueryJobConfiguration.of("SELECT *"));
  }

  @Test
  public void template_trustedArg() {
    ParameterizedQuery query =
        template("SELECT * FROM {tbl}").with(/* tbl */ ParameterizedQuery.of("Jobs"));
    assertThat(query.jobConfiguration()).isEqualTo(QueryJobConfiguration.of("SELECT * FROM Jobs"));
  }

  @Test
  public void template_subqueryArg() {
    ParameterizedQuery query =
        template("SELECT * FROM {tbl} WHERE id in ({ids})")
            .with(
                /* tbl */ ParameterizedQuery.of("Jobs"),
                /* ids */ ParameterizedQuery.of("SELECT id FROM Students where status = {status}", Status.ACTIVE));
    assertThat(query.jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * FROM Jobs WHERE id in (SELECT id FROM Students where status = @status)")
                .addNamedParameter("status", QueryParameterValue.string("ACTIVE"))
                .build());
  }

  @Test
  public void template_nullArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {expr} where {cond}");
    assertThat(query.with(/* expr */ null, /* cond */ true).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT NULL where @cond")
                .addNamedParameter("cond", QueryParameterValue.bool(true))
                .build());
  }

  @Test
  public void template_boolArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ false).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.bool(false))
                .build());
  }

  @Test
  public void template_stringArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT * where message like '%{id}%'");
    assertThat(query.with("1's id").jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * where message like '%@id%'")
                .addNamedParameter("id", QueryParameterValue.string("1's id"))
                .build());
  }

  @Test
  public void template_instantArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT * where timestamp = {now};");
    ZonedDateTime now =
        ZonedDateTime.of(2023, 10, 1, 8, 30, 0, 90000, ZoneId.of("America/Los_Angeles"));
    assertThat(query.with(now.toInstant()).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * where timestamp = @now;")
                .addNamedParameter(
                    "now", QueryParameterValue.timestamp("2023-10-01 15:30:00.000090+0000"))
                .build());
  }

  @Test
  public void template_localDateArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT * where date = {date};");
    assertThat(query.with(LocalDate.of(2023, 12, 1)).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * where date = @date;")
                .addNamedParameter("date", QueryParameterValue.date("2023-12-01"))
                .build());
  }

  @Test
  public void template_queryParameterValueArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {param}");
    QueryParameterValue param = QueryParameterValue.array(new Integer[] {1, 2}, Integer.class);
    assertThat(query.with(param).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @param")
                .addNamedParameter("param", param)
                .build());
  }

  @Test
  public void template_intArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.int64(1))
                .build());
  }

  @Test
  public void template_longArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1L).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.int64(1L))
                .build());
  }

  @Test
  public void template_floatArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1.0F).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.float64(1.0F))
                .build());
  }

  @Test
  public void template_doubleArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1.0D).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.float64(1.0D))
                .build());
  }

  @Test
  public void template_bigDecimalArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ new BigDecimal("1.23456")).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter(
                    "expr", QueryParameterValue.bigNumeric(new BigDecimal("1.23456")))
                .build());
  }

  @Test
  public void template_enumArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT * WHERE status = {status}");
    assertThat(query.with(Status.ACTIVE).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * WHERE status = @status")
                .addNamedParameter("status", QueryParameterValue.string("ACTIVE"))
                .build());
  }

  @Test
  public void template_stringArrayArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {names}");
    assertThat(query.with(/* names */ (Object) new String[] {"foo", "bar"}).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @names")
                .addNamedParameter(
                    "names", QueryParameterValue.array(new String[] {"foo", "bar"}, String.class))
                .build());
  }

  @Test
  public void template_integerArrayArg() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT {ids}");
    assertThat(query.with(/* ids */ (Object) new Integer[] {1, 2}).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @ids")
                .addNamedParameter(
                    "ids", QueryParameterValue.array(new Integer[] {1, 2}, Integer.class))
                .build());
  }

  @Test
  @SuppressWarnings("StringFormatArgsCheck")
  public void template_duplicatePlaceholderName_throwsWithConflictingValues() {
    StringFormat.To<ParameterizedQuery> query =
        template("SELECT * WHERE status in ({status}, {status}");
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> query.with(Status.ACTIVE, Status.INACTIVE));
    assertThat(thrown).hasMessageThat().contains("status");
  }

  @Test
  public void template_duplicatePlaceholderName_okWithConsistentValues() {
    StringFormat.To<ParameterizedQuery> query =
        template("SELECT {status} as status WHERE status = {status}");
    assertThat(query.with(Status.ACTIVE, Status.ACTIVE).jobConfiguration())
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @status as status WHERE status = @status")
                .addNamedParameter("status", QueryParameterValue.string("ACTIVE"))
                .build());
  }

  @Test
  public void template_iterableArgNotSupported() {
    StringFormat.To<ParameterizedQuery> query = template("SELECT * WHERE status = {status}");
    assertThrows(IllegalArgumentException.class, () -> query.with(asList(Status.ACTIVE)));
  }

  @Test
  public void testJoining_noParameters() {
    assertThat(
            Stream.of(ParameterizedQuery.of("a"), ParameterizedQuery.of("b"))
                .collect(ParameterizedQuery.joining(", ")))
        .isEqualTo(ParameterizedQuery.of("a, b"));
  }

  @Test
  public void testJoining_withParameters() {
    assertThat(
            Stream.of(ParameterizedQuery.of("{v1}", 1), ParameterizedQuery.of("{v2}", "2"))
                .collect(ParameterizedQuery.joining(", ")))
        .isEqualTo(ParameterizedQuery.of("{v1}, {v2}", 1, "2"));
  }

  @Test
  public void testJoining_parallel() {
    ParameterizedQuery query =
        Stream.of(
                ParameterizedQuery.of("{v1}", 1),
                ParameterizedQuery.of("{v2}", "2"),
                ParameterizedQuery.of("{v3}", 3))
            .parallel()
            .collect(ParameterizedQuery.joining(", "));
    assertThat(query).isEqualTo(ParameterizedQuery.of("{v1}, {v2}, {v3}", 1, "2", 3));
  }

  @Test
  public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(ParameterizedQuery.of("foo"))
        .addEqualityGroup(ParameterizedQuery.of("bar"))
        .addEqualityGroup(
            ParameterizedQuery.of("select {col1}, {col2}", "a", "b"),
            ParameterizedQuery.of("select {col1}, {col2}", "a", "b"))
        .addEqualityGroup(ParameterizedQuery.of("select {col1}, {col2}", "b", "a"))
        .testEquals();
  }

  private enum Status {
    ACTIVE,
    INACTIVE
  }
}
