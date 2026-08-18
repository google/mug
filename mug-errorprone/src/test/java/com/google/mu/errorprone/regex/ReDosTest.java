package com.google.mu.errorprone.regex;

import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parsers;
import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void checkRedosVulnerability_boundedGroupWithNestedQuantifier_safe() {
    RegexPattern pattern = RegexPattern.of("a(?:\\{\\s?b?\\})?c");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_nestedOptionalGroup_correctCulpritAndPayload() {
    RegexPattern pattern = RegexPattern.of(
        "(?<tag>\\p{Alpha}+)" + "(?:\\{\\s?(?<params>(?:\\p{Alpha}+=[\\w|\\.]+,?\\s?)+)?\\})?");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat().contains("contains nested quantifiers on '\\p{Alpha}+'");
    assertThat(thrown).hasMessageThat()
        .contains("attack payload: \"a{a=aa=aa=aa=aa=aa=aa=aa=aa=aa=a!\"");
  }

  @Test public void
      checkRedosVulnerability_nestedOverlappingAlternationInGroup_correctCulpritAndPayload() {
    RegexPattern pattern = RegexPattern.of(
        "(?<tag>\\p{Alpha}+)" + "(?:\\.randomized\\((?<random>\\d\\.\\d)\\))?"
            + "(?:\\.then\\((?<chain>(\\w|\\d|\\s|[,.(){}=])+)\\))?");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown).hasMessageThat()
        .contains("contains overlapping alternation branches '\\w|\\d|\\s|[,.(){}=]'");
    assertThat(thrown).hasMessageThat()
        .contains("attack payload: \"a.then(aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"");
  }

  @Test public void checkRedosVulnerability_safePattern_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("[a-zA-Z0-9]+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_dotSeparatedWords_safe() {
    RegexPattern pattern = RegexPattern.of("^[a-zA-Z]([\\w]*\\.[a-zA-Z][\\w]*)+$");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkPolynomialBacktracking_dotSeparatedWords_safe() {
    RegexPattern pattern = RegexPattern.of("^[a-zA-Z]([\\w]*\\.[a-zA-Z][\\w]*)+$");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkRedosVulnerability_commentLines_safe() {
    RegexPattern pattern = RegexPattern.of("(\n\\s*//.*)+\\s*$");
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
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly("Parser.consecutive(\"[a-zA-Z0-9]\").atLeastOnceDelimitedBy(\"_\")");
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
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
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("^a+$");
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
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("prefix_a+");
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
    assertThat(SuggestionSynthesizer.suggestRedosRewrite(RegexPattern.of("(a+)+"))).hasValue("a+");
  }

  @Test public void suggestRedosRewrite_nullableRepeated_suggestsNonNullable() {
    assertThat(SuggestionSynthesizer.suggestRedosRewrite(RegexPattern.of("(a*)+"))).hasValue("a*");
  }

  @Test public void suggestRedosRewrite_unrecognizedPattern_returnsEmpty() {
    assertThat(SuggestionSynthesizer.suggestRedosRewrite(RegexPattern.of("(a|b)+"))).isEmpty();
  }

  @Test public void suggestPolynomialRewrite_consecutiveIdenticalPlusQuantifiers_mergesToRange() {
    assertThat(SuggestionSynthesizer.suggestPolynomialRewrite(RegexPattern.of("a+a+")))
        .hasValue("a{2,}");
  }

  @Test public void suggestPolynomialRewrite_consecutiveIdenticalStarQuantifiers_mergesToStar() {
    assertThat(SuggestionSynthesizer.suggestPolynomialRewrite(RegexPattern.of("a*a*")))
        .hasValue("a*");
  }

  @Test public void
      suggestPolynomialRewrite_consecutiveIdenticalDigitPlusQuantifiers_mergesToRange() {
    assertThat(SuggestionSynthesizer.suggestPolynomialRewrite(RegexPattern.of("\\d+\\d+")))
        .hasValue("\\d{2,}");
  }

  @Test public void suggestPolynomialRewrite_overlappingQuantifiers_suggestsPossessive() {
    assertThat(SuggestionSynthesizer.suggestPolynomialRewrite(RegexPattern.of("\\d+\\w+")))
        .hasValue("\\d++\\w+");
  }

  @Test public void suggestPolynomialRewrite_disjointQuantifiers_returnsEmpty() {
    assertThat(SuggestionSynthesizer.suggestPolynomialRewrite(RegexPattern.of("a+b+"))).isEmpty();
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
        .containsExactly("Substring.last('a').split(input)");
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

  @Test public void suggestionSynthesizer_nullPattern_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> SuggestionSynthesizer.suggestRedosRewrite(null));
    assertThrows(
        NullPointerException.class, () -> SuggestionSynthesizer.suggestPolynomialRewrite(null));
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
    RegexPattern pattern = RegexPattern.of("a(b*)(b*)");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a(b*)");
  }

  @Test public void checkPolynomialBacktracking_siblingOverlappingInSequence_suggestsMergedRegex() {
    RegexPattern pattern = RegexPattern.of("^prefix(a+)(a+)suffix$");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("^prefix(a{2,})suffix$");
  }

  @Test public void
      checkPolynomialBacktracking_siblingOverlappingInGroup_suggestsPossessiveRegex() {
    RegexPattern pattern = RegexPattern.of("prefix(\\d+\\w+)suffix");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("prefix(\\d++\\w+)suffix");
  }

  @Test public void checkRedosVulnerability_nestedQuantifiersInSequence_suggestsSplicedRegex() {
    RegexPattern pattern = RegexPattern.of("^prefix(a+)+suffix$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("^prefixa+suffix$");
  }

  @Test public void
      checkRedosVulnerability_nestedQuantifiersInCapturingGroup_suggestsSplicedRegex() {
    RegexPattern pattern = RegexPattern.of("^foo((a+)+)bar$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("^foo(a+)bar$");
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
        .containsExactly("Substring.last('=').split(input)");
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
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("^[a-zA-Z0-9]+$");
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithInnerTrailingQuantifier_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+b*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly("Parser.consecutive(\"a\").atLeastOnceDelimitedBy(\"b\")");
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_overlappingAlternationOptionalSuffix_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a|aa?)*b");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("(?:a|(?:aa)?)*b");
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
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("([\\d]*)\"");
  }

  @Test public void
      checkRedosVulnerability_threeAlternationBranchesInLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+|b+|c+)*c");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
  }

  @Test public void
      checkRedosVulnerability_nestedSequenceLoopWithOptional_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(((a+a?)*)+b+)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("((a+a?)*b+)");
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithTrailingLiteral_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)+bbbb");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a+bbbb");
  }

  @Test public void
      checkRedosVulnerability_nestedLoopWithTrailingLiteralAnchored_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(a+)+aaaaa$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("a+aaaaa$");
  }

  @Test public void checkRedosVulnerability_nestedNewlines_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(\\n+)+\\n\\n$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("n+nn$");
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassRepeated_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([^X]+)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("[^X]*$");
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("([^X]b)*$");
  }

  @Test public void
      checkRedosVulnerability_negatedCharClassSequenceLoopWithBranch_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("(([^X]b)+)*($|[^X]c)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("([^X]b)*($|[^X]c)");
  }

  @Test public void checkRedosVulnerability_sequenceLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((ab)+)*$");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("(ab)*$");
  }

  @Test public void
      checkRedosVulnerability_whitespaceAndDotStarLoop_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("([\\n\\s]+)*(.)");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("[n\\s]*(.)");
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
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly("Parser.consecutive(\"[0-9]\").atLeastOnceDelimitedBy(\"X\")");
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
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("([^X]b)*($|[^X]b)");
  }

  @Test public void
      checkRedosVulnerability_sequenceLoopWithTrailingSequence_throwsIllegalArgumentException() {
    RegexPattern pattern = RegexPattern.of("((ab)+)*ababab");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives()).containsExactly("(ab)*ababab");
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
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
    assertThat(thrown.getSuggestedAlternatives()).isEmpty();
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
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly("Parsers.UNSIGNED_INTEGER.atLeastOnce()");
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

  @Test public void checkRedosVulnerability_exactQuantifierInPrefix_generatesFullPrefixInPayload() {
    RegexPattern pattern = RegexPattern.of("x{3}(a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getAttackPayload()).isEqualTo("xxx" + "a".repeat(30) + "!");
  }

  @Test public void
      checkRedosVulnerability_zeroMinQuantifierInPrefix_generatesMinimalPrefixInPayload() {
    RegexPattern pattern = RegexPattern.of("x*y?(a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getAttackPayload()).isEqualTo("a".repeat(30) + "!");
  }

  @Test public void
      checkRedosVulnerability_rangeQuantifierInPrefix_generatesMinRepetitionPrefixInPayload() {
    RegexPattern pattern = RegexPattern.of("x{2,5}(a+)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getAttackPayload()).isEqualTo("xx" + "a".repeat(30) + "!");
  }

  @Test public void checkPolynomialBacktracking_nullableQuantifiedInPrefix_doesNotDivideByZero() {
    RegexPattern pattern = RegexPattern.of("(a?){2}\\d+\\w+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getAttackPayload()).isEqualTo("0".repeat(30) + "!");
  }

  @Test public void
      checkPolynomialBacktracking_nullableStarQuantifiedInPrefix_doesNotDivideByZero() {
    RegexPattern pattern = RegexPattern.of("(a*){3}\\d+\\w+");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getAttackPayload()).isEqualTo("0".repeat(30) + "!");
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
        .containsExactly("Substring.last(':').split(input)");
  }

  @Test public void
      checkRedosVulnerability_delimitedWordsOptionalDelimiter_suggestsAtLeastOnceDelimitedBy() {
    RegexPattern pattern = RegexPattern.of("(\\w+,?)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains("Parser.consecutive(\"[a-zA-Z0-9_]\").atLeastOnceDelimitedBy(\",\")");
  }

  @Test public void
      checkRedosVulnerability_delimitedAlphaOptionalDelimiter_suggestsAtLeastOnceDelimitedBy() {
    RegexPattern pattern = RegexPattern.of("([a-z]+,?)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains("Parser.consecutive(\"[a-z]\").atLeastOnceDelimitedBy(\",\")");
  }

  @Test public void
      checkRedosVulnerability_delimitedNegatedCharClass_suggestsAtLeastOnceDelimitedBy() {
    RegexPattern pattern = RegexPattern.of("([^,]+,?)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains("Parser.consecutive(\"[^,]\").atLeastOnceDelimitedBy(\",\")");
  }

  @Test public void
      checkRedosVulnerability_delimitedKeyValuePairs_suggestsSequenceAtLeastOnceDelimitedBy() {
    RegexPattern pattern = RegexPattern.of("(\\w+=\\w+\\s*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains(
            "Parser.sequence(Parser.consecutive(\"[a-zA-Z0-9_]\").followedBy(\"=\"),"
                + " Parser.consecutive(\"[a-zA-Z0-9_]\"), Map::entry).atLeastOnceDelimitedBy(\""
                + " \")");
  }

  @Test public void
      checkRedosVulnerability_starQuantifiedDelimitedWords_suggestsZeroOrMoreDelimitedBy() {
    RegexPattern pattern = RegexPattern.of("(\\w+,?)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains("Parser.consecutive(\"[a-zA-Z0-9_]\").zeroOrMoreDelimitedBy(\",\")");
  }

  @Test public void
      checkRedosVulnerability_starQuantifiedKeyValuePairs_suggestsZeroOrMoreDelimitedBy() {
    RegexPattern pattern = RegexPattern.of("(\\w+=\\w+\\s*)*");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains(
            "Parser.sequence(Parser.consecutive(\"[a-zA-Z0-9_]\").followedBy(\"=\"),"
                + " Parser.consecutive(\"[a-zA-Z0-9_]\"), Map::entry).zeroOrMoreDelimitedBy(\""
                + " \")");
  }

  @Test public void checkRedosVulnerability_exactCountQuantifier_detected() {
    RegexPattern pattern = RegexPattern.of("((a+){3})+");
    assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
  }

  @Test public void checkRedosVulnerability_rangeQuantifierWithBound_detected() {
    RegexPattern pattern = RegexPattern.of("(a{2,5})+");
    assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
  }

  @Test public void
      checkRedosVulnerability_delimitedSeparatedList_suggestsAtLeastOnceDelimitedBy() {
    RegexPattern pattern = RegexPattern.of("([a-z]+(,[a-z]+)*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains("Parser.consecutive(\"[a-z]\").atLeastOnceDelimitedBy(\",\")");
  }

  @Test public void
      checkRedosVulnerability_delimitedWithOptionalWhitespaceAroundDelimiter_suggestsDelimitedParserWithCaveat() {
    RegexPattern pattern = RegexPattern.of("([a-z]+\\s*,?\\s*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains("Parser.consecutive(\"[a-z]\").atLeastOnceDelimitedBy(\",\")");
    Suggestion.ParserSuggestion ps = thrown.getSuggestions().stream()
        .filter(Suggestion.ParserSuggestion.class::isInstance)
        .map(Suggestion.ParserSuggestion.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(ps.caveats()).contains(
            "Use parseSkipping(Character::isWhitespace, input) to skip surrounding whitespace"
                + " during parsing");
  }

  @Test public void
      checkRedosVulnerability_delimitedSeparatedListWithOptionalWhitespace_suggestsDelimitedParserWithCaveat() {
    RegexPattern pattern = RegexPattern.of("([a-z]+(\\s*,\\s*[a-z]+)*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains("Parser.consecutive(\"[a-z]\").atLeastOnceDelimitedBy(\",\")");
    Suggestion.ParserSuggestion ps = thrown.getSuggestions().stream()
        .filter(Suggestion.ParserSuggestion.class::isInstance)
        .map(Suggestion.ParserSuggestion.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(ps.caveats()).contains(
            "Use parseSkipping(Character::isWhitespace, input) to skip surrounding whitespace"
                + " during parsing");
  }

  @Test public void
      checkRedosVulnerability_delimitedKeyValuePairsWithOptionalWhitespace_suggestsDelimitedParserWithCaveat() {
    RegexPattern pattern = RegexPattern.of("(\\w+\\s*=\\s*\\w+\\s*,?\\s*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .contains(
            "Parser.sequence(Parser.consecutive(\"[a-zA-Z0-9_]\").followedBy(\"=\"),"
                + " Parser.consecutive(\"[a-zA-Z0-9_]\"),"
                + " Map::entry).atLeastOnceDelimitedBy(\",\")");
    Suggestion.ParserSuggestion ps = thrown.getSuggestions().stream()
        .filter(Suggestion.ParserSuggestion.class::isInstance)
        .map(Suggestion.ParserSuggestion.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(ps.caveats()).contains(
            "Use parseSkipping(Character::isWhitespace, input) to skip surrounding whitespace"
                + " during parsing");
  }

  @Test public void
      checkRedosVulnerability_nestedKeyValuePairsInSequence_noParserSuggestionForSubExpression() {
    RegexPattern pattern = RegexPattern.of(
        "(?<tag>\\p{Alpha}+)(?:\\{\\s?(?<params>(?:\\p{Alpha}+=[\\w|\\.]+,?\\s?)+)?\\})?");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(
            thrown.getSuggestions().stream()
                .anyMatch(Suggestion.ParserSuggestion.class::isInstance))
        .isFalse();
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly("(?<tag>\\p{Alpha}+)(?:\\{\\s?(?:\\p{Alpha}+=[\\w|.]+,?\\s?)*\\})?");
  }

  @Test public void checkRedosVulnerability_structuredNumberGrammar_suggestsParsers() {
    RegexPattern pattern = RegexPattern.of("(0|[1-9][0-9]*)+");
    VulnerableRegexException thrown =
        assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
    assertThat(thrown.getSuggestedAlternatives())
        .containsExactly("Parsers.UNSIGNED_INTEGER.atLeastOnce()");
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

  @Test public void getSuggestions_delimitedWildcards_suggestsSubstringLastWithoutCaveat() {
    RegexPattern pattern = RegexPattern.of(".*:.*");
    VulnerableRegexException thrown = assertThrows(
        VulnerableRegexException.class, () -> ReDos.checkPolynomialBacktracking(pattern));
    assertThat(thrown.getSuggestions()).hasSize(1);
    assertThat(thrown.getSuggestions().get(0)).isInstanceOf(Suggestion.SubstringSuggestion.class);
    Suggestion.SubstringSuggestion ss =
        (Suggestion.SubstringSuggestion) thrown.getSuggestions().get(0);
    assertThat(ss.replacement()).isEqualTo("Substring.last(':').split(input)");
    assertThat(ss.toString()).isEqualTo("Substring.last(':').split(input)");
    assertThat(ss.isStrictlyEquivalent()).isTrue();
    assertThat(ss.caveats()).isEmpty();
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
    assertThat(ps.replacement()).isEqualTo("Parsers.UNSIGNED_INTEGER.atLeastOnce()");
    assertThat(ps.toString()).isEqualTo("Parsers.UNSIGNED_INTEGER.atLeastOnce()");
    assertThat(ps.isStrictlyEquivalent()).isFalse();
    assertThat(ps.caveats()).isNotEmpty();
  }

  @Test public void suggestion_substringSuggestion_instantiationAndAccessors() {
    Suggestion.SubstringSuggestion ss =
        new Suggestion.SubstringSuggestion(/* replacement= */ "Substring.last(':').split(input)");
    assertThat(ss.replacement()).isEqualTo("Substring.last(':').split(input)");
    assertThat(ss.isStrictlyEquivalent()).isTrue();
    assertThat(ss.caveats()).isEmpty();
    assertThat(ss.toString()).isEqualTo("Substring.last(':').split(input)");
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

  @Test public void suggestedAlternative_substringLastSplit_extractsIdenticalKeyAndValue() {
    Matcher matcher = Pattern.compile("^(.*):(.*)$").matcher("user:123");
    assertThat(matcher.matches()).isTrue();
    List<String> regexExtracted = List.of(matcher.group(1), matcher.group(2));
    List<String> substringExtracted =
        Substring.last(':').split("user:123", (l, r) -> List.of(l, r)).orElseThrow();
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

  @Test public void suggestedAlternative_substringLastSplit_matchesGreedyRegexOnMultiDelimiters() {
    Matcher matcher = Pattern.compile("^(.*):(.*)$").matcher("a:b:c");
    assertThat(matcher.matches()).isTrue();
    List<String> regexExtracted = List.of(matcher.group(1), matcher.group(2));
    List<String> substringExtracted =
        Substring.last(':').split("a:b:c", (l, r) -> List.of(l, r)).orElseThrow();
    assertThat(substringExtracted).isEqualTo(regexExtracted);
  }

  @Test public void suggestedAlternative_delimitedWords_parsesCommaSeparatedTokens() {
    assertThat(consecutive("[a-zA-Z0-9_]").atLeastOnceDelimitedBy(",").parse("foo,bar,baz"))
        .containsExactly("foo", "bar", "baz")
        .inOrder();
  }

  @Test public void suggestedAlternative_delimitedAlpha_parsesCommaSeparatedAlphaTokens() {
    assertThat(consecutive("[a-z]").atLeastOnceDelimitedBy(",").parse("apple,banana,orange"))
        .containsExactly("apple", "banana", "orange")
        .inOrder();
  }

  @Test public void suggestedAlternative_delimitedNegatedCharClass_parsesDelimitedTokens() {
    assertThat(consecutive("[^,]").atLeastOnceDelimitedBy(",").parse("hello,world"))
        .containsExactly("hello", "world")
        .inOrder();
  }

  @Test public void suggestedAlternative_delimitedKeyValuePairs_parsesSpaceSeparatedEntries() {
    assertThat(
            sequence(
                    consecutive("[a-zA-Z0-9_]").followedBy("="),
                    consecutive("[a-zA-Z0-9_]"),
                    Map::entry)
                .atLeastOnceDelimitedBy(" ")
                .parse("k1=v1 k2=v2"))
        .containsExactly(Map.entry("k1", "v1"), Map.entry("k2", "v2"))
        .inOrder();
  }

  @Test public void suggestedAlternative_delimitedWithWhitespace_parseSkippingParsesTokens() {
    assertThat(
            consecutive("[a-z]").atLeastOnceDelimitedBy(",")
                .parseSkipping(Character::isWhitespace, "apple , banana , orange"))
        .containsExactly("apple", "banana", "orange")
        .inOrder();
  }

  @Test public void suggestPolynomialRewrite_boundedRepetitionUnderThreshold_returnsEmpty() {
    assertThat(SuggestionSynthesizer.suggestPolynomialRewrite(RegexPattern.of("a{1,5}a{1,5}")))
        .isEmpty();
  }

  @Test public void suggestPolynomialRewrite_boundedRepetitionOverThreshold_suggestsPossessive() {
    assertThat(SuggestionSynthesizer.suggestPolynomialRewrite(RegexPattern.of("a{1,6}a{1,6}")))
        .hasValue("a{1,6}+a{1,6}");
  }

  @Test public void
      checkRedosVulnerability_nestedAtLeastWithMinTwo_throwsVulnerableRegexException() {
    assertThrows(
        VulnerableRegexException.class,
        () -> ReDos.checkRedosVulnerability(RegexPattern.of("(a{2,})+")));
  }

  @Test public void checkRedosVulnerability_safePatterns_doesNotThrow(
      @TestParameter({
            "abc",
            "a+",
            "[a-z]+",
            "\\d*",
            "a{1,3}",
            "(a{1,3}){1,3}",
            "(a++)+",
            "(a+)++",
            "a++a++",
            "a{1,5}a{1,5}",
            "(foo|bar)+",
            "(a|ab)+",
            "(ab|ba)+",
            "(0|[1-9][0-9]*+)+",
            "((0|[1-9][0-9]*),)+",
            "([^,]+,)+",
            "([^,\\n]+[,\\n])+",
            "(\\d+;)+",
            "([a-z]+[0-9]+)+",
            "\\d+,\\d+",
            "[a-z]+:[0-9]+",
            "([a-z]+:[0-9]+)+",
            "([a-zA-Z]+/[0-9]+)+",
            "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$",
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b",
            "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)",
            "(GET|POST|PUT|DELETE|HEAD|OPTIONS)",
            "(true|false|null)",
            "[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?",
            "\"(\\\\.|[^\"\\\\])*\"",
            "'(\\\\.|[^'\\\\])*'",
            "^\\b_((?:__|[^_])+?)_\\b|^\\*((?:\\*\\*|[^*])+?)\\*(?!\\*)",
            "((a|[^a])*)\"",
            "((\\s|\\d)*)\"",
            "\"((?:\\\\[\\x00-\\x7f]|[^\\x00-\\x08\\x0a-\\x1f\\x7f\"\\\\])*)\"",
          })
          String regex) {
    RegexPattern pattern = RegexPattern.of(regex);
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkPolynomialBacktracking_safePatterns_doesNotThrow(
      @TestParameter({
            "abc",
            "a+",
            "[a-z]+",
            "\\d*",
            "a{1,3}",
            "(a{1,3}){1,3}",
            "(a++)+",
            "(a+)++",
            "a++a++",
            "a{1,5}a{1,5}",
            "(foo|bar)+",
            "(a|ab)+",
            "(ab|ba)+",
            "(0|[1-9][0-9]*+)+",
            "((0|[1-9][0-9]*),)+",
            "([^,]+,)+",
            "(\\d+;)+",
            "([a-z]+[0-9]+)+",
            "\\d+,\\d+",
            "[a-z]+:[0-9]+",
            "([a-z]+:[0-9]+)+",
            "([a-zA-Z]+/[0-9]+)+",
            "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$",
            "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)",
            "(GET|POST|PUT|DELETE|HEAD|OPTIONS)",
            "(true|false|null)",
            "[+-]?\\d+(\\.\\d+)?([eE][+-]?\\d+)?",
            "\"(\\\\.|[^\"\\\\])*\"",
            "'(\\\\.|[^'\\\\])*'",
          })
          String regex) {
    RegexPattern pattern = RegexPattern.of(regex);
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkRedosVulnerability_exactBoundedQuantifierInAlternationLoop_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(%[a-f0-9]{2}|[a-z0-9!#$&+.\\^_`|~\\-])+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_exactHexInPercentEncoding_doesNotThrow() {
    RegexPattern pattern =
        RegexPattern.of("(?:%40|@)(([\\p{Ll}A-Za-z0-9_.~\\-] |%[A-Za-z0-9]{2})+)");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_possessiveOuterQuantifierWithNestedOverlappingAlternation_throwsVulnerableRegexException() {
    RegexPattern pattern = RegexPattern.of("((\\s++)|(/\\*(.|\\s)*?\\*/)|(//.*$))++");
    assertThrows(VulnerableRegexException.class, () -> ReDos.checkRedosVulnerability(pattern));
  }

  @Test public void checkPolynomialBacktracking_possessiveEnclosedQuantifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^\\{\\s*+((?:[^}\\\\]|\\\\.)++)\\s*\\}\\s*+$");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_possessiveWordAndSpace_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of(".*?from\\s++(\\w*+).*");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkRedosVulnerability_unicodeSpaceCategoryAlternation_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(\\p{Zl}|\\p{Zp}|\\p{Zs}){6,}");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_slashedPathSegmentsWithMandatoryDelimiter_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^/?[a-zA-Z0-9\\-_.]+(/[a-zA-Z0-9\\-_.]+)*$");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_piperDepotPathSegments_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("//depot/google3(/[a-zA-Z0-9_.%\\-]+)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_javaPackageDottedIdentifier_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^([a-z][a-z0-9_]+[.])+[A-Z][a-zA-Z0-9_$]+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_dottedFieldIdentifier_doesNotThrow() {
    RegexPattern pattern =
        RegexPattern.of("\\$?[a-zA-Z_][a-zA-Z_0-9]*(?:[.]\\$?[a-zA-Z_][a-zA-Z_0-9]*)*");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_domainNameWithMandatoryDots_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of(
        "^(?:[_a-z0-9](?:[_a-z0-9\\-]{0,61}[a-z0-9])?\\.)+(?:[a-z](?:[a-z0-9\\-]{0,61}[a-z0-9])?)?$");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaSeparatedNumbers_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^\\d{1,5}(,\\d{1,5})*$");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_commaSeparatedHexList_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^<cpu\\s+mask=\"(\\p{XDigit}+(,\\p{XDigit}+)*)\">");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkPolynomialBacktracking_boundedIpSegments_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^(\\d{1,3}[.\\-]){3}\\d{1,3}$");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_mandatoryLanguageTagSuffix_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(\\w+)\\-(\\w*)\\-(\\w{2,3})");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_possessiveWhitespaceInSurroundingWildcards_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of(".*?a\\s++(\\w*+).*");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkPolynomialBacktracking_possessiveWildcardCut_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of(".*?\\s++.*");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_reluctantQuantifierBoundedBySubsequentLiteral_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^\\{\\s*+((?:[^}\\\\]|\\\\.)+?)\\s*\\}\\s*+$");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_reluctantQuantifierBoundedByPredefinedClass_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("^\\b((?:\\D|\\\\.)+?)\\d+\\b");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkRedosVulnerability_dotInCharacterClassDoesNotOverlapDisjointBranch_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("([0-9.]|a)+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_possessiveIntermediateTokenOverlappingWithSurroundingCycles_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of(".*?a\\s++(\\s*+).*");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void
      checkPolynomialBacktracking_multiCharacterDisjointLiteralIntermediateWord_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of(".*?FPS=.*");
    ReDos.checkPolynomialBacktracking(pattern);
  }

  @Test public void checkRedosVulnerability_standaloneAtLeastWithMinTwo_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("a{2,}");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void checkRedosVulnerability_disjointUnicodeCaseCategories_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(\\p{Lu}|\\p{Ll})+");
    ReDos.checkRedosVulnerability(pattern);
  }

  @Test public void
      checkRedosVulnerability_disjointUnicodeLineAndParagraphSeparators_doesNotThrow() {
    RegexPattern pattern = RegexPattern.of("(\\p{Zl}|\\p{Zp}){6,}");
    ReDos.checkRedosVulnerability(pattern);
  }
}
