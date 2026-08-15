package com.google.mu.errorprone.regex;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.regex.RegexPattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RedosTest {

  @Test public void checkRedosVulnerability_nestedQuantifiers_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("(a+)+");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to exponential backtracking (ReDoS): '(a+)+' contains"
                + " nested quantifiers on 'a+' (attack payload:"
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\") (suggested rewrite: 'a+')");
  }

  @Test public void checkRedosVulnerability_overlappingAlternation_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("(a|a)+");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to exponential backtracking (ReDoS): '(a|a)+'"
                + " contains overlapping alternation branches 'a|a' (attack payload:"
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\")");
  }

  @Test public void checkRedosVulnerability_nullablePattern_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("(a?)*");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to exponential backtracking (ReDoS): '(a?)*' contains"
                + " unbounded repetition of nullable sub-pattern '(a?)' (attack payload:"
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\") (suggested rewrite: 'a*')");
  }

  @Test public void
      checkRedosVulnerability_disjointClassesWithInnerQuantifier_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("([a-z]+|[0-9]+)+");
    IllegalArgumentException thrown =
        assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .contains(
            "contains nested quantifiers on '[a-z]+' (attack payload:"
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\")");
  }

  @Test public void checkRedosVulnerability_safePattern_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[a-zA-Z0-9]+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_possessiveNestedQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a++)++");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([0-9]{1,3}\\.){1,3}[0-9]{1,3}");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointAlternation_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(ab|ac)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_delimitedQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a+b)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_nestedQuantifiers_starStar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a*)*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_nestedQuantifiers_starPlus_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_nestedQuantifiers_wordCharOptional_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([a-zA-Z0-9]+_?)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationInQuantifier_subsets_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a|aa)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationInQuantifier_overlappingClasses_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([a-z]+|[a-d]+)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_linearSequence_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+b");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointClassesAlternationInQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([a-z]|[0-9])+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedQuantifiers_ipv4_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([0-9]{1,3}\\.){1,3}[0-9]{1,3}");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedQuantifiers_macAddress_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([0-9a-fA-F]{2}:){1,5}[0-9a-fA-F]{2}");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_possessiveQuantifier_outer_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a+)++");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_possessiveQuantifier_inner_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a++)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointSuffixAlternation_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a|ab)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_crossingAlternationCycles_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(ab|ba)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_singleCharRegex_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("abc");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_identicalBranchesWithoutOuterLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+|a+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_identicalBranchesWithOuterLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+|a+)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_reluctantNestedQuantifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+?)+?");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_anchoredNestedQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^(a+)+$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_repeatedGroupWithoutInnerQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((a))+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_consecutiveIdenticalQuantifiers_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("a+a+");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .isEqualTo(
            "Regular expression is vulnerable to polynomial backtracking (PDA): 'a+a+' contains"
                + " consecutive overlapping quantifiers on 'a+' and 'a+' (attack payload:"
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\") (suggested rewrite: 'a++a+')");
  }

  @Test public void checkPolynomialBacktracking_digitFollowedByWordChar_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("\\d+\\w+");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("contains consecutive overlapping quantifiers on '\\d+' and '\\w+'");
    assertThat(thrown).hasMessageThat().contains("(suggested rewrite: '\\d++\\w+')");
  }

  @Test public void
      checkPolynomialBacktracking_overlappingCharacterClasses_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("[0-9]+[0-9a-z]+");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("contains consecutive overlapping quantifiers on '[0-9]+' and '[0-9a-z]+'");
    assertThat(thrown).hasMessageThat().contains("(suggested rewrite: '[0-9]++[0-9a-z]+')");
  }

  @Test public void suggestRedosRewrite_nestedQuantifier_suggestsFlattened() {
    assertThat(Redos.suggestRedosRewrite(RegexPattern.of("(a+)+"))).hasValue("a+");
  }

  @Test public void suggestRedosRewrite_nullableRepeated_suggestsNonNullable() {
    assertThat(Redos.suggestRedosRewrite(RegexPattern.of("(a*)+"))).hasValue("a*");
  }

  @Test public void suggestRedosRewrite_unrecognizedPattern_returnsEmpty() {
    assertThat(Redos.suggestRedosRewrite(RegexPattern.of("(a|b)+"))).isEmpty();
  }

  @Test public void suggestPolynomialRewrite_overlappingQuantifiers_suggestsPossessive() {
    assertThat(Redos.suggestPolynomialRewrite(RegexPattern.of("\\d+\\w+"))).hasValue("\\d++\\w+");
  }

  @Test public void suggestPolynomialRewrite_disjointQuantifiers_returnsEmpty() {
    assertThat(Redos.suggestPolynomialRewrite(RegexPattern.of("a+b+"))).isEmpty();
  }

  @Test public void checkPolynomialBacktracking_threeIdenticalQuantifiers_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("a*a*a*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_dotStarWithInterveningLiteral_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of(".*a.*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void checkPolynomialBacktracking_disjointQuantifiers_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+b+");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_disjointCharacterClasses_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[0-9]+[a-z]+");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_possessiveFirstQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a++a+");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_linearSequence_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a+b");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkRedosVulnerability_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> Redos.checkRedosVulnerability(null));
  }

  @Test public void checkPolynomialBacktracking_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> Redos.checkPolynomialBacktracking(null));
  }

  @Test public void suggestRedosRewrite_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> Redos.suggestRedosRewrite(null));
  }

  @Test public void suggestPolynomialRewrite_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> Redos.suggestPolynomialRewrite(null));
  }

  // --- New Polynomial Backtracking (PDA) Cases ---

  @Test public void
      checkPolynomialBacktracking_digitFollowedByDotStarAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("\\d+.*\\d+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_wordCharFollowedByDotStarAndWordChar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("\\w+.*\\w+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void checkPolynomialBacktracking_adjacentDotStars_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(".*.*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_overlappingCharClasses_lettersAndAlphanumeric_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("[a-z]+[a-z0-9]+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_optionalInterveningChar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("a+b?a+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_repeatedTrailingQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("ab*b*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_anchoredOverlappingQuantifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^a*a*$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_overlappingQuantifiersWithTrailingLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("a+a+b");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void
      checkPolynomialBacktracking_dotStarEqualsDotStar_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(".*=.*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
  }

  @Test public void checkPolynomialBacktracking_exponentialNestedQuantifiers_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a+)+");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_exponentialStarInsideStar_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([a-z]+)*");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_exponentialOptionalPrefixSequenceLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a?a)*");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_commaSeparatedDigits_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("\\d+,\\d+");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_colonSeparatedClasses_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[a-z]+:[0-9]+");
    Redos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_bothPossessive_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a++a++");
    Redos.checkPolynomialBacktracking(pattern);
  }

  // --- New Exponential ReDoS (EDA) Cases ---

  @Test public void
      checkRedosVulnerability_tripleNestedQuantifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((a+)+)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_anchoredWordCharOuterQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^([a-zA-Z0-9]+)+$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithInnerTrailingQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+b*)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_namedGroupNestedQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(?<name>[a-z]+)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingPredefinedClasses_wordAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\w|\\d)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingDigitAndAlphanumericAlternation_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([0-9]|[a-z0-9])+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationPrefixed_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(b|a?b)*c");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationOptionalSuffix_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a|aa?)*b");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationSubsetRanges_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([a-z]|[d-h])*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationComplementRanges_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^a-z]|[^0-9])*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationPredefinedAndCharClass_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\d|[0-9])*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_identicalPredefinedBranches_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\s|\\s)*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingWordCharAndLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\w|G)*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingWordAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\d|\\w)*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingDigitAndDigitLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\d|5)*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_identicalFormFeed_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\f|[\\f])*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingNonWordAndNonDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\W|\\D)*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingNonSpaceAndWord_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((\\S|\\w)*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingSequenceAndPredefined_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((1s|[\\da-z])*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_overlappingZeroAndDigit_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((0|[\\d])*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_nestedDigitQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([\\d]+)*)\"");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_threeAlternationBranchesInLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+|b+|c+)*c");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_nestedSequenceLoopWithOptional_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(((a+a?)*)+b+)");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithTrailingLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)+bbbb");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithTrailingLiteralAnchored_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)+aaaaa$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_nestedNewlines_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\n+)+\\n\\n$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassRepeated_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([^X]+)*$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoopWithBranch_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*($|[^X]c)");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_sequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((ab)+)*$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_whitespaceAndDotStarLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([\\n\\s]+)*(.)");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_overlappingLinebreaks_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\r\\n|\\r|\\n)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_literalOrDotLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a|.)*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_negatedQuotesLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([^\"']+)*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_digitWithOptionalSuffixLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\d+(X\\d+)?)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_digitWithOptionalSuffixStarLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([0-9]+(X[0-9]*)?)*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoopWithSameBranch_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*($|[^X]b)");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_sequenceLoopWithTrailingSequence_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((ab)+)*ababab");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_boundedInnerQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a{1,15})+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_optionalPrefixSequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a?a)*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_escapedDotSequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\\\?.)*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_reluctantDotStarInBracketLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\[.*?\\])*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_reluctantDotPlusInBracketLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\[.+?\\])*");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_nestedQuoteQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\"+)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_codeqlMarkdownRule_throwsIllegalArgumentException() {
    RegexPattern pattern =
        RegexPattern.of("^\\b_((?:__|[\\s\\S])+?)_\\b|^\\*((?:\\*\\*|[\\s\\S])+?)\\*(?!\\*)");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_codeqlQuotedStringsOrParens_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(
        "^(?:\\s+(?:\"(?:[^\"\\\\]|\\\\\\\\|\\\\.)+\"|'(?:[^'\\\\]|\\\\\\\\|\\\\.)+'|\\((?:[^)\\\\]|\\\\\\\\|\\\\.)+\\)))?");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_codeqlTableRows_throwsIllegalArgumentException() {
    RegexPattern pattern =
        RegexPattern.of("^ *(\\S.*\\|.*)\\n *([-:]+ *\\|[-| :]*)\\n((?:.*\\|.*(?:\\n|$))*)a");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_codeqlBracketsOrComments_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^([\\s\\[\\{\\(]|#.*)*$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_codeqlPropertyAccess_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(
        "^[\\_$a-z][\\_$a-z0-9]*(\\[.*?\\])*(\\.[\\_$a-z][\\_$a-z0-9]*(\\[.*?\\])*)*$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_codeqlEmailComplex_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of(
        "^([a-zA-Z0-9])(([\\\\-.]|[_]+)?([a-zA-Z0-9]+))*(@){1}[a-z0-9]+[.]{1}(([a-z]{2,3})|([a-z]{2,3}[.]{1}[a-z]{2,3}))$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_codeqlDottedIdentifiers_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("^(([a-z])+.)+[A-Z]([a-z])+$");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_codeqlCssSelectors_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([\\w#:.~>+()\\s-]+|\\*|\\[.*?\\])+)\\s*(,|$)");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_codeqlEscapedQuotes_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\"|')(\\\\?.)*?\\1");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  // --- New Safe Patterns ---

  @Test public void checkRedosVulnerability_emailPattern_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_markdownBoldItalic_doesNotThrow() {
    RegexPattern pattern =
        RegexPattern.of("^\\b_((?:__|[^_])+?)_\\b|^\\*((?:\\*\\*|[^*])+?)\\*(?!\\*)");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointCharOrNegatedCharLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((a|[^a])*)\"");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointSpaceOrDigitLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((\\s|\\d)*)\"");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_escapedStringLiteralLoop_doesNotThrow() {
    RegexPattern pattern =
        RegexPattern.of("\"((?:\\\\[\\x00-\\x7f]|[^\\x00-\\x08\\x0a-\\x1f\\x7f\"\\\\])*)\"");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaDelimitedRepeatedGroup_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("((0|[1-9][0-9]*),)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaDelimitedNegatedChars_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([^,]+,)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_semicolonDelimitedDigits_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(\\d+;)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_alternatingLetterAndDigitsLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([a-z]+[0-9]+)+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_colonSeparatedClasses_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[a-z]+:[0-9]+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaSeparatedDigits_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("\\d+,\\d+");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_boundedOuterAndInnerQuantifiers_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(a{1,3}){1,3}");
    Redos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_zeroOrPositiveInteger_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(0|[1-9][0-9]*)+");
    assertThrows(IllegalArgumentException.class, () -> Redos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_possessiveInnerPositiveInteger_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(0|[1-9][0-9]*+)+");
    Redos.checkRedosVulnerability(pattern);
  }
}
