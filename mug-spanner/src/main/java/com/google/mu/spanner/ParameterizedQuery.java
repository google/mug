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
package com.google.mu.spanner;

import static com.google.mu.spanner.InternalUtils.checkArgument;
import static com.google.mu.spanner.InternalUtils.checkState;
import static com.google.mu.spanner.InternalUtils.skippingEmpty;
import static com.google.mu.util.Substring.all;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.Substring.word;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static com.google.mu.util.stream.MoreStreams.indexesFrom;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.cloud.ByteArray;
import com.google.cloud.Date;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.Interval;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.Value;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.annotations.TemplateStringArgsMustBeQuoted;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.StringFormat;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.ProtocolMessageEnum;

/**
 * An injection-safe SQL template for Google Cloud Spanner.
 *
 * <p>Spanner SQL is constructed using compile-time enforced templates, with support for dynamic SQL
 * and flexible subquery composition. The library manages the parameterized query and the query
 * parameters automatically, across all subqueries.
 *
 * <p>Use this class to create Spanner SQL template with parameters, to compose subqueries,
 * and even to parameterize by identifiers (table names, column names), without risking injection.
 * For example: <pre>{@code
 * List<String> groupColumns = ...;
 * ParameterizedQuery query = ParameterizedQuery.of(
 *     """
 *     SELECT `{group_columns}`, SUM(revenue) AS revenue
 *     FROM Sales
 *     WHERE sku = '{sku}'
 *     GROUP BY `{group_columns}`
 *     """,
 *     groupColumns, sku, groupColumns);
 * try (ResultSet resultSet = dbClient.singleUse().executeQuery(query.statement())) {
 *   ...
 * }
 * }</pre>
 *
 * <p>If a placeholder is quoted by backticks (`) or double quotes, it's interpreted and
 * validated as an identifier; all other placeholder values (except `ParameterizedQuery` objects,
 * which are subqueries) are passed through Spanner's parameterization query.
 *
 * <p>To be explicit, a compile-time check is in place to require all non-identifier string
 * placeholders to be single-quoted: this makes the template more self-evident, and helps to avoid
 * the mistake of forgetting the quotes and then sending the column name as a query parameter.
 *
 * <p>Except the placeholders, everything outside the curly braces are strictly WYSIWYG
 * (what you see is what you get), so you can copy paste them between the Java code and your SQL
 * console for quick testing and debugging.
 *
 * <dl><dt><STRONG>Compile-time Protection</STRONG></dt></dl>
 *
 * <p>The templating engine uses compile-time checks to guard against accidental use of
 * untrusted strings in the SQL, ensuring that they can only be sent as Spanner query parameters:
 * try to use a dynamically generated String as the SQL template and you'll get a compilation error.
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
 *               <version>8.7</version>
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
 * ParameterizedQuery's template syntax is designed to avoid control flows that could obfuscate SQL.
 * Instead, complex control flow such as {@code if-else}, nested {@code if}, loops etc. should be
 * performed in Java and passed in as subqueries.
 *
 * <p>Simple conditional subqueries (e.g. selecting a column if a flag is enabled) can use the
 * guard operator {@code ->} inside template placeholders:
 *
 * <pre>{@code
 *   ParameterizedQuery sql = ParameterizedQuery.of(
 *       "SELECT {shows_email -> email,} name FROM Users", showsEmail());
 * }</pre>
 *
 * The query text after the {@code ->} operator is the conditional subquery that's only included if
 * {@code showEmail()} returns true. The subquery can include arbitrary characters except curly
 * braces, so you can also have multi-line conditional subqueries.
 *
 * <dl><dt><STRONG>Complex Dynamic Subqueries</STRONG></dt></dl>
 *
 * By composing ParameterizedQuery objects that encapsulate subqueries, you can also parameterize by
 * arbitrary sub-queries that are computed dynamically.
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
 *   ParameterizedQuery usersQuery(UserCriteria criteria, @CompileTimeConstant String... columns) {
 *     return ParameterizedQuery.of(
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
 *   SELECT `email`, `lastName` FROM Users WHERE firstName LIKE @first_name
 * }</pre>
 *
 * <dl><dt><STRONG>Parameterize by Column Names or Table Names</STRONG></dt></dl>
 *
 * Sometimes you may wish to parameterize by table names, column names etc.
 * for which Spanner parameterization has no support.
 *
 * <p>The safe way to parameterize dynamic strings as <em>identifiers</em> is to backtick-quote
 * their placeholders in the SQL template. For example: <pre>{@code
 *   ParameterizedQuery.of("SELECT `{columns}` FROM Users", request.getColumns())
 * }</pre>
 * The backticks tell ParameterizedQuery that the string is supposed to be an identifier (or a list of
 * identifiers). ParameterizedQuery will sanity-check the string(s) to ensure injection safety.
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
 * <p>Note that with straight Spanner SQL, if you try to use the LIKE operator to match a user-provided
 * substring, i.e. using {@code LIKE '%foo%'} to search for "foo", this seemingly intuitive
 * syntax is actually incorect: <pre>{@code
 *   String searchTerm = ...;
 *   Statement.newBuilder("SELECT id FROM Users WHERE firstName LIKE '%@searchTerm%'")
 *       .bind("searchTerm", searchTerm)
 *       .build();
 * }</pre>
 *
 * Spanner considers the quoted "@searchTerm" as a literal so the query won't work as expected.
 * You'll need to use the following workaround: <pre>{@code
 *   String searchTerm = ...;
 *   Statement.newBuilder("SELECT id FROM Users WHERE firstName LIKE @searchTerm")
 *       .bind("searchTerm", "%" + searchTerm + "%")
 *       .build();
 * }</pre>
 *
 * And even then, if the {@code searchTerm} includes special characters like '%' or backslash ('\'),
 * they'll be interepreted as wildcards and escape characters, opening it up to a form of minor
 * SQL injection despite already using the parameterized SQL.
 *
 * <p>The ParameterizedQuery template protects you from this caveat. The most intuitive syntax does
 * exactly what you'd expect (and it escapes special characters too): <pre>{@code
 *   String searchTerm = ...;
 *   ParameterizedQuery sql = ParameterizedQuery.of(
 *       "SELECT id FROM Users WHERE firstName LIKE '%{search_term}%'", searchTerm);
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
 *   ParameterizedQuery sql = ParameterizedQuery.of(
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
 * <p>That said, placeholder names used in different subqueries are completely independent.
 * There is no risk of name clash. The template will ensure the final parameter name uniqueness.
 *
 * @since 9.0
 */
