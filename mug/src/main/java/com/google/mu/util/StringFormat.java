package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import com.google.mu.util.stream.BiStream;

/**
 * A string parser to extract placeholder values from input strings according to a format string.
 * For example:
 *
 * <pre>{@code
 * return new StringFormat("{address}+{subaddress}@{domain}")
 *     .parse("my-ldap+test@google.com", (address, subaddress, domain) -> ...);
 * }</pre>
 *
 * <p>An ErrorProne check is provided to guard against incorrect lambda parameters to the {@code
 * parse()}, {@code parseOrThrow()}, {@code parseGreedy()} and {@code scan()} methods. Both the
 * number of parameters and the lambda parameter names are checked to ensure they match the format
 * string. The arguments passed to the {@link #format} are also checked. If you use bazel, the check
 * is automatically enforced.
 *
 * <p>Starting from 6.7, if a certain placeholder is uninteresting and you'd rather not name it, you
 * can use the special {@code ...} placeholder and then you won't need to assign a lambda variable
 * to capture it:
 *
 * <pre>{@code
 * return new StringFormat("{...}+{subaddress}@{domain}")
 *     .parse("my-ldap+test@google.com", (subaddress, domain) -> ...);
 * }</pre>
 *
 * <p>Note that except the placeholders, characters in the format string are treated as literals.
 * This works better if your pattern is close to free-form text with characters like '.', '?', '(',
 * '|' and whatnot because you don't need to escape them. On the other hand, the literal characters
 * won't offer regex functionalities you get from {@code (\w+)}, {@code (foo|bar)} etc.
 *
 * <p>In the face of ambiguity, the {@code parse()} methods can be lossy. Consider the format string
 * of {@code String.format("I bought %s and %s", "apples and oranges", "chips")}, it returns {@code
 * "I bought apples and oranges and chips"}; but the following parsing code will incorrectly parse
 * "apples" as "{fruits}" and "oranges and chips" as "{snacks}":
 *
 * <pre>{@code
 * new StringFormat("I bought {fruits} and {snacks}")
 *     .parse("I bought apples and oranges and chips", (fruits, snacks) -> ...);
 * }</pre>
 *
 * As such, only use this class on trusted input strings (i.e. not user inputs). And use regex
 * instead to better deal with ambiguity.
 *
 * <p>All the {@code parse()} methods attempt to match the entire input string from beginning to
 * end. If you need to find the string format as a substring anywhere inside the input string, or
 * need to find repeated occurrences from the input string, use the {@code scan()} methods instead.
 * Tack on {@code .findFirst()} on the returned lazy stream if you only care to find a single
 * occurrence.
 *
 * <p>This class is immutable and pre-compiles the format string at constructor time so that the
 * {@code parse()} and {@code scan()} methods will be more efficient.
 *
 * @since 6.6
 */
public final class StringFormat extends AbstractStringFormat {
  private static final Substring.RepeatingPattern PLACEHOLDERS =
      Substring.consecutive(c -> c != '{' && c != '}') // Find the inner-most pairs of curly braces.
          .immediatelyBetween("{", INCLUSIVE, "}", INCLUSIVE)
          .repeatedly();

  /**
   * Returns a {@link Substring.Pattern} spanning the substring matching {@code format}. For
   * example, {@code StringFormat.span("projects/{project}/")} is equivalent to {@code
   * spanningInOrder("projects/", "/")}.
   *
   * <p>Useful if you need a Substring.Pattern for purposes such as composition, but prefer a more
   * self-documenting syntax. The placeholder names in the format string don't affect runtime
   * semantics, but using meaningful names improves readability.
   *
   * @since 7.0
   */
  public static Substring.Pattern span(String format) {
    List<String> delimiters =
        PLACEHOLDERS.split(format).map(Substring.Match::toString).collect(toList());
    return delimiters.size() > 1 && delimiters.get(delimiters.size() - 1).isEmpty()
        // If the last placeholder is at end, treat it as anchoring to the end.
        ? spanInOrder(delimiters.subList(0, delimiters.size() - 1)).toEnd()
        : spanInOrder(delimiters);
  }

  private static Substring.Pattern spanInOrder(List<String> goalPosts) {
    return goalPosts.stream()
        .skip(1)
        .map(Substring::first)
        .reduce(Substring.first(goalPosts.get(0)), Substring.Pattern::extendTo);
  }

