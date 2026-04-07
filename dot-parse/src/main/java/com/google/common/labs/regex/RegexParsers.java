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
package com.google.common.labs.regex;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.mu.util.CharPredicate.ANY;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.stream.BiStream.groupingByEach;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.stream;
import static java.util.function.UnaryOperator.identity;

import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.CharRange;
import com.google.common.labs.regex.RegexPattern.Group;
import com.google.common.labs.regex.RegexPattern.Literal;
import com.google.common.labs.regex.RegexPattern.LiteralChar;
import com.google.common.labs.regex.RegexPattern.Lookaround;
import com.google.common.labs.regex.RegexPattern.PosixCharClass;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.mu.util.CharPredicate;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final Parser<Character> ESCAPED_CHAR =
      literally(string("\\").then(one(ANY, "escaped char")));
  private static final Map<String, RegexPattern.CharacterProperty> POSIX_CHAR_CLASS_MAP =
      stream(PosixCharClass.values())
          .collect(groupingByEach(charClass -> charClass.names().stream(), onlyElement(identity())))
          .collect(Collectors::toUnmodifiableMap);
  static final Parser<?> FREE_SPACES = anyOf(
      consecutive(Character::isWhitespace, "whitespace"),
      string("#").then(consecutive(c -> c != '\n', "comment").followedByOrEof(string("\n"))));

  static Parser<RegexPattern> pattern() {
    return Parser.define(me -> {
      Parser<RegexPattern> atomic = anyOf(
          charClass(),
          positiveCharacterProperty(),
          negativeCharacterProperty(),
          groupOrLookaround(me),
          anyOf(PredefinedCharClass.values()),
          anyOf(Anchor.values()),
          consecutive(noneOf(".[]{}()*+?^$|\\ #"), "literal char").map(Literal::new),
          consecutive(is('#').or(Character::isWhitespace), "whitespace or #").map(Literal::new),
          ESCAPED_CHAR.map(c -> new Literal(Character.toString(c))));
      Parser<RegexPattern> sequence =
          atomic.withPostfixes(quantifier()).atLeastOnce(RegexPattern.inSequence());
      return sequence.atLeastOnceDelimitedBy("|", RegexPattern.asAlternation());
    });
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Quantifier> question = string("?").thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = string("*").thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = string("+").thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> exact = number.between("{", "}").map(Quantifier::repeated);
    Parser<Quantifier> atLeast = number.followedBy(",").between("{", "}").map(Quantifier::atLeast);
    Parser<Quantifier> atMost =
        string(",").then(number).between("{", "}").map(Quantifier::atMost);
    Parser<Quantifier> range =
        sequence(number, string(",").then(number), Quantifier::repeated).between("{", "}");
    return anyOf(question, star, plus, exact, atLeast, atMost, range)
        .optionallyFollowedBy("?", Quantifier::reluctant)
        .optionallyFollowedBy("+", Quantifier::possessive);
  }

  private static Parser<RegexPattern.CharacterProperty> positiveCharacterProperty() {
    return string("\\p").then(characterPropertySuffix());
  }

  private static Parser<RegexPattern.CharacterProperty.Negated> negativeCharacterProperty() {
    return string("\\P")
        .then(characterPropertySuffix())
        .map(RegexPattern.CharacterProperty::negated);
  }

  private static Parser<RegexPattern.CharacterProperty> characterPropertySuffix() {
    return word()
        .between("{", "}")
        .map(name -> POSIX_CHAR_CLASS_MAP.getOrDefault(name, new RegexPattern.UnicodeProperty(name)));
  }

  private static Parser<RegexPattern.CharacterSet> charClass() {
    Parser<Character> literalChar = anyOf(ESCAPED_CHAR, one(noneOf("-]\\"), "literal character"));
    Parser<Character> literalCharOrDash =
        anyOf(ESCAPED_CHAR, one(CharPredicate.noneOf("]\\"), "literal character or dash"));
    Parser<CharRange> range =
        sequence(literalChar, string("-").then(literalChar), RegexPattern.CharRange::new);
    var element = anyOf(
        positiveCharacterProperty(),
        negativeCharacterProperty(),
        anyOf(PredefinedCharClass.values()),
        range,
        literalCharOrDash.map(LiteralChar::new));
    return anyOf(
        literally(element.atLeastOnce()).immediatelyBetween("[^", "]").map(RegexPattern::noneOf),
        literally(element.atLeastOnce()).immediatelyBetween("[", "]").map(RegexPattern::anyOf));
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    Parser<Group.Named> named = word()
        .between(string("?<").or(string("?P<")), string(">"))
        .flatMap(n -> content.map(c -> new Group.Named(n, c)))
        .between("(", ")");
    return anyOf(
        named,
        content.between("(?=", ")").map(Lookaround.Lookahead::new),
        content.between("(?!", ")").map(Lookaround.NegativeLookahead::new),
        content.between("(?<=", ")").map(Lookaround.Lookbehind::new),
        content.between("(?<!", ")").map(Lookaround.NegativeLookbehind::new),
        content.between("(?:", ")").map(Group.NonCapturing::new),
        content.between("(", ")").map(Group.Capturing::new));
  }
}
