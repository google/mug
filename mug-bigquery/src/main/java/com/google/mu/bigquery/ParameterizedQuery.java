package com.google.mu.bigquery;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Stream;

import com.google.cloud.bigquery.BigQuery.JobOption;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.JobException;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.annotations.RequiresBigQuery;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.stream.BiStream;

/**
 * Encapsulates a <a href="https://cloud.google.com/bigquery/docs/parameterized-queries">
 * BigQuery parameterized query</a>.
 *
 * <p>Instances of this class are created from a compile-time {@link TemplateString template}.
 * Template arguments are protected by the same set of compile-time checks that protect {@link
 * StringFormat}.
 *
 * <p>For simple use cases, a one-liner is enough to construct a parameterized query. For example:
 *
 * <pre>{@code
 * ParameterizedQuery query = ParameterizedQuery.of(
 *     "SELECT name FROM Students WHERE id = {id} and status = {status}",
 *     studentId, Status.ENROLLED);
 * TableResult result = query.run();
 * }</pre>
 *
 * <p>If you need to reuse the same query for different parameters, or to get a long query
 * "out of the way", you can define the query template as a class constant:
 *
 * <pre>{@code
 * private static final Template<ParameterizedQuery> GET_STUDENT = ParameterizedQuery.template(
 *     "SELECT name FROM Students WHERE id = {id} and status = {status}");
 *
 * // 200 lines later
 * TableResult enrolled = GET_STUDENT.with(studentId, Status.ENROLLED).run();
 * TableResult graduated = GET_STUDENT.with(alumniId, Status.GRADUATED).run();
 * }</pre>
 *
 * Compared to building the {@link QueryJobConfiguration} object manually, you get the following benefits:
 * <ul>
 * <li>Automatic type conversion. Particularly, {@link Instant} and {@link LocalDate} are
 *     formatted and converted to {@code TIMESTAMP} and {@code DATE} parameters respectively.
 * <li>Concise API for common use cases.
 * <li>Compile-time safety for defining the template as a class constant.
 * </ul>
 *
 * <p>In addition to parameterizing by values, you can also parameterize by columns, table names or
 * sub-queries. The following example allows you to use the same query on different datasets:
 *
 * <pre>{@code
 * private static final Template<ParameterizedQuery> GET_TABLES = ParameterizedQuery.template(
 *     "SELECT table_name FROM `{dataset}.INFORMATION_SCHEMA.TABLES`");
 *
 * TableResult marketingTables = GET_TABLES.with(ParameterizedQuery.of("marketing")).run();
 * TableResult humanResourceTables = GET_TABLES.with(ParameterizedQuery.of("human-resource")).run();
 * }</pre>
 *
 * Non-value string parameters must be wrapped inside {@code ParameterizedQuery} to ensure safety.
 *
 * @since 7.1
 */
@Immutable
@RequiresBigQuery
public final class ParameterizedQuery {
  private static final DateTimeFormatter TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSZZ");
  private final String query;

  @SuppressWarnings("Immutable")
  private final Map<String, QueryParameterValue> parameters;

  @SuppressWarnings("Immutable")
  private final Map<String, Object> originalValues;

  private ParameterizedQuery(
      String query,
      Map<String, QueryParameterValue> parameters,
      Map<String, Object> originalValues) {
    this.query = requireNonNull(query);
    // Defensive copy. Not worth pulling in Guava dependency just for this
    this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    this.originalValues = Collections.unmodifiableMap(new HashMap<>(originalValues));
  }

  /**
   * An empty query
   *
   * @since 8.2
   */
  public static ParameterizedQuery EMPTY = of("");

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
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  @TemplateFormatMethod
  public static ParameterizedQuery of(
      @CompileTimeConstant @TemplateString String query, Object... args) {
    return template(query).with(args);
  }

  /**
   * An optional query that's only rendered if {@code arg} is present; otherwise returns {@link
   * #EMPTY}. It's for use cases where a subquery is only added when present, for example the
   * following query will add the WHERE clause if the filter is present:
   *
   * <pre>{@code
   * SafeQuery query = ParameterizedQuery.of(
   *     "SELECT * FROM jobs {where}",
   *     ParameterizedQuery.optionally("WHERE {filter}", getOptionalFilter()));
   * }</pre>
   *
   * @since 8.2
   */
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  @TemplateFormatMethod
  public static ParameterizedQuery optionally(
      @CompileTimeConstant @TemplateString String query, Optional<?> arg) {
    return arg.map(v -> of(query, v)).orElse(EMPTY);
  }

