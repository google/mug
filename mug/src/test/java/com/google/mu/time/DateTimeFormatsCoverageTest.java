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
 *                                                                           *
 *****************************************************************************/
package com.google.mu.time;

import static com.google.common.truth.Truth.assertThat;
import static com.google.mu.time.DateTimeFormats.formatOf;
import static org.junit.Assert.assertThrows;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SuppressWarnings("DateTimeExampleStringCheck")
public class DateTimeFormatsCoverageTest {

  @Test
  public void iso8601Date_supported() {
    assertThat(DateTimeFormats.parseLocalDate("2023-10-20")).isEqualTo(LocalDate.of(2023, 10, 20));
  }

  @Test
  public void iso8601Time_supported() {
    assertThat(LocalTime.parse("10:30:00", formatOf("10:30:00")))
        .isEqualTo(LocalTime.of(10, 30, 0));
  }

  @Test
  public void timeWithAmPm_supported() {
    assertThat(LocalTime.parse("1:10AM", formatOf("1:10AM"))).isEqualTo(LocalTime.of(1, 10, 0));
    assertThat(LocalTime.parse("2:05 PM", formatOf("2:05 PM"))).isEqualTo(LocalTime.of(14, 5, 0));
  }

  @Test
  public void twelveHourTimeWithSeconds_supported() {
    assertThat(LocalTime.parse("09:00:30AM", formatOf("09:00:30AM")))
        .isEqualTo(LocalTime.of(9, 0, 30));
  }

  @Test
  public void dateWithWeekdayAbbreviation_supported() {
    assertThat(LocalDate.parse("Mon, 2022-07-25", formatOf("Mon, 2022-07-25")))
        .isEqualTo(LocalDate.of(2022, 7, 25));
  }

  @Test
  public void dateWithChineseWeekday_supported() {
    assertThat(LocalDate.parse("周一, 2022-07-25", formatOf("周一, 2022-07-25")))
        .isEqualTo(LocalDate.of(2022, 7, 25));
  }

  @Test
  public void mixedChineseAndColonTimeUnits_notSupported() {
    assertThrows(DateTimeException.class, () -> formatOf("2020-08-10 15点19:01"));
  }

  @Test
  public void dateTimeWithZoneAbbreviation_supported() {
    assertThat(DateTimeFormats.parseZonedDateTime("1994-08-03 19:32:42 UTC"))
        .isEqualTo(ZonedDateTime.parse("1994-08-03T19:32:42Z[UTC]"));
  }

  @Test
  public void dateTimeWithRegionalZone_supported() {
    assertThat(DateTimeFormats.parseZonedDateTime("2020-01-01T12:00:00 Asia/Shanghai"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T12:00:00+08:00[Asia/Shanghai]"));
  }

  @Test
  public void dateTimeWithCommaSeparatedZone_supported() {
    assertThat(DateTimeFormats.parseZonedDateTime("2020/01/01T00:00, America/Los_Angeles"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T00:00:00-08:00[America/Los_Angeles]"));
  }

  @Test
  public void slashSeparatedDate_supported() {
    assertThat(DateTimeFormats.parseLocalDate("10/30/2014")).isEqualTo(LocalDate.of(2014, 10, 30));
    assertThat(DateTimeFormats.parseLocalDate("30/01/2014")).isEqualTo(LocalDate.of(2014, 1, 30));
  }

  @Test
  public void iso8601WithOffsetAndZoneId_supported() {
    assertThat(DateTimeFormats.parseZonedDateTime("2020-01-01T00:00:01-07:00[America/New_York]"))
        .isEqualTo(ZonedDateTime.parse("2020-01-01T02:00:01-05:00[America/New_York]"));
  }

  @Test
  public void singleDigitMonthHyphenDate_supported() {
    assertThat(DateTimeFormats.parseLocalDate("2018-4-10")).isEqualTo(LocalDate.of(2018, 4, 10));
  }

  @Test
  public void singleDigitTimeWithoutAmPm_rejectedByDesign() {
    assertThrows(DateTimeException.class, () -> formatOf("2:12:12"));
    assertThrows(DateTimeException.class, () -> formatOf("12:2"));
  }

  @Test
  public void dotSeparatedDate_supported() {
    assertThat(DateTimeFormats.parseLocalDate("2017.02.01")).isEqualTo(LocalDate.of(2017, 2, 1));
  }

  @Test
  public void pureNumericDateTime_rejectedByDesign() {
    assertThrows(DateTimeException.class, () -> formatOf("20170201122345"));
  }

  @Test
  public void jdkLegacyDateToString_rejectedByDesign() {
    assertThrows(DateTimeException.class, () -> formatOf("Tue Jun 4 16:25:15 +0800 2019"));
    assertThrows(DateTimeException.class, () -> formatOf("Wed Sep 16 CST 2009"));
  }

  @Test
  public void fullChineseDateTime_supported() {
    LocalDateTime expected = LocalDateTime.of(2020, 8, 10, 15, 19, 1);
    assertThat(LocalDateTime.parse("2020年08月10日 15点19分01秒", formatOf("2020年08月10日 15点19分01秒")))
        .isEqualTo(expected);
  }

  @Test
  public void ambiguousMonthAndDay_rejected() {
    assertThrows(DateTimeException.class, () -> formatOf("01/02/03"));
    assertThrows(DateTimeException.class, () -> formatOf("01/02/2003"));
  }
}