  /**
   * Returns a factory of type {@code T} using {@code format} string as the template, whose
   * curly-braced placeholders will be filled with the template arguments and then passed to the
   * {@code creator} function to create the {@code T} instances.
   *
   * <p>A typical use case is to pre-create an exception template that can be used to create
   * exceptions filled with different parameter values. For example:
   *
   * <pre>{@code
   * private static final StringFormat.To<IOException> JOB_FAILED =
   *     StringFormat.to(
   *         IOException::new, "Job ({job_id}) failed with {error_code}, details: {details}");
   *
   *   // 150 lines later.
   *   // Compile-time enforced that parameters are correct and in the right order.
   *   throw JOB_FAILED.with(jobId, errorCode, errorDetails);
   * }</pre>
   *
   * @since 7.0
   */
  public static <T> To<T> to(
      Function<? super String, ? extends T> creator, String format) {
    requireNonNull(creator);
    StringFormat fmt = new StringFormat(format);
    return new To<T>() {
      @Override
      @SuppressWarnings("StringFormatArgsCheck")
      public T with(Object... params) {
        return creator.apply(fmt.format(params));
      }

      @Override
      public String toString() {
        return format;
      }
    };
  }

  /**
   * Returns a go/jep-430 style template of {@code T} produced by interpolating arguments into the
   * {@code template} string, using the given {@code interpolator} function.
   *
   * <p>The {@code interpolator} function is an SPI. That is, instead of users creating the lambda
   * in-line, you are expected to provide a canned implementation -- typically by wrapping it inside
   * a convenient facade class. For example:
   *
   * <pre>{@code
   * // Provided to the user:
   * public final class BigQuery {
   *   public static StringFormat.To<QueryRequest> template(String template) {
   *     return StringFormat.template(template, (fragments, placeholders) -> ...);
   *   }
   * }
   *
   * // At call site:
   * private static final StringFormat.To<QueryRequest> GET_CASE_BY_ID = BigQuery.template(
   *     "SELECT CaseId, Description FROM tbl WHERE CaseId = '{case_id}'");
   *
   *    ....
   *    QueryRequest query = GET_CASE_BY_ID.with(caseId);  // automatically escape special chars
   * }</pre>
   *
   * <p>This way, the StringFormat API provides compile-time safety, and the SPI plugs in custom
   * interpolation logic.
   *
   * <p>Calling {@link To#with} with unexpected number of parameters will throw {@link
   * IllegalArgumentException} without invoking {@code interpolator}.
   *
   * @since 7.0
   */
  public static <T> To<T> template(String template, Interpolator<? extends T> interpolator) {
    requireNonNull(interpolator);
    StringFormat formatter = new StringFormat(template);
    List<Substring.Match> placeholders =
        PLACEHOLDERS.match(template).collect(toImmutableList());
    return new To<T>() {
      @Override
      public T with(Object... params) {
        formatter.checkFormatArgs(params);
        return interpolator.interpolate(
            formatter.fragments, BiStream.zip(placeholders.stream(), Arrays.stream(params)));
      }

      @Override
      public String toString() {
        return template;
      }
    };
  }

  /**
   * Constructs a StringFormat with placeholders in the syntax of {@code "{foo}"}. For example:
   *
   * <pre>{@code
   * new StringFormat("Dear {customer}, your confirmation number is {conf#}");
   * }</pre>
   *
   * <p>Nesting "{placeholder}" syntax inside literal curly braces is supported. For example, you
   * could use a format like: {@code "{name: {name}, age: {age}}"}, and it will be able to parse
   * record-like strings such as "{name: Joe, age: 25}".
   *
   * @param format the template format with placeholders
   * @throws IllegalArgumentException if {@code format} is invalid
   *     (e.g. a placeholder immediately followed by another placeholder)
   */
  public StringFormat(String format) {
    super(format, PLACEHOLDERS, "{...}");
  }

  /**
   * A view of the {@code StringFormat} that returns an instance of {@code T}, after filling the
   * format with the given variadic parameters.
   *
   * @since 7.0
   */
  public interface To<T> {
    /** Returns an instance of {@code T} from the string format filled with {@code params}. */
    T with(Object... params);

    /** Returns the string representation of the format. */
    @Override
    public abstract String toString();
  }

  /**
   * A functional SPI interface for custom interpolation.
   *
   * @since 7.0
   */
  public interface Interpolator<T> {
    /**
     * Interpolates with {@code fragments} of size {@code N + 1} and {@code placeholders} of size
     * {@code N}. The {@code placeholders} BiStream includes pairs of placeholder names in the form
     * of "{foo}" and their corresponding values passed through the varargs parameter of {@link
     * To#with}.
     */
    T interpolate(List<String> fragments, BiStream<Substring.Match, Object> placeholders);
  }
}