  /**
   * Returns a template of {@link QueryJobConfiguration} based on the {@code template} string.
   *
   * <p>For example:
   *
   * <pre>{@code
   * private static final Template<QueryJobConfiguration> GET_JOB_IDS_BY_QUERY =
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
   *   <li>arrays
   * </ul>
   *
   * If you need to supply other types, consider to wrap them explicitly using one of the static
   * factory methods of {@link QueryParameterValue}.
   */
  public static Template<ParameterizedQuery> template(@CompileTimeConstant String template) {
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
                      builder.addParameter(paramName, value);
                    }
                  })
              .append(it.next())
              .build();
        });
  }

  /**
   * Returns the stream of enum constants defined by {@code enumClass},
   * with the names wrapped in ParameterizedQuery}.
   */
  public static Stream<ParameterizedQuery> enumConstants(Class<? extends Enum<?>> enumClass) {
    return Arrays.stream(enumClass.getEnumConstants())
        .map(e -> new ParameterizedQuery(e.name(), emptyMap(), emptyMap()));
  }

  /**
   * Returns a collector that joins ParameterizedQuery elements using {@code delimiter}.
   *
   * <p>Useful if you need to parameterize by a set of columns to select. Say, you might need to
   * query the table names only, or read the project, dataset and table names:
   *
   * <pre>{@code
   * private static final Template<ParameterizedQuery> QUERY_TABLES =
   *     ParameterizedQuery.template("SELECT {columns} FROM {dataset}.INFORMATION_SCHEMA.TABLES");
   *
   * ParameterizedQuery getTableNames = QUERY_TABLES.with(ParameterizedQuery.of("table_name"));
   * ParameterizedQuery getFullyQualified = QUERY_TABLES.with(
   *     Stream.of("table_catalog", "table_schema", "table_name")
   *         .map(ParameterizedQuery::of)
   *         .collect(ParameterizedQuery.joining(", ")),
   *     ParameterizedQuery.of("my-dataset"));
   * }</pre>
   */
  public static Collector<ParameterizedQuery, ?, ParameterizedQuery> joining(
      @CompileTimeConstant String delimiter) {
    return Collector.of(
        Builder::new,
        (b, q) -> b.appendDelimiter(delimiter).addSubQuery(q),
        (b1, b2) -> b1.appendDelimiter(delimiter).addSubQuery(b2.build()),
        Builder::build);
  }

  /**
   * Sends this query to BigQuery using the default client configuration with {@code options}
   * to control BigQuery jobs.
   *
   * <p>To use alternative configuration, pass the return value of {@link #jobConfiguration}
   * to the {@link com.google.cloud.bigquery.BigQuery} object of your choice.
   */
  public TableResult run(JobOption... options) throws JobException, InterruptedException {
    return BigQueryOptions.getDefaultInstance().getService().query(jobConfiguration(), options);
  }

  /** Returns the {@link QueryJobConfiguration} that can be sent to BigQuery. */
  @SuppressWarnings("CheckReturnValue") // addNamedParameter should use @CanIgnoreReturnValue
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
    private final Map<String, Object> originalValues = new HashMap<>();

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
    Builder addSubQuery(ParameterizedQuery subQuery) {
      queryText.append(subQuery.query);
      BiStream.from(subQuery.parameters)
          .forEachOrdered(
              (name, value) -> internalAddParameter(name, subQuery.originalValues.get(name), value));
      return this;
    }

    @CanIgnoreReturnValue
    Builder addParameter(String name, Object originalValue) {
      return internalAddParameter(name, originalValue, toQueryParameter(originalValue));
    }

    private Builder internalAddParameter(String name, Object originalValue, QueryParameterValue value) {
      Object oldValue = originalValues.put(name, originalValue);
      if (oldValue != null) {
        if (oldValue.equals(originalValue)) {
          return this; // consistent. Just do nothing
        }
        throw new IllegalArgumentException("Duplicate placeholder name: " + name);
      }
      parameters.put(name, value);
      return this;
    }

    ParameterizedQuery build() {
      return new ParameterizedQuery(queryText.toString(), parameters, originalValues);
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
}
