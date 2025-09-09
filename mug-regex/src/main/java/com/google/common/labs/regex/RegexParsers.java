package com.google.common.labs.regex;

import static java.util.Arrays.stream;

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
  private static final CharPredicate NUM = CharPredicate.range('0', '9');
  private static final CharPredicate ALPHA =
      CharPredicate.range('a', 'z').orRange('A', 'Z').or('_');

  static Parser<RegexPattern> pattern() {
    var lazy = new Parser.Lazy<RegexPattern>();
    Parser<RegexPattern> atomic =
        Parser.anyOf(
            literalChars(),
            characterSet(),
            groupOrLookaround(lazy),
            anyOf(PredefinedCharClass.values()),
            anyOf(Anchor.values()));
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
        .optionallyFollowedBy("?", Quantifier::reluctant);
  }

  private static Parser<RegexPattern.CharacterSet> characterSet() {
    Parser<Character> escapedChar =
        Parser.literal("\\").then(Parser.single(c -> true, "escaped character"));
    Parser<Character> literalChar =
        Parser.anyOf(
            escapedChar, Parser.single(CharPredicate.noneOf("-]\\"), "literal character"));
    Parser<Character> literalCharOrDash =
        Parser.anyOf(
            escapedChar, Parser.single(CharPredicate.noneOf("]\\"), "literal character or dash"));
    Parser<CharRange> range =
        Parser.sequence(
            literalChar, Parser.literal("-").then(literalChar), RegexPattern.CharRange::new);
    var charOrRange = Parser.anyOf(range, literalCharOrDash.map(LiteralChar::new));
    return Parser.anyOf(
        charOrRange.atLeastOnce().immediatelyBetween("[^", "]").map(RegexPattern::noneOf),
        charOrRange.atLeastOnce().immediatelyBetween("[", "]").map(RegexPattern::anyOf));
  }

  private static Parser<RegexPattern> groupOrLookaround(Parser<RegexPattern> content) {
    Parser<Group.Named> named =
        Parser.consecutive(ALPHA.or(NUM), "alphanumeric for group name")
            .immediatelyBetween("?<", ">")
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

  private static Parser<Literal> literalChars() {
    return Parser.consecutive(
        CharPredicate.noneOf(".[]{}()*+-?^$|\\"), "literal character").map(Literal::new);
  }
}
