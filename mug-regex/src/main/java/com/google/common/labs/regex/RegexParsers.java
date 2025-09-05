package com.google.common.labs.regex;

import static com.google.common.labs.regex.RegexPattern.toSequence;

import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.AnyChar;
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
    Parser<RegexPattern> groupOrLookaround = groupOrLookaround(lazy);

    Parser<RegexPattern> atomicParser =
        Parser.anyOf(
            literalChars(),
            charClass(),
            groupOrLookaround,
            Parser.literal(".").thenReturn(new AnyChar()),
            predefinedCharClass(),
            anchor());

    @SuppressWarnings("nullness") // error doesn't make sense
    Parser<RegexPattern> quantifiedParser =
        atomicParser.postfix(quantifier().map(q -> p -> new Quantified(p, q)));

    Parser<RegexPattern> sequenceParser =
        quantifiedParser
            .atLeastOnce()
            .map(
                elements ->
                    elements.size() == 1
                        ? elements.get(0)
                        : elements.stream().collect(toSequence()));

    Parser<RegexPattern> alternationParser =
        sequenceParser
            .delimitedBy("|")
            .map(
                alternatives ->
                    alternatives.size() == 1
                        ? alternatives.get(0)
                        : alternatives.stream().collect(RegexPattern.toAlternation()));

    return lazy.delegateTo(alternationParser);
  }

  private static Parser<Quantifier> quantifier() {
    Parser<Integer> number = Parser.consecutive(NUM).map(Integer::parseInt);
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
    return greedy.followedBy("?").map(RegexParsers::makeNonGreedy).or(greedy);
  }

  private static Parser<RegexPattern.CharacterClass> charClass() {
    Parser<LiteralChar> escaped =
        Parser.literal("\\").then(Parser.single(c -> true).map(LiteralChar::new));
    Parser<LiteralChar> literal = Parser.single(CharPredicate.noneOf("-]\\")).map(LiteralChar::new);
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
        Parser.consecutive(ALPHA.or(NUM))
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
    return Parser.anyOf(
        Parser.literal("\\d").thenReturn(PredefinedCharClass.DIGIT),
        Parser.literal("\\D").thenReturn(PredefinedCharClass.NON_DIGIT),
        Parser.literal("\\s").thenReturn(PredefinedCharClass.WHITESPACE),
        Parser.literal("\\S").thenReturn(PredefinedCharClass.NON_WHITESPACE),
        Parser.literal("\\w").thenReturn(PredefinedCharClass.WORD),
        Parser.literal("\\W").thenReturn(PredefinedCharClass.NON_WORD));
  }

  private static Parser<Anchor> anchor() {
    return Parser.anyOf(
        Parser.literal("^").thenReturn(new Anchor.AtBeginning()),
        Parser.literal("$").thenReturn(new Anchor.AtEnd()));
  }

  private static Parser<Literal> literalChars() {
    return Parser.consecutive(CharPredicate.noneOf(".[]{}()*+-?^$|\\")).map(Literal::new);
  }

  private static Quantifier makeNonGreedy(Quantifier q) {
    return switch (q) {
      case AtLeast atLeast -> new AtLeast(atLeast.min(), false);
      case AtMost atMost -> new AtMost(atMost.max(), false);
      case Limited limited -> new Limited(limited.min(), limited.max(), false);
    };
  }
}
