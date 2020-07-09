package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.last;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.NullPointerTester;

import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SubstringTest {

  @Test public void prefix_noMatch() {
    assertThat(prefix("foo").in("notfoo")).isEmpty();
    assertThat(prefix("foo").in("")).isEmpty();
  }

  @Test public void prefix_matchesFullString() {
    Optional<Substring.Match> match = prefix("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void prefix_matchesPrefix() {
    Optional<Substring.Match> match = prefix("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("at")).isEqualTo("atbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void prefix_emptyPrefix() {
    Optional<Substring.Match> match = prefix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void charPrefix_noMatch() {
    assertThat(prefix('f').in("notfoo")).isEmpty();
    assertThat(prefix('f').in("")).isEmpty();
  }

  @Test public void charPrefix_matchesFullString() {
    Optional<Substring.Match> match = prefix('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void charPrefix_matchesPrefix() {
    Optional<Substring.Match> match = prefix("f").in("fbar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("at")).isEqualTo("atbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void suffix_noMatch() {
    assertThat(suffix("foo").in("foonot")).isEmpty();
    assertThat(suffix("foo").in("")).isEmpty();
  }

  @Test public void suffix_matchesFullString() {
    Optional<Substring.Match> match = suffix("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void suffix_matchesPostfix() {
    Optional<Substring.Match> match = suffix("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void suffix_emptyPrefix() {
    Optional<Substring.Match> match = suffix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("foobar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void charSuffix_noMatch() {
    assertThat(suffix('f').in("foo")).isEmpty();
    assertThat(suffix('f').in("")).isEmpty();
  }

  @Test public void charSuffix_matchesFullString() {
    Optional<Substring.Match> match = suffix('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void charSuffix_matchesPostfix() {
    Optional<Substring.Match> match = suffix('r').in("bar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("ba");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("ba");
    assertThat(match.get().replaceWith("car")).isEqualTo("bacar");
    assertThat(match.get().getIndex()).isEqualTo(2);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
  }

  @Test public void firstSnippet_noMatch() {
    assertThat(first("foo").in("bar")).isEmpty();
    assertThat(first("foo").in("")).isEmpty();
  }

  @Test public void firstSnippet_matchesFullString() {
    Optional<Substring.Match> match = first("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstSnippet_matchesPrefix() {
    Optional<Substring.Match> match = first("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstSnippet_matchesPostfix() {
    Optional<Substring.Match> match = first("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstSnippet_matchesInTheMiddle() {
    Optional<Substring.Match> match = first("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstSnippet_emptySnippet() {
    Optional<Substring.Match> match = first("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void firstSnippet_matchesFirstOccurrence() {
    Optional<Substring.Match> match = first("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstRegex_noMatch() {
    assertThat(Substring.first(java.util.regex.Pattern.compile(".*x")).in("bar")).isEmpty();
    assertThat(Substring.first(java.util.regex.Pattern.compile(".*x")).in("")).isEmpty();
  }

  @Test public void firstRegex_matchesFullString() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile(".*oo")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstRegex_matchesPrefix() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile(".*oo")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstRegex_matchesPrefixWithStartingAnchor() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile("^.*oo")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstRegex_doesNotMatchPrefixDueToStartingAnchor() {
    assertThat(Substring.first(java.util.regex.Pattern.compile("^oob.")).in("foobar")).isEmpty();
  }

  @Test public void firstRegex_matchesPostfix() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile("b.*")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstRegex_matchesPostfixWithEndingAnchor() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile("b.*$")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstRegex_doesNotMatchPostfixDueToEndingAnchor() {
    assertThat(Substring.first(java.util.regex.Pattern.compile("b.$")).in("foobar")).isEmpty();
  }

  @Test public void firstRegex_matchesInTheMiddle() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile(".ar")).in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstRegex_emptySnippet() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile("")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void firstRegex_matchesFirstOccurrence() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile(".ar")).in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstRegexGroup_noMatch() {
    assertThat(Substring.first(java.util.regex.Pattern.compile("(.*)x"), 1).in("bar")).isEmpty();
    assertThat(Substring.first(java.util.regex.Pattern.compile("(.*x)"), 1).in("bar")).isEmpty();
    assertThat(Substring.first(java.util.regex.Pattern.compile(".*(x)"), 1).in("bar")).isEmpty();
    assertThat(Substring.first(java.util.regex.Pattern.compile("(.*x)"), 1).in("")).isEmpty();
  }

  @Test public void firstRegexGroup_matchesFirstGroup() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile("(f.)b.*"), 1).in("fobar");
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(2);
    assertThat(match.get().toString()).isEqualTo("fo");
  }

  @Test public void firstRegexGroup_matchesSecondGroup() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile("f(o.)(ba.)"), 2).in("foobarbaz");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstRegexGroup_group0() {
    Optional<Substring.Match> match = Substring.first(java.util.regex.Pattern.compile("f(o.)(ba.).*"), 0).in("foobarbaz");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("car")).isEqualTo("car");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(9);
    assertThat(match.get().toString()).isEqualTo("foobarbaz");
  }

  @Test public void firstRegexGroup_negativeGroup() {
    assertThrows(IndexOutOfBoundsException.class, () -> Substring.first(java.util.regex.Pattern.compile("."), -1));
  }

  @Test public void firstRegexGroup_invalidGroupIndex() {
    assertThrows(IndexOutOfBoundsException.class, () -> Substring.first(java.util.regex.Pattern.compile("f(o.)(ba.)"), 3));
  }

  @Test public void lastSnippet_noMatch() {
    assertThat(last("foo").in("bar")).isEmpty();
    assertThat(last("foo").in("")).isEmpty();
  }

  @Test public void lastSnippet_matchesFullString() {
    Optional<Substring.Match> match = last("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void lastSnippet_matchesPrefix() {
    Optional<Substring.Match> match = last("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void lastSnippet_matchesPostfix() {
    Optional<Substring.Match> match = last("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_matchesInTheMiddle() {
    Optional<Substring.Match> match = last("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_matchesLastOccurrence() {
    Optional<Substring.Match> match = last("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foobar");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobarcarbaz");
    assertThat(match.get().getIndex()).isEqualTo(6);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_emptySnippet() {
    Optional<Substring.Match> match = last("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("foobar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void firstChar_noMatch() {
    assertThat(first('f').in("bar")).isEmpty();
    assertThat(first('f').in("")).isEmpty();
  }

  @Test public void firstChar_matchesFullString() {
    Optional<Substring.Match> match = first('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void firstChar_matchesPrefix() {
    Optional<Substring.Match> match = first('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void firstChar_matchesPostfix() {
    Optional<Substring.Match> match = first('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("fooba");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobacar");
    assertThat(match.get().getIndex()).isEqualTo(5);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
  }

  @Test public void firstChar_matchesFirstOccurrence() {
    Optional<Substring.Match> match = first('b').in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("arbarbaz");
    assertThat(match.get().remove()).isEqualTo("fooarbarbaz");
    assertThat(match.get().replaceWith("coo")).isEqualTo("foocooarbarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
  }

  @Test public void lastChar_noMatch() {
    assertThat(last('f').in("bar")).isEmpty();
    assertThat(last('f').in("")).isEmpty();
  }

  @Test public void lastChar_matchesFullString() {
    Optional<Substring.Match> match = last('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void lastChar_matchesPrefix() {
    Optional<Substring.Match> match = last('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void lastChar_matchesPostfix() {
    Optional<Substring.Match> match = last('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("fooba");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobacar");
    assertThat(match.get().getIndex()).isEqualTo(5);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
  }

  @Test public void lastChar_matchesLastOccurrence() {
    Optional<Substring.Match> match = last('b').in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foobar");
    assertThat(match.get().getAfter()).isEqualTo("az");
    assertThat(match.get().remove()).isEqualTo("foobaraz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobarcaraz");
    assertThat(match.get().getIndex()).isEqualTo(6);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
  }

  @Test public void removeFrom_noMatch() {
    assertThat(first('f').removeFrom("bar")).isEqualTo("bar");
  }

  @Test public void removeFrom_match() {
    assertThat(first('f').removeFrom("foo")).isEqualTo("oo");
  }

  @Test public void replaceFrom_noMatch() {
    assertThat(first('f').replaceFrom("bar", "xyz")).isEqualTo("bar");
  }

  @Test public void replaceFrom_match() {
    assertThat(first('f').replaceFrom("foo", "bar")).isEqualTo("baroo");
  }

  @Test public void or_firstMatcherMatches() {
    Optional<Substring.Match> match =
        first('b').or(first("foo")).in("bar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("ar");
    assertThat(match.get().remove()).isEqualTo("ar");
    assertThat(match.get().replaceWith("coo")).isEqualTo("cooar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
  }

  @Test public void or_secondMatcherMatches() {
    Optional<Substring.Match> match =
        first('b').or(first("foo")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void or_neitherMatches() {
    assertThat(first("bar").or(first("foo")).in("baz"))
        .isEmpty();
  }

  @Test public void none() {
    assertThat(Substring.NONE.in("foo")).isEmpty();
    assertThat(Substring.NONE.removeFrom("foo")).isEqualTo("foo");
    assertThat(Substring.NONE.replaceFrom("foo", "xyz")).isEqualTo("foo");
  }

  @Test public void beginning() {
    assertThat(Substring.BEGINNING.from("foo")).hasValue("");
    assertThat(Substring.BEGINNING.removeFrom("foo")).isEqualTo("foo");
    assertThat(Substring.BEGINNING.replaceFrom("foo", "begin ")).isEqualTo("begin foo");
    assertThat(Substring.BEGINNING.in("foo").get().getBefore()).isEmpty();
    assertThat(Substring.BEGINNING.in("foo").get().getAfter()).isEqualTo("foo");
  }

  @Test public void end() {
    assertThat(Substring.END.from("foo")).hasValue("");
    assertThat(Substring.END.removeFrom("foo")).isEqualTo("foo");
    assertThat(Substring.END.replaceFrom("foo", " end")).isEqualTo("foo end");
    assertThat(Substring.END.in("foo").get().getBefore()).isEqualTo("foo");
    assertThat(Substring.END.in("foo").get().getAfter()).isEmpty();
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicInstanceMethods(Substring.BEGINNING.in("foobar").get());
    new NullPointerTester().testAllPublicInstanceMethods(Substring.END.in("foobar").get());
    newClassSanityTester()
        .testNulls(Substring.class);
    newClassSanityTester()
        .forAllPublicStaticMethods(Substring.class).testNulls();
  }

  @Test public void upTo_noMatch() {
    assertThat(Substring.upToIncluding(first("://")).in("abc")).isEmpty();
  }

  @Test public void upTo_matchAtPrefix() {
    assertThat(Substring.upToIncluding(first("://")).removeFrom("://foo")).isEqualTo("foo");
  }

  @Test public void upTo_matchInTheMiddle() {
    assertThat(Substring.upToIncluding(first("://")).removeFrom("http://foo")).isEqualTo("foo");
  }

  @Test public void toEnd_noMatch() {
    assertThat(first("//").toEnd().in("abc")).isEmpty();
  }

  @Test public void toEnd_matchAtSuffix() {
    assertThat(first("//").toEnd().removeFrom("foo//")).isEqualTo("foo");
  }

  @Test public void toEnd_matchInTheMiddle() {
    assertThat(first("//").toEnd().removeFrom("foo // bar")).isEqualTo("foo ");
  }

  @Test public void before_noMatch() {
    assertThat(Substring.before(first("://")).in("abc")).isEmpty();
  }

  @Test public void before_matchAtPrefix() {
    assertThat(Substring.before(first("//")).removeFrom("//foo")).isEqualTo("//foo");
  }

  @Test public void before_matchInTheMiddle() {
    assertThat(Substring.before(first("//")).removeFrom("http://foo")).isEqualTo("//foo");
  }

  @Test public void after_noMatch() {
    assertThat(Substring.after(first("//")).in("abc")).isEmpty();
  }

  @Test public void after_matchAtSuffix() {
    assertThat(Substring.after(last('.')).removeFrom("foo.")).isEqualTo("foo.");
  }

  @Test public void after_matchInTheMiddle() {
    assertThat(Substring.after(last('.')).removeFrom("foo. bar")).isEqualTo("foo.");
  }

  @Test public void between_matchedInTheMiddle() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("foo<bar>baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.getBefore()).isEqualTo("foo<");
    assertThat(match.getAfter()).isEqualTo(">baz");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_emptyMatch() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("foo<>baz").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.getBefore()).isEqualTo("foo<");
    assertThat(match.getAfter()).isEqualTo(">baz");
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void between_consecutiveFirstChar() {
    Substring.Pattern delimiter = first('-');
    Substring.Match match = Substring.between(delimiter, delimiter).in("foo-bar-baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.getBefore()).isEqualTo("foo-");
    assertThat(match.getAfter()).isEqualTo("-baz");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_matchedFully() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("<foo>").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.getBefore()).isEqualTo("<");
    assertThat(match.getAfter()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_outerMatchesEmpty() {
    Substring.Match match = Substring.between(first(""), last('.')).in("foo.").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.getBefore()).isEmpty();
    assertThat(match.getAfter()).isEqualTo(".");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_innerMatchesEmpty() {
    Substring.Match match = Substring.between(first(":"), last("")).in("hello:world").get();
    assertThat(match.toString()).isEqualTo("world");
    assertThat(match.getBefore()).isEqualTo("hello:");
    assertThat(match.getAfter()).isEmpty();
    assertThat(match.length()).isEqualTo(5);
  }

  @Test public void between_matchedLastOccurrence() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("<foo><bar> <baz>").get();
    assertThat(match.toString()).isEqualTo("baz");
    assertThat(match.getBefore()).isEqualTo("<foo><bar> <");
    assertThat(match.getAfter()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_matchedIncludingDelimiters() {
    Substring.Match match = Substring.between(Substring.before(last('<')), Substring.after(last('>')))
        .in("begin<foo>end")
        .get();
    assertThat(match.toString()).isEqualTo("<foo>");
    assertThat(match.getBefore()).isEqualTo("begin");
    assertThat(match.getAfter()).isEqualTo("end");
    assertThat(match.length()).isEqualTo(5);
  }

  @Test public void between_nothingBetweenSameChar() {
    assertThat(Substring.between(first('.'), first('.')).in(".")).isEmpty();
  }

  @Test public void between_matchesOpenButNotClose() {
    assertThat(Substring.between(first('<'), first('>')).in("<foo")).isEmpty();
  }

  @Test public void between_matchesCloseButNotOpen() {
    assertThat(Substring.between(first('<'), first('>')).in("foo>")).isEmpty();
  }

  @Test public void between_closeIsBeforeOpen() {
    assertThat(Substring.between(first('<'), first('>')).in(">foo<")).isEmpty();
    assertThat(Substring.between(first('<'), first('>')).from(">foo<bar>")).hasValue("bar");;
  }

  @Test public void between_closeUsesBefore() {
    Substring.Pattern open = first("-");
    Substring.Pattern close = Substring.before(first("-"));
    Substring.Match match = Substring.between(open, close)
        .in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.getBefore()).isEqualTo("-");
    assertThat(match.getAfter()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void between_closeUsesUpToIncluding() {
    Substring.Match match = Substring.between(first("-"), Substring.upToIncluding(first("-")))
        .in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.getBefore()).isEqualTo("-");
    assertThat(match.getAfter()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void between_closeBeforeOpenDoesNotCount() {
    Substring.Match match = Substring.between(first('<'), first('>')).in("><foo>").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.getBefore()).isEqualTo("><");
    assertThat(match.getAfter()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_closeUsesRegex() {
    Substring.Match match = Substring.between(first("-"), first(Pattern.compile(".*-")))
        .in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.getBefore()).isEqualTo("-");
    assertThat(match.getAfter()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void between_closeOverlapsWithOpen() {
    assertThat(Substring.between(first("abc"), last("cde")).in("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), last('c')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix("cde")).in("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix('c')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix("a")).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix('a')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first("a")).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first('a')).in("abc")).isEmpty();
  }

  @Test public void between_betweenInsideBetween() {
    Substring.Match match = Substring
        .between(first("-"), Substring.between(first(""), first('-')))
        .in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.getBefore()).isEqualTo("-");
    assertThat(match.getAfter()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void between_matchesNone() {
    assertThat(Substring.between(first('<'), first('>')).in("foo")).isEmpty();
  }


  @Test public void between_emptyOpen() {
    Substring.Match match = Substring.between(first(""), first(", ")).in("foo, bar").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.getBefore()).isEmpty();
    assertThat(match.getAfter()).isEqualTo(", bar");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_emptyClose() {
    Substring.Match match = Substring.between(first(":"), first("")).in("foo:bar").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.getBefore()).isEqualTo("foo:");
    assertThat(match.getAfter()).isEqualTo("bar");
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void between_emptyOpenAndClose() {
    Substring.Match match = Substring.between(first(""), first("")).in("foo").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.getBefore()).isEmpty();
    assertThat(match.getAfter()).isEqualTo("foo");
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void between_openAndCloseAreEqual() {
    Substring.Match match = Substring.between(first("-"), first("-")).in("foo-bar-baz-duh").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.getBefore()).isEqualTo("foo-");
    assertThat(match.getAfter()).isEqualTo("-baz-duh");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_closeBeforeOpenIgnored() {
    Substring.Match match = Substring.between(first("<"), first(">")).in(">foo<bar>").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.getBefore()).isEqualTo(">foo<");
    assertThat(match.getAfter()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void subSequence_negativeBeginIndex() {
    Substring.Match sub = Substring.BEGINNING.in("foo").get();
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(-1, 1));
  }

  @Test public void subSequence_beginIndexGreaterThanEndIndex() {
    Substring.Match sub = Substring.BEGINNING.in("foo").get();
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(1, 0));
  }

  @Test public void subSequence_endIndexGreaterThanLength() {
    Substring.Match sub = Substring.first("foo").in("foo bar").get();
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(0, 4));
  }

  @Test public void subSequence_fullSequence() {
    Substring.Match sub = Substring.first("foo").in("a foo b").get().subSequence(0, 3);
    assertThat(sub.toString()).isEqualTo("foo");
    assertThat(sub.length()).isEqualTo(3);
    assertThat(sub.getIndex()).isEqualTo(2);
    assertThat(sub.getBefore()).isEqualTo("a ");
    assertThat(sub.getAfter()).isEqualTo(" b");
  }

  @Test public void subSequence_partialSequence() {
    Substring.Match sub = Substring.first("fool").in("a fool b").get().subSequence(1, 3);
    assertThat(sub.toString()).isEqualTo("oo");
    assertThat(sub.length()).isEqualTo(2);
    assertThat(sub.getIndex()).isEqualTo(3);
    assertThat(sub.getBefore()).isEqualTo("a f");
    assertThat(sub.getAfter()).isEqualTo("l b");
  }

  @Test public void subSequence_zeroLength() {
    Substring.Match sub = Substring.first("fool").in("fool").get().subSequence(1, 1);
    assertThat(sub.toString()).isEmpty();
    assertThat(sub.length()).isEqualTo(0);
    assertThat(sub.getIndex()).isEqualTo(1);
    assertThat(sub.getBefore()).isEqualTo("f");
    assertThat(sub.getAfter()).isEqualTo("ool");
  }

  @Test public void patternFrom_noMatch() {
    assertThat(Substring.NONE.from("")).isEmpty();
  }

  @Test public void patternFrom_match() {
    assertThat(Substring.first("bar").from("foo bar")).hasValue("bar");
  }

  private static ClassSanityTester newClassSanityTester() {
    return new ClassSanityTester()
        .setDefault(int.class, 0)
        .setDefault(Substring.Pattern.class, Substring.BEGINNING);
  }
}
