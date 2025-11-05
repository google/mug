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

import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.mu.util.CharPredicate.ANY;
import static com.google.mu.util.CharPredicate.is;
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
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.mu.util.CharPredicate;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final Parser<Character> ESCAPED_CHAR =
      literally(string("\\").then(Parser.single(ANY, "escaped char")));
  private static final Map<String, RegexPattern.CharacterProperty> POSIX_CHAR_CLASS_MAP =
      stream(RegexPattern.PosixCharClass.values())
          .collect(groupingByEach(charClass -> charClass.names().stream(), onlyElement(identity())))
          .collect(Collectors::toUnmodifiableMap);
  static final Parser<?> FREE_SPACES =
      Parser.anyOf(
          consecutive(Character::isWhitespace, "whitespace"),
          string("#")
              .then(consecutive(c -> c != '\n', "comment").followedByOrEof(string("\n"))));

  static Parser<RegexPattern> pattern() {
    var lazy = new Parser.Rule<RegexPattern>();
    Parser<RegexPattern> atomic =
        Parser.anyOf(
            charClass(),
            positiveCharacterProperty(),
            negativeCharacterProperty(),
            groupOrLookaround(lazy),
            anyOf(PredefinedCharClass.values()),
            anyOf(Anchor.values()),
            consecutive(CharPredicate.noneOf(".[]{}()*+?^$|\\ #"), "literal char")
                .map(Literal::new),
            consecutive(is('#').or(Character::isWhitespace), "whitespace or #").map(Literal::new),
            ESCAPED_CHAR.map(c -> new Literal(Character.toString(c))));
    Parser<RegexPattern> sequence =
        atomic.postfix(quantifier()).atLeastOnce(RegexPattern.inSequence());
    return lazy.definedAs(sequence.atLeastOnceDelimitedBy("|", RegexPattern.asAlternation()));
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = Parser.digits().map(Integer::parseInt);
    Parser<Quantifier> question = string("?").thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = string("*").thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = string("+").thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> exact = number.between("{", "}").map(Quantifier::repeated);
    Parser<Quantifier> atLeast = number.followedBy(",").between("{", "}").map(Quantifier::atLeast);
    Parser<Quantifier> atMost =
        string(",").then(number).between("{", "}").map(Quantifier::atMost);
    Parser<Quantifier> range =
        Parser.sequence(number, string(",").then(number), Quantifier::repeated)
            .between("{", "}");
    return Parser.anyOf(question, star, plus, exact, atLeast, atMost, range)
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
        .map(
            name ->
                POSIX_CHAR_CLASS_MAP.getOrDefault(name, new RegexPattern.UnicodeProperty(name)));
  }

  private static Parser<RegexPattern.CharacterSet> charClass() {
    Parser<Character> literalChar =
        Parser.anyOf(ESCAPED_CHAR, Parser.single(CharPredicate.noneOf("-]\\"), "literal character"));
    Parser<Character> literalCharOrDash =
        Parser.anyOf(
            ESCAPED_CHAR, Parser.single(CharPredicate.noneOf("]\\"), "literal character or dash"));
    Parser<CharRange> range =
        Parser.sequence(
            literalChar, string("-").then(literalChar), RegexPattern.CharRange::new);
    var element =
        Parser.anyOf(
            positiveCharacterProperty(),
            negativeCharacterProperty(),
            anyOf(PredefinedCharClass.values()),
            range,
            literalCharOrDash.map(LiteralChar::new));
    return Parser.anyOf(
        literally(element.atLeastOnce()).immediatelyBetween("[^", "]").map(RegexPattern::noneOf),
        literally(element.atLeastOnce()).immediatelyBetween("[", "]").map(RegexPattern::anyOf));
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    Parser<Group.Named> named =
        word()
            .between(string("?<").or(string("?P<")), string(">"))
            .flatMap(n -> content.map(c -> new Group.Named(n, c)))
            .between("(", ")");
    return Parser.anyOf(
        named,
        content.between("(?=", ")").map(Lookaround.Lookahead::new),
        content.between("(?!", ")").map(Lookaround.NegativeLookahead::new),
        content.between("(?<=", ")").map(Lookaround.Lookbehind::new),
        content.between("(?<!", ")").map(Lookaround.NegativeLookbehind::new),
        content.between("(?:", ")").map(Group.NonCapturing::new),
        content.between("(", ")").map(Group.Capturing::new));
  }

  private static <E extends Enum<E>> Parser<E> anyOf(E... values) {
    return stream(values).map(e -> string(e.toString()).thenReturn(e)).collect(Parser.or());
  }
}
