package com.google.common.labs.regex;

import static com.google.common.labs.regex.RegexPattern.Quantifier.repeated;
import static com.google.common.labs.regex.RegexPattern.alternation;
import static com.google.common.labs.regex.RegexPattern.anyOf;
import static com.google.common.labs.regex.RegexPattern.intersection;
import static com.google.common.labs.regex.RegexPattern.noneOf;
import static com.google.common.labs.regex.RegexPattern.sequence;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parser.ParseException;
import com.google.common.labs.regex.RegexPattern.Alternation;
import com.google.common.labs.regex.RegexPattern.Backreference;
import com.google.common.labs.regex.RegexPattern.CharRange;
import com.google.common.labs.regex.RegexPattern.Group;
import com.google.common.labs.regex.RegexPattern.Literal;
import com.google.common.labs.regex.RegexPattern.LiteralChar;
import com.google.common.labs.regex.RegexPattern.Metadata;
import com.google.common.labs.regex.RegexPattern.ModifierDirective;
import com.google.common.labs.regex.RegexPattern.ModifierFlag;
import com.google.common.labs.regex.RegexPattern.Quantified;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Reproducing tests for defects found while auditing {@link RegexPattern} and {@code RegexParsers}
 * against {@link java.util.regex.Pattern} semantics.
 *
 * <p>Every test here asserts the behavior that agrees with {@code java.util.regex}. They are
 * expected to fail until the corresponding defects are fixed.
 */
@RunWith(JUnit4.class)
public final class RegexPatternConformanceTest {

