package com.google.mu.safesql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.Substring;

/**
 * An injection-safe parameterized SQL, constructed using compile-time enforced templates and can be
 * used to {@link #prepareStatement create} {@link java.sql.PreparedStatement}.
 *
 * <p>This class is intended to work with JDBC {@link Connection} and {@link PreparedStatement} API
 * with parameters set through the {@link PreparedStatement#setObject(int, Object) setObject()} method.
 * The main use case though, is to be able to compose subqueries and leaf-level parameters with a
 * consistent templating API.
 *
 * <p>For trivial parameterization, you can use:
 * <pre>{@code
 *   SafeSql sql = SafeSql.of(
 *       """
 *       select id from Employees
 *       where firstName = {first_name} and lastName = {last_name}
 *       """,
 *       firstName, lastName);
 *   try (var statement = sql.prepareStatement(connection),
 *       var resultSet = statement.executeQuery()) {
 *     ...
 *   }
 * }</pre>
 *
 * The code internally uses the JDBC {@code '?'} placeholder in the SQL text, and calls
 * {@link PreparedStatement#setObject(int, Object) PreparedStatement.setObject()} to
 * set all the parameter values for you so you don't have to keep track of the parameter
 * indices or risk forgetting to set a parameter value.
 *
 * <p>The templating engine uses compile-time checks to guard against accidental use of
 * untrusted strings in the SQL, ensuring that they can only be sent as parameters:
 * try to use a dynamically generated String as the SQL and you'll get a compilation error.
 * In addition, the same set of compile-time guardrails from the {@link StringFormat} class
 * are in effect to make sure that you don't pass {@code lastName} in the place of
 * {@code first_name}, for example.
 *
 * <p>That said, the main benefit of this library lies in flexible and dynamic query
 * composition. By composing smaller SafeSql objects that encapsulate subqueries,
 * you can parameterize by table name, by column names or by arbitrary sub-queries
 * that may be computed dynamically.
 *
 * <p>For example, the following code builds sql to query the Users table with flexible
 * number of columns and a flexible WHERE clause depending on the {@code UserCriteria}
 * object's state:
 *
 * <pre>{@code
 * import static com.google.mu.safesql.SafeSql.optionally;
 *
 *   class UserCriteria {
 *     Optional<String> userId();
 *     Optional<String> firstName();
 *     ...
 *   }
 *
 *   SafeSql queryUsers(UserCriteria criteria, @CompileTimeConstant String... columns) {
 *     SafeSql sql = SafeSql.of(
 *         "select {columns} from Users where {criteria}",
 *         SafeSql.listOf(columns).stream().collect(SafeSql.joining(", ")),
 *         Stream.of(
 *               optionally("id = {id}", criteria.userId()),
 *               optionally("firstName LIKE '%{first_name}%'", criteria.firstName()))
 *           .collect(SafeSql.and()));
 *   }
 *
 *   SafeSql usersQuery = queryUsers(userCriteria, "firstName", "lastName");
 * }</pre>
 *
 * If {@code UserCriteria} has specified {@code firstName()} but {@code userId()} is
 * unspecified (empty), the resulting SQL will look like:
 *
 * <pre>{@code
 * select firstName, lastName from Users where firstName LIKE ?
 * }</pre>
 *
 * And when you call {@code usersQuery.prepareStatement(connection)},
 * {@code statement.setObject(1, "%" + criteria.firstName().get() + "%")} will be called
 * to populate the PreparedStatement.
 *
 * <p>In contrast, {@link SafeQuery} directly escapes string parameters and is intended
 * to be used with SQL engines that don't have native support for parameterized queries.
 *
 * @since 8.2
 */
public final class SafeSql {
  private final String sql;
  private final List<?> paramValues;

  private SafeSql(String sql) {
    this(sql, emptyList());
  }

  private SafeSql(String sql, List<?> paramValues) {
    this.sql = sql;
    this.paramValues = paramValues;
  }

  /** An empty SQL */
  public static SafeSql EMPTY = new SafeSql("");

  /** Returns a SafeSql with compile-time {@code sql} text with no parameter. */
  @TemplateFormatMethod
  public static SafeSql of(@TemplateString @CompileTimeConstant String sql) {
    return new SafeSql(validate(sql));
  }

  /**
   * Convenience method when you need to create the {@link SafeSql} inline, with both the
   * query template and the arguments.
   *
   * <p>For example:
   *
   * <pre>{@code
   * PreparedStatement statement =
   *     SafeSql.of("select * from JOBS where id = {id}", jobId)
   *         .prepareStatement(connection);
   * }</pre>
   */
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  @TemplateFormatMethod
  public static SafeSql of(@TemplateString @CompileTimeConstant String query, Object... args) {
    return template(query).with(args);
  }

  /** Returns a SafeSql wrapping the name of {@code enumConstant}. */
  public static SafeSql of(Enum<?> enumConstant) {
    return new SafeSql(enumConstant.name());
  }

