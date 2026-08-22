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

  @Test public void of_literal_openingCurlyBrace() {
    assertThat(RegexPattern.of("{")).isEqualTo(new Literal("{"));
  }

  @Test public void of_literal_closingCurlyBrace() {
    assertThat(RegexPattern.of("}")).isEqualTo(new Literal("}"));
  }

  @Test public void of_literal_closingSquareBracket() {
    assertThat(RegexPattern.of("]")).isEqualTo(new Literal("]"));
  }

  @Test public void of_literal_curlyBracesNonQuantifier() {
    assertThat(RegexPattern.of("(dev|prod){ENV}"))
        .isEqualTo(
            sequence(
                new Group.Capturing(alternation(new Literal("dev"), new Literal("prod"))),
                new Literal("{ENV}")));
  }

  @Test public void of_literal_withCurlyBraces() {
    assertThat(RegexPattern.of("a{foo}b")).isEqualTo(new Literal("a{foo}b"));
  }

  @Test public void of_literal_loneOpeningBrace() {
    assertThat(RegexPattern.of("a{b")).isEqualTo(new Literal("a{b"));
  }

  @Test public void of_literal_withClosingBracketsAndBraces() {
    assertThat(RegexPattern.of("foo}bar]")).isEqualTo(new Literal("foo}bar]"));
  }

  @Test public void of_literal_escapedOpeningBraceWithUnescapedClosingBrace() {
    assertThat(RegexPattern.of("\\{foo}")).isEqualTo(new Literal("{foo}"));
  }

  @Test public void of_escapedLiteral() {
    assertThat(RegexPattern.of("\\ ")).isEqualTo(new Literal(" "));
    assertThat(RegexPattern.of("\\n")).isEqualTo(new Literal("\n"));
    assertThat(RegexPattern.of("\\t")).isEqualTo(new Literal("\t"));
    assertThat(RegexPattern.of("\\r")).isEqualTo(new Literal("\r"));
    assertThat(RegexPattern.of("\\f")).isEqualTo(new Literal("\f"));
    assertThat(RegexPattern.of("\\u2122")).isEqualTo(new Literal("\u2122"));
    assertThat(RegexPattern.of("\\x41")).isEqualTo(new Literal("A"));
    assertThat(RegexPattern.of("\\x{41}")).isEqualTo(new Literal("A"));
    assertThat(RegexPattern.of("\\\\")).isEqualTo(new Literal("\\"));
    assertThat(RegexPattern.of("\\{\\}")).isEqualTo(new Literal("{}"));
  }

  @Test public void of_escapedLiteral_bell() {
    assertThat(RegexPattern.of("\\a")).isEqualTo(new Literal("\u0007"));
  }

  @Test public void of_escapedLiteral_escape() {
    assertThat(RegexPattern.of("\\e")).isEqualTo(new Literal("\u001B"));
  }

  @Test public void of_escapedLiteral_octal() {
    assertThat(RegexPattern.of("\\0101")).isEqualTo(new Literal("A"));
  }

  @Test public void of_escapedLiteral_octalTwoDigits() {
    assertThat(RegexPattern.of("\\077")).isEqualTo(new Literal("?"));
  }

  @Test public void of_escapedLiteral_octalOneDigit() {
    assertThat(RegexPattern.of("\\07")).isEqualTo(new Literal("\u0007"));
  }

  @Test public void of_escapedLiteral_octalZero() {
    assertThat(RegexPattern.of("\\00")).isEqualTo(new Literal("\u0000"));
  }

  @Test public void of_escapedLiteral_control() {
    assertThat(RegexPattern.of("\\cA")).isEqualTo(new Literal("\u0001"));
  }

  @Test public void of_charClass_withSurrogatePairHexCodePoint() {
    assertThat(RegexPattern.of("[\\x{1f600}]"))
        .isEqualTo(new CharacterSet.AnyOf(List.of(new LiteralChar(0x1F600))));
  }

  @Test public void of_charClass_withBellAndEscape() {
    assertThat(RegexPattern.of("[\\a\\e]"))
        .isEqualTo(anyOf(new LiteralChar('\u0007'), new LiteralChar('\u001B')));
  }

  @Test public void of_charClass_withOctal() {
    assertThat(RegexPattern.of("[\\0101]")).isEqualTo(anyOf(new LiteralChar('A')));
  }

  @Test public void of_charClass_withControl() {
    assertThat(RegexPattern.of("[\\cA]")).isEqualTo(anyOf(new LiteralChar('\u0001')));
  }

  @Test public void of_escapedLiteralMixedWithPredefinedCharClasses() {
    assertThat(RegexPattern.of("\\j\\d\\w"))
        .isEqualTo(sequence(new Literal("j"), PredefinedCharClass.DIGIT, PredefinedCharClass.WORD));
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

  @Test public void of_group_nonCapturing_withEnabledFlags_empty() {
    assertThat(RegexPattern.of("(?i:)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal(""), List.of(ModifierFlag.CASE_INSENSITIVE), List.of()));
  }

  @Test public void of_group_nonCapturing_withDisabledFlags_empty() {
    assertThat(RegexPattern.of("(?-i:)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal(""), List.of(), List.of(ModifierFlag.CASE_INSENSITIVE)));
  }

  @Test public void of_group_nonCapturing_withEnabledAndDisabledFlags_empty() {
    assertThat(RegexPattern.of("(?i-m:)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal(""),
                List.of(ModifierFlag.CASE_INSENSITIVE),
                List.of(ModifierFlag.MULTILINE)));
  }

  @Test public void of_group_nonCapturing_withFlags_empty_toString() {
    assertThat(RegexPattern.of("(?i:)").toString()).isEqualTo("(?i)");
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

  @Test public void of_characterSet_leadingClosingBracket() {
    assertThat(RegexPattern.of("[]]")).isEqualTo(anyOf(new LiteralChar(']')));
  }

  @Test public void of_characterSet_leadingClosingBracket_withOtherElements() {
    assertThat(RegexPattern.of("[]a-z]"))
        .isEqualTo(anyOf(new LiteralChar(']'), new CharRange('a', 'z')));
  }

  @Test public void of_characterSet_negated_leadingClosingBracket() {
    assertThat(RegexPattern.of("[^]]")).isEqualTo(noneOf(new LiteralChar(']')));
  }

  @Test public void of_characterSet_negated_leadingClosingBracket_withOtherElements() {
    assertThat(RegexPattern.of("[^]a-z]"))
        .isEqualTo(noneOf(new LiteralChar(']'), new CharRange('a', 'z')));
  }

  @Test public void of_characterSet_leadingClosingBracket_range() {
    assertThat(RegexPattern.of("[]-z]")).isEqualTo(anyOf(new CharRange(']', 'z')));
  }

  @Test public void of_characterSet_quotedLiteral() {
    assertThat(RegexPattern.of("[\\Qabc\\E]"))
        .isEqualTo(anyOf(new LiteralChar('a'), new LiteralChar('b'), new LiteralChar('c')));
  }

  @Test public void of_characterSet_quotedLiteral_withSpecialChars() {
    assertThat(RegexPattern.of("[\\Q[]{}*+\\E]"))
        .isEqualTo(
            anyOf(
                new LiteralChar('['),
                new LiteralChar(']'),
                new LiteralChar('{'),
                new LiteralChar('}'),
                new LiteralChar('*'),
                new LiteralChar('+')));
  }

  @Test public void of_characterSet_quotedLiteral_example() {
    assertThat(RegexPattern.of("[\\Q,:\\!~_?*()[]{}$%@><\\E]"))
        .isEqualTo(
            anyOf(
                ",:\\!~_?*()[]{}$%@><"
                    .chars()
                    .mapToObj(c -> (CharSetElement) new LiteralChar((char) c))
                    .toList()));
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

  @Test public void literalChar_negativeCodePoint_throws() {
    assertThrows(IllegalArgumentException.class, () -> new LiteralChar(-1));
  }

  @Test public void literalChar_codePointTooLarge_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new LiteralChar(Character.MAX_CODE_POINT + 1));
  }

  @Test public void literalChar_supplementaryCodePoint() {
    LiteralChar lc = new LiteralChar(0x1F600);
    assertThat(lc.codePoint()).isEqualTo(0x1F600);
    assertThat(lc.toString()).isEqualTo("\uD83D\uDE00");
  }

  @Test public void literalCharToString_hyphen() {
    assertThat(new LiteralChar('-').toString()).isEqualTo("\\-");
  }

  @Test public void literalCharToString_openingBracket() {
    assertThat(new LiteralChar('[').toString()).isEqualTo("\\[");
  }

  @Test public void characterSetToString_withLiteralHyphen() {
    assertThat(anyOf(new LiteralChar('a'), new LiteralChar('-'), new LiteralChar('z')).toString())
        .isEqualTo("[a\\-z]");
  }

  @Test public void characterSetToString_withLiteralOpeningBracket() {
    assertThat(anyOf(new LiteralChar('[')).toString()).isEqualTo("[\\[]");
  }

  @Test public void charRange_negativeStartCodePoint_throws() {
    assertThrows(IllegalArgumentException.class, () -> new CharRange(-1, 'z'));
  }

  @Test public void charRange_negativeEndCodePoint_throws() {
    assertThrows(IllegalArgumentException.class, () -> new CharRange('a', -1));
  }

  @Test public void charRange_startCodePointTooLarge_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new CharRange(Character.MAX_CODE_POINT + 1, 'z'));
  }

  @Test public void charRange_endCodePointTooLarge_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> new CharRange('a', Character.MAX_CODE_POINT + 1));
  }

  @Test public void of_charRange_supplementaryCodePoints() {
    assertThat(RegexPattern.of("[\\x{1F600}-\\x{1F64F}]"))
        .isEqualTo(anyOf(new CharRange(0x1F600, 0x1F64F)));
  }

  @Test public void charRangeToString_supplementaryCodePoints() {
    assertThat(new CharRange(0x1F600, 0x1F64F).toString()).isEqualTo("\uD83D\uDE00-\uD83D\uDE4F");
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

  @Test public void of_standaloneModifierFlags_none() {
    assertThat(RegexPattern.of("(?)\t"))
        .isEqualTo(
            sequence(
                new Group.NonCapturing(new Literal(""), List.of(), List.of()), new Literal("\t")));
  }

  @Test public void of_standaloneModifierFlags_empty() {
    assertThat(RegexPattern.of("(?).*/DCIM/original(~\\d+)?\\.jpg"))
        .isEqualTo(
            sequence(
                new Group.NonCapturing(new Literal(""), List.of(), List.of()),
                new Quantified(PredefinedCharClass.ANY_CHAR, repeated()),
                new Literal("/DCIM/original"),
                new Quantified(
                    new Group.Capturing(
                        sequence(
                            new Literal("~"),
                            new Quantified(PredefinedCharClass.DIGIT, atLeast(1)))),
                    atMost(1)),
                new Literal(".jpg")));
  }

  @Test public void of_dotInCharacterSet_parsedAsLiteralChar() {
    assertThat(RegexPattern.of("[.]")).isEqualTo(anyOf(new LiteralChar('.')));
  }

  @Test public void of_dotInCharacterSetWithOtherChars_parsedAsLiteralChar() {
    assertThat(RegexPattern.of("[a-z.]"))
        .isEqualTo(anyOf(new CharRange('a', 'z'), new LiteralChar('.')));
  }

  @Test public void of_dotInNegatedCharacterSet_parsedAsLiteralChar() {
    assertThat(RegexPattern.of("[^.]")).isEqualTo(noneOf(new LiteralChar('.')));
  }

  @Test public void of_dotInsideCharacterSetRange_parsedAsLiteralChar() {
    assertThat(RegexPattern.of("[.-0]")).isEqualTo(anyOf(new CharRange('.', '0')));
  }

  @Test public void of_escapedDotInCharacterSet_parsedAsLiteralChar() {
    assertThat(RegexPattern.of("[\\.]")).isEqualTo(anyOf(new LiteralChar('.')));
  }

  @Test public void of_predefinedCharClassInCharacterSet(
      @TestParameter({"\\d", "\\D", "\\s", "\\S", "\\w", "\\W"}) String pattern) {
    assertThat(RegexPattern.of("[" + pattern + "]"))
        .isEqualTo(anyOf((CharSetElement) RegexPattern.of(pattern)));
  }

  @Test public void of_controlEscapes_newline() {
    assertThat(RegexPattern.of("\\n").toString()).isEqualTo("\\n");
  }

  @Test public void of_controlEscapes_return() {
    assertThat(RegexPattern.of("\\r").toString()).isEqualTo("\\r");
  }

  @Test public void of_controlEscapes_tab() {
    assertThat(RegexPattern.of("\\t").toString()).isEqualTo("\\t");
  }

  @Test public void of_unicodeEscape() {
    assertThat(RegexPattern.of("\\uFEFF")).isEqualTo(new Literal("\uFEFF"));
  }

  @Test public void of_hexEscape() {
    assertThat(RegexPattern.of("\\x41")).isEqualTo(new Literal("A"));
  }

  @Test public void of_escapedInCharacterSet_newlineAndReturn() {
    assertThat(RegexPattern.of("[^\\r\\n]").toString()).isEqualTo("[^\\r\\n]");
  }

  @Test public void of_horizontalWhitespace() {
    assertThat(RegexPattern.of("\\h")).isEqualTo(PredefinedCharClass.HORIZONTAL_WHITESPACE);
  }

  @Test public void of_verticalWhitespace() {
    assertThat(RegexPattern.of("\\v")).isEqualTo(PredefinedCharClass.VERTICAL_WHITESPACE);
  }

  @Test public void anchor_previousMatchEnd_toString() {
    assertThat(Anchor.PREVIOUS_MATCH_END.toString()).isEqualTo("\\G");
  }

  @Test public void anchor_previousMatchEnd_metadata() {
    assertThat(Anchor.PREVIOUS_MATCH_END.metadata())
        .isEqualTo(new Metadata(/* minSize= */ 0, /* maxSize= */ 0));
  }

  @Test public void anchor_graphemeClusterBoundary_toString() {
    assertThat(Anchor.GRAPHEME_CLUSTER_BOUNDARY.toString()).isEqualTo("\\b{g}");
  }

  @Test public void anchor_graphemeClusterBoundary_metadata() {
    assertThat(Anchor.GRAPHEME_CLUSTER_BOUNDARY.metadata())
        .isEqualTo(new Metadata(/* minSize= */ 0, /* maxSize= */ 0));
  }

  @Test public void predefinedCharClass_extendedGraphemeCluster_toString() {
    assertThat(PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER.toString()).isEqualTo("\\X");
  }

  @Test public void predefinedCharClass_extendedGraphemeCluster_metadata() {
    assertThat(PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER.metadata())
        .isEqualTo(new Metadata(/* minSize= */ 1, /* maxSize= */ Integer.MAX_VALUE));
  }

  @Test public void modifierFlag_canonicalEquivalence_toString() {
    assertThat(ModifierFlag.CANONICAL_EQUIVALENCE.toString()).isEqualTo("c");
  }

  @Test public void group_nonCapturing_withCanonicalEquivalence_toString() {
    Group.NonCapturing group = new Group.NonCapturing(
        new Literal("a"), List.of(ModifierFlag.CANONICAL_EQUIVALENCE), List.of());
    assertThat(group.toString()).isEqualTo("(?c:a)");
  }

  @Test public void of_anchor_previousMatchEnd() {
    assertThat(RegexPattern.of("\\G")).isEqualTo(Anchor.PREVIOUS_MATCH_END);
  }

  @Test public void of_anchor_previousMatchEnd_inSequence() {
    assertThat(RegexPattern.of("\\Gabc"))
        .isEqualTo(sequence(Anchor.PREVIOUS_MATCH_END, new Literal("abc")));
  }

  @Test public void of_anchor_graphemeClusterBoundary() {
    assertThat(RegexPattern.of("\\b{g}")).isEqualTo(Anchor.GRAPHEME_CLUSTER_BOUNDARY);
  }

  @Test public void of_anchor_graphemeClusterBoundary_inSequence() {
    assertThat(RegexPattern.of("\\b{g}abc"))
        .isEqualTo(sequence(Anchor.GRAPHEME_CLUSTER_BOUNDARY, new Literal("abc")));
  }

  @Test public void of_predefinedCharClass_extendedGraphemeCluster() {
    assertThat(RegexPattern.of("\\X")).isEqualTo(PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER);
  }

  @Test public void of_predefinedCharClass_extendedGraphemeCluster_inSequence() {
    assertThat(RegexPattern.of("a\\Xb"))
        .isEqualTo(
            sequence(
                new Literal("a"), PredefinedCharClass.EXTENDED_GRAPHEME_CLUSTER, new Literal("b")));
  }

  @Test public void of_escapedLiteral_namedUnicodeCharacter() {
    assertThat(RegexPattern.of("\\N{LATIN CAPITAL LETTER A}")).isEqualTo(new Literal("A"));
  }

  @Test public void of_escapedLiteral_namedUnicodeCharacter_supplementaryCodePoint() {
    assertThat(RegexPattern.of("\\N{WHITE SMILING FACE}")).isEqualTo(new Literal("\u263A"));
  }

  @Test public void of_escapedLiteral_namedUnicodeCharacter_inSequence() {
    assertThat(RegexPattern.of("pre\\N{LATIN CAPITAL LETTER A}post"))
        .isEqualTo(new Literal("preApost"));
  }

  @Test public void of_charClass_withNamedUnicodeCharacter() {
    assertThat(RegexPattern.of("[\\N{LATIN CAPITAL LETTER A}]"))
        .isEqualTo(anyOf(new LiteralChar('A')));
  }

  @Test public void of_charClass_withNamedUnicodeCharacter_range() {
    assertThat(RegexPattern.of("[\\N{LATIN CAPITAL LETTER A}-\\N{LATIN CAPITAL LETTER Z}]"))
        .isEqualTo(anyOf(new CharRange('A', 'Z')));
  }

  @Test public void of_group_nonCapturing_withCanonicalEquivalence() {
    assertThat(RegexPattern.of("(?c:a)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal("a"), List.of(ModifierFlag.CANONICAL_EQUIVALENCE), List.of()));
  }

  @Test public void of_group_nonCapturing_withDisabledCanonicalEquivalence() {
    assertThat(RegexPattern.of("(?-c:a)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal("a"), List.of(), List.of(ModifierFlag.CANONICAL_EQUIVALENCE)));
  }

  @Test public void of_standaloneModifierFlags_canonicalEquivalence() {
    assertThat(RegexPattern.of("(?c)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal(""), List.of(ModifierFlag.CANONICAL_EQUIVALENCE), List.of()));
  }

  @Test public void of_standaloneModifierFlags_disabledCanonicalEquivalence() {
    assertThat(RegexPattern.of("(?-c)"))
        .isEqualTo(
            new Group.NonCapturing(
                new Literal(""), List.of(), List.of(ModifierFlag.CANONICAL_EQUIVALENCE)));
  }

  @Test public void of_charClass_withExtendedGraphemeCluster() {
    assertThat(RegexPattern.of("[\\X]")).isEqualTo(anyOf(new LiteralChar('X')));
  }

  @Test public void anchor_nonGraphemeClusterBoundary_toString() {
    assertThat(Anchor.NON_GRAPHEME_CLUSTER_BOUNDARY.toString()).isEqualTo("\\B{g}");
  }

  @Test public void anchor_nonGraphemeClusterBoundary_metadata() {
    assertThat(Anchor.NON_GRAPHEME_CLUSTER_BOUNDARY.metadata())
        .isEqualTo(new Metadata(/* minSize= */ 0, /* maxSize= */ 0));
  }

  @Test public void of_anchor_nonGraphemeClusterBoundary() {
    assertThat(RegexPattern.of("\\B{g}")).isEqualTo(Anchor.NON_GRAPHEME_CLUSTER_BOUNDARY);
  }

  @Test public void of_anchor_nonGraphemeClusterBoundary_inSequence() {
    assertThat(RegexPattern.of("\\B{g}abc"))
        .isEqualTo(sequence(Anchor.NON_GRAPHEME_CLUSTER_BOUNDARY, new Literal("abc")));
  }

  @Test public void of_unicodeProperty_singleLetterUnbraced_letter() {
    assertThat(RegexPattern.of("\\pL")).isEqualTo(new UnicodeProperty("L"));
  }

  @Test public void of_unicodeProperty_singleLetterUnbraced_number() {
    assertThat(RegexPattern.of("\\pN")).isEqualTo(new UnicodeProperty("N"));
  }

  @Test public void of_negatedUnicodeProperty_singleLetterUnbraced() {
    assertThat(RegexPattern.of("\\PL")).isEqualTo(new UnicodeProperty("L").negated());
  }

  @Test public void of_charClass_withSingleLetterUnbracedUnicodeProperty() {
    assertThat(RegexPattern.of("[\\pL]")).isEqualTo(anyOf(new UnicodeProperty("L")));
  }

  @Test public void of_charClass_withNegatedSingleLetterUnbracedUnicodeProperty() {
    assertThat(RegexPattern.of("[\\PL]")).isEqualTo(anyOf(new UnicodeProperty("L").negated()));
  }

  @Test public void of_unicodeProperty_withScriptAssignment() {
    assertThat(RegexPattern.of("\\p{sc=Latin}")).isEqualTo(new UnicodeProperty("sc=Latin"));
  }

  @Test public void of_unicodeProperty_withBlockAssignment() {
    assertThat(RegexPattern.of("\\p{blk=Greek}")).isEqualTo(new UnicodeProperty("blk=Greek"));
  }

  @Test public void of_unicodeProperty_withGeneralCategoryAssignment() {
    assertThat(RegexPattern.of("\\p{gc=Lu}")).isEqualTo(new UnicodeProperty("gc=Lu"));
  }

  @Test public void of_charClass_withPropertyValueAssignment() {
    assertThat(RegexPattern.of("[\\p{sc=Latin}]"))
        .isEqualTo(anyOf(new UnicodeProperty("sc=Latin")));
  }

  @Test public void of_escapedLiteral_controlLowercase() {
    assertThat(RegexPattern.of("\\ca")).isEqualTo(new Literal("\u0001"));
  }

  @Test public void of_escapedLiteral_controlLowercase_z() {
    assertThat(RegexPattern.of("\\cz")).isEqualTo(new Literal("\u001A"));
  }

  @Test public void of_charClass_withControlLowercase() {
    assertThat(RegexPattern.of("[\\ca]")).isEqualTo(anyOf(new LiteralChar('\u0001')));
  }

  @Test public void of_charClass_withLinebreak() {
    assertThat(RegexPattern.of("[\\R]")).isEqualTo(anyOf(new LiteralChar('R')));
  }
}
