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
import static com.google.common.labs.regex.RegexPattern.asAlternation;
import static com.google.common.labs.regex.RegexPattern.inSequence;
import static com.google.mu.util.CharPredicate.ANY;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.stream.BiStream.groupingByEach;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.stream;
import static java.util.function.UnaryOperator.identity;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.CharRange;
import com.google.common.labs.regex.RegexPattern.CharacterProperty;
import com.google.common.labs.regex.RegexPattern.CharacterSet;
import com.google.common.labs.regex.RegexPattern.Group;
import com.google.common.labs.regex.RegexPattern.Literal;
import com.google.common.labs.regex.RegexPattern.LiteralChar;
import com.google.common.labs.regex.RegexPattern.Lookaround;
import com.google.common.labs.regex.RegexPattern.ModifierFlag;
import com.google.common.labs.regex.RegexPattern.PosixCharClass;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.common.labs.regex.RegexPattern.UnicodeProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final Parser<Character> ESCAPED_CHAR =
      literally(string("\\").then(one(ANY, "escaped char")));
  private static final Map<String, CharacterProperty> POSIX_CHAR_CLASSES =
      stream(PosixCharClass.values())
          .collect(groupingByEach(charClass -> charClass.names().stream(), onlyElement(identity())))
          .collect(Collectors::toUnmodifiableMap);
  static final Parser<?> FREE_SPACES = anyOf(
      consecutive(Character::isWhitespace, "whitespace"), one('#').then(consecutive("[^\n]")));

  static Parser<RegexPattern> pattern() {
    return Parser.define(me -> {
      Parser<RegexPattern> atomic = anyOf(
          charClass(), positiveCharacterProperty(), negativeCharacterProperty(),
          groupOrLookaround(me), anyOf(PredefinedCharClass.values()), anyOf(Anchor.values()),
          consecutive("[^.[]{}()*+?^$|\\ #]").map(Literal::new),
          consecutive(is('#').or(Character::isWhitespace), "whitespace or #").map(Literal::new),
          ESCAPED_CHAR.map(c -> new Literal(Character.toString(c))));
      return atomic.withPostfixes(quantifier())
          .atLeastOnce(inSequence())
          .atLeastOnceDelimitedBy("|", asAlternation());
    });
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Quantifier> question = string("?").thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = string("*").thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = string("+").thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> exact = number.between("{", "}").map(Quantifier::repeated);
    Parser<Quantifier> atLeast = number.followedBy(",").between("{", "}").map(Quantifier::atLeast);
    Parser<Quantifier> atMost = string(",").then(number).between("{", "}").map(Quantifier::atMost);
    Parser<Quantifier> range =
        sequence(number, string(",").then(number), Quantifier::repeated).between("{", "}");
    return anyOf(question, star, plus, exact, atLeast, atMost, range)
        .optionallyFollowedBy("?", Quantifier::reluctant)
        .optionallyFollowedBy("+", Quantifier::possessive);
  }

  private static Parser<CharacterProperty> positiveCharacterProperty() {
    return string("\\p").then(characterPropertySuffix());
  }

  private static Parser<CharacterProperty.Negated> negativeCharacterProperty() {
    return string("\\P").then(characterPropertySuffix()).map(CharacterProperty::negated);
  }

  private static Parser<CharacterProperty> characterPropertySuffix() {
    return word().between("{", "}")
        .map(name -> POSIX_CHAR_CLASSES.getOrDefault(name, new UnicodeProperty(name)));
  }

  private static Parser<CharacterSet> charClass() {
    Parser<Character> literalChar = anyOf(ESCAPED_CHAR, one("[^-]\\]"));
    Parser<Character> literalCharOrDash = anyOf(ESCAPED_CHAR, one("[^\\]]"));
    Parser<CharRange> range = sequence(literalChar, string("-").then(literalChar), CharRange::new);
    var element = anyOf(
        positiveCharacterProperty(), negativeCharacterProperty(),
        anyOf(PredefinedCharClass.values()), range, literalCharOrDash.map(LiteralChar::new));
    return anyOf(
        literally(element.atLeastOnce()).immediatelyBetween("[^", "]").map(RegexPattern::noneOf),
        literally(element.atLeastOnce()).immediatelyBetween("[", "]").map(RegexPattern::anyOf));
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    Parser<Group.Named> named =
        sequence(word().between(anyOf("?<", "?P<"), string(">")), content, Group.Named::new)
            .between("(", ")");
    Parser<ModifierFlag> modifier = anyOf(ModifierFlag.values());
    var modifierFlags = sequence(
        modifier.zeroOrMore(),
        string("-").then(modifier.atLeastOnce()).orElse(List.of()),
        (enabled, disabled) -> {
          Parser<RegexPattern> result = content.between(":", ")");
          if (disabled.contains(ModifierFlag.COMMENTS)) {
            result = literally(result);
          } else if (enabled.contains(ModifierFlag.COMMENTS)) {
            result = result.skipping(FREE_SPACES).within();
          }
          return result.map(c -> new Group.NonCapturing(c, enabled, disabled));
        });
    Parser<RegexPattern> modifierGroup =
        literally(string("(?").then(modifierFlags)).flatMap(identity());
    return anyOf(
        named,
        content.between("(?=", ")").map(Lookaround.Lookahead::new),
        content.between("(?!", ")").map(Lookaround.NegativeLookahead::new),
        content.between("(?<=", ")").map(Lookaround.Lookbehind::new),
        content.between("(?<!", ")").map(Lookaround.NegativeLookbehind::new),
        modifierGroup,
        content.between("(", ")").map(Group.Capturing::new));
  }
}
