package com.google.common.labs.regex;

import static java.util.Arrays.stream;

import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.AtLeast;
import com.google.common.labs.regex.RegexPattern.AtMost;
import com.google.common.labs.regex.RegexPattern.CharSetElement;
import com.google.common.labs.regex.RegexPattern.Group;
import com.google.common.labs.regex.RegexPattern.Limited;
import com.google.common.labs.regex.RegexPattern.Literal;
import com.google.common.labs.regex.RegexPattern.LiteralChar;
import com.google.common.labs.regex.RegexPattern.Lookaround;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantified;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.mu.util.CharPredicate;

/** Parsers for {@link RegexPattern}. */
final class RegexParsers {
  private static final CharPredicate NUM = CharPredicate.range('0', '9');
  private static final CharPredicate ALPHA =
      CharPredicate.range('a', 'z').orRange('A', 'Z').or('_');

  static Parser<RegexPattern> pattern() {
    var lazy = new Parser.Lazy<RegexPattern>();

    Parser<RegexPattern> atomicParser =
        Parser.anyOf(
            literalChars(),
            characterSet(),
            groupOrLookaround(lazy),
            predefinedCharClass(),
            anchor());
    Parser<RegexPattern> sequenceParser =
        atomicParser.postfix(quantifier().map(q -> p -> new Quantified(p, q)))
            .atLeastOnce(RegexPattern.inSequence());
    Parser<RegexPattern> alternationParser =
        sequenceParser.delimitedBy("|", RegexPattern.asAlternation());

    return lazy.delegateTo(alternationParser);
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = Parser.consecutive(NUM, "digit for quantifier").map(Integer::parseInt);
    Parser<Quantifier> question = Parser.literal("?").thenReturn(Quantifier.atMost(1));
    Parser<Quantifier> star = Parser.literal("*").thenReturn(Quantifier.repeated());
    Parser<Quantifier> plus = Parser.literal("+").thenReturn(Quantifier.atLeast(1));
    Parser<Quantifier> exact =
        number.immediatelyBetween("{", "}").map(n -> Quantifier.repeated(n, n));
    Parser<Quantifier> atLeast =
        number.followedBy(",").immediatelyBetween("{", "}").map(Quantifier::atLeast);
    Parser<Quantifier> atMost =
        Parser.literal(",").then(number).immediatelyBetween("{", "}").map(Quantifier::atMost);
    Parser<Quantifier> range =
        Parser.sequence(
                number,
                Parser.literal(",").then(number),
                (min, max) -> Quantifier.repeated(min, max))
            .immediatelyBetween("{", "}");
    Parser<Quantifier> greedy = Parser.anyOf(question, star, plus, exact, atLeast, atMost, range);
    return greedy.optionallyFollowedBy("?", RegexParsers::makeNonGreedy);
  }

  private static Parser<RegexPattern.CharacterSet> characterSet() {
    Parser<LiteralChar> escaped =
        Parser.literal("\\").then(Parser.single(c -> true, "escaped character").map(LiteralChar::new));
    Parser<LiteralChar> literal =
        Parser.single(CharPredicate.noneOf("-]\\"), "literal character").map(LiteralChar::new);
    Parser<LiteralChar> literalChar = Parser.anyOf(escaped, literal);
    Parser<CharSetElement> range =
        Parser.sequence(
            literalChar,
            Parser.literal("-").then(literalChar),
            (start, end) -> new RegexPattern.CharRange(start.value(), end.value()));
    Parser<CharSetElement> element = Parser.anyOf(range, literalChar);
    return Parser.anyOf(
        element.atLeastOnce().immediatelyBetween("[^", "]").map(RegexPattern::noneOf),
        element.atLeastOnce().immediatelyBetween("[", "]").map(RegexPattern::anyOf));
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

  private static Parser<PredefinedCharClass> predefinedCharClass() {
    return stream(PredefinedCharClass.values())
        .map(charClass -> Parser.literal(charClass.toString()).thenReturn(charClass))
        .collect(Parser.or());
  }

  private static Parser<Anchor> anchor() {
    return stream(Anchor.values())
        .map(anchor -> Parser.literal(anchor.toString()).thenReturn(anchor))
        .collect(Parser.or());
  }

  private static Parser<Literal> literalChars() {
    return Parser.consecutive(
        CharPredicate.noneOf(".[]{}()*+-?^$|\\"), "literal character").map(Literal::new);
  }

  private static Quantifier makeNonGreedy(Quantifier q) {
    return switch (q) {
      case AtLeast atLeast -> new AtLeast(atLeast.min(), false);
      case AtMost atMost -> new AtMost(atMost.max(), false);
      case Limited limited -> new Limited(limited.min(), limited.max(), false);
    };
  }
}
