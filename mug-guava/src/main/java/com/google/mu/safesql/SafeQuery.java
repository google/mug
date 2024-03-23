package com.google.mu.safesql;

import static com.google.common.base.CharMatcher.anyOf;
import static com.google.common.base.CharMatcher.javaIsoControl;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;

import java.util.Iterator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.annotations.RequiresGuava;
import com.google.mu.annotations.TemplateFormatMethod;
import com.google.mu.annotations.TemplateString;
import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;

/**
 * Facade class to generate queries based on a string template in the syntax of {@link
 * StringFormat}, with compile-time guard rails and runtime protection against SQL injection and
 * programmer mistakes. Special characters of string expressions are automatically escaped to
 * prevent SQL injection errors.
 *
 * <p>A SafeQuery encapsulates the query string. You can use {@link #toString} to access the query
 * string.
 *
 * <p>In addition, a SafeQuery may represent a subquery, and can be passed through {@link
 * StringFormat.To#with} to compose larger queries.
 *
 * <p>This class is Android compatible.
 *
 * @since 7.0
 */
@RequiresGuava
@Immutable
public final class SafeQuery {
  private static final CharMatcher ILLEGAL_IDENTIFIER_CHARS =
      anyOf("'\"`()[]{}\\~!@$^*,/?;").or(javaIsoControl());
  private static final String TRUSTED_SQL_TYPE_NAME =
      firstNonNull(
          System.getProperty("com.google.mu.safesql.SafeQuery.trusted_sql_type"),
          "com.google.storage.googlesql.safesql.TrustedSqlString");

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
   * Creates a template with the placeholders to be filled by subsequent {@link
   * StringFormat.To#with} calls. For example:
   *
   * <pre>{@code
   * private static final StringFormat.To<SafeQuery> FIND_CASE_BY_ID =
   *     SafeQuery.template(
   *         "SELECT * FROM `{project_id}.{dataset}.Cases` WHERE CaseId = '{case_id}'");
   *
   *   // ...
   *   SafeQuery query = FIND_CASE_BY_ID.with(projectId, dataset, caseId);
   * }</pre>
   *
   * <p>Placeholders must follow the following rules:
   *
   * <ul>
   *   <li>String placeholder values can only be used when the corresponding placeholder is quoted
   *       in the template string (such as '{case_id}').
   *       <ul>
   *         <li>If quoted by single quotes (') or double quotes ("), the placeholder value will be
   *             auto-escaped.
   *         <li>If quoted by backticks (`), the placeholder value cannot contain backtick, quotes
   *             and other special characters.
   *       </ul>
   *   <li>Unquoted placeholders are assumed to be symbols or subqueries. Strings (CharSequence) and
   *       characters cannot be used as placeholder value. If you have to pass a string, wrap it
   *       inside a {@code TrustedSqlString} (you can configure the type you trust by setting the
   *       "com.google.mu.safesql.SafeQuery.trusted_sql_type" system property).
   *   <li>An Iterable's elements are auto expanded and joined by a comma and space (", ").
   *   <li>If the iterable's placeholder is quoted as in {@code WHERE id IN ('{ids}')}, every
   *       expanded element is quoted (and auto-escaped). For example, passing {@code asList("foo",
   *       "bar")} as the value for placeholder {@code '{ids}'} will result in {@code WHERE id IN
   *       ('foo', 'bar')}.
   *   <li>If the Iterable's placeholder is backtick-quoted, every expanded element is
   *       backtick-quoted. For example, passing {@code asList("col1", "col2")} as the value for
   *       placeholder {@code `{cols}`} will result in {@code `col`, `col2`}.
   *   <li>Subqueries can be composed by using {@link SafeQuery} as the placeholder value.
   * </ul>
   *
   * <p>The placeholder values passed through {@link StringFormat.To#with} are checked by ErrorProne
   * to ensure the correct number of arguments are passed, and in the expected order.
   *
   * <p>Supported expression types:
   *
   * <ul>
   *   <li>null (NULL)
   *   <li>Boolean
   *   <li>Numeric
   *   <li>Enum
   *   <li>String (must be quoted in the template)
   *   <li>{@code TrustedSqlString}
   *   <li>{@link SafeQuery}
   *   <li>Iterable
   * </ul>
   *
   * <p>If you are trying to create SafeQuery inline like {@code SafeQuery.template(tmpl).with(a, b)},
   * please use {@code SafeQuery.of(tmpl, a, b)} instead.
   */
  public static StringFormat.To<SafeQuery> template(@CompileTimeConstant String formatString) {
    return new Translator().translate(formatString);
  }

  /**
   * A collector that joins boolean query snippets using {@code AND} operator. The AND'ed sub-queries
   * will be enclosed in pairs of parenthesis to avoid ambiguity. If the input is empty, the result
   * will be "TRUE".
   *
   * @since 7.2
   */
  public static Collector<SafeQuery, ?, SafeQuery> and() {
    return collectingAndThen(
        mapping(SafeQuery::parenthesized, joining(" AND ")),
        query -> query.toString().isEmpty() ? of("TRUE") : query);
  }

  /**
   * A collector that joins boolean query snippets using {@code OR} operator. The OR'ed sub-queries
   * will be enclosed in pairs of parenthesis to avoid ambiguity. If the input is empty, the result
   * will be "FALSE".
   *
   * @since 7.2
   */
  public static Collector<SafeQuery, ?, SafeQuery> or() {
    return collectingAndThen(
        mapping(SafeQuery::parenthesized, joining(" OR ")),
        query -> query.toString().isEmpty() ? of("FALSE") : query);
  }

  /**
   * Returns a collector that can join {@link SafeQuery} objects using {@code delim} as the
   * delimiter.
   */
  public static Collector<SafeQuery, ?, SafeQuery> joining(@CompileTimeConstant String delim) {
    return collectingAndThen(
        mapping(SafeQuery::toString, Collectors.joining(checkNotNull(delim))), SafeQuery::new);
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
   * An SPI class for subclasses to provide additional translation from placeholder values to safe
   * query strings.
   *
   * @since 7.2
   */
  public static class Translator {
    protected Translator() {}

    /**
     * Translates {@code template} to a factory of {@link SafeQuery} by filling the provided
     * parameters in the place of corresponding placeholders.
     */
    public final StringFormat.To<SafeQuery> translate(@CompileTimeConstant String formatString) {
      return StringFormat.template(
          formatString,
          (fragments, placeholders) -> {
            Iterator<String> it = fragments.iterator();
            return new SafeQuery(
                placeholders
                    .collect(
                        new StringBuilder(),
                        (b, p, v) ->
                            b.append(it.next()).append(fillInPlaceholder(p, v)))
                    .append(it.next())
                    .toString());
          });
    }

    private String fillInPlaceholder(Substring.Match placeholder, Object value) {
      validatePlaceholder(placeholder);
      if (value instanceof Iterable) {
        Iterable<?> iterable = (Iterable<?>) value;
        if (placeholder.isImmediatelyBetween("`", "`")) { // If backquoted, it's a list of symbols
          return String.join("`, `", Iterables.transform(iterable, v -> backquoted(placeholder, v)));
        }
        if (placeholder.isImmediatelyBetween("'", "'")) {
          return String.join(
              "', '", Iterables.transform(iterable, v -> quotedBy('\'', placeholder, v)));
        }
        if (placeholder.isImmediatelyBetween("\"", "\"")) {
          return String.join(
              "\", \"", Iterables.transform(iterable, v -> quotedBy('"', placeholder, v)));
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

    /** Called if {@code value} is a non-string literal appearing unquoted in the template. */
    protected String translateLiteral(Object value) {
      if (value == null) {
        return "NULL";
      }
      if (value instanceof Boolean) {
        return value.equals(Boolean.TRUE) ? "TRUE" : "FALSE";
      }
      if (value instanceof Number) {
        return value.toString();
      }
      if (value instanceof Enum) {
        return ((Enum<?>) value).name();
      }
      throw new IllegalArgumentException(
          "Unsupported argument type: " + value.getClass().getName());
    }

    private String unquoted(Substring.Match placeholder, Object value) {
      if (value != null && isTrusted(value)) {
        return value.toString();
      }
      checkArgument(
          !(value instanceof CharSequence || value instanceof Character),
          "Symbols should be wrapped inside %s;\n"
              + "subqueries must be wrapped in another SafeQuery object;\n"
              + "and string literals must be quoted like '%s'",
          TRUSTED_SQL_TYPE_NAME,
          placeholder);
      return translateLiteral(value);
    }

    private static String quotedBy(char quoteChar, Substring.Match placeholder, Object value) {
      checkNotNull(value, "Quoted placeholder cannot be null: '%s'", placeholder);
      checkArgument(
          !isTrusted(value),
          "placeholder of type %s should not be quoted: %s%s%s",
          value.getClass().getSimpleName(),
          quoteChar,
          placeholder,
          quoteChar);
      return escapeQuoted(quoteChar, value.toString());
    }

    private static String backquoted(Substring.Match placeholder, Object value) {
      if (value instanceof Enum) {
        return ((Enum<?>) value).name();
      }
      String name = removeQuotes('`', value.toString(), '`'); // ok if already backquoted
      // Make sure the backquoted string doesn't contain some special chars that may cause trouble.
      checkArgument(
          ILLEGAL_IDENTIFIER_CHARS.matchesNoneOf(name),
          "placeholder value for `%s` (%s) contains illegal character",
          placeholder,
          name);
      return escapeQuoted('`', name);
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

    private static boolean isTrusted(Object value) {
      return value instanceof SafeQuery || value.getClass().getName().equals(TRUSTED_SQL_TYPE_NAME);
    }

    private static void validatePlaceholder(Substring.Match placeholder) {
      checkArgument(
          !placeholder.isImmediatelyBetween("`", "'"),
          "Incorrectly quoted placeholder: `%s'",
          placeholder);
      checkArgument(
          !placeholder.isImmediatelyBetween("'", "`"),
          "Incorrectly quoted placeholder: '%s`",
          placeholder);
    }

    private static String removeQuotes(char left, String s, char right) {
      return Substring.between(prefix(left), suffix(right)).from(s).orElse(s);
    }
  }
}
