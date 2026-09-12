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
import com.google.common.labs.regex.RegexPattern.UnicodeProperty;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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
  private static final ModifierDirective FREE_SPACING =
      new ModifierDirective(List.of(ModifierFlag.COMMENTS), List.of());

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
  // Finding 9: backreference digits consume up to the count of capturing groups seen so far.
  // java.util.regex stops at the highest group seen so far, so `(a)\10` is group 1 followed by a
  // literal `0`.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_backreferenceDigitsBeyondGroupCount_stopsAtHighestGroupSeen() {
    assertThat(RegexPattern.of("(a)\\10"))
        .isEqualTo(
            sequence(
                new Group.Capturing(new Literal("a")),
                new Backreference.Numbered(1),
                new Literal("0")));
  }

  @Test public void of_backreferenceDigitsBeyondGroupCount_quantified() {
    assertThat(RegexPattern.of("(a)\\12*"))
        .isEqualTo(
            sequence(
                new Group.Capturing(new Literal("a")),
                new Backreference.Numbered(1),
                new Quantified(new Literal("2"), repeated())));
  }

  @Test public void of_backreferenceDigitsWithinGroupCount_keptAsNumbered() {
    StringBuilder pattern = new StringBuilder();
    for (int i = 1; i <= 12; i++) {
      pattern.append("(").append((char) ('a' + i - 1)).append(")");
    }
    pattern.append("\\12");
    RegexPattern parsed = RegexPattern.of(pattern.toString());
    assertThat(parsed).isInstanceOf(RegexPattern.Sequence.class);
    RegexPattern.Sequence seq = (RegexPattern.Sequence) parsed;
    assertThat(seq.elements().get(12)).isEqualTo(new Backreference.Numbered(12));
  }

  @Test public void of_backreferenceWithZeroGroups_takesFirstDigit() {
    assertThat(RegexPattern.of("\\12"))
        .isEqualTo(sequence(new Backreference.Numbered(1), new Literal("2")));
  }

  @Test public void of_backreferenceWithMultipleTrailingDigits_quantified() {
    assertThat(RegexPattern.of("(a)\\123*"))
        .isEqualTo(
            sequence(
                new Group.Capturing(new Literal("a")),
                new Backreference.Numbered(1),
                new Literal("2"),
                new Quantified(new Literal("3"), repeated())));
  }

  @Test public void of_backreferenceAfterNamedGroup_countsNamedGroup() {
    assertThat(RegexPattern.of("(?<g>a)\\10"))
        .isEqualTo(
            sequence(
                new Group.Named("g", new Literal("a")),
                new Backreference.Numbered(1),
                new Literal("0")));
  }

  @Test public void of_backreferenceSplit_mergesAdjacentTrailingLiterals() {
    assertThat(RegexPattern.of("(a)\\12b"))
        .isEqualTo(
            sequence(
                new Group.Capturing(new Literal("a")),
                new Backreference.Numbered(1),
                new Literal("2b")));
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

  // ---------------------------------------------------------------------------------------------
  // Finding 15: free spacing skips more than java.util.regex does.
  //
  // java.util.regex skips exactly six characters in free spacing mode: space, `\t`, `\n`, `\u000B`,
  // `\f` and `\r` (Pattern.isSpace). Mug uses Character::isWhitespace, which also covers the
  // information separators U+001C..U+001F and the Unicode space separators, so patterns that
  // java.util.regex reads as literal text silently lose characters here.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_freeSpacingSkipsVerticalTab() {
    assertThat(RegexPattern.of("(?x)a\u000Bb"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("ab")));
  }

  @Test public void of_freeSpacingSkipsFormFeed() {
    assertThat(RegexPattern.of("(?x)a\fb")).isEqualTo(sequence(FREE_SPACING, new Literal("ab")));
  }

  @Test public void of_freeSpacingKeepsFileSeparator() {
    assertThat(RegexPattern.of("(?x)a\u001Cb"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("a\u001Cb")));
  }

  @Test public void of_freeSpacingKeepsEmSpace() {
    assertThat(RegexPattern.of("(?x)a\u2003b"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("a\u2003b")));
  }

  @Test public void of_freeSpacingKeepsIdeographicSpace() {
    assertThat(RegexPattern.of("(?x)a\u3000b"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("a\u3000b")));
  }

  /** The reference behavior: an em space is literal text, not a skippable space. */
  @Test public void of_freeSpacingKeepsEmSpace_javaMatchesTheSpace() {
    assertThat(Pattern.compile("(?x)a\u2003b").matcher("a\u2003b").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 16: a `#` comment runs past every line terminator except `\n`.
  //
  // java.util.regex ends a comment at any of `\n`, `\r`, `\u0085`, `\u2028` and `\u2029`
  // (Pattern.isLineSeparator without UNIX_LINES). Only `\n` and `\r` are then skipped as
  // whitespace; the other three stay in the pattern as literal characters. Mug ends a comment at
  // `\n` alone, so everything up to the next `\n`, or to the end of the pattern, is swallowed.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_freeSpacingCommentEndsAtCarriageReturn() {
    assertThat(RegexPattern.of("(?x)a#c\rb")).isEqualTo(sequence(FREE_SPACING, new Literal("ab")));
  }

  @Test public void of_freeSpacingCommentEndsAtNextLine() {
    assertThat(RegexPattern.of("(?x)a#c\u0085b"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("a\u0085b")));
  }

  @Test public void of_freeSpacingCommentEndsAtLineSeparator() {
    assertThat(RegexPattern.of("(?x)a#c\u2028b"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("a\u2028b")));
  }

  @Test public void of_freeSpacingCommentEndsAtParagraphSeparator() {
    assertThat(RegexPattern.of("(?x)a#c\u2029b"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("a\u2029b")));
  }

  @Test public void of_freeSpacingCommentDoesNotEndAtVerticalTab() {
    assertThat(RegexPattern.of("(?x)a#c\u000Bb"))
        .isEqualTo(sequence(FREE_SPACING, new Literal("a")));
  }

  @Test public void of_freeSpacingCommentDoesNotEndAtFormFeed() {
    assertThat(RegexPattern.of("(?x)a#c\fb")).isEqualTo(sequence(FREE_SPACING, new Literal("a")));
  }

  /** The reference behavior: the comment stops at the carriage return, so `b` is matched. */
  @Test public void of_freeSpacingCommentEndsAtCarriageReturn_javaMatchesTheTail() {
    assertThat(Pattern.compile("(?x)a#c\rb").matcher("ab").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 17: `\cX` upper-cases the control letter before XOR-ing it.
  //
  // java.util.regex XORs the raw character with 64 (Pattern.c()), so `\ca` is U+0021 `!`. Perl
  // folds the letter first; Java does not, and Mug followed Perl.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_controlEscapeLowerCaseLetter_doesNotFoldTheLetter() {
    assertThat(RegexPattern.of("\\ca")).isEqualTo(new Literal("\u0021"));
  }

  @Test public void of_controlEscapeUpperCaseLetter_isTheControlCharacter() {
    assertThat(RegexPattern.of("\\cA")).isEqualTo(new Literal("\u0001"));
  }

  @Test public void of_controlEscapeNonLetter_xorsTheRawCharacter() {
    assertThat(RegexPattern.of("\\c@")).isEqualTo(new Literal("\u0000"));
  }

  /** The reference behavior: `\ca` is `!`, not U+0001. */
  @Test public void of_controlEscapeLowerCaseLetter_javaMatchesExclamationMark() {
    assertThat(Pattern.compile("\\ca").matcher("!").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 18: two hex escapes forming a surrogate pair are not paired before a quantifier.
  //
  // Each escape parses as its own atom, so the star in the test below quantifies the low surrogate
  // alone. Java quantifies the whole code point, so the pattern matches the empty string. The
  // literal spelling of the same code point already behaves correctly: it goes through a literal
  // run.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_surrogatePairEscapes_isOneLiteral() {
    assertThat(RegexPattern.of("\\uD83D\\uDE00")).isEqualTo(new Literal("\uD83D\uDE00"));
  }

  @Test public void of_starAfterSurrogatePairEscapes_quantifiesTheWholeCodePoint() {
    assertThat(RegexPattern.of("\\uD83D\\uDE00*"))
        .isEqualTo(new Quantified(new Literal("\uD83D\uDE00"), repeated()));
  }

  @Test public void of_starAfterSurrogatePairEscapes_canMatchEmpty() {
    assertThat(RegexPattern.of("\\uD83D\\uDE00*").metadata())
        .isEqualTo(new Metadata(/* minSize= */ 0, /* maxSize= */ Integer.MAX_VALUE));
  }

  @Test public void of_starAfterNonPairedEscapes_quantifiesTheLastEscapeOnly() {
    assertThat(RegexPattern.of("\\n\\r*"))
        .isEqualTo(sequence(new Literal("\n"), new Quantified(new Literal("\r"), repeated())));
  }

  /** The reference behavior: the star applies to the astral code point, so "" matches. */
  @Test public void of_starAfterSurrogatePairEscapes_javaMatchesEmpty() {
    assertThat(Pattern.compile("\\uD83D\\uDE00*").matcher("").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 19: a quoted character in a class can't be a range endpoint.
  //
  // Java unquotes the characters into the class body, so the one next to the `-` is an ordinary
  // endpoint, at either end. Mug parses the whole quote as a group of literal chars, leaving the
  // `-` a literal too.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_quotedCharBeforeHyphen_startsARange() {
    assertThat(RegexPattern.of("[\\Qa\\E-z]")).isEqualTo(anyOf(new CharRange('a', 'z')));
  }

  @Test public void of_manyQuotedCharsBeforeHyphen_onlyTheLastStartsARange() {
    assertThat(RegexPattern.of("[\\Qab\\E-z]"))
        .isEqualTo(anyOf(new LiteralChar('a'), new CharRange('b', 'z')));
  }

  @Test public void of_emptyQuoteBeforeHyphen_leavesTheHyphenLiteral() {
    assertThat(RegexPattern.of("[\\Q\\E-z]"))
        .isEqualTo(anyOf(new LiteralChar('-'), new LiteralChar('z')));
  }

  @Test public void of_quotedCharBeforeTrailingHyphen_leavesTheHyphenLiteral() {
    assertThat(RegexPattern.of("[\\Qa\\E-]"))
        .isEqualTo(anyOf(new LiteralChar('a'), new LiteralChar('-')));
  }

  @Test public void of_quotedCharAfterHyphen_endsARange() {
    assertThat(RegexPattern.of("[a-\\Qz\\E]")).isEqualTo(anyOf(new CharRange('a', 'z')));
  }

  @Test public void of_manyQuotedCharsAfterHyphen_onlyTheFirstEndsARange() {
    assertThat(RegexPattern.of("[a-\\Qzz\\E]"))
        .isEqualTo(anyOf(new CharRange('a', 'z'), new LiteralChar('z')));
  }

  @Test public void of_quotedCharsAtBothEnds_formARange() {
    assertThat(RegexPattern.of("[\\Qa\\E-\\Qz\\E]")).isEqualTo(anyOf(new CharRange('a', 'z')));
  }

  @Test public void of_emptyQuoteAfterHyphen_leavesTheHyphenLiteral() {
    assertThat(RegexPattern.of("[a-\\Q\\E]"))
        .isEqualTo(anyOf(new LiteralChar('a'), new LiteralChar('-')));
  }

  /** The reference behavior: the class is the range `a-z`, so `m` matches and `-` does not. */
  @Test public void of_quotedCharBeforeHyphen_javaMatchesTheRange() {
    assertThat(Pattern.compile("[\\Qa\\E-z]").matcher("m").matches()).isTrue();
  }

  /** The reference behavior: `-` is not a member of the range `a-z`. */
  @Test public void of_quotedCharBeforeHyphen_javaDoesNotMatchTheHyphen() {
    assertThat(Pattern.compile("[\\Qa\\E-z]").matcher("-").matches()).isFalse();
  }

  /** The reference behavior: a quoted character closes a range just as a written one does. */
  @Test public void of_quotedCharAfterHyphen_javaMatchesTheRange() {
    assertThat(Pattern.compile("[a-\\Qz\\E]").matcher("m").matches()).isTrue();
  }

  /** The reference behavior: only the first quoted character closes the range. */
  @Test public void of_manyQuotedCharsAfterHyphen_javaMatchesTheRange() {
    assertThat(Pattern.compile("[a-\\Qzz\\E]").matcher("m").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 20: `Integer.MAX_VALUE` doubles as the "unbounded" sentinel.
  //
  // A repetition count of exactly Integer.MAX_VALUE is a bounded count in Java, but Mug reads it as
  // `{n,}`, so `a{2147483647}` renders, and reparses, as an unbounded repetition.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_exactRepetitionOfMaxCount_rendersAnExactCount() {
    assertThat(RegexPattern.of("a{2147483647}").toString()).isEqualTo("a{2147483647}");
  }

  @Test public void quantifier_repeatedMaxCount_isNotUnbounded() {
    assertThat(repeated(Integer.MAX_VALUE).toString()).isEqualTo("{2147483647}");
  }

  @Test public void of_unboundedRepetitionFromMaxCount_staysUnbounded() {
    assertThat(RegexPattern.of("a{2147483647,}").toString()).isEqualTo("a{2147483647,}");
  }

  @Test public void of_boundedRepetitionUpToMaxCount_staysUnbounded() {
    assertThat(RegexPattern.of("a{2,2147483647}").toString()).isEqualTo("a{2,}");
  }

  /** The reference behavior: `{n}` is exactly n, so one repetition more does not match. */
  @Test public void of_exactRepetition_javaDoesNotMatchMore() {
    assertThat(Pattern.compile("a{2}").matcher("aaa").matches()).isFalse();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 21: a numbered backreference followed by a literal digit renders ambiguously.
  //
  // `Seq[(a), \1, "0"]` renders as `(a)\10`, which this parser reads back as group 10. The digit
  // has to be kept out of the group number.
  // ---------------------------------------------------------------------------------------------

  @Test public void sequence_backreferenceBeforeLiteralDigit_rendersTheDigitEscaped() {
    assertThat(
            sequence(
                    new Group.Capturing(new Literal("a")),
                    new Backreference.Numbered(1),
                    new Literal("0"))
                .toString())
        .isEqualTo("(a)\\1\\x30");
  }

  @Test public void sequence_backreferenceBeforeLiteralDigit_roundTrips() {
    RegexPattern pattern = sequence(
        new Group.Capturing(new Literal("a")), new Backreference.Numbered(1), new Literal("0"));
    assertThat(RegexPattern.of(pattern.toString())).isEqualTo(pattern);
  }

  @Test public void sequence_backreferenceBeforeMultiCharLiteral_escapesOnlyTheLeadingDigit() {
    assertThat(
            sequence(
                    new Group.Capturing(new Literal("a")),
                    new Backreference.Numbered(1),
                    new Literal("0z"))
                .toString())
        .isEqualTo("(a)\\1\\x30z");
  }

  @Test public void sequence_backreferenceBeforeNonDigit_rendersPlainly() {
    assertThat(
            sequence(
                    new Group.Capturing(new Literal("a")),
                    new Backreference.Numbered(1),
                    new Literal("z"))
                .toString())
        .isEqualTo("(a)\\1z");
  }

  /** The reference behavior: with one group, Java reads `(a)\10` as group 1 then a literal `0`. */
  @Test public void of_backreferenceThenDigit_javaStopsAtTheGroupCount() {
    assertThat(Pattern.compile("(a)\\10").matcher("aa0").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 22: a nested alternation renders without parentheses.
  //
  // `|` has the lowest precedence, so `Seq[a, Alt[b, c], d]` renders as `ab|cd`, a different
  // pattern. A leading modifier directive is the exception: Java scopes the flags to the end of the
  // enclosing group, so `(?i)a|b` means the same with or without the parentheses, and that shape is
  // what every leading `(?i)` with a top-level `|` parses to.
  // ---------------------------------------------------------------------------------------------

  @Test public void sequence_nestedAlternation_isParenthesized() {
    assertThat(
            sequence(
                    new Literal("a"), alternation(new Literal("b"), new Literal("c")),
                    new Literal("d"))
                .toString())
        .isEqualTo("a(?:b|c)d");
  }

  @Test public void sequence_trailingAlternation_isParenthesized() {
    assertThat(
            sequence(new Literal("a"), alternation(new Literal("b"), new Literal("c"))).toString())
        .isEqualTo("a(?:b|c)");
  }

  @Test public void sequence_nestedAlternation_roundTrips() {
    RegexPattern pattern = sequence(
        new Literal("a"), alternation(new Literal("b"), new Literal("c")), new Literal("d"));
    assertThat(RegexPattern.of(pattern.toString()).metadata()).isEqualTo(pattern.metadata());
  }

  @Test public void sequence_alternationAfterLeadingDirective_isNotParenthesized() {
    assertThat(RegexPattern.of("(?i)a|b").toString()).isEqualTo("(?i)a|b");
  }

  /** The reference behavior: the alternation is `ab` or `cd`, so `ab` alone matches. */
  @Test public void alternation_javaBindsLastAmongOperators() {
    assertThat(Pattern.compile("ab|cd").matcher("ab").matches()).isTrue();
  }

  /** The reference behavior: a leading `(?i)` covers the whole alternation that follows it. */
  @Test public void leadingDirective_javaAppliesAcrossTheAlternation() {
    assertThat(Pattern.compile("(?i)a|b").matcher("B").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 23: group names Java rejects are accepted.
  //
  // A group name is a Latin letter followed by Latin letters and digits. Mug accepted any word,
  // so `(?<a_b>x)` and `(?<1a>x)` parsed even though they can never compile.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_groupNameWithUnderscore_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<a_b>x)"));
    assertThat(e).hasMessageThat().contains("at 1:5:");
  }

  @Test public void of_groupNameStartingWithDigit_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("(?<1a>x)"));
    assertThat(e).hasMessageThat().contains("at 1:4:");
  }

  @Test public void of_backreferenceToNameWithUnderscore_rejected() {
    ParseException e =
        assertThrows(ParseException.class, () -> RegexPattern.of("(?<ab>x)\\k<a_b>"));
    assertThat(e).hasMessageThat().contains("at 1:13:");
  }

  @Test public void of_groupNameWithLettersAndDigits_accepted() {
    assertThat(RegexPattern.of("(?<A1>x)")).isEqualTo(new Group.Named("A1", new Literal("x")));
  }

  @Test public void of_backreferenceToNameWithLettersAndDigits_accepted() {
    assertThat(RegexPattern.of("(?<a1>x)\\k<a1>"))
        .isEqualTo(
            sequence(new Group.Named("a1", new Literal("x")), new Backreference.Named("a1")));
  }

  /** The reference behavior: an underscore ends the name, so the `>` is missing. */
  @Test public void of_groupNameWithUnderscore_javaRejects() {
    assertThrows(PatternSyntaxException.class, () -> javaCompile("(?<a_b>x)"));
  }

  /** The reference behavior: a group name must start with a Latin letter. */
  @Test public void of_groupNameStartingWithDigit_javaRejects() {
    assertThrows(PatternSyntaxException.class, () -> javaCompile("(?<1a>x)"));
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 24: a character class used as a range endpoint is accepted.
  //
  // `[a-\d]` has no single code point to close the range with, and Java rejects it. Mug read it as
  // the three members `a`, `-` and `\d`. Java only rejects it on the right of the `-`: `[\d-a]` is
  // the three members, and that stays accepted.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_predefinedClassAsRangeEnd_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-\\d]"));
    assertThat(e).hasMessageThat().contains("at 1:3: unexpected `character class as a range end`");
  }

  @Test public void of_propertyAsRangeEnd_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-\\p{L}]"));
    assertThat(e).hasMessageThat().contains("at 1:3: unexpected `character class as a range end`");
  }

  @Test public void of_negatedPropertyAsRangeEnd_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[a-\\P{L}]"));
    assertThat(e).hasMessageThat().contains("at 1:3: unexpected `character class as a range end`");
  }

  @Test public void of_quotedCharPredefinedClassAsRangeEnd_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[\\Qa\\E-\\d]"));
    assertThat(e).hasMessageThat().contains("at 1:7: unexpected `character class as a range end`");
  }

  @Test public void of_quotedCharPropertyAsRangeEnd_rejected() {
    ParseException e =
        assertThrows(ParseException.class, () -> RegexPattern.of("[\\Qa\\E-\\p{L}]"));
    assertThat(e).hasMessageThat().contains("at 1:7: unexpected `character class as a range end`");
  }

  @Test public void of_quotedTextPredefinedClassAsRangeEnd_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("[\\Qabc\\E-\\d]"));
    assertThat(e).hasMessageThat().contains("at 1:9: unexpected `character class as a range end`");
  }

  @Test public void of_predefinedClassAsRangeStart_accepted() {
    assertThat(RegexPattern.of("[\\d-a]"))
        .isEqualTo(
            anyOf(
                RegexPattern.PredefinedCharClass.DIGIT, new LiteralChar('-'),
                new LiteralChar('a')));
  }

  @Test public void of_nestedClassAfterHyphen_accepted() {
    assertThat(RegexPattern.of("[a-[b]]"))
        .isEqualTo(anyOf(new LiteralChar('a'), new LiteralChar('-'), anyOf(new LiteralChar('b'))));
  }

  /** A `-` that follows a closed range is a literal member, so no range opens after it. */
  @Test public void of_predefinedClassAfterClosedRange_accepted() {
    assertThat(RegexPattern.of("[a-z-\\d]"))
        .isEqualTo(
            anyOf(
                new CharRange('a', 'z'), new LiteralChar('-'),
                RegexPattern.PredefinedCharClass.DIGIT));
  }

  @Test public void of_propertyAfterClosedRange_accepted() {
    assertThat(RegexPattern.of("[a-z-\\p{L}]"))
        .isEqualTo(anyOf(new CharRange('a', 'z'), new LiteralChar('-'), new UnicodeProperty("L")));
  }

  /** The reference behavior: the class binds to nothing, it is just another member. */
  @Test public void of_predefinedClassAfterClosedRange_javaAccepts() {
    assertThat(Pattern.compile("[a-z-\\d]").matcher("-").matches()).isTrue();
  }

  @Test public void of_plainRange_stillAccepted() {
    assertThat(RegexPattern.of("[a-b]")).isEqualTo(anyOf(new CharRange('a', 'b')));
  }

  /** The reference behavior: a class can't close a range. */
  @Test public void of_predefinedClassAsRangeEnd_javaRejects() {
    assertThrows(PatternSyntaxException.class, () -> javaCompile("[a-\\d]"));
  }

  /** The reference behavior: on the left of the `-` it is just a member, so this compiles. */
  @Test public void of_predefinedClassAsRangeStart_javaAccepts() {
    assertThat(Pattern.compile("[\\d-a]").matcher("-").matches()).isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 25: a property name containing whitespace is accepted.
  //
  // No property name Java knows has whitespace in it, so `\p{ L }` is an unknown name and Java
  // rejects it. Mug read the spaces as part of the name. Under `(?x)` the spaces are skipped as
  // free spacing before the name is read, which is a separate divergence, not covered here.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_propertyNameWithLeadingSpace_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\p{ L}"));
    assertThat(e).hasMessageThat().contains("at 1:4: expecting <property name>");
  }

  @Test public void of_propertyNameWithTrailingSpace_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\p{L }"));
    assertThat(e).hasMessageThat().contains("at 1:5: expecting <}>");
  }

  @Test public void of_propertyNameWithInnerSpace_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("\\p{Is Lower}"));
    assertThat(e).hasMessageThat().contains("at 1:6: expecting <}>");
  }

  @Test public void of_propertyName_accepted() {
    assertThat(RegexPattern.of("\\p{L}")).isEqualTo(new UnicodeProperty("L"));
  }

  @Test public void of_propertyNameWithAssignment_accepted() {
    assertThat(RegexPattern.of("\\p{gc=Lu}")).isEqualTo(new UnicodeProperty("gc=Lu"));
  }

  /** The reference behavior: the spaces make it an unknown property name. */
  @Test public void of_propertyNameWithSpaces_javaRejects() {
    assertThrows(PatternSyntaxException.class, () -> javaCompile("\\p{ L }"));
  }

  // ---------------------------------------------------------------------------------------------
  // Finding 26: `{,n}` is read as a `{0,n}` quantifier.
  //
  // Java has no `{,n}` spelling: a repetition count must start with a number, in every position,
  // including at the start of a pattern where `{2}` itself is a literal. Mug quantified the
  // preceding atom with a bound Java never agreed to.
  // ---------------------------------------------------------------------------------------------

  @Test public void of_openEndedRepetition_accepted() {
    assertThat(RegexPattern.of("a{,3}"))
        .isEqualTo(new Quantified(new Literal("a"), RegexPattern.Quantifier.atMost(3)));
  }

  @Test public void of_openEndedRepetitionAtPatternStart_rejected() {
    ParseException e = assertThrows(ParseException.class, () -> RegexPattern.of("{,3}"));
    assertThat(e).hasMessageThat().contains("at 1:2: unexpected `repetition count`");
  }

  @Test public void of_openEndedRepetitionAfterGroup_accepted() {
    assertThat(RegexPattern.of("(abc){,4}"))
        .isEqualTo(
            new Quantified(
                new Group.Capturing(new Literal("abc")), RegexPattern.Quantifier.atMost(4)));
  }

  @Test public void of_boundedRepetition_stillAccepted() {
    assertThat(RegexPattern.of("a{2,3}"))
        .isEqualTo(new Quantified(new Literal("a"), repeated(2, 3)));
  }

  @Test public void of_openEndedRepetition_javaRejects() {
    assertThrows(PatternSyntaxException.class, () -> javaCompile("a{,3}"));
  }

  /** The reference behavior: not even a leading `{,n}`, though a leading `{n}` is a literal. */
  @Test public void of_openEndedRepetitionAtPatternStart_javaRejects() {
    assertThrows(PatternSyntaxException.class, () -> javaCompile("{,3}"));
  }

  /**
   * A comma with no count after it is not a repetition, so the brace stays literal. Java rejects
   * every `{` that follows an atom without starting a count; Mug only rejects the ones it would
   * otherwise mis-read as a quantifier.
   */
  @Test public void of_braceWithCommaButNoCount_acceptedAsLiteral_divergence() {
    assertThat(RegexPattern.of("a{,}")).isEqualTo(new Literal("a{,}"));
    assertThrows(PatternSyntaxException.class, () -> javaCompile("a{,}"));
  }

  /**
   * Compiles {@code regex} with {@code java.util.regex}. Reference tests for patterns Java rejects
   * go through here because Error Prone flags an invalid constant passed to {@code Pattern.compile}
   * at the call site.
   */
  private static Pattern javaCompile(String regex) {
    return Pattern.compile(regex);
  }
}