@Immutable
public final class ParameterizedQuery {
  private final String sql;
  @SuppressWarnings("Immutable") // it's an immutable list
  private final List<Parameter> parameters;

  private ParameterizedQuery(String sql) {
    this(sql, emptyList());
  }

  private ParameterizedQuery(String sql, List<Parameter> parameters) {
    this.sql = sql;
    this.parameters = parameters;
  }

  private static final Substring.Pattern OPTIONAL_PARAMETER =
      word().immediatelyBetween("", INCLUSIVE, "?", INCLUSIVE);
  private static final Substring.RepeatingPattern TOKENS =
      Stream.of(word(), first(c -> !Character.isWhitespace(c)))
          .collect(firstOccurrence())
          .repeatedly();
  private static final StringFormat PLACEHOLDER_ELEMENT_NAME =
      new StringFormat("{placeholder}[{index}]");
  private static final ParameterizedQuery FALSE = new ParameterizedQuery("(1 = 0)");
  private static final ParameterizedQuery TRUE = new ParameterizedQuery("(1 = 1)");
  private static final StringFormat.Template<ParameterizedQuery> PARAM = template("{param}");

  /** An empty SQL */
  public static final ParameterizedQuery EMPTY = new ParameterizedQuery("");

  /**
   * Returns {@link ParameterizedQuery} using {@code template} and {@code params}.
   *
   * <p>For example:
   *
   * <pre>{@code
   * List<Long> jobIds = ParameterizedQuery.of(
   *         "SELECT id FROM Jobs WHERE timestamp BETWEEN {start} AND {end}",
   *         startTime, endTime)
   *     .query(dataSource, Long.class);
   * }</pre>
   *
   * @param template the sql template
   * @param params The template parameters. Parameters that are themselves {@link ParameterizedQuery} are
   * considered trusted subqueries and are appended directly. Other types are passed through as
   * Spanner query parameters, with one exception: when the corresponding placeholder
   * is quoted by backticks like {@code `{columns}`}, its string parameter value
   * (or {@code Iterable<String>} parameter value) are directly appended (quotes, backticks,
   * backslash and other special characters are disallowed).
   * This makes it easy to parameterize by table names, column names etc.
   */
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  @TemplateFormatMethod
  @TemplateStringArgsMustBeQuoted
  public static ParameterizedQuery of(
      @TemplateString @CompileTimeConstant String template, Object... params) {
    return template(template).with(params);
  }

