package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.safesql.GoogleSql.template;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;

@RunWith(JUnit4.class)
public class GoogleSqlTest {
  @BeforeClass  // Consistently set the system property across the test suite
  public static void setUpTrustedType() {
    System.setProperty(
        "com.google.mu.safesql.SafeQuery.trusted_sql_type",
        SafeQueryTest.TrustedSql.class.getName());
  }

  @Test
  public void farPastTimestampPlaceholder() {
    ZonedDateTime time = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"));
    assertThat(
            template("SELECT * FROM tbl WHERE creation_time = {creation_time}")
                .with(/* creation_time */ time.toInstant()))
        .isEqualTo(
            SafeQuery.of(
                "SELECT * FROM tbl WHERE creation_time = "
                    + "TIMESTAMP('1900-01-01T00:00:00.000000', 'America/Los_Angeles')"));
  }

  @Test
  public void farFutureTimestampPlaceholder() {
    ZonedDateTime time =
        ZonedDateTime.of(2100, 12, 31, 23, 59, 59, 0, ZoneId.of("America/Los_Angeles"));
    assertThat(
            template("SELECT * FROM tbl WHERE creation_time = {creation_time}")
                .with(/* creation_time */ time.toInstant()))
        .isEqualTo(
            SafeQuery.of(
                "SELECT * FROM tbl WHERE creation_time = "
                    + "TIMESTAMP('2100-12-31T23:59:59.000000', 'America/Los_Angeles')"));
  }

  @Test
  public void timestampMillisPlaceholder() {
    assertThat(
            template("SELECT * FROM tbl WHERE creation_time = {creation_time}")
                .with(/* creation_time */ Instant.ofEpochMilli(123456)))
        .isEqualTo(
            SafeQuery.of(
                "SELECT * FROM tbl WHERE creation_time = "
                    + "TIMESTAMP('1969-12-31T16:02:03.456000', 'America/Los_Angeles')"));
  }

  @Test
  public void timestampSecondsPlaceholder() {
    assertThat(
            template("SELECT * FROM tbl WHERE creation_time = {creation_time}")
                .with(/* creation_time */ Instant.ofEpochSecond(123456)))
        .isEqualTo(
            SafeQuery.of(
                "SELECT * FROM tbl WHERE creation_time = "
                    + "TIMESTAMP('1970-01-02T02:17:36.000000', 'America/Los_Angeles')"));
  }

  @Test
  public void dateTimePlaceholder() {
    assertThat(
            template("SELECT * FROM tbl WHERE creation_time = {creation_time}")
                .with(
                    /* creation_time */ ZonedDateTime.of(
                        2023, 10, 1, 8, 30, 0, 90000, ZoneId.of("America/Los_Angeles"))))
        .isEqualTo(
            SafeQuery.of(
                "SELECT * FROM tbl WHERE creation_time = "
                    + "DATETIME('2023-10-01T08:30:00.000090', 'America/Los_Angeles')"));
  }

  @Test
  public void datePlaceholder() {
    assertThat(
            template("SELECT * FROM tbl WHERE creation_date = {date}")
                .with(LocalDate.of(2023, 10, 1)))
        .isEqualTo(SafeQuery.of("SELECT * FROM tbl WHERE creation_date = DATE(2023, 10, 1)"));
  }

  @Test
  public void listOfTimestamp() {
    ZonedDateTime time = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"));
    assertThat(
            template("SELECT * FROM tbl WHERE creation_time in ({instants})")
                .with(/* instants */ ImmutableList.of(time.toInstant())))
        .isEqualTo(
            SafeQuery.of(
                "SELECT * FROM tbl WHERE creation_time in "
                    + "(TIMESTAMP('1900-01-01T00:00:00.000000', 'America/Los_Angeles'))"));
  }

  @Test
  public void mixedWithDefaultTranslation() {
    ZonedDateTime time = ZonedDateTime.of(1900, 1, 1, 0, 0, 0, 0, ZoneId.of("America/Los_Angeles"));
    assertThat(
            template("SELECT * FROM tbl WHERE creation_time = {instant} AND id = {id}")
                .with(time.toInstant(), /* id */ 1))
        .isEqualTo(
            SafeQuery.of(
                "SELECT * FROM tbl WHERE creation_time = "
                    + "TIMESTAMP('1900-01-01T00:00:00.000000', 'America/Los_Angeles') AND id = 1"));
  }
}
