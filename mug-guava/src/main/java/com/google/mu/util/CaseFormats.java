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

import static com.google.common.base.CaseFormat.LOWER_UNDERSCORE;
import static com.google.mu.util.CharPredicate.ALPHA;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;

import java.util.stream.Collector;

import com.google.common.base.Ascii;
import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.annotations.RequiresGuava;

/**
 * Additional utilities pertaining to {@link CaseFormat}.
 *
 * <p><b>Warning:</b> This class doesn't recognize <a
 * href="https://docs.oracle.com/javase/8/docs/api/java/lang/Character.html#supplementary">supplementary
 * code points</a>.
 *
 * @since 9.0
 */
@CheckReturnValue
@RequiresGuava
public final class CaseFormats {
  private static final CharPredicate NUM = CharPredicate.range('0', '9');

  /**
   * Converts {@code input} string to using the given {@link CaseFormat}. {@code input} can be in
   * {@code snake_case}, {@code lowerCamelCase}, {@code UpperCamelCase}, {@code CONSTANT_CASE},
   * {@code dash-case} or any combination thereof. For example:
   *
   * <pre>{@code
   * toCase(LOWER_CAMEL, "user_id")                 => "userId"
   * toCase(LOWER_HYPHEN, "UserID")                 => "user-id"
   * toCase(UPPER_UNDERSCORE, "orderId")            => "ORDER_ID"
   * toCase(LOWER_UNDERSCORE, "primaryUser.userId") => "primary_user.user_id"
   * }</pre>
   *
   * <p>Given that {@link CaseFormat} only handles ascii, characters outside of the range of {@code
   * [a-zA-Z0-9_-]} (e.g. whitespaces, parenthesis, non-ascii) are passed through as is. If you need
   * to support non-ascii camel case such as Greek upper case ('Β') and lower case ('β'), consider
   * using {@link CaseBreaker#breakCase} to break up words in the source and then apply target casing
   * manually using e.g. {@link Character#toLowerCase}.
   */
  public static String toCase(CaseFormat caseFormat, String input) {
    // Ascii char matchers are faster than the default.
    CharPredicate caseDelimiter = CharMatcher.anyOf("_-")::matches;
    CaseBreaker breaker = new CaseBreaker()
        .withPunctuationChars(caseDelimiter)
        .withLowerCaseChars(NUM.or(Ascii::isLowerCase));
    Collector<String, ?, String> toSnakeCase =
        mapping(Ascii::toLowerCase, joining("_")); // first convert to snake_case
    Substring.RepeatingPattern words =
        Substring.consecutive(ALPHA.or(NUM).or(caseDelimiter)).repeatedly();
    return caseFormat.equals(LOWER_UNDERSCORE)
        ? words.replaceAllFrom(input, w -> breaker.breakCase(w).collect(toSnakeCase))
        : words.replaceAllFrom(
            input, w -> LOWER_UNDERSCORE.to(caseFormat, breaker.breakCase(w).collect(toSnakeCase)));
  }
}
