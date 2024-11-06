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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static com.google.mu.safesql.InternalCollectors.skippingEmpty;
import static com.google.mu.safesql.SafeQuery.checkIdentifier;
import static com.google.mu.safesql.SafeQuery.validatePlaceholder;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;

/**
 * An injection-safe dynamic SQL, constructed using compile-time enforced templates and can be
 * used to {@link #prepareStatement create} {@link java.sql.PreparedStatement}.
 *
 * <p>This class is intended to work with JDBC {@link Connection} API with parameters set through
 * the {@link PreparedStatement#setObject(int, Object) setObject()} method.
 * The main use case though, is to be able to compose subqueries and leaf-level parameters with an
 * intuitive templating API.
 *
 * <dl><dt><STRONG>The {@code IN} Operator</STRONG></dt></dl>
 *
 * A common dynamic SQL use case is to use the {@code IN} SQL operator:
 *
 * <pre>{@code
 *   SafeSql sql = SafeSql.of(
 *       """
 *       SELECT id FROM Users
 *       WHERE firstName = {first_name} AND lastName IN ({last_names})
 *       """,
 *       firstName, lastNamesList);
 *   List<Long> ids = sql.query(connection, row -> row.getLong("id"));
 * }</pre>
 *
 * In the above example if {@code firstName} is "Emma" and {@code lastNamesList} is
 * {@code ["Watson", "Lin"]}, the generated SQL will be: <pre>{@code
 *   SELECT id FROM Employees
 *   WHERE firstName = ? AND lastName IN (?, ?)
 * }</pre>
 *
 * And the parameters will be set as: <pre>{@code
 *   statement.setObject(1, "Emma");
 *   statement.setObject(2, "Watson");
 *   statement.setObject(3, "Lin");
 * }</pre>
 *
 * <p>The templating engine uses compile-time checks to guard against accidental use of
 * untrusted strings in the SQL, ensuring that they can only be sent as parameters:
 * try to use a dynamically generated String as the SQL template and you'll get a compilation error.
 * In addition, the same set of compile-time guardrails from the {@link StringFormat} class
 * are in effect to make sure that you don't pass {@code lastName} in the place of
 * {@code first_name}, for example.
 *
 * <dl><dt><STRONG>Conditional Subqueries</STRONG></dt></dl>
 *
 * By composing SafeSql objects that encapsulate subqueries, you can also parameterize by
 * arbitrary sub-queries that are computed dynamically.
 *
 * <p>For example, the following code builds SQL to query the Users table with flexible
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
 *         "SELECT `{columns}` FROM Users WHERE {criteria}",
 *         asList(columns),
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
 * SELECT `firstName`, `lastName` FROM Users WHERE firstName LIKE ?
 * }</pre>
 *
 * <p>And when you call {@code usersQuery.prepareStatement(connection)} or one of the similar
 * convenience methods, {@code statement.setObject(1, "%" + criteria.firstName().get() + "%")}
 * will be called to populate the PreparedStatement.
 *
 * <dl><dt><STRONG>Parameterize by Column Names or Table Names</STRONG></dt></dl>
 *
 * Sometimes you may wish to parameterize by table names, column names etc.
 * for which JDBC has no support.
 *
 * If the identifiers can come from compile-time literals, you can wrap them using
 * {@code SafeSql.of(COLUMN_NAME)}, which can then be composed as subqueries.
 *
 * <p>But what if the identifier string is loaded from a resource file, or is specified by a
 * request field? Passing the string directly as a template parameter will only generate the JDBC
 * "?" in its place, not what's needed; {@code SafeSql.of(theString)} will fail to compile
 * because such strings are inherently dynamic and untrusted.
 *
 * <p>The safe way to parameterize dynamic strings as identifiers is to backtick-quote their
 * placeholders in the SQL template. For example: <pre>{@code
 *   SafeSql.of("SELECT `{columns}` FROM Users", request.getColumns())
 * }</pre>
 * The backticks tell SafeSql that the string is supposed to be an identifier (or a list of
 * identifiers). SafeSql will sanity-check the string(s) to make sure injection isn't possible.
 *
 * <p>In the above example, if {@code getColumns()} returns {@code ["id", "age"]}, the genereated
 * SQL will be {@code SELECT `id`, `age` FROM Users}. That is, each individual string will
 * be backtick-quoted and then joined by ", ".
 *
 * <dl><dt><STRONG>The {@code LIKE} Operator</STRONG></dt></dl>
 *
 * <p>Note that with straight JDBC, if you try to use the LIKE operator to match a user-provided
 * substring, i.e. using {@code LIKE '%foo%'} to search for "foo", this seemingly intuitive
 * syntax is actually incorect: <pre>{@code
 *   String searchBy = ...;
 *   PreparedStatement statement =
 *       connection.prepareStatement("SELECT id FROM Users WHERE firstName LIKE '%?%'");
 *   statement.setString(1, searchBy);
 * }</pre>
 *
 * JDBC considers the quoted question mark as a literal so the {@code setString()}
 * call will fail. You'll need to use the following workaround: <pre>{@code
 *   PreparedStatement statement =
 *       connection.prepareStatement("SELECT id FROM Users WHERE firstName LIKE ?");
 *   statement.setString(1, "%" + searchBy + "%");
 * }</pre>
 *
 * And even then, if the {@code searchTerm} includes special characters like '%' or backslash ('\'),
 * they'll be interepreted as wildcards and escape characters, opening it up to a form of minor
 * SQL injection despite already using the parameterized SQL.
 *
 * <p>The SafeSql template protects you from this caveat. The most intuitive syntax does exactly
 * what you'd expect (and it escapes special characters too): <pre>{@code
 *   String searchBy = ...;
 *   SafeSql sql = SafeSql.of(
 *       "SELECT id FROM Users WHERE firstName LIKE '%{search_term}%'", searchTerm);
 *   List<Long> ids = sql.query(connection, row -> row.getLong("id"));
 * }</pre>
 *
 * <dl><dt><STRONG>Quote String Placeholders</STRONG></dt></dl>
 *
 * Even when you don't use the {@code LIKE} operator or the percent sign (%), it may still be
 * more readable to quote the string parameters just so the SQL template explicitly tells readers
 * that the parameter is a string. The following template works with or without the quotes:
 *
 * <pre>{@code
 *   // Reads more clearly that the {id} is a string
 *   SafeSql sql = SafeSql.of("SELECT * FROM Users WHERE id = '{id}'", userId);
 * }</pre>
 *
 * <dl><dt><STRONG>Enforce Identical Parameter</STRONG></dt></dl>
 *
 * <p>The compile-time check tries to be helpful and checks that if you use the
 * same parameter name more than once in the template, the same value must be used for it.
 *
 * So for example, if you are trying to generate a SQL that looks like: <pre>{@code
 *   SELECT u.firstName, p.profileId
 *   FROM (SELECT firstName FROM Users WHERE id = 'foo') u,
 *        (SELECT profileId FROM Profiles WHERE userId = 'foo') p
 * }</pre>
 *
 * It'll be important to use the same user id for both subqueries. And you can use the following
 * template to make sure of it at compile time: <pre>{@code
 *   SafeSql sql = SafeSql.of(
 *       """
 *       SELECT u.firstName, p.profileId
 *       FROM (SELECT firstName FROM Users WHERE id = '{user_id}') u,
 *            (SELECT profileId FROM Profiles WHERE userId = '{user_id}') p
 *       """,
 *       userId, userId);
 * }</pre>
 *
 * If someone mistakenly passes in inconsistent ids, they'll get a compilation error.
 *
 * <p>Immutable if the template parameters you pass to it are immutable.
 *
 * <p>This class serves a different purpose than {@link SafeQuery}. The latter is to directly escape
 * string parameters when the SQL backend has no native support for parameterized queries.
 *
 * @since 8.2
 */