  /**
   * An optional query that's only rendered if {@code condition} is true; otherwise returns {@link
   * #EMPTY}. It's for use cases where a subquery is only conditionally added, for example the
   * following query will only include the userEmail column under super user mode:
   *
   * <pre>{@code
   * SafeSql query = SafeSql.of(
   *     "SELECT job_id, start_timestamp {user_email} FROM jobs",
   *     SafeSql.when(isSuperUser, ", user_email"));
   * }</pre>
   */
  @TemplateFormatMethod
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public static SafeSql when(
      boolean condition, @TemplateString @CompileTimeConstant String query, Object... args) {
    checkNotNull(query);
    checkNotNull(args);
    return condition ? of(query, args) : EMPTY;
  }

  /**
   * An optional query that's only rendered if {@code arg} is present; otherwise returns {@link
   * #EMPTY}. It's for use cases where a subquery is only added when present, for example the
   * following query will add the WHERE clause if the filter is present:
   *
   * <pre>{@code
   * SafeSql query = SafeSql.of(
   *     "SELECT * FROM jobs {where}",
   *     SafeSql.optionally("WHERE {filter}", getOptionalWhereClause()));
   * }</pre>
   */
  @TemplateFormatMethod
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public static SafeSql optionally(
      @TemplateString @CompileTimeConstant String query, Optional<?> arg) {
    checkNotNull(query);
    return arg.map(v -> of(query, v)).orElse(EMPTY);
  }

  /** Wraps the compile-time string constants as SafeSql objects. */
  public static ImmutableList<SafeSql> listOf(@CompileTimeConstant String... texts) {
    return Arrays.stream(texts).map(t -> new SafeSql(validate(t))).collect(toImmutableList());
  }


  /**
   * Returns a template of {@link SafeSql} based on the {@code template} string.
   *
   * <p>For example:
   *
   * <pre>{@code
   * private static final Template<SafeSql> GET_JOB_IDS_BY_QUERY =
   *     SafeSql.template(
   *         """
   *         SELECT job_id from Jobs
   *         WHERE query LIKE '%{keyword}%'
   *         """);
   *
   * PreparedStatement stmt = GET_JOB_IDS_BY_QUERY.with("sensitive word").prepareStatement(conn);
   * }</pre>
   *
   * <p>Except {@link SafeSql} itself, which are directly substituted into the query, all
   * other placeholder arguments are passed into the PreparedStatement as query parameters.
   */
  public static Template<SafeSql> template(@CompileTimeConstant String template) {
    return StringFormat.template(template, (fragments, placeholders) -> {
      Iterator<String> it = fragments.iterator();
      class SqlComposer {
        private final Builder builder = new Builder();
        private String next = it.next();

        SafeSql composeSql() {
          placeholders.forEachOrdered(this::composeForPlaceholder);
          builder.appendSql(next);
          checkState(!it.hasNext());
          return builder.build();
        }

        private void composeForPlaceholder(Substring.Match placeholder, Object value) {
          String paramName = placeholder.skip(1, 1).toString().trim();
          if (value instanceof SafeSql) {
            validate(paramName);
            builder.appendSql(nextFragment()).addSubQuery((SafeSql) value);
            return;
          }
          checkArgument(!(value instanceof SafeQuery), "Don't mix SafeQuery with SafeSql.");
          checkArgument(
              !(value instanceof Optional),
              "Optional parameter not supported. Consider using SafeSql.optionally() or SafeSql.when()?");
          if (appendBeforeQuotedPlaceholder("'%", placeholder, "%'", value)) {
            builder.addParameter(paramName, "%" + escapePercent((String) value) + "%");
          } else if (appendBeforeQuotedPlaceholder("'%", placeholder, "'", value)) {
            builder.addParameter(paramName, "%" + escapePercent((String) value));
          } else if (appendBeforeQuotedPlaceholder("'", placeholder, "%'", value)) {
            builder.addParameter(paramName, escapePercent((String) value) + "%");
          } else if (appendBeforeQuotedPlaceholder("'", placeholder, "'", value)) {
            builder.addParameter(paramName, value);
          } else {
            builder.appendSql(nextFragment());
            builder.addParameter(paramName, value);
          }
        }

        private boolean appendBeforeQuotedPlaceholder(
            String open, Substring.Match placeholder, String close, Object value) {
          boolean quoted = placeholder.isImmediatelyBetween(open, close);
          if (quoted) {
            checkArgument(
                value instanceof String, "Placeholder %s%s%s must be String", open, placeholder, close);
            builder.appendSql(suffix(open).removeFrom(nextFragment()));
            next = prefix(close).removeFrom(next);
          }
          return quoted;
        }

        private String nextFragment() {
          String fragment = next;
          next = it.next();
          return fragment;
        }
      }
      return new SqlComposer().composeSql();
    });
  }

  /**
   * A collector that joins boolean query snippets using {@code AND} operator. The
   * AND'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "TRUE".
   *
   * <p>Empty SafeSql elements are ignored and not joined.
   */
  public static Collector<SafeSql, ?, SafeSql> and() {
    return collectingAndThen(
        nonEmptyQueries(mapping(SafeSql::parenthesized, joining(" AND "))),
        query -> query.sql.isEmpty() ? of("1 = 1") : query);
  }

