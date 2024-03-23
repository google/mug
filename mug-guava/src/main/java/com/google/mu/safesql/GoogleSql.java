package com.google.mu.safesql;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.mu.annotations.RequiresGuava;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;

/**
 * Facade class providing {@link SafeQuery} templates for GoogleSQL.
 *
 * @since 7.0
 */
@RequiresGuava
public final class GoogleSql {
  private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]");
  private static final StringFormat DATE_TIME_EXPRESSION =
      new StringFormat("DATETIME('{time}', '{zone}')");
  private static final StringFormat TIMESTAMP_EXPRESSION =
      new StringFormat("TIMESTAMP('{time}', '{zone}')");
  private static final StringFormat DATE_EXPRESSION =
      new StringFormat("DATE({year}, {month}, {day})");
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
  public static StringFormat.To<SafeQuery> template(@CompileTimeConstant String formatString) {
    return new SafeQuery.Translator() {
      @Override protected String translateLiteral(Object value) {
        if (value instanceof Instant) {
          return timestampExpression((Instant) value);
        }
        if (value instanceof ZonedDateTime) {
          return dateTimeExpression((ZonedDateTime) value);
        }
        if (value instanceof LocalDate) {
          return dateExpression((LocalDate) value);
        }
        return super.translateLiteral(value);
      }
    }.translate(formatString);
  }


  private static String dateTimeExpression(ZonedDateTime dateTime) {
    return DATE_TIME_EXPRESSION.format(
        dateTime.toLocalDateTime().format(LOCAL_DATE_TIME_FORMATTER), dateTime.getZone());
  }

  private static String dateExpression(LocalDate date) {
    return DATE_EXPRESSION.format(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  private static String timestampExpression(Instant instant) {
    return TIMESTAMP_EXPRESSION.format(
        instant.atZone(GOOGLE_ZONE_ID).toLocalDateTime().format(LOCAL_DATE_TIME_FORMATTER), GOOGLE_ZONE_ID);
  }

  private GoogleSql() {}
}
