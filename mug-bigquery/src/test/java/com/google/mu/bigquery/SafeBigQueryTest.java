package com.google.mu.bigquery;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.bigquery.SafeBigQuery.template;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.mu.util.StringFormat;

@RunWith(JUnit4.class)
public class SafeBigQueryTest {
  @Test
  public void template_noArg() {
    assertThat(template("SELECT *").with()).isEqualTo(QueryJobConfiguration.of("SELECT *"));
  }

  @Test
  public void template_trustedArg() {
    assertThat(template("SELECT * FROM {tbl}").with(/* tbl */ TrustedSql.of("Jobs")))
        .isEqualTo(QueryJobConfiguration.of("SELECT * FROM Jobs"));
  }

  @Test
  public void template_nullArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {expr} where {cond}");
    assertThat(query.with(/* expr */ null, /* cond */ true))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT NULL where @cond")
                .addNamedParameter("cond", QueryParameterValue.bool(true))
                .build());
  }

  @Test
  public void template_boolArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ false))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.bool(false))
                .build());
  }

  @Test
  public void template_stringArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT * where message like '%{id}%'");
    assertThat(query.with("1's id"))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * where message like '%@id%'")
                .addNamedParameter("id", QueryParameterValue.string("1's id"))
                .build());
  }

  @Test
  public void template_instantArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT * where timestamp = {now};");
    ZonedDateTime now =
        ZonedDateTime.of(2023, 10, 1, 8, 30, 0, 90000, ZoneId.of("America/Los_Angeles"));
    assertThat(query.with(now.toInstant()))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * where timestamp = @now;")
                .addNamedParameter(
                    "now", QueryParameterValue.timestamp("2023-10-01 15:30:00.000090+0000"))
                .build());
  }

  @Test
  public void template_localDateArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT * where date = {date};");
    assertThat(query.with(LocalDate.of(2023, 12, 1)))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * where date = @date;")
                .addNamedParameter("date", QueryParameterValue.date("2023-12-01"))
                .build());
  }

  @Test
  public void template_queryParameterValueArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {param}");
    QueryParameterValue param = QueryParameterValue.array(new Integer[] {1, 2}, Integer.class);
    assertThat(query.with(param))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @param")
                .addNamedParameter("param", param)
                .build());
  }

  @Test
  public void template_intArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.int64(1))
                .build());
  }

  @Test
  public void template_longArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1L))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.int64(1L))
                .build());
  }

  @Test
  public void template_floatArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1.0F))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.float64(1.0F))
                .build());
  }

  @Test
  public void template_doubleArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ 1.0D))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter("expr", QueryParameterValue.float64(1.0D))
                .build());
  }

  @Test
  public void template_bigDecimalArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT {expr}");
    assertThat(query.with(/* expr */ new BigDecimal("1.23456")))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT @expr")
                .addNamedParameter(
                    "expr", QueryParameterValue.bigNumeric(new BigDecimal("1.23456")))
                .build());
  }

  @Test
  public void template_enumArg() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT * WHERE status = {status}");
    assertThat(query.with(Status.ACTIVE))
        .isEqualTo(
            QueryJobConfiguration.newBuilder("SELECT * WHERE status = @status")
                .addNamedParameter("status", QueryParameterValue.string("ACTIVE"))
                .build());
  }

  @Test
  public void template_duplicatePlaceholderNameThrows() {
    StringFormat.To<QueryJobConfiguration> query =
        template("SELECT * WHERE status in ({status}, {status}");
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> query.with(Status.ACTIVE, Status.INACTIVE));
    assertThat(thrown).hasMessageThat().contains("{status}");
  }

  @Test
  public void template_iterableArgNotSupported() {
    StringFormat.To<QueryJobConfiguration> query = template("SELECT * WHERE status = {status}");
    assertThrows(IllegalArgumentException.class, () -> query.with(asList(Status.ACTIVE)));
  }

  private enum Status {
    ACTIVE,
    INACTIVE
  }
}
