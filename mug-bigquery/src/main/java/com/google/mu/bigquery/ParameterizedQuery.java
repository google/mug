package com.google.mu.bigquery;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collector;

import com.google.cloud.bigquery.BigQuery.JobOption;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.JobException;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.util.StringFormat;
import com.google.mu.util.stream.BiStream;

/**
 * Facade class to create BigQuery parameterized queries using a template string and parameters.
 *
 * <p>The string template syntax is defined by {@link StringFormat} and protected by the same
 * compile-time checks.
 *
 * @since 7.1
 */
@Immutable
public final class ParameterizedQuery {
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSZZ");
  private final String query;

  @SuppressWarnings("Immutable")
  private final Map<String, QueryParameterValue> parameters;

  private ParameterizedQuery(String query, Map<String, QueryParameterValue> parameters) {
    this.query = requireNonNull(query);
    // Defensive copy. Not worth pulling in Guava dependency just for this
    this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
  }

  /**
   * Convenience method when you need to create the {@link ParameterizedQuery} inline, with both the
   * query template and the arguments.
   *
   * <p>For example:
   *
   * <pre>{@code
   * TableResult result = ParameterizedQuery.of("select * from JOBS where id = {id}", jobId).run();
   * }</pre>
   */
  @SuppressWarnings("StringFormatArgsCheck") // Called immediately, runtime error is good enough.
  public static ParameterizedQuery of(@CompileTimeConstant String query, Object... args) {
    return template(query).with(args);
  }

  /**
   * Returns a template of {@iink QueryJobConfiguration} based on the {@code template} string.
   *
   * <p>For example:
   *
   * <pre>{@code
   * private static final StringFormat.To<QueryJobConfiguration> GET_JOB_IDS_BY_QUERY =
   *     ParameterizedQuery.template(
   *         """
   *         SELECT job_id from INFORMATION_SCHEMA.JOBS_BY_PROJECT
   *         WHERE configuration.query LIKE '%{keyword}%'
   *         """);
   *
   * TableResult result = GET_JOB_IDS_BY_QUERY.with("sensitive word").run();
   * }</pre>
   *
   * <p>Except {@link ParameterizedQuery} itself, which are directly substituted into the query, all
   * other placeholder arguments are passed into the QueryJobConfiguration as query parameters.
   *
   * <p>Placeholder types supported:
   *
   * <ul>
   *   <li>CharSequence
   *   <li>Enum
   *   <li>java.time.Instant (translated to TIMESTAMP)
   *   <li>java.time.LocalDate (translated to DATE)
   *   <li>Integer
   *   <li>Long
   *   <li>BigDecimal
   *   <li>Double
   *   <li>Float
   * </ul>
   *
   * If you need to supply other types, consider to wrap them explicitly using one of the static
   * factory methods of {@link QueryParameterValue}.
   */
  public static StringFormat.To<ParameterizedQuery> template(@CompileTimeConstant String template) {
    return StringFormat.template(
        template,
        (fragments, placeholders) -> {
          Iterator<String> it = fragments.iterator();
          return placeholders
              .collect(
                  new Builder(),
                  (builder, placeholder, value) -> {
                    builder.append(it.next());
                    if (value == null) {
                      builder.append("NULL");
                    } else if (value instanceof ParameterizedQuery) {
                      builder.addSubQuery((ParameterizedQuery) value);
                    } else {
                      String paramName = placeholder.skip(1, 1).toString().trim();
                      builder.append("@" + paramName);
                      builder.addParameter(paramName, toQueryParameter(value));
                    }
                  })
              .append(it.next())
              .build();
        });
  }

  /** Returns a joiner that joins ParameterizedQuery elements using {@code delim}. */
  public static Collector<ParameterizedQuery, ?, ParameterizedQuery> joining(
      @CompileTimeConstant String delim) {
    return Collector.of(
        Builder::new,
        (b, q) -> b.appendDelimiter(delim).addSubQuery(q),
        (b1, b2) -> b1.appendDelimiter(delim).addSubQuery(b2.build()),
        Builder::build);
  }

  /**
   * Sends this query to BigQuery using the default options.
   *
   * <p>To use alternative options, pass {@link #jobConfiguration} to the {link BigQueryOptions} of
   * your choice.
   */
  public TableResult run(JobOption... options) throws JobException, InterruptedException {
    return BigQueryOptions.getDefaultInstance().getService().query(jobConfiguration());
  }

  /** Returns the {@link QueryJobConfiguration} that can be sent to BigQuery. */
  public QueryJobConfiguration jobConfiguration() {
    return BiStream.from(parameters)
        .collect(
            QueryJobConfiguration.newBuilder(query),
            QueryJobConfiguration.Builder::addNamedParameter)
        .build();
  }

  private static final class Builder {
    private final StringBuilder queryText = new StringBuilder();
    private final LinkedHashMap<String, QueryParameterValue> parameters = new LinkedHashMap<>();

    @CanIgnoreReturnValue
    Builder append(String snippet) {
      queryText.append(snippet);
      return this;
    }

    @CanIgnoreReturnValue
    Builder appendDelimiter(String delim) {
      if (queryText.length() > 0) {
        queryText.append(delim);
      }
      return this;
    }

    @CanIgnoreReturnValue
    Builder addParameter(String name, QueryParameterValue value) {
      if (parameters.put(name, value) != null) {
        throw new IllegalArgumentException("Duplicate placeholder name " + name);
      }
      return this;
    }

    @CanIgnoreReturnValue
    Builder addSubQuery(ParameterizedQuery subQuery) {
      queryText.append(subQuery.query);
      BiStream.from(subQuery.parameters).forEachOrdered(this::addParameter);
      return this;
    }

    ParameterizedQuery build() {
      return new ParameterizedQuery(queryText.toString(), parameters);
    }
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
    if (value.getClass().isArray()) {
      @SuppressWarnings("rawtypes")
      Class componentType = value.getClass().getComponentType();
      return QueryParameterValue.array((Object[]) value, componentType);
    }
    throw new IllegalArgumentException(
        "Unsupported parameter type: "
            + value.getClass().getName()
            + ". Consider manually converting it to QueryParameterValue.");
  }

  @Override
  public int hashCode() {
    return Objects.hash(query, parameters);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ParameterizedQuery) {
      ParameterizedQuery that = (ParameterizedQuery) obj;
      return query.equals(that.query) && parameters.equals(that.parameters);
    }
    return false;
  }

  @Override
  public String toString() {
    return query;
  }

  /**
   * A simple command-line tool for you to try it out. Pass command line args like:
   *
   * <pre>"select name from {tbl} where id = {id}" students 123</pre>
   * @param args
   */
  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("'[parameterized query]' [args...]");
     return;
    }
    try {
      @SuppressWarnings("CompileTimeConstant")
      TableResult result = of(args[0], Arrays.asList(args).subList(1, args.length).toArray(new Object[0]))
          .run();
      System.out.println(result);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
