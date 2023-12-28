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
import static com.google.mu.time.DateTimeFormats.formatOf;
import static org.junit.Assert.assertThrows;

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
  public void singleDigitHourWithoutAmPm_throws() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("1"));
  }

  @Test
  public void singleDigitHourWithAmPm() {
    assertLocalTime("1AM", "ha").isEqualTo(LocalTime.of(1, 0, 0));
    assertLocalTime("2 PM", "h a").isEqualTo(LocalTime.of(14, 0, 0));
  }

  @Test
  public void singleDigitHourMinuteWithoutAmPm_throws() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("1:10"));
  }

  @Test
  public void singleDigitHourMinuteWithAmPm() {
    assertLocalTime("1:10AM", "h:mma").isEqualTo(LocalTime.of(1, 10, 0));
    assertLocalTime("2:05 PM", "h:mm a").isEqualTo(LocalTime.of(14, 5, 0));
  }

  @Test
  public void singleDigitHourMinuteSecondWithoutAmPm_throws() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("1:10:00"));
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
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void monthOfYear_notSupported() {
    assertThrows(
        IllegalArgumentException.class, () -> formatOf("Dec 31, 2023 12:00:00 America/New_York"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void mmddyyyy_notSupported() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("10/20/2023 10:10:10"));
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
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample(example);
    LocalTime time = LocalTime.parse(example, formatter);
    assertThat(time.format(formatter)).isEqualTo(example);
  }

  @Test
  public void localDateExamplesFromDifferentFormatters(
      @TestParameter({"ISO_LOCAL_DATE", "yyyy/MM/dd"})
          String formatterName,
      @TestParameter({"2020-01-01", "1979-01-01", "2035-12-31"}) String date)
      throws Exception {
    LocalDate day = LocalDate.parse(date);
    String example = day.format(getFormatterByName(formatterName));
    assertThat(LocalDate.parse(example, DateTimeFormats.inferFromExample(example))).isEqualTo(day);
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
    assertThat(ZonedDateTime.parse(example, DateTimeFormats.inferFromExample(example)))
        .isEqualTo(zonedTime);
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
    assertThat(
            ZonedDateTime.parse(example, DateTimeFormats.inferFromExample(example))
                .withFixedOffsetZone())
        .isEqualTo(zonedTime.withFixedOffsetZone());
  }

  @Test
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
    assertThat(ZonedDateTime.parse(example, DateTimeFormats.inferFromExample(example)))
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
    assertThat(ZonedDateTime.parse(example, DateTimeFormats.inferFromExample(example)))
        .isEqualTo(zonedTime);
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
  public void offsetTimeExamples(
      @TestParameter({"00:00:00+18:00", "12:00-08:00", "23:59:59.999999999-18:00"})
          String example) {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample(example);
    OffsetTime time = OffsetTime.parse(example, formatter);
    assertThat(time.format(formatter)).isEqualTo(example);
  }

  @Test
  public void timeZoneMixedIn_zeroOffset() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("M dd yyyy HH:mm:ss<Z>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30Z", formatter);
    assertThat(dateTime)
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.UTC));
  }

  @Test
  public void timeZoneMixedIn_offsetWithoutColon() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("MM dd yyyy HH:mm:ss<+0100>");
    ZonedDateTime dateTime = ZonedDateTime.parse("01 10 2023 10:20:30-0800", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test
  public void timeZoneMixedIn_hourOffset() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("M dd yyyy HH:mm:ss<+01>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-08", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test
  public void timeZoneMixedIn_offsetWithColon() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("M dd yyyy HH:mm:ss<-01:00>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-08:00", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test
  public void timeZoneMixedIn_zoneNameWithEuropeDateStyle() {
    DateTimeFormatter formatter =
        DateTimeFormats.inferFromExample("dd MM yyyy HH:mm:ss.SSS <America/New_York>");
    ZonedDateTime dateTime = ZonedDateTime.parse("30 10 2023 10:20:30.123 Europe/Paris", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 10, 30, 10, 20, 30, 123000000), ZoneId.of("Europe/Paris")));
  }

  @Test
  public void timeZoneMixedIn_offsetWithAmericanDateStyle() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("M dd yyyy HH:mm:ss<+01:00>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-07:00", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("-07:00")));
  }

  @Test
  public void timeZoneMixedIn_twoLetterZoneNameAbbreviation() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("M dd yyyy HH:mm:ss<PT>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30PT", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("America/Los_Angeles")));
  }

  @Test
  public void timeZoneMixedIn_fourLetterZoneNameAbbreviation() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("M dd yyyy HH:mm:ss<CAST>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30CAST", formatter);
    assertThat(dateTime.toLocalDateTime())
        .isEqualTo(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0));
  }

  @Test
  public void timeZoneMixedIn_abbreviatedZoneName() {
    DateTimeFormatter formatter = DateTimeFormats.inferFromExample("MM dd yyyy HH:mm:ss<GMT>");
    ZonedDateTime dateTime = ZonedDateTime.parse("01 10 2023 10:20:30PST", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("America/Los_Angeles")));
  }

  @Test
  public void timeZoneMixedIn_unsupportedZoneSpec() {
    assertThrows(
        IllegalArgumentException.class, () -> DateTimeFormats.inferDateTimePattern("1234"));
    assertThrows(
        IllegalArgumentException.class, () -> DateTimeFormats.inferDateTimePattern("12:34:5"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void emptyExample_disallowed() {
    assertThrows(IllegalArgumentException.class, () -> formatOf(""));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlySpaces_disallowed() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("  "));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyPunctuations_disallowed() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("/"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyNumbers_disallowed() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("1234"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyWords_disallowed() {
    assertThrows(IllegalArgumentException.class, () -> formatOf("yyyy"));
    assertThrows(IllegalArgumentException.class, () -> formatOf("America"));
  }

  @Test
  @SuppressWarnings("DateTimeExampleStringCheck")
  public void typoInExample() {
    assertThrows(
        IllegalArgumentException.class, () -> formatOf("<Febuary Wedenesday>, <2021/20/30>"));
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
