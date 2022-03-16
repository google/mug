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

import static com.google.mu.util.Substring.END;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.upToIncluding;
import static java.util.Objects.requireNonNull;

import java.util.stream.Stream;

/**
 * Utility class to break input strings (normally identifier strings) in camelCase, UpperCamelCase,
 * snake_case, UPPER_SNAKE_CASE, dash-case etc.
 *
 * <p>By default, non-alphanum ascii characters are treated as caseDelimiter characters and are
 * treated the same as any case delimiter. {@link Character#isLowerCase(int) Lower case}
 * characters and {@link Character#isDigit(int) digits} are considers to be lower-case when
 * breaking up camel case.
 *
 * <p>If the default settings don't work for you, they can be customized by using {@link
 * #withCaseDelimiterChars} and/or {@link #withLowerCaseChars}.
 *
 * @since 6.0
 */
public final class CaseBreaker {
  private static final CodePointMatcher NUM = CodePointMatcher.range('0', '9');
  private final CodePointMatcher caseDelimiter;
  private final CodePointMatcher camelLower;

  public CaseBreaker() {
    this.caseDelimiter = CodePointMatcher.ASCII.and(CodePointMatcher.ALPHA.or(NUM).negate());
    this.camelLower = CodePointMatcher.of(Character::isDigit).or(Character::isLowerCase);
  }

  private CaseBreaker(CodePointMatcher caseDelimiter, CodePointMatcher camelLower) {
    this.caseDelimiter = caseDelimiter;
    this.camelLower = camelLower;
  }

  /**
   * Returns a new instance using {@code caseDelimiter} to identify case delimiter characters, for
   * example if you need to respect CJK caseDelimiter characters.
   */
  public CaseBreaker withCaseDelimiterChars(CodePointMatcher caseDelimiter) {
    return new CaseBreaker(requireNonNull(caseDelimiter), camelLower);
  }

  /**
   * Returns a new instance using {@code camelLower} to identify lower case characters (don't forget
   * to include digits if they should also be treated as lower case).
   */
  public CaseBreaker withLowerCaseChars(CodePointMatcher camelLower) {
    return new CaseBreaker(caseDelimiter, requireNonNull(camelLower));
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
        first(camelLower).withBoundary(CodePointMatcher.ANY, camelLower.negate());
    return Substring.consecutive(caseDelimiter.negate())
        .repeatedly()
        .from(text)
        .flatMap(upToIncluding(lowerTail.or(END)).repeatedly()::from);
  }
}
