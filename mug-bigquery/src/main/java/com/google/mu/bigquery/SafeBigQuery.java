package com.google.mu.bigquery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.mu.util.StringFormat;
import com.google.mu.util.stream.BiStream;

/**
 * Facade class to create templates of {@link QueryJobConfiguration} using the BigQuery
 * parameterized query API to prevent SQL injection.
 *
 * <p>The string template syntax is defined by {@link StringFormat} and protected by the same
 * compile-time checks.
 *
 * @since 7.1
 */
public final class SafeBigQuery {
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSZZ");

  /**
   * Returns a template of {@iink QueryJobConfiguration} based on the {@code template} string.
   *
   * <p>For example: <pre>{@code
   * private static final StringFormat.To<QueryJobConfiguration> GET_JOB_IDS_BY_QUERY =
   *     SafeBigQuery.template(
   *         """
   *         SELECT job_id from INFORMATION_SCHEMA.JOBS_BY_PROJECT
   *         WHERE configuration.query LIKE '%{keyword}%'
   *         """);
   * 
   * QueryJobConfiguration query = GET_JOB_IDS_BY_QUERY.with("sensitive word");
   * }</pre>
   *
   * <p>Except  {@link TrustedSql}, which are directly substituted into the query,
   * all other placeholder arguments are passed into the QueryJobConfiguration as query parameters.
   *
   * <p>Placeholder types supported:
   * <ul>
   * <li>CharSequence
   * <li>Enum
   * <li>java.time.Instant (translated to TIMESTAMP)
   * <li>java.time.LocalDate (translated to DATE)
   * <li>Integer
   * <li>Long
   * <li>BigDecimal
   * <li>Double
   * <li>Float
   * </ul>
   * 
   * If you need to supply other types, consider to wrap them explicitly using one of the static
   * factory methods of {@link QueryParameterValue}.
   */
  public static StringFormat.To<QueryJobConfiguration> template(
      @CompileTimeConstant String template) {
    return StringFormat.template(
        template,
        (fragments, placeholders) -> {
          Iterator<String> it = fragments.iterator();
          Set<String> paramNames = new HashSet<>();
          BiStream.Builder<String, QueryParameterValue> parameters = BiStream.builder();
          StringBuilder queryText = new StringBuilder();
          placeholders.forEachOrdered(
              (placeholder, value) -> {
                queryText.append(it.next());
                if (value == null) {
                  queryText.append("NULL");
                } else if (value instanceof TrustedSql) {
                  queryText.append(value);
                } else {
                  String paramName = placeholder.skip(1, 1).toString().trim();
                  if (!paramNames.add(paramName)) {
                    throw new IllegalArgumentException("Duplicate placeholder name " + placeholder);
                  }
                  queryText.append("@" + paramName);
                  parameters.add(paramName, toQueryParameter(value));
                }
              });
          queryText.append(it.next());
          return parameters
              .build()
              .collect(
                  QueryJobConfiguration.newBuilder(queryText.toString()),
                  QueryJobConfiguration.Builder::addNamedParameter)
              .build();
        });
  }

  private static QueryParameterValue toQueryParameter(Object value) {
    if (value instanceof CharSequence) {
      return QueryParameterValue.string(value.toString());
    }
    if (value instanceof Instant) {
      Instant time = (Instant) value;
      return QueryParameterValue.timestamp(
          time.atZone(ZoneId.of("UTC")).format(TIMESTAMP_FORMATTER));
    }
    if (value instanceof LocalDate) {
      return QueryParameterValue.date(((LocalDate) value).toString());
    }
    if (value instanceof Boolean) {
      return QueryParameterValue.bool((Boolean) value);
    }
    if (value instanceof Integer) {
      return QueryParameterValue.int64((Integer) value);
    }
    if (value instanceof Long) {
      return QueryParameterValue.int64((Long) value);
    }
    if (value instanceof Double) {
      return QueryParameterValue.float64((Double) value);
    }
    if (value instanceof Float) {
      return QueryParameterValue.float64((Float) value);
    }
    if (value instanceof BigDecimal) {
      return QueryParameterValue.bigNumeric((BigDecimal) value);
    }
    if (value instanceof byte[]) {
      return QueryParameterValue.bytes((byte[]) value);
    }
    if (value instanceof QueryParameterValue) {
      return (QueryParameterValue) value;
    }
    if (value instanceof Enum) {
      return QueryParameterValue.string(((Enum<?>) value).name());
    }
    throw new IllegalArgumentException(
        "Unsupported parameter type: "
            + value.getClass().getName()
            + ". Consider manually converting it to QueryParameterValue.");
  }
}
