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
import static com.google.common.labs.parse.Parser.hexDigits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.DIGIT;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.HORIZONTAL_WHITESPACE;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.LINEBREAK;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.NON_DIGIT;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.NON_HORIZONTAL_WHITESPACE;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.NON_VERTICAL_WHITESPACE;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.NON_WHITESPACE;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.NON_WORD;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.VERTICAL_WHITESPACE;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.WHITESPACE;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.WORD;
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
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.parse.Parsers;
import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.Backreference;
import com.google.common.labs.regex.RegexPattern.CharRange;
import com.google.common.labs.regex.RegexPattern.CharSetElement;
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
  private static final Parser<Character> UNICODE_ESCAPE = string("\\u").then(Parsers.BMP_CODE_UNIT);
  private static final Parser<Integer> CODE_POINT =
      anyOf(consecutive("[0-9a-fA-F]").between("{", "}"), hexDigits(2))
          .map(hex -> Integer.parseInt(hex, 16))
          .suchThat(codePoint -> codePoint <= Character.MAX_CODE_POINT, "code point");
  private static final Parser<Character> CONTROL_ESCAPE = anyOf(
      string("\\n").thenReturn('\n'),
      string("\\r").thenReturn('\r'),
      string("\\t").thenReturn('\t'),
      string("\\f").thenReturn('\f'),
      string("\\a").thenReturn('\u0007'),
      string("\\e").thenReturn('\u001B'));
  private static final Parser<Character> OCTAL_ESCAPE = string("\\0").then(
          anyOf(
                  sequence(one("[0-3]"), one("[0-7]").optional(), one("[0-7]").optional()),
                  sequence(one("[4-7]"), one("[0-7]").optional()))
              .source()
              .map(digits -> (char) Integer.parseInt(digits, 8)));
  private static final Parser<String> ESCAPED = literally(
      anyOf(
          CONTROL_ESCAPE.map(String::valueOf),
          UNICODE_ESCAPE.map(String::valueOf),
          OCTAL_ESCAPE.map(String::valueOf),
          string("\\c").then(one(ANY, "control char")).map(c -> Character.toString(c ^ 64)),
          string("\\x").then(CODE_POINT).map(Character::toString),
          string("\\").then(one(ANY, "escaped char")).map(String::valueOf)));
  private static final Map<String, CharacterProperty> POSIX_CHAR_CLASSES = stream(
          PosixCharClass.values())
      .collect(groupingByEach(charClass -> charClass.names().stream(), onlyElement(identity())))
      .collect(Collectors::toUnmodifiableMap);
  static final Parser<?> FREE_SPACES = anyOf(
      consecutive(Character::isWhitespace, "whitespace"), one('#').then(consecutive("[^\n]")));
  static final Parser<RegexPattern> PARSER = define(RegexParsers::pattern);

  private static Parser<RegexPattern> pattern(Parser<RegexPattern> regex) {
    Parser<RegexPattern> atomic = anyOf(
        define(RegexParsers::charClass), positiveCharacterProperty(), negativeCharacterProperty(),
        groupOrLookaround(regex), anyOf(PredefinedCharClass.values()), anyOf(Anchor.values()),
        numberedBackreference(), namedBackreference(), literally(quotedLiteral()),
        consecutive("[^.[]{}()*+?^$|\\ #]").map(Literal::new),
        consecutive(is('#').or(Character::isWhitespace), "whitespace or #").map(Literal::new),
        anyOf(ESCAPED, one("[{}]]").map(String::valueOf)).map(Literal::new));
    return atomic.followedByZeroOrMore(quantifier())
        .atLeastOnce(inSequence())
        .orElse(new RegexPattern.Literal(""))
        .delimitedBy("|", asAlternation())
        .notEmpty()
        .as("subpattern");
  }

  private static Parser<String> quotedText() {
    var content = anyOf(
            consecutive(isNot('\\'), "non-backslash"),
            string("\\").then(one(isNot('E'), "char")).map(c -> "\\" + c))
        .zeroOrMore(joining());
    return string("\\Q").then(content).optionallyFollowedBy("\\E");
  }

  private static Parser<Literal> quotedLiteral() {
    return quotedText().map(Literal::new);
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
    Parser<Character> literalChar = anyOf(
        ESCAPED.suchThat(s -> s.length() == 1, "BMP char").map(s -> s.charAt(0)),
        one("[^-&\\]]"),
        one('&').notFollowedBy("&"));
    Parser<LiteralChar> literalCharOrDash = anyOf(
        ESCAPED.map(s -> new LiteralChar(s.codePointAt(0))),
        one("[^&\\]]").map(LiteralChar::new),
        one('&').notFollowedBy("&").map(LiteralChar::new));
    Parser<CharRange> range = sequence(literalChar, one('-').then(literalChar), CharRange::new);
    Parser<CharSetElement> element = anyOf(
        positiveCharacterProperty(),
        negativeCharacterProperty(),
        anyOf(
            DIGIT,
            NON_DIGIT,
            WHITESPACE,
            NON_WHITESPACE,
            WORD,
            NON_WORD,
            HORIZONTAL_WHITESPACE,
            NON_HORIZONTAL_WHITESPACE,
            VERTICAL_WHITESPACE,
            NON_VERTICAL_WHITESPACE,
            LINEBREAK),
        charClass,
        range,
        literalCharOrDash);
    Parser<List<CharSetElement>> quotedInClass = quotedText().map(
            s -> s.codePoints().mapToObj(LiteralChar::new).collect(toUnmodifiableList()));
    Parser<CharSetElement> leadingBracket = anyOf(
        sequence(one(']'), one('-').then(literalChar), CharRange::new),
        one(']').map(LiteralChar::new));
    Parser<List<CharSetElement>> elements =
        sequence(
                leadingBracket.optional(),
                anyOf(quotedInClass, element.map(List::of))
                    .zeroOrMore(flatMapping(List::stream, toList())),
                (leading, rest) -> leading.map(head -> prepend(head, rest)).orElse(rest))
            .notEmpty();
    Parser<CharacterSet> characterSet =
        anyOf(charClass, elements.map(CharacterSet.AnyOf::new)).as("character set");
    Parser<CharacterSet> positiveTerm = sequence(
        elements.map(CharacterSet.AnyOf::new),
        string("&&").then(characterSet).zeroOrMore(),
        (first, rest) -> rest.isEmpty() ? first : intersection(prepend(first, rest)));
    Parser<CharacterSet> negatedTerm = sequence(
        elements.map(CharacterSet.NoneOf::new),
        string("&&").then(characterSet).zeroOrMore(),
        (first, rest) -> rest.isEmpty() ? first : intersection(prepend(first, rest)));
    return anyOf(
        literally(negatedTerm).immediatelyBetween("[^", "]"),
        literally(positiveTerm).immediatelyBetween("[", "]"));
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    var groupContent = content.orElse(new Literal(""));
    Parser<Group.Named> named =
        sequence(word().between(anyOf("?<", "?P<"), one('>')), groupContent, Group.Named::new)
            .between("(", ")");
    Parser<Group.Atomic> atomic = groupContent.between("(?>", ")").map(Group.Atomic::new);
    Parser<ModifierFlag> modifier = anyOf(ModifierFlag.values()).as("modifier flag");
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

  private static <T> List<T> prepend(T first, List<T> rest) {
    List<T> list = new ArrayList<>(rest.size() + 1);
    list.add(first);
    list.addAll(rest);
    return list;
  }
}
