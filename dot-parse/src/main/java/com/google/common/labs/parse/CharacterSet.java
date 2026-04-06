/*****************************************************************************
 * Copyright (C) google.com                                                  *
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
package com.google.common.labs.parse;

import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.CharPredicate.isNot;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.prefix;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import com.google.mu.util.CharPredicate;

/**
 * Represents a set of characters specified by a regex-like character set string.
 *
 * <p>For example {@code charsIn("[a-zA-Z-_]")} is a shorthand of {@code
 * CharPredicate.range('a', 'z').orRange('A', 'Z').or('-').or('_')}.
 *
 * <p>You can also use {@code '^'} to get negative character set like:
 * {@code charsIn("[^a-zA-Z]")}, which is any non-alphabet character.
 *
 * <p>Note that it's different from {@code CharPredicate.anyOf(string)},
 * which treats the string as a list of literal characters, not a regex-like
 * character set.
 *
 * <p>It's strongly recommended to install the mug-errorprone plugin (v9.4+) in your
 * compiler's and IDE's annotationProcessorPaths so that you can get instant feedback
 * against incorrect character set syntax.
 *
 * <p>Implementation Note: regex isn't used during parsing. The character set string is translated
 * to a {@link CharPredicate#precomputeForAscii precomputed} {@code CharPredicate}, at construction time.
 *
 * @since 9.4
 */
public final class CharacterSet implements CharPredicate {
  private final String string;
  private final CharPredicate predicate;

  private CharacterSet(String string, CharPredicate predicate) {
    this.string = string;
    this.predicate = predicate;
  }

  /**
   * Returns a {@link CharacterSet} instance compiled from the given {@code characterSet} specifier.
   *
   * @param characterSet A regex-like character set string (e.g. {@code "[a-zA-Z0-9-_]"}),
   *        but disallows backslash so doesn't support escaping.
   *        If your character set includes special characters like literal backslash
   *        or right bracket, use {@link CharPredicate} directly.
   * @throws IllegalArgumentException if {@code characterSet} includes backslash
   *         or the right bracket (except the outmost pairs of {@code []}).
   */
  public static CharacterSet charsIn(String characterSet) {
    return new CharacterSet(characterSet, compileCharacterSet(characterSet));
  }

  /** Returns true if this set contains the character {@code ch}. */
  @Override public boolean test(char ch) {
    return predicate.test(ch);
  }

  /** Returns true if this set contains the character {@code ch}. */
  public boolean contains(char ch) {
    return predicate.test(ch);
  }

  /**
   * No-op because a {@code CharacterSet} is already pre-computed for ASCII.
   *
   * @since 9.9.4
   */
  @Override public CharacterSet precomputeForAscii() {
    return new CharacterSet(string, predicate.precomputeForAscii());
  }

  @Override public CharacterSet not() {
    return new CharacterSet(
        after(prefix("["))
            .in(string)
            .map(m -> m.startsWith("^") ? "[" + m.skip(1, 0) : "[^" + m)
            .orElse(string),
        predicate.not());
  }

  @Override public boolean equals(Object obj) {
    return (obj instanceof CharacterSet that) && string.equals(that.string);
  }

  @Override public int hashCode() {
    return string.hashCode();
  }

  /** Returns the character set string representation. For example {@code "[a-zA-Z0-9-_]"}. */
  @Override public String toString() {
    return string;
  }

  private static CharPredicate compileCharacterSet(String characterSet) {
    checkArgument(characterSet.startsWith("[") && characterSet.endsWith("]"),
        "Character set must be in square brackets. Use [%s] instead.", characterSet);
    checkArgument(
        !characterSet.contains("\\"),
        "Escaping (%s) not supported in a character set. Please use CharePredicate instead.",
        characterSet);
    Parser<Character> validChar = Parser.one(isNot(']'), "character");
    Parser<CharPredicate> range =
        Parser.sequence(validChar.followedBy("-"), validChar,
            (c1, c2) -> {
              checkArgument(
                  c1 <= c2, "Invalid range [%s-%s] in character set %s", c1, c2, characterSet);
              return CharPredicate.range(c1, c2);
            });
    Parser<CharPredicate>.OrEmpty positiveSet =
        Parser.anyOf(range, validChar.map(CharPredicate::is))
            .zeroOrMore(reducing(CharPredicate.NONE, CharPredicate::or));
    Parser<CharPredicate> negativeSet =
        Parser.string("^").then(positiveSet).map(CharPredicate::not);
    return negativeSet.or(positiveSet).between("[", "]").parse(characterSet).precomputeForAscii();
  }

  Optional<Set<Character>> candidateCharsIfAscii() {
    if (string.startsWith("[^")) {
      return Optional.empty();
    }
    Parser<Character> asciiChar = Parser.one(c -> c != ']' && c < 128, "ascii char");
    Parser<Set<Character>> range =
        Parser.sequence(asciiChar.followedBy("-"), asciiChar, CharacterSet::charsInRange);
    return Parser.anyOf(range, asciiChar.map(Set::of))
        .zeroOrMore(flatMapping(Set::stream, toUnmodifiableSet()))
        .between("[", "]")
        .probe(string)
        .findFirst();
  }

  private static Set<Character> charsInRange(char c1, char c2) {
    return IntStream.rangeClosed(c1, c2)
      .mapToObj(c -> (char) c)
      .collect(toUnmodifiableSet());
  }
}
