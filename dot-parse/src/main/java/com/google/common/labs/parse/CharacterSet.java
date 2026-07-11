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

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.prefix;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import com.google.errorprone.annotations.concurrent.LazyInit;
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
 * @deprecated Use the {@code Parser} overloads that directly take a {@code characterClass} string
 *     parameter, such as {@link Parser#consecutive(String)}.
 */
public final class CharacterSet implements CharPredicate {
  private static final Parser<CharPredicate> CHARACTER_SET_PARSER = makeCharacterSetParser();
  private static final Parser<Set<Character>> ASCII_SET_PARSER = makeAsciiSetParser();

  static final CharacterSet DECIMAL = charsIn("[0-9]");
  static final CharacterSet HEX = charsIn("[0-9a-fA-F]");

  private final String string;
  private final CharPredicate predicate;
  @LazyInit private Set<String> asciiPrefixes;

  private CharacterSet(String string, CharPredicate predicate) {
    this.string = string;
    this.predicate = predicate;
  }

  /**
   * Returns a {@link CharacterSet} instance compiled from the given {@code characterSet} specifier.
   *
   * @param characterSet A regex-like character set string (e.g. {@code "[a-zA-Z0-9-_]"}).
   * @throws IllegalArgumentException if {@code characterSet} is malformed
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
    CharPredicate needsEscaping = c -> c == '\\' || Character.isISOControl(c);
    if (needsEscaping.matchesNoneOf(string)) {
      return string;
    }
    return string.chars().mapToObj(
        c -> switch (c) {
          case '\r' -> "\\r";
          case '\n' -> "\\n";
          case '\t' -> "\\t";
          case '\f' -> "\\f";
          case '\b' -> "\\b";
          case '\\' -> "\\\\";
          default -> Character.isISOControl(c) ? String.format("\\u%04X", c) : Character.toString(c);
        })
        .collect(joining());
  }

  Set<String> getAsciiPrefixes() {
    Set<String> result = asciiPrefixes;
    if (result == null) {
      asciiPrefixes = result = candidateCharsIfAscii()
          .map(chars -> chars.stream().map(Object::toString).collect(toUnmodifiableSet()))
          .orElse(Set.of(""));
    }
    return result;
  }

  Optional<Set<Character>> candidateCharsIfAscii() {
    if (string.startsWith("[^")) {
      return Optional.empty();
    }
    return ASCII_SET_PARSER.probe(string).findFirst();
  }

  private static CharPredicate compileCharacterSet(String characterSet) {
    checkArgument(
        characterSet.startsWith("[") && characterSet.endsWith("]"),
        "Character set must be in square brackets. Use [%s] instead.", characterSet);
    try {
      return CHARACTER_SET_PARSER.parse(characterSet).precomputeForAscii();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("in character set " + characterSet, e);
    }
  }

  private static Parser<CharPredicate> makeCharacterSetParser() {
    Parser<Character> validChar = one(ANY, "literal char").notFollowedByEof();
    Parser<CharPredicate> range = sequence(
        validChar.followedBy("-"), validChar,
        (c1, c2) -> {
          checkArgument(c1 <= c2, "invalid range [%s-%s]", c1, c2);
          return CharPredicate.range(c1, c2);
        });
    Parser<CharPredicate>.OrEmpty positiveSet =
        anyOf(range, validChar.map(CharPredicate::is))
            .zeroOrMore(reducing(CharPredicate.NONE, CharPredicate::or));
    Parser<CharPredicate> negativeSet =
        string("^").then(positiveSet).map(CharPredicate::not);
    return negativeSet.or(positiveSet).between("[", "]");
  }

  private static Parser<Set<Character>> makeAsciiSetParser() {
    Parser<Character> asciiChar = one(c -> c < 128, "ascii char").notFollowedByEof();
    Parser<Set<Character>> range =
        sequence(asciiChar.followedBy("-"), asciiChar, CharacterSet::charsInRange);
    return anyOf(range, asciiChar.map(Set::of))
        .zeroOrMore(flatMapping(Set::stream, toUnmodifiableSet()))
        .between("[", "]");
  }

  private static Set<Character> charsInRange(char c1, char c2) {
    checkArgument(c1 <= c2, "invalid range [%s-%s]", c1, c2);
    return IntStream.rangeClosed(c1, c2)
      .mapToObj(c -> (char) c)
      .collect(toUnmodifiableSet());
  }
}
