package com.google.mu.errorprone.regex;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion.ParserSuggestion;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion.RegexSuggestion;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion.StringFormatSuggestion;
import com.google.mu.errorprone.regex.VulnerableRegexException.Suggestion.SubstringSuggestion;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SuggestionTest {

  @Test public void regexSuggestion_singleArgConstructor_defaultsToStrictlyEquivalent() {
    Suggestion suggestion = new RegexSuggestion("a+");
    assertThat(suggestion.replacement()).isEqualTo("a+");
    assertThat(suggestion.isStrictlyEquivalent()).isTrue();
    assertThat(suggestion.caveats()).isEmpty();
    assertThat(suggestion.toString()).isEqualTo("a+");
  }

  @Test public void regexSuggestion_twoArgsConstructor_defaultsToNonEquivalentWithCaveat() {
    Suggestion suggestion = new RegexSuggestion("\\d++\\w+", "May reject overlapping inputs");
    assertThat(suggestion.replacement()).isEqualTo("\\d++\\w+");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("May reject overlapping inputs");
    assertThat(suggestion.toString()).isEqualTo("\\d++\\w+");
  }

  @Test public void regexSuggestion_threeArgsConstructor_customValues() {
    Suggestion suggestion = new RegexSuggestion("a{2,}", true, List.of());
    assertThat(suggestion.replacement()).isEqualTo("a{2,}");
    assertThat(suggestion.isStrictlyEquivalent()).isTrue();
    assertThat(suggestion.caveats()).isEmpty();
  }

  @Test public void regexSuggestion_nullReplacement_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new RegexSuggestion(null));
  }

  @Test public void regexSuggestion_nullCaveat_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new RegexSuggestion("a+", (String) null));
  }

  @Test public void regexSuggestion_nullCaveatsList_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new RegexSuggestion("a+", true, null));
  }

  @Test public void regexSuggestion_caveatsListIsUnmodifiable() {
    Suggestion suggestion = new RegexSuggestion("a+", "Caveat");
    assertThrows(UnsupportedOperationException.class, () -> suggestion.caveats().add("Another"));
  }

  @Test public void stringFormatSuggestion_singleArgConstructor_returnsFormatAndReplacement() {
    StringFormatSuggestion suggestion = new StringFormatSuggestion("{left}:{right}");
    assertThat(suggestion.format()).isEqualTo("{left}:{right}");
    assertThat(suggestion.replacement()).isEqualTo("new StringFormat(\"{left}:{right}\")");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).isEmpty();
    assertThat(suggestion.toString()).isEqualTo("new StringFormat(\"{left}:{right}\")");
  }

  @Test public void stringFormatSuggestion_twoArgsConstructor_hasCaveat() {
    StringFormatSuggestion suggestion =
        new StringFormatSuggestion("{left}:{right}", "Delimiters matched left to right");
    assertThat(suggestion.format()).isEqualTo("{left}:{right}");
    assertThat(suggestion.replacement()).isEqualTo("new StringFormat(\"{left}:{right}\")");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("Delimiters matched left to right");
    assertThat(suggestion.toString()).isEqualTo("new StringFormat(\"{left}:{right}\")");
  }

  @Test public void stringFormatSuggestion_threeArgsConstructor_customValues() {
    StringFormatSuggestion suggestion =
        new StringFormatSuggestion("{left}={right}", false, List.of("Caveat"));
    assertThat(suggestion.format()).isEqualTo("{left}={right}");
    assertThat(suggestion.replacement()).isEqualTo("new StringFormat(\"{left}={right}\")");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("Caveat");
  }

  @Test public void stringFormatSuggestion_nullFormat_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new StringFormatSuggestion(null));
  }

  @Test public void stringFormatSuggestion_nullCaveat_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> new StringFormatSuggestion("{left}", (String) null));
  }

  @Test public void stringFormatSuggestion_nullCaveatsList_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> new StringFormatSuggestion("{left}", false, null));
  }

  @Test public void stringFormatSuggestion_caveatsListIsUnmodifiable() {
    StringFormatSuggestion suggestion = new StringFormatSuggestion("{left}", "Caveat");
    assertThrows(UnsupportedOperationException.class, () -> suggestion.caveats().add("Another"));
  }

  @Test public void parserSuggestion_singleArgConstructor_returnsReplacement() {
    ParserSuggestion suggestion = new ParserSuggestion("Parsers.integer().repeatedly()");
    assertThat(suggestion.replacement()).isEqualTo("Parsers.integer().repeatedly()");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).isEmpty();
    assertThat(suggestion.toString()).isEqualTo("Parsers.integer().repeatedly()");
  }

  @Test public void parserSuggestion_twoArgsConstructor_hasCaveat() {
    ParserSuggestion suggestion =
        new ParserSuggestion("Parsers.integer().repeatedly()", "Deterministic parsing");
    assertThat(suggestion.replacement()).isEqualTo("Parsers.integer().repeatedly()");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("Deterministic parsing");
    assertThat(suggestion.toString()).isEqualTo("Parsers.integer().repeatedly()");
  }

  @Test public void parserSuggestion_threeArgsConstructor_customValues() {
    ParserSuggestion suggestion =
        new ParserSuggestion("Parsers.integer()", false, List.of("Caveat"));
    assertThat(suggestion.replacement()).isEqualTo("Parsers.integer()");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("Caveat");
  }

  @Test public void parserSuggestion_nullReplacement_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new ParserSuggestion(null));
  }

  @Test public void parserSuggestion_nullCaveat_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> new ParserSuggestion("Parsers.integer()", (String) null));
  }

  @Test public void parserSuggestion_nullCaveatsList_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class, () -> new ParserSuggestion("Parsers.integer()", false, null));
  }

  @Test public void parserSuggestion_caveatsListIsUnmodifiable() {
    ParserSuggestion suggestion = new ParserSuggestion("Parsers.integer()", "Caveat");
    assertThrows(UnsupportedOperationException.class, () -> suggestion.caveats().add("Another"));
  }

  @Test public void substringSuggestion_singleArgConstructor_returnsReplacement() {
    SubstringSuggestion suggestion = new SubstringSuggestion("Substring.last(':').split(input)");
    assertThat(suggestion.replacement()).isEqualTo("Substring.last(':').split(input)");
    assertThat(suggestion.isStrictlyEquivalent()).isTrue();
    assertThat(suggestion.caveats()).isEmpty();
    assertThat(suggestion.toString()).isEqualTo("Substring.last(':').split(input)");
  }

  @Test public void substringSuggestion_twoArgsConstructor_hasCaveat() {
    SubstringSuggestion suggestion =
        new SubstringSuggestion("Substring.first(':').split(input)", "Splits at first match");
    assertThat(suggestion.replacement()).isEqualTo("Substring.first(':').split(input)");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("Splits at first match");
    assertThat(suggestion.toString()).isEqualTo("Substring.first(':').split(input)");
  }

  @Test public void substringSuggestion_threeArgsConstructor_customValues() {
    SubstringSuggestion suggestion = new SubstringSuggestion(
        "Substring.between(\"[\", \"]\").from(input)", false, List.of("Caveat"));
    assertThat(suggestion.replacement()).isEqualTo("Substring.between(\"[\", \"]\").from(input)");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("Caveat");
  }

  @Test public void substringSuggestion_nullReplacement_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> new SubstringSuggestion(null));
  }

  @Test public void substringSuggestion_nullCaveat_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new SubstringSuggestion("Substring.first(':')", (String) null));
  }

  @Test public void substringSuggestion_nullCaveatsList_throwsNullPointerException() {
    assertThrows(
        NullPointerException.class,
        () -> new SubstringSuggestion("Substring.first(':')", false, null));
  }

  @Test public void substringSuggestion_caveatsListIsUnmodifiable() {
    SubstringSuggestion suggestion = new SubstringSuggestion("Substring.first(':')", "Caveat");
    assertThrows(UnsupportedOperationException.class, () -> suggestion.caveats().add("Another"));
  }

  @Test public void regexSuggestion_multipleCaveats_varargs() {
    Suggestion suggestion = new RegexSuggestion("a+", "Caveat 1", "Caveat 2");
    assertThat(suggestion.replacement()).isEqualTo("a+");
    assertThat(suggestion.isStrictlyEquivalent()).isFalse();
    assertThat(suggestion.caveats()).containsExactly("Caveat 1", "Caveat 2").inOrder();
  }

  @Test public void stringFormatSuggestion_multipleCaveats_varargs() {
    StringFormatSuggestion suggestion =
        new StringFormatSuggestion("{left}:{right}", "Caveat 1", "Caveat 2");
    assertThat(suggestion.caveats()).containsExactly("Caveat 1", "Caveat 2").inOrder();
  }

  @Test public void parserSuggestion_multipleCaveats_varargs() {
    ParserSuggestion suggestion = new ParserSuggestion("Parsers.integer()", "Caveat 1", "Caveat 2");
    assertThat(suggestion.caveats()).containsExactly("Caveat 1", "Caveat 2").inOrder();
  }

  @Test public void substringSuggestion_multipleCaveats_varargs() {
    SubstringSuggestion suggestion =
        new SubstringSuggestion("Substring.first(':')", "Caveat 1", "Caveat 2");
    assertThat(suggestion.caveats()).containsExactly("Caveat 1", "Caveat 2").inOrder();
  }

  @Test public void preservesCaptureGroups_matchingNamedAndNumbered_returnsTrue() {
    RegexPattern original = RegexPattern.of("(?<foo>a+)(\\d+)");
    RegexPattern rewritten = RegexPattern.of("(?<foo>a*)(\\d*)");
    assertThat(SuggestionSynthesizer.preservesCaptureGroups(original, rewritten)).isTrue();
  }

  @Test public void preservesCaptureGroups_differentNamedGroupNames_returnsFalse() {
    RegexPattern original = RegexPattern.of("(?<foo>a+)");
    RegexPattern rewritten = RegexPattern.of("(?<bar>a+)");
    assertThat(SuggestionSynthesizer.preservesCaptureGroups(original, rewritten)).isFalse();
  }

  @Test public void preservesCaptureGroups_differentCapturingGroupCount_returnsFalse() {
    RegexPattern original = RegexPattern.of("(a+)(b+)");
    RegexPattern rewritten = RegexPattern.of("(a+)b+");
    assertThat(SuggestionSynthesizer.preservesCaptureGroups(original, rewritten)).isFalse();
  }
}
