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
import static com.google.common.collect.Streams.stream;
import static com.google.mu.safesql.InternalCollectors.skippingEmpty;
import static com.google.mu.safesql.SafeQuery.checkIdentifier;
import static com.google.mu.safesql.SafeQuery.validatePlaceholder;
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
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;

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
 *         "select `{columns}` from Users where {criteria}",
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
 * select `firstName`, `lastName` from Users where firstName LIKE ?
 * }</pre>
 *
 * And when you call {@code usersQuery.prepareStatement(connection)},
 * {@code statement.setObject(1, "%" + criteria.firstName().get() + "%")} will be called
 * to populate the PreparedStatement.
 *
 * <p>Sometimes you may wish to parameterize by table names, column names etc.
 * for which JDBC has no support.
 *
 * If the identifiers can come from compile-time literals or enum values, prefer to wrap
 * them using {@code SafeSql.of(identifier)} which can then be composed as subqueries.
 *
 * <p>But what if the identifier string is loaded from a resource file, or is specified by a
 * request field?
 *
 * While such strings are inherently dynamic and untrusted, you can still parameterize them
 * if you backtick-quote the placeholder in the SQL template. For example: <pre>{@code
 *   SafeSql.of("select `{columns}` from Users", request.getColumns())
 * }</pre>
 * The backticks tell SafeSql that the string is supposed to be an identifier (or a list of
 * identifiers). SafeSql will sanity-check the string(s) to make sure injection isn't possible.
 *
 * In the above example, if {@code getColumns()} returns {@code ["id", "age"]}, the genereated
 * SQL will be {@code select `id`, `age` from Users}. That is, each individual string will
 * be backtick-quoted and then joined by ", ".
 *
 * <p>Note that with straight JDBC, if you try to use the LIKE operator to match a user-provided
 * substring, i.e. using {@code LIKE '%foo%'} to search for "foo", this seemingly intuitive
 * syntax is actually incorect: <pre>{@code
 *   String searchBy = ...;
 *   PreparedStatement statement =
 *       connection.prepareStatement("select * from Users where firstName LIKE '%?%'");
 *   statement.setString(1, searchBy);
 * }</pre>
 *
 * JDBC considers the quoted question mark as a literal so the {@code setString()}
 * call will fail. You'll need to use the following workaround: <pre>{@code
 *   PreparedStatement statement =
 *       connection.prepareStatement("select * from Users where firstName LIKE ?");
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
 *       "select * from Users where firstName LIKE '%{search_term}%'", searchTerm);
 *   try (PreparedStatement statement = sql.prepareStatement(connection)) {
 *     ...
 *   }
 * }</pre>
 *
 * And even when you don't use LIKE operator or the percent sign (%), it may still be more readable
 * to quote the string parameters just so the SQL template explicitly tells readers that
 * the parameter is a string. The following template works with or without the quotes: <pre>{@code
 *   // Reads more clearly that the {id} is a string
 *   SafeSql sql = SafeSql.of("select * from Users where id = '{id}'", userId);
 * }</pre>
 *
 * <p>A useful tip: the compile-time check tries to be helpful and checks that if you use the
 * same parameter name more than once in the template, the same value must be used for it.
 *
 * So for example, if you are trying to generate a SQL that looks like: <pre>{@code
 *   SELECT u.firstName, p.profileId
 *   FROM (select firstName FROM Users where id = 'foo') u,
 *        (select profileId FROM Profiles where userId = 'foo') p
 * }</pre>
 *
 * It'll be important to use the same user id for both subqueries. And you can use the following
 * template to make sure of it at compile time: <pre>{@code
 *   SafeSql sql = SafeSql.of(
 *       """
 *       SELECT u.firstName, p.profileId
 *       FROM (select firstName FROM Users where id = {user_id}) u,
 *            (select profileId FROM Profiles where userId = {user_id}) p
 *       """,
 *       userId, userId);
 * }</pre>
 *
 * If someone mistakenly passes in inconsistent ids, they'll get a compilation error.
 *
 * <p>This class serves a different purpose than {@link SafeQuery}, which is to directly escape
 * string parameters when the SQL backend has no native support for parameterized queries.
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

  private static final SafeSql FALSE = new SafeSql("(1 = 0)");
  private static final SafeSql TRUE = new SafeSql("(1 = 1)");


  /** An empty SQL */
  public static final SafeSql EMPTY = new SafeSql("");

  /**
   * Returns {@link SafeSql} using {@code template} and {@code params}.
   *
   * <p>For example:
   *
   * <pre>{@code
   * PreparedStatement statement =
   *     SafeSql.of("select * from JOBS where id = {id}", jobId)
   *         .prepareStatement(connection);
   * }</pre>
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

  /** Returns a SafeSql wrapping the name of {@code enumConstant}. */
  public static SafeSql of(Enum<?> enumConstant) {
    return new SafeSql(enumConstant.name());
  }

  /**
   * Convenience method equivalent to {@code of("{param}", param)}, which
   * is translated to a single question mark ('?') with {@code param} being the value.
   *
   * <p>If you have a list of candidate ids that need to be passed to the IN opertor, you can use:
   * <pre>{@code
   *   List<Long> userIds = ...;
   *   SafeSql.of(
   *       "SELECT * FROM Users WHERE id IN ({user_ids})",
   *       userIds.stream().map(SafeSql::ofParam).toList())
   * }</pre>
   */
  public static SafeSql ofParam(@Nullable Object param) {
    return of("{param}", param);
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
   *         SELECT job_id from Jobs
   *         WHERE query LIKE '%{keyword}%'
   *         """);
   *
   * PreparedStatement stmt = GET_JOB_IDS_BY_QUERY.with("sensitive word").prepareStatement(conn);
   * }</pre>
   *
   * <p>See {@link #of(String, Object...)} for discussion on the template arguments.
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
            builder.appendSql(texts.pop());
            if (placeholder.isImmediatelyBetween("`", "`")) {
              builder.appendSql(
                  mustBeIdentifiers(placeholder, elements).collect(Collectors.joining("`, `")));
            } else {
              builder.addSubQuery(mustBeSubqueries(placeholder, elements).collect(joining(", ")));
              validateSubqueryPlaceholder(placeholder);
            }
          } else if (value instanceof SafeSql) {
            builder.appendSql(texts.pop()).addSubQuery((SafeSql) value);
            validateSubqueryPlaceholder(placeholder);
          } else if (appendBeforeQuotedPlaceholder("`", placeholder, "`", value)) {
            String identifier = checkIdentifier(placeholder, (String) value);
            checkArgument(identifier.length() > 0, "`%s` cannot be empty", placeholder);
            builder.appendSql("`" + identifier + "`");
          } else if (appendBeforeQuotedPlaceholder("'%", placeholder, "%'", value)) {
            builder.addParameter(paramName, "%" + escapePercent((String) value) + "%");
          } else if (appendBeforeQuotedPlaceholder("'%", placeholder, "'", value)) {
            builder.addParameter(paramName, "%" + escapePercent((String) value));
          } else if (appendBeforeQuotedPlaceholder("'", placeholder, "%'", value)) {
            builder.addParameter(paramName, escapePercent((String) value) + "%");
          } else if (appendBeforeQuotedPlaceholder("'", placeholder, "'", value)) {
            builder.addParameter(paramName, value);
          } else {
            builder.appendSql(texts.pop()).addParameter(paramName, value);
          }
        }

        private boolean appendBeforeQuotedPlaceholder(
            String open, Substring.Match placeholder, String close, Object value) {
          boolean quoted = placeholder.isImmediatelyBetween(open, close);
          if (quoted) {
            checkArgument(
                value instanceof String,
                "Placeholder %s%s%s must be String", open, placeholder, close);
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
   * A collector that joins boolean query snippets using {@code AND} operator. The
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
   * A collector that joins boolean query snippets using {@code OR} operator. The
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
  }

  private static Stream<SafeSql> mustBeSubqueries(
      CharSequence placeholder, Iterator<?> elements) {
    return BiStream.zip(indexesFrom(0), stream(elements))
        .mapToObj((index, element) -> {
          checkArgument(
              element != null,
              "%s[%s] expected to be SafeSql, but is null", placeholder, index);
          checkArgument(
              element instanceof SafeSql,
              "%s[%s] expected to be SafeSql, but is %s",
              placeholder, index, element.getClass());
          return (SafeSql) element;
        });
  }

  private static Stream<String> mustBeIdentifiers(
      CharSequence placeholder, Iterator<?> elements) {
    StringFormat elementNameFormat = new StringFormat("{placeholder}[{index}]");
    return BiStream.zip(indexesFrom(0), stream(elements))
        .mapToObj((index, element) -> {
          String name = elementNameFormat.format(placeholder, index);
          checkArgument(element != null, "%s expected to be an identifier, but is null", name);
          checkArgument(
              element instanceof String,
              "%s expected to be String, but is %s", name, element.getClass());
          return checkIdentifier(name, (String) element);
        });
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
