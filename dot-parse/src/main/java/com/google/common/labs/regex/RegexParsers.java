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
import com.google.mu.util.Substring;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
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
          // java.util.regex XORs the character as written; it does not upper-case it first, so
          // `\ca` is '!' (0x61 ^ 64), not U+0001.
          .map(c -> Character.toString(c ^ 64)),
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

  /**
   * A capturing group name: a Latin letter followed by Latin letters and digits, which is what
   * {@code java.util.regex} accepts. It's {@code literally} so free spacing can't hide a space in
   * the middle of a name.
   */
  private static final Parser<String> GROUP_NAME =
      literally(one("[a-zA-Z]"), zeroOrMore("[a-zA-Z0-9]")).source().as("group name");

  /** Any whitespace, which is wider than the set free spacing mode skips. */
  private static final CharPredicate WHITESPACE = Character::isWhitespace;

  /**
   * The characters free spacing mode skips, which are the six that {@code Pattern.isSpace()}
   * accepts. {@code Character::isWhitespace} would be wider: it also covers the information
   * separators U+001C..U+001F and the Unicode space separators, all of which {@code
   * java.util.regex} reads as literal text.
   */
  private static final CharPredicate FREE_SPACE = CharPredicate.anyOf(" \t\n\u000B\f\r");

  /**
   * The characters a {@code #} comment may contain, that is anything but a line terminator as
   * {@code Pattern.isLineSeparator()} defines it (with {@code UNIX_LINES} off). Of the five
   * terminators only {@code \n} and {@code \r} are also free spaces; the other three end the
   * comment and then stay in the pattern as literal characters.
   */
  private static final CharPredicate COMMENT_CHAR = noneOf("\n\r\u0085\u2028\u2029");

  /** Free spaces are ignored under free spacing mode, and {@code #} starts a comment. */
  private static final CharPredicate FREE_SPACING_CHAR = is('#').or(FREE_SPACE);

  private static final Parser<?> FREE_SPACES = anyOf(
      consecutive(FREE_SPACE, "whitespace"), one('#').then(zeroOrMore(COMMENT_CHAR, "comment")));
  private static final Parser<RegexPattern> PARSER = define(RegexParsers::pattern);

  /**
   * A {@code (?flags)} directive at the very start of the pattern, applying to the whole pattern.
   *
   * <p>Unlike {@link #standaloneDirective}, this one takes the rest of the pattern as its operand
   * rather than standing beside it as a sibling, which is what allows the {@code x} flag to switch
   * the rest of the pattern to free spacing as it is tokenized. Taking an operand costs nothing
   * here: the operand is everything that follows, so no {@code |} is reparented and the tree is the
   * same one a sibling node would have produced, except that the alternation sits under the
   * directive and therefore reads as being in its scope.
   */
  private static final Parser<RegexPattern> LEADING_DIRECTIVE = string("(?")
      .then(
          afterModifierFlags((enabled, disabled) -> trailingFreeSpacesSkipped(
              applyingFreeSpacing(one(')').then(PARSER.orElse(new Literal(""))), enabled, disabled)
                  .map(rest -> directiveThen(enabled, disabled, rest)),
              enabled)))
      .flatMap(identity());

  static final Parser<RegexPattern> TOP_LEVEL = anyOf(LEADING_DIRECTIVE, PARSER);

  /** The last char that isn't a low surrogate starts the last code point. */
  private static final Substring.Pattern LAST_CODE_POINT =
      Substring.last(range(Character.MIN_LOW_SURROGATE, Character.MAX_LOW_SURROGATE).not()).toEnd();

  private static Parser<RegexPattern> pattern(Parser<RegexPattern> regex) {
    Parser<Quantifier> quantifier = quantifier();
    Parser<RegexPattern> atomic = anyOf(
        quantifiable(define(RegexParsers::charClass), quantifier),
        quantifiable(positiveCharacterProperty(), quantifier),
        quantifiable(negativeCharacterProperty(), quantifier),
        quantifiable(groupOrLookaround(regex, quantifier), quantifier),
        quantifiable(anyOf(PredefinedCharClass.values()), quantifier),
        quantifiable(ANCHOR, quantifier),
        quantifiable(
            literally(
                string("\\")
                    .then(
                        sequence(one("[1-9]"), digits().optional())
                            .source()
                            .map(s -> new Backreference.Numbered(parseNumber(s, 10))))),
            quantifier),
        quantifiable(
            string("\\k").then(GROUP_NAME.between("<", ">")).map(Backreference.Named::new),
            quantifier),
        literalRun(quotedText(), quantifier),
        // free spacing chars are left to the next alternative, which free spacing mode can skip
        literalRun(
            consecutive(noneOf(".[]{}()*+?^$|\\").and(FREE_SPACING_CHAR.not()), "literal char"),
            quantifier),
        literalRun(consecutive(FREE_SPACING_CHAR, "whitespace or #"), quantifier),
        // a run, not a single atom: two hex escapes can spell one supplementary code point, and a
        // trailing quantifier must apply to that code point, not to the low surrogate alone.
        literalRun(
            anyOf(
                    ESCAPED,
                    // only the trailing `]` closes the specifier, so this is the set {`}`, `]`}
                    one("[}]]").map(String::valueOf),
                    // `{` is a literal only when it doesn't look like a repetition count.
                    // Otherwise a malformed quantifier like `a{3,2}`, or `a{,2}` which Java has no
                    // spelling for, would silently parse as a literal.
                    one('{')
                        .notFollowedBy(anyOf(digits(), one(',').then(digits())), "repetition count")
                        .map(String::valueOf))
                .atLeastOnce(joining()),
            quantifier));
    return atomic
        .atLeastOnce(inSequence())
        .orElse(new RegexPattern.Literal(""))
        .delimitedBy("|", asAlternation())
        .notEmpty()
        .as("subpattern");
  }

  /**
   * Returns {@code atom} with at most one quantifier bound to it. A second quantifier is a dangling
   * metacharacter, as in {@code a+*}, not a quantifier of a quantifier.
   */
  private static Parser<RegexPattern> quantifiable(
      Parser<? extends RegexPattern> atom, Parser<Quantifier> quantifier) {
    @SuppressWarnings("unchecked")
    Parser<RegexPattern> widened = (Parser<RegexPattern>) atom;
    return widened.optionallyFollowedBy(quantifier, Quantified::new);
  }

  /**
   * Returns a parser of a run of literal characters, where a trailing {@code quantifier} applies to
   * the last code point of the run only. That is, {@code ab*} is an 'a' followed by zero or more
   * 'b', not zero or more "ab".
   */
  private static Parser<RegexPattern> literalRun(
      Parser<String> text, Parser<Quantifier> quantifier) {
    return sequence(
        text, suffix(quantifier, RegexParsers::quantifyLastCodePoint).orElse(Literal::new),
        Suffix::apply);
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
    Parser<Quantifier> range =
    // `{n}`, `{n,m}` and `{n,}` are three different quantifiers: an absent max can't be
    // spelled as Integer.MAX_VALUE, which is a repetition count a pattern can ask for.
    sequence(
            number,
            anyOf(
                    suffix(one(',').then(number), RegexParsers::repetitionRange),
                    Suffix.<Integer, Quantifier>suffix(",", Quantifier::atLeast))
                .orElse(Quantifier::repeated),
            Suffix::apply)
        .between("{", "}");
    return anyOf(question, star, plus, range)
        .optionallyFollowedBy("?", Quantifier::reluctant)
        .optionallyFollowedBy("+", Quantifier::possessive);
  }

  /** Creates a {@code {min,max}} quantifier, reporting an invalid range as a parse error. */
  private static Quantifier repetitionRange(int min, int max) {
    try {
      return Quantifier.repeated(min, max);
    } catch (IllegalArgumentException e) {
      throw fail(e.getMessage());
    }
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
        // No property name java.util.regex knows has whitespace in it, so `\p{ L }` is an unknown
        // name there. Under free spacing the whitespace is skipped before the name is read.
        consecutive(noneOf("}\r\n").and(WHITESPACE.not()), "property name").between("{", "}"),
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
    // `-` is a literal only where it can't form a range. It's legal at either end of one, so
    // `[--z]` is U+002D through `z`, and `[!--]` is `!` through U+002D.
    Parser<Integer> rangeChar = anyOf(literalChar, one('-').map(c -> (int) c));
    Parser<CharSetElement> charClassOrProperty = anyOf(
        anyOf(PredefinedCharClass.values())
            .suchThat(v -> !DISALLOWED_IN_CHAR_CLASS.contains(v), "predefined char class"),
        positiveCharacterProperty(),
        negativeCharacterProperty());
    Parser<CharSetElement> element = anyOf(charClassOrProperty, charClass);
    // A `\Q...\E` quote contributes its chars to the class body as if they were written out, so
    // both it and a single char are just runs of code points here.
    Parser<int[]> charRun = anyOf(
        quotedText().map(quoted -> quoted.codePoints().toArray()),
        rangeChar.map(c -> new int[] {c}));
    Parser<List<CharSetElement>> chars = sequence(
            charRun,
            suffix(one('-').then(charRun), RegexParsers::charsThroughRange)
                .orElse(RegexParsers::literalChars),
            Suffix::apply)
        // A class has no single code point to close a range with, so `[a-\d]` is an error, as in
        // `java.util.regex`. Rejecting it here also rules out reading the `-` as a literal member,
        // which is what the range suffix above falls back to. A nested class is not a class here:
        // Java reads `[a-[b]]` as plain members.
        .notFollowedBy(one('-').then(charClassOrProperty), "character class as a range end");
    var elements =
        sequence(
                one(']')
                    .<CharSetElement>map(LiteralChar::new)
                    .optionallyFollowedBy(
                        one('-').then(literalChar), (unused, to) -> charRange(']', to))
                    .orElse(null),
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

  /** Returns the code points of a char run, each a literal member of a char class. */
  private static List<CharSetElement> literalChars(int[] codePoints) {
    return stream(codePoints).<CharSetElement>mapToObj(LiteralChar::new).toList();
  }

  /**
   * Returns the members of {@code from-to}, where each end is a run of code points. Only the last
   * code point of {@code from} and the first of {@code to} bound the range; the rest are literal
   * members, so {@code [\Qab\E-\Qzz\E]} is {@code a}, the range {@code b-z}, and {@code z}. An
   * empty run leaves nothing to bound the range with, so {@code [a-\Q\E]} is {@code a} and a
   * literal {@code -}, as in {@code java.util.regex}.
   */
  private static List<CharSetElement> charsThroughRange(int[] from, int[] to) {
    if (from.length == 0 || to.length == 0) {
      List<CharSetElement> elements = new ArrayList<>(literalChars(from));
      elements.add(new LiteralChar('-'));
      elements.addAll(literalChars(to));
      return elements;
    }
    List<CharSetElement> elements = new ArrayList<>(from.length + to.length - 1);
    elements.addAll(literalChars(Arrays.copyOf(from, from.length - 1)));
    elements.add(charRange(from[from.length - 1], to[0]));
    elements.addAll(literalChars(Arrays.copyOfRange(to, 1, to.length)));
    return elements;
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

  private static Parser<RegexPattern> groupOrLookaround(
      Parser<RegexPattern> content, Parser<Quantifier> quantifier) {
    var groupContent = content.orElse(new Literal(""));
    var modifierFlags = afterModifierFlags((enabled, disabled) -> {
      Parser<RegexPattern> scopedGroup =
          applyingFreeSpacing(groupContent.between(":", ")"), enabled, disabled)
              .map(scoped -> new Group.NonCapturing(scoped, enabled, disabled));
      // A zero-width node has nothing for a quantifier to repeat, so `a(?i)*` is a dangling
      // `*`, the same as `a+*` is. The enclosing quantifiable() would otherwise bind it.
      Parser<RegexPattern> directive = one(')')
          .notFollowedBy(quantifier, "quantifier")
          .map(closed -> standaloneDirective(enabled, disabled));
      return anyOf(scopedGroup, directive);
    });
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
   * Returns the leading {@code (?flags)} directive, followed by the {@code rest} of the pattern
   * that it applies to. The enclosing {@link RegexPattern#inSequence} flattens the pair back into
   * the surrounding sequence.
   */
  private static RegexPattern directiveThen(
      List<ModifierFlag> enabled, List<ModifierFlag> disabled, RegexPattern rest) {
    ModifierDirective directive = new ModifierDirective(enabled, disabled);
    return rest.equals(new Literal(""))
        ? directive
        : Stream.of(directive, rest).collect(inSequence());
  }

  /**
   * Parses the {@code enabled[-disabled]} flag list of a {@code (?...)} construct and lets {@code
   * next} decide, from the flags, which parser to continue with. The result is a parser of a
   * parser, to be {@link Parser#flatMap flatMap}ped with {@code identity()}.
   */
  private static Parser<Parser<RegexPattern>>.OrEmpty afterModifierFlags(
      BiFunction<List<ModifierFlag>, List<ModifierFlag>, Parser<RegexPattern>> next) {
    Parser<ModifierFlag> modifier = anyOf(ModifierFlag.values()).as("modifier flag");
    return sequence(
        modifier.zeroOrMore(), one('-').then(modifier.atLeastOnce()).orElse(List.of()), next);
  }

  /**
   * Returns the zero-width node of a standalone {@code (?flags)} directive, which the enclosing
   * sequence emits as a sibling preceding the elements the flags apply to.
   *
   * <p>The {@code x} (free spacing) flag is rejected here. Free spacing is lexical: whether a space
   * is a token has to be decided while the rest of the group is tokenized, so honoring it would
   * mean parsing the rest of the group as this directive's operand rather than as its siblings.
   * That operand would have to extend across {@code |} too, since the flags reach the later
   * alternatives, which inverts the precedence of {@code |} and then has to be undone. A leading
   * {@code (?x)} does not have that problem, because its operand is the whole pattern and no
   * precedence is disturbed; {@link #LEADING_DIRECTIVE} handles that one. Everywhere else, {@code
   * (?x:...)} says the same thing with the scope spelled out.
   */
  private static RegexPattern standaloneDirective(
      List<ModifierFlag> enabled, List<ModifierFlag> disabled) {
    if (enabled.contains(ModifierFlag.COMMENTS) || disabled.contains(ModifierFlag.COMMENTS)) {
      throw fail(
          "free spacing flag (x) is only supported at the start of the pattern;"
              + " use (?x:...) to scope it");
    }
    return new ModifierDirective(enabled, disabled);
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
