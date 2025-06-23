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

import static com.google.common.base.CharMatcher.breakingWhitespace;
import static com.google.common.base.CharMatcher.whitespace;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static com.google.mu.safesql.InternalCollectors.skippingEmpty;
import static com.google.mu.safesql.SafeQuery.checkIdentifier;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.Substring.word;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.mu.annotations.RequiresGuava;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;

/**
 * An injection-safe <em>dynamic SQL</em>, constructed using compile-time enforced templates.
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
 *   List<Long> ids = sql.query(connection, Long.class);
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
 * <dl><dt><STRONG>Compile-time Protection</STRONG></dt></dl>
 *
 * <p>The templating engine uses compile-time checks to guard against accidental use of
 * untrusted strings in the SQL, ensuring that they can only be sent as parameters of
 * PreparedStatement: try to use a dynamically generated String as the SQL template and
 * you'll get a compilation error.
 *
 * <p>In addition, the same set of compile-time guardrails from the {@link StringFormat} class
 * are in effect to make sure that you don't pass {@code lastName} in the place of
 * {@code first_name}, for example.
 *
 * <p>To enable the compile-time plugin, copy the {@code <annotationProcessorPaths>} in the
 * "maven-compiler-plugin" section from the following pom.xml file snippet:
 *
 * <pre>{@code
 * <build>
 *   <pluginManagement>
 *     <plugins>
 *       <plugin>
 *         <artifactId>maven-compiler-plugin</artifactId>
 *         <configuration>
 *           <annotationProcessorPaths>
 *             <path>
 *               <groupId>com.google.errorprone</groupId>
 *               <artifactId>error_prone_core</artifactId>
 *               <version>2.23.0</version>
 *             </path>
 *             <path>
 *               <groupId>com.google.mug</groupId>
 *               <artifactId>mug-errorprone</artifactId>
 *               <version>8.6</version>
 *             </path>
 *           </annotationProcessorPaths>
 *         </configuration>
 *       </plugin>
 *     </plugins>
 *   </pluginManagement>
 * </build>
 * }</pre>
 *
 * <dl><dt><STRONG>Conditional Subqueries</STRONG></dt></dl>
 *
 * SafeSql's template syntax is designed to avoid control flows that could obfuscate SQL. Instead,
 * complex control flow such as {@code if-else}, nested {@code if}, loops etc. should be performed
 * in Java and passed in as subqueries.
 *
 * <p>Starting from v8.4, simple conditional subqueries (e.g. selecting a column if a flag is
 * enabled) can use the conditional subquery operator {@code ->} inside template placeholders:
 *
 * <pre>{@code
 *   SafeSql sql = SafeSql.of(
 *       "SELECT {shows_email -> email,} name FROM Users", showsEmail());
 * }</pre>
 *
 * The query text after the {@code ->} operator is the conditional subquery that's only included if
 * {@code showEmail()} returns true. The subquery can include arbitrary characters except curly
 * braces, so you can also have multi-line conditional subqueries.
 *
 * <dl><dt><STRONG>Complex Dynamic Subqueries</STRONG></dt></dl>
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
 *   SafeSql usersQuery(UserCriteria criteria, @CompileTimeConstant String... columns) {
 *     return SafeSql.of(
 *         "SELECT `{columns}` FROM Users WHERE {criteria}",
 *         asList(columns),
 *         Stream.of(
 *               optionally("id = {id}", criteria.userId()),
 *               optionally("firstName LIKE '%{first_name}%'", criteria.firstName()))
 *           .collect(SafeSql.and()));
 *   }
 *
 *   List<User> users = usersQuery(userCriteria, "email", "lastName")
 *       .query(connection, User.class);
 * }</pre>
 *
 * If {@code UserCriteria} has specified {@code firstName()} but {@code userId()} is
 * unspecified (empty), the resulting SQL will look like:
 *
 * <pre>{@code
 *   SELECT `email`, `lastName` FROM Users WHERE firstName LIKE ?
 * }</pre>
 *
 * <p>And when you call {@code usersQuery.prepareStatement(connection)} or one of the similar
 * convenience methods, {@code statement.setObject(1, "%" + criteria.firstName().get() + "%")}
 * will be called to populate the PreparedStatement.
 *
 * <dl><dt><STRONG>Parameterize by Column Names or Table Names</STRONG></dt></dl>
 *
 * Sometimes you may wish to parameterize by table names, column names etc.
 * for which JDBC parameterization has no support.
 *
 * <p>If the identifiers are compile-time string literals, you can wrap them using
 * {@code SafeSql.of(COLUMN_NAME)}, which can then be composed as subqueries.
 * But what if the identifier string is loaded from a resource file, or is specified by a
 * request field?
 *
 * <p>Passing the string directly as a template parameter will only generate the JDBC
 * <code>'?'</code> parameter in its place, which won't work (PreparedStatement can't parameterize
 * identifiers); {@code SafeSql.of(theString)} will fail to compile because such strings are
 * inherently dynamic and untrusted.
 *
 * <p>The safe way to parameterize dynamic strings as <em>identifiers</em> is to backtick-quote
 * their placeholders in the SQL template (if you use Oracle, PostgreSQL that use double quotes for
 * identifier, use double quotes instead). For example: <pre>{@code
 *   SafeSql.of("SELECT `{columns}` FROM Users", request.getColumns())
 * }</pre>
 * The backticks tell SafeSql that the string is supposed to be an identifier (or a list of
 * identifiers). SafeSql will sanity-check the string(s) to ensure injection safety.
 *
 * <p>In the above example, if {@code getColumns()} returns {@code ["id", "age"]}, the genereated
 * SQL will be:
 *
 * <pre>{@code
 *   SELECT `id`, `age` FROM Users
 * }</pre>
 *
 * <p>That is, each individual string will be backtick-quoted and then joined by ", ".
 *
 * <dl><dt><STRONG>The {@code LIKE} Operator</STRONG></dt></dl>
 *
 * <p>Note that with straight JDBC API, if you try to use the LIKE operator to match a user-provided
 * substring, i.e. using {@code LIKE '%foo%'} to search for "foo", this seemingly intuitive
 * syntax is actually incorect: <pre>{@code
 *   String searchTerm = ...;
 *   PreparedStatement statement =
 *       connection.prepareStatement("SELECT id FROM Users WHERE firstName LIKE '%?%'");
 *   statement.setString(1, searchTerm);
 * }</pre>
 *
 * JDBC PreparedStatement considers the quoted question mark as a literal so the {@code setString()}
 * call will fail. You'll need to use the following workaround: <pre>{@code
 *   PreparedStatement statement =
 *       connection.prepareStatement("SELECT id FROM Users WHERE firstName LIKE ?");
 *   statement.setString(1, "%" + searchTerm + "%");
 * }</pre>
 *
 * And even then, if the {@code searchTerm} includes special characters like '%' or backslash ('\'),
 * they'll be interepreted as wildcards and escape characters, opening it up to a form of minor
 * SQL injection despite already using the parameterized SQL.
 *
 * <p>The SafeSql template protects you from this caveat. The most intuitive syntax does exactly
 * what you'd expect (and it escapes special characters too): <pre>{@code
 *   String searchTerm = ...;
 *   SafeSql sql = SafeSql.of(
 *       "SELECT id FROM Users WHERE firstName LIKE '%{search_term}%'", searchTerm);
 *   List<Long> ids = sql.query(connection, Long.class);
 * }</pre>
 *
 * <p><strong>Automatic Escaping: No Need for ESCAPE Clause</strong></p>
 *
 * <p>This means you <em>do not</em> need to (and in fact, must not) write SQL
 * using {@code ESCAPE} clauses after {@code LIKE '%{foo}%'}. Any such attempt, as in:
 *
 * <pre>{@code
 *   SELECT name FROM Users WHERE name LIKE '%{term}%' ESCAPE '\'
 * }</pre>
 *
 * <p>...will be rejected, because SafeSql already performs all necessary escaping internally
 * and automatically uses {@code ESCAPE '^'}. In other words, LIKE <em>just works</em>.
 *
 * <p>This eliminates the need for developers to deal with brittle double-escaping
 * (like {@code '\\'}), or any cross-dialect compatibility issues.
 * The template is also more readable.
 *
 * <p>If you find yourself wanting to use {@code ESCAPE}, consider whether you are
 * manually escaping strings that could instead be safely passed as-is to SafeSql's
 * template system.
 *
 * <p>That said, this only applies when template placeholder is used in the LIKE string.
 * You can use any valid SQL ESCAPE syntax if placeholder isn't used in the LIKE expression.
 *
 * <dl><dt><STRONG>Quote String Placeholders</STRONG></dt></dl>
 *
 * Even when you don't use the {@code LIKE} operator or the percent sign (%), it may still be
 * more readable to quote the string placeholders just so the SQL template explicitly tells readers
 * that the parameter is a string. The following template works with or without the quotes around
 * the <code>{id}</code> placeholder:
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
 * <p>So for example, if you are trying to generate a SQL that looks like: <pre>{@code
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
 * <hr width = "100%" size = "2"></hr>
 *
 * <p>Immutable if the template parameters you pass to it are immutable.
 *
 * <p>This class serves a different purpose than {@link SafeQuery}. The latter is to directly escape
 * string parameters when the SQL backend has no native support for parameterized queries.
 *
 * @since 8.2
 */
