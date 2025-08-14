/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.time;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.mu.time.DateTimeFormats.formatOf;
import static org.junit.Assert.assertThrows;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.truth.ComparableSubject;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public final class DateTimeFormatsTest {
  @Test
  public void dateOnlyExamples() {
    assertLocalDate("2023-10-20", "yyyy-MM-dd").isEqualTo(LocalDate.of(2023, 10, 20));
    assertLocalDate("1986/01/01", "yyyy/MM/dd").isEqualTo(LocalDate.of(1986, 1, 1));
  }

  @Test
  public void timeOnlyExamples() {
    assertLocalTime("10:30:00", "HH:mm:ss").isEqualTo(LocalTime.of(10, 30, 0));
    assertLocalTime("10:30", "HH:mm").isEqualTo(LocalTime.of(10, 30, 0));
    assertLocalTime("10:30:00.001234", "HH:mm:ss.SSSSSS")
        .isEqualTo(LocalTime.of(10, 30, 0, 1234000));
    assertLocalTime("10:30:00.123456789", "HH:mm:ss.SSSSSSSSS")
        .isEqualTo(LocalTime.of(10, 30, 0, 123456789));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourWithoutAmPm_throws() {
    assertThrows(DateTimeException.class, () -> formatOf("1"));
  }

  @Test
  public void singleDigitHourWithAmPm() {
    assertLocalTime("1AM", "ha").isEqualTo(LocalTime.of(1, 0, 0));
    assertLocalTime("2 PM", "h a").isEqualTo(LocalTime.of(14, 0, 0));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteWithoutAmPm_throws() {
    assertThrows(DateTimeException.class, () -> formatOf("1:10"));
  }

  @Test
  public void singleDigitHourMinuteWithAmPm() {
    assertLocalTime("1:10AM", "h:mma").isEqualTo(LocalTime.of(1, 10, 0));
    assertLocalTime("2:05 PM", "h:mm a").isEqualTo(LocalTime.of(14, 5, 0));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteSecondWithoutAmPm_throws() {
    assertThrows(DateTimeException.class, () -> formatOf("1:10:00"));
  }

  @Test
  public void singleDigitHourMinuteSecondWithAmPm() {
    assertLocalTime("1:10:30AM", "h:mm:ssa").isEqualTo(LocalTime.of(1, 10, 30));
    assertLocalTime("2:05:00 PM", "h:mm:ss a").isEqualTo(LocalTime.of(14, 5, 0));
  }

  @Test
  public void twoDigitHourWithAmPm() {
    assertLocalTime("09AM", "HHa").isEqualTo(LocalTime.of(9, 0, 0));
    assertLocalTime("12 PM", "HH a").isEqualTo(LocalTime.of(12, 0, 0));
  }

  @Test
  public void twoDigitHourMinuteWithoutAmPm() {
    assertLocalTime("09:00", "HH:mm").isEqualTo(LocalTime.of(9, 0, 0));
    assertLocalTime("15:00", "HH:mm").isEqualTo(LocalTime.of(15, 0, 0));
  }

  @Test
  public void twoDigitHourMinuteWithAmPm() {
    assertLocalTime("09:00AM", "HH:mma").isEqualTo(LocalTime.of(9, 0, 0));
    assertLocalTime("12:00 PM", "HH:mm a").isEqualTo(LocalTime.of(12, 0, 0));
  }

  @Test
  public void twoDigitHourMinuteSecondWithAmPm() {
    assertLocalTime("09:00:30AM", "HH:mm:ssa").isEqualTo(LocalTime.of(9, 0, 30));
    assertLocalTime("15:00:30 PM", "HH:mm:ss a").isEqualTo(LocalTime.of(15, 0, 30));
  }


  @Test
  public void twoDigitHourMinuteSecondWithoutAmPm() {
    assertLocalTime("09:00:30", "HH:mm:ss").isEqualTo(LocalTime.of(9, 0, 30));
    assertLocalTime("15:00:30", "HH:mm:ss").isEqualTo(LocalTime.of(15, 0, 30));
  }

  @Test
  public void dateAndTimeExamples() {
    assertLocalDateTime("2023-10-20 15:30:05", "yyyy-MM-dd HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2023, 10, 20, 15, 30, 5));
    assertLocalDateTime("2023/10/05 15:30:05", "yyyy/MM/dd HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2023, 10, 5, 15, 30, 5));
  }

  @Test
  public void instantExample() {
    assertThat(formatOf("2023-10-05T15:30:05Z").parse(Instant.now().toString())).isNotNull();
  }

  @Test
  public void isoLocalDateTimeExample() {
    assertThat(LocalDateTime.parse("2023-10-05T15:03:05", formatOf("2023-10-05T15:30:05")))
        .isEqualTo(LocalDateTime.of(2023, 10, 5, 15, 3, 5));
  }

  @Test
  public void isoLocalDateExample() {
    assertThat(LocalDate.parse("2022-10-05", formatOf("2023-10-05")))
        .isEqualTo(LocalDate.of(2022, 10, 5));
  }

  @Test
  public void isoOffsetDateTimeExample() {
    assertThat(
            ZonedDateTime.parse("2022-10-05T00:10:00-08:00", formatOf("2023-10-05T11:12:13-05:00")))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2022, 10, 5, 0, 10, 0, 0), ZoneId.of("-08:00")));
    assertThat(
            ZonedDateTime.parse(
                "2022-10-05T00:10:00.123456789-08:00", formatOf("2023-10-05T11:12:13-05:00")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2022, 10, 5, 0, 10, 0, 123456789), ZoneId.of("-08:00")));
  }

  @Test public void offsetDateTimeExample() {
    DateTimeFormatter formatter = formatOf("2001-10-30 00:00:00-07");
    assertThat(OffsetDateTime.parse("1976-10-31 01:12:35-07", formatter))
        .isEqualTo(OffsetDateTime.of(1976, 10, 31, 1, 12, 35, 0, ZoneOffset.ofHours(-7)));
    assertThat(OffsetDateTime.parse("1976-10-31 01:12:35-18", formatter))
    .isEqualTo(OffsetDateTime.of(1976, 10, 31, 1, 12, 35, 0, ZoneOffset.ofHours(-18)));
    assertThat(OffsetDateTime.parse("2001-10-30 00:00:00+03", formatter))
        .isEqualTo(OffsetDateTime.of(2001, 10, 30, 0, 0, 0, 0, ZoneOffset.ofHours(3)));
  }

  @Test
  public void isoZonedDateTimeExample() {
    assertThat(
            ZonedDateTime.parse(
                "2022-10-05T00:10:00-07:00[America/Los_Angeles]",
                formatOf("2023-10-09T11:12:13+01:00[Europe/Paris]")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2022, 10, 5, 0, 10, 0, 0), ZoneId.of("America/Los_Angeles")));
    assertThat(
            ZonedDateTime.parse(
                "2022-10-05T00:10:00.123456789-07:00[America/Los_Angeles]",
                formatOf("2023-10-09T11:12:13+01:00[Europe/Paris]")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2022, 10, 5, 0, 10, 0, 123456789),
                ZoneId.of("America/Los_Angeles")));
  }

  @Test
  public void isoZonedDateTime_withNanosExample() {
    assertThat(
            ZonedDateTime.parse(
                "2022-10-05T00:10:00.123456789-07:00[America/Los_Angeles]",
                formatOf("2023-10-09T11:12:13.1+01:00[Europe/Paris]")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2022, 10, 5, 0, 10, 0, 123456789),
                ZoneId.of("America/Los_Angeles")));
    assertThat(
            ZonedDateTime.parse(
                "2022-10-05T00:10:00.123456789-07:00[America/Los_Angeles]",
                formatOf("2023-10-09T11:12:13.123456+01:00[Europe/Paris]")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2022, 10, 5, 0, 10, 0, 123456789),
                ZoneId.of("America/Los_Angeles")));
  }

  @Test
  public void isoLocalTimeExample() {
    assertThat(LocalTime.parse("10:20:10", formatOf("10:30:12")))
        .isEqualTo(LocalTime.of(10, 20, 10));
  }

  @Test
  public void rfc1123Example() {
    assertThat(
            ZonedDateTime.parse(
                "Fri, 6 Jun 2008 03:10:10 GMT", formatOf("Tue, 10 Jun 2008 11:05:30 GMT")))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2008, 6, 6, 3, 10, 10, 0), ZoneOffset.UTC));
    assertThat(
            ZonedDateTime.parse(
                "6 Jun 2008 03:10:10 GMT", formatOf("Tue, 3 Jun 2008 11:05:30 GMT")))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2008, 6, 6, 3, 10, 10, 0), ZoneOffset.UTC));
    assertThat(
            ZonedDateTime.parse(
                "Fri, 20 Jun 2008 03:10:10 GMT", formatOf("13 Jun 2008 11:05:30 GMT")))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2008, 6, 20, 3, 10, 10, 0), ZoneOffset.UTC));
    assertThat(ZonedDateTime.parse("13 Jun 2008 03:10:10 GMT", formatOf("3 Jun 2008 11:05:30 GMT")))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2008, 6, 13, 3, 10, 10, 0), ZoneOffset.UTC));
  }

  @Test
  public void invalid_rfc3339Example() {
    assertThrows(DateTimeParseException.class, () -> DateTimeFormats.parseToInstant("2000-40-01T00:00:00Z"));

  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void monthOfYear_notSupported() {
    assertThrows(
        DateTimeException.class, () -> formatOf("Dec 31, 2023 12:00:00 America/New_York"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousMmddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("10/12/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("01/12/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("10/02/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousMddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/12/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("1/02/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousDdmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("10/1/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("01/1/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousDmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/2/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeMmddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("10/32/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("13/13/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeMddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/32/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("0/31/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeDdmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("32/1/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("31/0/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangemDdyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/32/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("0/31/2023 10:10:10"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeDmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("0/0/2023 10:10:10"));
  }

  @Test
  public void mmddyyyy_supportedIfDayIsGreaterThan12() {
    assertEquivalent(
        formatOf("10/13/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "MM/dd/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("10/31/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "MM/dd/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("10-13-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "MM-dd-yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("10-31-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "MM-dd-yyyy HH:mm:ss VV");
  }

  @Test
  public void mddyyyy_supportedIfDayIsGreaterThan12() {
    assertEquivalent(
        formatOf("1/13/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "M/dd/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("1/31/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "M/dd/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("1-13-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "M-dd-yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("1-31-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "M-dd-yyyy HH:mm:ss VV");
  }

  @Test
  public void ddmmyyyy_supportedIfDayIsGreaterThan12() {
    assertEquivalent(
        formatOf("13/10/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd/MM/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("31/10/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd/MM/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("13-10-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd-MM-yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("31-10-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd-MM-yyyy HH:mm:ss VV");
  }

  @Test
  public void ddmyyyy_supportedIfDayIsGreaterThan12() {
    assertEquivalent(
        formatOf("13/1/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd/M/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("31/1/2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd/M/yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("13-1-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd-M-yyyy HH:mm:ss VV");
    assertEquivalent(
        formatOf("31-1-2023 10:10:10 Europe/Paris"),
        ZonedDateTime.of(LocalDateTime.of(2023, 1, 2, 1, 2, 3), ZoneId.of("America/Los_Angeles")),
        "dd-M-yyyy HH:mm:ss VV");
  }

  @Test
  public void formatOf_mmddyyMixedIn() {
    DateTimeFormatter formatter = formatOf("MM/dd/yyyy <12:10:00> <America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertThat(zonedTime.format(formatter)).isEqualTo("10/20/2023 01:02:03 America/Los_Angeles");
  }

  @Test
  public void formatOf_ddmmyyMixedIn() {
    DateTimeFormatter formatter = formatOf("dd MM yyyy <12:10:00  America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertThat(zonedTime.format(formatter)).isEqualTo("20 10 2023 01:02:03  America/Los_Angeles");
  }

  @Test
  public void formatOf_monthOfYearMixedIn() {
    DateTimeFormatter formatter = formatOf("E, LLL dd yyyy <12:10:00 America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss VV");
  }

  @Test
  public void formatOf_fullWeekdayAndMonthNamePlaceholder() {
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    DateTimeFormatter formatter = formatOf("<Tuesday>, <May> dd yyyy <12:10:00> <+08:00> <America/New_York>");
    assertEquivalent(formatter, zonedTime, "EEEE, LLLL dd yyyy HH:mm:ss ZZZZZ VV");
  }

  @Test
  public void formatOf_12HourFormat() {
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    DateTimeFormatter formatter = formatOf("dd MM yyyy <AD> hh:mm <PM> <+08:00>");
    assertThat(zonedTime.format(formatter)).isEqualTo("20 10 2023 AD 01:02 AM -07:00");
  }

  @Test
  public void formatOf_zoneNameNotRetranslated() {
    DateTimeFormatter formatter = formatOf("<Mon>, <Jan> dd yyyy <12:10:00> VV");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss VV");
  }

  @Test
  public void formatOf_zoneOffsetNotRetranslated() {
    DateTimeFormatter formatter = formatOf("E, LLL dd yyyy <12:10:00> zzzz");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss zzzz");
  }

  @Test
  public void formatOf_monthOfYearMixedIn_withDayOfWeek() {
    DateTimeFormatter formatter = formatOf("E, LLL dd yyyy <12:10:00> <America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss VV");
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void localTimeExamples(
      @TestParameter({
            "00:00",
            "12:00",
            "00:00:00",
            "00:00:00.000000001",
            "00:01:02.3",
            "00:01:02.34",
            "23:59:59.999999999"
          })
          String example) {
    DateTimeFormatter formatter = DateTimeFormats.formatOf(example);
    LocalTime time = LocalTime.parse(example, formatter);
    assertWithMessage("Using format %s", formatter).that(time.format(formatter)).isEqualTo(example);
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void allZerosLocalTime(
      @TestParameter({
            "00:00:00",
            "00:00:00.0",
            "00:00:00.00",
            "00:00:00.000",
            "00:00:00.0000",
            "00:00:00.00000",
            "00:00:00.000000",
            "00:00:00.0000000",
            "00:00:00.00000000",
            "00:00:00.000000000",
          })
          String example) {
    DateTimeFormatter formatter = DateTimeFormats.formatOf(example);
    LocalTime time = LocalTime.parse(example, formatter);
    assertWithMessage("Using format %s", formatter).that(time.format(formatter)).isEqualTo("00:00:00");
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void localDateExamplesFromDifferentFormatters(
      @TestParameter({"ISO_LOCAL_DATE", "yyyy/MM/dd"})
          String formatterName,
      @TestParameter({"2020-01-01", "1979-01-01", "2035-12-31"}) String date)
      throws Exception {
    LocalDate day = LocalDate.parse(date);
    String example = day.format(getFormatterByName(formatterName));
    assertThat(LocalDate.parse(example, DateTimeFormats.formatOf(example))).isEqualTo(day);
  }

  @Test
  public void localDateWithWeekdayExamples() {
    ZonedDateTime date = DateTimeFormats.parseZonedDateTime("Mon, 2007-12-31 00:00:00 America/New_York");
    assertThat(date.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
  }

  @Test
  public void zonedDateTimeExamplesFromDifferentFormatters(
      @TestParameter({
            "ISO_OFFSET_DATE_TIME",
            "ISO_DATE_TIME",
            "ISO_ZONED_DATE_TIME",
            "RFC_1123_DATE_TIME",
            "yyyy/MM/dd HH:mm:ss.SSSSSSX",
            "yyyy/MM/dd HH:mm:ss.SSSSSSx",
            "yyyy/MM/dd HH:mm:ssZ",
            "yyyy/MM/dd HH:mm:ssZ",
            "yyyy/MM/dd HH:mm:ss.nnnZZ",
            "yyyy-MM-dd HH:mm:ss.nnnZZZ",
            "yyyy-MM-dd HH:mm:ssZZZZZ",
            "yyyy-MM-dd HH:mm:ssz",
            "yyyy-MM-dd HH:mm:sszz",
            "yyyy-MM-dd HH:mm:sszzz",
            "yyyy-MM-d HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ssZZZZZ",
            "yyyy-MM-dd HH:mm:ss VV",
          })
          String formatterName,
      @TestParameter({
            "2020-01-01T00:00:00+08:00",
            "1979-01-01T00:00:00+01:00",
            "2035-12-31T00:00:01-12:00"
          })
          String datetime)
      throws Exception {
    ZonedDateTime zonedTime = OffsetDateTime.parse(datetime).toZonedDateTime();
    String example = zonedTime.format(getFormatterByName(formatterName));
    assertThat(DateTimeFormats.parseZonedDateTime(example)).isEqualTo(zonedTime);
  }

  @Test
  public void withZoneIdExamplesFromDifferentFormatters(
      @TestParameter({
            "ISO_OFFSET_DATE_TIME",
            "ISO_DATE_TIME",
            "ISO_ZONED_DATE_TIME",
            "RFC_1123_DATE_TIME",
            "yyyy/MM/dd HH:mm:ssa VV",
            "yyyy/MM/dd HH:mm:ss VV",
            "yyyy/MM/dd HH:mm:ss.nnn VV",
            "yyyy/MM/dd HH:mm:ss.nnn VV",
            "yyyy/MM/dd HH:mm:ss.SSSSSS VV",
            "yyyy-MM-dd HH:mm:ss.SSSSSS VV",
            "yyyy/MM/dd HH:mm:ss.SSSSSSx",
            "yyyy/MM/dd HH:mm:ss.SSSSSSX",
            "yyyy/MM/dd HH:mm:ssZ",
            "yyyy/MM/dd HH:mm:ssZZ",
            "yyyy/MM/dd HH:mm:ssZZZ",
            "yyyy/MM/dd HH:mm:ssZZZZZ",
            "yyyy/MM/dd HH:mm:ssz",
            "yyyy/MM/dd HH:mm:sszz",
            "yyyy/MM/dd HH:mm:sszzz",
            "yyyy/MM/dd HH:mm:ssz",
          })
          String formatterName,
      @TestParameter({
            "2020-01-01T00:00:01-07:00[America/New_York]",
            "1979-01-01T00:00:00+01:00[Europe/Paris]",
          })
          String datetime)
      throws Exception {
    ZonedDateTime zonedTime = ZonedDateTime.parse(datetime, DateTimeFormatter.ISO_DATE_TIME);
    String example = zonedTime.format(getFormatterByName(formatterName));
    assertThat( DateTimeFormats.parseZonedDateTime(example).withFixedOffsetZone())
        .isEqualTo(zonedTime.withFixedOffsetZone());
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void zoneIdRetainedExamples(
      @TestParameter({
            "ISO_DATE_TIME",
            "ISO_ZONED_DATE_TIME",
            "yyyy/MM/dd HH:mm:ss VV",
            "yyyy/MM/dd HH:mm:ss VV",
            "yyyy/MM/dd HH:mm:ss.nnn VV",
            "yyyy/MM/dd HH:mm:ss.nnn VV",
            "yyyy/MM/dd HH:mm:ss.SSSSSS VV",
            "yyyy-MM-dd HH:mm:ss.SSSSSS VV",
            "yyyy-MM-dd HH:mm:ss.SSSSSS z",
            "yyyy-MM-dd HH:mm:ss.SSSSSS zz",
            "yyyy-MM-dd HH:mm:ss.SSSSSS zzz",
          })
          String formatterName,
      @TestParameter({
            "2020-01-01T00:00:01-07:00[America/New_York]",
            "1979-01-01T00:00:00+01:00[Europe/Paris]",
          })
          String datetime)
      throws Exception {
    ZonedDateTime zonedTime = ZonedDateTime.parse(datetime, DateTimeFormatter.ISO_DATE_TIME);
    String example = zonedTime.format(getFormatterByName(formatterName));
    assertThat(ZonedDateTime.parse(example, DateTimeFormats.formatOf(example)))
        .isEqualTo(zonedTime);
  }

  @Test
  public void zonedDateTimeWithNanosExamples(
      @TestParameter({
            "ISO_OFFSET_DATE_TIME",
            "ISO_DATE_TIME",
            "ISO_ZONED_DATE_TIME",
            "yyyy/MM/dd HH:mm:ss.SSSSSSX",
            "yyyy/MM/dd HH:mm:ss.SSSSSSVV",
            "yyyy/MM/dd HH:mm:ss.SSSSSSZ",
            "yyyy/MM/dd HH:mm:ss.SSSSSSZ",
            "yyyy/MM/dd HH:mm:ss.SSSSSSVV",
            "yyyy/MM/dd HH:mm:ss.SSSSSSz",
            "yyyy/MM/dd HH:mm:ss.SSSSSSzz",
            "yyyy/MM/dd HH:mm:ss.SSSSSSzzz",
            "yyyy/MM/dd HH:mm:ss.SSSSSSzzzz",
            "yyyy/MM/dd HH:mm:ss.SSSSSSSSSVV",
          })
          String formatterName,
      @TestParameter({
            "2020-01-01T00:00:00.123+08:00",
            "1979-01-01T00:00:00.1+01:00",
            "2035-12-31T00:00:01.123456-12:00"
          })
          String datetime)
      throws Exception {
    ZonedDateTime zonedTime = OffsetDateTime.parse(datetime).toZonedDateTime();
    String example = zonedTime.format(getFormatterByName(formatterName));
    assertThat(DateTimeFormats.parseZonedDateTime(example)).isEqualTo(zonedTime);
  }

  @Test
  public void offsetDateTimeWithNanosExamples(
      @TestParameter({
            "ISO_OFFSET_DATE_TIME",
            "ISO_DATE_TIME",
            "ISO_ZONED_DATE_TIME",
          })
          String formatterName,
      @TestParameter({
            "2020-01-01T00:00:00.123+08:00",
            "1979-01-01T00:00:00.1+01:00",
            "2035-12-31T00:00:01.123456-12:00"
          })
          String datetime)
      throws Exception {
    OffsetDateTime dateTime = DateTimeFormats.parseOffsetDateTime(datetime);
    String example = dateTime.format(getFormatterByName(formatterName));
    assertThat(DateTimeFormats.parseOffsetDateTime(example)).isEqualTo(dateTime);
  }

  @Test
  public void parseToInstant_fromDateTimeString(
      @TestParameter({
            "ISO_OFFSET_DATE_TIME",
            "ISO_DATE_TIME",
            "ISO_ZONED_DATE_TIME",
            "yyyy/MM/dd HH:mm:ss.SSSSSSX",
            "yyyy/MM/dd HH:mm:ss.SSSSSSVV",
            "yyyy/MM/dd HH:mm:ss.SSSSSSz",
            "yyyy-MM-dd HH:mm:ss.SSSSSSzz",
          })
          String formatterName,
      @TestParameter({
            "2020-01-01T00:00:00.123+08:00",
            "1979-01-01T00:00:00.1+01:00",
            "2035-12-31T00:00:01.123456-12:00"
          })
          String datetime)
      throws Exception {
    ZonedDateTime dateTime = DateTimeFormats.parseZonedDateTime(datetime);
    String example = dateTime.format(getFormatterByName(formatterName));
    assertThat(DateTimeFormats.parseToInstant(example)).isEqualTo(dateTime.toInstant());
  }

  @Test
  public void parseToInstant_fromInstantString(
      @TestParameter({
            "2020-01-01T00:00:00.123+08:00",
            "1979-01-01T00:00:00.1+01:00",
            "2035-12-31T00:00:01.123456-12:00"
          })
          String datetime)
      throws Exception {
    ZonedDateTime dateTime = DateTimeFormats.parseZonedDateTime(datetime);
    assertThat(DateTimeFormats.parseToInstant(dateTime.toInstant().toString()))
        .isEqualTo(dateTime.toInstant());
  }

  @Test
  public void parseOffsetDateTime_nonStandardFormat()
      throws Exception {
    assertThat(DateTimeFormats.parseOffsetDateTime("2020-01-01T00:00:00.123  +08:00"))
        .isEqualTo(OffsetDateTime.parse("2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseOffsetDateTime("2020-01-01 00:00:00.123  +08:00"))
        .isEqualTo(OffsetDateTime.parse("2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
  }

  @Test
  public void parseOffsetDateTime_invalid()
      throws Exception {
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseOffsetDateTime("2020-01-01T00:00:00.123 bad +08:00"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseOffsetDateTime("2020-01-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseOffsetDateTime("2020/01/01"));
  }

  @Test
  public void parseZonedDateTime_nonStandardFormat()
      throws Exception {
    assertThat(DateTimeFormats.parseZonedDateTime("2020-01-01T00:00:00.123  +08:00"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("2020-01-01 00:00:00.123  +08:00"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("2020/01/01T00:00, America/Los_Angeles"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T00:00:00-08:00[America/Los_Angeles]", DateTimeFormatter.ISO_ZONED_DATE_TIME));
  }

  @Test
  public void parseZonedDateTime_invalid()
      throws Exception {
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseZonedDateTime("2020-01-01T00:00:00.123 bad +08:00"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseZonedDateTime("2020-01-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseZonedDateTime("2020/01/02"));
  }

  @Test
  public void parseZonedDateTime_unknownZoneName() {
    assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2020-01-01T12:00:00 China/Beijing"));
  }

  @Test
  public void parseZonedDateTime_knownZoneName() {
    ZonedDateTime dateTime = DateTimeFormats.parseZonedDateTime("2020-01-01T12:00:00 Asia/Shanghai");
    assertThat(dateTime.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
  }

  @Test
  public void parseToInstant_nonStandardFormat()
      throws Exception {
    assertThat(DateTimeFormats.parseToInstant("2020-01-01T00:00:00.123  +08:00"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
    assertThat(DateTimeFormats.parseToInstant("2020-01-01 00:00:00.123  +08:00"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant());
    assertThat(DateTimeFormats.parseToInstant("2020/01/01T00:00, America/Los_Angeles"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T00:00:00-08:00[America/Los_Angeles]", DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant());
  }

  @Test
  public void parseToInstant_invalid()
      throws Exception {
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseToInstant("2020-01-01T00:00:00.123 bad +08:00"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseToInstant("2020-01-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseToInstant("2020/01/01"));
  }

  @Test
  public void parseLocalDate_basicIsoDate() {
    assertThat(DateTimeFormats.parseLocalDate("20211020")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("20211001")).isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("20210101")).isEqualTo(LocalDate.of(2021, 1, 1));
  }

  @Test
  public void parseLocalDate_isoDate() {
    assertThat(DateTimeFormats.parseLocalDate("2021-10-20")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("2021-10-01")).isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021-01-01")).isEqualTo(LocalDate.of(2021, 1, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021-01-2")).isEqualTo(LocalDate.of(2021, 1, 2));
  }

  @Test
  public void parseLocalDate_euDate_mmddyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("10-30-2021")).isEqualTo(LocalDate.of(2021, 10, 30));
    assertThat(DateTimeFormats.parseLocalDate("1-30-2021")).isEqualTo(LocalDate.of(2021, 1, 30));
    assertThat(DateTimeFormats.parseLocalDate("10/20/2021")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("1/20/2021")).isEqualTo(LocalDate.of(2021, 1, 20));
  }

  @Test
  public void parseLocalDate_euDate_ddmmyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("30-10-2021")).isEqualTo(LocalDate.of(2021, 10, 30));
    assertThat(DateTimeFormats.parseLocalDate("20/10/2021")).isEqualTo(LocalDate.of(2021, 10, 20));
  }

  @Test
  public void parseLocalDate_withMonthName_yyyymmdd() {
    assertThat(DateTimeFormats.parseLocalDate("2021 Oct 20")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("2021 October 1")).isEqualTo(LocalDate.of(2021, 10, 1));
  }

  @Test
  public void parseLocalDate_withMonthName_mmddyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("Oct 20 2021")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("October 1 2021")).isEqualTo(LocalDate.of(2021, 10, 1));
  }

  @Test
  public void parseLocalDate_withMonthName_ddmmyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("20 Oct 2021")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("1 October 2021")).isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("01 October 2021")).isEqualTo(LocalDate.of(2021, 10, 1));
  }

  @Test
  public void parseLocalDate_isoDateWithSlash() {
    assertThat(DateTimeFormats.parseLocalDate("2021/10/20")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("2021/10/01")).isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021/01/01")).isEqualTo(LocalDate.of(2021, 1, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021/01/2")).isEqualTo(LocalDate.of(2021, 1, 2));
  }

  @Test
  public void parseLocalDate_instantHasNoDate() {
    Instant time = OffsetDateTime.of(2024, 4, 1, 10, 05, 30, 0, ZoneOffset.UTC).toInstant();
    assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseLocalDate(time.toString()));
  }

  @Test
  public void parseLocalDate_cannotParseZonedDateTimeStringToLocalDate() {
    ZonedDateTime time = ZonedDateTime.of(2024, 4, 1, 10, 05, 30, 0, ZoneId.of("America/New_York"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate(time.toString()));
  }

  @Test
  public void parseLocalDate_cannotParseOffsetDateTimeStringToLocalDate() {
    OffsetDateTime time = OffsetDateTime.of(2024, 4, 1, 10, 05, 30, 0, ZoneOffset.of("-08:30"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate(time.toString()));
  }

  @Test
  public void parseLocalDate_incorrectDate() {
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("20213001"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("2021/30/01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("2021-30-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("01-01-2021"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("01/01/2021"));
  }

  @Test
  public void tIsRecognizedAndEscaped() {
    assertThat(
            ZonedDateTime.parse(
                "2023-11-06T00:10 Europe/Paris", formatOf("2022-10-05T00:10 America/New_York")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 11, 6, 0, 10, 0, 0), ZoneId.of("Europe/Paris")));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void offsetTimeExamples(
      @TestParameter({"00:00:00+18:00", "12:00-08:00", "23:59:59.999999999-18:00"})
          String example) {
    DateTimeFormatter formatter = DateTimeFormats.formatOf(example);
    OffsetTime time = OffsetTime.parse(example, formatter);
    assertThat(time.format(formatter)).isEqualTo(example);
  }

  @Test
  public void timeZoneMixedIn_zeroOffset() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<Z>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30Z", formatter);
    assertThat(dateTime)
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.UTC));
  }

  @Test
  public void timeZoneMixedIn_offsetWithoutColon() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("MM dd yyyy HH:mm:ss<+0100>");
    ZonedDateTime dateTime = ZonedDateTime.parse("01 10 2023 10:20:30-0800", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test
  public void timeZoneMixedIn_hourOffset() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<+01>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-08", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test
  public void timeZoneMixedIn_offsetWithColon() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<-01:00>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-08:00", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test
  public void timeZoneMixedIn_zoneNameWithEuropeDateStyle() {
    DateTimeFormatter formatter =
        DateTimeFormats.formatOf("dd MM yyyy HH:mm:ss.SSS <America/New_York>");
    ZonedDateTime dateTime = ZonedDateTime.parse("30 10 2023 10:20:30.123 Europe/Paris", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 10, 30, 10, 20, 30, 123000000), ZoneId.of("Europe/Paris")));
  }

  @Test
  public void timeZoneMixedIn_offsetWithAmericanDateStyle() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<+01:00>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-07:00", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("-07:00")));
  }

  @Test
  public void timeZoneMixedIn_twoLetterZoneNameAbbreviation() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<PT>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30PT", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("America/Los_Angeles")));
  }

  @Ignore("fails under Java 22")
  @Test
  public void timeZoneMixedIn_fourLetterZoneNameAbbreviation() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<CAST>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30CAST", formatter);
    assertThat(dateTime.toLocalDateTime())
        .isEqualTo(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0));
  }

  @Test
  public void timeZoneMixedIn_abbreviatedZoneName() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("MM dd yyyy HH:mm:ss<GMT>");
    ZonedDateTime dateTime = ZonedDateTime.parse("01 10 2023 10:20:30PST", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("America/Los_Angeles")));
  }

  @Test
  public void timeZoneMixedIn_unsupportedZoneSpec() {
    assertThrows(
        DateTimeException.class, () -> DateTimeFormats.inferDateTimePattern("1234"));
    assertThrows(
        DateTimeException.class, () -> DateTimeFormats.inferDateTimePattern("12:34:5"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void emptyExample_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf(""));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlySpaces_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("  "));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyPunctuations_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("/"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyNumbers_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("1234"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyWords_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("yyyy"));
    assertThrows(DateTimeException.class, () -> formatOf("America"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void typoInExample() {
    assertThrows(
        DateTimeException.class, () -> formatOf("<Febuary Wedenesday>, <2021/20/30>"));
  }

  @Test public void chineseDates() {
    assertLocalDate("2020年08月10日", "yyyy年MM月dd日")
        .isEqualTo(LocalDate.parse("2020年08月10日", DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
    assertLocalDate("2020年08月1日", "yyyy年MM月d日")
       .isEqualTo(LocalDate.parse("2020年08月1日", DateTimeFormatter.ofPattern("yyyy年MM月d日")));
    assertLocalDate("2020年8月10日", "yyyy年M月dd日")
         .isEqualTo(LocalDate.parse("2020年8月10日", DateTimeFormatter.ofPattern("yyyy年M月dd日")));
    assertLocalDate("2020年8月1日", "yyyy年M月d日")
        .isEqualTo(LocalDate.parse("2020年8月1日", DateTimeFormatter.ofPattern("yyyy年M月d日")));
    assertLocalDate("8月1日2020年", "M月d日yyyy年")
        .isEqualTo(LocalDate.parse("8月1日2020年", DateTimeFormatter.ofPattern("M月d日yyyy年")));
  }

  @Test public void chineseZonedDateTimeWithWeekdays() {
    assertLocalDate("2025年8月13日 星期三", "yyyy年M月dd日 EEEE", Locale.CHINA)
        .isEqualTo(LocalDate.parse("2025年8月13日 星期三", DateTimeFormatter.ofPattern("yyyy年M月dd日 EEEE").withLocale(Locale.CHINA)));
    assertLocalDate("2025年8月10日 周日", "yyyy年M月dd日 EEE", Locale.CHINA)
        .isEqualTo(LocalDate.parse("2025年8月10日 周日", DateTimeFormatter.ofPattern("yyyy年M月dd日 EEE").withLocale(Locale.CHINA)));
  }

  @Test public void chineseLocalDateTimes() {
    assertLocalDateTime("2020年08月10日 15点19分", "yyyy年MM月dd日 HH点mm分")
        .isEqualTo(LocalDateTime.parse("2020年08月10日 15点19分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH点mm分")));
    assertLocalDateTime("2020年08月10日 15点19分01秒", "yyyy年MM月dd日 HH点mm分ss秒")
       .isEqualTo(LocalDateTime.parse("2020年08月10日 15点19分01秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH点mm分ss秒")));
    assertLocalDateTime("2020年08月10日 5点9分", "yyyy年MM月dd日 H点m分")
        .isEqualTo(LocalDateTime.parse("2020年08月10日 5点9分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H点m分")));
    assertLocalDateTime("2020年08月10日 5点9分8秒", "yyyy年MM月dd日 H点m分s秒")
        .isEqualTo(LocalDateTime.parse("2020年08月10日 5点9分8秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H点m分s秒")));
    assertLocalDateTime("2020年08月10日 15时19分", "yyyy年MM月dd日 HH时mm分")
        .isEqualTo(LocalDateTime.parse("2020年08月10日 15时19分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分")));
    assertLocalDateTime("2020年08月10日 15时19分01秒", "yyyy年MM月dd日 HH时mm分ss秒")
       .isEqualTo(LocalDateTime.parse("2020年08月10日 15时19分01秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒")));
    assertLocalDateTime("2020年08月10日 5时9分", "yyyy年MM月dd日 H时m分")
        .isEqualTo(LocalDateTime.parse("2020年08月10日 5时9分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H时m分")));
    assertLocalDateTime("2020年08月10日 5时9分8秒", "yyyy年MM月dd日 H时m分s秒")
        .isEqualTo(LocalDateTime.parse("2020年08月10日 5时9分8秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H时m分s秒")));
  }

  @Test public void fuzzTests() {
    assertLocalDate("2005-04-27", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2005-04-27"));
    assertLocalDate("2004-10-27", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2004-10-27"));
    assertLocalDate("1993/07/05", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1993-07-05"));
    assertLocalDate("Tue, 2016-09-20", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2016-09-20"));
    assertLocalDate("2009/09/23", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2009-09-23"));
    assertLocalDate("2027-04-02", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2027-04-02"));
    assertZonedDateTime("1994-08-03 19:32:42 UTC", "yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("1994-08-03T19:32:42+00:00[UTC]"));
    assertLocalDate("Thu, 2011-06-09", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2011-06-09"));
    assertLocalDate("2021-01-11", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2021-01-11"));
    assertZonedDateTime("2000-04-28 19:32 UTC", "yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2000-04-28T19:32:00+00:00[UTC]"));
    assertLocalDate("2018-08-13", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2018-08-13"));
    assertLocalDate("Mon, 2022-07-25", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2022-07-25"));
    assertLocalDate("Mon, 2019-09-09", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2019-09-09"));
    assertLocalDate("2024-08-04", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2024-08-04"));
    assertLocalDate("2027/11/13", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2027-11-13"));
    assertLocalDate("2008-06-03", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2008-06-03"));
    assertLocalDate("Wed, 2014-01-08", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2014-01-08"));
    assertLocalDate("Wed, 2004-03-24", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2004-03-24"));
    assertLocalDate("2023/06/04", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2023-06-04"));
    assertLocalDate("2023-12-18", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2023-12-18"));
    assertLocalDate("1998-05-17", "yyyy-MM-dd").isEqualTo(LocalDate.parse("1998-05-17"));
    assertLocalDate("Fri, 2023-05-05", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2023-05-05"));
    assertLocalDate("Wed, 1991-11-27", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("1991-11-27"));
    assertZonedDateTime("Sat, 2027-06-05 19:32 UTC", "EEE, yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2027-06-05T19:32:00+00:00[UTC]"));
    assertLocalDate("2002/03/04", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2002-03-04"));
    assertZonedDateTime("2003-01-12 19:32 UTC", "yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2003-01-12T19:32:00+00:00[UTC]"));
    assertLocalDate("2020-06-11", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-06-11"));
    assertLocalDate("1991/08/20", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1991-08-20"));
    assertLocalDate("2015-03-23", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2015-03-23"));
    assertLocalDate("2019/01/25", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2019-01-25"));
    assertLocalDate("2005/05/14", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2005-05-14"));
    assertZonedDateTime("Sun, 2011-09-18 19:32:42 UTC", "EEE, yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("2011-09-18T19:32:42+00:00[UTC]"));
    assertLocalDate("1992/05/10", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1992-05-10"));
    assertLocalDate("Fri, 2027-12-03", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2027-12-03"));
    assertLocalDate("Fri, 2020-05-15", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-05-15"));
    assertLocalDate("Sun, 2020-10-25", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-10-25"));
    assertLocalDate("1999/12/15", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1999-12-15"));
    assertZonedDateTime("Thu, 1997-02-06 19:32:42 UTC", "EEE, yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("1997-02-06T19:32:42+00:00[UTC]"));
    assertLocalDate("Thu, 2021-08-05", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2021-08-05"));
    assertLocalDate("Wed, 2007-01-10", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2007-01-10"));
    assertLocalDate("1998/11/17", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1998-11-17"));
    assertLocalDate("2026-08-27", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2026-08-27"));
    assertLocalDate("Tue, 2016-08-02", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2016-08-02"));
    assertLocalDate("1991/07/24", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1991-07-24"));
    assertLocalDate("1994-06-06", "yyyy-MM-dd").isEqualTo(LocalDate.parse("1994-06-06"));
    assertLocalDate("2007/05/06", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2007-05-06"));
    assertLocalDate("2020/07/07", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2020-07-07"));
    assertZonedDateTime("2025-08-06 19:32:42.4 UTC", "yyyy-MM-dd HH:mm:ss.S zzz")
        .isEqualTo(ZonedDateTime.parse("2025-08-06T19:32:42.4+00:00[UTC]"));
    assertLocalDate("Tue, 1992-08-04", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("1992-08-04"));
    assertLocalDate("2014/01/20", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2014-01-20"));
    assertLocalDate("2028/03/09", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2028-03-09"));
    assertZonedDateTime("2003-09-08 19:32 UTC", "yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2003-09-08T19:32:00+00:00[UTC]"));
    assertLocalDate("Sat, 2020-04-11", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-04-11"));
    assertLocalDate("2029-05-03", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2029-05-03"));
    assertLocalDate("2001/10/17", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2001-10-17"));
    assertLocalDate("2029/06/04", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2029-06-04"));
    assertLocalDate("Thu, 2023-03-16", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2023-03-16"));
    assertLocalDate("2022/12/01", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2022-12-01"));
    assertLocalDate("Thu, 2024-08-01", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2024-08-01"));
    assertZonedDateTime("2001-04-25 19:32:42.47 UTC", "yyyy-MM-dd HH:mm:ss.SS zzz")
        .isEqualTo(ZonedDateTime.parse("2001-04-25T19:32:42.47+00:00[UTC]"));
    assertLocalDate("1997/02/28", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1997-02-28"));
    assertLocalDate("2014-01-17", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2014-01-17"));
    assertLocalDate("Thu, 2022-07-14", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2022-07-14"));
    assertLocalDate("2013-05-26", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2013-05-26"));
    assertLocalDate("2026/07/14", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2026-07-14"));
    assertLocalDate("2003-01-11", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2003-01-11"));
    assertZonedDateTime("Mon, 2003-10-20 19:32 UTC", "EEE, yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2003-10-20T19:32:00+00:00[UTC]"));
    assertLocalDate("Sun, 2023-12-03", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2023-12-03"));
    assertLocalDate("2007-12-07", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2007-12-07"));
    assertLocalDate("1993/08/09", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1993-08-09"));
    assertLocalDate("2000-03-09", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2000-03-09"));
    assertLocalDate("2003/08/12", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2003-08-12"));
    assertLocalDate("Sun, 1995-12-03", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("1995-12-03"));
    assertZonedDateTime("Tue, 2014-08-05 19:32:42 UTC", "EEE, yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("2014-08-05T19:32:42+00:00[UTC]"));
    assertLocalDate("1998/05/05", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1998-05-05"));
    assertLocalDate("Tue, 2014-02-04", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2014-02-04"));
    assertLocalDate("2017/06/28", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2017-06-28"));
    assertLocalDate("Fri, 2027-02-12", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2027-02-12"));
    assertLocalDate("Wed, 2007-08-08", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2007-08-08"));
    assertLocalDate("Sat, 2004-06-05", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2004-06-05"));
    assertLocalDate("2009-04-17", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2009-04-17"));
    assertLocalDate("Wed, 2028-12-20", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2028-12-20"));
    assertZonedDateTime("Fri, 1999-01-22 19:32 UTC", "EEE, yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("1999-01-22T19:32:00+00:00[UTC]"));
    assertLocalDate("2010-05-23", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2010-05-23"));
    assertLocalDate("2012/02/18", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2012-02-18"));
    assertLocalDate("2028/05/12", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2028-05-12"));
    assertLocalDate("2016-05-26", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2016-05-26"));
    assertLocalDate("1994/08/27", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1994-08-27"));
    assertLocalDate("1995-08-16", "yyyy-MM-dd").isEqualTo(LocalDate.parse("1995-08-16"));
    assertLocalDate("2019-11-04", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2019-11-04"));
    assertLocalDate("2020-07-01", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-07-01"));
    assertLocalDate("2025/03/23", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2025-03-23"));
    assertLocalDate("Wed, 2013-10-02", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2013-10-02"));
    assertLocalDate("2003/12/14", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2003-12-14"));
    assertLocalDate("2014-06-23", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2014-06-23"));
    assertLocalDate("1992-04-21", "yyyy-MM-dd").isEqualTo(LocalDate.parse("1992-04-21"));
  }

  @Test
  public void fuzzTestsWithZoneAndWeekdays() {
    assertLocalDate("2005-04-27", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2005-04-27"));
    assertLocalDate("2004-10-27", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2004-10-27"));
    assertLocalDate("1993/07/05", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1993-07-05"));
    assertLocalDate("Tue, 2016-09-20", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2016-09-20"));
    assertLocalDate("2009/09/23", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2009-09-23"));
    assertLocalDate("2027-04-02", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2027-04-02"));
    assertZonedDateTime("1994-08-03 19:32:42 UTC", "yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("1994-08-03T19:32:42+00:00[UTC]"));
    assertLocalDate("Thu, 2011-06-09", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2011-06-09"));
    assertLocalDate("2021-01-11", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2021-01-11"));
    assertZonedDateTime("2000-04-28 19:32 UTC", "yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2000-04-28T19:32:00+00:00[UTC]"));
    assertLocalDate("2018-08-13", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2018-08-13"));
    assertLocalDate("Mon, 2022-07-25", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2022-07-25"));
    assertLocalDate("Mon, 2019-09-09", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2019-09-09"));
    assertLocalDate("2024-08-04", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2024-08-04"));
    assertLocalDate("2027/11/13", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2027-11-13"));
    assertLocalDate("2008-06-03", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2008-06-03"));
    assertLocalDate("Wed, 2014-01-08", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2014-01-08"));
    assertLocalDate("Wed, 2004-03-24", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2004-03-24"));
    assertLocalDate("2023/06/04", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2023-06-04"));
    assertZonedDateTime("Thu, 1994-03-24 19:32:42.4 UTC", "EEE, yyyy-MM-dd HH:mm:ss.S zzz")
        .isEqualTo(ZonedDateTime.parse("1994-03-24T19:32:42.4+00:00[UTC]"));
    assertLocalDate("2023-12-18", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2023-12-18"));
    assertLocalDate("1998-05-17", "yyyy-MM-dd").isEqualTo(LocalDate.parse("1998-05-17"));
    assertLocalDate("Fri, 2023-05-05", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2023-05-05"));
    assertLocalDate("Wed, 1991-11-27", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("1991-11-27"));
    assertZonedDateTime("Sat, 2027-06-05 19:32 UTC", "EEE, yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2027-06-05T19:32:00+00:00[UTC]"));
    assertLocalDate("2002/03/04", "yyyy/MM/dd").isEqualTo(LocalDate.parse("2002-03-04"));
    assertZonedDateTime("2003-01-12 19:32 UTC", "yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2003-01-12T19:32:00+00:00[UTC]"));
    assertLocalDate("2020-06-11", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-06-11"));
    assertLocalDate("1991/08/20", "yyyy/MM/dd").isEqualTo(LocalDate.parse("1991-08-20"));
    assertLocalDate("2015-03-23", "yyyy-MM-dd").isEqualTo(LocalDate.parse("2015-03-23"));
    assertZonedDateTime("Sun, 2011-09-18 19:32:42 UTC", "EEE, yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("2011-09-18T19:32:42+00:00[UTC]"));
    assertLocalDate("Fri, 2027-12-03", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2027-12-03"));
    assertLocalDate("Fri, 2020-05-15", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-05-15"));
    assertLocalDate("Sun, 2020-10-25", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-10-25"));
    assertZonedDateTime("Thu, 1997-02-06 19:32:42 UTC", "EEE, yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("1997-02-06T19:32:42+00:00[UTC]"));
    assertLocalDate("Tue, 2016-08-02", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2016-08-02"));
    assertZonedDateTime("2025-08-06 19:32:42.4 UTC", "yyyy-MM-dd HH:mm:ss.S zzz")
        .isEqualTo(ZonedDateTime.parse("2025-08-06T19:32:42.4+00:00[UTC]"));
    assertZonedDateTime("2003-09-08 19:32 UTC", "yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2003-09-08T19:32:00+00:00[UTC]"));
    assertLocalDate("Sat, 2020-04-11", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2020-04-11"));
    assertLocalDate("Thu, 2024-08-01", "EEE, yyyy-MM-dd").isEqualTo(LocalDate.parse("2024-08-01"));
    assertZonedDateTime("2001-04-25 19:32:42.47 UTC", "yyyy-MM-dd HH:mm:ss.SS zzz")
        .isEqualTo(ZonedDateTime.parse("2001-04-25T19:32:42.47+00:00[UTC]"));
    assertZonedDateTime("Mon, 2003-10-20 19:32 UTC", "EEE, yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("2003-10-20T19:32:00+00:00[UTC]"));
    assertZonedDateTime("Tue, 2014-08-05 19:32:42 UTC", "EEE, yyyy-MM-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("2014-08-05T19:32:42+00:00[UTC]"));
    assertZonedDateTime("Fri, 1999-01-22 19:32 UTC", "EEE, yyyy-MM-dd HH:mm zzz")
        .isEqualTo(ZonedDateTime.parse("1999-01-22T19:32:00+00:00[UTC]"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitSecond_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("12:00:1"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousMonthAndDay() {
    assertThrows(DateTimeException.class, () -> formatOf("01/02/03"));
    assertThrows(DateTimeException.class, () -> formatOf("01/02/2003"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void trailingDotAfterSecond_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("2023-01-01T00:00:00."));
  }

  private static ComparableSubject<ZonedDateTime> assertZonedDateTime(
      @CompileTimeConstant String example, String equivalentPattern) {
    String pattern = DateTimeFormats.inferDateTimePattern(example);
    assertThat(pattern).isEqualTo(equivalentPattern);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    ZonedDateTime dateTime = ZonedDateTime.parse(example, formatter);
    assertThat(dateTime.format(formatter)).isEqualTo(example);
    return assertThat(dateTime);
  }

  private static ComparableSubject<LocalDateTime> assertLocalDateTime(
      @CompileTimeConstant String example, String equivalentPattern) {
    String pattern = DateTimeFormats.inferDateTimePattern(example);
    assertThat(pattern).isEqualTo(equivalentPattern);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    LocalDateTime dateTime = LocalDateTime.parse(example, formatter);
    assertThat(dateTime.format(formatter)).isEqualTo(example);
    return assertThat(dateTime);
  }

  private static ComparableSubject<LocalDate> assertLocalDate(
      @CompileTimeConstant String example, String equivalentPattern) {
    String pattern = DateTimeFormats.inferDateTimePattern(example);
    assertThat(pattern).isEqualTo(equivalentPattern);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    LocalDate date = LocalDate.parse(example, formatter);
    assertThat(date.format(formatter)).isEqualTo(example);
    return assertThat(date);
  }

  private static ComparableSubject<LocalDate> assertLocalDate(
      @CompileTimeConstant String example, String equivalentPattern, Locale locale) {
    String pattern = DateTimeFormats.inferDateTimePattern(example);
    assertThat(pattern).isEqualTo(equivalentPattern);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern).withLocale(locale);
    LocalDate date = LocalDate.parse(example, formatter);
    assertThat(date.format(formatter)).isEqualTo(example);
    return assertThat(date);
  }

  private static ComparableSubject<LocalTime> assertLocalTime(
      @CompileTimeConstant String example, String equivalentPattern) {
    String pattern = DateTimeFormats.inferDateTimePattern(example);
    assertThat(pattern).isEqualTo(equivalentPattern);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    LocalTime time = LocalTime.parse(example, formatter);
    assertThat(time.format(formatter)).isEqualTo(example);
    return assertThat(time);
  }

  private static DateTimeFormatter getFormatterByName(String formatterName) throws Exception {
    try {
      return (DateTimeFormatter) DateTimeFormatter.class.getDeclaredField(formatterName).get(null);
    } catch (NoSuchFieldException e) {
      return DateTimeFormatter.ofPattern(formatterName);
    }
  }

  private static void assertEquivalent(DateTimeFormatter formatter, ZonedDateTime time, String pattern) {
    assertThat(time.format(formatter)).isEqualTo(time.format(DateTimeFormatter.ofPattern(pattern)));
    assertThat(ZonedDateTime.parse(time.format(DateTimeFormatter.ofPattern(pattern)), formatter))
        .isEqualTo(time);
  }
}
