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

import static com.google.common.base.CharMatcher.anyOf;
import static com.google.common.base.CharMatcher.javaIsoControl;
import static com.google.common.base.CharMatcher.whitespace;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.mu.safesql.InternalCollectors.skippingEmpty;
import static com.google.mu.safesql.TrustedTypes.TRUSTED_SQL_TYPE_NAME;
import static com.google.mu.safesql.TrustedTypes.isTrusted;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;

import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Iterables;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.annotations.RequiresGuava;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;
import com.google.mu.util.StringFormat.Template;
import com.google.mu.util.Substring;

/**
 * A piece of provably-safe (from SQL injection) query string constructed by the
 * combination of a compile-time string constant, other SafeQuery, safe literal
 * values (booleans, enum constant names, numbers etc.), and/or
 * mandatorily-quoted, auto-escaped string values.
 *
 * <p>It's best practice for your db layer API to require SafeQuery instead of
 * String as the parameter to ensure safety. Internally, you can use
 * {@link #toString} to access the query string.
 *
 * <p>This class supports generating SQL based on a string template in the syntax
 * of {@link TemplateString}, and with the same compile-time protection. To
 * prevent SQL injection errors, special characters of string values are
 * automatically escaped; and negative numbers are automatically enclosed by
 * parenthesis.
 *
 * <p>Compared to {@link SafeSql}, SafeQuery is independent of the underlying DB engine.
 * It simply wraps a sanitized query string, using auto-escaping and unicode encoding to prevent SQL
 * injection. It can be used for databases that don't support JDBC parameterization, or if you
 * prefer dynamic SQL over parameterization for debugging reasons etc.
 *
 * <p>API Note: it's common that a piece of the query needs to be conditionally guarded, you can use
 * the conditional query operator {@code ->} in the placeholder, for example:
 *
 * <pre>{@code
 * SafeQuery.of("SELECT {shows_id -> id,} name FROM tbl", showsId());
 * }</pre>
 *
 * <p>This class is Android compatible.
 *
 * @since 7.0
 * @deprecated You most likely need {@link SafeSql} to work with JDBC
 */
@RequiresGuava
@Immutable
@CheckReturnValue
@Deprecated
public final class SafeQuery {
  /**
   * An empty query string.
   *
   * @since 8.1
   */
  public static final SafeQuery EMPTY = new SafeQuery("");
  private static final CharMatcher ILLEGAL_IDENTIFIER_CHARS = anyOf("'\"`()[]{}\\~!@$^*,/?;").or(javaIsoControl());

  private final String query;

  private SafeQuery(String query) {
    this.query = query;
  }

  /** Returns a query using a constant query template filled with {@code args}. */
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  @TemplateFormatMethod
  public static SafeQuery of(@CompileTimeConstant @TemplateString String query, Object... args) {
    return template(query).with(args);
  }

  /**
   * An optional query that's only rendered if {@code condition} is true; otherwise returns {@link
   * #EMPTY}. It's for use cases where a subquery is only conditionally added, for example the
   * following query will only include the userEmail column under super user mode:
   *
   * <pre>{@code
   * SafeQuery query = SafeQuery.of(
   *     "SELECT job_id, start_timestamp {user_email} FROM jobs",
   *     SafeQuery.when(isSuperUser, ", user_email"));
   * }</pre>
   *
   * @since 8.1
   */
  @TemplateFormatMethod
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public static SafeQuery when(
      boolean condition, @TemplateString @CompileTimeConstant String query, Object... args) {
    checkNotNull(query);
    checkNotNull(args);
    return condition ? of(query, args) : EMPTY;
  }

  /**
   * Returns this SafeQuery if {@code condition} is true; otherwise returns {@link #EMPTY}.
   *
   * @since 8.4
   */
  public SafeQuery when(boolean condition) {
    return condition ? this : EMPTY;
  }

  /**
   * An optional query that's only rendered if {@code arg} is present; otherwise returns {@link
   * #EMPTY}. It's for use cases where a subquery is only added when present, for example the
   * following query will add the WHERE clause if the filter is present:
   *
   * <pre>{@code
   * SafeQuery query = SafeQuery.of(
   *     "SELECT * FROM jobs {where}",
   *     SafeQuery.optionally("WHERE {filter}", getOptionalFilter()));
   * }</pre>
   *
   * @since 8.1
   */
  @TemplateFormatMethod
  @SuppressWarnings("StringFormatArgsCheck") // protected by @TemplateFormatMethod
  public static SafeQuery optionally(
      @TemplateString @CompileTimeConstant String query, Optional<?> arg) {
    checkNotNull(query);
    return arg.map(v -> of(query, v)).orElse(EMPTY);
  }

