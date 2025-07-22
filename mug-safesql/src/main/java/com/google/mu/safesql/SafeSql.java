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

import static com.google.mu.safesql.SafeSqlUtils.checkArgument;
import static com.google.mu.safesql.SafeSqlUtils.skippingEmpty;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.Substring.all;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.word;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static com.google.mu.util.stream.BiStream.biStream;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static com.google.mu.util.stream.MoreStreams.whileNotNull;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.errorprone.annotations.ThreadSafe;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.BiOptional;
import com.google.mu.util.CharPredicate;
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
 * <p>The syntax to create a SQL with JDBC parameters, and potentially with dynamic SQL arguments (such
 * as column names) is as simple and intuitive as the following example: <pre>{@code
 * List<String> groupColumns = ...;
 * SafeSql sql = SafeSql.of(
 *     """
 *     SELECT `{group_columns}`, SUM(revenue) AS revenue
 *     FROM Sales
 *     WHERE sku = {sku}
 *     GROUP BY `{group_columns}`
 *     """,
 *     groupColumns, sku, groupColumns);
 * List<RevenueRecord> results = sql.query(dataSource, RevenueRecord.class);
 * }</pre>
 *
 * <p>By default, all placeholder values are passed as JDBC parameters, unless quoted by backticks
 * (used by databases like BigQuery, Databricks) or double quotes (used by databases like Oracle,
 * Microsoft SQL Server or PostgreSQL). These are validated and interpreted as identifiers.
 *
 * <p>In the above example, placeholder {@code sku} will be passed as JDBC parameter,
 * whereas the backtick-quoted {@code groupColumns} string list will be validated and then
 * used as identifiers.
 *
 * <p>Except the placeholders, everything outside the curly braces are strictly WYSIWYG
 * (what you see is what you get), so you can copy paste them between the Java code and your SQL
 * console for quick testing and debugging.
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
 *   List<Long> ids = sql.query(dataSource, Long.class);
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
 *               <version>9.1</version>
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
 * enabled) can use the guard operator {@code ->} inside template placeholders:
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
 * <p>The {@code ->} guard operator can also be used for {@link Optional} parameters such that
 * the right-hand-side SQL will only render if the optional value is present.
 *
 * <p>For example, the following code builds SQL to query the Users table with flexible
 * number of columns and a flexible WHERE clause depending on the {@code UserCriteria}
 * object's state:
 *
 * <pre>{@code
 *   class UserCriteria {
 *     Optional<String> userId();
 *     Optional<String> firstName();
 *     ...
 *   }
 *
 *   SafeSql usersQuery(UserCriteria criteria, @CompileTimeConstant String... columns) {
 *     return SafeSql.of(
 *         """
 *         SELECT `{columns}`
 *         FROM Users
 *         WHERE 1 = 1
 *             {user_id? -> AND id = user_id?}
 *             {first_name? -> AND firstName LIKE '%first_name?%'}
 *         """,
 *         asList(columns),
 *         criteria.userId()),
 *         criteria.firstName());
 *   }
 *
 *   List<User> users = usersQuery(userCriteria, "email", "lastName")
 *       .query(dataSource, User.class);
 * }</pre>
 *
 * <p>The special "{foo? -> ...}" guard syntax informs the template engine that the
 * right hand side query snippet is only rendered if the {@code Optional} arg corresponding to the
 * "foo?" placeholder is present, in which case the value of the Optional will be used in the right
 * hand side snippet as if it were a regular template argument.
 *
 * <p>If {@code UserCriteria} has specified {@code firstName()} but {@code userId()} is
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
 * <dl><dt><STRONG>Complex Dynamic Subqueries</STRONG></dt></dl>
 *
 * By composing SafeSql objects that encapsulate subqueries, you can parameterize by
 * arbitrary sub-queries that are computed dynamically.
 *
 * <p>Imagine if you need to translate a user-facing structured search expression like
 * {@code location:US AND name:jing OR status:active} into SQL. And you already have the search
 * expression parser that turns the search expression into an AST (abstract syntax tree).
 * The following code uses SafeSql template to turn it into SQL where clause that can be used to
 * query the database for the results: <pre>{@code
 *
 * // The AST
 * interface Expression permits AndExpression, OrExpression, HasExpression {}
 *
 * record AndExpression(Expression left, Expression right) implements Expression {}
 * record OrExpression(Expression left, Expression right) implements Expression {}
 * record HasExpression(String field, String text) implements Expression {}
 *
 * // AST -> SafeSql
 * SafeSql toSqlFilter(Expression expression) {
 *   return switch (expression) {
 *     case HasExpression(String field, String text) ->
 *         SafeSql.of("`{field}` LIKE '%{text}%'", field, text);
 *     case AndExpression(Expression left, Expression right) ->
 *         SafeSql.of("({left}) AND ({right})", toSqlFilter(left), toSqlFilter(right));
 *     case OrExpression(Expression left, Expression right) ->
 *         SafeSql.of("({left}) OR ({right})", toSqlFilter(left), toSqlFilter(right));
 *   };
 * }
 *
 * SafeSql query = SafeSql.of("SELECT * FROM Foos WHERE {filter}", toSqlFilter(expression));
 * }</pre>
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
 *   List<Long> ids = sql.query(dataSource, Long.class);
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
 * <p><em>For Spring users:</em> in order to participate in Spring declarative transaction
 * (methods annotated with {@code @Transactional}),
 * you need to call one of the methods that accept a {@link Connection}, such as the
 * {@link #update(Connection)} method. In a nutshell, it takes calling
 * {@code DataSourceUtils.getConnection(dataSource)} to get the connection in the current transaction,
 * then passing it to {@code update(connection)}.
 *
 * <p>Note that you will also need to catch {@link SQLException} and turn it into a Spring
 * DataAccessException. For that you need Spring's {@code SQLExceptionTranslator}, which
 * has some quirks to use.
 *
 * <p>At this point, it may be easier to create a small wrapper class to execute SafeSql from within
 * a Spring transaction. And while you are there, might as well make it safer to also support
 * calling from outside of a transaction, by using try-with-resources to close the connection:
 *
 * <pre>{@code
 * // Use Java 16 record for brevity. You can use a regular class too.
 * @Component
 * public record SafeSqlBridge(DataSource dataSource, SQLExceptionTranslator translator) {
 *   public int executeUpdate(SafeSql sql) {
 *     try {
 *       if (TransactionSynchronizationManager.isActualTransactionActive()) {
 *         // in an active transaction, don't close or release the connection.
 *         return sql.update(DataSourceUtils.getConnection(dataSource()));
 *       } else {
 *         // not in active transaction, should close the connection.
 *         try (Connection connection = dataSource().getConnection()) {
 *           return sql.update(connection);
 *         }
 *       }
 *     } catch (SQLException e) {
 *       DataAccessException dae =
 *           translator().translate("executeUpdate(SafeSql)", sql.debugString(), e);
 *       if (dae == null) throw new UncheckedSqlException(e);
 *       throw dae;
 *     }
 *   }
 * }
 * }</pre>
 *
 * You can then dependency-inject SafeSqlBridge to execute SafeSql queries: <pre>{@code
 * // Use Java 16 record for brevity. You can use a regular class too.
 * @Service
 * record MyService(SafeSqlBridge bridge) {
 *   @Transactional void transferCredit(String fromAccount, String toAccount) {
 *     SafeSql sql = SafeSql.of("INSERT INTO(...)...'{from}'...'{to}'", fromAccount(), toAccount());
 *     bridge().executeUpdate(sql);
 *   }
 * }
 * }</pre>
 *
 * <hr width = "100%" size = "2"></hr>
 *
 * <p>Immutable if the template parameters you pass to it are immutable.
 *
 * <p>Starting from v9.0, SafeSql is moved to the mug-safesql artifact, and no longer requires Guava
 * as a dependency.
 *
 * @since 8.2
 */
@ThreadSafe
@CheckReturnValue
public final class SafeSql {
  private static final Substring.Pattern OPTIONAL_PARAMETER =
      word().immediatelyBetween("", INCLUSIVE, "?", INCLUSIVE);
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
   *     .query(dataSource, Long.class);
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
   * <p>Although, you should generally use the OFFSET-FETCH syntax, which supports parameterization.
   *
   * @throws IllegalArgumentException if {@code number} is negative
   * @deprecated Prefer {@code OFFSET-FETCH} clause, which is parameterizable
   */
  @Deprecated
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
   *
   * @deprecated Use {@code SafeSql.of("{foo? -> OR foo?}", optionalFoo)} instead of
   *             {@code optionally("or {foo}", optionalFoo)} because the former allows
   *             you to reference {@code foo?} multiple times in the right hand side snippet.
   */
  @TemplateFormatMethod
  @Deprecated
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public static SafeSql optionally(
      @TemplateString @CompileTimeConstant String query, Optional<?> param) {
    requireNonNull(query);
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
    requireNonNull(template);
    requireNonNull(params);
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
   *     .query(dataSource, User.class);
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
    return unsafeTemplate(template);
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
   * SafeSql.of(
   *     """
   *     CREATE TABLE ...
   *     {cluster_by}
   *     """,
   *     when(enableCluster, "CLUSTER BY (`{cluster_columns}`)", clusterColumns)
   *         .orElse("-- no cluster"));
   * }</pre>
   *
   * @since 8.3
   */
  @TemplateFormatMethod
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public SafeSql orElse(@TemplateString @CompileTimeConstant String fallback, Object... params) {
    requireNonNull(fallback);
    requireNonNull(params);
    return orElse(() -> of(fallback, params));
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #optionally} or {@link #when}),
   * returns the {@code fallback} query.
   *
   * @since 8.3
   */
  public SafeSql orElse(SafeSql fallback) {
    requireNonNull(fallback);
    return orElse(() -> fallback);
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #optionally} or {@link #when}),
   * returns the query produced by the {@code fallback} supplier.
   *
   * @since 8.3
   */
  public SafeSql orElse(Supplier<SafeSql> fallback) {
    requireNonNull(fallback);
    return sql.isEmpty() ? fallback.get() : this;
  }

  /**
   * Executes the encapsulated SQL as a query against {@code dataSource.getConnection()}.
   * The {@link ResultSet} will be consumed, transformed to a list of {@code T} and then closed before returning.
   *
   * <p>For example: <pre>{@code
   * List<User> users = SafeSql.of("SELECT id, name FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(dataSource, User.class);
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
   *     .query(dataSource, String.class);
   * }</pre>
   *
   * <p>You can also map the result rows to Java Beans, for example: <pre>{@code
   * List<UserBean> users =
   *     SafeSql.of("SELECT id, name FROM Users WHERE name LIKE '%{name}%'", name)
   *         .query(dataSource, UserBean.class);
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
   * <li>The column names can be a superset, or a subset of the bean property names.
   *     This allows you to use "select *" in the query (when performance isn't a concern),
   *     or use a generic Java bean that may have more properties than some individual queries.
   * <li>If a bean property is of primitive type, and the corresponding query column value
   *     is null, the property will be left as is.
   * <li>If you can't make a bean property match a query column, consider annotating the setter
   *     method with the {@code @SqlName} annotation to customize the column name.
   * <li>Exception will be thrown if a column doesn't map to a settable property, and the columns
   *     aren't a superset. For example, you may have renamed a property but forgot to rename the
   *     corresponding query column. In such case, failing loudly and clearly is safer than letting
   *     the program silently run with corrupted state.
   * </ul>
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 9.0
   */
  public <T> List<T> query(DataSource dataSource, Class<? extends T> resultType) {
    return query(dataSource, stmt -> {}, resultType);
  }

  /**
   * Similar to {@link #query(DataSource, Class)}, but uses an existing connection.
   *
   * <p>It's usually more convenient to use the {@code DataSource} overload because you won't have
   * to manage the JDBC resources. Use this method if you need to reuse a connection
   * (mostly for multi-statement transactions).
   *
   * @since 8.7
   */
  public <T> List<T> query(Connection connection, Class<? extends T> resultType)
      throws SQLException {
    return query(connection, stmt -> {}, resultType);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code dataSource.getConnection()}.
   * The {@link ResultSet} will be consumed, transformed by {@code rowMapper} and then closed before returning.
   *
   * <p>For example: <pre>{@code
   * List<Long> ids = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(dataSource, row -> row.getLong("id"));
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 9.0
   */
  public <T> List<T> query(
      DataSource dataSource, SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    return query(dataSource, stmt -> {}, rowMapper);
  }

  /**
   * Similar to {@link #query(DataSource, Class)}, but with {@code settings}
   * (can be set via lambda like {@code stmt -> stmt.setMaxRows(100)})
   * to allow customization.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 9.0
   */
  public <T> List<T> query(
      DataSource dataSource,
      SqlConsumer<? super Statement> settings,
      Class<? extends T> resultType) {
    return query(dataSource, settings, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Similar to {@link #query(DataSource, SqlConsumer, Class)}, but uses an existing connection.
   *
   * <p>It's usually more convenient to use the {@code DataSource} overload because you won't have
   * to manage the JDBC resources. Use this method if you need to reuse a connection
   * (mostly for multi-statement transactions).
   *
   * @since 9.0
   */
  public <T> List<T> query(
      Connection connection,
      SqlConsumer<? super Statement> settings,
      Class<? extends T> resultType) throws SQLException {
    return query(connection, settings, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code dataSource.getConnection()},
   * using {@code settings} (can be set via lambda like {@code stmt -> stmt.setMaxRows(100)}).
   * The {@link ResultSet} will be consumed, transformed by {@code rowMapper} and then closed
   * before returning.
   *
   * <p>For example: <pre>{@code
   * List<Long> ids = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(dataSource, stmt -> stmt.setMaxRows(100000), row -> row.getLong("id"));
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @since 9.0
   */
  public <T> List<T> query(
      DataSource dataSource,
      SqlConsumer<? super Statement> settings,
      SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    try (Connection connection = dataSource.getConnection()) {
      return query(connection, settings, rowMapper);
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Similar to {@link #query(DataSource, SqlConsumer, SqlFunction)}, but uses an existing connection.
   *
   * <p>It's usually more convenient to use the {@code DataSource} overload because you won't have
   * to manage the JDBC resources. Use this method if you need to reuse a connection
   * (mostly for multi-statement transactions).
   *
   * <p>For example: <pre>{@code
   * List<Long> ids = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(connection, stmt -> stmt.setMaxRows(100000), row -> row.getLong("id"));
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @since 9.0
   */
  public <T> List<T> query(
      Connection connection,
      SqlConsumer<? super Statement> settings,
      SqlFunction<? super ResultSet, ? extends T> rowMapper) throws SQLException {
    requireNonNull(rowMapper);
    if (paramValues.isEmpty()) {
      try (Statement stmt = connection.createStatement()) {
        settings.accept(stmt);
        try (ResultSet resultSet = stmt.executeQuery(sql)) {
          return mapResults(resultSet, rowMapper);
        }
      }
    }
    try (PreparedStatement stmt = prepareStatement(connection)) {
      settings.accept(stmt);
      try (ResultSet resultSet = stmt.executeQuery()) {
        return mapResults(resultSet, rowMapper);
      }
    }
  }

  /**
   * Similar to {@link #query(DataSource, SqlFunction)}, but uses an existing connection.
   *
   * <p>It's usually more convenient to use the {@code DataSource} overload because you won't have
   * to manage the JDBC resources. Use this method if you need to reuse a connection
   * (mostly for multi-statement transactions).
   *
   * <p>For example: <pre>{@code
   * List<Long> ids = SafeSql.of("SELECT id FROM Users WHERE name LIKE '%{name}%'", name)
   *     .query(connection, row -> row.getLong("id"));
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   */
  public <T> List<T> query(
      Connection connection, SqlFunction<? super ResultSet, ? extends T> rowMapper)
      throws SQLException {
    return query(connection, stmt -> {}, rowMapper);
  }

  /**
   * Similar to {@link #query(DataSource, Class)}, but only fetches one row if the query
   * result includes at least one rows, or else returns {@code Optional.empty()}.
   *
   * <p>Suitable for queries that search by the primary key, for example: <pre>{@code
   * Optional<User> user =
   *     SafeSql.of("select id, name from Users where id = {id}", userId)
   *         .queryForOne(dataSource, User.class);
   * }</pre>
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @throws NullPointerException if the first column value is null, and {@code resultType}
   *   isn't a record or Java Bean.
   * @since 9.2
   */
  public <T> Optional<T> queryForOne(DataSource dataSource, Class<? extends T> resultType) {
    return queryForOne(dataSource, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Similar to {@link #query(Connection, Class)}, but only fetches one row if the query
   * result includes at least one rows, or else returns {@code Optional.empty()}.
   *
   * <p>Suitable for queries that search by the primary key, for example: <pre>{@code
   * Optional<User> user =
   *     SafeSql.of("select id, name from Users where id = {id}", userId)
   *         .queryForOne(connection, User.class);
   * }</pre>
   *
   * @throws NullPointerException if the first column value is null, and {@code resultType}
   *   isn't a record or Java Bean.
   * @since 9.2
   */
  public <T> Optional<T> queryForOne(Connection connection, Class<? extends T> resultType)
      throws SQLException {
    return queryForOne(connection, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Similar to {@link #query(DataSource, SqlFunction)}, but only fetches one row if the query
   * result includes at least one rows, or else returns {@code Optional.empty()}.
   *
   * <p>Suitable for queries that search by the primary key.
   *
   * @throws UncheckedSqlException wraps {@link SQLException} if failed
   * @throws NullPointerException if {@code rowMapper} returns null
   * @since 9.2
   */
  public <T> Optional<T> queryForOne(
      DataSource dataSource, SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    try (Connection connection = dataSource.getConnection()) {
      return queryForOne(connection, rowMapper);
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  /**
   * Similar to {@link #query(Connection, SqlFunction)}, but only fetches one row if the query
   * result includes at least one rows, or else returns {@code Optional.empty()}.
   *
   * <p>Suitable for queries that search by the primary key.
   *
   * @throws NullPointerException if {@code rowMapper} returns null
   * @since 9.2
   */
  public <T> Optional<T> queryForOne(
      Connection connection, SqlFunction<? super ResultSet, ? extends T> rowMapper)
      throws SQLException {
    try (Stream<T> stream =
        queryLazily(connection, stmt -> { stmt.setMaxRows(1); stmt.setFetchSize(1); }, rowMapper)) {
      return stream.peek(r -> {
        if (r == null) {
          throw new NullPointerException(
              "Null result not supported. Consider using a record or Java Bean with a nullable property " +
              "as the result type, or using queryLazily() or query() that support nulls.");
        }
      }).findFirst();
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
   * @since 8.7
   */
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public <T> Stream<T> queryLazily(Connection connection, Class<? extends T> resultType)
      throws SQLException {
    return queryLazily(connection, stmt -> {}, resultType);
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
      Connection connection, SqlFunction<? super ResultSet, ? extends T> rowMapper)
      throws SQLException {
    return queryLazily(connection, stmt -> {}, rowMapper);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection}, with {@code settings}
   * (can be set via lambda like {@code stmt -> stmt.setFetchSize(100)}), and then fetches the
   * results lazily in a stream.
   *
   * <p>Each result row is transformed into {@code resultType}.
   *
   * <p>For example: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT name FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<String> names = sql.queryLazily(
   *     connection, stmt -> stmt.setFetchSize(100), String.class)) {
   *   return names.findFirst();
   * }
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @since 9.0
   */
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public <T> Stream<T> queryLazily(
      Connection connection,
      SqlConsumer<? super Statement> settings,
      Class<? extends T> resultType) throws SQLException {
    return queryLazily(connection, settings, ResultMapper.toResultOf(resultType)::from);
  }

  /**
   * Executes the encapsulated SQL as a query against {@code connection}, with {@code settings}
   * (can be set via lambda like {@code stmt -> stmt.setFetchSize(100)}, and then fetches the
   * results lazily in a stream.
   *
   * <p>The returned {@code Stream} includes results transformed by {@code rowMapper}.
   * The caller must close it using try-with-resources idiom, which will close the associated
   * {@link Statement} and {@link ResultSet}.
   *
   * <p>For example: <pre>{@code
   * SafeSql sql = SafeSql.of("SELECT name FROM Users WHERE name LIKE '%{name}%'", name);
   * try (Stream<String> names = sql.queryLazily(
   *     connection, stmt -> stmt.setFetchSize(100), row -> row.getString("name"))) {
   *   return names.findFirst();
   * }
   * }</pre>
   *
   * <p>Internally it delegates to {@link PreparedStatement#executeQuery} or {@link
   * Statement#executeQuery} if this sql contains no JDBC binding parameters.
   *
   * @since 9.0
   */
  @MustBeClosed
  @SuppressWarnings("MustBeClosedChecker")
  public <T> Stream<T> queryLazily(
      Connection connection,
      SqlConsumer<? super Statement> settings,
      SqlFunction<? super ResultSet, ? extends T> rowMapper) throws SQLException {
    requireNonNull(rowMapper);
    if (paramValues.isEmpty()) {
      return lazy(connection::createStatement, settings, stmt -> stmt.executeQuery(sql), rowMapper);
    }
    return lazy(() -> prepareStatement(connection), settings, PreparedStatement::executeQuery, rowMapper);
  }

  @MustBeClosed
  private static <S extends Statement, T> Stream<T> lazy(
      SqlSupplier<? extends S> createStatement,
      SqlConsumer<? super Statement> settings,
      SqlFunction<? super S, ResultSet> execute,
      SqlFunction<? super ResultSet, ? extends T> rowMapper) throws SQLException {
    try (JdbcCloser closer = new JdbcCloser()) {
      S stmt = createStatement.get();
      closer.register(stmt::close);
      settings.accept(stmt);
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
   */
  @CanIgnoreReturnValue public int update(Connection connection) throws SQLException {
    return update(connection, stmt -> {});
  }

  /**
   * Similar to {@link #update(Connection)}, but with {@code settings}
   * (can be set via lambda like {@code stmt -> stmt.setQueryTimeout(100)})
   * to allow customization.
   *
   * @since 9.0
   */
  @CanIgnoreReturnValue public int update(
      Connection connection, SqlConsumer<? super Statement> settings) throws SQLException {
    if (paramValues.isEmpty()) {
      try (Statement stmt = connection.createStatement()) {
        settings.accept(stmt);
        return stmt.executeUpdate(sql);
      }
    }
    try (PreparedStatement stmt = prepareStatement(connection)) {
      settings.accept(stmt);
      return stmt.executeUpdate();
    }
  }

  /**
   * Returns a {@link PreparedStatement} with the encapsulated sql and parameters.
   *
   * <p>It's often more convenient to use {@link #query} or {@link #update} unless you need to
   * directly operate on the PreparedStatement.
   */
  @MustBeClosed
  public PreparedStatement prepareStatement(Connection connection) throws SQLException {
    return setParameters(connection.prepareStatement(sql));
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
   * cached PreparedStatement. The {@link ResultSet} objects are guaranteed to be closed after each use
   * before the PreparedStatement is closed.
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
   * cached PreparedStatement. The {@link ResultSet} objects are guaranteed to be closed after each use
   * before the PreparedStatement is closed.
   */
  public static <T> Template<List<T>> prepareToQuery(
      Connection connection, @CompileTimeConstant String template,
      SqlFunction<? super ResultSet, ? extends T> rowMapper) {
    requireNonNull(rowMapper);
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
   * Returns a query string with the parameter values embedded for easier debugging (logging,
   * testing, golden file etc.). DO NOT use it as the production SQL query because embedding the
   * parameter values isn't safe from SQL injection.
   *
   * @since 9.0
   */
  public String debugString() {
    StringFormat placeholderWithValue = new StringFormat("? /* {...} */");
    Iterator<?> args = paramValues.iterator();
    return all("?").replaceAllFrom(sql, q -> placeholderWithValue.format(args.next()));
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

  private static Template<SafeSql> unsafeTemplate(String template) {
    TemplatePlaceholdersContext context = new TemplatePlaceholdersContext(template);
    return StringFormat.template(template, (fragments, placeholders) -> {
      TemplateFragmentScanner scanner = new TemplateFragmentScanner(fragments);
      Builder builder = new Builder();
      class Liker {
        BiOptional<String, String> like(Substring.Match placeholder) {
          return biStream(allowedAffixes())
              .mapIfPresent((prefix, unused) -> likedStartingWith(prefix, placeholder))
              .findFirst();
        }

        private BiOptional<String, String> likedStartingWith(
            String prefix, Substring.Match placeholder) {
          String left = "'" + prefix;
          if (!context.lookbehind("LIKE " + left, placeholder)) return BiOptional.empty();
          context.rejectEscapeAfter(placeholder);
          return biStream(allowedAffixes())
              .mapKeysIfPresent(
                  suffix -> scanner.nextFragmentIfQuoted(left, placeholder, suffix + "'"))
              .findFirst()
              .peek((fragment, suffix) -> builder.appendSql(fragment))
              .map((fragment, suffix) -> BiOptional.of(prefix, suffix))
              .orElseThrow(
                  () -> new IllegalArgumentException(
                      "unsupported wildcard in LIKE " + left + placeholder));
        }

        private Stream<String> allowedAffixes() {
          return Stream.of("%", "_", "");
        }
      }
      Liker liker = new Liker();
      placeholders.forEach((placeholder, value) -> {
        checkMisuse(placeholder, value);
        String paramName = placeholder.skip(1, 1).toString().trim();
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
          if (value instanceof Optional) {
            String rhs = validateOptionalOperatorRhs(conditional);
            builder.appendSql(scanner.nextFragment());
            ((Optional<?>) value)
                .map(present -> innerSubquery(rhs, present))
                .ifPresent(builder::addSubQuery);
            return;
          }
          checkArgument(
              value instanceof Boolean,
              "conditional placeholder {%s->} can only be used with a boolean or Optional value; %s encountered.",
              conditional.before(),
              value.getClass().getName());
          builder.appendSql(scanner.nextFragment());
          if ((Boolean) value) {
            builder.appendSql(conditional.after().trim());
          }
          return;
        }
        rejectQuestionMark(paramName);
        checkArgument(
            !(value instanceof Optional),
            "%s: optional parameter not supported. " +
            "Consider using the {%s? -> ...} syntax, or SafeSql.when()?",
            paramName, paramName);
        if (value instanceof Iterable) {
          Iterator<?> elements = ((Iterable<?>) value).iterator();
          checkArgument(elements.hasNext(), "%s cannot be empty list", placeholder);
          if (placeholder.isImmediatelyBetween("'", "'")
              && context.lookaround("IN ('", placeholder, "')")
              && scanner.nextFragmentIfQuoted("'", placeholder, "'")
                  .map(builder::appendSql)
                  .isPresent()) {
            builder.addSubQuery(
                eachPlaceholderValue(placeholder, elements)
                    .mapToObj(SafeSql::mustBeString)
                    .map(PARAM::with)
                    .collect(joining(", ")));
            return;
          }
          builder.appendSql(scanner.nextFragment());
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
          } else if (context.lookaround("IN (", placeholder, ")")) {
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
          builder.appendSql(scanner.nextFragment()).addSubQuery((SafeSql) value);
          validateSubqueryPlaceholder(placeholder);
        } else if (
            scanner.nextFragmentIfQuoted("`", placeholder, "`")
                .map(builder::appendSql)
                .isPresent()) {
          String identifier = mustBeIdentifier("`" + placeholder + "`", value);
          checkArgument(identifier.length() > 0, "`%s` cannot be empty", placeholder);
          builder.appendSql("`" + identifier + "`");
        } else if (
            scanner.nextFragmentIfQuoted("\"", placeholder, "\"")
                .map(builder::appendSql)
                .isPresent()) {
          String identifier = mustBeIdentifier("\"" + placeholder + "\"", value);
          checkArgument(identifier.length() > 0, "\"%s\" cannot be empty", placeholder);
          builder.appendSql("\"" + identifier + "\"");
        } else if (
            scanner.nextFragmentIfQuoted("'", placeholder, "'")
                .map(builder::appendSql)
                .isPresent()) {
          builder.addParameter(paramName, mustBeString("'" + placeholder + "'", value));
        } else if (
            liker.like(placeholder)
                .map((prefix, suffix) ->
                    builder.addParameter(
                        paramName,
                        prefix + escapePercent(mustBeString(placeholder, value)) + suffix))
                .isPresent()) {
          builder.appendSql(" ESCAPE '^'");
        } else {
          checkMissingPlaceholderQuotes(placeholder);
          builder.appendSql(scanner.nextFragment()).addParameter(paramName, value);
        }
      });
      return builder.appendSql(scanner.nextFragment()).build();
    });
  }

  private static String validateOptionalOperatorRhs(Substring.Match operator) {
    String name = operator.before().trim();
    checkArgument(
        name.length() > 0 && OPTIONAL_PARAMETER.from(name).orElse("").equals(name),
        "optional placeholder {%s->} must be an identifier followed by '?'",
        name);
    String rhs = operator.after().trim();
    Set<String> referencedNames =
        OPTIONAL_PARAMETER.repeatedly().from(rhs).collect(toCollection(LinkedHashSet::new));
    checkArgument(
        referencedNames.remove(name),
        "optional parameter %s must be referenced at least once to the" + " right of {%s->}",
        name,
        name);
    checkArgument(
        referencedNames.isEmpty(), "Unexpected optional placeholders: %s", referencedNames);
    return rhs;
  }

  @SuppressWarnings("StringFormatArgsCheck")
  private static SafeSql innerSubquery(String optionalTemplate, Object arg) {
    Template<SafeSql> innerTemplate =
        unsafeTemplate(
            OPTIONAL_PARAMETER
                .repeatedly()
                .replaceAllFrom(optionalTemplate, p -> "{" + p.skip(0, 1) + "}"));
    Object[] innerArgs =
        OPTIONAL_PARAMETER.repeatedly().match(optionalTemplate).map(m -> arg).toArray();
    return innerTemplate.with(innerArgs);
  }

  private static void checkMisuse(Substring.Match placeholder, Object value) {
    validatePlaceholder(placeholder);
    checkArgument(
        value == null || !value.getClass().getName().endsWith("SafeQuery"),
        "%s: don't mix in SafeQuery with SafeSql.", placeholder);
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
    requireNonNull(connection);
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

  private static String checkIdentifier(CharSequence placeholder, String name) {
    // Make sure the backquoted string doesn't contain some special chars that may cause trouble.
    CharPredicate illegal = c -> Character.isISOControl(c) || "'\"`()[]{}\\~!@$^*,/?;".indexOf(c) >= 0;
    checkArgument(
        illegal.matchesNoneOf(name),
        "placeholder value for `%s` (%s) contains illegal character", placeholder, name);
    return name;
  }

  private static void validatePlaceholder(Substring.Match placeholder) {
    checkArgument(
        !placeholder.isImmediatelyBetween("`", "'"),
        "Incorrectly quoted placeholder: `%s'", placeholder);
    checkArgument(
        !placeholder.isImmediatelyBetween("'", "`"),
        "Incorrectly quoted placeholder: '%s`", placeholder);
  }

  private static <T> Stream<T> stream(Iterator<T> iterator) {
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
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
      paramValues.addAll(subQuery.paramValues);
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
          !(is('-').isSuffixOf(queryText) && snippet.startsWith("-")),
          "accidental line comment: -%s", snippet);
      checkArgument(
          !(is('/').isSuffixOf(queryText) && snippet.startsWith("*")),
          "accidental block comment: /%s", snippet);
      queryText.append(snippet);
    }
  }
}
