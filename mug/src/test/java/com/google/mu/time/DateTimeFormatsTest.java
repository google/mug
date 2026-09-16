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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.mu.time.DateTimeFormats.formatOf;
import static org.junit.Assert.assertThrows;

import com.google.common.testing.TearDownStack;
import com.google.common.truth.ComparableSubject;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
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
import java.util.List;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class DateTimeFormatsTest {
  @TestParameter(valuesProvider = LocaleProvider.class)
  private Locale locale;

  private final TearDownStack tearDowns = new TearDownStack();

  @Before public void setUpEnvironment() {
    overrideLocale(locale);
  }

  @After public void restoreEnvironment() {
    tearDowns.runTearDown();
  }

  @Test public void dateOnlyExamples() {
    assertLocalDate("2023-10-20", "yyyy-MM-dd").isEqualTo(LocalDate.of(2023, 10, 20));
    assertLocalDate("1986/01/01", "yyyy/MM/dd").isEqualTo(LocalDate.of(1986, 1, 1));
  }

  @Test public void singleDigitMonth_hyphenDate() {
    assertLocalDate("2019-3-21", "yyyy-M-dd").isEqualTo(LocalDate.of(2019, 3, 21));
  }

  @Test public void singleDigitMonth_slashDate() {
    assertLocalDate("2017/2/01", "yyyy/M/dd").isEqualTo(LocalDate.of(2017, 2, 1));
  }

  @Test public void singleDigitMonthAndDay_hyphenDate() {
    assertLocalDate("2019-3-5", "yyyy-M-d").isEqualTo(LocalDate.of(2019, 3, 5));
  }

  @Test public void singleDigitMonthAndDay_slashDate() {
    assertLocalDate("2017/2/1", "yyyy/M/d").isEqualTo(LocalDate.of(2017, 2, 1));
  }

  @Test public void dotFormat_dMyyyy() {
    assertLocalDate("1.2.2011", "d.M.yyyy").isEqualTo(LocalDate.of(2011, 2, 1));
  }

  @Test public void dotFormat_dMyyyy_withSpaces() {
    assertLocalDate("1. 2. 2011", "d. M. yyyy").isEqualTo(LocalDate.of(2011, 2, 1));
  }

  @Test public void dotFormat_ddMyyyy() {
    assertLocalDate("10.2.2011", "dd.M.yyyy").isEqualTo(LocalDate.of(2011, 2, 10));
  }

  @Test public void dotFormat_ddMyyyy_withSpaces() {
    assertLocalDate("10. 2. 2011", "dd. M. yyyy").isEqualTo(LocalDate.of(2011, 2, 10));
  }

  @Test public void dotFormat_dMMyyyy() {
    assertLocalDate("1.12.2011", "d.MM.yyyy").isEqualTo(LocalDate.of(2011, 12, 1));
  }

  @Test public void dotFormat_dMMyyyy_withSpaces() {
    assertLocalDate("1. 12. 2011", "d. MM. yyyy").isEqualTo(LocalDate.of(2011, 12, 1));
  }

  @Test public void dotFormat_ddMMyyyy() {
    assertLocalDate("11.12.2011", "dd.MM.yyyy").isEqualTo(LocalDate.of(2011, 12, 11));
  }

  @Test public void dotFormat_ddMMyyyy_withSpaces() {
    assertLocalDate("11. 12. 2011", "dd. MM. yyyy").isEqualTo(LocalDate.of(2011, 12, 11));
  }

  @Test public void dotFormat_yyyyMMdd() {
    assertLocalDate("2011.11.12", "yyyy.MM.dd").isEqualTo(LocalDate.of(2011, 11, 12));
  }

  @Test public void dotFormat_yyyyMMdd_withSpaces() {
    assertLocalDate("2011. 11. 12", "yyyy. MM. dd").isEqualTo(LocalDate.of(2011, 11, 12));
  }

  @Test public void dotFormat_yyyyMdd() {
    assertLocalDate("2011.1.12", "yyyy.M.dd").isEqualTo(LocalDate.of(2011, 1, 12));
  }

  @Test public void dotFormat_yyyyMdd_withSpaces() {
    assertLocalDate("2011. 1. 12", "yyyy. M. dd").isEqualTo(LocalDate.of(2011, 1, 12));
  }

  @Test public void dotFormat_yyyyMMd() {
    assertLocalDate("2011.11.2", "yyyy.MM.d").isEqualTo(LocalDate.of(2011, 11, 2));
  }

  @Test public void dotFormat_yyyyMMd_withSpaces() {
    assertLocalDate("2011. 11. 2", "yyyy. MM. d").isEqualTo(LocalDate.of(2011, 11, 2));
  }

  @Test public void dotFormat_yyyyMd() {
    assertLocalDate("2011.1.1", "yyyy.M.d").isEqualTo(LocalDate.of(2011, 1, 1));
  }

  @Test public void dotFormat_yyyyMd_withSpaces() {
    assertLocalDate("2011. 1. 1", "yyyy. M. d").isEqualTo(LocalDate.of(2011, 1, 1));
  }

  @Test public void timeOnlyExamples() {
    assertLocalTime("10:30:00", "HH:mm:ss").isEqualTo(LocalTime.of(10, 30, 0));
    assertLocalTime("10:30", "HH:mm").isEqualTo(LocalTime.of(10, 30, 0));
    assertLocalTime("10:30:00.001234", "HH:mm:ss.SSSSSS")
        .isEqualTo(LocalTime.of(10, 30, 0, 1234000));
    assertLocalTime("10:30:00.123456789", "HH:mm:ss.SSSSSSSSS")
        .isEqualTo(LocalTime.of(10, 30, 0, 123456789));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourWithoutAmPm_throws() {
    assertThrows(DateTimeException.class, () -> formatOf("1"));
  }

  @Test public void singleDigitHour_upperCaseAmMarker() {
    assertLocalTime("1AM", "ha").isEqualTo(LocalTime.of(1, 0, 0));
  }

  @Test public void singleDigitHour_upperCasePmMarkerAfterSpace() {
    assertLocalTime("2 PM", "h a").isEqualTo(LocalTime.of(14, 0, 0));
  }

  // The library pins Locale.ENGLISH for AM/PM, and the "a" specifier is case sensitive, so a
  // lower case marker is rejected in every locale rather than resolving differently per machine.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHour_lowerCaseAmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("1am"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 1am (ha)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHour_lowerCasePmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("2pm"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 2pm (ha)");
  }

  // "a.m." is a registered marker spelling, but no hour-plus-marker shape is registered for a
  // single digit hour, so the example is not covered at all.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHour_dottedAmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("1a.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 1a.m.");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHour_dottedPmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("2p.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 2p.m.");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteWithoutAmPm_throws() {
    assertThrows(DateTimeException.class, () -> formatOf("1:10"));
  }

  @Test public void singleDigitHourMinute_upperCaseAmMarker() {
    assertLocalTime("1:10AM", "h:mma").isEqualTo(LocalTime.of(1, 10, 0));
  }

  @Test public void singleDigitHourMinute_upperCasePmMarkerAfterSpace() {
    assertLocalTime("2:05 PM", "h:mm a").isEqualTo(LocalTime.of(14, 5, 0));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinute_lowerCaseAmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("1:10 am"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 1:10 am (h:mm a)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinute_lowerCasePmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("2:05pm"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 2:05pm (h:mma)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinute_dottedAmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("1:10 a.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 1:10 a.m.");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinute_dottedPmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("2:05p.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 2:05p.m.");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteSecondWithoutAmPm_throws() {
    assertThrows(DateTimeException.class, () -> formatOf("1:10:00"));
  }

  @Test public void singleDigitHourMinuteSecond_upperCaseAmMarker() {
    assertLocalTime("1:10:30AM", "h:mm:ssa").isEqualTo(LocalTime.of(1, 10, 30));
  }

  @Test public void singleDigitHourMinuteSecond_upperCasePmMarkerAfterSpace() {
    assertLocalTime("2:05:00 PM", "h:mm:ss a").isEqualTo(LocalTime.of(14, 5, 0));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteSecond_lowerCaseAmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("1:10:30 am"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("invalid date time example: 1:10:30 am (h:mm:ss a)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteSecond_lowerCasePmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("2:05:00pm"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 2:05:00pm (h:mm:ssa)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteSecond_dottedAmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("1:10:30a.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 1:10:30a.m.");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitHourMinuteSecond_dottedPmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("2:05:00p.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 2:05:00p.m.");
  }

  @Test public void twoDigitHour_upperCaseAmMarker() {
    assertLocalTime("09AM", "HHa").isEqualTo(LocalTime.of(9, 0, 0));
  }

  @Test public void twoDigitHour_upperCasePmMarkerAfterSpace() {
    assertLocalTime("12 PM", "HH a").isEqualTo(LocalTime.of(12, 0, 0));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHour_lowerCaseAmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("09am"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 09am (HHa)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHour_lowerCasePmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("12 pm"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 12 pm (HH a)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHour_dottedAmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("09 a.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 09 a.m.");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHour_dottedPmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("12 p.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 12 p.m.");
  }

  @Test public void twoDigitHourMinuteWithoutAmPm() {
    assertLocalTime("09:00", "HH:mm").isEqualTo(LocalTime.of(9, 0, 0));
    assertLocalTime("15:00", "HH:mm").isEqualTo(LocalTime.of(15, 0, 0));
  }

  @Test public void twoDigitHourMinute_upperCaseAmMarker() {
    assertLocalTime("09:00AM", "HH:mma").isEqualTo(LocalTime.of(9, 0, 0));
  }

  @Test public void twoDigitHourMinute_upperCasePmMarkerAfterSpace() {
    assertLocalTime("12:00 PM", "HH:mm a").isEqualTo(LocalTime.of(12, 0, 0));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinute_lowerCasePmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("12:00 pm"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 12:00 pm (HH:mm a)");
  }

  // 12:00 is hour 12 on a 24-hour dial, which is PM, so an AM marker contradicts it. This is the
  // 24-hour reading of a two-digit hour, not a 12-hour clock reading midnight as noon.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinute_amMarkerContradictsHour_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("12:00 AM"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 12:00 AM (HH:mm a)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinute_dottedAmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("12:00 a.m."));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: 12:00 a.m. (HH:mm a)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinute_dottedPmMarker_unsupported() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("12:00 p.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 12:00 p.m.");
  }

  @Test public void twoDigitHourMinuteSecond_upperCaseAmMarker() {
    assertLocalTime("09:00:30AM", "HH:mm:ssa").isEqualTo(LocalTime.of(9, 0, 30));
  }

  // 15:00:30 is hour 15, which is PM, so the marker agrees and the example is accepted. The hour
  // stays a 24-hour number; the marker is redundant.
  @Test public void twoDigitHourMinuteSecond_upperCasePmMarkerAfterSpace() {
    assertLocalTime("15:00:30 PM", "HH:mm:ss a").isEqualTo(LocalTime.of(15, 0, 30));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinuteSecond_lowerCaseAmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("09:00:30am"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("invalid date time example: 09:00:30am (HH:mm:ssa)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinuteSecond_lowerCasePmMarker_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("15:00:30 pm"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("invalid date time example: 15:00:30 pm (HH:mm:ss a)");
  }

  // "a.m." is a locale-specific spelling: en-CA, fr-CA, nl and ~47 others use it, en-US and en-GB
  // do not. It is not a Token.AM_PM name, so no locale is pinned and the example follows the
  // runtime locale. Nothing else in such an example is locale sensitive -- any weekday, month or
  // zone name would pin ENGLISH and reject the marker -- so the only outcomes are these two.
  @Test public void twoDigitHourMinuteSecond_dottedAmMarker_localeThatSpellsItThatWay() {
    overrideLocale(Locale.CANADA);
    assertLocalTime("09:00:30a.m.", "HH:mm:ssa").isEqualTo(LocalTime.of(9, 0, 30));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinuteSecond_dottedAmMarker_localeThatDoesNot_disallowed() {
    overrideLocale(Locale.US);
    DateTimeException thrown =
        assertThrows(DateTimeException.class, () -> formatOf("09:00:30a.m."));
    assertThat(thrown)
        .hasMessageThat()
        .contains("invalid date time example: 09:00:30a.m. (HH:mm:ssa)");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void twoDigitHourMinuteSecond_dottedPmMarker_unsupported() {
    DateTimeException thrown =
        assertThrows(DateTimeException.class, () -> formatOf("15:00:30 p.m."));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 15:00:30 p.m.");
  }

  @Test public void twoDigitHourMinuteSecondWithoutAmPm() {
    assertLocalTime("09:00:30", "HH:mm:ss").isEqualTo(LocalTime.of(9, 0, 30));
    assertLocalTime("15:00:30", "HH:mm:ss").isEqualTo(LocalTime.of(15, 0, 30));
  }

  @Test public void dateAndTimeExamples() {
    assertLocalDateTime("2023-10-20 15:30:05", "yyyy-MM-dd HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2023, 10, 20, 15, 30, 5));
    assertLocalDateTime("2023/10/05 15:30:05", "yyyy/MM/dd HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2023, 10, 5, 15, 30, 5));
  }

  @Test public void singleDigitMonth_dateTime() {
    assertLocalDateTime("2019-3-21 15:30:05", "yyyy-M-dd HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2019, 3, 21, 15, 30, 5));
    assertLocalDateTime("2017/2/01 15:30:05", "yyyy/M/dd HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2017, 2, 1, 15, 30, 5));
  }

  @Test public void singleDigitMonthAndDay_dateTime() {
    assertLocalDateTime("2019-3-5 15:30:05", "yyyy-M-d HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2019, 3, 5, 15, 30, 5));
    assertLocalDateTime("2017/2/1 15:30:05", "yyyy/M/d HH:mm:ss")
        .isEqualTo(LocalDateTime.of(2017, 2, 1, 15, 30, 5));
  }

  @Test public void instantExample() {
    assertThat(formatOf("2023-10-05T15:30:05Z").parse(Instant.now().toString())).isNotNull();
  }

  @Test public void instantExample_parseToInstant() {
    assertThat(DateTimeFormats.parseToInstant("2011-12-03T10:15:30Z"))
        .isEqualTo(Instant.parse("2011-12-03T10:15:30Z"));
  }

  // Instant.toString() emits exactly this shape, so all three entry points must read it.
  @Test public void instantExample_parseZonedDateTime() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30Z"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneOffset.UTC));
  }

  @Test public void instantExample_parseOffsetDateTime() {
    assertThat(DateTimeFormats.parseOffsetDateTime("2011-12-03T10:15:30Z"))
        .isEqualTo(OffsetDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneOffset.UTC));
  }

  @Test public void instantExample_withNanos_parseZonedDateTime() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30.123Z"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30, 123000000), ZoneOffset.UTC));
  }

  @Test public void isoLocalDateTimeExample() {
    assertThat(LocalDateTime.parse("2023-10-05T15:03:05", formatOf("2023-10-05T15:30:05")))
        .isEqualTo(LocalDateTime.of(2023, 10, 5, 15, 3, 5));
  }

  @Test public void isoLocalDateExample() {
    assertThat(LocalDate.parse("2022-10-05", formatOf("2023-10-05")))
        .isEqualTo(LocalDate.of(2022, 10, 5));
  }

  @Test public void isoOffsetDateTimeExample() {
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

  @Test public void isoZonedDateTimeExample() {
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

  @Test public void isoZonedDateTime_withNanosExample() {
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

  // TODO: remove the suppression after mug-errorprone is released with this fix.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void zoneIdInBrackets_withoutOffset() {
    assertThat(
            ZonedDateTime.parse(
                "2022-10-05T00:10:00.12345[America/Los_Angeles]",
                formatOf("2023-12-09T10:00:00.12345[Europe/Paris]")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2022, 10, 5, 0, 10, 0, 123450000),
                ZoneId.of("America/Los_Angeles")));
  }

  @Test public void zoneIdInBrackets_zeroSeconds() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03T10:15+01:00[Europe/Paris]"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15), ZoneId.of("Europe/Paris")));
  }

  @Test public void zoneIdInBrackets_utc() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30Z[UTC]"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("UTC")));
  }

  @Test public void zoneIdInBrackets_etcUtc() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30Z[Etc/UTC]"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Etc/UTC")));
  }

  @Test public void zoneIdInBrackets_ambiguousZoneAbbreviation() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30+01:00[CET]"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("CET")));
  }

  @Test public void zoneIdInBrackets_threePartZoneId() {
    assertThat(
            DateTimeFormats.parseZonedDateTime(
                "2011-12-03T10:15:30-03:00[America/Argentina/Buenos_Aires]"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30),
                ZoneId.of("America/Argentina/Buenos_Aires")));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void zoneIdInBrackets_contentNotAZoneId_unsupported() {
    DateTimeException thrown =
        assertThrows(DateTimeException.class, () -> formatOf("2011-12-03T10:15:30[Foo]"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03T10:15:30[Foo]");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void zoneIdInBrackets_zoneNameThatIsNotAZoneId_invalid() {
    DateTimeException thrown =
        assertThrows(DateTimeException.class, () -> formatOf("2011-12-03T10:15:30[PST]"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "invalid date time example: 2011-12-03T10:15:30[PST]"
                + " (yyyy-MM-dd'T'HH:mm:ss'['VV']')");
  }

  /** Brackets are literal text, so a bracketed date is a date, not a zone id. */
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void bracketedDate_notTreatedAsZoneId() {
    assertThat(LocalDateTime.parse("[2011-12-03] 10:15:30", formatOf("[2011-12-03] 10:15:30")))
        .isEqualTo(LocalDateTime.of(2011, 12, 3, 10, 15, 30));
  }

  @Test public void bareGmtOffsetZone_isZoneId() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 GMT+08:00"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("GMT+08:00")));
  }

  @Test public void bareGmtNegativeOffsetZone_isZoneId() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 GMT-08:00"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("GMT-08:00")));
  }

  /** {@code VV} cannot parse the short spelling, so it stays a localized offset. */
  @Test public void bareGmtShortOffset_staysLocalizedOffset() {
    assumeUsLocale(); // the O specifier renders "GMT" differently in other locales
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 GMT+8"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneOffset.ofHours(8)));
  }

  @Test public void bareUtcOffsetZone_isZoneId() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 UTC+08:00"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("UTC+08:00")));
  }

  @Test public void bareUtcNegativeOffsetZone_isZoneId() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 UTC-08:00"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("UTC-08:00")));
  }

  /**
   * The localized offset specifier {@code O} requires the literal "GMT" prefix, and {@code VV}
   * rejects the abbreviated offset, so no pattern can read this.
   */
  @Test public void bareUtcShortOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 UTC+8"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 UTC+8' could not be parsed at index 20");
  }

  @Test public void bareUtcTwoDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 UTC+12"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 UTC+12' could not be parsed at index 20");
  }

  @Test public void bareZoneAbbreviationWithTwoDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PST+12"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 PST+12' could not be parsed at index 20");
  }

  @Test public void bareZoneIdAbbreviationWithTwoDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 CET+12"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 CET+12' could not be parsed at index 20");
  }

  @Test public void bareZoneIdAbbreviationWithColonOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 CET+08:00"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 CET+08:00' could not be parsed at index 20");
  }

  @Test public void bracketedUtcTwoDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30+08:00[UTC+08]"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03T10:15:30+08:00[UTC+08]' could not be parsed at index 26");
  }

  @Test public void etcUtcShortOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/UTC+8"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "Text '2011-12-03 10:15:30 Etc/UTC+8' could not be parsed, unparsed text found at"
                + " index 27");
  }

  @Test public void etcUtcTwoDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/UTC+10"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            "Text '2011-12-03 10:15:30 Etc/UTC+10' could not be parsed, unparsed text found at"
                + " index 27");
  }

  @Test public void bareGmtFourDigitOffset_parses() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 GMT+0800"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneOffset.ofHours(8)));
  }

  @Test public void bareGmtFourDigitOffset_jsDateToStringShape_parses() {
    assertThat(DateTimeFormats.parseZonedDateTime("Sat Dec 03 2011 10:15:30 GMT+0800"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneOffset.ofHours(8)));
  }

  @SuppressWarnings("DateTimeExampleStringCheck") // TODO: remove after mug-errorprone release
  @Test public void bareGmtFourDigitOffset_roundTrips() {
    DateTimeFormatter formatter = formatOf("2011-12-03 10:15:30 GMT+0800");
    ZonedDateTime time =
        ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneOffset.ofHours(8));
    assertThat(formatter.format(time)).isEqualTo("2011-12-03 10:15:30 GMT+0800");
  }

  @Test public void bareUtcFourDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 UTC+0800"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 UTC+0800' could not be parsed at index 20");
  }

  @Test public void bareZoneAbbreviationWithFourDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PST+0800"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 PST+0800' could not be parsed at index 20");
  }

  @Test public void bareZoneIdAbbreviationWithFourDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 CET+0800"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 CET+0800' could not be parsed at index 20");
  }

  @Test public void bareZoneAbbreviationWithColonOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PST+08:00"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 PST+08:00' could not be parsed at index 20");
  }

  @Test public void bareGenericZoneAbbreviationWithShortOffset_throws() {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PT+08"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 PT+08");
  }

  @Test public void bareGenericZoneAbbreviationWithFourDigitOffset_throws() {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PT+0800"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 PT+0800");
  }

  @Test public void bareGenericZoneAbbreviationWithColonOffset_throws() {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PT+08:00"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 PT+08:00");
  }

  @Test public void bareGmtSixDigitOffset_parses() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 GMT+080000"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneOffset.ofHours(8)));
  }

  @Test public void bareZoneAbbreviationWithSixDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PST+080000"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03 10:15:30 PST+080000' could not be parsed at index 20");
  }

  @Test public void bracketedEtcUtcTwoDigitOffset_throws() {
    DateTimeParseException thrown = assertThrows(
        DateTimeParseException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30+08:00[Etc/UTC+08]"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Text '2011-12-03T10:15:30+08:00[Etc/UTC+08]' could not be parsed at index 33");
  }

  @Test public void zoneIdInBrackets_gmtOffsetZone() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30+08:00[GMT+08:00]"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("GMT+8")));
  }

  @Test public void bareZoneId_threeParts() {
    assertThat(
            DateTimeFormats.parseZonedDateTime(
                "2011-12-03 10:15:30 America/Argentina/Buenos_Aires"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30),
                ZoneId.of("America/Argentina/Buenos_Aires")));
  }

  @Test public void bareZoneId_singleWord_throws() {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Japan"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 Japan");
  }

  @Test public void bareZoneId_secondPartIsRegionName() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 US/Pacific"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("US/Pacific")));
  }

  @Test public void bareZoneId_secondPartIsZoneNameAbbreviation() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Australia/ACT"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Australia/ACT")));
  }

  @Test public void bareZoneId_etcUtc() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/UTC"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Etc/UTC")));
  }

  @Test public void bareZoneId_etcGmtWithOffset() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/GMT+8"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Etc/GMT+8")));
  }

  @Test public void bareZoneId_etcGmtWithTwoDigitOffset() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/GMT-14"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Etc/GMT-14")));
  }

  @Test public void bareZoneId_etcGmtWithNegativeSingleDigitOffset() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/GMT-0"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Etc/GMT-0")));
  }

  @Test public void bareZoneId_etcGmtWithPositiveTwoDigitOffset() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/GMT+10"))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Etc/GMT+10")));
  }

  @Test public void bareZoneId_etcRegionAlias() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Etc/Greenwich"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Etc/Greenwich")));
  }

  @Test public void bareZoneId_ambiguousAbbreviationCet() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 CET"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("CET")));
  }

  @Test public void bareZoneId_ambiguousAbbreviationEet() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 EET"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("EET")));
  }

  @Test public void bareZoneId_ambiguousAbbreviationWet() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 WET"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("WET")));
  }

  @Test public void legacyZoneAbbreviation_throws(@TestParameter({"MET", "UCT"}) String name) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 " + name));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 " + name);
  }

  @Test public void bracketedLegacyZoneAbbreviation_throws(
      @TestParameter({"MET", "UCT"}) String name) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30[" + name + "]"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03T10:15:30[" + name + "]");
  }

  @Test public void zoneAbbreviation_resolvesSameZoneInEveryLocale() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PST").getZone())
        .isEqualTo(ZoneId.of("America/Los_Angeles"));
  }

  @Test public void allZoneNameAbbreviationsParse() {
    for (String name : DateTimeFormats.zoneNameAbbreviations()) {
      assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 " + name)).isNotNull();
    }
  }

  @Test public void wrongOffsetZoneAbbreviation_throws(
      @TestParameter({"IST", "BST", "GST", "ACST", "ACT", "IDT", "CIT"}) String name) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 " + name));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 " + name);
  }

  @Test public void unresolvableZoneAbbreviation_throws(
      @TestParameter({
            "BET", "BIOT", "CEDT", "CWST", "DFT", "DUT", "EEDT", "EIT", "FET", "HAEC",
            "HMT", "HNE", "MEZ", "PHT", "SLT", "THA", "VOLT", "WEDT", "YET", "YKT",
            "YST", "BRT", "SGT", "ICT", "CAST", "MYT", "WST", "NPT"
          })
          String name) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 " + name));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 " + name);
  }

  @Test public void genericZoneAbbreviation_pt_throws() {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 PT"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 PT");
  }

  @Test public void genericZoneAbbreviation_mt_throws() {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 MT"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 MT");
  }

  @Test public void zoneAbbreviation_cst() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 CST").getZone())
        .isEqualTo(ZoneId.of("America/Chicago"));
  }

  @Test public void zoneAbbreviation_est() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 EST").getZone())
        .isEqualTo(ZoneId.of("America/New_York"));
  }

  @Test public void zoneAbbreviation_cest() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 CEST").getZone())
        .isEqualTo(ZoneId.of("Europe/Paris"));
  }

  @Test public void zoneAbbreviation_utc() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 UTC").getZone())
        .isEqualTo(ZoneId.of("UTC"));
  }

  @Test public void zoneAbbreviation_gmt() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 GMT").getZone())
        .isEqualTo(ZoneId.of("GMT"));
  }

  @Test public void zoneAbbreviation_ut() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 UT").getZone())
        .isEqualTo(ZoneId.of("UT"));
  }

  @Test public void zoneAbbreviation_precededByWeekday_resolvesSameZone() {
    assertThat(DateTimeFormats.parseZonedDateTime("Sat 2011-12-03 10:15:30 PST").getZone())
        .isEqualTo(ZoneId.of("America/Los_Angeles"));
  }

  @Test public void bareZoneId_hyphenatedCityName() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 Africa/Porto-Novo"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("Africa/Porto-Novo")));
  }

  @Test public void bareZoneId_twiceHyphenatedCityName() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 America/Port-au-Prince"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("America/Port-au-Prince")));
  }

  @Test public void legacySingleWordAndHyphenatedRegionAlias_throws(
      @TestParameter({
            "Cuba", "Egypt", "Eire", "GB", "GB-Eire", "Greenwich", "Hongkong", "Iceland", "Iran",
            "Israel", "Jamaica", "Japan", "Kwajalein", "Libya", "Navajo", "NZ", "NZ-CHAT", "Poland",
            "Portugal", "PRC", "ROK", "Singapore", "Turkey", "Universal", "W-SU", "Zulu"
          })
          String alias) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 " + alias));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 " + alias);
  }

  @Test public void bracketedLegacySingleWordAndHyphenatedRegionAlias_throws(
      @TestParameter({"GB-Eire", "NZ", "NZ-CHAT", "PRC", "ROK", "W-SU", "Japan", "Singapore"})
          String alias) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30[" + alias + "]"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03T10:15:30[" + alias + "]");
  }

  @Test public void legacySystemVAndPosixZone_throws(
      @TestParameter({
            "SystemV/AST4",
            "SystemV/AST4ADT",
            "SystemV/HST10",
            "SystemV/YST9YDT",
            "CST6CDT",
            "EST5EDT",
            "MST7MDT",
            "PST8PDT",
            "GMT0",
            "Etc/GMT0"
          })
          String zone) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03 10:15:30 " + zone));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 " + zone);
  }

  @Test public void bracketedLegacySystemVAndPosixZone_throws(
      @TestParameter({"SystemV/AST4ADT", "CST6CDT"}) String zone) {
    DateTimeException thrown = assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2011-12-03T10:15:30[" + zone + "]"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03T10:15:30[" + zone + "]");
  }

  /**
   * The ISO date-time designator is a single letter immediately followed by the hour digits. It
   * must stay a token of its own, or else the hour would be swallowed into an opaque word.
   */
  @Test public void isoDateTime_tDesignatorNotFusedWithHour() {
    assertThat(formatOf("2011-12-03T10:15:30")).isEqualTo(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  /** A lone unrecognized word must stay unsupported, so that typos aren't reported as bad zones. */
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void unrecognizedWord_notTreatedAsZoneId() {
    DateTimeException thrown =
        assertThrows(DateTimeException.class, () -> formatOf("2011-12-03 10:15:30 Foo"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("unsupported date time example: 2011-12-03 10:15:30 Foo");
  }

  @Test public void isoLocalTimeExample() {
    assertThat(LocalTime.parse("10:20:10", formatOf("10:30:12")))
        .isEqualTo(LocalTime.of(10, 20, 10));
  }

  @Test public void rfc1123Example() {
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

  @Test public void rfc1123_negativeOffsetWithoutWeekday_singleDigitDayExample() {
    assertThat(
            ZonedDateTime.parse(
                "Fri, 6 Jun 2008 03:10:10 -0800", formatOf("1 Jun 2008 11:05:30 -0800")))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2008, 6, 6, 3, 10, 10, 0), ZoneOffset.ofHours(-8)));
  }

  @Test public void rfc1123_negativeOffsetWithoutWeekday_twoDigitDayExample() {
    assertThat(
            ZonedDateTime.parse(
                "Fri, 20 Jun 2008 03:10:10 -0800", formatOf("13 Jun 2008 11:05:30 -0800")))
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2008, 6, 20, 3, 10, 10, 0), ZoneOffset.ofHours(-8)));
  }

  @Test public void rfc1123Shape_gmtZoneName_usesRfcFormatter() {
    assertThat(formatOf("Tue, 10 Jun 2008 11:05:30 GMT"))
        .isSameInstanceAs(DateTimeFormatter.RFC_1123_DATE_TIME);
  }

  @Test public void rfc1123Shape_utcZoneName_notShadowedByRfcFormatter() {
    assertThat(DateTimeFormats.parseZonedDateTime("Tue, 10 Jun 2008 11:05:30 UTC"))
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2008, 6, 10, 11, 5, 30), ZoneId.of("UTC")));
  }

  @Test public void rfc1123Shape_zoneNameAbbreviation_notShadowedByRfcFormatter() {
    assertThat(DateTimeFormats.parseZonedDateTime("Tue, 10 Jun 2008 11:05:30 PST"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2008, 6, 10, 11, 5, 30), ZoneId.of("America/Los_Angeles")));
  }

  @Test public void rfc1123Shape_withoutWeekday_zoneNameAbbreviation_notShadowedByRfcFormatter() {
    assertThat(DateTimeFormats.parseZonedDateTime("10 Jun 2008 11:05:30 PST"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2008, 6, 10, 11, 5, 30), ZoneId.of("America/Los_Angeles")));
  }

  @Test public void invalid_rfc3339Example() {
    assertThrows(
        DateTimeParseException.class, () -> DateTimeFormats.parseToInstant("2000-40-01T00:00:00Z"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void monthOfYear_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("Dec 31, 2023 12:00:00 America/New_York"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousMmddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("10/12/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("01/12/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("10/02/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousMddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/12/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("1/02/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousDdmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("10/1/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("01/1/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousDmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/2/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeMmddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("10/32/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("13/13/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeMddyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/32/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("0/31/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeDdmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("32/1/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("31/0/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangemDdyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("1/32/2023 10:10:10"));
    assertThrows(DateTimeException.class, () -> formatOf("0/31/2023 10:10:10"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void outOfRangeDmyyyy_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("0/0/2023 10:10:10"));
  }

  @Test public void mmddyyyy_supportedIfDayIsGreaterThan12() {
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

  @Test public void mddyyyy_supportedIfDayIsGreaterThan12() {
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

  @Test public void ddmmyyyy_supportedIfDayIsGreaterThan12() {
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

  @Test public void ddmyyyy_supportedIfDayIsGreaterThan12() {
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

  // TODO: remove the suppressions after mug-errorprone is released with this fix.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void mmddyyyy_precededByWeekday() {
    assertThat(LocalDate.parse("Fri 01/23/2015", formatOf("Thu 10/30/2014")))
        .isEqualTo(LocalDate.of(2015, 1, 23));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void ddmmyyyy_precededByWeekday() {
    assertThat(LocalDate.parse("Fri 23/01/2015", formatOf("Thu 30/10/2014")))
        .isEqualTo(LocalDate.of(2015, 1, 23));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void mmddyyyy_followedByUnsupportedWord_throwsDateTimeException() {
    DateTimeException thrown =
        assertThrows(DateTimeException.class, () -> formatOf("10/30/2014 Foo"));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: 10/30/2014 Foo");
  }

  @Test public void formatOf_mmddyyMixedIn() {
    DateTimeFormatter formatter = formatOf("MM/dd/yyyy <12:10:00> <America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertThat(zonedTime.format(formatter)).isEqualTo("10/20/2023 01:02:03 America/Los_Angeles");
  }

  @Test public void formatOf_ddmmyyMixedIn() {
    DateTimeFormatter formatter = formatOf("dd MM yyyy <12:10:00  America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertThat(zonedTime.format(formatter)).isEqualTo("20 10 2023 01:02:03  America/Los_Angeles");
  }

  @Test public void formatOf_monthOfYearMixedIn() {
    DateTimeFormatter formatter = formatOf("E, LLL dd yyyy <12:10:00 America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss VV");
  }

  @Test public void formatOf_fullWeekdayAndMonthNamePlaceholder() {
    assumeUsLocale();
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    DateTimeFormatter formatter =
        formatOf("<Tuesday>, <May> dd yyyy <12:10:00> <+08:00> <America/New_York>");
    assertEquivalent(formatter, zonedTime, "EEEE, LLLL dd yyyy HH:mm:ss ZZZZZ VV");
  }

  @Test public void formatOf_12HourFormat() {
    assumeUsLocale();
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    DateTimeFormatter formatter = formatOf("dd MM yyyy <AD> hh:mm <PM> <+08:00>");
    assertThat(zonedTime.format(formatter)).isEqualTo("20 10 2023 AD 01:02 AM -07:00");
  }

  @Test public void formatOf_zoneNameNotRetranslated() {
    assumeUsLocale();
    DateTimeFormatter formatter = formatOf("<Mon>, <Jan> dd yyyy <12:10:00> VV");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss VV");
  }

  @Test public void formatOf_zoneOffsetNotRetranslated() {
    DateTimeFormatter formatter = formatOf("E, LLL dd yyyy <12:10:00> O");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneOffset.ofHours(-7));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss O");
  }

  @Test public void formatOf_monthOfYearMixedIn_withDayOfWeek() {
    DateTimeFormatter formatter = formatOf("E, LLL dd yyyy <12:10:00> <America/New_York>");
    ZonedDateTime zonedTime =
        ZonedDateTime.of(LocalDateTime.of(2023, 10, 20, 1, 2, 3), ZoneId.of("America/Los_Angeles"));
    assertEquivalent(formatter, zonedTime, "E, LLL dd yyyy HH:mm:ss VV");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
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

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
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
    assertWithMessage("Using format %s", formatter)
        .that(time.format(formatter))
        .isEqualTo("00:00:00");
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void localDateExamplesFromDifferentFormatters(
      @TestParameter({"ISO_LOCAL_DATE", "yyyy/MM/dd"}) String formatterName,
      @TestParameter({"2020-01-01", "1979-01-01", "2035-12-31"}) String date)
      throws Exception {
    LocalDate day = LocalDate.parse(date);
    String example = day.format(getFormatterByName(formatterName));
    assertThat(LocalDate.parse(example, DateTimeFormats.formatOf(example))).isEqualTo(day);
  }

  @Test public void localDateWithWeekdayExamples() {
    ZonedDateTime date =
        DateTimeFormats.parseZonedDateTime("Mon, 2007-12-31 00:00:00 America/New_York");
    assertThat(date.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
  }

  @Test public void zonedDateTimeExamplesFromDifferentFormatters(
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

  @Test public void withZoneIdExamplesFromDifferentFormatters(
      @TestParameter({
            "ISO_OFFSET_DATE_TIME",
            "ISO_DATE_TIME",
            "ISO_ZONED_DATE_TIME",
            "RFC_1123_DATE_TIME",
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
    // Zone names (z, zz, zzz) are rendered in English because that's the locale the library pins
    // when parsing them back. Without it, the two halves of the roundtrip would disagree.
    String example = zonedTime.format(getFormatterByName(formatterName).withLocale(Locale.ENGLISH));
    assertThat(DateTimeFormats.parseZonedDateTime(example).withFixedOffsetZone())
        .isEqualTo(zonedTime.withFixedOffsetZone());
  }

  @Test public void withZoneIdExamplesFromDifferentFormatters_usLocaleSpecific(
      @TestParameter({"yyyy/MM/dd HH:mm:ssa VV"}) String formatterName,
      @TestParameter({
            "2020-01-01T00:00:01-07:00[America/New_York]",
            "1979-01-01T00:00:00+01:00[Europe/Paris]",
          })
          String datetime)
      throws Exception {
    assumeUsLocale();
    ZonedDateTime zonedTime = ZonedDateTime.parse(datetime, DateTimeFormatter.ISO_DATE_TIME);
    String example = zonedTime.format(getFormatterByName(formatterName));
    assertThat(DateTimeFormats.parseZonedDateTime(example).withFixedOffsetZone())
        .isEqualTo(zonedTime.withFixedOffsetZone());
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
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

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void zoneIdRetainedExamples_usLocaleOnly(
      @TestParameter({
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
    assumeUsLocale();
    ZonedDateTime zonedTime = ZonedDateTime.parse(datetime, DateTimeFormatter.ISO_DATE_TIME);
    String example = zonedTime.format(getFormatterByName(formatterName));
    assertThat(ZonedDateTime.parse(example, DateTimeFormats.formatOf(example)).toInstant())
        .isEqualTo(zonedTime.toInstant());
  }

  @Test public void zonedDateTimeWithNanosExamples(
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

  @Test public void offsetDateTimeWithNanosExamples(
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

  @Test public void parseToInstant_fromDateTimeString(
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

  @Test public void parseToInstant_fromInstantString(
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

  @Test public void parseOffsetDateTime_nonStandardFormat() throws Exception {
    assertThat(DateTimeFormats.parseOffsetDateTime("2020-01-01T00:00:00.123  +08:00"))
        .isEqualTo(
            OffsetDateTime.parse(
                "2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseOffsetDateTime("2020-01-01 00:00:00.123  +08:00"))
        .isEqualTo(
            OffsetDateTime.parse(
                "2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
  }

  @Test public void parseOffsetDateTime_invalid() throws Exception {
    assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseOffsetDateTime("2020-01-01T00:00:00.123 bad +08:00"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseOffsetDateTime("2020-01-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseOffsetDateTime("2020/01/01"));
  }

  @Test public void parseZonedDateTime_nonStandardFormat() throws Exception {
    assertThat(DateTimeFormats.parseZonedDateTime("2020-01-01T00:00:00.123  +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("2020-01-01 00:00:00.123  +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("2020/01/01T00:00, America/Los_Angeles"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2020-01-01T00:00:00-08:00[America/Los_Angeles]",
                DateTimeFormatter.ISO_ZONED_DATE_TIME));
  }

  @Test public void parseZonedDateTime_chinese() throws Exception {
    assertThat(DateTimeFormats.parseZonedDateTime("2025年8月13日 0点0分0秒 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T00:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("周三 2025年8月13日 0点0分0秒 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T00:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 0点0分0秒 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T00:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 上午10点 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T10:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 下午14点 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T14:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 下午2点 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T14:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 下午2时 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T14:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 下午2:10 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T14:10:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 下午2:10:00 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T14:10:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 上午2:10:00 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T02:10:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    assertThat(DateTimeFormats.parseZonedDateTime("星期三 2025年8月13日 上午2:10 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                "2025-08-13T02:10:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME));
  }

  /**
   * A CJK token pins {@link Locale#CHINA} and a zone abbreviation pins {@link Locale#ENGLISH}. When
   * an example carries both, CHINA wins, and the zone reading differs as a result. This is the only
   * shape where the precedence is observable: every other mix of the two is unparseable in either
   * locale.
   */
  @Test public void localePrecedence_cjkWeekdayWinsOverZoneAbbreviation() {
    assertThat(DateTimeFormats.parseZonedDateTime("星期六 2011-12-03 10:15:30 MDT"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("America/Mazatlan")));
  }

  @Test public void localePrecedence_englishWeekdayKeepsEnglishZoneReading() {
    assertThat(DateTimeFormats.parseZonedDateTime("Sat 2011-12-03 10:15:30 MDT"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 15, 30), ZoneId.of("America/Denver")));
  }

  @Test public void localePrecedence_cjkAmPmMarkerWinsOverZoneAbbreviation() {
    assertThat(DateTimeFormats.parseZonedDateTime("2011年12月3日 上午10点 PST"))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2011, 12, 3, 10, 0), ZoneId.of("America/Los_Angeles")));
  }

  /**
   * {@code AD} and {@code BC} are English spellings read through the locale-sensitive {@code G}
   * specifier, so the era token pins {@link Locale#ENGLISH}. Unpinned, the example parses in only 6
   * of the 22 locales below -- the ones whose own era text happens to be "AD"/"BC".
   */
  // TODO: drop @SuppressWarnings once a mug-errorprone release carries the era locale pin. Until
  // then the compile-time check runs the old, unpinned inference against whatever locale the build
  // machine defaults to, so it accepts or rejects these examples depending on the machine.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void era_adSuffix_readInEnglish() {
    assertThat(LocalDate.parse("2011-12-03 AD", formatOf("2011-12-03 AD")))
        .isEqualTo(LocalDate.of(2011, 12, 3));
  }

  // TODO: drop @SuppressWarnings once a mug-errorprone release carries the era locale pin.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void era_adPrefix_readInEnglish() {
    assertThat(LocalDate.parse("AD 2011-12-03", formatOf("AD 2011-12-03")))
        .isEqualTo(LocalDate.of(2011, 12, 3));
  }

  // TODO: drop @SuppressWarnings once a mug-errorprone release carries the era locale pin.
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void era_bcSuffix_readInEnglish() {
    assertThat(LocalDate.parse("0500-12-03 BC", formatOf("0500-12-03 BC")))
        .isEqualTo(LocalDate.of(-499, 12, 3));
  }

  /**
   * The CJK tokens are declared before the era token, so they keep winning the locale. A mixed
   * example is rejected rather than read under a locale that can only understand half of it.
   */
  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void era_mixedWithCjkWeekday_throws() {
    DateTimeException thrown =
        assertThrows(DateTimeException.class, () -> formatOf("星期六 2011-12-03 AD"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("invalid date time example: 星期六 2011-12-03 AD (EEEE yyyy-MM-dd G)");
  }

  @Test public void parseZonedDateTime_invalid() throws Exception {
    assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2020-01-01T00:00:00.123 bad +08:00"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseZonedDateTime("2020-01-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseZonedDateTime("2020/01/02"));
  }

  @Test public void parseZonedDateTime_unknownZoneName() {
    assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseZonedDateTime("2020-01-01T12:00:00 China/Beijing"));
  }

  @Test public void parseZonedDateTime_knownZoneName() {
    ZonedDateTime dateTime =
        DateTimeFormats.parseZonedDateTime("2020-01-01T12:00:00 Asia/Shanghai");
    assertThat(dateTime.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
  }

  @Test public void parseToInstant_nonStandardFormat() throws Exception {
    assertThat(DateTimeFormats.parseToInstant("2020-01-01T00:00:00.123  +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                    "2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("2020-01-01 00:00:00.123  +08:00"))
        .isEqualTo(
            ZonedDateTime.parse(
                    "2020-01-01T00:00:00.123+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("2020/01/01T00:00, America/Los_Angeles"))
        .isEqualTo(
            ZonedDateTime.parse(
                    "2020-01-01T00:00:00-08:00[America/Los_Angeles]",
                    DateTimeFormatter.ISO_ZONED_DATE_TIME)
                .toInstant());
  }

  @Test public void parseToInstant_chinese() throws Exception {
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 0点0分 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T00:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("周三 2025年08月13日 0点0分 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T00:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("2025年8月13日 0点0分 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T00:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("2025年08月13日 0点0分 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T00:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 上午10点 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T10:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 下午14点 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T14:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 下午2点 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T14:00:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 下午2时30分 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T14:30:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 下午2:30 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T14:30:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 下午2:30:10 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T14:30:10+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 上午2:30 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T02:30:00+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 上午2:30:10 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T02:30:10+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
    assertThat(DateTimeFormats.parseToInstant("星期三 2025年8月13日 上午02:30:10 +08:00"))
        .isEqualTo(
            ZonedDateTime.parse("2025-08-13T02:30:10+08:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant());
  }

  @Test public void parseToInstant_invalid() throws Exception {
    assertThrows(
        DateTimeException.class,
        () -> DateTimeFormats.parseToInstant("2020-01-01T00:00:00.123 bad +08:00"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseToInstant("2020-01-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseToInstant("2020/01/01"));
  }

  @Test public void parseLocalDate_basicIsoDate() {
    assertThat(DateTimeFormats.parseLocalDate("20211020")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("20211001")).isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("20210101")).isEqualTo(LocalDate.of(2021, 1, 1));
  }

  @Test public void parseLocalDate_isoDate() {
    assertThat(DateTimeFormats.parseLocalDate("2021-10-20")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("2021-10-01")).isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021-01-01")).isEqualTo(LocalDate.of(2021, 1, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021-01-2")).isEqualTo(LocalDate.of(2021, 1, 2));
  }

  @Test public void parseLocalDate_euDate_mmddyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("10-30-2021")).isEqualTo(LocalDate.of(2021, 10, 30));
    assertThat(DateTimeFormats.parseLocalDate("1-30-2021")).isEqualTo(LocalDate.of(2021, 1, 30));
    assertThat(DateTimeFormats.parseLocalDate("10/20/2021")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("1/20/2021")).isEqualTo(LocalDate.of(2021, 1, 20));
  }

  @Test public void parseLocalDate_euDate_ddmmyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("30-10-2021")).isEqualTo(LocalDate.of(2021, 10, 30));
    assertThat(DateTimeFormats.parseLocalDate("20/10/2021")).isEqualTo(LocalDate.of(2021, 10, 20));
  }

  @Test public void parseLocalDate_withMonthName_yyyymmdd() {
    assertThat(DateTimeFormats.parseLocalDate("2021 Oct 20")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("2021 October 1"))
        .isEqualTo(LocalDate.of(2021, 10, 1));
  }

  @Test public void parseLocalDate_withMonthName_mmddyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("Oct 20 2021")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("October 1 2021"))
        .isEqualTo(LocalDate.of(2021, 10, 1));
  }

  @Test public void parseLocalDate_withMonthName_ddmmyyyy() {
    assertThat(DateTimeFormats.parseLocalDate("20 Oct 2021")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("1 October 2021"))
        .isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("01 October 2021"))
        .isEqualTo(LocalDate.of(2021, 10, 1));
  }

  @Test public void parseLocalDate_isoDateWithSlash() {
    assertThat(DateTimeFormats.parseLocalDate("2021/10/20")).isEqualTo(LocalDate.of(2021, 10, 20));
    assertThat(DateTimeFormats.parseLocalDate("2021/10/01")).isEqualTo(LocalDate.of(2021, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021/01/01")).isEqualTo(LocalDate.of(2021, 1, 1));
    assertThat(DateTimeFormats.parseLocalDate("2021/01/2")).isEqualTo(LocalDate.of(2021, 1, 2));
  }

  @Test public void parseLocalDate_instantHasNoDate() {
    Instant time = OffsetDateTime.of(2024, 4, 1, 10, 05, 30, 0, ZoneOffset.UTC).toInstant();
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate(time.toString()));
  }

  @Test public void parseLocalDate_cannotParseZonedDateTimeStringToLocalDate() {
    ZonedDateTime time = ZonedDateTime.of(2024, 4, 1, 10, 05, 30, 0, ZoneId.of("America/New_York"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate(time.toString()));
  }

  @Test public void parseLocalDate_cannotParseOffsetDateTimeStringToLocalDate() {
    OffsetDateTime time = OffsetDateTime.of(2024, 4, 1, 10, 05, 30, 0, ZoneOffset.of("-08:30"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate(time.toString()));
  }

  @Test public void parseLocalDate_incorrectDate() {
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("20213001"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("2021/30/01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("2021-30-01"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("01-01-2021"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("01/01/2021"));
  }

  @Test public void tIsRecognizedAndEscaped() {
    assertThat(
            ZonedDateTime.parse(
                "2023-11-06T00:10 Europe/Paris", formatOf("2022-10-05T00:10 America/New_York")))
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 11, 6, 0, 10, 0, 0), ZoneId.of("Europe/Paris")));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void offsetTimeExamples(
      @TestParameter({"00:00:00+18:00", "12:00-08:00", "23:59:59.999999999-18:00"})
          String example) {
    DateTimeFormatter formatter = DateTimeFormats.formatOf(example);
    OffsetTime time = OffsetTime.parse(example, formatter);
    assertThat(time.format(formatter)).isEqualTo(example);
  }

  @Test public void timeZoneMixedIn_zeroOffset() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<Z>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30Z", formatter);
    assertThat(dateTime)
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.UTC));
  }

  @Test public void timeZoneMixedIn_offsetWithoutColon() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("MM dd yyyy HH:mm:ss<+0100>");
    ZonedDateTime dateTime = ZonedDateTime.parse("01 10 2023 10:20:30-0800", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test public void timeZoneMixedIn_hourOffset() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<+01>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-08", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test public void timeZoneMixedIn_offsetWithColon() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<-01:00>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-08:00", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneOffset.ofHours(-8)));
  }

  @Test public void timeZoneMixedIn_zoneNameWithEuropeDateStyle() {
    DateTimeFormatter formatter =
        DateTimeFormats.formatOf("dd MM yyyy HH:mm:ss.SSS <America/New_York>");
    ZonedDateTime dateTime = ZonedDateTime.parse("30 10 2023 10:20:30.123 Europe/Paris", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(
                LocalDateTime.of(2023, 10, 30, 10, 20, 30, 123000000), ZoneId.of("Europe/Paris")));
  }

  @Test public void timeZoneMixedIn_offsetWithAmericanDateStyle() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<+01:00>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30-07:00", formatter);
    assertThat(dateTime)
        .isEqualTo(
            ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("-07:00")));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void timeZoneMixedIn_twoLetterGenericZoneAbbreviation_throws() {
    DateTimeException thrown = assertThrows(
        DateTimeException.class, () -> DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<PT>"));
    assertThat(thrown).hasMessageThat().contains("unsupported date time example: PT");
  }

  @Test public void timeZoneMixedIn_twoLetterZoneNameAbbreviation() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<UT>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30UT", formatter);
    assertThat(dateTime)
        .isEqualTo(ZonedDateTime.of(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("UT")));
  }

  @Test public void timeZoneMixedIn_fourLetterZoneNameAbbreviation() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("M dd yyyy HH:mm:ss<CEST>");
    ZonedDateTime dateTime = ZonedDateTime.parse("1 10 2023 10:20:30CEST", formatter);
    assertThat(dateTime.toLocalDateTime()).isEqualTo(LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0));
  }

  @Test public void timeZoneMixedIn_abbreviatedZoneName() {
    DateTimeFormatter formatter = DateTimeFormats.formatOf("MM dd yyyy HH:mm:ss<GMT>");
    ZonedDateTime dateTime = ZonedDateTime.parse("01 10 2023 10:20:30PST", formatter);
    assertThat(dateTime.toInstant())
        .isEqualTo(
            ZonedDateTime.of(
                    LocalDateTime.of(2023, 1, 10, 10, 20, 30, 0), ZoneId.of("America/Los_Angeles"))
                .toInstant());
  }

  @Test public void timeZoneMixedIn_unsupportedZoneSpec() {
    assertThrows(DateTimeException.class, () -> DateTimeFormats.inferDateTimePattern("1234"));
    assertThrows(DateTimeException.class, () -> DateTimeFormats.inferDateTimePattern("12:34:5"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void emptyExample_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf(""));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlySpaces_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("  "));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyPunctuations_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("/"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyNumbers_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("1234"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void exampleWithOnlyWords_disallowed() {
    assertThrows(DateTimeException.class, () -> formatOf("yyyy"));
    assertThrows(DateTimeException.class, () -> formatOf("America"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void typoInExample() {
    assertThrows(DateTimeException.class, () -> formatOf("<Febuary Wedenesday>, <2021/20/30>"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void placeholderExample_invalidPatternLetterInVerbatimPart_disallowed() {
    DateTimeException thrown = assertThrows(DateTimeException.class, () -> formatOf("<Tue> foo"));
    assertThat(thrown).hasMessageThat().contains("invalid date time example: <Tue> foo (EEE foo)");
  }

  @Test public void parseLocalDate_chinese() {
    assertThat(DateTimeFormats.parseLocalDate("2025年8月13日")).isEqualTo(LocalDate.of(2025, 8, 13));
    assertThat(DateTimeFormats.parseLocalDate("2025年8月1日")).isEqualTo(LocalDate.of(2025, 8, 1));
    assertThat(DateTimeFormats.parseLocalDate("2025年10月1日")).isEqualTo(LocalDate.of(2025, 10, 1));
    assertThat(DateTimeFormats.parseLocalDate("2025年10月13日")).isEqualTo(LocalDate.of(2025, 10, 13));
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
        .isEqualTo(
            LocalDate.parse(
                "2025年8月13日 星期三",
                DateTimeFormatter.ofPattern("yyyy年M月dd日 EEEE").withLocale(Locale.CHINA)));
    assertLocalDate("2025年8月10日 周日", "yyyy年M月dd日 EEE", Locale.CHINA)
        .isEqualTo(
            LocalDate.parse(
                "2025年8月10日 周日",
                DateTimeFormatter.ofPattern("yyyy年M月dd日 EEE").withLocale(Locale.CHINA)));
  }

  @Test public void chineseLocalDateTimes() {
    assertLocalDateTime("2020年08月10日 15点19分", "yyyy年MM月dd日 HH点mm分")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 15点19分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH点mm分")));
    assertLocalDateTime("2020年08月10日 15点19分01秒", "yyyy年MM月dd日 HH点mm分ss秒")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 15点19分01秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH点mm分ss秒")));
    assertLocalDateTime("2020年08月10日 5点9分", "yyyy年MM月dd日 H点m分")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 5点9分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H点m分")));
    assertLocalDateTime("2020年08月10日 5点9分8秒", "yyyy年MM月dd日 H点m分s秒")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 5点9分8秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H点m分s秒")));
    assertLocalDateTime("2020年08月10日 15时19分", "yyyy年MM月dd日 HH时mm分")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 15时19分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分")));
    assertLocalDateTime("2020年08月10日 15时19分01秒", "yyyy年MM月dd日 HH时mm分ss秒")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 15时19分01秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒")));
    assertLocalDateTime("2020年08月10日 5时9分", "yyyy年MM月dd日 H时m分")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 5时9分", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H时m分")));
    assertLocalDateTime("2020年08月10日 5时9分8秒", "yyyy年MM月dd日 H时m分s秒")
        .isEqualTo(
            LocalDateTime.parse(
                "2020年08月10日 5时9分8秒", DateTimeFormatter.ofPattern("yyyy年MM月dd日 H时m分s秒")));
  }

  @Test public void fuzzTests() {
    assumeUsLocale();
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
    assertLocalDate("周一, 2022-07-25", "EEE, yyyy-MM-dd", Locale.CHINA)
        .isEqualTo(LocalDate.parse("2022-07-25"));
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

  @Test public void fuzzTestsWithZoneAndWeekdays() {
    assumeUsLocale();
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

  @Test public void singleDigitMonth_zonedDateTime() {
    assumeUsLocale();
    assertZonedDateTime("2019-3-21 19:32:42 UTC", "yyyy-M-dd HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("2019-03-21T19:32:42+00:00[UTC]"));
  }

  @Test public void singleDigitMonthAndDay_zonedDateTime() {
    assumeUsLocale();
    assertZonedDateTime("2019-3-5 19:32:42 UTC", "yyyy-M-d HH:mm:ss zzz")
        .isEqualTo(ZonedDateTime.parse("2019-03-05T19:32:42+00:00[UTC]"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void singleDigitSecond_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("12:00:1"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void ambiguousMonthAndDay() {
    assertThrows(DateTimeException.class, () -> formatOf("01/02/03"));
    assertThrows(DateTimeException.class, () -> formatOf("01/02/2003"));
  }

  @Test @SuppressWarnings("DateTimeExampleStringCheck")
  public void trailingDotAfterSecond_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("2023-01-01T00:00:00."));
  }

  @Test public void parseLocalDate_invalidDotDateThrows() {
    assertThrows(DateTimeException.class, () -> DateTimeFormats.parseLocalDate("10.20.2011"));
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

  // The example is a parameter, so the compile-time example check can't evaluate it here.
  @SuppressWarnings("DateTimeExampleStringCheck")
  private static ComparableSubject<LocalTime> assertLocalTime(
      @CompileTimeConstant String example, String equivalentPattern) {
    assertThat(DateTimeFormats.inferDateTimePattern(example)).isEqualTo(equivalentPattern);
    // Build through the public formatOf() so the test sees the locale the library pins for AM/PM.
    // Compiling the raw pattern instead would read the marker in the ambient locale, which made
    // every AM/PM assertion below silently skip.
    DateTimeFormatter formatter = formatOf(example);
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

  private static void assertEquivalent(
      DateTimeFormatter formatter, ZonedDateTime time, String pattern) {
    assertWithMessage(formatter.toString())
        .that(time.format(formatter))
        .isEqualTo(time.format(DateTimeFormatter.ofPattern(pattern)));
    assertWithMessage(formatter.toString())
        .that(ZonedDateTime.parse(time.format(DateTimeFormatter.ofPattern(pattern)), formatter))
        .isEqualTo(time);
  }

  private void overrideLocale(Locale locale) {
    Locale originalLocale = Locale.getDefault();
    tearDowns.addTearDown(() -> {
      Locale.setDefault(originalLocale);
    });
    Locale.setDefault(locale);
  }

  private void assumeUsLocale() {
    assume().that(locale).isAnyOf(Locale.US, Locale.ENGLISH);
  }

  private static class LocaleProvider implements TestParameterValuesProvider {
    @Override public List<Locale> provideValues() {
      return List.of(
          Locale.ROOT, Locale.US, Locale.ENGLISH, Locale.UK, Locale.CANADA, Locale.CANADA_FRENCH,
          Locale.FRANCE, Locale.FRENCH, Locale.GERMAN, Locale.GERMANY, Locale.ITALY, Locale.ITALIAN,
          Locale.CHINA, Locale.CHINESE, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE,
          Locale.TAIWAN, Locale.PRC, Locale.JAPAN, Locale.JAPANESE, Locale.KOREA, Locale.KOREAN);
    }
  }
}