  /**
   * Creates a template with the placeholders to be filled by subsequent
   * {@link StringFormat.Template#with} calls. For example:
   *
   * <pre>{@code
   *   private static final Template<SafeQuery> FIND_CASE_BY_ID = SafeQuery
   *       .template("SELECT * FROM `{project_id}.{dataset}.Cases` WHERE CaseId = '{case_id}'");
   *
   *   // ...
   *   SafeQuery query = FIND_CASE_BY_ID.with(projectId, dataset, caseId);
   * }</pre>
   *
   * <p>
   * Placeholders must follow the following rules:
   *
   * <ul>
   * <li>String placeholder values can only be used when the corresponding
   * placeholder is quoted in the template string (such as '{case_id}').
   * <ul>
   * <li>If quoted by single quotes (') or double quotes ("), the placeholder
   * value will be auto-escaped.
   * <li>If quoted by backticks (`), the placeholder value cannot contain
   * backtick, quotes and other special characters.
   * </ul>
   * <li>Unquoted placeholders are assumed to be symbols or subqueries. Strings
   * (CharSequence) and characters cannot be used as placeholder value. If you
   * have to pass a string, wrap it inside a {@code TrustedSqlString} (you can
   * configure the type you trust by setting the
   * "com.google.mu.safesql.SafeQuery.trusted_sql_type" system property).
   * <li>An Iterable's elements are auto expanded and joined by a comma and space
   * (", ").
   * <li>If the iterable's placeholder is quoted as in {@code WHERE id IN
   * ('{ids}')}, every expanded element is quoted (and auto-escaped). For example,
   * passing {@code asList("foo",
   *       "bar")} as the value for placeholder {@code '{ids}'} will result in
   * {@code WHERE id IN
   *       ('foo', 'bar')}.
   * <li>If the Iterable's placeholder is backtick-quoted, every expanded element
   * is backtick-quoted. For example, passing {@code asList("col1", "col2")} as
   * the value for placeholder {@code `{cols}`} will result in
   * {@code `col`, `col2`}.
   * <li>Subqueries can be composed by using {@link SafeQuery} as the placeholder
   * value.
   * </ul>
   *
   * <p>
   * The placeholder values passed through {@link StringFormat.Template#with} are
   * checked by ErrorProne to ensure the correct number of arguments are passed,
   * and in the expected order.
   *
   * <p>
   * Supported expression types:
   *
   * <ul>
   * <li>null (NULL)
   * <li>Boolean
   * <li>Numeric (negative numbers will be enclosed in parenthesis to avoid
   * semantic change)
   * <li>Enum
   * <li>String (must be quoted in the template)
   * <li>{@code TrustedSqlString}
   * <li>{@link SafeQuery}
   * <li>Iterable
   * </ul>
   *
   * <p>
   * If you are trying to create SafeQuery inline like
   * {@code SafeQuery.template(tmpl).with(a, b)}, please use
   * {@code SafeQuery.of(tmpl, a, b)} instead.
   */
  public static Template<SafeQuery> template(@CompileTimeConstant String formatString) {
    return new Translator().translate(formatString);
  }

  /**
   * A collector that joins boolean query snippets using {@code AND} operator. The
   * AND'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "TRUE".
   *
   * <p>Empty SafeQuery elements are ignored and not joined.
   *
   * @since 7.2
   */
  public static Collector<SafeQuery, ?, SafeQuery> and() {
    return collectingAndThen(
        skippingEmpty(mapping(SafeQuery::parenthesized, joining(" AND "))),
        query -> query.toString().isEmpty() ? new SafeQuery("TRUE") : query);
  }

  /**
   * A collector that joins boolean query snippets using {@code OR} operator. The
   * OR'ed sub-queries will be enclosed in pairs of parenthesis to avoid
   * ambiguity. If the input is empty, the result will be "FALSE".
   *
   * <p>Empty SafeQuery elements are ignored and not joined.
   *
   * @since 7.2
   */
  public static Collector<SafeQuery, ?, SafeQuery> or() {
    return collectingAndThen(
        skippingEmpty(mapping(SafeQuery::parenthesized, joining(" OR "))),
        query -> query.toString().isEmpty() ? new SafeQuery("FALSE") : query);
  }