  /**
   * A collector that joins boolean query snippets using {@code OR} operator. The
   * OR'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "FALSE".
   *
   * <p>Empty SafeSql elements are ignored and not joined.
   */
  public static Collector<SafeSql, ?, SafeSql> or() {
    return collectingAndThen(
        nonEmptyQueries(mapping(SafeSql::parenthesized, joining(" OR "))),
        query -> query.sql.isEmpty() ? of("1 = 0") : query);
  }

  /**
   * Returns a collector that joins SafeSql elements using {@code delimiter}.
   *
   * <p>Useful if you need to parameterize by a set of columns to select. Say, you might need to
   * query the table names only, or read the project, dataset and table names:
   *
   * <pre>{@code
   * private static final Template<SafeSql> QUERY_TABLES =
   *     SafeSql.template("SELECT {columns} FROM {schema}.INFORMATION_SCHEMA.TABLES");
   *
   * SafeSql getTableNames = QUERY_TABLES.with(SafeSql.of("table_name"));
   * SafeSql getFullyQualified = QUERY_TABLES.with(
   *     SafeSql.listOf("table_catalog", "table_schema", "table_name")
   *         .stream()
   *         .collect(SafeSql.joining(", ")),
   *     SafeSql.of("my-schema"));
   * }</pre>
   *
   * <p>Empty SafeSql elements are ignored and not joined.
   */
  public static Collector<SafeSql, ?, SafeSql> joining(@CompileTimeConstant String delimiter) {
    validate(delimiter);
    return nonEmptyQueries(
        Collector.of(
            Builder::new,
            (b, q) -> b.appendDelimiter(delimiter).addSubQuery(q),
            (b1, b2) -> b1.appendDelimiter(delimiter).addSubQuery(b2.build()),
            Builder::build));
  }

  /**
   * Returns a {@link PreparedStatement} with the encapsulated sql and parameters.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   */
  @MustBeClosed
  public PreparedStatement prepareStatement(Connection connection) {
    try {
      return setArgs(connection.prepareStatement(sql));
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Returns a {@link CallableStatement} with the encapsulated sql and parameters.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   */
  @MustBeClosed
  public CallableStatement prepareCall(Connection connection) {
    try {
      return setArgs(connection.prepareCall(sql));
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Returns the parameter values in the order they occur in the SQL.
   * They are used by methods like {@link #prepareStatement} and {@link #prepareCall}
   * to create and populate the returned {@link PreparedStatement}
   */
  public List<?> getParameters() {
    return paramValues;
  }

  /**
   * Returns the SQL text with {@code '?'} as the placeholders.
   * It's used by methods like {@link #prepareStatement} and {@link #prepareCall}
   * to create and populate the returned {@link PreparedStatement}.
   */
  @Override
  public String toString() {
    return sql;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sql, paramValues);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SafeSql) {
      SafeSql that = (SafeSql) obj;
      return sql.equals(that.sql)
          && paramValues.equals(that.paramValues);
    }
    return false;
  }

  private <S extends PreparedStatement> S setArgs(S statement) throws SQLException {
    for (int i = 0; i < paramValues.size(); i++) {
      statement.setObject(i + 1, paramValues.get(i));
    }
    return statement;
  }

  private static String validate(String sql) {
    checkArgument(sql.indexOf('?') < 0, "please use named {placeholder} instead of '?'");
    return sql;
  }

  private SafeSql parenthesized() {
    return new SafeSql("(" + sql + ")", paramValues);
  }

  private static String escapePercent(String s) {
    return Substring.first(c -> c == '\\' || c == '%').repeatedly().replaceAllFrom(s, c -> "\\" + c);
  }

  private static <R> Collector<SafeSql, ?, R> nonEmptyQueries(
      Collector<SafeSql, ?, R> downstream) {
    return filtering(q -> !q.sql.isEmpty(), downstream);
  }

  // Not in Java 8
  private static <T, A, R> Collector<T, A, R> filtering(
      Predicate<? super T> filter, Collector<? super T, A, R> collector) {
    BiConsumer<A, ? super T> accumulator = collector.accumulator();
    return Collector.of(
        collector.supplier(),
        (a, input) -> {if (filter.test(input)) {accumulator.accept(a, input);}},
        collector.combiner(),
        collector.finisher(),
        collector.characteristics().toArray(new Characteristics[0]));
  }

  private static final class Builder {
    private final StringBuilder queryText = new StringBuilder();
    private final List<Object> paramValues = new ArrayList<>();

    Builder appendSql(String snippet) {
      queryText.append(validate(snippet));
      return this;
    }

    Builder addParameter(String name, Object value) {
      validate(name);
      queryText.append("?");
      paramValues.add(value);
      return this;
    }

    Builder addSubQuery(SafeSql subQuery) {
      queryText.append(subQuery.sql);
      paramValues.addAll(subQuery.getParameters());
      return this;
    }

    Builder appendDelimiter(String delim) {
      if (queryText.length() > 0) {
        queryText.append(delim);
      }
      return this;
    }

    SafeSql build() {
      return new SafeSql(queryText.toString(), unmodifiableList(new ArrayList<>(paramValues)));
    }
  }
}
