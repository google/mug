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
import static com.google.common.labs.parse.Parser.fail;
import static com.google.common.labs.parse.Parser.hexDigits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedBy;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.parse.Parser.zeroOrMore;
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
import static com.google.mu.util.CharPredicate.range;
import static com.google.mu.util.stream.BiStream.groupingByEach;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparingInt;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.toList;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.regex.RegexPattern.Alternation;
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
import com.google.common.labs.regex.RegexPattern.ModifierDirective;
import com.google.common.labs.regex.RegexPattern.ModifierFlag;
import com.google.common.labs.regex.RegexPattern.PosixCharClass;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantified;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.common.labs.regex.RegexPattern.Sequence;
import com.google.common.labs.regex.RegexPattern.UnicodeProperty;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.Substring;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final Parser<Integer> CODE_POINT =
      anyOf(consecutive("[0-9a-fA-F]").between("{", "}"), hexDigits(2))
          .map(hex -> parseNumber(hex, 16))
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
          .then(
              consecutive("[^}\r\n]")
                  .as("character name")
                  .between("{", "}")
                  .map(name -> {
                    try {
                      return Character.codePointOf(name);
                    } catch (IllegalArgumentException e) {
                      throw fail(e.getMessage());
                    }
                  }))
          .map(Character::toString),
      literally(
              string("\\")
                  .then(
                      one(
                          range('a', 'z').or(range('A', 'Z')).or(range('0', '9')).not(),
                          "escaped char")))
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

  /** Whitespace is ignored under free spacing mode, and {@code #} starts a comment. */
  private static final CharPredicate FREE_SPACING_CHAR = is('#').or(Character::isWhitespace);

  private static final Parser<?> FREE_SPACES = anyOf(
      consecutive(Character::isWhitespace, "whitespace"), one('#').then(consecutive("[^\n]")));
  static final Parser<RegexPattern> PARSER = define(RegexParsers::pattern);

  /** The last char that isn't a low surrogate starts the last code point. */
  private static final Substring.Pattern LAST_CODE_POINT =
      Substring.last(range(Character.MIN_LOW_SURROGATE, Character.MAX_LOW_SURROGATE).not()).toEnd();

  private static Parser<RegexPattern> pattern(Parser<RegexPattern> regex) {
    Parser<Quantifier> quantifier = quantifier();
    Parser<RegexPattern> atomic = anyOf(
        define(RegexParsers::charClass),
        positiveCharacterProperty(),
        negativeCharacterProperty(),
        groupOrLookaround(regex),
        anyOf(PredefinedCharClass.values()),
        ANCHOR,
        literally(
            string("\\")
                .then(
                    sequence(one("[1-9]"), digits().optional())
                        .source()
                        .map(s -> new Backreference.Numbered(parseNumber(s, 10))))),
        string("\\k").then(word().between("<", ">")).map(Backreference.Named::new),
        literalRun(quotedText(), quantifier),
        // free spacing chars are left to the next alternative, which free spacing mode can skip
        literalRun(
            consecutive(noneOf(".[]{}()*+?^$|\\").and(FREE_SPACING_CHAR.not()), "literal char"),
            quantifier),
        literalRun(consecutive(FREE_SPACING_CHAR, "whitespace or #"), quantifier),
        anyOf(
                ESCAPED,
                // only the trailing `]` closes the specifier, so this is the set {`}`, `]`}
                one("[}]]").map(String::valueOf),
                // `{` is a literal only when it doesn't start a repetition count. Otherwise a
                // malformed quantifier like `a{3,2}` would silently parse as a literal.
                one('{').notFollowedBy(digits(), "repetition count").map(String::valueOf))
            .map(Literal::new));
    return atomic
        .followedByZeroOrMore(quantifier)
        .atLeastOnce(inSequence())
        .orElse(new RegexPattern.Literal(""))
        .delimitedBy("|", asAlternatives())
        .notEmpty()
        .as("subpattern");
  }

  /**
   * Collects the alternatives of a subpattern.
   *
   * <p>A standalone {@code (?i)} directive applies to the rest of the enclosing group, so it parses
   * that rest, including any later alternative, as its own subtree. Those alternatives are lifted
   * back here so that {@code x(?i)a|b} stays an alternation of {@code x(?i)a} and {@code b}, not
   * {@code x} followed by {@code (?i)} and {@code (a|b)}.
   */
  private static Collector<RegexPattern, ?, RegexPattern> asAlternatives() {
    return collectingAndThen(
        toList(), alternatives -> lifted(alternatives).stream().collect(asAlternation()));
  }

  /**
   * Only the last alternative can have swallowed what follows it, and it can only have parsed it
   * into a trailing {@link Alternation}, because every other way to nest an alternation goes
   * through a {@link Group} or a {@link Lookaround}.
   */
  private static List<RegexPattern> lifted(List<RegexPattern> alternatives) {
    RegexPattern last = alternatives.get(alternatives.size() - 1);
    if (!(last instanceof Sequence sequence
        && sequence.elements().get(sequence.elements().size() - 1)
            instanceof Alternation swallowed)) {
      return alternatives;
    }
    List<RegexPattern> beforeSwallowed =
        sequence.elements().subList(0, sequence.elements().size() - 1);
    List<RegexPattern> result = new ArrayList<>(alternatives.subList(0, alternatives.size() - 1));
    result.add(
        Stream.concat(beforeSwallowed.stream(), Stream.of(swallowed.alternatives().get(0)))
            .collect(inSequence()));
    result.addAll(swallowed.alternatives().subList(1, swallowed.alternatives().size()));
    return result;
  }

  /**
   * Returns a parser of a run of literal characters, where a trailing {@code quantifier} applies to
   * the last code point of the run only. That is, {@code ab*} is an 'a' followed by zero or more
   * 'b', not zero or more "ab".
   */
  private static Parser<RegexPattern> literalRun(
      Parser<String> text, Parser<Quantifier> quantifier) {
    return sequence(text, quantifier.optional(), RegexParsers::literalRun);
  }

  private static RegexPattern literalRun(String literal, Optional<Quantifier> quantifier) {
    return quantifier
        .map(q -> quantifyLastCodePoint(literal, q))
        .orElseGet(() -> new Literal(literal));
  }

  private static RegexPattern quantifyLastCodePoint(String literal, Quantifier quantifier) {
    return LAST_CODE_POINT
        .in(literal)
        .map(lastCodePoint -> {
          RegexPattern quantified =
              new Quantified(new Literal(lastCodePoint.toString()), quantifier);
          String precedingChars = lastCodePoint.before();
          return precedingChars.isEmpty()
              ? quantified
              : RegexPattern.sequence(new Literal(precedingChars), quantified);
        })
        // an empty \Q\E: there is no last code point to quantify
        .orElseGet(() -> new Quantified(new Literal(literal), quantifier));
  }

  private static Parser<String> quotedText() {
    return anyOf(quotedBy("\\Q", "\\E"), literally(string("\\Q").then(zeroOrMore(ANY, "quoted"))));
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = digits().map(s -> parseNumber(s, 10));
    Parser<Quantifier> question = one('?').thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = one('*').thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = one('+').thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> range = anyOf(
            number
                .map(Quantifier::repeated)
                .optionallyFollowedBy(
                    one(',').then(number.orElse(Integer.MAX_VALUE)),
                    (q, max) -> {
                      try {
                        return Quantifier.repeated(q.min(), max);
                      } catch (IllegalArgumentException e) {
                        throw fail(e.getMessage());
                      }
                    }),
            one(',').then(number).map(Quantifier::atMost))
        .between("{", "}");
    return anyOf(question, star, plus, range)
        .optionallyFollowedBy("?", Quantifier::reluctant)
        .optionallyFollowedBy("+", Quantifier::possessive);
  }

  /**
   * Parses {@code digits} in {@code radix}, reporting a parse error instead of letting {@link
   * NumberFormatException} escape when the number doesn't fit in an int.
   */
  private static int parseNumber(String digits, int radix) {
    try {
      return Integer.parseInt(digits, radix);
    } catch (NumberFormatException e) {
      throw fail("number too large: " + digits);
    }
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
        // only the trailing `]` closes the specifier, so this excludes `-`, `&`, `\`, `]` and `[`
        one("[^-&\\][]").map(c -> (int) c),
        ESCAPED.map(s -> s.codePointAt(0)),
        one('&').notFollowedBy("&").map(c -> (int) c));
    Parser<CharSetElement> element = anyOf(
        anyOf(PredefinedCharClass.values())
            .suchThat(v -> !DISALLOWED_IN_CHAR_CLASS.contains(v), "predefined char class"),
        sequence(
            literalChar, one('-').then(literalChar).orElse(null),
            (c1, c2) -> c2 == null ? new LiteralChar(c1) : charRange(c1, c2)),
        one('-').map(LiteralChar::new),
        positiveCharacterProperty(),
        negativeCharacterProperty(),
        charClass);
    Parser<List<LiteralChar>> quotedChars =
        quotedText().map(s -> s.codePoints().mapToObj(LiteralChar::new).toList());
    var elements =
        sequence(
                one(']')
                    .<CharSetElement>map(LiteralChar::new)
                    .optionallyFollowedBy(
                        one('-').then(literalChar), (unused, to) -> charRange(']', to))
                    .orElse(null),
                anyOf(quotedChars, element.map(List::of))
                    .zeroOrMore(flatMapping(List::stream, toList())),
                (leading, rest) -> leading == null ? rest : prepend(leading, rest))
            .notEmpty();
    Parser<CharacterSet> characterSet =
        anyOf(elements.map(RegexParsers::toCharacterSet), charClass).as("character set");
    Parser<CharacterSet> body = intersected(elements.map(RegexPattern::anyOf), characterSet);
    return anyOf(
        body.between("[^", "]").map(RegexParsers::complementOf),
        // `^` is only a literal after the first position, so `[^]` is not an empty negated class.
        body.between(one('[').notFollowedBy("^"), one(']')));
  }

  /** Creates a {@link CharRange}, reporting an invalid range as a parse error. */
  private static CharRange charRange(int from, int to) {
    try {
      return new CharRange(from, to);
    } catch (IllegalArgumentException e) {
      throw fail(e.getMessage());
    }
  }

  /**
   * Returns the complement of {@code set}. The {@code ^} of {@code [^a-z&&d-f]} negates the whole
   * intersection, not just the leading {@code a-z}.
   */
  private static CharacterSet complementOf(CharacterSet set) {
    return set instanceof CharacterSet.AnyOf anyOf
        ? RegexPattern.noneOf(anyOf.elements())
        : RegexPattern.noneOf(set);
  }

  private static Parser<CharacterSet> intersected(
      Parser<CharacterSet> primary, Parser<CharacterSet> secondary) {
    return sequence(
        primary, string("&&").then(secondary).zeroOrMore(),
        (first, rest) -> rest.isEmpty() ? first : intersection(prepend(first, rest)));
  }

  private static CharacterSet toCharacterSet(List<? extends CharSetElement> elements) {
    return elements.size() == 1 && elements.get(0) instanceof CharacterSet cset
        ? cset
        : RegexPattern.anyOf(elements);
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    var groupContent = content.orElse(new Literal(""));
    Parser<ModifierFlag> modifier = anyOf(ModifierFlag.values()).as("modifier flag");
    var modifierFlags = sequence(
        modifier.zeroOrMore(),
        one('-').then(modifier.atLeastOnce()).orElse(List.of()),
        (enabled, disabled) -> {
          Parser<RegexPattern> scopedGroup =
              applyingFreeSpacing(groupContent.between(":", ")"), enabled, disabled)
                  .map(scoped -> new Group.NonCapturing(scoped, enabled, disabled));
          // A standalone `(?i)` applies to the rest of the enclosing group, so the rest has to be
          // parsed under the flags, crossing `|` into the later alternatives.
          Parser<RegexPattern> restOfGroup = one(')').then(groupContent);
          Parser<RegexPattern> directive = applyingFreeSpacing(restOfGroup, enabled, disabled)
              .map(rest -> directiveThen(rest, enabled, disabled));
          return anyOf(scopedGroup, trailingFreeSpacesSkipped(directive, enabled));
        });
    return one('(').then( // spaces allowed after (, under free spacing mode
        anyOf(
            groupContent.between("?=", ")").map(Lookaround.Lookahead::new),
            groupContent.between("?!", ")").map(Lookaround.NegativeLookahead::new),
            groupContent.between("?<=", ")").map(Lookaround.Lookbehind::new),
            groupContent.between("?<!", ")").map(Lookaround.NegativeLookbehind::new),
            groupContent.between("?>", ")").map(Group.Atomic::new),
            sequence(word().between(anyOf("?<", "?P<"), one('>')), groupContent, Group.Named::new)
                .followedBy(")"),
            literally(one('?').then(modifierFlags)).flatMap(identity()),
            groupContent.map(Group.Capturing::new).followedBy(")")));
  }

  /** Turns free spacing on or off for {@code parser} if the modifier flags say so. */
  private static Parser<RegexPattern> applyingFreeSpacing(
      Parser<RegexPattern> parser, List<ModifierFlag> enabled, List<ModifierFlag> disabled) {
    if (disabled.contains(ModifierFlag.COMMENTS)) {
      return literally(parser);
    }
    if (enabled.contains(ModifierFlag.COMMENTS)) {
      return parser.skipping(FREE_SPACES).within();
    }
    return parser;
  }

  /**
   * Skips the free spaces trailing the {@code directive} if it enables free spacing. They are past
   * the last token of the directive's own range, so {@link Parser.Lexical#within within()}, which
   * only skips <em>between</em> tokens, leaves them behind.
   */
  private static Parser<RegexPattern> trailingFreeSpacesSkipped(
      Parser<RegexPattern> directive, List<ModifierFlag> enabled) {
    return enabled.contains(ModifierFlag.COMMENTS)
        ? directive.followedBy(FREE_SPACES.zeroOrMore())
        : directive;
  }

  /**
   * Returns a standalone {@code (?flags)} directive, followed by the {@code rest} of the enclosing
   * group that it applies to. The enclosing {@link RegexPattern#inSequence} flattens the pair back
   * into the surrounding sequence.
   */
  private static RegexPattern directiveThen(
      RegexPattern rest, List<ModifierFlag> enabled, List<ModifierFlag> disabled) {
    ModifierDirective directive = new ModifierDirective(enabled, disabled);
    return rest.equals(new Literal("")) ? directive : RegexPattern.sequence(directive, rest);
  }

  private static Parser<Anchor> anchor(Anchor anchor) {
    return anchor.tokens().stream()
        .map(Parser::string)
        .reduce(Parser::then)
        .orElseThrow()
        .thenReturn(anchor);
  }

  private static <T> List<T> prepend(T first, List<? extends T> rest) {
    List<T> list = new ArrayList<>(rest.size() + 1);
    list.add(first);
    list.addAll(rest);
    return list;
  }
}