  /**
   * An optional query that's only rendered if {@code condition} is true; otherwise returns {@link
   * #EMPTY}. It's for use cases where a subquery is only conditionally added, for example the
   * following query will only include the userEmail column under super user mode:
   *
   * <pre>{@code
   * ParameterizedQuery query = ParameterizedQuery.of(
   *     "SELECT job_id, start_timestamp {user_email} FROM jobs",
   *     ParameterizedQuery.when(isSuperUser, ", user_email"));
   * }</pre>
   *
   * @param condition the guard condition to determine if {@code template} should be renderd
   * @param template the template to render if {@code condition} is true
   * @param params see {@link #of(String, Object...)} for discussion on the template arguments
   */
  @TemplateFormatMethod
  @TemplateStringArgsMustBeQuoted
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public static ParameterizedQuery when(
      boolean condition, @TemplateString @CompileTimeConstant String template, Object... params) {
    requireNonNull(template);
    requireNonNull(params);
    return condition ? of(template, params) : EMPTY;
  }

  /**
   * Returns this ParameterizedQuery if {@code condition} is true; otherwise returns {@link #EMPTY}.
   */
  public ParameterizedQuery when(boolean condition) {
    return condition ? this : EMPTY;
  }

  /**
   * Returns a {@link Template} of {@link ParameterizedQuery} based on the {@code template} string.
   * Useful for creating a constant to be reused with different parameters.
   *
   * <p>For example:
   *
   * <pre>{@code
   * private static final Template<ParameterizedQuery> FIND_USERS_BY_NAME =
   *     ParameterizedQuery.template("SELECT `{columns}` FROM Users WHERE name LIKE '%{name}%'");
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
  static Template<ParameterizedQuery> template(@CompileTimeConstant String template) {
    return unsafeTemplate(template);
  }

  /**
   * A collector that joins boolean query snippet using {@code AND} operator. The
   * AND'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "(1 = 1)".
   *
   * <p>Empty ParameterizedQuery elements are ignored and not joined.
   */
  public static Collector<ParameterizedQuery, ?, ParameterizedQuery> and() {
    return collectingAndThen(
        skippingEmpty(mapping(ParameterizedQuery::parenthesized, joining(" AND "))),
        query -> query.sql.isEmpty() ? TRUE : query);
  }

  /**
   * A collector that joins boolean query snippet using {@code OR} operator. The
   * OR'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "(1 = 0)".
   *
   * <p>Empty ParameterizedQuery elements are ignored and not joined.
   */
  public static Collector<ParameterizedQuery, ?, ParameterizedQuery> or() {
    return collectingAndThen(
        skippingEmpty(mapping(ParameterizedQuery::parenthesized, joining(" OR "))),
        query -> query.sql.isEmpty() ? FALSE : query);
  }

