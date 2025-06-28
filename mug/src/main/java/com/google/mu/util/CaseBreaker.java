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
import static java.util.Objects.requireNonNull;

import java.util.stream.Stream;

/**
 * Utility to {@link #breakCase break} input strings (normally identifier strings) in {@code camelCase},
 * {@code UpperCamelCase}, {@code snake_case}, {@code UPPER_SNAKE_CASE} and {@code dash-case} etc.
 *
 * <p>Unlike Guava {@code CaseFormat}, this class doesn't require you to know the input casing. You can
 * take any string and then extract or convert into the target casing.
 *
 * <p><b>Warning:</b> This class doesn't recognize <a
 * href="https://docs.oracle.com/javase/8/docs/api/java/lang/Character.html#supplementary">supplementary
 * code points</a>.
 *
 * <p>Starting from v9.0, CaseBreaker is moved to the core mug artifact, and no longer requires
 * Guava as a dependency. The old {@code toCase(CaseFormat, Strig)} method is moved to a new
 * class {@code CaseFormats} in the mug-guava artifact.
 *
 * @since 9.0
 */
public final class CaseBreaker {
  private static final CharPredicate NUM = CharPredicate.range('0', '9');

  /** For example, the '_' and '-' in snake_case and dash-case. */
  private final CharPredicate punctuation;

  /** By default, the lower-case and numeric characters that don't conclude a word in camelCase. */
  private final CharPredicate camelLower;

  public CaseBreaker() {
    this.punctuation = ASCII.and(ALPHA.or(NUM).not());
    this.camelLower = NUM.or(Character::isLowerCase);
  }

  private CaseBreaker(CharPredicate punctuation, CharPredicate camelLower) {
    this.punctuation = punctuation;
    this.camelLower = camelLower;
  }

  /**
   * Returns a new instance using {@code punctuation} to identify punctuation characters (ones
   * that separate words but aren't themselves included in the result), for
   * example if you want to support dash-case using the en dash (–) character.
   */
  public CaseBreaker withPunctuationChars(CharPredicate punctuation) {
    return new CaseBreaker(requireNonNull(punctuation), camelLower);
  }

  /**
   * Returns a new instance using {@code camelLower} to identify lower case characters (don't forget
   * to include digits if they should also be treated as lower case).
   */
  public CaseBreaker withLowerCaseChars(CharPredicate camelLower) {
    return new CaseBreaker(punctuation, requireNonNull(camelLower));
  }

  /**
   * Returns a lazy stream of words split out from {@code text}, delimited by non-letter-digit ascii
   * characters, and further split at {@code lowerCamelCase} and {@code UpperCamelCase} boundaries.
   *
   * <p>Examples:
   *
   * <pre>{@code
   * breakCase("userId")            => ["user", "Id"]
   * breakCase("field_name")        => ["field", "name"]
   * breakCase("CONSTANT_NAME")     => ["CONSTANT", "NAME"]
   * breakCase("dash-case")         => ["dash", "case"]
   * breakCase("3 separate words")  => ["3", "separate", "words"]
   * breakCase("TheURLs")           => ["The", "URLs"]
   * breakCase("🅣ⓗⓔ🅤🅡🅛ⓢ")      => ["🅣ⓗⓔ", "🅤🅡🅛ⓢ""]
   * breakCase("UpgradeIPv4ToIPv6") => ["Upgrade", "IPv4", "To", "IPv6"]
   * }</pre>
   *
   * <p>By default, non-alphanumeric ascii characters are treated as case delimiter characters. And
   * <a href="https://docs.oracle.com/javase/8/docs/api/java/lang/Character.html#isLowerCase-char-">
   * Java lower case</a> characters and ascii digits are considered to be lower case when breaking up
   * camel case.
   *
   * <p>Besides used as case delimiters, non-letter-digit ascii characters are filtered out from the
   * returned words.
   *
   * <p>If the default setting doesn't work for you, it can be customized by using {@link
   * #withPunctuationChars} and/or {@link #withLowerCaseChars}.
   */
  public Stream<String> breakCase(CharSequence text) {
    Substring.Pattern lowerTail = // The 'l' in 'camelCase', 'CamelCase', 'camel' or 'Camel'.
        first(camelLower).separatedBy(CharPredicate.ANY, camelLower.not());
    return Substring.consecutive(punctuation.not())
        .repeatedly()
        .from(text)
        .flatMap(upToIncluding(lowerTail.or(END)).repeatedly()::from);
  }
}
