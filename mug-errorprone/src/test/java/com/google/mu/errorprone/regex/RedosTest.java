package com.google.mu.errorprone.regex;

import static com.google.common.truth.Truth.assertThat;
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
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\")");
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
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\")");
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
                + " \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\")");
  }

  @Test public void checkPolynomialBacktracking_digitFollowedByWordChar_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("\\d+\\w+");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("contains consecutive overlapping quantifiers on '\\d+' and '\\w+'");
  }

  @Test public void
      checkPolynomialBacktracking_overlappingCharacterClasses_throwsDetailedMessage() {
    RegexPattern pattern = RegexPattern.of("[0-9]+[0-9a-z]+");
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> Redos.checkPolynomialBacktracking(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("contains consecutive overlapping quantifiers on '[0-9]+' and '[0-9a-z]+'");
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
}
