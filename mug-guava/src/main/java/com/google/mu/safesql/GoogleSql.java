package com.google.mu.safesql;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collector;

import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;

/**
 * Facade class providing {@link SafeQuery} templates for GoogleSQL.
 *
 * @since 7.0
 */
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
  private static final StringFormat.To<SafeQuery> PARENTHESIZED = template("({q})");


  /**
   * Much like {@link SafeQuery#template}, but with additional GoogleSQL translation rules.
   *
   * <p>Specifically, {@link Instant} are translated to `TIMESTAMP()` GoogleSql function,
   * {@link ZonedDateTime} are translated to `DATETIME()` GoogleSql function,
   * and {@link LocalDate} are translated to `DATE()` GoogleSql function.
   */
  public static StringFormat.To<SafeQuery> template(@CompileTimeConstant String formatString) {
    return new SafeQuery.Translator() {
      @Override protected String unquoted(Substring.Match placeholder, Object value) {
        if (value instanceof Instant) {
          return timestampExpression((Instant) value);
        }
        if (value instanceof ZonedDateTime) {
          return dateTimeExpression((ZonedDateTime) value);
        }
        if (value instanceof LocalDate) {
          return dateExpression((LocalDate) value);
        }
        return super.unquoted(placeholder, value);
      }
    }.translate(formatString);
  }

  /**
   * A collector that joins boolean query snippets using {@code AND} operator.
   *
   * @since 7.2
   */
  public static Collector<SafeQuery, ?, SafeQuery> and() {
    return collectingAndThen(
        mapping(PARENTHESIZED::with, SafeQuery.joining(" AND ")),
        query -> query.toString().isEmpty() ? SafeQuery.of("TRUE") : query);
  }

  /**
   * A collector that joins boolean query snippets using {@code OR} operator.
   *
   * @since 7.2
   */
  public static Collector<SafeQuery, ?, SafeQuery> or() {
    return collectingAndThen(
        mapping(PARENTHESIZED::with, SafeQuery.joining(" OR ")),
        query -> query.toString().isEmpty() ? SafeQuery.of("FALSE") : query);
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
