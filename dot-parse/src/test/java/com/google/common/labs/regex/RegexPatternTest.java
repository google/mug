package com.google.common.labs.regex;

import static com.google.common.labs.regex.RegexPattern.Quantifier.atLeast;
import static com.google.common.labs.regex.RegexPattern.Quantifier.atMost;
import static com.google.common.labs.regex.RegexPattern.Quantifier.repeated;
import static com.google.common.labs.regex.RegexPattern.alternation;
import static com.google.common.labs.regex.RegexPattern.anyOf;
import static com.google.common.labs.regex.RegexPattern.intersection;
import static com.google.common.labs.regex.RegexPattern.noneOf;
import static com.google.common.labs.regex.RegexPattern.sequence;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parser;
import com.google.common.labs.regex.RegexPattern.Alternation;
import com.google.common.labs.regex.RegexPattern.Anchor;
import com.google.common.labs.regex.RegexPattern.AtLeast;
import com.google.common.labs.regex.RegexPattern.AtMost;
import com.google.common.labs.regex.RegexPattern.Backreference;
import com.google.common.labs.regex.RegexPattern.CharRange;
import com.google.common.labs.regex.RegexPattern.CharSetElement;
import com.google.common.labs.regex.RegexPattern.CharacterSet;
import com.google.common.labs.regex.RegexPattern.Group;
import com.google.common.labs.regex.RegexPattern.Limited;
import com.google.common.labs.regex.RegexPattern.Literal;
import com.google.common.labs.regex.RegexPattern.LiteralChar;
import com.google.common.labs.regex.RegexPattern.Lookaround;
import com.google.common.labs.regex.RegexPattern.Metadata;
import com.google.common.labs.regex.RegexPattern.ModifierFlag;
import com.google.common.labs.regex.RegexPattern.PosixCharClass;
import com.google.common.labs.regex.RegexPattern.PredefinedCharClass;
import com.google.common.labs.regex.RegexPattern.Quantified;
import com.google.common.labs.regex.RegexPattern.Quantifier;
import com.google.common.labs.regex.RegexPattern.Sequence;
import com.google.common.labs.regex.RegexPattern.UnicodeProperty;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class RegexPatternTest {

  @Test public void sequenceToString() {
    RegexPattern sequence = sequence(new Literal("a"), new Literal("b"));
    assertThat(sequence.toString()).isEqualTo("ab");
  }

  @Test public void alternationToString() {
    RegexPattern alternation = alternation(new Literal("a"), new Literal("b"));
    assertThat(alternation.toString()).isEqualTo("a|b");
  }

  @Test public void inSequence_collector_merged() {
    assertThat(Stream.of(new Literal("a"), new Literal("b")).collect(RegexPattern.inSequence()))
        .isEqualTo(new Literal("ab"));
    assertThat(
            Stream.of(new Literal("a"), sequence(new Literal("b"), new Literal("c")))
                .collect(RegexPattern.inSequence()))
        .isEqualTo(new Literal("abc"));
  }

  @Test public void inSequence_collector_notMerged() {
    assertThat(
            Stream.of(
                    new Literal("a"), alternation(new Literal("b"), new Literal("c")),
                    new Literal("d"))
                .collect(RegexPattern.inSequence()))
        .isEqualTo(
            sequence(
                new Literal("a"), alternation(new Literal("b"), new Literal("c")),
                new Literal("d")));
  }

  @Test public void inSequence_collector_singleElement_success() {
    assertThat(Stream.of(new Literal("a")).collect(RegexPattern.inSequence()))
        .isEqualTo(new Literal("a"));
  }

  @Test public void inSequence_collector_emptyStream_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.<RegexPattern>of().collect(RegexPattern.inSequence()));
  }

  @Test public void asAlternation_collector_success() {
    assertThat(Stream.of(new Literal("a"), new Literal("b")).collect(RegexPattern.asAlternation()))
        .isEqualTo(alternation(new Literal("a"), new Literal("b")));
  }

  @Test public void asAlternation_collector_singleElement_success() {
    assertThat(Stream.of(new Literal("a")).collect(RegexPattern.asAlternation()))
        .isEqualTo(new Literal("a"));
  }

  @Test public void asAlternation_collector_emptyStream_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Stream.<RegexPattern>of().collect(RegexPattern.asAlternation()));
  }

  @Test public void quantifiedToString() {
    Quantified quantified = new Quantified(new Literal("a"), repeated());
    assertThat(quantified.toString()).isEqualTo("a*");
  }

  @Test public void quantifiedSequenceToString() {
    Quantified quantified =
        new Quantified(sequence(new Literal("a"), new Literal("b")), repeated());
    assertThat(quantified.toString()).isEqualTo("(?:ab)*");
  }

  @Test public void atLeastToString() {
    assertThat(atLeast(0).toString()).isEqualTo("*");
    assertThat(atLeast(1).toString()).isEqualTo("+");
    assertThat(atLeast(3).toString()).isEqualTo("{3,}");
  }

  @Test public void atMostToString() {
    assertThat(atMost(1).toString()).isEqualTo("?");
    assertThat(atMost(5).toString()).isEqualTo("{0,5}");
    assertThat(atMost(Integer.MAX_VALUE).toString()).isEqualTo("{0," + Integer.MAX_VALUE + "}");
  }

  @Test public void limitedToString() {
    assertThat(repeated(3, 5).toString()).isEqualTo("{3,5}");
    assertThat(repeated(3, 3).toString()).isEqualTo("{3}");
  }

  @Test public void possessiveToString() {
    assertThat(atMost(1).possessive().toString()).isEqualTo("?+");
    assertThat(repeated().possessive().toString()).isEqualTo("*+");
    assertThat(atLeast(1).possessive().toString()).isEqualTo("++");
    assertThat(repeated(2, 2).possessive().toString()).isEqualTo("{2}+");
    assertThat(atLeast(2).possessive().toString()).isEqualTo("{2,}+");
    assertThat(repeated(2, 5).possessive().toString()).isEqualTo("{2,5}+");
  }

  @Test public void repeatedDelegation() {
    assertThat(repeated(0, 5)).isEqualTo(atMost(5));
    assertThat(repeated(0, Integer.MAX_VALUE)).isEqualTo(atMost(Integer.MAX_VALUE));
    assertThat(repeated(3, Integer.MAX_VALUE)).isEqualTo(atLeast(3));
  }

  @Test public void groupToString_capturing() {
    Group.Capturing capturing = new Group.Capturing(new Literal("a"));
    assertThat(capturing.toString()).isEqualTo("(a)");
  }

  @Test public void groupToString_nonCapturing() {
    Group.NonCapturing nonCapturing = new Group.NonCapturing(new Literal("a"));
    assertThat(nonCapturing.toString()).isEqualTo("(?:a)");
  }

  @Test public void groupToString_named() {
    Group.Named named = new Group.Named("foo", new Literal("a"));
    assertThat(named.toString()).isEqualTo("(?<foo>a)");
  }

  @Test public void literalToString() {
    assertThat(new Literal("a.b").toString()).isEqualTo("a\\.b");
  }

  @Test public void literalToString_withSpecialCharacters() {
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

  @Test public void predefinedCharClassToString() {
    assertThat(PredefinedCharClass.DIGIT.toString()).isEqualTo("\\d");
  }

  @Test public void characterSetToString() {
    assertThat(RegexPattern.of("[ab0-9]").toString()).isEqualTo("[ab0-9]");
  }

  @Test public void negatedCharacterSetToString() {
    assertThat(RegexPattern.of("[^ab0-9]").toString()).isEqualTo("[^ab0-9]");
  }

  @Test public void complexRegexToString() {
    assertThat(RegexPattern.of("^(foo|bar.+)$").toString()).isEqualTo("^(foo|bar.+)$");
  }

  @Test public void factoryMethods_emptyList_throwsException() {
    assertThrows(IllegalArgumentException.class, RegexPattern::sequence);
    assertThrows(IllegalArgumentException.class, RegexPattern::alternation);
    assertThrows(IllegalArgumentException.class, RegexPattern::anyOf);
    assertThrows(IllegalArgumentException.class, RegexPattern::noneOf);
  }

  @Test public void of_literal() {
    assertThat(RegexPattern.of("a")).isEqualTo(new Literal("a"));
    assertThat(RegexPattern.of("foo")).isEqualTo(new Literal("foo"));
  }

  @Test public void of_literal_closingCurlyBrace() {
    assertThat(RegexPattern.of("}")).isEqualTo(new Literal("}"));
  }

  @Test public void of_literal_closingSquareBracket() {
    assertThat(RegexPattern.of("]")).isEqualTo(new Literal("]"));
  }

  @Test public void of_literal_withClosingBracketsAndBraces() {
    assertThat(RegexPattern.of("foo}bar]")).isEqualTo(new Literal("foo}bar]"));
  }

  @Test public void of_literal_escapedOpeningBraceWithUnescapedClosingBrace() {
    assertThat(RegexPattern.of("\\{foo}")).isEqualTo(new Literal("{foo}"));
  }

  @Test public void of_escapedLiteral() {
    assertThat(RegexPattern.of("\\a")).isEqualTo(new Literal("a"));
    assertThat(RegexPattern.of("\\\\")).isEqualTo(new Literal("\\"));
    assertThat(RegexPattern.of("\\{\\}")).isEqualTo(new Literal("{}"));
  }

  @Test public void of_backreference() {
    assertThat(RegexPattern.of("(a)\\1"))
        .isEqualTo(sequence(new Group.Capturing(new Literal("a")), new Backreference.Numbered(1)));
    assertThat(RegexPattern.of("(?<foo>a)\\k<foo>"))
        .isEqualTo(
            sequence(new Group.Named("foo", new Literal("a")), new Backreference.Named("foo")));
  }

  @Test public void backreferenceToString() {
    assertThat(new Backreference.Numbered(1).toString()).isEqualTo("\\1");
    assertThat(new Backreference.Named("foo").toString()).isEqualTo("\\k<foo>");
  }

  @Test public void of_escapedLiteralMixedWithPredefinedCharClasses() {
    assertThat(RegexPattern.of("\\a\\d\\w"))
        .isEqualTo(sequence(new Literal("a"), PredefinedCharClass.DIGIT, PredefinedCharClass.WORD));
  }

  @Test public void of_predefinedCharClass(@TestParameter PredefinedCharClass predefinedCharClass) {
    assertThat(RegexPattern.of(predefinedCharClass.toString())).isEqualTo(predefinedCharClass);
  }

  @Test public void of_anchor(@TestParameter Anchor anchor) {
    assertThat(RegexPattern.of(anchor.toString())).isEqualTo(anchor);
  }

  @Test public void of_sequence() {
    assertThat(RegexPattern.of("ab")).isEqualTo(new Literal("ab"));
    assertThat(RegexPattern.of("a."))
        .isEqualTo(sequence(new Literal("a"), PredefinedCharClass.ANY_CHAR));
  }

  @Test public void of_alternation() {
    assertThat(RegexPattern.of("a|b")).isEqualTo(alternation(new Literal("a"), new Literal("b")));
    assertThat(RegexPattern.of("a|b|c"))
        .isEqualTo(alternation(new Literal("a"), new Literal("b"), new Literal("c")));
  }

  @Test public void of_quantifier_greedy() {
    assertThat(RegexPattern.of("a?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atMost(1)));
    assertThat(RegexPattern.of("a*"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated()));
    assertThat(RegexPattern.of("a+"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(1)));
    assertThat(RegexPattern.of("a{2}"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 2)));
    assertThat(RegexPattern.of("a{2,}"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(2)));
    assertThat(RegexPattern.of("a{2,5}"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 5)));
  }

  @Test public void of_quantifier_reluctant() {
    assertThat(RegexPattern.of("a??"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atMost(1).reluctant()));
    assertThat(RegexPattern.of("a*?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(0).reluctant()));
    assertThat(RegexPattern.of("a+?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(1).reluctant()));
    assertThat(RegexPattern.of("a{2}?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 2).reluctant()));
    assertThat(RegexPattern.of("a{2,}?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(2).reluctant()));
    assertThat(RegexPattern.of("a{2,5}?"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 5).reluctant()));
  }

  @Test public void of_quantifier_possessive() {
    assertThat(RegexPattern.of("a?+"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atMost(1).possessive()));
    assertThat(RegexPattern.of("a*+"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated().possessive()));
    assertThat(RegexPattern.of("a++"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(1).possessive()));
    assertThat(RegexPattern.of("a{2}+"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 2).possessive()));
    assertThat(RegexPattern.of("a{2,}+"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.atLeast(2).possessive()));
    assertThat(RegexPattern.of("a{2,5}+"))
        .isEqualTo(new Quantified(new Literal("a"), Quantifier.repeated(2, 5).possessive()));
  }

  @Test public void of_quantifiedMultiChar() {
    assertThat(RegexPattern.of("(abc)*"))
        .isEqualTo(new Quantified(new Group.Capturing(new Literal("abc")), Quantifier.repeated()));
    assertThat(RegexPattern.of("(abc)+"))
        .isEqualTo(new Quantified(new Group.Capturing(new Literal("abc")), Quantifier.atLeast(1)));
    assertThat(RegexPattern.of("(abc){2}"))
        .isEqualTo(
            new Quantified(new Group.Capturing(new Literal("abc")), Quantifier.repeated(2, 2)));
    assertThat(RegexPattern.of("(abc){2,}"))
        .isEqualTo(new Quantified(new Group.Capturing(new Literal("abc")), Quantifier.atLeast(2)));
    assertThat(RegexPattern.of("(abc){,4}"))
        .isEqualTo(new Quantified(new Group.Capturing(new Literal("abc")), Quantifier.atMost(4)));
    assertThat(RegexPattern.of("(abc){2,4}"))
        .isEqualTo(
            new Quantified(new Group.Capturing(new Literal("abc")), Quantifier.repeated(2, 4)));
    assertThat(RegexPattern.of("abc*"))
        .isEqualTo(new Quantified(new Literal("abc"), Quantifier.repeated()));
    assertThat(RegexPattern.of("abc{2}"))
        .isEqualTo(new Quantified(new Literal("abc"), Quantifier.repeated(2, 2)));
    assertThat(RegexPattern.of("abc{2,}"))
        .isEqualTo(new Quantified(new Literal("abc"), Quantifier.atLeast(2)));
    assertThat(RegexPattern.of("abc{,4}"))
        .isEqualTo(new Quantified(new Literal("abc"), Quantifier.atMost(4)));
    assertThat(RegexPattern.of("abc{2,4}"))
        .isEqualTo(new Quantified(new Literal("abc"), Quantifier.repeated(2, 4)));
  }

  @Test public void of_group() {
    assertThat(RegexPattern.of("(a)")).isEqualTo(new Group.Capturing(new Literal("a")));
    assertThat(RegexPattern.of("(?:a)")).isEqualTo(new Group.NonCapturing(new Literal("a")));
    assertThat(RegexPattern.of("(?<name>a)")).isEqualTo(new Group.Named("name", new Literal("a")));
    assertThat(RegexPattern.of("(?P<name>a)")).isEqualTo(new Group.Named("name", new Literal("a")));
  }

  @Test public void of_group_empty() {
    assertThat(RegexPattern.of("()")).isEqualTo(new Group.Capturing(new Literal("")));
  }

  @Test public void of_group_nonCapturing_empty() {
    assertThat(RegexPattern.of("(?:)")).isEqualTo(new Group.NonCapturing(new Literal("")));
  }

  @Test public void of_group_named_empty() {
    assertThat(RegexPattern.of("(?<name>)")).isEqualTo(new Group.Named("name", new Literal("")));
  }

  @Test public void of_group_atomic_empty() {
    assertThat(RegexPattern.of("(?>)")).isEqualTo(new Group.Atomic(new Literal("")));
  }

  @Test public void of_lookahead_empty() {
    assertThat(RegexPattern.of("(?=)")).isEqualTo(new Lookaround.Lookahead(new Literal("")));
  }

  @Test public void of_lookbehind_empty() {
    assertThat(RegexPattern.of("(?<=)")).isEqualTo(new Lookaround.Lookbehind(new Literal("")));
  }

  @Test public void of_negativeLookahead_empty() {
    assertThat(RegexPattern.of("(?!)"))
        .isEqualTo(new Lookaround.NegativeLookahead(new Literal("")));
  }

  @Test public void of_negativeLookbehind_empty() {
    assertThat(RegexPattern.of("(?<!)"))
        .isEqualTo(new Lookaround.NegativeLookbehind(new Literal("")));
  }

  @Test public void of_group_atomic() {
    assertThat(RegexPattern.of("(?>a+)"))
        .isEqualTo(new Group.Atomic(new Quantified(new Literal("a"), atLeast(1))));
  }

  @Test public void of_group_atomic_toString() {
    assertThat(RegexPattern.of("(?>a+)").toString()).isEqualTo("(?>a+)");
  }

  @Test public void of_quotedLiteral() {
    assertThat(RegexPattern.of("\\Q[a-z]*\\E")).isEqualTo(new Literal("[a-z]*"));
  }

  @Test public void of_quotedLiteral_withoutClosingE() {
    assertThat(RegexPattern.of("\\Q[a-z]*")).isEqualTo(new Literal("[a-z]*"));
  }

  @Test public void of_quotedLiteral_withEscapes() {
    assertThat(RegexPattern.of("\\Qa\\db\\E")).isEqualTo(new Literal("a\\db"));
  }

  @Test public void of_predefinedCharClass_linebreak() {
    assertThat(RegexPattern.of("\\R")).isEqualTo(PredefinedCharClass.LINEBREAK);
  }

  @Test public void of_group_nested() {
    assertThat(RegexPattern.of("((a))"))
        .isEqualTo(new Group.Capturing(new Group.Capturing(new Literal("a"))));
    assertThat(RegexPattern.of("(a(b))"))
        .isEqualTo(
            new Group.Capturing(sequence(new Literal("a"), new Group.Capturing(new Literal("b")))));
    assertThat(RegexPattern.of("(?<n1>(?<n2>a))"))
        .isEqualTo(new Group.Named("n1", new Group.Named("n2", new Literal("a"))));
    assertThat(RegexPattern.of("(?:(a))"))
        .isEqualTo(new Group.NonCapturing(new Group.Capturing(new Literal("a"))));
  }

  @Test public void of_characterSet() {
    assertThat(RegexPattern.of("[a]")).isEqualTo(anyOf(new RegexPattern.LiteralChar('a')));
    assertThat(RegexPattern.of("[ab]"))
        .isEqualTo(anyOf(new RegexPattern.LiteralChar('a'), new RegexPattern.LiteralChar('b')));
    assertThat(RegexPattern.of("[a-z]")).isEqualTo(anyOf(new RegexPattern.CharRange('a', 'z')));
    assertThat(RegexPattern.of("[^a-z]")).isEqualTo(noneOf(new RegexPattern.CharRange('a', 'z')));
    assertThat(RegexPattern.of("[^a-z0-9]"))
        .isEqualTo(
            noneOf(new RegexPattern.CharRange('a', 'z'), new RegexPattern.CharRange('0', '9')));
    assertThat(RegexPattern.of("[^a]"))
        .isEqualTo(RegexPattern.noneOf(new RegexPattern.LiteralChar('a')));
    assertThat(RegexPattern.of("[^a-z]"))
        .isEqualTo(RegexPattern.noneOf(new RegexPattern.CharRange('a', 'z')));
  }

  @Test public void of_characterSet_withHyphen() {
    assertThat(RegexPattern.of("[-a]"))
        .isEqualTo(anyOf(new RegexPattern.LiteralChar('-'), new RegexPattern.LiteralChar('a')));
    assertThat(RegexPattern.of("[a-]"))
        .isEqualTo(anyOf(new RegexPattern.LiteralChar('a'), new RegexPattern.LiteralChar('-')));
    assertThat(RegexPattern.of("[a-b-c]"))
        .isEqualTo(
            anyOf(
                new RegexPattern.CharRange('a', 'b'), new RegexPattern.LiteralChar('-'),
                new RegexPattern.LiteralChar('c')));
  }

  @Test public void of_characterSet_escapedSpecialChars() {
    assertThat(RegexPattern.of("[\\[\\]\\-\\^\\&]"))
        .isEqualTo(
            anyOf(
                new LiteralChar('['),
                new LiteralChar(']'),
                new LiteralChar('-'),
                new LiteralChar('^'),
                new LiteralChar('&')));
  }

  @Test public void of_characterSet_nested() {
    assertThat(RegexPattern.of("[a-z[0-9]]"))
        .isEqualTo(anyOf(new CharRange('a', 'z'), anyOf(new CharRange('0', '9'))));
  }

  @Test public void of_characterSet_nested_toString() {
    assertThat(RegexPattern.of("[a-z[0-9]]").toString()).isEqualTo("[a-z[0-9]]");
  }

  @Test public void of_characterSet_intersection() {
    assertThat(RegexPattern.of("[a-z&&[def]]"))
        .isEqualTo(
            intersection(
                anyOf(new CharRange('a', 'z')),
                anyOf(new LiteralChar('d'), new LiteralChar('e'), new LiteralChar('f'))));
  }

  @Test public void of_characterSet_intersection_unbracketedRhs() {
    assertThat(RegexPattern.of("[a-z&&d-f]"))
        .isEqualTo(intersection(anyOf(new CharRange('a', 'z')), anyOf(new CharRange('d', 'f'))));
  }

  @Test public void of_characterSet_intersection_negatedRhs() {
    assertThat(RegexPattern.of("[a-z&&[^bc]]"))
        .isEqualTo(
            intersection(
                anyOf(new CharRange('a', 'z')),
                noneOf(new LiteralChar('b'), new LiteralChar('c'))));
  }

  @Test public void of_characterSet_intersection_negatedLhs() {
    assertThat(RegexPattern.of("[^a-z&&d-f]"))
        .isEqualTo(intersection(noneOf(new CharRange('a', 'z')), anyOf(new CharRange('d', 'f'))));
  }

  @Test public void of_characterSet_intersection_toString() {
    assertThat(RegexPattern.of("[a-z&&[^bc]]").toString()).isEqualTo("[a-z&&[^bc]]");
  }

  @Test public void of_characterSet_intersection_negatedLhs_toString() {
    assertThat(RegexPattern.of("[^a-z&&d-f]").toString()).isEqualTo("[^a-z&&d-f]");
  }

  @Test public void of_literalHyphen() {
    assertThat(RegexPattern.of("-+help(short)?(=true)?"))
        .isEqualTo(
            sequence(
                new Quantified(new Literal("-"), atLeast(1)), new Literal("help"),
                new Quantified(new Group.Capturing(new Literal("short")), atMost(1)),
                new Quantified(new Group.Capturing(new Literal("=true")), atMost(1))));
  }

  @Test public void of_posixCharClassInSet() {
    assertThat(RegexPattern.of("[\\p{Lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER));
    assertThat(RegexPattern.of("[\\p{lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER));
    assertThat(RegexPattern.of("[\\p{ASCII}]")).isEqualTo(anyOf(PosixCharClass.ASCII));
    assertThat(RegexPattern.of("[^\\p{Lower}]")).isEqualTo(noneOf(PosixCharClass.LOWER));
  }

  @Test public void of_negatedPosixCharClassInSet() {
    assertThat(RegexPattern.of("[\\P{Lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER.negated()));
    assertThat(RegexPattern.of("[\\P{lower}]")).isEqualTo(anyOf(PosixCharClass.LOWER.negated()));
    assertThat(RegexPattern.of("[\\P{ASCII}]")).isEqualTo(anyOf(PosixCharClass.ASCII.negated()));
    assertThat(RegexPattern.of("[^\\P{Lower}]")).isEqualTo(noneOf(PosixCharClass.LOWER.negated()));
  }

  @Test public void of_unicodePropertyInSet() {
    assertThat(RegexPattern.of("[\\p{Nd}]")).isEqualTo(anyOf(new UnicodeProperty("Nd")));
    assertThat(RegexPattern.of("[\\p{IsGreek}]")).isEqualTo(anyOf(new UnicodeProperty("IsGreek")));
    assertThat(RegexPattern.of("[^\\p{Nd}]")).isEqualTo(noneOf(new UnicodeProperty("Nd")));
  }

  @Test public void of_negatedUnicodePropertyInSet() {
    assertThat(RegexPattern.of("[\\P{Nd}]")).isEqualTo(anyOf(new UnicodeProperty("Nd").negated()));
    assertThat(RegexPattern.of("[\\P{IsGreek}]"))
        .isEqualTo(anyOf(new UnicodeProperty("IsGreek").negated()));
    assertThat(RegexPattern.of("[^\\P{Nd}]"))
        .isEqualTo(noneOf(new UnicodeProperty("Nd").negated()));
  }

  @Test public void of_characterSet_mixedClasses() {
    assertThat(RegexPattern.of("[a-c\\p{Lower}\\p{Nd}\\w\\S]"))
        .isEqualTo(
            anyOf(
                new RegexPattern.CharRange('a', 'c'), PosixCharClass.LOWER,
                new UnicodeProperty("Nd"), PredefinedCharClass.WORD,
                PredefinedCharClass.NON_WHITESPACE));
    assertThat(RegexPattern.of("[^a-c\\p{Lower}\\p{Nd}\\w\\S]"))
        .isEqualTo(
            noneOf(
                new RegexPattern.CharRange('a', 'c'), PosixCharClass.LOWER,
                new UnicodeProperty("Nd"), PredefinedCharClass.WORD,
                PredefinedCharClass.NON_WHITESPACE));
  }

  @Test public void of_posixCharClass() {
    assertThat(RegexPattern.of("\\p{Lower}")).isEqualTo(PosixCharClass.LOWER);
    assertThat(RegexPattern.of("\\p{lower}")).isEqualTo(PosixCharClass.LOWER);
    assertThat(RegexPattern.of("\\p{ASCII}")).isEqualTo(PosixCharClass.ASCII);
  }

  @Test public void of_negatedPosixCharClass() {
    assertThat(RegexPattern.of("\\P{Lower}")).isEqualTo(PosixCharClass.LOWER.negated());
    assertThat(RegexPattern.of("\\P{lower}")).isEqualTo(PosixCharClass.LOWER.negated());
    assertThat(RegexPattern.of("\\P{ASCII}")).isEqualTo(PosixCharClass.ASCII.negated());
  }

  @Test public void of_unicodeProperty() {
    assertThat(RegexPattern.of("\\p{Nd}")).isEqualTo(new UnicodeProperty("Nd"));
    assertThat(RegexPattern.of("\\p{IsGreek}")).isEqualTo(new UnicodeProperty("IsGreek"));
  }

  @Test public void of_negatedUnicodeProperty() {
    assertThat(RegexPattern.of("\\P{Nd}")).isEqualTo(new UnicodeProperty("Nd").negated());
    assertThat(RegexPattern.of("\\P{IsGreek}")).isEqualTo(new UnicodeProperty("IsGreek").negated());
  }

  @Test public void lookaroundToString() {
    assertThat(new Literal("a").followedBy(new Literal("b")).toString()).isEqualTo("a(?=b)");
    assertThat(new Literal("a").notFollowedBy(new Literal("b")).toString()).isEqualTo("a(?!b)");
    assertThat(new Literal("a").precededBy(new Literal("b")).toString()).isEqualTo("(?<=b)a");
    assertThat(new Literal("a").notPrecededBy(new Literal("b")).toString()).isEqualTo("(?<!b)a");
  }

  @Test public void of_lookaround() {
    assertThat(RegexPattern.of("a(?=b)"))
        .isEqualTo(
            sequence(new Literal("a"), new RegexPattern.Lookaround.Lookahead(new Literal("b"))));
    assertThat(RegexPattern.of("a(?!b)"))
        .isEqualTo(
            sequence(
                new Literal("a"), new RegexPattern.Lookaround.NegativeLookahead(new Literal("b"))));
    assertThat(RegexPattern.of("(?<=a)b"))
        .isEqualTo(
            sequence(new RegexPattern.Lookaround.Lookbehind(new Literal("a")), new Literal("b")));
    assertThat(RegexPattern.of("(?<!a)b"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.NegativeLookbehind(new Literal("a")),
                new Literal("b")));
  }

  @Test public void of_complex() {
    assertThat(RegexPattern.of("^(a|b)+[c-e]?$"))
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

  @Test public void of_complex_with_groups_lookarounds_and_quantifiers() {
    assertThat(RegexPattern.of("(?:a|b)+(?!c)"))
        .isEqualTo(
            sequence(
                new Quantified(
                    new Group.NonCapturing(alternation(new Literal("a"), new Literal("b"))),
                    RegexPattern.Quantifier.atLeast(1)),
                new RegexPattern.Lookaround.NegativeLookahead(new Literal("c"))));

    assertThat(RegexPattern.of("(?<=start)word(?=end)"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.Lookbehind(new Literal("start")), new Literal("word"),
                new RegexPattern.Lookaround.Lookahead(new Literal("end"))));

    assertThat(RegexPattern.of("(?<!USD)\\d+"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.NegativeLookbehind(new Literal("USD")),
                new Quantified(PredefinedCharClass.DIGIT, RegexPattern.Quantifier.atLeast(1))));

    assertThat(RegexPattern.of("a(?=(b|c))"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new RegexPattern.Lookaround.Lookahead(
                    new Group.Capturing(alternation(new Literal("b"), new Literal("c"))))));

    assertThat(RegexPattern.of("(?<=(?:a|b))c"))
        .isEqualTo(
            sequence(
                new RegexPattern.Lookaround.Lookbehind(
                    new Group.NonCapturing(alternation(new Literal("a"), new Literal("b")))),
                new Literal("c")));
  }

  @Test public void of_empty() {
    assertThat(RegexPattern.of("")).isEqualTo(new Literal(""));
  }

  @Test public void of_group_missingRightParen() {
    Parser.ParseException e =
        assertThrows(Parser.ParseException.class, () -> RegexPattern.of("(?:a|b"));
    assertThat(e).hasMessageThat()
        .isEqualTo(
            """
            at 1:7: expecting <)>, encountered:
                (?:a|b
                      ^
            """);
  }

  @Test public void of_group_invalidModifier_empty_throws() {
    Parser.ParseException e =
        assertThrows(Parser.ParseException.class, () -> RegexPattern.of("(?)"));
    assertThat(e).hasMessageThat().contains("at 1:3:");
  }

  @Test public void of_failure() {
    assertThrows(Parser.ParseException.class, () -> RegexPattern.of("("));
    assertThrows(Parser.ParseException.class, () -> RegexPattern.of("[a-"));
    assertThrows(IllegalArgumentException.class, () -> RegexPattern.of("a{1,0}"));
    assertThrows(Parser.ParseException.class, () -> RegexPattern.of("\\"));
  }

  @Test public void of_freeSpacingMode_spacesIgnored() {
    assertThat(RegexPattern.of("(?x) a b ")).isEqualTo(new Literal("ab"));
  }

  @Test public void of_freeSpacingMode_newlinesIgnored() {
    assertThat(RegexPattern.of("(?x) a  \n b ")).isEqualTo(new Literal("ab"));
  }

  @Test public void of_freeSpacingMode_commentsIgnored() {
    assertThat(RegexPattern.of("(?x) a  # comment \n (b ) "))
        .isEqualTo(sequence(new Literal("a"), new Group.Capturing(new Literal("b"))));
    assertThat(RegexPattern.of("(?x)a#comment\nb")).isEqualTo(new Literal("ab"));
    assertThat(RegexPattern.of("(?x)a#comment b")).isEqualTo(new Literal("a"));
  }

  @Test public void of_freeSpacingMode_spaceInCharClassIsLiteral() {
    assertThat(RegexPattern.of("(?x) [ ] a"))
        .isEqualTo(sequence(anyOf(new RegexPattern.LiteralChar(' ')), new Literal("a")));
    assertThat(RegexPattern.of("(?x) [a ]"))
        .isEqualTo(anyOf(new RegexPattern.LiteralChar('a'), new RegexPattern.LiteralChar(' ')));
    assertThat(RegexPattern.of("(?x) [^ ] a"))
        .isEqualTo(sequence(noneOf(new RegexPattern.LiteralChar(' ')), new Literal("a")));
  }

  @Test public void of_freeSpacingMode_escapedSpaceIsLiteral() {
    assertThat(RegexPattern.of("(?x) a\\ b")).isEqualTo(new Literal("a b"));
  }

  @Test public void of_nestedFreeSpacingMode_enabled() {
    assertThat(RegexPattern.of("a(?x: b c )d"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Group.NonCapturing(
                    new Literal("bc"), List.of(ModifierFlag.COMMENTS), List.of()),
                new Literal("d")));
  }

  @Test public void of_nestedFreeSpacingMode_disabled() {
    assertThat(RegexPattern.of("(?x)a(?-x: b c )d"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Group.NonCapturing(
                    new Literal(" b c "), List.of(), List.of(ModifierFlag.COMMENTS)),
                new Literal("d")));
  }

  @Test public void of_nestedFreeSpacingMode_invalidFlags() {
    assertThrows(Parser.ParseException.class, () -> RegexPattern.of("(?z:a)"));
  }

  @Test public void of_nestedModifierFlags_caseInsensitive() {
    assertThat(RegexPattern.of("a(?i:b)c"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Group.NonCapturing(
                    new Literal("b"), List.of(ModifierFlag.CASE_INSENSITIVE), List.of()),
                new Literal("c")));
  }

  @Test public void of_nestedModifierFlags_multiple() {
    assertThat(RegexPattern.of("a(?is-U:b)c"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Group.NonCapturing(
                    new Literal("b"), List.of(ModifierFlag.CASE_INSENSITIVE, ModifierFlag.DOTALL),
                    List.of(ModifierFlag.UNICODE_CHARACTER_CLASS)),
                new Literal("c")));
  }

  @Test public void of_toString_preservesModifiers() {
    assertThat(RegexPattern.of("(?is-U:b)").toString()).isEqualTo("(?is-U:b)");
    assertThat(RegexPattern.of("(?:b)").toString()).isEqualTo("(?:b)");
  }

  @Test public void of_nestedModifierFlags_contradictoryFlagsResolved() {
    assertThat(RegexPattern.of("(?x-x:a)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal("a"), List.of(ModifierFlag.COMMENTS), List.of(ModifierFlag.COMMENTS)));
    assertThat(RegexPattern.of("(?i-i:a)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal("a"), List.of(ModifierFlag.CASE_INSENSITIVE),
                List.of(ModifierFlag.CASE_INSENSITIVE)));
    assertThat(RegexPattern.of("(?is-s:a)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal("a"), List.of(ModifierFlag.CASE_INSENSITIVE, ModifierFlag.DOTALL),
                List.of(ModifierFlag.DOTALL)));
  }

  @Test public void of_nestedModifierFlags_syntaxBoundaries() {
    assertThat(RegexPattern.of("(?ii:a)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal("a"),
                List.of(ModifierFlag.CASE_INSENSITIVE, ModifierFlag.CASE_INSENSITIVE), List.of()));

    assertThrows(Parser.ParseException.class, () -> RegexPattern.of("(?-:a)"));
    assertThrows(Parser.ParseException.class, () -> RegexPattern.of("(?i-:a)"));
  }

  @Test public void of_nestedModifierFlags_inheritsFreeSpacing() {
    assertThat(RegexPattern.of("(?x) a (?i: b ) c"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Group.NonCapturing(
                    new Literal("b"), List.of(ModifierFlag.CASE_INSENSITIVE), List.of()),
                new Literal("c")));
  }

  @Test public void of_nestedModifierFlags_disabledCommentsPreservesLeadingSpace() {
    assertThat(RegexPattern.of("(?x)a(?-x: b)"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Group.NonCapturing(
                    new Literal(" b"), List.of(), List.of(ModifierFlag.COMMENTS))));
  }

  @Test public void safeMath_saturatedAdd() {
    assertThat(SafeMath.saturatedAdd(10, 20)).isEqualTo(30);
    assertThat(SafeMath.saturatedAdd(Integer.MAX_VALUE, 1)).isEqualTo(Integer.MAX_VALUE);
    assertThat(SafeMath.saturatedAdd(Integer.MAX_VALUE, Integer.MAX_VALUE))
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void safeMath_saturatedMultiply() {
    assertThat(SafeMath.saturatedMultiply(10, 20)).isEqualTo(200);
    assertThat(SafeMath.saturatedMultiply(Integer.MAX_VALUE, 2)).isEqualTo(Integer.MAX_VALUE);
    assertThat(SafeMath.saturatedMultiply(Integer.MAX_VALUE, Integer.MAX_VALUE))
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void maxSize_overflowOnMultiply() {
    // 2000000000 * 3 overflows Integer.MAX_VALUE
    RegexPattern multipliedOverflow = new Quantified(new Literal("abc"), repeated(2000000000));
    assertThat(multipliedOverflow.metadata().maxSize()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void maxSize_overflowOnAdd() {
    // 2000000000 + 2000000000 overflows Integer.MAX_VALUE
    RegexPattern addedOverflow = sequence(
        new Quantified(new Literal("a"), repeated(2000000000)),
        new Quantified(new Literal("b"), repeated(2000000000)));
    assertThat(addedOverflow.metadata().maxSize()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void maxSize_zeroOrMore_yieldsMaxValue() {
    assertThat(
            new Quantified(new Literal("a"), repeated(0, Integer.MAX_VALUE)).metadata().maxSize())
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void maxSize_oneOrMore_yieldsMaxValue() {
    assertThat(new Quantified(new Literal("a"), atLeast(1)).metadata().maxSize())
        .isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void maxSize_optional_preservesSize() {
    assertThat(new Quantified(new Literal("abc"), atMost(1)).metadata().maxSize()).isEqualTo(3);
  }

  @Test public void maxSize_group() {
    assertThat(RegexPattern.of("(abc)").metadata().maxSize()).isEqualTo(3);
    assertThat(RegexPattern.of("(?:abc)").metadata().maxSize()).isEqualTo(3);
    assertThat(RegexPattern.of("(?i:abc)").metadata().maxSize()).isEqualTo(3);
    assertThat(RegexPattern.of("(?i:.)").metadata().maxSize()).isEqualTo(2);
    assertThat(RegexPattern.of("(?i:a)b").metadata().maxSize()).isEqualTo(2);
  }

  @Test public void minSize_literal() {
    assertThat(RegexPattern.of("abc").metadata().minSize()).isEqualTo(3);
  }

  @Test public void minSize_optional() {
    assertThat(RegexPattern.of("a?").metadata().minSize()).isEqualTo(0);
  }

  @Test public void minSize_zeroOrMore() {
    assertThat(RegexPattern.of("a*").metadata().minSize()).isEqualTo(0);
  }

  @Test public void minSize_oneOrMore() {
    assertThat(RegexPattern.of("a+").metadata().minSize()).isEqualTo(1);
  }

  @Test public void minSize_sequence() {
    assertThat(RegexPattern.of(".*b").metadata().minSize()).isEqualTo(1);
    assertThat(RegexPattern.of(".+bc").metadata().minSize()).isEqualTo(3);
  }

  @Test public void minSize_alternation() {
    assertThat(RegexPattern.of("abc|d").metadata().minSize()).isEqualTo(1);
  }

  @Test public void minSize_optionalGroup() {
    assertThat(RegexPattern.of("(?i:abc)?").metadata().minSize()).isEqualTo(0);
  }

  @Test public void minSize_group() {
    assertThat(RegexPattern.of("(abc)").metadata().minSize()).isEqualTo(3);
    assertThat(RegexPattern.of("(?:abc)").metadata().minSize()).isEqualTo(3);
    assertThat(RegexPattern.of("(?i:abc)").metadata().minSize()).isEqualTo(3);
    assertThat(RegexPattern.of("(?i:.)").metadata().minSize()).isEqualTo(1);
    assertThat(RegexPattern.of("(?i:a)b").metadata().minSize()).isEqualTo(2);
  }

  @Test public void minSize_dot() {
    assertThat(RegexPattern.of(".").metadata().minSize()).isEqualTo(1);
  }

  @Test public void minSize_characterClass() {
    assertThat(RegexPattern.of("[abc]").metadata().minSize()).isEqualTo(1);
  }

  @Test public void minSize_negatedCharacterClass() {
    assertThat(RegexPattern.of("[^abc]").metadata().minSize()).isEqualTo(1);
  }

  @Test public void minSize_characterProperty() {
    assertThat(RegexPattern.of("\\p{Lower}").metadata().minSize()).isEqualTo(1);
  }

  @Test public void minSize_negatedCharacterProperty() {
    assertThat(RegexPattern.of("\\P{Lower}").metadata().minSize()).isEqualTo(1);
  }

  @Test public void maxSize_negatedCharacterClass() {
    assertThat(RegexPattern.of("[^abc]").metadata().maxSize()).isEqualTo(2);
  }

  @Test public void maxSize_characterProperty() {
    assertThat(RegexPattern.of("\\p{Lower}").metadata().maxSize()).isEqualTo(2);
  }

  @Test public void maxSize_negatedCharacterProperty() {
    assertThat(RegexPattern.of("\\P{Lower}").metadata().maxSize()).isEqualTo(2);
  }

  @Test public void minSize_anchor() {
    assertThat(RegexPattern.of("^").metadata().minSize()).isEqualTo(0);
    assertThat(RegexPattern.of("$").metadata().minSize()).isEqualTo(0);
    assertThat(RegexPattern.of("\\b").metadata().minSize()).isEqualTo(0);
  }

  @Test public void maxSize_anchor() {
    assertThat(RegexPattern.of("^").metadata().maxSize()).isEqualTo(0);
    assertThat(RegexPattern.of("$").metadata().maxSize()).isEqualTo(0);
    assertThat(RegexPattern.of("\\b").metadata().maxSize()).isEqualTo(0);
  }

  @Test public void minSize_lookaround() {
    assertThat(RegexPattern.of("(?=a)").metadata().minSize()).isEqualTo(0);
    assertThat(RegexPattern.of("(?!a)").metadata().minSize()).isEqualTo(0);
  }

  @Test public void maxSize_lookaround() {
    assertThat(RegexPattern.of("(?=a)").metadata().maxSize()).isEqualTo(0);
    assertThat(RegexPattern.of("(?!a)").metadata().maxSize()).isEqualTo(0);
  }

  @Test public void minSize_backreference() {
    assertThat(RegexPattern.of("(a)\\1").metadata().minSize())
        .isEqualTo(
            1); // Group matches "a" (size 1), but wait, what about just the backreference itself?
    // Let's construct a raw Backreference record to test it directly without Sequence wrappers!
    assertThat(new Backreference.Numbered(1).metadata().minSize()).isEqualTo(0);
  }

  @Test public void maxSize_backreference() {
    assertThat(new Backreference.Numbered(1).metadata().maxSize()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void metadata_constructorThrows_whenMinSizeIsNegative() {
    int minSizeNeg = -1;
    int maxSizeFive = 5;
    assertThrows(
        IllegalArgumentException.class, () -> new RegexPattern.Metadata(minSizeNeg, maxSizeFive));
  }

  @Test public void metadata_constructorThrows_whenMaxSizeIsNegative() {
    int minSizeFive = 5;
    int maxSizeNeg = -1;
    assertThrows(
        IllegalArgumentException.class, () -> new RegexPattern.Metadata(minSizeFive, maxSizeNeg));
  }

  @Test public void metadata_constructorThrows_whenMaxSizeIsLessThanMinSize() {
    int minSizeFiveForCompare = 5;
    int maxSizeFour = 4;
    assertThrows(
        IllegalArgumentException.class,
        () -> new RegexPattern.Metadata(minSizeFiveForCompare, maxSizeFour));
  }

  @Test public void metadata_constructsSuccessfully() {
    int minSize = 5;
    int maxSize = 5;
    Metadata metadata = new Metadata(minSize, maxSize);
    assertThat(metadata.minSize()).isEqualTo(5);
    assertThat(metadata.maxSize()).isEqualTo(5);
  }

  @Test public void literalCharToString_hyphen() {
    assertThat(new LiteralChar('-').toString()).isEqualTo("\\-");
  }

  @Test public void characterSetToString_withLiteralHyphen() {
    assertThat(anyOf(new LiteralChar('a'), new LiteralChar('-'), new LiteralChar('z')).toString())
        .isEqualTo("[a\\-z]");
  }

  @Test public void quantifiedMultiCharLiteralToString() {
    assertThat(new Quantified(new Literal("abc"), repeated()).toString()).isEqualTo("(?:abc)*");
  }

  @Test public void quantifiedSingleCharLiteralToString() {
    assertThat(new Quantified(new Literal("a"), repeated()).toString()).isEqualTo("a*");
  }

  @Test public void atLeast_reluctantThenPossessive() {
    AtLeast quantifier = atLeast(1).reluctant().possessive();
    assertThat(quantifier.isReluctant()).isFalse();
    assertThat(quantifier.isPossessive()).isTrue();
  }

  @Test public void atLeast_possessiveThenReluctant() {
    AtLeast quantifier = atLeast(1).possessive().reluctant();
    assertThat(quantifier.isReluctant()).isTrue();
    assertThat(quantifier.isPossessive()).isFalse();
  }

  @Test public void atLeast_reluctantAndPossessive_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new AtLeast(1, true, true));
  }

  @Test public void atMost_reluctantThenPossessive() {
    AtMost quantifier = atMost(1).reluctant().possessive();
    assertThat(quantifier.isReluctant()).isFalse();
    assertThat(quantifier.isPossessive()).isTrue();
  }

  @Test public void atMost_possessiveThenReluctant() {
    AtMost quantifier = atMost(1).possessive().reluctant();
    assertThat(quantifier.isReluctant()).isTrue();
    assertThat(quantifier.isPossessive()).isFalse();
  }

  @Test public void atMost_reluctantAndPossessive_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new AtMost(1, true, true));
  }

  @Test public void limited_reluctantThenPossessive() {
    Limited quantifier = new Limited(1, 2, false, false).reluctant().possessive();
    assertThat(quantifier.isReluctant()).isFalse();
    assertThat(quantifier.isPossessive()).isTrue();
  }

  @Test public void limited_possessiveThenReluctant() {
    Limited quantifier = new Limited(1, 2, false, false).possessive().reluctant();
    assertThat(quantifier.isReluctant()).isTrue();
    assertThat(quantifier.isPossessive()).isFalse();
  }

  @Test public void limited_reluctantAndPossessive_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new Limited(1, 2, true, true));
  }

  @Test public void sequence_defensiveCopy() {
    List<RegexPattern> list = new ArrayList<>(List.of(new Literal("a")));
    Sequence seq = new Sequence(list);
    list.clear();
    assertThat(seq.elements()).containsExactly(new Literal("a"));
  }

  @Test public void alternation_defensiveCopy() {
    List<RegexPattern> list = new ArrayList<>(List.of(new Literal("a")));
    Alternation alt = new Alternation(list);
    list.clear();
    assertThat(alt.alternatives()).containsExactly(new Literal("a"));
  }

  @Test public void anyOf_defensiveCopy() {
    List<CharSetElement> list = new ArrayList<>(List.of(new LiteralChar('a')));
    CharacterSet.AnyOf anyOf = new CharacterSet.AnyOf(list);
    list.clear();
    assertThat(anyOf.elements()).containsExactly(new LiteralChar('a'));
  }

  @Test public void noneOf_defensiveCopy() {
    List<CharSetElement> list = new ArrayList<>(List.of(new LiteralChar('a')));
    CharacterSet.NoneOf noneOf = new CharacterSet.NoneOf(list);
    list.clear();
    assertThat(noneOf.elements()).containsExactly(new LiteralChar('a'));
  }

  @Test public void inSequence_collector_deeplyNestedSequences_merged() {
    assertThat(
            Stream.of(
                    new Literal("a"),
                    sequence(sequence(new Literal("b"), new Literal("c")), new Literal("d")))
                .collect(RegexPattern.inSequence()))
        .isEqualTo(new Literal("abcd"));
  }

  @Test public void of_emptyAlternative() {
    assertThat(RegexPattern.of("^//(?:javatests/|java/|)"))
        .isEqualTo(
            sequence(
                Anchor.BEGINNING,
                new Literal("//"),
                new Group.NonCapturing(
                    alternation(
                        new Literal("javatests/"), new Literal("java/"), new Literal("")))));
  }

  @Test public void of_standaloneModifierFlags() {
    assertThat(RegexPattern.of("(?m)^interface\\s+[A-Za-z0-9_]+\\s*\\{[^}]*\\}\\s*"))
        .isEqualTo(
            sequence(
                new Group.NonCapturing(new Literal(""), List.of(ModifierFlag.MULTILINE), List.of()),
                Anchor.BEGINNING,
                new Literal("interface"),
                new Quantified(PredefinedCharClass.WHITESPACE, atLeast(1)),
                new Quantified(
                    anyOf(
                        new CharRange('A', 'Z'),
                        new CharRange('a', 'z'),
                        new CharRange('0', '9'),
                        new LiteralChar('_')),
                    atLeast(1)),
                new Quantified(PredefinedCharClass.WHITESPACE, repeated()),
                new Literal("{"),
                new Quantified(noneOf(new LiteralChar('}')), repeated()),
                new Literal("}"),
                new Quantified(PredefinedCharClass.WHITESPACE, repeated())));
  }

  @Test public void of_standaloneModifierFlags_disabledOnly() {
    assertThat(RegexPattern.of("(?-i)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal(""), List.of(), List.of(ModifierFlag.CASE_INSENSITIVE)));
  }

  @Test public void of_standaloneModifierFlags_enabledAndDisabled() {
    assertThat(RegexPattern.of("(?is-m)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal(""),
                List.of(ModifierFlag.CASE_INSENSITIVE, ModifierFlag.DOTALL),
                List.of(ModifierFlag.MULTILINE)));
  }

  @Test public void of_standaloneModifierFlags_toString() {
    assertThat(RegexPattern.of("(?m)").toString()).isEqualTo("(?m)");
  }

  @Test public void of_standaloneModifierFlags_withDisabledFlags_toString() {
    assertThat(RegexPattern.of("(?is-m)").toString()).isEqualTo("(?is-m)");
  }
}
