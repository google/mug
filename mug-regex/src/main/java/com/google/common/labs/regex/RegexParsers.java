package com.google.common.labs.regex;

import static com.google.mu.util.stream.BiStream.groupingByEach;
import static com.google.mu.util.stream.MoreCollectors.onlyElement;
import static java.util.Arrays.stream;

import java.util.Map;

import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.CharRange;
import com.google.common.labs.regex.RegexPattern.Group;
import com.google.common.labs.regex.RegexPattern.Literal;
import com.google.common.labs.regex.RegexPattern.LiteralChar;
import com.google.common.labs.regex.RegexPattern.Lookaround;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.mu.util.CharPredicate;
import com.google.mu.util.stream.BiCollectors;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final CharPredicate NUM = CharPredicate.range('0', '9');
  private static final CharPredicate ALPHA =
      CharPredicate.range('a', 'z').orRange('A', 'Z').or('_');
  private static final Parser<Character> ESCAPED_CHAR =
      Parser.literal("\\").then(Parser.single(c -> true, "escaped char"));
  private static final Map<String, RegexPattern.CharacterProperty> POSIX_CHAR_CLASS_MAP =
      stream(RegexPattern.PosixCharClass.values())
          .collect(groupingByEach(charClass -> charClass.names().stream(), onlyElement(c -> c)))
          .collect(BiCollectors.toMap());

  static Parser<RegexPattern> pattern() {
    var lazy = new Parser.Lazy<RegexPattern>();
    Parser<RegexPattern> atomic =
        Parser.anyOf(
            charClass(),
            positiveCharacterProperty(),
            negativeCharacterProperty(),
            groupOrLookaround(lazy),
            anyOf(PredefinedCharClass.values()),
            anyOf(Anchor.values()),
            Parser.consecutive(CharPredicate.noneOf(".[]{}()*+?^$|\\"), "literal char")
                .map(Literal::new),
            ESCAPED_CHAR.map(c -> new Literal(Character.toString(c))));
    Parser<RegexPattern> sequence =
        atomic.postfix(quantifier()).atLeastOnce(RegexPattern.inSequence());
    return lazy.delegateTo(sequence.delimitedBy("|", RegexPattern.asAlternation()));
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = Parser.consecutive(NUM, "digit for quantifier").map(Integer::parseInt);
    Parser<Quantifier> question = Parser.literal("?").thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = Parser.literal("*").thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = Parser.literal("+").thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> exact = number.immediatelyBetween("{", "}").map(Quantifier::repeated);
    Parser<Quantifier> atLeast =
        number.followedBy(",").immediatelyBetween("{", "}").map(Quantifier::atLeast);
    Parser<Quantifier> atMost =
        Parser.literal(",").then(number).immediatelyBetween("{", "}").map(Quantifier::atMost);
    Parser<Quantifier> range =
        Parser.sequence(number, Parser.literal(",").then(number), Quantifier::repeated)
            .immediatelyBetween("{", "}");
    return Parser.anyOf(question, star, plus, exact, atLeast, atMost, range)
        .optionallyFollowedBy("?", Quantifier::reluctant)
        .optionallyFollowedBy("+", Quantifier::possessive);
  }

  private static Parser<RegexPattern.CharacterProperty> positiveCharacterProperty() {
    return Parser.literal("\\p").then(characterPropertySuffix());
  }

  private static Parser<RegexPattern.CharacterProperty.Negated> negativeCharacterProperty() {
    return Parser.literal("\\P")
        .then(characterPropertySuffix())
        .map(RegexPattern.CharacterProperty::negated);
  }

  private static Parser<RegexPattern.CharacterProperty> characterPropertySuffix() {
    return Parser.consecutive(ALPHA.or(NUM), "character property name")
        .immediatelyBetween("{", "}")
        .map(name -> POSIX_CHAR_CLASS_MAP.getOrDefault(name, new RegexPattern.UnicodeProperty(name)));
  }


  private static Parser<RegexPattern.CharacterSet> charClass() {
    Parser<Character> literalChar =
        Parser.anyOf(
            ESCAPED_CHAR, Parser.single(CharPredicate.noneOf("-]\\"), "literal char"));
    Parser<Character> literalCharOrDash =
        Parser.anyOf(
            ESCAPED_CHAR, Parser.single(CharPredicate.noneOf("]\\"), "literal char or dash"));
    Parser<CharRange> range =
        Parser.sequence(
            literalChar, Parser.literal("-").then(literalChar), RegexPattern.CharRange::new);
    var element =
        Parser.anyOf(
            positiveCharacterProperty(),
            negativeCharacterProperty(),
            anyOf(PredefinedCharClass.values()),
            range,
            literalCharOrDash.map(LiteralChar::new));
    return Parser.anyOf(
        element.atLeastOnce().immediatelyBetween("[^", "]").map(RegexPattern::noneOf),
        element.atLeastOnce().immediatelyBetween("[", "]").map(RegexPattern::anyOf));
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    Parser<Group.Named> named =
        Parser.consecutive(ALPHA.or(NUM), "alphanumeric for group name")
            .immediatelyBetween(Parser.literal("?P<").or(Parser.literal("?<")), Parser.literal(">"))
            .flatMap(n -> content.map(c -> new Group.Named(n, c)))
            .immediatelyBetween("(", ")");
    return Parser.anyOf(
        named,
        content.immediatelyBetween("(?=", ")").map(Lookaround.Lookahead::new),
        content.immediatelyBetween("(?!", ")").map(Lookaround.NegativeLookahead::new),
        content.immediatelyBetween("(?<=", ")").map(Lookaround.Lookbehind::new),
        content.immediatelyBetween("(?<!", ")").map(Lookaround.NegativeLookbehind::new),
        content.immediatelyBetween("(?:", ")").map(Group.NonCapturing::new),
        content.immediatelyBetween("(", ")").map(Group.Capturing::new));
  }

  @SafeVarargs
  private static <E extends Enum<E>> Parser<E> anyOf(E... values) {
    return stream(values).map(e -> Parser.literal(e.toString()).thenReturn(e)).collect(Parser.or());
  }
}
