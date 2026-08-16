package com.google.mu.errorprone.regex;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parsers;
import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ReDosTest {

  @Test public void checkRedosVulnerability_nestedQuantifiers_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("(a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to exponential backtracking (ReDoS): '(a+)+' contains"
                + " nested quantifiers on 'a+'\n"
                + "  attack payload: \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"\n"
                + "  consider: 'a+'");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a+");
  }

  @Test public void checkRedosVulnerability_overlappingAlternation_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("(a|a)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to exponential backtracking (ReDoS): '(a|a)+'"
                + " contains overlapping alternation branches 'a|a'\n"
                + "  attack payload: \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"");
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_nullablePattern_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("(a?)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to exponential backtracking (ReDoS): '(a?)*' contains"
                + " unbounded repetition of nullable sub-pattern '(a?)'\n"
                + "  attack payload: \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"\n"
                + "  consider: 'a*'");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a*");
  }

  @Test public void
      checkRedosVulnerability_disjointClassesWithInnerQuantifier_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("([a-z]+|[0-9]+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .contains(
            "contains nested quantifiers on '[a-z]+'\n"
                + "  attack payload: \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void checkRedosVulnerability_safePattern_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[a-zA-Z0-9]+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_possessiveNestedQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a++)++");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([0-9]{1,3}\\.){1,3}[0-9]{1,3}");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointAlternation_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(ab|ac)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_disjointAlternationWithCommonPrefixAndDisjointClasses_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a[b-c]|a[d-e])*");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_disjointAlternationWithMultiCharCommonPrefix_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(prefix_foo|prefix_bar)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_delimitedQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a+b)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_nestedQuantifiers_starStar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a*)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a*");
  }

  @Test public void
      checkRedosVulnerability_nestedQuantifiers_starPlus_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a*");
  }

  @Test public void
      checkRedosVulnerability_nestedQuantifiers_wordCharOptional_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([a-zA-Z0-9]+_?)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationInQuantifier_subsets_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a|aa)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationInQuantifier_overlappingClasses_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([a-z]+|[a-d]+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void checkRedosVulnerability_linearSequence_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+b");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointClassesAlternationInQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([a-z]|[0-9])+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedQuantifiers_ipv4_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([0-9]{1,3}\\.){1,3}[0-9]{1,3}");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedQuantifiers_macAddress_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([0-9a-fA-F]{2}:){1,5}[0-9a-fA-F]{2}");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_possessiveQuantifier_outer_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a+)++");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_possessiveQuantifier_inner_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a++)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointSuffixAlternation_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a|ab)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_crossingAlternationCycles_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(ab|ba)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_singleCharRegex_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("abc");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_identicalBranchesWithoutOuterLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+|a+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_identicalBranchesWithOuterLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+|a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void
      checkRedosVulnerability_reluctantNestedQuantifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+?)+?");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a+");
  }

  @Test public void
      checkRedosVulnerability_anchoredNestedQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^(a+)+$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_repeatedGroupWithoutInnerQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((a))+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_consecutiveIdenticalQuantifiers_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("a+a+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to polynomial backtracking (PDA): 'a+a+' contains"
                + " consecutive overlapping quantifiers on 'a+' and 'a+'\n"
                + "  attack payload: \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"\n"
                + "  consider: 'a{2,}'");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a{2,}");
  }

  @Test public void checkPolynomialBacktracking_digitFollowedByWordChar_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("\\d+\\w+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("contains consecutive overlapping quantifiers on '\\d+' and '\\w+'");
    assertThat(thrown).hasMessageThat().contains("consider: '\\d++\\w+'");
    assertThat(thrown).hasMessageThat()
        .contains("caveat: Possessive quantifier '\\d++' prevents backtracking");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("\\d++\\w+");
  }

  @Test public void
      checkPolynomialBacktracking_overlappingCharacterClasses_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("[0-9]+[0-9a-z]+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("contains consecutive overlapping quantifiers on '[0-9]+' and '[0-9a-z]+'");
    assertThat(thrown).hasMessageThat().contains("consider: '[0-9]++[0-9a-z]+'");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("[0-9]++[0-9a-z]+");
  }

  @Test public void
      checkRedosVulnerability_prefixGatedNestedQuantifier_attackPayloadIncludesPrefix() {
    RegexPattern pattern = RegexPattern.of("prefix_(a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("attack payload: \"prefix_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"");
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkPolynomialBacktracking_prefixGatedOverlappingQuantifiers_attackPayloadIncludesPrefix() {
    RegexPattern pattern = RegexPattern.of("prefix_\\d+\\w+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("attack payload: \"prefix_000000000000000000000000000000!\"");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("prefix_\\d++\\w+");
  }

  @Test public void suggestRedosRewrite_nestedQuantifier_suggestsFlattened() {
    assertThat(ReDos.suggestRedosRewrite(RegexPattern.of("(a+)+"))).hasValue("a+");
  }

  @Test public void suggestRedosRewrite_nullableRepeated_suggestsNonNullable() {
    assertThat(ReDos.suggestRedosRewrite(RegexPattern.of("(a*)+"))).hasValue("a*");
  }

  @Test public void suggestRedosRewrite_unrecognizedPattern_returnsEmpty() {
    assertThat(ReDos.suggestRedosRewrite(RegexPattern.of("(a|b)+"))).isEmpty();
  }

  @Test public void suggestPolynomialRewrite_consecutiveIdenticalPlusQuantifiers_mergesToRange() {
    assertThat(ReDos.suggestPolynomialRewrite(RegexPattern.of("a+a+"))).hasValue("a{2,}");
  }

  @Test public void suggestPolynomialRewrite_consecutiveIdenticalStarQuantifiers_mergesToStar() {
    assertThat(ReDos.suggestPolynomialRewrite(RegexPattern.of("a*a*"))).hasValue("a*");
  }

  @Test public void
      suggestPolynomialRewrite_consecutiveIdenticalDigitPlusQuantifiers_mergesToRange() {
    assertThat(ReDos.suggestPolynomialRewrite(RegexPattern.of("\\d+\\d+"))).hasValue("\\d{2,}");
  }

  @Test public void suggestPolynomialRewrite_overlappingQuantifiers_suggestsPossessive() {
    assertThat(ReDos.suggestPolynomialRewrite(RegexPattern.of("\\d+\\w+"))).hasValue("\\d++\\w+");
  }

  @Test public void suggestPolynomialRewrite_disjointQuantifiers_returnsEmpty() {
    assertThat(ReDos.suggestPolynomialRewrite(RegexPattern.of("a+b+"))).isEmpty();
  }

  @Test public void checkPolynomialBacktracking_threeIdenticalQuantifiers_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("a*a*a*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a*a*");
  }

  @Test public void
      checkPolynomialBacktracking_dotStarWithInterveningLiteral_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of(".*a.*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly(
            "new StringFormat(\"{left}a{right}\")", "Substring.first('a').split(input)")
        .inOrder();
  }

  @Test public void checkPolynomialBacktracking_disjointQuantifiers_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+b+");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_disjointCharacterClasses_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[0-9]+[a-z]+");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_adjacentQuantifiersWithCommonPrefixAndDisjointSuffix_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(ab)+(ac)+");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_possessiveFirstQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a++a+");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_linearSequence_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+b");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkRedosVulnerability_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> ReDos.checkRedosVulnerability(null));
  }

  @Test public void checkPolynomialBacktracking_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> ReDos.checkPolynomialBacktracking(null));
  }

  @Test public void suggestRedosRewrite_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> ReDos.suggestRedosRewrite(null));
  }

  @Test public void suggestPolynomialRewrite_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> ReDos.suggestPolynomialRewrite(null));
  }

  // --- New Polynomial Backtracking (PDA) Cases ---

  @Test public void
      checkPolynomialBacktracking_digitFollowedByDotStarAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("\\d+.*\\d+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("\\d++.*\\d+");
  }

  @Test public void
      checkPolynomialBacktracking_wordCharFollowedByDotStarAndWordChar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("\\w+.*\\w+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("\\w++.*\\w+");
  }

  @Test public void checkPolynomialBacktracking_adjacentDotStars_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(".*.*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly(".*");
  }

  @Test public void
      checkPolynomialBacktracking_overlappingCharClasses_lettersAndAlphanumeric_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("[a-z]+[a-z0-9]+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("[a-z]++[a-z0-9]+");
  }

  @Test public void
      checkPolynomialBacktracking_optionalInterveningChar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("a+b?a+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a++b?a+");
  }

  @Test public void
      checkPolynomialBacktracking_repeatedTrailingQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("ab*b*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkPolynomialBacktracking_anchoredOverlappingQuantifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^a*a*$");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("^a*$");
  }

  @Test public void
      checkPolynomialBacktracking_overlappingQuantifiersWithTrailingLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("a+a+b");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a{2,}b");
  }

  @Test public void
      checkPolynomialBacktracking_dotStarEqualsDotStar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(".*=.*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly(
            "new StringFormat(\"{left}={right}\")", "Substring.first('=').split(input)")
        .inOrder();
  }

  @Test public void checkPolynomialBacktracking_exponentialNestedQuantifiers_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a+)+");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_exponentialStarInsideStar_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([a-z]+)*");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_exponentialOptionalPrefixSequenceLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a?a)*");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_commaSeparatedDigits_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("\\d+,\\d+");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_colonSeparatedClasses_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[a-z]+:[0-9]+");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_bothPossessive_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a++a++");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  // --- New Exponential ReDoS (EDA) Cases ---

  @Test public void
      checkRedosVulnerability_tripleNestedQuantifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((a+)+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("(a+)+");
  }

  @Test public void
      checkRedosVulnerability_anchoredWordCharOuterQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^([a-zA-Z0-9]+)+$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithInnerTrailingQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+b*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_namedGroupNestedQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(?<name>[a-z]+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("[a-z]+");
  }

  @Test public void
      checkRedosVulnerability_overlappingPredefinedClasses_wordAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\w|\\d)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingDigitAndAlphanumericAlternation_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([0-9]|[a-z0-9])+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationPrefixed_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(b|a?b)*c");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationOptionalSuffix_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a|aa?)*b");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationSubsetRanges_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([a-z]|[d-h])*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationComplementRanges_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^a-z]|[^0-9])*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationPredefinedAndCharClass_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\d|[0-9])*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_identicalPredefinedBranches_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\s|\\s)*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingWordCharAndLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\w|G)*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingWordAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\d|\\w)*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingDigitAndDigitLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\d|5)*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_identicalFormFeed_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\f|[\\f])*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingNonWordAndNonDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\W|\\D)*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingNonSpaceAndWord_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\S|\\w)*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingSequenceAndPredefined_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((1s|[\\da-z])*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingZeroAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((0|[\\d])*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_nestedDigitQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([\\d]+)*)\"");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_threeAlternationBranchesInLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+|b+|c+)*c");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void
      checkRedosVulnerability_nestedSequenceLoopWithOptional_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(((a+a?)*)+b+)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithTrailingLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)+bbbb");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithTrailingLiteralAnchored_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)+aaaaa$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_nestedNewlines_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\n+)+\\n\\n$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassRepeated_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([^X]+)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoopWithBranch_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*($|[^X]c)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_sequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((ab)+)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_whitespaceAndDotStarLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([\\n\\s]+)*(.)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_overlappingLinebreaks_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\r\\n|\\r|\\n)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_literalOrDotLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a|.)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_negatedQuotesLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([^\"']+)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("[^\"']*");
  }

  @Test public void
      checkRedosVulnerability_digitWithOptionalSuffixLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\d+(X\\d+)?)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_digitWithOptionalSuffixStarLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([0-9]+(X[0-9]*)?)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoopWithSameBranch_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*($|[^X]b)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_sequenceLoopWithTrailingSequence_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((ab)+)*ababab");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_boundedInnerQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a{1,15})+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a+");
  }

  @Test public void
      checkRedosVulnerability_optionalPrefixSequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a?a)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_escapedDotSequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\\\?.)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_reluctantDotStarInBracketLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\[.*?\\])*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_reluctantDotPlusInBracketLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\[.+?\\])*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_nestedQuoteQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\"+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("\"+");
  }

  @Test public void checkRedosVulnerability_codeqlMarkdownRule_throwsIllegalArgumentException() {
    RegexPattern pattern =
        RegexPattern.of("^\\b_((?:__|[\\s\\S])+?)_\\b|^\\*((?:\\*\\*|[\\s\\S])+?)\\*(?!\\*)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_codeqlQuotedStringsOrParens_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(
        "^(?:\\s+(?:\"(?:[^\"\\\\]|\\\\\\\\|\\\\.)+\"|'(?:[^'\\\\]|\\\\\\\\|\\\\.)+'|\\((?:[^)\\\\]|\\\\\\\\|\\\\.)+\\)))?");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void checkRedosVulnerability_codeqlTableRows_throwsIllegalArgumentException() {
    RegexPattern pattern =
        RegexPattern.of("^ *(\\S.*\\|.*)\\n *([-:]+ *\\|[-| :]*)\\n((?:.*\\|.*(?:\\n|$))*)a");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_codeqlBracketsOrComments_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^([\\s\\[\\{\\(]|#.*)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void checkRedosVulnerability_codeqlPropertyAccess_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(
        "^[\\_$a-z][\\_$a-z0-9]*(\\[.*?\\])*(\\.[\\_$a-z][\\_$a-z0-9]*(\\[.*?\\])*)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_codeqlEmailComplex_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(
        "^([a-zA-Z0-9])(([\\\\-.]|[_]+)?([a-zA-Z0-9]+))*(@){1}[a-z0-9]+[.]{1}(([a-z]{2,3})|([a-z]{2,3}[.]{1}[a-z]{2,3}))$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void
      checkRedosVulnerability_codeqlDottedIdentifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^(([a-z])+.)+[A-Z]([a-z])+$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_codeqlCssSelectors_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([\\w#:.~>+()\\s-]+|\\*|\\[.*?\\])+)\\s*(,|$)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void checkRedosVulnerability_codeqlEscapedQuotes_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\"|')(\\\\?.)*?\\1");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  // --- New Safe Patterns ---

  @Test public void checkRedosVulnerability_emailPattern_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_markdownBoldItalic_doesNotThrow() {
    RegexPattern pattern =
        RegexPattern.of("^\\b_((?:__|[^_])+?)_\\b|^\\*((?:\\*\\*|[^*])+?)\\*(?!\\*)");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointCharOrNegatedCharLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((a|[^a])*)\"");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointSpaceOrDigitLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((\\s|\\d)*)\"");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_escapedStringLiteralLoop_doesNotThrow() {
    RegexPattern pattern =
        RegexPattern.of("\"((?:\\\\[\\x00-\\x7f]|[^\\x00-\\x08\\x0a-\\x1f\\x7f\"\\\\])*)\"");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaDelimitedRepeatedGroup_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((0|[1-9][0-9]*),)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaDelimitedNegatedChars_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([^,]+,)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_semicolonDelimitedDigits_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(\\d+;)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_alternatingLetterAndDigitsLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([a-z]+[0-9]+)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_colonSeparatedClasses_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[a-z]+:[0-9]+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaSeparatedDigits_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("\\d+,\\d+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedOuterAndInnerQuantifiers_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a{1,3}){1,3}");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_zeroOrPositiveInteger_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(0|[1-9][0-9]*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void checkRedosVulnerability_possessiveInnerPositiveInteger_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(0|[1-9][0-9]*+)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_largeRegexWithFiftyTransitions_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of(
        "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|0|1|2|3|4|5|6|7|8|9|A|B|C|D|E|F|G|H|I|J|K|L|M|N)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_throwsVulnerableRegexExceptionWithStructuredDetails() {
    RegexPattern pattern = RegexPattern.of("(a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getPattern()).isEqualTo(pattern);
    assertThat(thrown.getAttackPayload()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a+");
  }

  @Test public void
      checkPolynomialBacktracking_throwsVulnerableRegexExceptionWithStructuredDetails() {
    RegexPattern pattern = RegexPattern.of("\\d+\\w+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getPattern()).isEqualTo(pattern);
    assertThat(thrown.getAttackPayload()).isEqualTo("000000000000000000000000000000!");
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("\\d++\\w+");
  }

  @Test public void checkPolynomialBacktracking_delimitedWildcards_suggestsStringFormat() {
    RegexPattern pattern = RegexPattern.of(".*:.*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly(
            "new StringFormat(\"{left}:{right}\")", "Substring.first(':').split(input)")
        .inOrder();
  }

  @Test public void checkRedosVulnerability_structuredNumberGrammar_suggestsParsers() {
    RegexPattern pattern = RegexPattern.of("(0|[1-9][0-9]*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("Parsers.integer().repeatedly()");
  }

  @Test public void
      getSuggestions_strictlyEquivalentRegexSuggestion_isStrictlyEquivalentIsTrueAndNoCaveats() {
    RegexPattern pattern = RegexPattern.of("(a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    Suggestion suggestion = thrown.getSuggestions().get(0);
    assertThat(suggestion).isInstanceOf(Suggestion.RegexSuggestion.class);
    assertThat(suggestion.isStrictlyEquivalent()).isTrue();
    assertThat(suggestion.caveats()).isEmpty();
    assertThat(suggestion.replacement()).isEqualTo("a+");
    assertThat(suggestion.toString()).isEqualTo("a+");
  }

  @Test public void
      getSuggestions_possessiveRegexSuggestion_isStrictlyEquivalentIsFalseAndHasCaveats() {
    RegexPattern pattern = RegexPattern.of("\\d+\\w+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    Suggestion suggestion = thrown.getSuggestions().get(0);
    assertThat(suggestion).isInstanceOf(Suggestion.RegexSuggestion.class);
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).isNotEmpty();
    assertThat(suggestion.replacement()).isEqualTo("\\d++\\w+");
    assertThat(suggestion.toString()).isEqualTo("\\d++\\w+");
  }

  @Test public void getSuggestions_stringFormatSuggestion_hasFormatAndReplacementAndCaveats() {
    RegexPattern pattern = RegexPattern.of(".*:.*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    Suggestion suggestion = thrown.getSuggestions().get(0);
    assertThat(suggestion).isInstanceOf(Suggestion.StringFormatSuggestion.class);
    Suggestion.StringFormatSuggestion sf = (Suggestion.StringFormatSuggestion) suggestion;
    assertThat(sf.format()).isEqualTo("{left}:{right}");
    assertThat(sf.replacement()).isEqualTo("new StringFormat(\"{left}:{right}\")");
    assertThat(sf.toString()).isEqualTo("new StringFormat(\"{left}:{right}\")");
    assertThat(sf.isStrictlyEquivalent()).isFalse();
    assertThat(sf.caveats()).isNotEmpty();
  }

  @Test public void
      checkPolynomialBacktracking_delimitedWildcards_suggestsStringFormatAndSubstringInOrder() {
    RegexPattern pattern = RegexPattern.of(".*:.*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestions()).hasSize(2);
    assertThat(thrown.getSuggestions().get(0))
        .isInstanceOf(Suggestion.StringFormatSuggestion.class);
    assertThat(thrown.getSuggestions().get(1)).isInstanceOf(Suggestion.SubstringSuggestion.class);
    Suggestion.SubstringSuggestion ss =
        (Suggestion.SubstringSuggestion) thrown.getSuggestions().get(1);
    assertThat(ss.replacement()).isEqualTo("Substring.first(':').split(input)");
    assertThat(ss.toString()).isEqualTo("Substring.first(':').split(input)");
    assertThat(ss.isStrictlyEquivalent()).isFalse();
    assertThat(ss.caveats()).containsExactly(
            "Substring splits at the first occurrence of the delimiter");
  }

  @Test public void
      checkPolynomialBacktracking_bracketEnclosedWildcards_suggestsSubstringBetween() {
    RegexPattern pattern = RegexPattern.of(".*\\[.*\\].*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestions())
        .contains(
            new Suggestion.SubstringSuggestion(
                /* replacement= */ "Substring.between(\"[\", \"]\").from(input)",
                "Substring.between extracts the first matching enclosed range"));
  }

  @Test public void getSuggestions_parserSuggestion_hasParserExpressionAndCaveats() {
    RegexPattern pattern = RegexPattern.of("(0|[1-9][0-9]*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    Suggestion suggestion = thrown.getSuggestions().get(0);
    assertThat(suggestion).isInstanceOf(Suggestion.ParserSuggestion.class);
    Suggestion.ParserSuggestion ps = (Suggestion.ParserSuggestion) suggestion;
    assertThat(ps.replacement()).isEqualTo("Parsers.integer().repeatedly()");
    assertThat(ps.toString()).isEqualTo("Parsers.integer().repeatedly()");
    assertThat(ps.isStrictlyEquivalent()).isFalse();
    assertThat(ps.caveats()).isNotEmpty();
  }

  @Test public void suggestion_substringSuggestion_instantiationAndAccessors() {
    Suggestion.SubstringSuggestion ss = new Suggestion.SubstringSuggestion(
        /* replacement= */ "Substring.first(':').split(input)", "Splits at the first match");
    assertThat(ss.replacement()).isEqualTo("Substring.first(':').split(input)");
    assertThat(ss.isStrictlyEquivalent()).isFalse();
    assertThat(ss.caveats()).containsExactly("Splits at the first match");
    assertThat(ss.toString()).isEqualTo("Substring.first(':').split(input)");
  }

  @Test public void suggestedAlternative_nestedQuantifiersPlus_matchesEquivalentInput() {
    Pattern original = Pattern.compile("(a+)+");
    Pattern suggestion = Pattern.compile("a+");
    assertThat(suggestion.matcher("aaaa").matches()).isEqualTo(original.matcher("aaaa").matches());
  }

  @Test public void suggestedAlternative_nestedQuantifiersPlus_rejectsNonMatchingInput() {
    Pattern original = Pattern.compile("(a+)+");
    Pattern suggestion = Pattern.compile("a+");
    assertThat(suggestion.matcher("b").matches()).isEqualTo(original.matcher("b").matches());
  }

  @Test public void suggestedAlternative_possessiveQuantifier_matchesEquivalentDisjointTokens() {
    Pattern original = Pattern.compile("\\d+\\w+");
    Pattern suggestion = Pattern.compile("\\d++\\w+");
    assertThat(suggestion.matcher("123abc").matches())
        .isEqualTo(original.matcher("123abc").matches());
  }

  @Test public void suggestedAlternative_possessiveQuantifier_rejectsNonMatchingInput() {
    Pattern original = Pattern.compile("\\d+\\w+");
    Pattern suggestion = Pattern.compile("\\d++\\w+");
    assertThat(suggestion.matcher("abc").matches()).isEqualTo(original.matcher("abc").matches());
  }

  @Test public void suggestedAlternative_mergedPlusQuantifiers_matchesEquivalentInput() {
    Pattern original = Pattern.compile("a+a+");
    Pattern suggestion = Pattern.compile("a{2,}");
    assertThat(suggestion.matcher("aaaa").matches()).isEqualTo(original.matcher("aaaa").matches());
  }

  @Test public void suggestedAlternative_mergedPlusQuantifiers_rejectsUnderMinLengthInput() {
    Pattern original = Pattern.compile("a+a+");
    Pattern suggestion = Pattern.compile("a{2,}");
    assertThat(suggestion.matcher("a").matches()).isEqualTo(original.matcher("a").matches());
  }

  @Test public void suggestedAlternative_mergedStarQuantifiers_matchesEquivalentInput() {
    Pattern original = Pattern.compile("a*a*");
    Pattern suggestion = Pattern.compile("a*");
    assertThat(suggestion.matcher("aaaa").matches()).isEqualTo(original.matcher("aaaa").matches());
  }

  @Test public void suggestedAlternative_mergedStarQuantifiers_matchesEmptyInput() {
    Pattern original = Pattern.compile("a*a*");
    Pattern suggestion = Pattern.compile("a*");
    assertThat(suggestion.matcher("").matches()).isEqualTo(original.matcher("").matches());
  }

  @Test public void suggestedAlternative_mergedDigitPlusQuantifiers_matchesEquivalentInput() {
    Pattern original = Pattern.compile("\\d+\\d+");
    Pattern suggestion = Pattern.compile("\\d{2,}");
    assertThat(suggestion.matcher("12345").matches())
        .isEqualTo(original.matcher("12345").matches());
  }

  @Test public void suggestedAlternative_mergedDigitPlusQuantifiers_rejectsSingleDigitInput() {
    Pattern original = Pattern.compile("\\d+\\d+");
    Pattern suggestion = Pattern.compile("\\d{2,}");
    assertThat(suggestion.matcher("1").matches()).isEqualTo(original.matcher("1").matches());
  }

  @Test public void suggestedAlternative_stringFormat_extractsIdenticalKeyAndValue() {
    Matcher matcher = Pattern.compile("^(.*?):(.*)$").matcher("user:123");
    assertThat(matcher.matches()).isTrue();
    List<String> regexExtracted = List.of(matcher.group(1), matcher.group(2));
    List<String> formatExtracted =
        new StringFormat("{left}:{right}").parse("user:123", (l, r) -> List.of(l, r)).orElseThrow();
    assertThat(formatExtracted).isEqualTo(regexExtracted);
  }

  @Test public void suggestedAlternative_substringFirstSplit_extractsIdenticalKeyAndValue() {
    Matcher matcher = Pattern.compile("^(.*?):(.*)$").matcher("user:123");
    assertThat(matcher.matches()).isTrue();
    List<String> regexExtracted = List.of(matcher.group(1), matcher.group(2));
    List<String> substringExtracted =
        Substring.first(':').split("user:123", (l, r) -> List.of(l, r)).orElseThrow();
    assertThat(substringExtracted).isEqualTo(regexExtracted);
  }

  @Test public void suggestedAlternative_substringBetween_extractsIdenticalEnclosedContent() {
    Matcher matcher = Pattern.compile(".*?\\[(.*?)\\].*").matcher("prefix[payload]suffix");
    assertThat(matcher.matches()).isTrue();
    String regexExtracted = matcher.group(1);
    String substringExtracted =
        Substring.between("[", "]").from("prefix[payload]suffix").orElseThrow();
    assertThat(substringExtracted).isEqualTo(regexExtracted);
  }

  @Test public void suggestedAlternative_parsersIntegerRepeatedly_parsesMatchingDigits() {
    assertThat(Pattern.compile("(0|[1-9][0-9]*)+").matcher("12345").matches()).isTrue();
    assertThat(Parsers.UNSIGNED_INTEGER.atLeastOnce().parse("12345")).containsExactly("12345");
  }
}
