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
package com.google.mu.util;

import static com.google.mu.util.CharPredicate.ALPHA;
import static com.google.mu.util.CharPredicate.ASCII;
import static com.google.mu.util.Substring.END;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.upToIncluding;
import static java.util.stream.Collectors.joining;

import java.util.stream.Stream;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Utility class to break input strings (normally identifier strings) in camelCase, UpperCamelCase,
 * snake_case, UPPER_SNAKE_CASE, dash-case etc.
 *
 * <p>By default, non-alphanumeric ascii characters are treated as case delimiter characters. And
 * {@link Character#isLowerCase JDK lower case} characters and ascii digits are considered to be
 * lower-case when breaking up camel case.
 *
 * <p>If the default settings don't work for you, they can be customized by using {@link
 * #withCaseDelimiterChars} and/or {@link #withLowerCaseChars}.
 *
 * <p><b>Warning:</b> This class doesn't recognize <a
 * href="https://docs.oracle.com/javase/8/docs/api/java/lang/Character.html#supplementary">supplementary
 * code points</a>.
 *
 * @since 6.0
 */
@CheckReturnValue
public final class CaseBreaker {
  private static final CharPredicate NUM = CharPredicate.range('0', '9');
  private final CharPredicate caseDelimiter;
  private final CharPredicate camelLower;

  // Use javaLowerCase() to break 'αβΑβ' into ['αβ', 'Αβ'].
  // We don't support supplemental codepoints due to the use of CharMatcher.
  public CaseBreaker() {
    this.caseDelimiter = ASCII.and(ALPHA.or(NUM).not());
    this.camelLower = ((CharPredicate) Character::isLowerCase).or(NUM);
  }

  private CaseBreaker(CharPredicate caseDelimiter, CharPredicate camelLower) {
    this.caseDelimiter = caseDelimiter;
    this.camelLower = camelLower;
  }

  /**
   * Returns a new instance using {@code caseDelimiter} to identify case delimiter characters, for
   * example if you need to respect CJK caseDelimiter characters.
   */
  public CaseBreaker withCaseDelimiterChars(CharMatcher caseDelimiter) {
    return new CaseBreaker(caseDelimiter::matches, camelLower);
  }

  /**
   * Returns a new instance using {@code camelLower} to identify lower case characters (don't forget
   * to include digits if they should also be treated as lower case).
   */
  public CaseBreaker withLowerCaseChars(CharMatcher camelLower) {
    return new CaseBreaker(caseDelimiter, camelLower::matches);
  }

  /**
   * Returns a lazy stream of words split out from {@code text}, delimited by non-letter-digit ascii
   * characters, and further split at lowerCamelCase and UpperCamelCase boundaries.
   *
   * <p>Examples:
   *
   * <pre>{@code
   * breakCase("userId") => ["user", "Id"]
   * breakCase("field_name") => ["field", "name"]
   * breakCase("CONSTANT_NAME") => ["CONSTANT", "NAME"]
   * breakCase("dash-case") => ["dash", "case"]
   * breakCase("3 separate words") => ["3", "separate", "words"]
   * breakCase("TheURLs") => ["The", "URLs"]
   * breakCase("🅣ⓗⓔ🅤🅡🅛ⓢ") => ["🅣ⓗⓔ", "🅤🅡🅛ⓢ""]
   * breakCase("UpgradeIPv4ToIPv6") => ["Upgrade", "IPv4", "To", "IPv6"]
   * }</pre>
   *
   * <p>Besides used as case delimiters, non-letter-digit ascii characters are filtered out from the
   * returned words.
   */
  public Stream<String> breakCase(CharSequence text) {
    Substring.Pattern lowerTail = // The 'l' in 'camelCase', 'CamelCase', 'camel' or 'Camel'.
        first(camelLower).withBoundary(CharPredicate.ANY, camelLower.not());
    return Substring.consecutive(caseDelimiter.not())
        .repeatedly()
        .from(text)
        .flatMap(upToIncluding(lowerTail.or(END)).repeatedly()::from);
  }

  /**
   * Converts {@code input} string to using the given {@link CaseFormat}. {@code input} can be in
   * snake_case, lowerCamelCase, UpperCamelCase, CONSTANT_CASE, dash-case, snake_case or any
   * combination thereof. For example:
   *
   * <pre>{@code
   * convertAsciiTo(LOWER_CAMEL, "user_id") => "userId"
   * convertAsciiTo(LOWER_HYPHEN, "UserID") => "user-id"
   * convertAsciiTo(UPPER_UNDERSCORE, "orderId") => "ORDER_ID"
   * }</pre>
   *
   * <p>Behavior for non-ascii characters is undefined.
   */
  public String convertAsciiTo(CaseFormat format, CharSequence input) {
    String snake = breakCase(input).map(Ascii::toLowerCase).collect(joining("_"));
    return CaseFormat.LOWER_UNDERSCORE.converterTo(format).convert(snake);
  }
}