@RequiresGuava
@ThreadSafe
@CheckReturnValue
public final class SafeSql {
  private static final Substring.RepeatingPattern TOKENS =
      Stream.of(word(), first(breakingWhitespace().negate()::matches))
          .collect(firstOccurrence())
          .repeatedly();
  private static final StringFormat PLACEHOLDER_ELEMENT_NAME =
      new StringFormat("{placeholder}[{index}]");
  private static final SafeSql FALSE = new SafeSql("(1 = 0)");
  private static final SafeSql TRUE = new SafeSql("(1 = 1)");
  private static final StringFormat.Template<SafeSql> PARAM = template("{param}");

  /** An empty SQL */
  public static final SafeSql EMPTY = new SafeSql("");

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
   *     .query(connection, Long.class);
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
   * Wraps non-negative {@code number} as a literal SQL snippet in a SafeSql object.
   *
   * <p>For example, the following SQL Server query allows parameterization by the TOP n number:
   * <pre>{@code
   *   SafeSql.of("SELECT TOP {page_size} UserId FROM Users", nonNegativeLiteral(pageSize))
   * }</pre>
   *
   * <p>Needed because the SQL Server JDBC driver doesn't support parameterizing the TOP number
   * through {@link PreparedStatement} API.
   *
   * @throws IllegalArgumentException if {@code number} is negative
   */
  public static SafeSql nonNegativeLiteral(int number) {
    checkArgument(number >= 0, "negative number disallowed: %s", number);
    return new SafeSql(Integer.toString(number));
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
   * Returns this SafeSql if {@code condition} is true; otherwise returns {@link #EMPTY}.
   *
   * @since 8.4
   */
  public SafeSql when(boolean condition) {
    return condition ? this : EMPTY;
  }

  /**
   * Returns a {@link Template} of {@link SafeSql} based on the {@code template} string.
   * Useful for creating a constant to be reused with different parameters.
   *
   * <p>For example:
   *
   * <pre>{@code
   * private static final Template<SafeSql> FIND_USERS_BY_NAME =
   *     SafeSql.template("SELECT `{columns}` FROM Users WHERE name LIKE '%{name}%'");
   *
   * String searchBy = ...;
   * List<User> userIds = FIND_USERS_BY_NAME.with(asList("id", "name"), searchBy)
   *     .query(connection, User.class);
   * }</pre>
   *
   * <p>If you don't need a reusable template, consider using {@link #of} instead, which is simpler.
   *
   * <p>The template arguments follow the same rules as discussed in {@link #of(String, Object...)}
   * and receives the same compile-time protection against mismatch or out-of-order human mistakes,
   * so it's safe to use the template as a constant.
   *
   * <p>The returned template is immutable and thread safe.
   */
  public static Template<SafeSql> template(@CompileTimeConstant String template) {
    ImmutableList<Substring.Match> allTokens = TOKENS.match(template).collect(toImmutableList());
    ImmutableMap<Integer, Integer> charIndexToTokenIndex =
        BiStream.zip(allTokens.stream(), indexesFrom(0))
            .mapKeys(Substring.Match::index)
            .collect(ImmutableMap::toImmutableMap);
    return StringFormat.template(template, (fragments, placeholders) -> {
      Deque<String> texts = new ArrayDeque<>(fragments);
      Builder builder = new Builder();
      class SqlWriter {
        void writePlaceholder(Substring.Match placeholder, Object value) {
          String paramName = rejectQuestionMark(placeholder.skip(1, 1).toString().trim());
          Substring.Match conditional = first("->").in(paramName).orElse(null);
          if (conditional != null) {
            checkArgument(
                !placeholder.isImmediatelyBetween("`", "`"),
                "boolean placeholder {%s->} shouldn't be backtick quoted",
                conditional.before());
            checkArgument(
                !placeholder.isImmediatelyBetween("\"", "\""),
                "boolean placeholder {%s->} shouldn't be double quoted",
                conditional.before());
            checkArgument(
                value != null,
                "boolean placeholder {%s->} cannot be used with a null value",
                conditional.before());
            checkArgument(
                value instanceof Boolean,
                "boolean placeholder {%s->} can only be used with a boolean value; %s encountered.",
                conditional.before(),
                value.getClass().getName());
            builder.appendSql(texts.pop());
            if ((Boolean) value) {
              builder.appendSql(whitespace().trimFrom(conditional.after()));
            }
            return;
          }
          if (value instanceof Iterable) {
            Iterator<?> elements = ((Iterable<?>) value).iterator();
            checkArgument(elements.hasNext(), "%s cannot be empty list", placeholder);
            if (placeholder.isImmediatelyBetween("'", "'")
                && lookaround("IN ('", placeholder, "')")
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
            } else if (placeholder.isImmediatelyBetween("\"", "\"")) {
              builder.appendSql(
                  eachPlaceholderValue(placeholder, elements)
                      .mapToObj(SafeSql::mustBeIdentifier)
                      .collect(Collectors.joining("\", \"")));
            } else if (lookaround("IN (", placeholder, ")")) {
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
          } else if (appendBeforeQuotedPlaceholder("\"", placeholder, "\"")) {
            String identifier = mustBeIdentifier("\"" + placeholder + "\"", value);
            checkArgument(identifier.length() > 0, "\"%s\" cannot be empty", placeholder);
            builder.appendSql("\"" + identifier + "\"");
          } else if (lookbehind("LIKE '%", placeholder)
              && appendBeforeQuotedPlaceholder("'%", placeholder, "%'")) {
            rejectEscapeAfter(placeholder);
            builder
                .addParameter(paramName, "%" + escapePercent(mustBeString(placeholder, value)) + "%")
                .appendSql(" ESCAPE '^'");
          } else if (lookbehind("LIKE '%", placeholder)
              && appendBeforeQuotedPlaceholder("'%", placeholder, "'")) {
            rejectEscapeAfter(placeholder);
            builder
                .addParameter(paramName, "%" + escapePercent(mustBeString(placeholder, value)))
                .appendSql(" ESCAPE '^'");
          } else if (lookbehind("LIKE '", placeholder)
              && appendBeforeQuotedPlaceholder("'", placeholder, "%'")) {
            rejectEscapeAfter(placeholder);
            builder
                .addParameter(paramName, escapePercent(mustBeString(placeholder, value)) + "%")
                .appendSql(" ESCAPE '^'");
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

        private void rejectEscapeAfter(Substring.Match placeholder) {
          checkArgument(
              !lookahead(placeholder, "%' ESCAPE") && !lookahead(placeholder, "' ESCAPE"),
              "ESCAPE not supported after %s. Just leave the placeholder alone and SafeSql will auto escape.",
              placeholder);
        }

        private boolean lookaround(
            String leftPattern, Substring.Match placeholder, String rightPattern) {
          return lookahead(placeholder, rightPattern) && lookbehind(leftPattern, placeholder);
        }

        private boolean lookahead(Substring.Match placeholder, String rightPattern) {
          ImmutableList<String> lookahead = TOKENS.from(rightPattern).collect(toImmutableList());
          int closingBraceIndex = placeholder.index() + placeholder.length() - 1;
          int nextTokenIndex = charIndexToTokenIndex.get(closingBraceIndex) + 1;
          return BiStream.zip(lookahead, allTokens.subList(nextTokenIndex, allTokens.size()))
                  .filter((s, t) -> s.equalsIgnoreCase(t.toString()))
                  .count() == lookahead.size();
        }

        private boolean lookbehind(String leftPattern, Substring.Match placeholder) {
          ImmutableList<String> lookbehind = TOKENS.from(leftPattern).collect(toImmutableList());
          ImmutableList<Substring.Match> leftTokens =
              allTokens.subList(0, charIndexToTokenIndex.get(placeholder.index()));
          return BiStream.zip(lookbehind.reverse(), leftTokens.reverse())  // right-to-left
                  .filter((s, t) -> s.equalsIgnoreCase(t.toString()))
                  .count() == lookbehind.size();
        }
      }
      placeholders
          .peek(SafeSql::checkMisuse)
          .forEachOrdered(new SqlWriter()::writePlaceholder);
      checkState(texts.size() == 1);
      return builder.appendSql(texts.pop()).build();
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
    rejectQuestionMark(delimiter);
    return skippingEmpty(
        Collector.of(
            Builder::new,
            (b, q) -> b.delimit(delimiter).addSubQuery(q),
            (b1, b2) -> b1.delimit(delimiter).addSubQuery(b2.build()),
            Builder::build));
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #optionally} or {@link #when}),
   * returns the SafeSql produced from the {@code fallback} template and {@code args}.
   *
   * <p>Using this method, you can create a chain of optional queries like:
   *
   * <pre>{@code
   * import static ....Optionals.nonEmpty;
   *
   * SafeSql.of(
   *     """
   *     CREATE TABLE ...
   *     {cluster_by}
   *     """,
   *     optionally("CLUSTER BY (`{cluster_columns}`)", nonEmpty(clusterColumns))
   *         .orElse("-- no cluster"));
   * }</pre>
   *
   * @since 8.3
   */
  @TemplateFormatMethod
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public SafeSql orElse(@TemplateString @CompileTimeConstant String fallback, Object... args) {
    checkNotNull(fallback);
    checkNotNull(args);
    return orElse(() -> of(fallback, args));
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #optionally} or {@link #when}),
   * returns the {@code fallback} query.
   *
   * @since 8.3
   */
  public SafeSql orElse(SafeSql fallback) {
    checkNotNull(fallback);
    return orElse(() -> fallback);
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #optionally} or {@link #when}),
   * returns the query produced by the {@code fallback} supplier.
   *
   * @since 8.3
   */
  public SafeSql orElse(Supplier<SafeSql> fallback) {
    checkNotNull(fallback);
    return sql.isEmpty() ? fallback.get() : this;
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection}. The {@link ResultSet}
   * will be consumed, transformed to a list of {@code T} and then closed before returning.
   *
   * <p>For example: <pre>{@code
   * List<User> users = SafeSql.of("SELECT id, name FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(connection, User.class);
   *
   * record User(long id, String name) {...}
   * }</pre>
   *
   * <p>The class of {@code resultType} must define a non-private constructor that accepts
   * the same number of parameters as returned by the query. The parameter order doesn't
   * matter but the parameter <em>names</em> and types must match.
   *
   * <p>Note that if you've enabled the {@code -parameters} javac flag, the above example code
   * will just work. If you can't enable {@code -parameters}, consider explicitly annotating
   * the constructor parameters as in:
   *
   * <pre>{@code
   * record User(@SqlName("id") long id, @SqlName("name") String name) {...}
   * }</pre>
   *
   * <p>Alternatively, if your query only selects one column, you could also use this method
   * to read the results: <pre>{@code
   * List<String> names = SafeSql.of("SELECT name FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(connection, String.class);
   * }</pre>
   *
   * <p>You can also map the result rows to Java Beans, like:
   *
   * <p>For example: <pre>{@code
   * List<UserBean> users =
   *     SafeSql.of("SELECT id, name FROM Users WHERE name LIKE '%{name}%'", name)
   *         .query(connection, UserBean.class);
   *
   * public class UserBean {
   *   public void setId(long id) {...}
   *   public void setName(String name) {...}
   * }
   * }</pre>
   *
   * <p>The rules of mapping query columns to Java Bean properties are:
   * <ul>
   * <li>Case doesn't matter. {@code job_id} will match {@code jobId} or {@code JOB_ID}.
   * <li>It's okay if a query column doesn't map to a bean property, as long as all settable
   *     bean properties are mapped to a query column. The column value will just be ignored.
   * <li>It's okay if a bean property doesn't map to a query column, as long as all query
   *     columns have been mapped to a bean property.
   * <li>If a bean property is of primitive type, and the corresponding query column value
   *     is null, the property will be left as is.
   * <li>If you can't make a bean property match a query column, you can annotate the setter method
   *     with the {@code @SqlName} annotation to customize the column name.
   * </ul>
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 8.7
   */
  public <T> List<T> query(Connection connection, Class<? extends T> resultType) {
    return query(connection, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection}. The {@link ResultSet}
   * will be consumed, transformed by {@code rowMapper} and then closed before returning.
   *
   * <p>For example: <pre>{@code
   * List<Long> ids = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(connection, row -> row.getLong("id"));
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   */
  public <T> List<T> query(
      Connection connection, SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    checkNotNull(rowMapper);
    if (paramValues.isEmpty()) {
      try (Statement stmt = connection.createStatement();
          ResultSet resultSet = stmt.executeQuery(sql)) {
        return mapResults(resultSet, rowMapper);
      } catch (SQLException e) {
        throw new UncheckedSqlException(e);
      }
    }
    try (PreparedStatement stmt = prepareStatement(connection);
        ResultSet resultSet = stmt.executeQuery()) {
      return mapResults(resultSet, rowMapper);
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection},
   * and then fetches the results lazily in a stream.
   *
   * <p>Each result row is transformed into {@code resultType}.
   *
   * <p>The caller must close it using try-with-resources idiom, which will close the associated
   * {@link Statement} and {@link ResultSet}.
   *
   * <p>For example: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT id, name FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<User> users = sql.queryLazily(connection, User.class)) {
   *   return users.findFirst();
   * }
   *
   * record User(long id, String name) {...}
   * }</pre>
   *
   * <p>The class of {@code resultType} must define a non-private constructor that accepts
   * the same number of parameters as returned by the query. The parameter order doesn't
   * matter but the parameter <em>names</em> and types must match.
   *
   * <p>Note that if you've enabled the {@code -parameters} javac flag, the above example code
   * will just work. If you can't enable {@code -parameters}, consider explicitly annotating
   * the constructor parameters as in:
   *
   * <pre>{@code
   * record User(@SqlName("id") long id, @SqlName("name") String name) {...}
   * }</pre>
   *
   * <p>Alternatively, if your query only selects one column, you could also use this method
   * to read the results: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<Long> ids = sql.queryLazily(connection, Long.class)) {
   *   return ids.findFirst();
   * }
   * }</pre>
   *
   * <p>You can also map the result rows to Java Beans, similar to {@link #query(Connection, Class)}.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 8.7
   */
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public <T> Stream<T> queryLazily(Connection connection, Class<? extends T> resultType) {
    return queryLazily(connection, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection},
   * and then fetches the results lazily in a stream.
   *
   * <p>The returned {@code Stream} includes results transformed by {@code rowMapper}.
   * The caller must close it using try-with-resources idiom, which will close the associated
   * {@link Statement} and {@link ResultSet}.
   *
   * <p>For example: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT name FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<String> names = sql.queryLazily(connection, row -> row.getString("name"))) {
   *   return names.findFirst();
   * }
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 8.4
   */
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public <T> Stream<T> queryLazily(
      Connection connection, SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    return queryLazily(connection, 0, rowMapper);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection}, sets {@code fetchSize}
   * using {@link Statement#setFetchSize}, and then fetches the results lazily in a stream.
   *
   * <p>Each result row is transformed into {@code resultType}.
   *
   * <p>The caller must close it using try-with-resources idiom, which will close the associated
   * {@link Statement} and {@link ResultSet}.
   *
   * <p>For example: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT id, name FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<User> users = sql.queryLazily(connection, fetchSize, User.class)) {
   *   return users.findFirst();
   * }
   *
   * record User(long id, String name) {...}
   * }</pre>
   *
   * <p>The class of {@code resultType} must define a non-private constructor that accepts
   * the same number of parameters as returned by the query. The parameter order doesn't
   * matter but the parameter <em>names</em> and types must match.
   *
   * <p>Note that if you've enabled the {@code -parameters} javac flag, the above example code
   * will just work. If you can't enable {@code -parameters}, consider explicitly annotating
   * the constructor parameters as in:
   *
   * <pre>{@code
   * record User(@SqlName("id") long id, @SqlName("name") String name) {...}
   * }</pre>
   *
   * <p>Alternatively, if your query only selects one column, you could also use this method
   * to read the results: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT birthday FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<LocalDate> birthdays = sql.queryLazily(connection, fetchSize, LocalDate.class)) {
   *   return birthdays.findFirst();
   * }
   * }</pre>
   *
   * <pYou can also map the result rows to Java Beans, similar to {@link #query(Connection, Class)}.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 8.7
   */
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public <T> Stream<T> queryLazily(Connection connection, int fetchSize, Class<? extends T> resultType) {
    return queryLazily(connection, fetchSize, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection}, sets {@code fetchSize}
   * using {@link Statement#setFetchSize}, and then fetches the results lazily in a stream.
   *
   * <p>The returned {@code Stream} includes results transformed by {@code rowMapper}.
   * The caller must close it using try-with-resources idiom, which will close the associated
   * {@link Statement} and {@link ResultSet}.
   *
   * <p>For example: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT name FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<String> names = sql.queryLazily(connection, row -> row.getString("name"))) {
   *   return names.findFirst();
   * }
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 8.4
   */
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public <T> Stream<T> queryLazily(
      Connection connection, int fetchSize, SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    checkNotNull(rowMapper);
    if (paramValues.isEmpty()) {
      return lazy(connection::createStatement, fetchSize, stmt -> stmt.executeQuery(sql), rowMapper);
    }
    return lazy(() -> prepareStatement(connection), fetchSize, PreparedStatement::executeQuery, rowMapper);
  }

  @MustBeClosed
  private static <S extends Statement, T> Stream<T> lazy(
      SqlSupplier<? extends S> createStatement,
      int fetchSize,
      SqlFunction<? super S, ResultSet> execute,
      SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    try (JdbcCloser closer = new JdbcCloser()) {
      S stmt = createStatement.get();
      closer.register(stmt::close);
      stmt.setFetchSize(fetchSize);
      ResultSet resultSet = execute.apply(stmt);
      closer.register(resultSet::close);
      return closer.attachTo(
          whileNotNull(() -> {
            try {
              return resultSet.next() ? new AtomicReference<T>(rowMapper.apply(resultSet)) : null;
            } catch (SQLException e) {
              throw new UncheckedSqlException(e);
            }
          })
          .map(AtomicReference::get));
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Executes the encapsulated DML (create, update, delete statements) against {@code connection}
   * and returns the number of affected rows.
   *
   * <p>For example: <pre>{@code
   * SafeSql.of("INSERT INTO Users(id, name) VALUES({id}, '{name}')", id, name)
   *     .update(connection);
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeUpdate}.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   */
  @CanIgnoreReturnValue public int update(Connection connection) {
    if (paramValues.isEmpty()) {
      try (Statement stmt = connection.createStatement()) {
        return stmt.executeUpdate(sql);
      } catch (SQLException e) {
        throw new UncheckedSqlException(e);
      }
    }
    try (PreparedStatement stmt = prepareStatement(connection)) {
      return stmt.executeUpdate();
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Returns a {@link PreparedStatement} with the encapsulated sql and parameters.
   *
   * <p>It's often more convenient to use {@link #query} or {@link #update} unless you need to
   * directly operate on the PreparedStatement.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   */
  @MustBeClosed
  public PreparedStatement prepareStatement(Connection connection) {
    try {
      return setParameters(connection.prepareStatement(sql));
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Returns a query template that will reuse the same cached {@code PreparedStatement}
   * for repeated calls of {@link Template#with} using different parameters.
   *
   * <p>Allows callers to take advantage of the performance benefit of PreparedStatement
   * without having to re-create the statement for each call. For example in: <pre>{@code
   *   try (var connection = ...) {
   *     var queryByName = SafeSql.prepareToQuery(
   *         connection, "SELECT id, name FROM Users WHERE name LIKE '%{name}%'",
   *         User.class);
   *     for (String name : names) {
   *       for (User user : queryByName.with(name))) {
   *         ...
   *       }
   *     }
   *   }
   *
   *   record User(long id, String name) {...}
   * }</pre>
   *
   * Each time {@code queryByName.with(name)} is called, it executes the same query template
   * against the connection, but with a different {@code name} parameter. Internally it reuses the
   * cached PreparedStatement object and just calls {@link PreparedStatement#setObject(int, Object)}
   * with the new set of parameters before calling {@link PreparedStatement#executeQuery}.
   *
   * <p>The template arguments follow the same rules as discussed in {@link #of(String, Object...)}
   * and receives the same compile-time protection against mismatch or out-of-order human mistakes.
   *
   * <p>The returned Template is <em>not</em> thread safe.
   *
   * <p>The caller is expected to close the {@code connection} after done, which will close the
   * cached PreparedStatement.
   *
   * @since 8.7
   */
  public static <T> Template<List<T>> prepareToQuery(
      Connection connection, @CompileTimeConstant String template, Class<? extends T> resultType) {
    return prepareToQuery(connection, template, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Returns a query template that will reuse the same cached {@code PreparedStatement}
   * for repeated calls of {@link Template#with} using different parameters.
   *
   * <p>Allows callers to take advantage of the performance benefit of PreparedStatement
   * without having to re-create the statement for each call. For example in: <pre>{@code
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
   * Each time {@code queryByName.with(name)} is called, it executes the same query template
   * against the connection, but with a different {@code name} parameter. Internally it reuses the
   * cached PreparedStatement object and just calls {@link PreparedStatement#setObject(int, Object)}
   * with the new set of parameters before calling {@link PreparedStatement#executeQuery}.
   *
   * <p>The template arguments follow the same rules as discussed in {@link #of(String, Object...)}
   * and receives the same compile-time protection against mismatch or out-of-order human mistakes.
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
   * Returns a DML (create, update, delete) template that will reuse the same cached {@code
   * PreparedStatement} for repeated calls of {@link Template#with} using different parameters.
   *
   * <p>Allows callers to take advantage of the performance benefit of PreparedStatement
   * without having to re-create the statement for each call. For example in: <pre>{@code
   *   try (var connection = ...) {
   *     var insertUser = SafeSql.prepareToUpdate(
   *         connection, "INSERT INTO Users(id, name) VALUES({id}, '{name}')");
   *     int totalRowsAffected = insertUser.with(1, "Tom") + insertUser.with(2, "Emma");
   *   }
   * }</pre>
   *
   * Each time {@code insertUser.with(...)} is called, it executes the same DML template
   * against the connection, but with different {@code id} and {@code name} parameters.
   * Internally it reuses the cached PreparedStatement object and just calls {@link
   * PreparedStatement#setObject(int, Object)} with the new set of parameters before calling
   * {@link PreparedStatement#executeUpdate}.
   *
   * <p>The template arguments follow the same rules as discussed in {@link #of(String, Object...)}
   * and receives the same compile-time protection against mismatch or out-of-order human mistakes.
   *
   * <p>The returned Template is <em>not</em> thread safe because the cached {@link
   * PreparedStatement} objects aren't.
   *
   * <p>The caller is expected to close the {@code connection} after done, which will close the
   * cached PreparedStatement.
   */
  public static Template<Integer> prepareToUpdate(
      Connection connection, @CompileTimeConstant String template) {
    return prepare(connection, template, PreparedStatement::executeUpdate);
  }

  /**
   * Returns the parameter values in the order they occur in the SQL. They are used by methods
   * like {@link #query query()}, {@link #update update()} or {@link #prepareStatement}  to
   * populate the {@link PreparedStatement}.
   */
  List<?> getParameters() {
    return paramValues;
  }

  /**
   * Returns the SQL text with the template parameters translated to the JDBC {@code '?'}
   * placeholders.
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

  private PreparedStatement setParameters(PreparedStatement statement) throws SQLException {
    for (int i = 0; i < paramValues.size(); i++) {
      statement.setObject(i + 1, paramValues.get(i));
    }
    return statement;
  }

  private static void checkMisuse(Substring.Match placeholder, Object value) {
    SafeQuery.validatePlaceholder(placeholder);
    checkArgument(
        !(value instanceof SafeQuery), "%s: don't mix in SafeQuery with SafeSql.", placeholder);
    checkArgument(
        !(value instanceof Optional),
        "%s: optional parameter not supported. Consider using SafeSql.optionally() or SafeSql.when()?",
        placeholder);
  }

  @CanIgnoreReturnValue private static String rejectQuestionMark(String sql) {
    checkArgument(sql.indexOf('?') < 0, "please use named {placeholder} instead of '?'");
    return sql;
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

  private static String escapePercent(String s) {
    return first(c -> c == '^' || c == '%' || c == '_').repeatedly().replaceAllFrom(s, c -> "^" + c);
  }

  private static <T> Template<T> prepare(
      Connection connection, @CompileTimeConstant String template,
      SqlFunction<? super PreparedStatement, ? extends T> action) {
    checkNotNull(connection);
    Template<SafeSql> sqlTemplate = template(template);
    return new Template<T>() {
      private final ConcurrentMap<String, PreparedStatement> cached = new ConcurrentHashMap<>();

      @SuppressWarnings("StringFormatArgsCheck")  // The returned is also a Template<>
      @Override public T with(Object... params) {
        SafeSql sql = sqlTemplate.with(params);
        PreparedStatement stmt = cached.computeIfAbsent(sql.toString(), s -> {
          try {
            return connection.prepareStatement(s);
          } catch (SQLException e) {
            throw new UncheckedSqlException(e);
          }
        });
        try {
          return action.apply(sql.setParameters(stmt));
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

    @CanIgnoreReturnValue Builder appendSql(String snippet) {
      safeAppend(rejectQuestionMark(snippet));
      return this;
    }

    @CanIgnoreReturnValue Builder addParameter(String name, Object value) {
      safeAppend("?");
      paramValues.add(value);
      return this;
    }

    @CanIgnoreReturnValue Builder addSubQuery(SafeSql subQuery) {
      safeAppend(subQuery.sql);
      paramValues.addAll(subQuery.getParameters());
      return this;
    }

    @CanIgnoreReturnValue Builder delimit(String delim) {
      if (queryText.length() > 0) {
        safeAppend(delim);
      }
      return this;
    }

    SafeSql build() {
      return new SafeSql(queryText.toString(), unmodifiableList(new ArrayList<>(paramValues)));
    }

    private void safeAppend(String snippet) {
      checkArgument(
          !(endsWith('-') && snippet.startsWith("-")), "accidental line comment: -%s", snippet);
      checkArgument(
          !(endsWith('/') && snippet.startsWith("*")), "accidental block comment: /%s", snippet);
      queryText.append(snippet);
    }

    private boolean endsWith(char c) {
      return queryText.length() > 0 && queryText.charAt(queryText.length() - 1) == c;
    }
  }
}