public final class SafeSql {
  private static final Logger logger = Logger.getLogger(SafeSql.class.getName());

  /** An empty SQL */
  public static final SafeSql EMPTY = new SafeSql("");
  private static final SafeSql FALSE = new SafeSql("(1 = 0)");
  private static final SafeSql TRUE = new SafeSql("(1 = 1)");
  private static final StringFormat.Template<SafeSql> PARAM = template("{param}");
  private static final StringFormat PLACEHOLDER_ELEMENT_NAME =
      new StringFormat("{placeholder}[{index}]");
  private static final Substring.RepeatingPattern TOKENS =
      Substring.first(Pattern.compile("(\\w+)|(\\S)")).repeatedly();

  private final String sql;
  private final List<?> paramValues;

  private SafeSql(String sql) {
    this(sql, emptyList());
  }

  private SafeSql(String sql, List<?> paramValues) {
    this.sql = sql;
    this.paramValues = paramValues;
  }

  /**
   * Returns {@link SafeSql} using {@code template} and {@code params}.
   *
   * <p>For example:
   *
   * <pre>{@code
   * List<Long> jobIds = SafeSql.of(
   *         "SELECT id FROM Jobs WHERE timestamp BETWEEN {start} AND {end}",
   *         startTime, endTime)
   *     .query(connection, row -> row.getLong("id"));
   * }</pre>
   *
   * <p>Note that if you plan to create a {@link PreparedStatement} and use it multiple times
   * with different sets of parameters, it's more efficient to use {@link #prepareToQuery
   * prepareToQuery()} or {@link #prepareToUpdate prepareToUpdate()}, which will reuse the same
   * PreparedStatement for multiple calls. The returned {@link Template}s are protected at
   * compile-time against incorrect varargs.
   *
   * @param template the sql template
   * @param params The template parameters. Parameters that are themselves {@link SafeSql} are
   * considered trusted subqueries and are appended directly. Other types are passed through JDBC
   * {@link PreparedStatement#setObject}, with one exception: when the corresponding placeholder
   * is quoted by backticks like {@code `{columns}`}, its string parameter value
   * (or {@code Iterable<String>} parameter value) are directly appended (quotes, backticks,
   * backslash and other special characters are disallowed).
   * This makes it easy to parameterize by table names, column names etc.
   */
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  @TemplateFormatMethod
  public static SafeSql of(@TemplateString @CompileTimeConstant String template, Object... params) {
    return template(template).with(params);
  }

