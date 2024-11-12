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
package com.google.mu.safesql;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.mu.annotations.RequiresGuava;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.Substring;

/**
 * Facade class providing {@link SafeQuery} templates for GoogleSQL.
 *
 * @since 7.0
 */
@RequiresGuava
@CheckReturnValue
public final class GoogleSql {
  private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]");
  private static final Template<SafeQuery> DATE_TIME_EXPRESSION =
      SafeQuery.template("DATETIME('{time}', '{zone}')");
  private static final Template<SafeQuery> TIMESTAMP_EXPRESSION =
      SafeQuery.template("TIMESTAMP('{time}', '{zone}')");
  private static final Template<SafeQuery> DATE_EXPRESSION =
      SafeQuery.template("DATE({year}, {month}, {day})");
  private static final ZoneId GOOGLE_ZONE_ID = ZoneId.of("America/Los_Angeles");

  /**
   * Much like {@link SafeQuery#of}, but with additional GoogleSQL translation rules.
   *
   * <p>Specifically, {@link Instant} are translated to `TIMESTAMP()` GoogleSql function, {@link
   * ZonedDateTime} are translated to `DATETIME()` GoogleSql function, and {@link LocalDate} are
   * translated to `DATE()` GoogleSql function.
   *
   * @since 8.0
   */
  @SuppressWarnings("StringFormatArgsCheck")  // protected by @@TemplateFormatMethod
  @TemplateFormatMethod
  public static SafeQuery from(
      @CompileTimeConstant @TemplateString String queryTemplate, Object... args) {
    return template(queryTemplate).with(args);
  }

  /**
   * Much like {@link SafeQuery#template}, but with additional GoogleSQL translation rules.
   *
   * <p>Specifically, {@link Instant} are translated to `TIMESTAMP()` GoogleSql function,
   * {@link ZonedDateTime} are translated to `DATETIME()` GoogleSql function,
   * and {@link LocalDate} are translated to `DATE()` GoogleSql function.
   */
  public static Template<SafeQuery> template(@CompileTimeConstant String formatString) {
    return new SafeQuery.Translator() {
      @Override protected SafeQuery translateLiteral(Substring.Match placeholder, Object value) {
        if (value instanceof Instant) {
          return timestampExpression((Instant) value);
        }
        if (value instanceof ZonedDateTime) {
          return dateTimeExpression((ZonedDateTime) value);
        }
        if (value instanceof LocalDate) {
          return dateExpression((LocalDate) value);
        }
        return super.translateLiteral(placeholder, value);
      }
    }.translate(formatString);
  }


  private static SafeQuery dateTimeExpression(ZonedDateTime dateTime) {
    return DATE_TIME_EXPRESSION.with(
        dateTime.toLocalDateTime().format(LOCAL_DATE_TIME_FORMATTER), dateTime.getZone());
  }

  private static SafeQuery dateExpression(LocalDate date) {
    return DATE_EXPRESSION.with(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  private static SafeQuery timestampExpression(Instant instant) {
    return TIMESTAMP_EXPRESSION.with(
        instant.atZone(GOOGLE_ZONE_ID).toLocalDateTime().format(LOCAL_DATE_TIME_FORMATTER), GOOGLE_ZONE_ID);
  }

  private GoogleSql() {}
}