  // ---------------------------------------------------------------------------------------------
  // Finding 1: a quantifier binds to the entire adjacent literal run instead of the last character.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_starAfterTwoCharLiteral_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("ab*"))
        .isEqualTo(sequence(new Literal("a"), new Quantified(new Literal("b"), repeated())));
  }

  @Test public void of_plusAfterTwoCharLiteral_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("ab+"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Quantified(new Literal("b"), RegexPattern.Quantifier.atLeast(1))));
  }

  @Test public void of_questionAfterTwoCharLiteral_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("ab?"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Quantified(new Literal("b"), RegexPattern.Quantifier.atMost(1))));
  }

  @Test public void of_curlyQuantifierAfterTwoCharLiteral_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("ab{2}"))
        .isEqualTo(
            sequence(
                new Literal("a"),
                new Quantified(new Literal("b"), RegexPattern.Quantifier.repeated(2, 2))));
  }

  @Test public void of_starAfterThreeCharLiteral_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("abc*"))
        .isEqualTo(sequence(new Literal("ab"), new Quantified(new Literal("c"), repeated())));
  }

  @Test public void of_starAfterLiteralRunFollowedByMoreText_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("ab*c"))
        .isEqualTo(
            sequence(
                new Literal("a"), new Quantified(new Literal("b"), repeated()), new Literal("c")));
  }

  @Test public void of_starAfterQuotedLiteral_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("\\Qab\\E*"))
        .isEqualTo(sequence(new Literal("a"), new Quantified(new Literal("b"), repeated())));
  }

  @Test public void of_starAfterLiteralRunPrecededByCharClass_quantifiesLastCharOnly() {
    assertThat(RegexPattern.of("[ab]cd*"))
        .isEqualTo(
            sequence(
                anyOf(new LiteralChar('a'), new LiteralChar('b')),
                new Literal("c"),
                new Quantified(new Literal("d"), repeated())));
  }

  @Test public void of_curlyQuantifierAfterTwoCharLiteral_metadata() {
    assertThat(RegexPattern.of("ab{2}").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 3, /* maxSize= */ 3));
  }

  @Test public void of_starAfterTwoCharLiteral_toString() {
    assertThat(RegexPattern.of("ab*").toString()).isEqualTo("ab*");
  }

  @Test public void of_starAfterWhitespaceRun_quantifiesLastSpaceOnly() {
    assertThat(RegexPattern.of("a  *"))
        .isEqualTo(sequence(new Literal("a "), new Quantified(new Literal(" "), repeated())));
  }

  @Test public void of_starAfterSurrogatePair_quantifiesWholePairOnly() {
    assertThat(RegexPattern.of("a\uD83D\uDE00*"))
        .isEqualTo(
            sequence(new Literal("a"), new Quantified(new Literal("\uD83D\uDE00"), repeated())));
  }

  @Test public void of_starAfterLoneSurrogatePair_quantifiesWholePair() {
    assertThat(RegexPattern.of("\uD83D\uDE00*"))
        .isEqualTo(new Quantified(new Literal("\uD83D\uDE00"), repeated()));
  }

  @Test public void of_starAfterSurrogatePair_renderedPatternRepeatsWholePair() {
    assertThat(
            Pattern.compile(RegexPattern.of("a\uD83D\uDE00*").toString())
                .matcher("a\uD83D\uDE00\uD83D\uDE00")
                .matches())
        .isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 2: in `[^a-z&&d-f]`, `^` negates the whole intersection, not just its first operand.
  // And a negated set as the first operand of `&&` renders differently than in any other position,
  // so `Intersection` is not commutative.
  // ---------------------------------------------------------------------------------------------

  @Test public void intersection_negatedOperandFirst_rendersCommutatively() {
    String negatedFirst =
        intersection(
                noneOf(new LiteralChar('b'), new LiteralChar('c')), anyOf(new CharRange('a', 'z')))
            .toString();
    String negatedLast =
        intersection(
                anyOf(new CharRange('a', 'z')), noneOf(new LiteralChar('b'), new LiteralChar('c')))
            .toString();
    assertThat(Pattern.compile(negatedFirst).matcher("0").matches())
        .isEqualTo(Pattern.compile(negatedLast).matcher("0").matches());
  }

  @Test public void intersection_negatedOperandFirst_toString() {
    assertThat(
            intersection(
                    noneOf(new LiteralChar('b'), new LiteralChar('c')),
                    anyOf(new CharRange('a', 'z')))
                .toString())
        .isEqualTo("[[^bc]&&a-z]");
  }

  @Test public void of_negatedIntersection_negatesTheWholeIntersection() {
    assertThat(RegexPattern.of("[^a-z&&d-f]"))
        .isEqualTo(
            noneOf(intersection(anyOf(new CharRange('a', 'z')), anyOf(new CharRange('d', 'f')))));
  }

  @Test public void of_negatedIntersection_renderedPatternMatchesOutsideTheIntersection() {
    // java.util.regex: `[^a-z&&d-f]` is `NOT(a-z AND d-f)`, so it matches 'a'.
    assertThat(Pattern.compile(RegexPattern.of("[^a-z&&d-f]").toString()).matcher("a").matches())
        .isTrue();
  }

  @Test public void of_negatedIntersection_renderedPatternRejectsInsideTheIntersection() {
    assertThat(Pattern.compile(RegexPattern.of("[^a-z&&d-f]").toString()).matcher("e").matches())
        .isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 3: `(?i:)` and `(?i)` collapse to the same node, so re-rendering widens the flag scope.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_emptyFlagGroup_notEqualToStandaloneFlagDirective() {
    assertThat(RegexPattern.of("(?i:)")).isNotEqualTo(RegexPattern.of("(?i)"));
  }

  @Test public void of_emptyFlagGroup_renderedPatternIsNotCaseInsensitive() {
    String rendered = RegexPattern.of("(?i:)abc").toString();
    assertThat(Pattern.compile(rendered).matcher("ABC").matches()).isFalse();
  }

  @Test public void of_emptyDotallGroup_renderedPatternDoesNotEnableDotall() {
    String rendered = RegexPattern.of("(?s:)a.b").toString();
    assertThat(Pattern.compile(rendered).matcher("a\nb").matches()).isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 4: literals containing whitespace or '#' are rendered unescaped inside a group that
  // enables COMMENTS.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_commentsGroupWithEscapedSpace_renderedPatternStillMatchesSpace() {
    String rendered = RegexPattern.of("(?x:a\\ b)").toString();
    assertThat(Pattern.compile(rendered).matcher("a b").matches()).isTrue();
  }

  @Test public void of_commentsGroupWithEscapedHash_renderedPatternStillMatchesHash() {
    String rendered = RegexPattern.of("(?x:a\\#b)").toString();
    assertThat(Pattern.compile(rendered).matcher("a#b").matches()).isTrue();
  }

  @Test public void of_commentsDirectiveWithEscapedSpace_renderedPatternStillMatchesSpace() {
    String rendered = RegexPattern.of("(?x)a\\ b").toString();
    assertThat(Pattern.compile(rendered).matcher("a b").matches()).isTrue();
  }

  @Test public void of_commentsDirectiveWithEscapedHash_renderedPatternStillMatchesHash() {
    String rendered = RegexPattern.of("(?x)a\\#b").toString();
    assertThat(Pattern.compile(rendered).matcher("a#b").matches()).isTrue();
  }

  @Test public void of_commentsGroupCharClassWithEscapedSpace_renderedPatternStillMatchesSpace() {
    String rendered = RegexPattern.of("(?x:[a\\ b])").toString();
    assertThat(Pattern.compile(rendered).matcher(" ").matches()).isTrue();
  }

  @Test public void of_commentsGroupCharClassWithEscapedHash_renderedPatternStillMatchesHash() {
    String rendered = RegexPattern.of("(?x:[a\\#b])").toString();
    assertThat(Pattern.compile(rendered).matcher("#").matches()).isTrue();
  }

  @Test public void of_literalSpaceOutsideFreeSpacing_roundTrips() {
    RegexPattern pattern = RegexPattern.of("a b");
    assertThat(RegexPattern.of(pattern.toString())).isEqualTo(pattern);
  }

  @Test public void of_literalHashOutsideFreeSpacing_roundTrips() {
    RegexPattern pattern = RegexPattern.of("a#b");
    assertThat(RegexPattern.of(pattern.toString())).isEqualTo(pattern);
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 5: free spacing is only supported at the start of the pattern.
  //
  // java.util.regex lets a standalone `(?x)` turn free spacing on anywhere, for the rest of the
  // enclosing group. Mug honors it only as the first thing in the pattern and rejects it anywhere
  // else; RegexParsers.standaloneDirective() explains why, and `(?x:...)` expresses the same thing
  // with its scope spelled out. A `_divergence` test asserts what Mug does where it knowingly
  // differs from java.util.regex, so it passing is working as intended.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_freeSpacingCombinedWithCaseInsensitive_ignoresWhitespace() {
    assertThat(RegexPattern.of("(?ix) a b").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 2, /* maxSize= */ 2));
  }

  @Test public void of_freeSpacingFlagListedLast_ignoresWhitespace() {
    assertThat(RegexPattern.of("(?xi) a b").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 2, /* maxSize= */ 2));
  }

  @Test public void of_freeSpacingWithTrailingWhitespace_ignoresWhitespace() {
    assertThat(RegexPattern.of("(?ix) a b ").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 2, /* maxSize= */ 2));
  }

  /** The reference behavior: java.util.regex honors `(?x)` after a literal. */
  @Test public void of_freeSpacingFlagAfterLeadingLiteral_javaIgnoresWhitespace() {
    assertThat(Pattern.compile("a(?x) b c").matcher("abc").matches()).isTrue();
  }

  @Test public void of_freeSpacingFlagAfterLeadingLiteral_rejected_divergence() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("a(?x) b c"));
    assertThat(e)
        .hasMessageThat()
        .contains("at 1:5: free spacing flag (x) is only supported at the start of the pattern");
  }

  /** The reference behavior: `(?x)` runs to the end of the enclosing group, crossing `|`. */
  @Test public void of_freeSpacingFlagInAlternationBranch_javaAppliesToLaterBranches() {
    assertThat(Pattern.compile("a|(?x) b|c d").matcher("cd").matches()).isTrue();
  }

  @Test public void of_freeSpacingFlagInAlternationBranch_rejected_divergence() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("a|(?x) b|c d"));
    assertThat(e)
        .hasMessageThat()
        .contains("at 1:6: free spacing flag (x) is only supported at the start of the pattern");
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 6: diagnostics.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_freeSpacingPrefix_errorPositionIsRelativeToOriginalInput() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?x)abc(("));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo(
            """
            at 1:10: expecting one of [subpattern, ), ?, ?!, ?<, ?<!, ?<=, ?=, ?>, ?P<], \
            encountered:
                (?x)abc((
                         ^
            """);
  }

  @Test public void of_descendingQuantifierRange_reportsParseErrorAtPosition() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("a{3,2}"));
    assertThat(e).hasMessageThat().contains("at 1:3:");
    assertThat(e).hasMessageThat().contains("max must be at least min");
  }

  @Test public void of_oversizedBackreferenceNumber_reportsParseErrorAtPosition() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\99999999999"));
    assertThat(e).hasMessageThat().contains("at 1:2:");
    assertThat(e).hasMessageThat().contains("number too large: 99999999999");
  }

  @Test public void of_oversizedHexCodePoint_reportsParseErrorAtPosition() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\x{FFFFFFFFFF}"));
    assertThat(e).hasMessageThat().contains("at 1:3:");
    assertThat(e).hasMessageThat().contains("number too large: FFFFFFFFFF");
  }

  @Test public void of_unrecognizedCharacterName_reportsParseErrorAtPosition() {
    ParseException e =
        assertThrows(ParseException.class, () -> RegexPattern.of("\\N{NO SUCH NAME}"));
    assertThat(e).hasMessageThat().contains("at 1:3:");
    assertThat(e).hasMessageThat().contains("NO SUCH NAME");
  }

  @Test public void of_oversizedQuantifierNumber_reportsParseErrorAtPosition() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("a{99999999999}"));
    assertThat(e).hasMessageThat().contains("at 1:3:");
    assertThat(e).hasMessageThat().contains("number too large: 99999999999");
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 7: `{0,}` is normalized to AtMost(Integer.MAX_VALUE) instead of AtLeast(0).
  // ---------------------------------------------------------------------------------------------

  @Test public void of_openEndedRangeFromZero_isAtLeastZero() {
    assertThat(RegexPattern.of("a{0,}")).isEqualTo(new Quantified(new Literal("a"), repeated()));
  }

  @Test public void of_openEndedRangeFromZero_toString() {
    assertThat(RegexPattern.of("a{0,}").toString()).isEqualTo("a*");
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 8: input that java.util.regex rejects is accepted.
  // ---------------------------------------------------------------------------------------------

  @Test public void charRange_descending_rejected() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new CharRange('z', 'a'));
    assertThat(e).hasMessageThat().contains("z-a");
  }

  @Test public void of_descendingCharRange_reportsParseErrorAtPosition() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[z-a]"));
    assertThat(e).hasMessageThat().contains("at 1:2:");
  }

  @Test public void of_emptyNegatedCharClass_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[^]"));
    assertThat(e).hasMessageThat().contains("at 1:4:");
    assertThat(e).hasMessageThat().contains("expecting <]>");
  }

  @Test public void of_caretAfterFirstPosition_isLiteral() {
    assertThat(RegexPattern.of("[a^b]"))
        .isEqualTo(anyOf(new LiteralChar('a'), new LiteralChar('^'), new LiteralChar('b')));
  }

  @Test public void of_escapedCaretInCharClass_isLiteral() {
    assertThat(RegexPattern.of("[\\^]")).isEqualTo(anyOf(new LiteralChar('^')));
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 13: a quantifier binds to an atom at most once. java.util.regex rejects `a+*` the same
  // way, but accepts a trailing repetition count and then discards it: `a*{2}` matches whatever
  // `a*` matches, not `(?:a*){2}`. Rather than model that, the grammar rejects the whole shape.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_starAfterPlus_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("a+*"));
    assertThat(e).hasMessageThat().contains("at 1:3:");
  }

  @Test public void of_repetitionCountAfterStar_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("a*{2}"));
    assertThat(e).hasMessageThat().contains("at 1:4:");
  }

  @Test public void of_reluctantQuantifier_stillAccepted() {
    assertThat(RegexPattern.of("a+?"))
        .isEqualTo(
            new Quantified(new Literal("a"), RegexPattern.Quantifier.atLeast(1).reluctant()));
  }

  @Test public void of_possessiveQuantifier_stillAccepted() {
    assertThat(RegexPattern.of("a*+"))
        .isEqualTo(new Quantified(new Literal("a"), repeated().possessive()));
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 9: backreference digits use maximal munch. java.util.regex instead stops at the
  // highest group seen so far, so `(a)\10` is group 1 followed by a literal `0` there. Reproducing
  // that needs parse-order state this grammar doesn't carry; the divergence is documented on
  // Backreference.Numbered and pinned here.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_backreferenceDigitsBeyondGroupCount_usesMaximalMunch() {
    assertThat(RegexPattern.of("(a)\\10"))
        .isEqualTo(sequence(new Group.Capturing(new Literal("a")), new Backreference.Numbered(10)));
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 8c: constraints that depend on the pattern as a whole are not enforced. This is
  // documented on RegexPattern.of and pinned here.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_duplicateGroupNames_accepted() {
    assertThat(RegexPattern.of("(?<a>x)(?<a>y)"))
        .isEqualTo(
            sequence(
                new Group.Named("a", new Literal("x")), new Group.Named("a", new Literal("y"))));
  }

  @Test public void of_undefinedNamedBackreference_accepted() {
    assertThat(RegexPattern.of("\\k<nope>")).isEqualTo(new Backreference.Named("nope"));
  }

  @Test public void of_forwardBackreference_accepted() {
    assertThat(RegexPattern.of("\\1(a)"))
        .isEqualTo(sequence(new Backreference.Numbered(1), new Group.Capturing(new Literal("a"))));
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 11: only the space character is excluded from a literal run, so free spacing mode
  // never sees a tab, newline or carriage return.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_freeSpacingGroupWithTab_ignoresTab() {
    assertThat(RegexPattern.of("(?x: a\tb )").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 2, /* maxSize= */ 2));
  }

  @Test public void of_freeSpacingGroupWithNewline_ignoresNewline() {
    assertThat(RegexPattern.of("(?x: a b\n)").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 2, /* maxSize= */ 2));
  }

  @Test public void of_freeSpacingGroupWithCarriageReturn_ignoresCarriageReturn() {
    assertThat(RegexPattern.of("(?x: a\rb )").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 2, /* maxSize= */ 2));
  }

  @Test public void of_freeSpacingDirectiveWithTab_ignoresTab() {
    assertThat(RegexPattern.of("(?ix) a\tb").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 2, /* maxSize= */ 2));
  }

  @Test public void of_freeSpacingGroupWithTab_renderedPatternMatchesWithoutTab() {
    String rendered = RegexPattern.of("(?x: a\tb )").toString();
    assertThat(Pattern.compile(rendered).matcher("ab").matches()).isTrue();
  }

  @Test public void of_tabOutsideFreeSpacing_staysLiteral() {
    assertThat(RegexPattern.of("a\tb")).isEqualTo(new Literal("a\tb"));
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 12: a standalone directive is a zero-width node, emitted as a sibling of the elements
  // it applies to, so it leaves the binding of `|` alone.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_directiveAfterLeadingLiteral_keepsAlternationStructure() {
    assertThat(RegexPattern.of("x(?i)a|b"))
        .isEqualTo(
            alternation(
                sequence(
                    new Literal("x"),
                    new ModifierDirective(List.of(ModifierFlag.CASE_INSENSITIVE), List.of()),
                    new Literal("a")),
                new Literal("b")));
  }

  @Test public void of_directiveAfterLeadingLiteral_shortestMatchIsTheLaterAlternative() {
    assertThat(RegexPattern.of("x(?i)a|b").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 1, /* maxSize= */ 2));
  }

  @Test public void of_directiveAfterLeadingLiteral_roundTrips() {
    assertThat(RegexPattern.of("x(?i)a|b").toString()).isEqualTo("x(?i)a|b");
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 14: a directive's flags are not recorded as reaching the alternatives after it.
  //
  // What diverges: for `x(?i)a|b`, java.util.regex compiles flags sequentially to the end of the
  // enclosing group, so the `i` reaches `b` and the pattern matches "B". Mug parses `b` into a
  // plain sibling branch that carries no flags, so a consumer reading the tree on its own sees a
  // case sensitive `b`. Only the recorded scope differs; the parse and the rendering both agree
  // with java.util.regex.
  //
  // Why it cannot be fixed in the tree: the directive is zero-width, so it lands inside the branch
  // it was written in, and the later branches are siblings of that branch rather than of the
  // directive. Nesting them under the directive instead is the only way a tree can say that the
  // flags reach them, and that is exactly the shape `|` forbids: it would read as `x` gating `b`,
  // which changes which strings match. Annotating the later branches with the active flags would
  // express the scope, at the cost of no longer rendering back to the source.
  //
  // Why the divergence is acceptable:
  //   - Nothing is erased. The directive is still in the tree, at the top level of the preceding
  //     branch, so a consumer that needs the flags can scan branches left to right and carry a
  //     still-active directive forward. This is a representation choice, not data loss.
  //   - Rendering is unaffected, as of_directiveAfterLeadingLiteral_roundTrips shows, so anything
  //     that parses and re-renders never observes the difference.
  //   - The alternative is worse. The nested shape misstates which strings match, which affects
  //     every consumer, whereas under-recorded flag scope only affects flag sensitive ones.
  //   - The blast radius is narrow: it takes a standalone directive plus a later `|` in the same
  //     group. Scoped `(?i:...)` groups, and a leading `(?i)` with no alternation, are both right.
  //
  // A `_divergence` test asserts what Mug does where it knowingly differs from java.util.regex, so
  // it passing is working as intended. Findings 5 and 9 are the others.
  // ---------------------------------------------------------------------------------------------

  /** The reference behavior: java.util.regex carries the flag across the `|`. */
  @Test public void of_directiveBeforeAlternation_javaAppliesFlagsToLaterAlternative() {
    assertThat(Pattern.compile("x(?i)a|b").matcher("B").matches()).isTrue();
  }

  /** What Mug records instead: the lifted branch is a bare literal, with no flags attached. */
  @Test public void of_directiveBeforeAlternation_laterAlternativeCarriesNoFlags_divergence() {
    Alternation alternation = (Alternation) RegexPattern.of("x(?i)a|b");
    assertThat(alternation.alternatives().getLast()).isEqualTo(new Literal("b"));
  }
}