  /**
   * Wraps non-negative {@code number} as a SafeSql object.
   *
   * <p>For example, the following SQL Server query allows parameterization by the TOP n number:
   * <pre>{@code
   *   SafeSql.of("SELECT TOP {page_size} UserId FROM Users", nonNegative(pageSize))
   * }</pre>
   *
   * <p>This is needed because in SQL Server the TOP number can't be parameterized by JDBC.
   */
  public static SafeSql nonNegative(long number) {
    checkArgument(number >= 0, "negative number disallowed: %s", number);
    return new SafeSql(Long.toString(number));
  }

  /**
   * An optional query that's only rendered if {@code param} is present; otherwise returns {@link
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
      @TemplateString @CompileTimeConstant String query, Optional<?> param) {
    checkNotNull(query);
    return param.map(v -> of(query, v)).orElse(EMPTY);
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
   *
   * @param condition the guard condition to determine if {@code template} should be renderd
   * @param template the template to render if {@code condition} is true
   * @param params see {@link #of(String, Object...)} for discussion on the template arguments
   */
  @TemplateFormatMethod
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public static SafeSql when(
      boolean condition, @TemplateString @CompileTimeConstant String template, Object... params) {
    checkNotNull(template);
    checkNotNull(params);
    return condition ? of(template, params) : EMPTY;
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
   *         SELECT JobId FROM Jobs
   *         WHERE query LIKE '%{keyword}%'
   *         """);
   *
   * List<String> sensitiveJobIds = GET_JOB_IDS_BY_QUERY.with("sensitive word")
   *     .query(connection, row -> row.getString("JobId"));
   * }</pre>
   *
   * <p>See {@link #of(String, Object...)} for discussion on the template arguments.
   *
   * <p>The returned template is immutable and thread safe.
   */
  public static Template<SafeSql> template(@CompileTimeConstant String template) {
    return StringFormat.template(template, (fragments, placeholders) -> {
      Deque<String> texts = new ArrayDeque<>(fragments);
      Builder builder = new Builder();
      class SqlWriter {
        void writePlaceholder(Substring.Match placeholder, Object value) {
          validatePlaceholder(placeholder);
          String paramName = validate(placeholder.skip(1, 1).toString().trim());
          checkArgument(
              !(value instanceof SafeQuery),
              "%s: don't mix in SafeQuery with SafeSql.", placeholder);
          checkArgument(
              !(value instanceof Optional),
              "%s: optional parameter not supported." +
              " Consider using SafeSql.optionally() or SafeSql.when()?",
              placeholder);
          if (value instanceof Iterable) {
            Iterator<?> elements = ((Iterable<?>) value).iterator();
            checkArgument(elements.hasNext(), "%s cannot be empty list", placeholder);
            if (placeholder.isImmediatelyBetween("'", "'")
                && matchesPattern("IN ('", placeholder, "')")
                && appendBeforeQuotedPlaceholder("'", placeholder, "'")) {
              builder.addSubQuery(
                  eachPlaceholderValue(placeholder, elements)
                      .mapToObj(SafeSql::mustBeString)
                      .map(PARAM::with)
                      .collect(joining(", ")));
              return;
            }
            builder.appendSql(texts.pop());
            if (placeholder.isImmediatelyBetween("`", "`")) {
              builder.appendSql(
                  eachPlaceholderValue(placeholder, elements)
                      .mapToObj(SafeSql::mustBeIdentifier)
                      .collect(Collectors.joining("`, `")));
            } else if (matchesPattern("IN (", placeholder, ")")) {
              builder.addSubQuery(
                  eachPlaceholderValue(placeholder, elements)
                      .mapToObj(SafeSql::subqueryOrParameter)
                      .collect(joining(", ")));
            } else {
              builder.addSubQuery(
                  eachPlaceholderValue(placeholder, elements)
                      .mapToObj(SafeSql::mustBeSubquery)
                      .collect(joining(", ")));
              validateSubqueryPlaceholder(placeholder);
            }
          } else if (value instanceof SafeSql) {
            builder.appendSql(texts.pop()).addSubQuery((SafeSql) value);
            validateSubqueryPlaceholder(placeholder);
          } else if (appendBeforeQuotedPlaceholder("`", placeholder, "`")) {
            String identifier = mustBeIdentifier("`" + placeholder + "`", value);
            checkArgument(identifier.length() > 0, "`%s` cannot be empty", placeholder);
            builder.appendSql("`" + identifier + "`");
          } else if (appendBeforeQuotedPlaceholder("'%", placeholder, "%'")) {
            builder.addParameter(
                paramName, "%" + escapePercent(mustBeString(placeholder, value)) + "%");
          } else if (appendBeforeQuotedPlaceholder("'%", placeholder, "'")) {
            builder.addParameter(paramName, "%" + escapePercent(mustBeString(placeholder, value)));
          } else if (appendBeforeQuotedPlaceholder("'", placeholder, "%'")) {
            builder.addParameter(paramName, escapePercent(mustBeString(placeholder, value)) + "%");
          } else if (appendBeforeQuotedPlaceholder("'", placeholder, "'")) {
            builder.addParameter(paramName, mustBeString("'" + placeholder + "'", value));
          } else {
            checkMissingPlaceholderQuotes(placeholder);
            builder.appendSql(texts.pop()).addParameter(paramName, value);
          }
        }

        private boolean appendBeforeQuotedPlaceholder(
            String open, Substring.Match placeholder, String close) {
          boolean quoted = placeholder.isImmediatelyBetween(open, close);
          if (quoted) {
            builder.appendSql(suffix(open).removeFrom(texts.pop()));
            texts.push(prefix(close).removeFrom(texts.pop()));
          }
          return quoted;
        }
      }
      placeholders.forEachOrdered(new SqlWriter()::writePlaceholder);
      builder.appendSql(texts.pop());
      checkState(texts.isEmpty());
      return builder.build();
    });
  }

  /**
   * A collector that joins boolean query snippet using {@code AND} operator. The
   * AND'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "(1 = 1)".
   *
   * <p>Empty SafeSql elements are ignored and not joined.
   */
  public static Collector<SafeSql, ?, SafeSql> and() {
    return collectingAndThen(
        skippingEmpty(mapping(SafeSql::parenthesized, joining(" AND "))),
        query -> query.sql.isEmpty() ? TRUE : query);
  }

  /**
   * A collector that joins boolean query snippet using {@code OR} operator. The
   * OR'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "(1 = 0)".
   *
   * <p>Empty SafeSql elements are ignored and not joined.
   */
  public static Collector<SafeSql, ?, SafeSql> or() {
    return collectingAndThen(
        skippingEmpty(mapping(SafeSql::parenthesized, joining(" OR "))),
        query -> query.sql.isEmpty() ? FALSE : query);
  }

  /**
   * Returns a collector that joins SafeSql elements using {@code delimiter}.
   *
   * <p>Empty SafeSql elements are ignored and not joined.
   */
  public static Collector<SafeSql, ?, SafeSql> joining(@CompileTimeConstant String delimiter) {
    validate(delimiter);
    return skippingEmpty(
        Collector.of(
            Builder::new,
            (b, q) -> b.appendDelimiter(delimiter).addSubQuery(q),
            (b1, b2) -> b1.appendDelimiter(delimiter).addSubQuery(b2.build()),
            Builder::build));
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection}. The {@link ResultSet}
   * will be iterated through, transformed by {@code rowMapper} and finally closed before returning.
   *
   * <p>For example: <pre>{@code
   * List<Long> ids = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(connection, row -> row.getLong("id"));
   * }</pre>
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   */
  public <T> List<T> query(
      Connection connection, SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    checkNotNull(rowMapper);
    try {
      try (PreparedStatement stmt = prepareStatement(connection);
          ResultSet resultSet = stmt.executeQuery()) {
        return mapResults(resultSet, rowMapper);
      }
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Executes the encapsulated DML against {@code connection} and returns the number of affected
   * rows.
   *
   * <p>For example: <pre>{@code
   * SafeSql.of("INSERT INTO Users(id, name) VALUES({id}, '{name}')", id, name)
   *     .update(connection);
   * }</pre>
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   */
  public int update(Connection connection) {
    try {
      try (PreparedStatement stmt = prepareStatement(connection)) {
        return stmt.executeUpdate();
      }
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
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
   * Returns a query template that will reuse the same cached {@code PreparedStatement}
   * for repeated calls of {@link Template#with} using different parameters.
   *
   * <p>Allows callers to take advantage of the performance benefit of PreparedStatement
   * without having to re-create the statement for each call. For example: <pre>{@code
   *   try (var connection = ...) {
   *     var queryByName = SafeSql.prepareToQuery(
   *         connection, "SELECT id FROM Users WHERE name LIKE '%{name}%'",
   *         row -> row.getLong("id"));
   *     for (String name : names) {
   *       for (long id : queryByName.with(name))) {
   *         ...
   *       }
   *     }
   *   }
   * }</pre>
   *
   * <p>The returned Template is <em>not</em> thread safe.
   *
   * <p>The caller is expected to close the {@code connection} after done, which will close the
   * cached PreparedStatement.
   */
  public static <T> Template<List<T>> prepareToQuery(
      Connection connection, @CompileTimeConstant String template,
      SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    checkNotNull(rowMapper);
    return prepare(connection, template, stmt -> {
      try (ResultSet resultSet = stmt.executeQuery()) {
        return mapResults(resultSet, rowMapper);
      }
    });
  }

  /**
   * Returns a DML template that will reuse the same cached {@code PreparedStatement}
   * for repeated calls of {@link Template#with} using different parameters.
   *
   * <p>Allows callers to take advantage of the performance benefit of PreparedStatement
   * without having to re-create the statement for each call. For example: <pre>{@code
   *   try (var connection = ...) {
   *     var insertUser = SafeSql.prepareToUpdate(
   *         connection, "INSERT INTO Users(id, name) VALUES({id}, '{name}')");
   *     int totalRowsAffected = insertUser.with(1, "Tom") + insertUser.with(2, "Emma");
   *   }
   * }</pre>
   *
   * <p>The returned Template is <em>not</em> thread safe.
   *
   * <p>The caller is expected to close the {@code connection} after done, which will close the
   * cached PreparedStatement.
   */
  public static Template<Integer> prepareToUpdate(
      Connection connection, @CompileTimeConstant String template) {
    return prepare(connection, template, PreparedStatement::executeUpdate);
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
    return sql.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SafeSql) {
      SafeSql that = (SafeSql) obj;
      return sql.equals(that.sql) && paramValues.equals(that.paramValues);
    }
    return false;
  }

  private SafeSql parenthesized() {
    return new SafeSql('(' + sql + ')', paramValues);
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

  private static void checkMissingPlaceholderQuotes(Substring.Match placeholder) {
    rejectHalfQuotes(placeholder, "'");
    rejectHalfQuotes(placeholder, "`");
    rejectHalfQuotes(placeholder, "\"");
  }

  private static void rejectHalfQuotes(Substring.Match placeholder, String quote) {
    checkArgument(
        !placeholder.isPrecededBy(quote), "half quoted placeholder: %s%s", quote, placeholder);
    checkArgument(
        !placeholder.isFollowedBy(quote), "half quoted placeholder: %s%s", placeholder, quote);
  }

  private static void validateSubqueryPlaceholder(Substring.Match placeholder) {
    checkArgument(
        !placeholder.isImmediatelyBetween("'", "'"),
        "SafeSql should not be quoted: '%s'", placeholder);
    checkArgument(
        !placeholder.isImmediatelyBetween("\"", "\""),
        "SafeSql should not be quoted: \"%s\"", placeholder);
    checkArgument(
        !placeholder.isImmediatelyBetween("`", "`"),
        "SafeSql should not be backtick quoted: `%s`", placeholder);
    checkMissingPlaceholderQuotes(placeholder);
  }

  private static String mustBeIdentifier(CharSequence name, Object element) {
    checkArgument(element != null, "%s expected to be an identifier, but is null", name);
    checkArgument(
        element instanceof String || element instanceof Enum,
        "%s expected to be String, but is %s", name, element.getClass());
    return checkIdentifier(name, element.toString());
  }

  private static String mustBeString(CharSequence name, Object element) {
    checkArgument(element != null, "%s expected to be String, but is null", name);
    checkArgument(
        element instanceof String,
        "%s expected to be String, but is %s", name, element.getClass());
    return (String) element;
  }

  private static SafeSql mustBeSubquery(CharSequence name, Object element) {
    checkArgument(element != null, "%s expected to be SafeSql, but is null", name);
    checkArgument(
        element instanceof SafeSql,
        "%s expected to be SafeSql, but is %s", name, element.getClass());
    return (SafeSql) element;
  }

  private static SafeSql subqueryOrParameter(CharSequence name, Object param) {
    checkArgument(param != null, "%s must not be null", name);
    return param instanceof SafeSql ? (SafeSql) param : PARAM.with(param);
  }

  private static BiStream<String, ?> eachPlaceholderValue(
      Substring.Match placeholder, Iterator<?> elements) {
    return BiStream.zip(indexesFrom(0), stream(elements))
        .mapKeys(index -> PLACEHOLDER_ELEMENT_NAME.format(placeholder, index));
  }

  @VisibleForTesting
  static boolean matchesPattern(String left, Substring.Match placeholder, String right) {
    ImmutableList<String> leftTokensToMatch =
        TOKENS.from(Ascii.toUpperCase(left)).collect(toImmutableList());
    ImmutableList<String> rightTokensToMatch =
        TOKENS.from(Ascii.toUpperCase(right)).collect(toImmutableList());
    // Matches right side first because it's lazy and more efficient
    return BiStream.zip(
                rightTokensToMatch.stream(),
                TOKENS.from(Ascii.toUpperCase(placeholder.after())))
            .filter(String::equals)
            .count() == rightTokensToMatch.size()
        && BiStream.zip(
                leftTokensToMatch.reverse(),
                TOKENS.from(Ascii.toUpperCase(placeholder.before()))
                    .collect(toImmutableList()).reverse())
            .filter(String::equals)
            .count() == leftTokensToMatch.size();
  }

  private static String escapePercent(String s) {
    return first(c -> c == '\\' || c == '%').repeatedly().replaceAllFrom(s, c -> "\\" + c);
  }

  private static <T> Template<T> prepare(
      Connection connection, @CompileTimeConstant String template,
      SqlFunction<? super PreparedStatement, ? extends T> action) {
    checkNotNull(connection);
    Template<SafeSql> sqlTemplate = template(template);
    return new Template<T>() {
      private PreparedStatement statement;
      private String cachedSql;

      @SuppressWarnings("StringFormatArgsCheck")  // The returned is also a Template<>
      @Override public T with(Object... params) {
        SafeSql sql = sqlTemplate.with(params);
        try {
          if (statement == null) {
            statement = connection.prepareStatement(sql.toString());
          } else if (!sql.toString().equals(cachedSql)) {
            logger.warning(
                "cached PreparedStatement invalided due to sql change from:\n  "
                    + cachedSql + "\nto:\n  " + sql);
            statement = connection.prepareStatement(sql.toString());
          }
          cachedSql = sql.toString();
          return action.apply(sql.setArgs(statement));
        } catch (SQLException e) {
          throw new UncheckedSqlException(e);
        }
      }

      @Override
      public String toString() {
        return sqlTemplate.toString();
      }
    };
  }

  private static <T> List<T> mapResults(
      ResultSet resultSet, SqlFunction<? super ResultSet, ? extends T> mapper) throws SQLException {
    List<T> values = new ArrayList<>();
    while (resultSet.next()) {
      values.add(mapper.apply(resultSet));
    }
    return unmodifiableList(values);
  }

  private static final class Builder {
    private final StringBuilder queryText = new StringBuilder();
    private final List<Object> paramValues = new ArrayList<>();

    Builder appendSql(String snippet) {
      queryText.append(validate(snippet));
      return this;
    }

    Builder addParameter(String name, Object value) {
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