  /**
   * Returns a collector that can join {@link SafeQuery} objects using
   * {@code delim} as the delimiter.
   *
   * <p>Empty SafeQuery elements are ignored and not joined.
   */
  public static Collector<SafeQuery, ?, SafeQuery> joining(@CompileTimeConstant String delim) {
    return collectingAndThen(
        skippingEmpty(mapping(SafeQuery::toString, Collectors.joining(checkNotNull(delim)))), SafeQuery::new);
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #optionally} or {@link #when}),
   * returns the SafeQuery produced from the {@code fallback} template and {@code args}.
   *
   * <p>Using this method, you can create a chain of optional queries like:
   *
   * <pre>{@code
   * import static c.g.common.labs.base.Optionals.nonEmpty;
   *
   * SafeQuery.of(
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
  public SafeQuery orElse(@TemplateString @CompileTimeConstant String fallback, Object... args) {
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
  public SafeQuery orElse(SafeQuery fallback) {
    checkNotNull(fallback);
    return orElse(() -> fallback);
  }

  /**
   * If {@code this} query is empty (likely from a call to {@link #optionally} or {@link #when}),
   * returns the query produced by the {@code fallback} supplier.
   *
   * @since 8.3
   */
  public SafeQuery orElse(Supplier<SafeQuery> fallback) {
    checkNotNull(fallback);
    return query.isEmpty() ? fallback.get() : this;
  }

  /** Returns the encapsulated SQL query. */
  @Override
  public String toString() {
    return query;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof SafeQuery) {
      return query.equals(((SafeQuery) obj).query);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return query.hashCode();
  }

  private SafeQuery parenthesized() {
    return new SafeQuery("(" + query + ")");
  }

  /**
   * when a sub-query starts with '-' (e.g. from a negative number), it can cause
   * surprising semantics when combined with other subqueries. For example "{a} -
   * {b}" when b is negative will result in "a -- b", where it becomes a line
   * comment.
   *
   * <p>
   * To be safe, we wrap the expression within a pair of parenthesis in such case.
   */
  private SafeQuery guardDashExpression(Substring.Match placeholder) {
    return query.startsWith("-") && !placeholder.isImmediatelyBetween("(", ")") ? parenthesized() : this;
  }

  private static Iterable<?> skipEmptySubqueries(Iterable<?> iterable) {
    return Iterables.filter(iterable, v -> !(v instanceof SafeQuery && v.toString().isEmpty()));
  }

  /**
   * An SPI class for subclasses to provide additional translation from
   * placeholder values to safe query strings.
   *
   * @since 7.2
   * @deprecated You most likely need {@link SafeSql} to work with JDBC
   */
  @Deprecated
  public static class Translator {
    protected Translator() {}

    /**
     * Translates {@code template} to a factory of {@link SafeQuery} by filling the
     * provided parameters in the places of corresponding placeholders.
     */
    public final Template<SafeQuery> translate(@CompileTimeConstant String template) {
      return StringFormat.template(template, (fragments, placeholders) -> {
        Iterator<String> it = fragments.iterator();
        return new SafeQuery(
            placeholders.collect(new StringBuilder(), (b, p, v) -> b.append(it.next()).append(fillInPlaceholder(p, v)))
                .append(it.next()).toString());
      });
    }

    /**
     * Called if a placeholder {@code value} is a non-string, non-Iterable literal
     * appearing unquoted in the template.
     *
     * <p>
     * Subclasses should translate their trusted types and delegate to {@code
     * super.translateLiteral()} for all other types.
     *
     * @param placeholder the placeholder in the template to be filled with
     *                    {@code value}
     * @param value       the literal value to fill in (not CharSequence or
     *                    Character). Can be null.
     * @return a sub-query to be filled into the result query in the place of
     *         {@code placeholder}
     *
     * @since 8.0
     */
    protected SafeQuery translateLiteral(Substring.Match placeholder, Object value) {
      if (value == null) {
        return new SafeQuery("NULL");
      }
      if (value instanceof Boolean) {
        return new SafeQuery(value.equals(Boolean.TRUE) ? "TRUE" : "FALSE");
      }
      if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
        return new SafeQuery(value.toString()).guardDashExpression(placeholder);
      }
      if (value instanceof Float || value instanceof Double) {
        double doubleValue = ((Number) value).doubleValue();
        checkArgument(!Double.isNaN(doubleValue), "%s: NaN value not supported", placeholder);
        checkArgument(!Double.isInfinite(doubleValue), "%s: infinite value not supported", placeholder);
        DecimalFormat fmt = new DecimalFormat("#.#");
        fmt.setMinimumIntegerDigits(1);
        fmt.setMaximumFractionDigits(9);
        return new SafeQuery(fmt.format(doubleValue)).guardDashExpression(placeholder);
      }
      if (value instanceof UnsignedInteger || value instanceof UnsignedLong) {
        return new SafeQuery(value.toString());
      }
      if (value instanceof Enum) {
        return new SafeQuery(((Enum<?>) value).name());
      }
      throw new IllegalArgumentException(placeholder + ": unsupported argument type " + value.getClass().getName());
    }

    private String fillInPlaceholder(Substring.Match placeholder, Object value) {
      validatePlaceholder(placeholder);
      Substring.Match conditional = first("->").in(placeholder.skip(1, 1).toString()).orElse(null);
      if (conditional != null) {
        checkArgument(
            !placeholder.isImmediatelyBetween("`", "`"),
            "boolean placeholder {%s->} shouldn't be backtick quoted",
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
        return (Boolean) value ? whitespace().trimFrom(conditional.after()) : "";
      }
      if (value instanceof Iterable) {
        Iterable<?> iterable = skipEmptySubqueries((Iterable<?>) value);
        if (placeholder.isImmediatelyBetween("`", "`")) { // If backquoted, it's a list of symbols
          return String.join("`, `", Iterables.transform(iterable, v -> backquoted(placeholder, v)));
        }
        if (placeholder.isImmediatelyBetween("'", "'")) {
          return String.join("', '", Iterables.transform(iterable, v -> quotedBy('\'', placeholder, v)));
        }
        if (placeholder.isImmediatelyBetween("\"", "\"")) {
          return String.join("\", \"", Iterables.transform(iterable, v -> quotedBy('"', placeholder, v)));
        }
        return String.join(", ", Iterables.transform(iterable, v -> unquoted(placeholder, v)));
      }
      if (placeholder.isImmediatelyBetween("'", "'")) {
        return quotedBy('\'', placeholder, value);
      }
      if (placeholder.isImmediatelyBetween("\"", "\"")) {
        return quotedBy('"', placeholder, value);
      }
      if (placeholder.isImmediatelyBetween("`", "`")) {
        return backquoted(placeholder, value);
      }
      return unquoted(placeholder, value);
    }

    private String unquoted(Substring.Match placeholder, Object value) {
      if (value != null && isTrusted(value)) {
        String s = value.toString();
        checkArgument(
            !(placeholder.isPrecededBy("-") && s.startsWith("-")),
            "subquery in the place of %s appears to cause a line comment: -%s",
            placeholder,
            s);
        checkArgument(
            !(placeholder.isPrecededBy("/") && s.startsWith("*")),
            "subquery in the place of %s appears to cause a block comment: /%s",
            placeholder,
            s);
        return s;
      }
      checkArgument(!(value instanceof CharSequence || value instanceof Character),
          "Symbols should be wrapped inside %s;\n" + "subqueries must be wrapped in another SafeQuery object;\n"
              + "and string literals must be quoted like '%s'",
          TRUSTED_SQL_TYPE_NAME, placeholder);
      return translateLiteral(placeholder, value).toString();
    }

    private static String quotedBy(char quoteChar, Substring.Match placeholder, Object value) {
      checkNotNull(value, "Quoted placeholder cannot be null: '%s'", placeholder);
      checkArgument(!isTrusted(value), "placeholder of type %s should not be quoted: %s%s%s",
          value.getClass().getSimpleName(), quoteChar, placeholder, quoteChar);
      return escapeQuoted(quoteChar, value.toString());
    }

    private static String backquoted(Substring.Match placeholder, Object value) {
      if (value instanceof Enum) {
        return ((Enum<?>) value).name();
      }
      String name = removeQuotes('`', value.toString(), '`'); // ok if already backquoted
      return escapeQuoted('`', checkIdentifier(placeholder, name));
    }

    private static String escapeQuoted(char quoteChar, String s) {
      StringBuilder builder = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
        int codePoint = s.codePointAt(i);
        if (codePoint == quoteChar) {
          builder.append("\\").appendCodePoint(quoteChar);
        } else if (codePoint == '\\') {
          builder.append("\\\\");
        } else if (codePoint >= 0x20 && codePoint < 0x7F || codePoint == '\t') {
          // 0x20 is space, \t is tab, keep. 0x7F is DEL control character, escape.
          // <=0x1f and >=0x7F are ISO control characters.
          builder.appendCodePoint(codePoint);
        } else if (Character.charCount(codePoint) == 1) {
          builder.append(String.format("\\u%04X", codePoint));
        } else {
          char hi = Character.highSurrogate(codePoint);
          char lo = Character.lowSurrogate(codePoint);
          builder.append(String.format("\\u%04X\\u%04X", (int) hi, (int) lo));
          i++;
        }
      }
      return builder.toString();
    }

    private static String removeQuotes(char left, String s, char right) {
      return Substring.between(prefix(left), suffix(right)).from(s).orElse(s);
    }
  }

  static void validatePlaceholder(Substring.Match placeholder) {
    checkArgument(!placeholder.isImmediatelyBetween("`", "'"), "Incorrectly quoted placeholder: `%s'", placeholder);
    checkArgument(!placeholder.isImmediatelyBetween("'", "`"), "Incorrectly quoted placeholder: '%s`", placeholder);
  }

  static String checkIdentifier(CharSequence placeholder, String name) {
    // Make sure the backquoted string doesn't contain some special chars that may cause trouble.
    checkArgument(
        ILLEGAL_IDENTIFIER_CHARS.matchesNoneOf(name),
        "placeholder value for `%s` (%s) contains illegal character", placeholder, name);
    return name;
  }
}