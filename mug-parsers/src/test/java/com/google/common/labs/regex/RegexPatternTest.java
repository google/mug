package com.google.common.labs.regex;

import static com.google.common.labs.regex.RegexPattern.alternation;
import static com.google.common.labs.regex.RegexPattern.anyOf;
import static com.google.common.labs.regex.RegexPattern.noneOf;
import static com.google.common.labs.regex.RegexPattern.sequence;
import static com.google.common.labs.regex.RegexPattern.Quantifier.atLeast;
import static com.google.common.labs.regex.RegexPattern.Quantifier.atMost;
import static com.google.common.labs.regex.RegexPattern.Quantifier.repeated;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.labs.parser.Parser;
import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.Group;
import com.google.common.labs.regex.RegexPattern.Literal;
import com.google.common.labs.regex.RegexPattern.PosixCharClass;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantified;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.common.labs.regex.RegexPattern.UnicodeProperty;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public final class RegexPatternTest {

  @Test
  public void sequenceToString() {
    RegexPattern sequence = sequence(new Literal("a"), new Literal("b"));
    assertThat(sequence.toString()).isEqualTo("ab");
  }

  @Test
  public void alternationToString() {
    RegexPattern alternation = alternation(new Literal("a"), new Literal("b"));
    assertThat(alternation.toString()).isEqualTo("a|b");
  }

  @Test
  public void inSequence_collector_merged() {
    assertThat(Stream.of(new Literal("a"), new Literal("b")).collect(RegexPattern.inSequence()))
        .isEqualTo(new Literal("ab"));
    assertThat(
            Stream.of(new Literal("a"), sequence(new Literal("b"), new Literal("c")))
                .collect(RegexPattern.inSequence()))
        .isEqualTo(new Literal("abc"));
  }

  @Test
  public void inSequence_collector_notMerged() {
    assertThat(
            Stream.of(
                    new Literal("a"),
                    alternation(new Literal("b"), new Literal("c")),
                    new Literal("d"))
                .collect(RegexPattern.inSequence()))
        .isEqualTo(
            sequence(
                new Literal("a"),
                alternation(new Literal("b"), new Literal("c")),
                new Literal("d")));
  }

  @Test
  public void inSequence_collector_singleElement_success() {
    assertThat(Stream.of(new Literal("a")).collect(RegexPattern.inSequence()))
        .isEqualTo(new Literal("a"));
  }

  @Test
  public void inSequence_collector_emptyStream_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.<RegexPattern>of().collect(RegexPattern.inSequence()));
  }

  @Test
  public void asAlternation_collector_success() {
    assertThat(Stream.of(new Literal("a"), new Literal("b")).collect(RegexPattern.asAlternation()))
        .isEqualTo(alternation(new Literal("a"), new Literal("b")));
  }

  @Test
  public void asAlternation_collector_singleElement_success() {
    assertThat(Stream.of(new Literal("a")).collect(RegexPattern.asAlternation()))
        .isEqualTo(new Literal("a"));
  }

  @Test
  public void asAlternation_collector_emptyStream_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.<RegexPattern>of().collect(RegexPattern.asAlternation()));
  }

  @Test
  public void quantifiedToString() {
    Quantified quantified = new Quantified(new Literal("a"), repeated());
    assertThat(quantified.toString()).isEqualTo("a*");
  }

  @Test
  public void quantifiedSequenceToString() {
    Quantified quantified =
        new Quantified(sequence(new Literal("a"), new Literal("b")), repeated());
    assertThat(quantified.toString()).isEqualTo("(?:ab)*");
  }

  @Test
  public void atLeastToString() {
    assertThat(atLeast(0).toString()).isEqualTo("*");
    assertThat(atLeast(1).toString()).isEqualTo("+");
    assertThat(atLeast(3).toString()).isEqualTo("{3,}");
  }

  @Test
  public void atMostToString() {
    assertThat(atMost(1).toString()).isEqualTo("?");
    assertThat(atMost(5).toString()).isEqualTo("{0,5}");
    assertThat(atMost(Integer.MAX_VALUE).toString()).isEqualTo("{0," + Integer.MAX_VALUE + "}");
  }

  @Test
  public void limitedToString() {
    assertThat(repeated(3, 5).toString()).isEqualTo("{3,5}");
    assertThat(repeated(3, 3).toString()).isEqualTo("{3}");
  }

  @Test
  public void possessiveToString() {
    assertThat(atMost(1).possessive().toString()).isEqualTo("?+");
    assertThat(repeated().possessive().toString()).isEqualTo("*+");
    assertThat(atLeast(1).possessive().toString()).isEqualTo("++");
    assertThat(repeated(2, 2).possessive().toString()).isEqualTo("{2}+");
    assertThat(atLeast(2).possessive().toString()).isEqualTo("{2,}+");
    assertThat(repeated(2, 5).possessive().toString()).isEqualTo("{2,5}+");
  }

  @Test
  public void repeatedDelegation() {
    assertThat(repeated(0, 5)).isEqualTo(atMost(5));
    assertThat(repeated(0, Integer.MAX_VALUE)).isEqualTo(atMost(Integer.MAX_VALUE));
    assertThat(repeated(3, Integer.MAX_VALUE)).isEqualTo(atLeast(3));
  }

  @Test
  public void groupToString_capturing() {
    Group.Capturing capturing = new Group.Capturing(new Literal("a"));
    assertThat(capturing.toString()).isEqualTo("(a)");
  }

  @Test
  public void groupToString_nonCapturing() {
    Group.NonCapturing nonCapturing = new Group.NonCapturing(new Literal("a"));
    assertThat(nonCapturing.toString()).isEqualTo("(?:a)");
  }

  @Test
  public void groupToString_named() {
    Group.Named named = new Group.Named("foo", new Literal("a"));
    assertThat(named.toString()).isEqualTo("(?<foo>a)");
  }

  @Test
  public void literalToString() {
    assertThat(new Literal("a.b").toString()).isEqualTo("a\\.b");
  }

  @Test
  public void literalToString_withSpecialCharacters() {
    assertThat(new Literal("\\").toString()).isEqualTo("\\\\");
    assertThat(new Literal("$").toString()).isEqualTo("\\$");
    assertThat(new Literal("^").toString()).isEqualTo("\\^");
    assertThat(new Literal(".").toString()).isEqualTo("\\.");
    assertThat(new Literal("|").toString()).isEqualTo("\\|");
    assertThat(new Literal("?").toString()).isEqualTo("\\?");
    assertThat(new Literal("*").toString()).isEqualTo("\\*");
    assertThat(new Literal("+").toString()).isEqualTo("\\+");
    assertThat(new Literal("(").toString()).isEqualTo("\\(");
    assertThat(new Literal(")").toString()).isEqualTo("\\)");
    assertThat(new Literal("[").toString()).isEqualTo("\\[");
    assertThat(new Literal("]").toString()).isEqualTo("\\]");
    assertThat(new Literal("{").toString()).isEqualTo("\\{");
    assertThat(new Literal("}").toString()).isEqualTo("\\}");
  }

  @Test
  public void predefinedCharClassToString() {
    assertThat(PredefinedCharClass.DIGIT.toString()).isEqualTo("\\d");
  }

  @Test
  public void characterSetToString() {
    assertThat(RegexPattern.parse("[ab0-9]").toString()).isEqualTo("[ab0-9]");
  }

  @Test
  public void negatedCharacterSetToString() {
    assertThat(RegexPattern.parse("[^ab0-9]").toString()).isEqualTo("[^ab0-9]");
  }

  @Test
  public void complexRegexToString() {
    assertThat(RegexPattern.parse("^(foo|bar.+)$").toString()).isEqualTo("^(foo|bar.+)$");
  }

  @Test
  public void factoryMethods_emptyList_throwsException() {
    assertThrows(IllegalArgumentException.class, RegexPattern::sequence);
    assertThrows(IllegalArgumentException.class, RegexPattern::alternation);
    assertThrows(IllegalArgumentException.class, RegexPattern::anyOf);
    assertThrows(IllegalArgumentException.class, RegexPattern::noneOf);
  }

  @Test
  public void parse_literal() {
    assertThat(RegexPattern.parse("a")).isEqualTo(new Literal("a"));
    assertThat(RegexPattern.parse("foo")).isEqualTo(new Literal("foo"));
  }

  @Test
  public void parse_predefinedCharClass(@TestParameter PredefinedCharClass charClass) {
    assertThat(RegexPattern.parse(charClass.toString())).isEqualTo(charClass);
  }

  @Test
  public void parse_anchor(@TestParameter Anchor anchor) {
    assertThat(RegexPattern.parse(anchor.toString())).isEqualTo(anchor);
  }

  @Test
  public void parse_sequence() {
    assertThat(RegexPattern.parse("ab")).isEqualTo(new Literal("ab"));
    assertThat(RegexPattern.parse("a.")).isEqualTo(sequence(new Literal("a"), PredefinedCharClass.ANY_CHAR));
  }

  @Test
  public void parse_alternation() {
    assertThat(RegexPattern.parse("a|b"))
        .isEqualTo(alternation(new Literal("a"), new Literal("b")));
    assertThat(RegexPattern.parse("a|b|c"))
        .isEqualTo(alternation(new Literal("a"), new Literal("b"), new Literal("c")));
  }

  @Test
  public void parse_quantifier_greedy() {
    assertThat(RegexPattern.parse("a?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atMost(1)));
    assertThat(RegexPattern.parse("a*"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated()));
    assertThat(RegexPattern.parse("a+"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(1)));
    assertThat(RegexPattern.parse("a{2}"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 2)));
    assertThat(RegexPattern.parse("a{2,}"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(2)));
    assertThat(RegexPattern.parse("a{2,5}"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 5)));
  }

  @Test
  public void parse_quantifier_reluctant() {
    assertThat(RegexPattern.parse("a??"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atMost(1).reluctant()));
    assertThat(RegexPattern.parse("a*?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(0).reluctant()));
    assertThat(RegexPattern.parse("a+?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(1).reluctant()));
    assertThat(RegexPattern.parse("a{2}?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 2).reluctant()));
    assertThat(RegexPattern.parse("a{2,}?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(2).reluctant()));
    assertThat(RegexPattern.parse("a{2,5}?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 5).reluctant()));
  }

  @Test
  public void parse_group() {
    assertThat(RegexPattern.parse("(a)")).isEqualTo(new Group.Capturing(new Literal("a")));
    assertThat(RegexPattern.parse("(?:a)")).isEqualTo(new Group.NonCapturing(new Literal("a")));
    assertThat(RegexPattern.parse("(?<name>a)"))
        .isEqualTo(new Group.Named("name", new Literal("a")));
    assertThat(RegexPattern.parse("(?P<name>a)"))
        .isEqualTo(new Group.Named("name", new Literal("a")));
  }

  @Test
  public void parse_group_nested() {
    assertThat(RegexPattern.parse("((a))"))
        .isEqualTo(new Group.Capturing(new Group.Capturing(new Literal("a"))));
    assertThat(RegexPattern.parse("(a(b))"))
        .isEqualTo(
            new Group.Capturing(sequence(new Literal("a"), new Group.Capturing(new Literal("b")))));
    assertThat(RegexPattern.parse("(?<n1>(?<n2>a))"))
        .isEqualTo(new Group.Named("n1", new Group.Named("n2", new Literal("a"))));
    assertThat(RegexPattern.parse("(?:(a))"))
        .isEqualTo(new Group.NonCapturing(new Group.Capturing(new Literal("a"))));
  }

  @Test
  public void parse_characterSet() {
    assertThat(RegexPattern.parse("[a]")).isEqualTo(anyOf(new RegexPattern.LiteralChar('a')));
    assertThat(RegexPattern.parse("[ab]"))
        .isEqualTo(anyOf(new RegexPattern.LiteralChar('a'), new RegexPattern.LiteralChar('b')));
    assertThat(RegexPattern.parse("[a-z]")).isEqualTo(anyOf(new RegexPattern.CharRange('a', 'z')));
    assertThat(RegexPattern.parse("[^a-z]"))
        .isEqualTo(noneOf(new RegexPattern.CharRange('a', 'z')));
    assertThat(RegexPattern.parse("[^a-z0-9]"))
        .isEqualTo(
            noneOf(new RegexPattern.CharRange('a', 'z'), new RegexPattern.CharRange('0', '9')));
    assertThat(RegexPattern.parse("[^a]"))
        .isEqualTo(RegexPattern.noneOf(new RegexPattern.LiteralChar('a')));
    assertThat(RegexPattern.parse("[^a-z]"))
        .isEqualTo(RegexPattern.noneOf(new RegexPattern.CharRange('a', 'z')));
  }

  @Test
  public void parse_characterSet_withHyphen() {
    assertThat(RegexPattern.parse("[-a]"))
        .isEqualTo(
            anyOf(new RegexPattern.LiteralChar('-'), new RegexPattern.LiteralChar('a')));
    assertThat(RegexPattern.parse("[a-]"))
        .isEqualTo(
            anyOf(new RegexPattern.LiteralChar('a'), new RegexPattern.LiteralChar('-')));
    assertThat(RegexPattern.parse("[a-b-c]"))
        .isEqualTo(
            anyOf(
                new RegexPattern.CharRange('a', 'b'),
                new RegexPattern.LiteralChar('-'),
                new RegexPattern.LiteralChar('c')));
  }

  @Test
  public void parse_literalHyphen() {
    assertThat(RegexPattern.parse("-+help(short)?(=true)?"))
        .isEqualTo(
            sequence(
                new Quantified(new Literal("-"), atLeast(1)),
                new Literal("help"),
                new Quantified(new Group.Capturing(new Literal("short")), atMost(1)),
                new Quantified(new Group.Capturing(new Literal("=true")), atMost(1))));
  }

  @Test
  public void parse_posixCharClassInSet() {
    assertThat(RegexPattern.parse("[\\p{Lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER));
    assertThat(RegexPattern.parse("[\\p{lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER));
    assertThat(RegexPattern.parse("[\\p{ASCII}]")).isEqualTo(anyOf(PosixCharClass.ASCII));
    assertThat(RegexPattern.parse("[^\\p{Lower}]")).isEqualTo(noneOf(PosixCharClass.LOWER));
  }

  @Test
  public void parse_negatedPosixCharClassInSet() {
    assertThat(RegexPattern.parse("[\\P{Lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER.negated()));
    assertThat(RegexPattern.parse("[\\P{lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER.negated()));
    assertThat(RegexPattern.parse("[\\P{ASCII}]")).isEqualTo(anyOf(PosixCharClass.ASCII.negated()));
    assertThat(RegexPattern.parse("[^\\P{Lower}]"))
        .isEqualTo(noneOf(PosixCharClass.LOWER.negated()));
  }

  @Test
  public void parse_unicodePropertyInSet() {
    assertThat(RegexPattern.parse("[\\p{Nd}]")).isEqualTo(anyOf(new UnicodeProperty("Nd")));
    assertThat(RegexPattern.parse("[\\p{IsGreek}]"))
        .isEqualTo(anyOf(new UnicodeProperty("IsGreek")));
    assertThat(RegexPattern.parse("[^\\p{Nd}]")).isEqualTo(noneOf(new UnicodeProperty("Nd")));
  }

  @Test
  public void parse_negatedUnicodePropertyInSet() {
    assertThat(RegexPattern.parse("[\\P{Nd}]"))
        .isEqualTo(anyOf(new UnicodeProperty("Nd").negated()));
    assertThat(RegexPattern.parse("[\\P{IsGreek}]"))
        .isEqualTo(anyOf(new UnicodeProperty("IsGreek").negated()));
    assertThat(RegexPattern.parse("[^\\P{Nd}]"))
        .isEqualTo(noneOf(new UnicodeProperty("Nd").negated()));
  }

  @Test
  public void parse_characterSet_mixedClasses() {
    assertThat(RegexPattern.parse("[a-c\\p{Lower}\\p{Nd}\\w\\S]"))
        .isEqualTo(
            anyOf(
                new RegexPattern.CharRange('a', 'c'),
                PosixCharClass.LOWER,
                new UnicodeProperty("Nd"),
                PredefinedCharClass.WORD,
                PredefinedCharClass.NON_WHITESPACE));
    assertThat(RegexPattern.parse("[^a-c\\p{Lower}\\p{Nd}\\w\\S]"))
        .isEqualTo(
            noneOf(
                new RegexPattern.CharRange('a', 'c'),
                PosixCharClass.LOWER,
                new UnicodeProperty("Nd"),
                PredefinedCharClass.WORD,
                PredefinedCharClass.NON_WHITESPACE));
  }

  @Test
  public void parse_posixCharClass() {
    assertThat(RegexPattern.parse("\\p{Lower}")).isEqualTo(PosixCharClass.LOWER);
    assertThat(RegexPattern.parse("\\p{lower}")).isEqualTo(PosixCharClass.LOWER);
    assertThat(RegexPattern.parse("\\p{ASCII}")).isEqualTo(PosixCharClass.ASCII);
  }

  @Test
  public void parse_negatedPosixCharClass() {
    assertThat(RegexPattern.parse("\\P{Lower}")).isEqualTo(PosixCharClass.LOWER.negated());
    assertThat(RegexPattern.parse("\\P{lower}")).isEqualTo(PosixCharClass.LOWER.negated());
    assertThat(RegexPattern.parse("\\P{ASCII}")).isEqualTo(PosixCharClass.ASCII.negated());
  }

  @Test
  public void parse_unicodeProperty() {
    assertThat(RegexPattern.parse("\\p{Nd}")).isEqualTo(new UnicodeProperty("Nd"));
    assertThat(RegexPattern.parse("\\p{IsGreek}")).isEqualTo(new UnicodeProperty("IsGreek"));
  }

  @Test
  public void parse_negatedUnicodeProperty() {
    assertThat(RegexPattern.parse("\\P{Nd}")).isEqualTo(new UnicodeProperty("Nd").negated());
    assertThat(RegexPattern.parse("\\P{IsGreek}"))
        .isEqualTo(new UnicodeProperty("IsGreek").negated());
  }

  @Test
  public void lookaroundToString() {
    assertThat(new Literal("a").followedBy(new Literal("b")).toString()).isEqualTo("a(?=b)");
    assertThat(new Literal("a").notFollowedBy(new Literal("b")).toString()).isEqualTo("a(?!b)");
    assertThat(new Literal("a").precededBy(new Literal("b")).toString()).isEqualTo("(?<=b)a");
    assertThat(new Literal("a").notPrecededBy(new Literal("b")).toString()).isEqualTo("(?<!b)a");
  }

  @Test
  public void parse_lookaround() {
    assertThat(RegexPattern.parse("a(?=b)"))
        .isEqualTo(
            sequence(new Literal("a"), new RegexPattern.Lookaround.Lookahead(new Literal("b"))));
    assertThat(RegexPattern.parse("a(?!b)"))
        .isEqualTo(
            sequence(
                new Literal("a"), new RegexPattern.Lookaround.NegativeLookahead(new Literal("b"))));
    assertThat(RegexPattern.parse("(?<=a)b"))
        .isEqualTo(
            sequence(new RegexPattern.Lookaround.Lookbehind(new Literal("a")), new Literal("b")));
    assertThat(RegexPattern.parse("(?<!a)b"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.NegativeLookbehind(new Literal("a")),
                new Literal("b")));
  }

  @Test
  public void parse_complex() {
    assertThat(RegexPattern.parse("^(a|b)+[c-e]?$"))
        .isEqualTo(
            sequence(
                Anchor.BEGINNING,
                new Quantified(
                    new Group.Capturing(alternation(new Literal("a"), new Literal("b"))),
                    RegexPattern.Quantifier.atLeast(1)),
                new Quantified(
                    anyOf(new RegexPattern.CharRange('c', 'e')), RegexPattern.Quantifier.atMost(1)),
                Anchor.END));
  }

  @Test
  public void parse_complex_with_groups_lookarounds_and_quantifiers() {
    assertThat(RegexPattern.parse("(?:a|b)+(?!c)"))
        .isEqualTo(
            sequence(
                new Quantified(
                    new Group.NonCapturing(alternation(new Literal("a"), new Literal("b"))),
                    RegexPattern.Quantifier.atLeast(1)),
                new RegexPattern.Lookaround.NegativeLookahead(new Literal("c"))));

    assertThat(RegexPattern.parse("(?<=start)word(?=end)"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.Lookbehind(new Literal("start")),
                new Literal("word"),
                new RegexPattern.Lookaround.Lookahead(new Literal("end"))));

    assertThat(RegexPattern.parse("(?<!USD)\\d+"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.NegativeLookbehind(new Literal("USD")),
                new Quantified(PredefinedCharClass.DIGIT, RegexPattern.Quantifier.atLeast(1))));

    assertThat(RegexPattern.parse("a(?=(b|c))"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new RegexPattern.Lookaround.Lookahead(
                    new Group.Capturing(alternation(new Literal("b"), new Literal("c"))))));

    assertThat(RegexPattern.parse("(?<=(?:a|b))c"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.Lookbehind(
                    new Group.NonCapturing(alternation(new Literal("a"), new Literal("b")))),
                new Literal("c")));
  }

  @Test
  public void parse_empty() {
    assertThat(RegexPattern.parse("")).isEqualTo(new Literal(""));
  }

  @Test
  public void parse_group_missingRightParen() {
    Parser.ParseException e =
        assertThrows(Parser.ParseException.class, () -> RegexPattern.parse("(?:a|b"));
    assertThat(e).hasMessageThat().contains("at 6: expecting `)`");
  }

  @Test
  public void parse_lookbehind_missingSubject() {
    Parser.ParseException e =
        assertThrows(Parser.ParseException.class, () -> RegexPattern.parse("(?<=)"));
    assertThat(e).hasMessageThat().contains("at 4");
  }

  @Test
  public void parse_failure() {
    assertThrows(Parser.ParseException.class, () -> RegexPattern.parse("("));
    assertThrows(Parser.ParseException.class, () -> RegexPattern.parse("[a-"));
    assertThrows(IllegalArgumentException.class, () -> RegexPattern.parse("a{1,0}"));
    assertThrows(Parser.ParseException.class, () -> RegexPattern.parse("\\"));
  }
}