  /**
   * Returns a collector that joins ParameterizedQuery elements using {@code delimiter}.
   *
   * <p>Empty ParameterizedQuery elements are ignored and not joined.
   */
  public static Collector<ParameterizedQuery, ?, ParameterizedQuery> joining(
      @CompileTimeConstant String delimiter) {
    rejectReservedChars(delimiter);
    return skippingEmpty(
        Collector.of(
            Builder::new,
            (b, q) -> b.delimit(delimiter).addSubQuery(q),
            (b1, b2) -> b1.delimit(delimiter).addSubQuery(b2.build()),
            Builder::build));
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #when}),
   * returns the ParameterizedQuery produced from the {@code fallback} template and {@code args}.
   *
   * <p>Using this method, you can create a chain of optional queries like:
   *
   * <pre>{@code
   * ParameterizedQuery.of(
   *     """
   *     CREATE TABLE ...
   *     {cluster_by}
   *     """,
   *     when(enableCluster, "CLUSTER BY (`{cluster_columns}`)", clusterColumns)
   *         .orElse("-- no cluster"));
   * }</pre>
   */
  @TemplateFormatMethod
  @TemplateStringArgsMustBeQuoted
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public ParameterizedQuery orElse(
      @TemplateString @CompileTimeConstant String fallback, Object... params) {
    requireNonNull(fallback);
    requireNonNull(params);
    return orElse(() -> of(fallback, params));
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #when}),
   * returns the {@code fallback} query.
   */
  public ParameterizedQuery orElse(ParameterizedQuery fallback) {
    requireNonNull(fallback);
    return orElse(() -> fallback);
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #when}),
   * returns the query produced by the {@code fallback} supplier.
   */
  public ParameterizedQuery orElse(Supplier<ParameterizedQuery> fallback) {
    requireNonNull(fallback);
    return sql.isEmpty() ? fallback.get() : this;
  }

  /**
   * Creates an equivalent {@link Statement} to be passed to Spanner.
   *
   * <p>This is how ParameterizedQuery is eventually consumed.
   */
  public Statement statement() {
    List<String> bindingNames = toBindingNames();
    Iterator<String> atNames = bindingNames.stream().map("@"::concat).iterator();
    Statement.Builder builder =
        Statement.newBuilder(
            all("?").replaceAllFrom(sql, q -> atNames.next() + (q.isFollowedBy(CharPredicate.ALPHA) ? " " : "")));
    for (int i = 0; i < bindingNames.size(); i++) {
      builder.bind(bindingNames.get(i)).to(parameters.get(i).value);
    }
    return builder.build();
  }

  /**
   * Returns the SQL text with the template parameters translated to named Spanner
   * parameters, annotated with parameter values.
   */
  @Override
  public String toString() {
    Iterator<String> names = toBindingNames().iterator();
    StringFormat placeholderWithValue = new StringFormat("@{name} /* {...} */");
    Iterator<Value> values = parameters.stream().map(param -> param.value).iterator();
    return all("?").replaceAllFrom(sql, q -> placeholderWithValue.format(names.next(), values.next()));
  }

  @Override
  public int hashCode() {
    return sql.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ParameterizedQuery) {
      ParameterizedQuery that = (ParameterizedQuery) obj;
      return sql.equals(that.sql) && parameters.equals(that.parameters);
    }
    return false;
  }

  private ParameterizedQuery parenthesized() {
    return new ParameterizedQuery('(' + sql + ')', parameters);
  }

  private static Template<ParameterizedQuery> unsafeTemplate(String template) {
    List<Substring.Match> allTokens = TOKENS.match(template).collect(toList());
    Map<Integer, Integer> charIndexToTokenIndex =
        BiStream.zip(allTokens.stream(), indexesFrom(0))
            .mapKeys(Substring.Match::index)
            .collect(Collectors::toMap);
    return StringFormat.template(template, (fragments, placeholders) -> {
      Deque<String> texts = new ArrayDeque<>(fragments);
      Builder builder = new Builder();
      class SqlWriter {
        void writePlaceholder(Substring.Match placeholder, Object value) {
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
              builder.appendSql(texts.pop());
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
            builder.appendSql(texts.pop());
            if ((Boolean) value) {
              builder.appendSql(conditional.after().trim());
            }
            return;
          }
          rejectReservedChars(paramName);
          checkArgument(
              !(value instanceof Optional),
              "%s: optional parameter not supported. " +
              "Consider using the {%s? -> ...} syntax, or ParameterizedQuery.when()?",
              paramName, paramName);
          if (value instanceof Collection) {
            Collection<?> elements = (Collection<?>) value;
            checkArgument(
                elements.size() > 0,
                "Cannot infer type from empty collection. Use explicit Value instead for %s", placeholder);
            if (placeholder.isImmediatelyBetween("'", "'")
                && appendBeforeQuotedPlaceholder("'", placeholder, "'")) {
              eachPlaceholderValue(placeholder, elements)
                  .forEach(ParameterizedQuery::mustBeString);
              builder.addArrayParameter(paramName, elements);
              return;
            }
            builder.appendSql(texts.pop());
            if (placeholder.isImmediatelyBetween("`", "`")) {
              builder.appendSql(
                  eachPlaceholderValue(placeholder, elements)
                      .mapToObj(ParameterizedQuery::mustBeIdentifier)
                      .collect(Collectors.joining("`, `")));
            } else if (placeholder.isImmediatelyBetween("\"", "\"")) {
              builder.appendSql(
                  eachPlaceholderValue(placeholder, elements)
                      .mapToObj(ParameterizedQuery::mustBeIdentifier)
                      .collect(Collectors.joining("\", \"")));
            } else if (elements.stream().allMatch(ParameterizedQuery.class::isInstance)) {
              builder.addSubQuery(elements.stream().map(ParameterizedQuery.class::cast).collect(joining(", ")));
              validateSubqueryPlaceholder(placeholder);
            } else {
              builder.addArrayParameter(paramName, elements);
            }
          } else if (value instanceof ParameterizedQuery) {
            builder.appendSql(texts.pop()).addSubQuery((ParameterizedQuery) value);
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
                .addParameter(paramName, "%" + escapePercent(mustBeString(placeholder, value)) + "%");
          } else if (lookbehind("LIKE '%", placeholder)
              && appendBeforeQuotedPlaceholder("'%", placeholder, "'")) {
            rejectEscapeAfter(placeholder);
            builder
                .addParameter(paramName, "%" + escapePercent(mustBeString(placeholder, value)));
          } else if (lookbehind("LIKE '", placeholder)
              && appendBeforeQuotedPlaceholder("'", placeholder, "%'")) {
            rejectEscapeAfter(placeholder);
            builder
                .addParameter(paramName, escapePercent(mustBeString(placeholder, value)) + "%");
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
              "ESCAPE not supported after %s. Just leave the placeholder alone and ParameterizedQuery will auto escape.",
              placeholder);
        }

        private boolean lookahead(Substring.Match placeholder, String rightPattern) {
          List<String> lookahead = TOKENS.from(rightPattern).collect(toList());
          int closingBraceIndex = placeholder.index() + placeholder.length() - 1;
          int nextTokenIndex = charIndexToTokenIndex.get(closingBraceIndex) + 1;
          return BiStream.zip(lookahead, allTokens.subList(nextTokenIndex, allTokens.size()))
                  .filter((s, t) -> s.equalsIgnoreCase(t.toString()))
                  .count() == lookahead.size();
        }

        private boolean lookbehind(String leftPattern, Substring.Match placeholder) {
          List<String> lookbehind = TOKENS.from(leftPattern).collect(toList());
          List<Substring.Match> leftTokens =
              allTokens.subList(0, charIndexToTokenIndex.get(placeholder.index()));
          return BiStream.zip(reverse(lookbehind), reverse(leftTokens))  // right-to-left
                  .filter((s, t) -> s.equalsIgnoreCase(t.toString()))
                  .count() == lookbehind.size();
        }
      }
      placeholders
          .peek(ParameterizedQuery::checkMisuse)
          .forEachOrdered(new SqlWriter()::writePlaceholder);
      checkState(texts.size() == 1, "should have only one text left, got: %s", texts);
      return builder.appendSql(texts.pop()).build();
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
  private static ParameterizedQuery innerSubquery(String optionalTemplate, Object arg) {
    Template<ParameterizedQuery> innerTemplate =
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
        "%s: don't mix in SafeQuery with ParameterizedQuery.", placeholder);
  }

  @CanIgnoreReturnValue private static String rejectReservedChars(String sql) {
    checkArgument(sql.indexOf('?') < 0, "please use named {placeholder} instead of '?'");
    checkArgument(sql.indexOf('@') < 0, "please use named {placeholder} instead of '@'");
    return sql;
  }

  private static void validateSubqueryPlaceholder(Substring.Match placeholder) {
    checkArgument(
        !placeholder.isImmediatelyBetween("'", "'"),
        "ParameterizedQuery should not be quoted: '%s'", placeholder);
    checkArgument(
        !placeholder.isImmediatelyBetween("\"", "\""),
        "ParameterizedQuery should not be quoted: \"%s\"", placeholder);
    checkArgument(
        !placeholder.isImmediatelyBetween("`", "`"),
        "ParameterizedQuery should not be backtick quoted: `%s`", placeholder);
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

  private static BiStream<String, ?> eachPlaceholderValue(
      Substring.Match placeholder, Collection<?> elements) {
    return BiStream.zip(indexesFrom(0), elements.stream())
        .mapKeys(index -> PLACEHOLDER_ELEMENT_NAME.format(placeholder, index));
  }

  private static String escapePercent(String s) {
    return first(c -> c == '\\' || c == '%' || c == '_').repeatedly().replaceAllFrom(s, c -> "\\" + c);
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

  private static Value toValue(String name, Object obj) {
    checkArgument(obj != null, "Cannot infer type from null. Use explicit Value instead for {%s}", name);
    if (obj instanceof Value) {
      return (Value) obj;
    }
    if (obj instanceof boolean[]) {
      return Value.boolArray((boolean[]) obj);
    }
    if (obj instanceof long[]) {
      return Value.int64Array((long[]) obj);
    }
    if (obj instanceof float[]) {
      return Value.float32Array((float[]) obj);
    }
    if (obj instanceof double[]) {
      return Value.float64Array((double[]) obj);
    }
    return ValueType.inferFromPojo(name, obj).toValue(obj);
  }

  private static Value toArrayValue(String name, Collection<?> elements) {
    Set<ValueType> inferred = elements.stream()
        .filter(Objects::nonNull)
        .map(obj -> ValueType.inferFromPojo(name, obj))
        .collect(toSet());
    checkArgument(inferred.size() > 0, "Failed to infer array type for {%s}", name);
    checkArgument(inferred.size() == 1, "Conflicting array type inferred: %s", inferred);
    return inferred.iterator().next().toArrayValue(elements);
  }

  private static Timestamp toTimestamp(Instant time) {
    return Timestamp.ofTimeSecondsAndNanos(time.getEpochSecond(), time.getNano());
  }

  private static Date toDate(LocalDate date) {
    return Date.fromYearMonthDay(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  private enum ValueType {
    BOOL {
      @Override public Value toValue(Object obj) {
        return Value.bool((Boolean) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.boolArray((Collection<Boolean>) elements);
      }
    },
    STRING {
      @Override public Value toValue(Object obj) {
        return Value.string((String) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.stringArray((Collection<String>) elements);
      }
    },
    INT {
      @Override public Value toValue(Object obj) {
        return Value.int64(((Integer) obj).longValue());
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<Integer> ints = (Collection<Integer>) elements;
        return Value.int64Array(ints.stream().map(Integer::longValue).collect(toList()));
      }
    },
    LONG {
      @Override public Value toValue(Object obj) {
        return Value.int64(((Long) obj));
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.int64Array((Collection<Long>) elements);
      }
    },
    FLOAT {
      @Override public Value toValue(Object obj) {
        return Value.float32((Float) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.float32Array((Collection<Float>) elements);
      }
    },
    DOUBLE {
      @Override public Value toValue(Object obj) {
        return Value.float64((Double) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.float64Array((Collection<Double>) elements);
      }
    },
    BIG_DECIMAL {
      @Override public Value toValue(Object obj) {
        return Value.numeric((BigDecimal) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.numericArray((Collection<BigDecimal>) elements);
      }
    },
    INSTANT {
      @Override public Value toValue(Object obj) {
        return Value.timestamp(toTimestamp((Instant) obj));
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<Instant> instants = (Collection<Instant>) elements;
        return Value.timestampArray(instants.stream().map(t -> toTimestamp(t)).collect(toList()));
      }
    },
    ZONED_DATE_TIME {
      @Override public Value toValue(Object obj) {
        return Value.timestamp(toTimestamp((((ZonedDateTime) obj).toInstant())));
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<ZonedDateTime> times = (Collection<ZonedDateTime>) elements;
        return Value.timestampArray(times.stream().map(t -> toTimestamp(t.toInstant())).collect(toList()));
      }
    },
    OFFSET_DATE_TIME {
      @Override public Value toValue(Object obj) {
        return Value.timestamp(toTimestamp((((OffsetDateTime) obj).toInstant())));
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<OffsetDateTime> times = (Collection<OffsetDateTime>) elements;
        return Value.timestampArray(times.stream().map(t -> toTimestamp(t.toInstant())).collect(toList()));
      }
    },
    LOCAL_DATE {
      @Override public Value toValue(Object obj) {
        return Value.date(toDate((LocalDate) obj));
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<LocalDate> dates = (Collection<LocalDate>) elements;
        return Value.dateArray(dates.stream().map(d -> toDate(d)).collect(toList()));
      }
    },
    UUID {
      @Override public Value toValue(Object obj) {
        return Value.uuid((UUID) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.uuidArray((Collection<UUID>) elements);
      }
    },
    BYTE_ARRAY {
      @Override public Value toValue(Object obj) {
        return Value.bytes((ByteArray) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.bytesArray((Collection<ByteArray>) elements);
      }
    },
    INTERVAL {
      @Override public Value toValue(Object obj) {
        return Value.interval((Interval) obj);
      }
      @SuppressWarnings("unchecked")  // checked by toArrayValue static method
      @Override public Value toArrayValue(Collection<?> elements) {
        return Value.intervalArray((Collection<Interval>) elements);
      }
    },
    PROTO_ENUM {
      @Override public Value toValue(Object obj) {
        return Value.protoEnum((ProtocolMessageEnum) obj);
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<ProtocolMessageEnum> enums = (Collection<ProtocolMessageEnum>) elements;
        return Value.protoEnumArray(enums, enums.iterator().next().getDescriptorForType());
      }
    },
    PROTO {
      @Override public Value toValue(Object obj) {
        return Value.protoMessage((AbstractMessage) obj);
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<AbstractMessage> messages = (Collection<AbstractMessage>) elements;
        return Value.protoMessageArray(messages, messages.iterator().next().getDescriptorForType());
      }
    },
    STRUCT {
      @Override public Value toValue(Object obj) {
        return Value.struct((Struct) obj);
      }
      @Override public Value toArrayValue(Collection<?> elements) {
        @SuppressWarnings("unchecked")  // checked by toArrayValue static method
        Collection<Struct> structs = (Collection<Struct>) elements;
        return Value.structArray(structs.iterator().next().getType(), structs);
      }
    },
    ;

    static ValueType inferFromPojo(String name, Object obj) {
      if (obj instanceof Boolean) {
        return BOOL;
      }
      if (obj instanceof String) {
        return STRING;
      }
      if (obj instanceof Long) {
        return LONG;
      }
      if (obj instanceof Integer) {
        return INT;
      }
      if (obj instanceof Float) {
        return FLOAT;
      }
      if (obj instanceof Double) {
        return DOUBLE;
      }
      if (obj instanceof BigDecimal) {
        return BIG_DECIMAL;
      }
      if (obj instanceof Instant) {
        return INSTANT;
      }
      if (obj instanceof ZonedDateTime) {
        return ZONED_DATE_TIME;
      }
      if (obj instanceof OffsetDateTime) {
        return OFFSET_DATE_TIME;
      }
      if (obj instanceof LocalDate) {
        return LOCAL_DATE;
      }
      if (obj instanceof UUID) {
        return UUID;
      }
      if (obj instanceof AbstractMessage) {
        return PROTO;
      }
      if (obj instanceof ProtocolMessageEnum) {
        return PROTO_ENUM;
      }
      if (obj instanceof Struct) {
        return STRUCT;
      }
      if (obj instanceof ByteArray) {
        return BYTE_ARRAY;
      }
      if (obj instanceof Interval) {
        return INTERVAL;
      }
      StringFormat message = new StringFormat(
          "Cannot convert object of {class} to Value for {{name}}. " +
          "Consider using the static factory methods in Value class to convert explicitly.");
      throw new IllegalArgumentException(message.format(obj.getClass(), name));
    }

    abstract Value toValue(Object obj);
    abstract Value toArrayValue(Collection<?> elements);
  }

  private static <T> List<T> reverse(List<T> list) {
    return new AbstractList<T>() {
      @Override public T get(int i) {
        return list.get(list.size() - i - 1);
      }

      @Override public int size() {
        return list.size();
      }
    };
  }

  private static final class Builder {
    private final StringBuilder queryText = new StringBuilder();
    private final List<Parameter> parameters = new ArrayList<>();

    @CanIgnoreReturnValue Builder appendSql(String snippet) {
      safeAppend(rejectReservedChars(snippet));
      return this;
    }

    @CanIgnoreReturnValue Builder addParameter(String name, Object value) {
      Parameter parameter = new Parameter(name, toValue(name, value));
      safeAppend("?");  // Will be replaced later if duplicative
      parameters.add(parameter);
      return this;
    }

    @CanIgnoreReturnValue Builder addArrayParameter(String name, Collection<?> elements) {
      Parameter parameter = new Parameter(name, toArrayValue(name, elements));
      queryText.append("?");
      parameters.add(parameter);
      return this;
    }

    @CanIgnoreReturnValue Builder addSubQuery(ParameterizedQuery subQuery) {
      safeAppend(subQuery.sql);
      parameters.addAll(subQuery.parameters);
      return this;
    }

    @CanIgnoreReturnValue Builder delimit(String delim) {
      if (queryText.length() > 0) {
        safeAppend(delim);
      }
      return this;
    }

    ParameterizedQuery build() {
      return new ParameterizedQuery(
          queryText.toString(), unmodifiableList(new ArrayList<>(parameters)));
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

  private List<String> toBindingNames() {
    Map<String, AtomicInteger> nameCounts = new HashMap<>();
    List<String> bindingNames = new ArrayList<>();
    for (Parameter param : parameters) {
      int duplicates =
          nameCounts.computeIfAbsent(param.name, n -> new AtomicInteger()).getAndIncrement();
      bindingNames.add(duplicates == 0 ? param.name : param.name + "_" + duplicates);
    }
    return bindingNames;
  }

  @Immutable
  private static final class Parameter {
    private final String name;
    @SuppressWarnings("Immutable") // it's immutable
    private final Value value;

    Parameter(String name, Value value) {
      this.name = word().repeatedly().from(name).collect(Collectors.joining("_"));
      this.value = value;
      checkArgument(
          CharPredicate.ALPHA.isPrefixOf(name),
          "Parameter name (%s) must start with alpha character", name);
    }

    @Override public String toString() {
      return "@" + name;
    }

    @Override public int hashCode() {
      return value.hashCode();
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof Parameter) {
        Parameter that = (Parameter) obj;
        return name.equals(that.name) && value.equals(that.value);
      }
      return false;
    }
  }
}
