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
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.labs.parse.Parsers.BMP_CODE_UNIT;
import static com.google.common.labs.parse.Parsers.Suffix.suffix;
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
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.parse.Parsers.Suffix;
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
import com.google.common.labs.regex.RegexPattern.UnicodeProperty;
import com.google.mu.util.CharPredicate;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final Literal EMPTY = new Literal("");

  /** In {@code \N{Foo Bar}} or {@code \p{Is Lower}}, everything up to the brace is the name. */
  private static final CharPredicate BRACED_NAME_CHAR = noneOf("}\r\n");

  private static final CharPredicate LATIN_LETTER = range('a', 'z').or(range('A', 'Z'));
  private static final CharPredicate ALPHANUMERIC = LATIN_LETTER.or(range('0', '9'));
  private static final Parser<Integer> CODE_POINT =
      anyOf(consecutive("[0-9a-fA-F]").between("{", "}"), hexDigits(2))
          .map(hex -> parseNumber(hex, 16))
          .suchThat(Character::isValidCodePoint, "code point");
  private static final Parser<Integer> OCTAL = anyOf(
          literally(one("[0-3]"), one("[0-7]").optional(), one("[0-7]").optional()),
          literally(one("[4-7]"), one("[0-7]").optional()))
      .source()
      .map(digits -> Integer.parseInt(digits, 8));

  private static final Parser<Integer> ESCAPED = anyOf(
      string("\\n").thenReturn((int) '\n'),
      string("\\r").thenReturn((int) '\r'),
      string("\\t").thenReturn((int) '\t'),
      string("\\f").thenReturn((int) '\f'),
      string("\\a").thenReturn(0x0007),
      string("\\e").thenReturn(0x001B),
      string("\\u").then(BMP_CODE_UNIT).map(c -> (int) c),
      string("\\0").then(OCTAL),
      string("\\c")
          .then(one(ANY, "control char"))
          // java.util.regex XORs the character as written; it does not upper-case it first, so
          // `\ca` is '!' (0x61 ^ 64), not U+0001.
          .map(c -> c ^ 64),
      string("\\x").then(CODE_POINT),
      string("\\N")
          .then(
              consecutive(BRACED_NAME_CHAR, "character name")
                  .between("{", "}")
                  .map(name -> {
                    try {
                      return Character.codePointOf(name);
                    } catch (IllegalArgumentException e) {
                      throw fail(e.getMessage());
                    }
                  })),
      literally(string("\\").then(one(ALPHANUMERIC.not(), "escaped char"))).map(c -> (int) c));

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

  /** {@code (?<n1>...)} yes; {@code (?<1n>...)}, {@code (?<a_b>...)}, {@code (?<a b>...)} no. */
  private static final Parser<String> GROUP_NAME =
      literally(one(LATIN_LETTER, "Latin letter"), zeroOrMore(ALPHANUMERIC, "alphanumeric"))
          .source()
          .as("group name");

  /** {@code (?x)a b} is {@code ab}, but {@code (?x)a\u00A0b} keeps the NBSP: only these six. */
  private static final CharPredicate FREE_SPACE = CharPredicate.anyOf(" \t\n\u000B\f\r");

  /** {@code a #b} is the literal {@code a #b} normally and just {@code a} under {@code (?x)}. */
  private static final CharPredicate FREE_SPACING_CHAR = is('#').or(FREE_SPACE);

  /** {@code ab-c} is one literal run; {@code a.b}, {@code a b} and {@code a#b} are not. */
  private static final CharPredicate LITERAL_CHAR =
      noneOf(".[]{}()*+?^$|\\").and(FREE_SPACING_CHAR.not());

  private static final Parser<?> FREE_SPACES = anyOf(
      consecutive(FREE_SPACE, "whitespace"),
      one('#').then(zeroOrMore(noneOf("\n\r\u0085\u2028\u2029"), "comment")));

  private static final Parser<ModifierFlag> MODIFIER =
      anyOf(ModifierFlag.values()).as("modifier flag");

  /** The {@code i-sx} of {@code (?i-sx)} and {@code (?i-sx:...)}; empty in {@code (?:...)}. */
  private static final Parser<ModifierFlags>.OrEmpty MODIFIER_FLAGS = sequence(
      MODIFIER.zeroOrMore(),
      one('-').then(MODIFIER.atLeastOnce()).orElse(List.of()),
      ModifierFlags::new);

  private static final Parser<Quantifier> QUANTIFIER = quantifier();

  private static final Parser<RegexPattern> PARSER = define(RegexParsers::pattern);

  static final Parser<RegexPattern> TOP_LEVEL =
      anyOf(string("(?").then(MODIFIER_FLAGS).flatMap(flags -> flags.until(')')), PARSER);

  private static Parser<RegexPattern> pattern(Parser<RegexPattern> regex) {
    Parser<RegexPattern> atom = anyOf(
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
                        .suchThat(digits -> digits.length() == 1, "single-digit backreference")
                        .map(s -> new Backreference.Numbered(parseNumber(s, 10))))),
        string("\\k").then(GROUP_NAME.between("<", ">")).map(Backreference.Named::new));
    Parser<String> literalText = anyOf(
        quotedText(),
        // free spacing chars are left to the next alternative, which free spacing mode can skip
        consecutive(LITERAL_CHAR, "literal char"),
        consecutive(FREE_SPACING_CHAR, "whitespace or #"),
        // a run, not a single atom: two hex escapes can spell one supplementary code point, and a
        // trailing quantifier must apply to that code point, not to the low surrogate alone.
        anyOf(
                ESCAPED.map(Character::toString),
                // only the trailing `]` closes the specifier, so this is the set {`}`, `]`}
                one("[}]]").map(String::valueOf),
                // `{` is a literal only when it doesn't look like a repetition count. Otherwise a
                // malformed quantifier like `a{3,2}`, or `a{,2}` which Java has no spelling for,
                // would silently parse as a literal.
                one('{')
                    .notFollowedBy(anyOf(digits(), one(',').then(digits())), "repetition count")
                    .map(String::valueOf))
            .atLeastOnce(joining()));
    // At most one quantifier binds to an atom. A second one is a dangling metacharacter, as in
    // `a+*`, not a quantifier of a quantifier.
    Parser<RegexPattern> atomic = anyOf(
        atom.optionallyFollowedBy(QUANTIFIER, Quantified::new),
        literalRun(literalText, QUANTIFIER));
    return atomic
        .atLeastOnce(inSequence())
        .orElse(EMPTY)
        .delimitedBy("|", asAlternation())
        .notEmpty()
        .as("subpattern");
  }

  /** {@code ab*} is {@code a} then {@code b*}; {@code \uD83D\uDE00*} quantifies the pair. */
  private static Parser<RegexPattern> literalRun(
      Parser<String> text, Parser<Quantifier> quantifier) {
    return sequence(
        text, suffix(quantifier, RegexParsers::quantifyLastCodePoint).orElse(Literal::new),
        Suffix::apply);
  }

  private static RegexPattern quantifyLastCodePoint(String literal, Quantifier quantifier) {
    if (literal.isEmpty()) { // an empty \Q\E: there is no last code point to quantify
      return new Quantified(new Literal(literal), quantifier);
    }
    int split = literal.length() - Character.charCount(literal.codePointBefore(literal.length()));
    Quantified quantified = new Quantified(new Literal(literal.substring(split)), quantifier);
    return split == 0
        ? quantified
        : RegexPattern.sequence(new Literal(literal.substring(0, split)), quantified);
  }

  private static Parser<String> quotedText() {
    return anyOf(quotedBy("\\Q", "\\E"), literally(string("\\Q").then(zeroOrMore(ANY, "quoted"))));
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = digits().map(s -> parseNumber(s, 10));
    Parser<UnaryOperator<Quantifier>> modifier = anyOf(
        one('?').thenReturn(Quantifier::reluctant), one('+').thenReturn(Quantifier::possessive));
    Parser<Quantifier> atLeast = sequence(
        number,
        anyOf(
                suffix(one(',').then(number), RegexParsers::repetitionRange),
                suffix(",", Quantifier::atLeast))
            .orElse(Quantifier::repeated),
        Suffix::apply);
    Parser<Quantifier> atMost = one(',').then(number).map(Quantifier::atMost);
    return anyOf(
            one('?').thenReturn(Quantifier.atMost(1)),
            one('*').thenReturn(Quantifier.repeated()),
            one('+').thenReturn(Quantifier.atLeast(1)),
            atLeast.between("{", "}"),
            atMost.between("{", "}"))
        .optionallyFollowedBy(modifier, Suffix::apply);
  }

  /** Creates a {@code {min,max}} quantifier, reporting an invalid range as a parse error. */
  private static Quantifier repetitionRange(int min, int max) {
    try {
      return Quantifier.repeated(min, max);
    } catch (IllegalArgumentException e) {
      throw fail(e.getMessage());
    }
  }

  private static int parseNumber(String digits, int radix) {
    try {
      return Integer.parseInt(digits, radix);
    } catch (NumberFormatException e) {
      throw fail("number too large: " + digits);
    }
  }

  private static Parser<CharacterProperty> characterPropertySuffix() {
    Parser<String> name = anyOf(
        // The JDK slices to the `}` and looks the name up, so a misplaced space is just an
        // unknown name. `literally` keeps free spacing out of the name; before `{` it's skipped.
        literally(consecutive(BRACED_NAME_CHAR, "property name")).between("{", "}"),
        one(LATIN_LETTER, "category").map(String::valueOf));
    return name.map(n -> POSIX_CHAR_CLASSES.getOrDefault(n, new UnicodeProperty(n)));
  }

  private static Parser<CharacterProperty> positiveCharacterProperty() {
    return string("\\p").then(characterPropertySuffix());
  }

  private static Parser<CharacterProperty.Negated> negativeCharacterProperty() {
    return string("\\P").then(characterPropertySuffix()).map(CharacterProperty::negated);
  }

  private static Parser<CharacterSet> charClass(Parser<CharacterSet> charClass) {
    // `-` is a literal only where it can't form a range. It's legal at either end of one, so
    // `[--z]` is U+002D through `z`, and `[!--]` is `!` through U+002D.
    Parser<int[]> rangeChar =
        anyOf(literalChar(RegexParsers::toArray), one('-').thenReturn(toArray('-')));
    Parser<CharSetElement> charClassOrProperty = anyOf(
        anyOf(PredefinedCharClass.values())
            .suchThat(v -> !DISALLOWED_IN_CHAR_CLASS.contains(v), "predefined char class"),
        positiveCharacterProperty(),
        negativeCharacterProperty());
    Parser<CharSetElement> element = anyOf(charClassOrProperty, charClass);
    // A `\Q...\E` quote contributes its chars as if written out, so it and a single char are both
    // runs of code points and one form serves every member. (Measured: a separate plain-char
    // alternative, or a `notFollowedBy("-")` shortcut in front, is slower, not faster.)
    Parser<int[]> quotedRun = quotedText().map(quoted -> quoted.codePoints().toArray());
    Parser<int[]> rangeStartOrEnd = anyOf(rangeChar, quotedRun);
    // `[a-\d]` is an error, as in the JDK. The lookahead on the range start is what rejects it:
    // the optional range suffix below would otherwise fall back to a literal `-`. A `-` after a
    // closed range stays literal, so `[a-z-\d]` is legal; `[a-[b]]` is plain members, as in Java.
    Parser<List<CharSetElement>> chars = sequence(
        rangeStartOrEnd.notFollowedBy(
            one('-').then(charClassOrProperty), "character class as a range end"),
        one('-').then(rangeStartOrEnd).orElse(null),
        (from, to) -> to == null ? literalChars(from) : charsThroughRange(from, to));
    var elements =
        sequence(
                one(']')
                    .<CharSetElement>map(LiteralChar::new)
                    .optionallyFollowedBy(
                        one('-').then(literalChar(c -> (int) c)),
                        (unused, to) -> charRange(']', to))
                    .orElse(null),
                // a class or property first: it's the cheaper test, and the two can't both match
                anyOf(element.map(List::of), chars).zeroOrMore(flatMapping(List::stream, toList())),
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

  private static <T> Parser<T> literalChar(IntFunction<? extends T> f) {
    return anyOf(
        // only the trailing `]` closes the specifier, so this excludes `-`, `&`, `\`, `]` and `[`
        one("[^-&\\][]").map(c -> f.apply(c)),
        ESCAPED.map(f::apply),
        one('&').notFollowedBy("&").thenReturn(f.apply('&')));
  }

  /** {@code [\Qabc\E]} is {@code [abc]}: each code point a member. */
  private static List<CharSetElement> literalChars(int[] codePoints) {
    return new AbstractList<CharSetElement>() {
      @Override public LiteralChar get(int i) {
        return new LiteralChar(codePoints[i]);
      }

      @Override public int size() {
        return codePoints.length;
      }
    };
  }

  /** {@code [\Qab\E-\Qzz\E]} is {@code [ab-zz]}; {@code [a-\Q\E]} is {@code [a\-]}. */
  private static List<CharSetElement> charsThroughRange(int[] from, int[] to) {
    if (from.length == 0 || to.length == 0) {
      List<CharSetElement> elements = new ArrayList<>(from.length + to.length + 1);
      elements.addAll(literalChars(from));
      elements.add(new LiteralChar('-'));
      elements.addAll(literalChars(to));
      return elements;
    }
    CharRange range = charRange(from[from.length - 1], to[0]);
    if (from.length == 1 && to.length == 1) {
      return List.of(range);
    }
    List<CharSetElement> elements = new ArrayList<>(from.length + to.length - 1);
    for (int i = 0; i < from.length - 1; i++) {
      elements.add(new LiteralChar(from[i]));
    }
    elements.add(range);
    for (int i = 1; i < to.length; i++) {
      elements.add(new LiteralChar(to[i]));
    }
    return elements;
  }

  /** The {@code ^} of {@code [^a-z&&d-f]} negates the whole intersection, not just {@code a-z}. */
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
    var groupContent = content.orElse(EMPTY);
    Parser<RegexPattern> scoped = groupContent.between(":", ")");
    // After `(?flags`: `:` content `)` scopes the flags; a bare `)` is a standalone directive.
    Parser<RegexPattern> modified = literally(one('?').then(MODIFIER_FLAGS))
        .flatMap(flags -> anyOf(
            flags.modifying(scoped),
            // A directive is zero-width, so `a(?i)*` is a dangling `*` like `a+*`, not a
            // quantifier of it; the optional quantifier after an atom would otherwise bind it.
            one(')')
                .suchThat(closed -> !flags.hasCommentMode(), "inline modifier flags without (x)")
                .notFollowedBy(QUANTIFIER, "quantifier")
                .thenReturn(flags.asDirective())));
    return one('(').then( // spaces allowed after (, under free spacing mode
        anyOf(
            groupContent.between("?=", ")").map(Lookaround.Lookahead::new),
            groupContent.between("?!", ")").map(Lookaround.NegativeLookahead::new),
            groupContent.between("?<=", ")").map(Lookaround.Lookbehind::new),
            groupContent.between("?<!", ")").map(Lookaround.NegativeLookbehind::new),
            groupContent.between("?>", ")").map(Group.Atomic::new),
            sequence(
                    GROUP_NAME.between(anyOf("?<", "?P<"), one('>')), groupContent,
                    Group.Named::new)
                .followedBy(")"),
            modified,
            groupContent.map(Group.Capturing::new).followedBy(")")));
  }

  /** {@code (?i-sx)} or {@code (?i-sx:...)}: enabled {@code [i]}, disabled {@code [s, x]}. */
  private record ModifierFlags(List<ModifierFlag> enabled, List<ModifierFlag> disabled) {
    Parser<RegexPattern> until(char delimiter) {
      var enclosed = applyFreeSpacing(one(delimiter).then(PARSER.orElse(EMPTY)));
      if (enabled.contains(ModifierFlag.COMMENTS)) {
        enclosed = enclosed.followedBy(FREE_SPACES.zeroOrMore());
      }
      return enclosed.map(
          rest -> rest.equals(EMPTY) ? asDirective() : RegexPattern.sequence(asDirective(), rest));
    }

    Parser<RegexPattern> modifying(Parser<RegexPattern> content) {
      return applyFreeSpacing(content).map(c -> new Group.NonCapturing(c, enabled, disabled));
    }

    boolean hasCommentMode() {
      return enabled.contains(ModifierFlag.COMMENTS) || disabled.contains(ModifierFlag.COMMENTS);
    }

    RegexPattern asDirective() {
      return new ModifierDirective(enabled, disabled);
    }

    private <T> Parser<T> applyFreeSpacing(Parser<T> parser) {
      if (disabled.contains(ModifierFlag.COMMENTS)) {
        return literally(parser);
      }
      if (enabled.contains(ModifierFlag.COMMENTS)) {
        return parser.skipping(FREE_SPACES).within();
      }
      return parser;
    }
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

  private static int[] toArray(int i) {
    return new int[] {i};
  }
}
