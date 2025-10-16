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

import static com.google.common.labs.parse.Parser.literally;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.stream.BiStream.groupingByEach;
import static java.util.Arrays.stream;

import java.util.Map;
import java.util.function.UnaryOperator;
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
import com.google.mu.util.stream.MoreCollectors;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final CharPredicate NUM = CharPredicate.range('0', '9');
  private static final CharPredicate ALPHA =
      CharPredicate.range('a', 'z').orRange('A', 'Z').or('_');
  private static final Parser<Character> ESCAPED_CHAR =
      literally(Parser.string("\\").then(Parser.single(c -> true, "escaped char")));
  private static final Map<String, RegexPattern.CharacterProperty> POSIX_CHAR_CLASS_MAP =
      stream(RegexPattern.PosixCharClass.values())
          .collect(groupingByEach(charClass -> charClass.names().stream(), MoreCollectors.onlyElement(UnaryOperator.identity())))
          .collect(Collectors::toUnmodifiableMap);
  static final Parser<?> FREE_SPACES =
      Parser.anyOf(
          Parser.consecutive(Character::isWhitespace, "whitespace"),
          Parser.string("#")
              .then(Parser.consecutive(c -> c != '\n', "comment").optionallyFollowedBy("\n")));

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
            Parser.consecutive(CharPredicate.noneOf(".[]{}()*+?^$|\\ #"), "literal char")
                .map(Literal::new),
            Parser.consecutive(is('#').or(Character::isWhitespace), "whitespace or #").map(Literal::new),
            ESCAPED_CHAR.map(c -> new Literal(Character.toString(c))));
    Parser<RegexPattern> sequence =
        atomic.postfix(quantifier()).atLeastOnce(RegexPattern.inSequence());
    return lazy.definedAs(sequence.atLeastOnceDelimitedBy("|", RegexPattern.asAlternation()));
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = Parser.consecutive(NUM, "digit for quantifier").map(Integer::parseInt);
    Parser<Quantifier> question = Parser.string("?").thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = Parser.string("*").thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = Parser.string("+").thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> exact = number.between("{", "}").map(Quantifier::repeated);
    Parser<Quantifier> atLeast = number.followedBy(",").between("{", "}").map(Quantifier::atLeast);
    Parser<Quantifier> atMost =
        Parser.string(",").then(number).between("{", "}").map(Quantifier::atMost);
    Parser<Quantifier> range =
        Parser.sequence(number, Parser.string(",").then(number), Quantifier::repeated)
            .between("{", "}");
    return Parser.anyOf(question, star, plus, exact, atLeast, atMost, range)
        .optionallyFollowedBy("?", Quantifier::reluctant)
        .optionallyFollowedBy("+", Quantifier::possessive);
  }

  private static Parser<RegexPattern.CharacterProperty> positiveCharacterProperty() {
    return Parser.string("\\p").then(characterPropertySuffix());
  }

  private static Parser<RegexPattern.CharacterProperty.Negated> negativeCharacterProperty() {
    return Parser.string("\\P")
        .then(characterPropertySuffix())
        .map(RegexPattern.CharacterProperty::negated);
  }

  private static Parser<RegexPattern.CharacterProperty> characterPropertySuffix() {
    return Parser.consecutive(ALPHA.or(NUM), "character property name")
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
            literalChar, Parser.string("-").then(literalChar), RegexPattern.CharRange::new);
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
        Parser.consecutive(ALPHA.or(NUM), "alphanumeric for group name")
            .between(Parser.string("?<").or(Parser.string("?P<")), Parser.string(">"))
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
    return stream(values).map(e -> Parser.string(e.toString()).thenReturn(e)).collect(Parser.or());
  }
}
