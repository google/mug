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
import static com.google.common.labs.parse.Parser.define;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.regex.RegexPattern.asAlternation;
import static com.google.common.labs.regex.RegexPattern.inSequence;
import static com.google.common.labs.regex.RegexPattern.intersection;
import static com.google.mu.util.CharPredicate.ANY;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.isNot;
import static com.google.mu.util.stream.BiStream.groupingByEach;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.stream;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.joining;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.Backreference;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final Parser<Character> ESCAPED_CHAR =
      literally(string("\\").then(one(ANY, "escaped char")));
  private static final Map<String, CharacterProperty> POSIX_CHAR_CLASSES = stream(
          PosixCharClass.values())
      .collect(groupingByEach(charClass -> charClass.names().stream(), onlyElement(identity())))
      .collect(Collectors::toUnmodifiableMap);
  static final Parser<?> FREE_SPACES = anyOf(
      consecutive(Character::isWhitespace, "whitespace"), one('#').then(consecutive("[^\n]")));

  static Parser<RegexPattern> pattern(Parser<RegexPattern> regex) {
    Parser<RegexPattern> atomic = anyOf(
        define(RegexParsers::charClass), positiveCharacterProperty(), negativeCharacterProperty(),
        groupOrLookaround(regex), anyOf(PredefinedCharClass.values()), anyOf(Anchor.values()),
        numberedBackreference(), namedBackreference(), literally(quotedLiteral()),
        consecutive("[^.[]{}()*+?^$|\\ #]").map(Literal::new),
        consecutive(is('#').or(Character::isWhitespace), "whitespace or #").map(Literal::new),
        ESCAPED_CHAR.map(c -> new Literal(Character.toString(c))),
        one('}').thenReturn(new Literal("}")), one(']').thenReturn(new Literal("]")));
    return atomic.followedByZeroOrMore(quantifier())
        .atLeastOnce(inSequence())
        .orElse(new RegexPattern.Literal(""))
        .delimitedBy("|", asAlternation())
        .notEmpty();
  }

  private static Parser<Literal> quotedLiteral() {
    var content = anyOf(
            consecutive(isNot('\\'), "non-backslash"),
            string("\\").then(one(isNot('E'), "char")).map(c -> "\\" + c))
        .zeroOrMore(joining());
    return string("\\Q").then(content).optionallyFollowedBy("\\E").map(Literal::new);
  }

  private static Parser<Backreference.Numbered> numberedBackreference() {
    return string("\\").then(sequence(one("[1-9]"), digits().optional()).source())
        .map(s -> new Backreference.Numbered(Integer.parseInt(s)));
  }

  private static Parser<Backreference.Named> namedBackreference() {
    return string("\\k<").then(word()).followedBy(">").map(Backreference.Named::new);
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Quantifier> question = one('?').thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = one('*').thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = one('+').thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> exact = number.between("{", "}").map(Quantifier::repeated);
    Parser<Quantifier> atLeast = number.followedBy(",").between("{", "}").map(Quantifier::atLeast);
    Parser<Quantifier> atMost = one(',').then(number).between("{", "}").map(Quantifier::atMost);
    Parser<Quantifier> range =
        sequence(number, one(',').then(number), Quantifier::repeated).between("{", "}");
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

  private static Parser<CharacterSet> charClass(Parser<CharacterSet> charClass) {
    Parser<Character> literalChar =
        anyOf(ESCAPED_CHAR, one("[^-&\\]]"), one('&').notFollowedBy("&"));
    Parser<Character> literalCharOrDash =
        anyOf(ESCAPED_CHAR, one("[^&\\]]"), one('&').notFollowedBy("&"));
    Parser<CharRange> range = sequence(literalChar, one('-').then(literalChar), CharRange::new);
    var element = anyOf(
        positiveCharacterProperty(), negativeCharacterProperty(),
        anyOf(PredefinedCharClass.values()), charClass, range,
        literalCharOrDash.map(LiteralChar::new));
    Parser<CharacterSet> unbracketedTerm =
        anyOf(charClass, element.atLeastOnce().map(RegexPattern::anyOf));
    Parser<CharacterSet> positiveTerm = sequence(
        element.atLeastOnce().map(RegexPattern::anyOf),
        string("&&").then(unbracketedTerm).zeroOrMore(),
        (first, rest) -> rest.isEmpty() ? first : intersection(combine(first, rest)));
    Parser<CharacterSet> negatedTerm = sequence(
        element.atLeastOnce().map(RegexPattern::noneOf),
        string("&&").then(unbracketedTerm).zeroOrMore(),
        (first, rest) -> rest.isEmpty() ? first : intersection(combine(first, rest)));
    return anyOf(
        literally(negatedTerm).immediatelyBetween("[^", "]"),
        literally(positiveTerm).immediatelyBetween("[", "]"));
  }

  private static List<CharacterSet> combine(CharacterSet first, List<CharacterSet> rest) {
    List<CharacterSet> list = new ArrayList<>(rest.size() + 1);
    list.add(first);
    list.addAll(rest);
    return list;
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    var groupContent = content.orElse(new Literal(""));
    Parser<Group.Named> named =
        sequence(word().between(anyOf("?<", "?P<"), one('>')), groupContent, Group.Named::new)
            .between("(", ")");
    Parser<Group.Atomic> atomic = groupContent.between("(?>", ")").map(Group.Atomic::new);
    Parser<ModifierFlag> modifier = anyOf(ModifierFlag.values());
    var modifierFlags = sequence(
        modifier.zeroOrMore(),
        one('-').then(modifier.atLeastOnce()).orElse(List.of()),
        (enabled, disabled) -> {
          Parser<RegexPattern> withContent = groupContent.between(":", ")");
          if (disabled.contains(ModifierFlag.COMMENTS)) {
            withContent = literally(withContent);
          } else if (enabled.contains(ModifierFlag.COMMENTS)) {
            withContent = withContent.skipping(FREE_SPACES).within();
          }
          Parser<RegexPattern> nonCapturingGroup =
              withContent.map(c -> new Group.NonCapturing(c, enabled, disabled));
          if (enabled.isEmpty() && disabled.isEmpty()) {
            return nonCapturingGroup;
          }
          Parser<RegexPattern> standaloneFlags =
              one(')').thenReturn(new Group.NonCapturing(new Literal(""), enabled, disabled));
          return anyOf(nonCapturingGroup, standaloneFlags);
        });
    return anyOf(
        named, atomic, groupContent.between("(?=", ")").map(Lookaround.Lookahead::new),
        groupContent.between("(?!", ")").map(Lookaround.NegativeLookahead::new),
        groupContent.between("(?<=", ")").map(Lookaround.Lookbehind::new),
        groupContent.between("(?<!", ")").map(Lookaround.NegativeLookbehind::new),
        literally(string("(?").then(modifierFlags)).flatMap(identity()),
        groupContent.between("(", ")").map(Group.Capturing::new));
  }
}
