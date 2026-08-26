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
import static com.google.common.labs.parse.Parser.quotedBy;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.parse.Parsers.BMP_CODE_UNIT;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.ANY_CHAR;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER;
import static com.google.common.labs.regex.RegexPattern.PredefinedCharClass.LINEBREAK;
import static com.google.common.labs.regex.RegexPattern.asAlternation;
import static com.google.common.labs.regex.RegexPattern.inSequence;
import static com.google.common.labs.regex.RegexPattern.intersection;
import static com.google.mu.util.CharPredicate.ANY;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;
import static com.google.mu.util.stream.BiStream.groupingByEach;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparingInt;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.common.labs.parse.Parser;
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
import java.util.Set;
import java.util.stream.Collectors;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final Parser<Integer> CODE_POINT =
      anyOf(consecutive("[0-9a-fA-F]").between("{", "}"), hexDigits(2))
          .map(hex -> Integer.parseInt(hex, 16))
          .suchThat(Character::isValidCodePoint, "code point");
  private static final Parser<Integer> OCTAL = anyOf(
          literally(one("[0-3]"), one("[0-7]").optional(), one("[0-7]").optional()),
          literally(one("[4-7]"), one("[0-7]").optional()))
      .source()
      .map(digits -> Integer.parseInt(digits, 8));
  private static final Parser<String> ESCAPED = anyOf(
      string("\\n").thenReturn("\n"),
      string("\\r").thenReturn("\r"),
      string("\\t").thenReturn("\t"),
      string("\\f").thenReturn("\f"),
      string("\\a").thenReturn("\u0007"),
      string("\\e").thenReturn("\u001B"),
      string("\\u").then(BMP_CODE_UNIT).map(String::valueOf),
      string("\\0").then(OCTAL).map(Character::toString),
      string("\\c")
          .then(one(ANY, "control char"))
          .map(c -> Character.toString(Character.toUpperCase(c) ^ 64)),
      string("\\x").then(CODE_POINT).map(Character::toString),
      string("\\N")
          .then(consecutive("[^}\r\n]").as("character name").between("{", "}"))
          .map(Character::codePointOf)
          .map(Character::toString),
      literally(string("\\").then(one(noneOf("0123456789xNuckpP"), "escaped char")))
          .map(String::valueOf));
  private static final Set<PredefinedCharClass> DISALLOWED_IN_CHAR_CLASS =
      Set.of(ANY_CHAR, EXTENDED_GRAPHEME_CLUSTER, LINEBREAK);
  private static final Map<String, CharacterProperty> POSIX_CHAR_CLASSES =
      stream(PosixCharClass.values())
          .collect(groupingByEach(charClass -> charClass.names().stream(), onlyElement(identity())))
          .collect(Collectors::toUnmodifiableMap);
  private static final Parser<Anchor> ANCHOR = stream(Anchor.values())
      .sorted(comparingInt((Anchor a) -> a.tokens().size()).reversed().thenComparing(Anchor::name))
      .map(RegexParsers::anchor)
      .collect(Parser.or());
  static final Parser<?> FREE_SPACES = anyOf(
      consecutive(Character::isWhitespace, "whitespace"), one('#').then(consecutive("[^\n]")));
  static final Parser<RegexPattern> PARSER = define(RegexParsers::pattern);

  private static Parser<RegexPattern> pattern(Parser<RegexPattern> regex) {
    Parser<RegexPattern> atomic = anyOf(
        define(RegexParsers::charClass), positiveCharacterProperty(), negativeCharacterProperty(),
        groupOrLookaround(regex), anyOf(PredefinedCharClass.values()), ANCHOR,
        literally(string("\\").then(sequence(one("[1-9]"), digits().optional()).source()))
            .map(s -> new Backreference.Numbered(Integer.parseInt(s))),
        string("\\k").then(word().between("<", ">")).map(Backreference.Named::new),
        quotedText().map(Literal::new), consecutive("[^.[]{}()*+?^$|\\ #]").map(Literal::new),
        consecutive(is('#').or(Character::isWhitespace), "whitespace or #").map(Literal::new),
        anyOf(ESCAPED, one("[{}]]").map(String::valueOf)).map(Literal::new));
    return atomic
        .followedByZeroOrMore(quantifier())
        .atLeastOnce(inSequence())
        .orElse(new RegexPattern.Literal(""))
        .delimitedBy("|", asAlternation())
        .notEmpty()
        .as("subpattern");
  }

  private static Parser<String> quotedText() {
    return anyOf(quotedBy("\\Q", "\\E"), literally(string("\\Q").then(consecutive(ANY, "quoted"))));
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Quantifier> question = one('?').thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = one('*').thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = one('+').thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> range = anyOf(
            number
                .map(Quantifier::repeated)
                .optionallyFollowedBy(
                    one(',').then(number.orElse(Integer.MAX_VALUE)),
                    (q, max) -> Quantifier.repeated(q.min(), max)),
            one(',').then(number).map(Quantifier::atMost))
        .between("{", "}");
    return anyOf(question, star, plus, range)
        .optionallyFollowedBy("?", Quantifier::reluctant)
        .optionallyFollowedBy("+", Quantifier::possessive);
  }

  private static Parser<CharacterProperty> characterPropertySuffix() {
    Parser<String> name = anyOf(
        consecutive("[^}\r\n]").as("property name").between("{", "}"),
        one("[a-zA-Z]").as("category").map(String::valueOf));
    return name.map(n -> POSIX_CHAR_CLASSES.getOrDefault(n, new UnicodeProperty(n)));
  }

  private static Parser<CharacterProperty> positiveCharacterProperty() {
    return string("\\p").then(characterPropertySuffix());
  }

  private static Parser<CharacterProperty.Negated> negativeCharacterProperty() {
    return string("\\P").then(characterPropertySuffix()).map(CharacterProperty::negated);
  }

  private static Parser<CharacterSet> charClass(Parser<CharacterSet> charClass) {
    Parser<Integer> literalChar = anyOf(
        ESCAPED.map(s -> s.codePointAt(0)),
        one("[^-&\\]]").map(c -> (int) c),
        one('&').notFollowedBy("&").map(c -> (int) c));
    Parser<LiteralChar> literalCharOrDash =
        anyOf(literalChar.map(LiteralChar::new), one('-').map(LiteralChar::new));
    Parser<CharRange> range = sequence(literalChar, one('-').then(literalChar), CharRange::new);
    Parser<CharSetElement> element = anyOf(
        positiveCharacterProperty(),
        negativeCharacterProperty(),
        anyOf(PredefinedCharClass.values())
            .suchThat(v -> !DISALLOWED_IN_CHAR_CLASS.contains(v), "predefined char class"),
        charClass,
        range,
        literalCharOrDash);
    Parser<List<CharSetElement>> quotedInClass = quotedText()
        .map(s -> s.codePoints().mapToObj(LiteralChar::new).collect(toUnmodifiableList()));
    Parser<List<CharSetElement>> elements =
        sequence(
                one(']')
                    .<CharSetElement>map(LiteralChar::new)
                    .optionallyFollowedBy(
                        one('-').then(literalChar), (unused, to) -> new CharRange(']', to))
                    .optional(),
                anyOf(quotedInClass, element.map(List::of))
                    .zeroOrMore(flatMapping(List::stream, toList())),
                (leading, rest) -> leading.map(head -> prepend(head, rest)).orElse(rest))
            .notEmpty();
    Parser<CharacterSet> characterSet =
        anyOf(charClass, elements.map(CharacterSet.AnyOf::new)).as("character set");
    return anyOf(
        intersected(elements.map(CharacterSet.NoneOf::new), characterSet).between("[^", "]"),
        intersected(elements.map(CharacterSet.AnyOf::new), characterSet).between("[", "]"));
  }

  private static Parser<CharacterSet> intersected(
      Parser<CharacterSet> primary, Parser<CharacterSet> secondary) {
    return sequence(
        primary, string("&&").then(secondary).zeroOrMore(),
        (first, rest) -> rest.isEmpty() ? first : intersection(prepend(first, rest)));
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    var groupContent = content.orElse(new Literal(""));
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
    return one('(')
        .then(
            anyOf(
                groupContent.between("?=", ")").map(Lookaround.Lookahead::new),
                groupContent.between("?!", ")").map(Lookaround.NegativeLookahead::new),
                groupContent.between("?<=", ")").map(Lookaround.Lookbehind::new),
                groupContent.between("?<!", ")").map(Lookaround.NegativeLookbehind::new),
                groupContent.between("?>", ")").map(Group.Atomic::new),
                sequence(
                        word().between(anyOf("?<", "?P<"), one('>')), groupContent,
                        Group.Named::new)
                    .followedBy(")"),
                literally(one('?').then(modifierFlags)).flatMap(identity()),
                groupContent.map(Group.Capturing::new).followedBy(")")));
  }

  private static Parser<Anchor> anchor(Anchor anchor) {
    return anchor.tokens().stream()
        .map(Parser::string)
        .reduce(Parser::then)
        .orElseThrow()
        .thenReturn(anchor);
  }

  private static <T> List<T> prepend(T first, List<T> rest) {
    List<T> list = new ArrayList<>(rest.size() + 1);
    list.add(first);
    list.addAll(rest);
    return list;
  }
}
