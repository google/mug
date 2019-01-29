package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.last;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.suffix;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;

import java.util.Optional;
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
    Optional<Substring> match = prefix("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void prefix_matchesPrefix() {
    Optional<Substring> match = prefix("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith('a')).isEqualTo("abar");
    assertThat(match.get().replaceWith("at")).isEqualTo("atbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void prefix_emptyPrefix() {
    Optional<Substring> match = prefix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('b')).isEqualTo("bfoo");
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
    Optional<Substring> match = prefix('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void charPrefix_matchesPrefix() {
    Optional<Substring> match = prefix("f").in("fbar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith('a')).isEqualTo("abar");
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
    Optional<Substring> match = suffix("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void suffix_matchesPostfix() {
    Optional<Substring> match = suffix("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('c')).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void suffix_emptyPrefix() {
    Optional<Substring> match = suffix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('b')).isEqualTo("foob");
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
    Optional<Substring> match = suffix('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void charSuffix_matchesPostfix() {
    Optional<Substring> match = suffix('r').in("bar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("ba");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("ba");
    assertThat(match.get().replaceWith('c')).isEqualTo("bac");
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
    Optional<Substring> match = first("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstSnippet_matchesPrefix() {
    Optional<Substring> match = first("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith('c')).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstSnippet_matchesPostfix() {
    Optional<Substring> match = first("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('c')).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstSnippet_matchesInTheMiddle() {
    Optional<Substring> match = first("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstSnippet_emptySnippet() {
    Optional<Substring> match = first("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('b')).isEqualTo("bfoo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void firstSnippet_matchesFirstOccurrence() {
    Optional<Substring> match = first("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foocbarbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regex_noMatch() {
    assertThat(Substring.regex(".*x").in("bar")).isEmpty();
    assertThat(Substring.regex(".*x").in("")).isEmpty();
  }

  @Test public void regex_matchesFullString() {
    Optional<Substring> match = Substring.regex(".*oo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void regex_matchesPrefix() {
    Optional<Substring> match = Substring.regex(".*oo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith('c')).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void regex_matchesPrefixWithStartingAnchor() {
    Optional<Substring> match = Substring.regex("^.*oo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith('c')).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void regex_doesNotMatchPrefixDueToStartingAnchor() {
    assertThat(Substring.regex("^oob.").in("foobar")).isEmpty();
  }

  @Test public void regex_matchesPostfix() {
    Optional<Substring> match = Substring.regex("b.*").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('c')).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regex_matchesPostfixWithEndingAnchor() {
    Optional<Substring> match = Substring.regex("b.*$").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('c')).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regex_doesNotMatchPostfixDueToEndingAnchor() {
    assertThat(Substring.regex("b.$").in("foobar")).isEmpty();
  }

  @Test public void regex_matchesInTheMiddle() {
    Optional<Substring> match = Substring.regex(".ar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regex_emptySnippet() {
    Optional<Substring> match = Substring.regex("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('b')).isEqualTo("bfoo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void regex_matchesFirstOccurrence() {
    Optional<Substring> match = Substring.regex(".ar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foocbarbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regexGroup_noMatch() {
    assertThat(Substring.regexGroup(".*x", 1).in("bar")).isEmpty();
    assertThat(Substring.regexGroup("(.*x)", 1).in("bar")).isEmpty();
    assertThat(Substring.regexGroup(".*(x)", 1).in("bar")).isEmpty();
    assertThat(Substring.regexGroup(".*x", 1).in("")).isEmpty();
  }

  @Test public void regexGroup_matchesFirstGroup() {
    Optional<Substring> match = Substring.regexGroup("(f.)b.*", 1).in("fobar");
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith('c')).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(2);
    assertThat(match.get().toString()).isEqualTo("fo");
  }

  @Test public void regexGroup_matchesSecondGroup() {
    Optional<Substring> match = Substring.regexGroup("f(o.)(ba.)", 2).in("foobarbaz");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regexGroup_group0() {
    Optional<Substring> match = Substring.regexGroup("f(o.)(ba.).*", 0).in("foobarbaz");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('c')).isEqualTo("c");
    assertThat(match.get().replaceWith("car")).isEqualTo("car");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(9);
    assertThat(match.get().toString()).isEqualTo("foobarbaz");
  }

  @Test public void regexGroup_negativeGroup() {
    assertThrows(IllegalArgumentException.class, () -> Substring.regexGroup(".", -1));
  }

  @Test public void regexGroup_invalidGroupIndex() {
    Substring.Pattern pattern = Substring.regexGroup("f(o.)(ba.)", 3);
    assertThrows(IndexOutOfBoundsException.class, () -> pattern.in("foobarbaz"));
  }

  @Test public void lastSnippet_noMatch() {
    assertThat(last("foo").in("bar")).isEmpty();
    assertThat(last("foo").in("")).isEmpty();
  }

  @Test public void lastSnippet_matchesFullString() {
    Optional<Substring> match = last("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void lastSnippet_matchesPrefix() {
    Optional<Substring> match = last("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith('c')).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void lastSnippet_matchesPostfix() {
    Optional<Substring> match = last("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('c')).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_matchesInTheMiddle() {
    Optional<Substring> match = last("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().getIndex()).isEqualTo(3);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_matchesLastOccurrence() {
    Optional<Substring> match = last("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foobar");
    assertThat(match.get().getAfter()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foobarcbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobarcarbaz");
    assertThat(match.get().getIndex()).isEqualTo(6);
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_emptySnippet() {
    Optional<Substring> match = last("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith('b')).isEqualTo("foob");
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
    Optional<Substring> match = first('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void firstChar_matchesPrefix() {
    Optional<Substring> match = first('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith('c')).isEqualTo("coobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void firstChar_matchesPostfix() {
    Optional<Substring> match = first('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("fooba");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().replaceWith('c')).isEqualTo("foobac");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobacar");
    assertThat(match.get().getIndex()).isEqualTo(5);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
  }

  @Test public void firstChar_matchesFirstOccurrence() {
    Optional<Substring> match = first('b').in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foo");
    assertThat(match.get().getAfter()).isEqualTo("arbarbaz");
    assertThat(match.get().remove()).isEqualTo("fooarbarbaz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foocarbarbaz");
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
    Optional<Substring> match = last('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void lastChar_matchesPrefix() {
    Optional<Substring> match = last('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith('c')).isEqualTo("coobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void lastChar_matchesPostfix() {
    Optional<Substring> match = last('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("fooba");
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().replaceWith('c')).isEqualTo("foobac");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobacar");
    assertThat(match.get().getIndex()).isEqualTo(5);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
  }

  @Test public void lastChar_matchesLastOccurrence() {
    Optional<Substring> match = last('b').in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEqualTo("foobar");
    assertThat(match.get().getAfter()).isEqualTo("az");
    assertThat(match.get().remove()).isEqualTo("foobaraz");
    assertThat(match.get().replaceWith('c')).isEqualTo("foobarcaz");
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
    assertThat(first('f').replaceFrom("bar", 'x')).isEqualTo("bar");
    assertThat(first('f').replaceFrom("bar", "xyz")).isEqualTo("bar");
  }

  @Test public void replaceFrom_match() {
    assertThat(first('f').replaceFrom("foo", 'b')).isEqualTo("boo");
    assertThat(first('f').replaceFrom("foo", "bar")).isEqualTo("baroo");
  }

  @Test public void or_firstMatcherMatches() {
    Optional<Substring> match =
        first('b').or(first("foo")).in("bar");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEqualTo("ar");
    assertThat(match.get().remove()).isEqualTo("ar");
    assertThat(match.get().replaceWith('c')).isEqualTo("car");
    assertThat(match.get().replaceWith("coo")).isEqualTo("cooar");
    assertThat(match.get().getIndex()).isEqualTo(0);
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
  }

  @Test public void or_secondMatcherMatches() {
    Optional<Substring> match =
        first('b').or(first("foo")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().getBefore()).isEmpty();
    assertThat(match.get().getAfter()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith('b')).isEqualTo("b");
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
    assertThat(Substring.NONE.replaceFrom("foo", 'x')).isEqualTo("foo");
    assertThat(Substring.NONE.replaceFrom("foo", "xyz")).isEqualTo("foo");
  }

  @Test public void all() {
    assertThat(Substring.ALL.in("foo").get().toString()).isEqualTo("foo");
    assertThat(Substring.ALL.removeFrom("foo")).isEmpty();
    assertThat(Substring.ALL.replaceFrom("foo", 'x')).isEqualTo("x");
    assertThat(Substring.ALL.replaceFrom("foo", "xyz")).isEqualTo("xyz");
  }

  @Test public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicInstanceMethods(Substring.ALL.in("foobar").get());
    new ClassSanityTester()
        .setDefault(Substring.Pattern.class, Substring.ALL)
        .testNulls(Substring.class);
    new ClassSanityTester()
        .setDefault(Substring.Pattern.class, Substring.ALL)
        .forAllPublicStaticMethods(Substring.class).testNulls();
  }

  @Test public void testSerializable() throws Exception {
    new ClassSanityTester()
        .setDefault(Substring.Pattern.class, Substring.ALL)
        .forAllPublicStaticMethods(Substring.class)
        .testSerializable();
  }

  @Test public void upTo_noMatch() {
    assertThat(Substring.upTo(first("://")).in("abc")).isEmpty();
  }

  @Test public void upTo_matchAtPrefix() {
    assertThat(Substring.upTo(first("://")).removeFrom("://foo")).isEqualTo("foo");
  }

  @Test public void upTo_matchInTheMiddle() {
    assertThat(Substring.upTo(first("://")).removeFrom("http://foo")).isEqualTo("foo");
  }

  @Test public void from_noMatch() {
    assertThat(Substring.from(first("//")).in("abc")).isEmpty();
  }

  @Test public void from_matchAtSuffix() {
    assertThat(Substring.from(first("//")).removeFrom("foo//")).isEqualTo("foo");
  }

  @Test public void from_matchInTheMiddle() {
    assertThat(Substring.from(first("//")).removeFrom("foo // bar")).isEqualTo("foo ");
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
    Substring match = Substring.between(last('<'), last('>')).in("foo<bar>baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.getBefore()).isEqualTo("foo<");
    assertThat(match.getAfter()).isEqualTo(">baz");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_matchedByEqualDelimiters() {
    Substring match = Substring.between(first('-'), first('-')).in("foo-bar-baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.getBefore()).isEqualTo("foo-");
    assertThat(match.getAfter()).isEqualTo("-baz");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_matchedFully() {
    Substring match = Substring.between(last('<'), last('>')).in("<foo>").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.getBefore()).isEqualTo("<");
    assertThat(match.getAfter()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_outerMatchesEmpty() {
    Substring match = Substring.between(first(""), last('.')).in("foo.").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.getBefore()).isEmpty();
    assertThat(match.getAfter()).isEqualTo(".");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_innerMatchesEmpty() {
    Substring match = Substring.between(first(":"), last("")).in("hello:world").get();
    assertThat(match.toString()).isEqualTo("world");
    assertThat(match.getBefore()).isEqualTo("hello:");
    assertThat(match.getAfter()).isEmpty();
    assertThat(match.length()).isEqualTo(5);
  }

  @Test public void between_matchedLastOccurrence() {
    Substring match = Substring.between(last('<'), last('>')).in("<foo><bar> <baz>").get();
    assertThat(match.toString()).isEqualTo("baz");
    assertThat(match.getBefore()).isEqualTo("<foo><bar> <");
    assertThat(match.getAfter()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void between_matchedIncludingDelimiters() {
    Substring match = Substring.between(Substring.before(last('<')), Substring.after(last('>')))
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
  }

  @Test public void between_matchesNone() {
    assertThat(Substring.between(first('<'), first('>')).in("foo")).isEmpty();
  }

  @Test public void within_outerDoesNotMatch() {
    Substring.Pattern pattern = Substring.before(last(')')).within(Substring.after(first('(')));
    assertThat(pattern.in("abc)")).isEmpty();
  }

  @Test public void within_innerDoesNotMatch() {
    Substring.Pattern pattern = Substring.before(last(')')).within(Substring.after(first('(')));
    assertThat(pattern.in("(abc")).isEmpty();
  }

  @Test public void within_innerDoesNotMatchAfterOuterMatched() {
    Substring.Pattern pattern = Substring.before(last('*')).within(Substring.after(first('*')));
    assertThat(pattern.in("a*bc")).isEmpty();
  }

  @Test public void subSequence_negativeBeginIndex() {
    Substring sub = Substring.ALL.in("foo").get();
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(-1, 1));
  }

  @Test public void subSequence_beginIndexGreaterThanEndIndex() {
    Substring sub = Substring.ALL.in("foo").get();
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(1, 0));
  }

  @Test public void subSequence_endIndexGreaterThanLength() {
    Substring sub = Substring.first("foo").in("foo bar").get();
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(0, 4));
  }

  @Test public void subSequence_fullSequence() {
    Substring sub = Substring.first("foo").in("a foo b").get().subSequence(0, 3);
    assertThat(sub.toString()).isEqualTo("foo");
    assertThat(sub.length()).isEqualTo(3);
    assertThat(sub.getIndex()).isEqualTo(2);
    assertThat(sub.getBefore()).isEqualTo("a ");
    assertThat(sub.getAfter()).isEqualTo(" b");
  }

  @Test public void subSequence_partialSequence() {
    Substring sub = Substring.first("fool").in("a fool b").get().subSequence(1, 3);
    assertThat(sub.toString()).isEqualTo("oo");
    assertThat(sub.length()).isEqualTo(2);
    assertThat(sub.getIndex()).isEqualTo(3);
    assertThat(sub.getBefore()).isEqualTo("a f");
    assertThat(sub.getAfter()).isEqualTo("l b");
  }

  @Test public void subSequence_zeroLength() {
    Substring sub = Substring.first("fool").in("fool").get().subSequence(1, 1);
    assertThat(sub.toString()).isEmpty();
    assertThat(sub.length()).isEqualTo(0);
    assertThat(sub.getIndex()).isEqualTo(1);
    assertThat(sub.getBefore()).isEqualTo("f");
    assertThat(sub.getAfter()).isEqualTo("ool");
  }

  @Test public void within_bothInnerAndOuterMatched() {
    Substring.Pattern pattern = Substring.before(last('*')).within(Substring.after(first('*')));
    Substring substring = pattern.in("*foo*bar").get();
    assertThat(substring.toString()).isEqualTo("foo");
    assertThat(substring.getBefore()).isEqualTo("*");
    assertThat(substring.getAfter()).isEqualTo("*bar");
    assertThat(substring.remove()).isEqualTo("**bar");
    assertThat(substring.getIndex()).isEqualTo(1);
    assertThat(substring.length()).isEqualTo(3);
  }
  
  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(Substring.ALL.in("foo"), Substring.ALL.in("foo"))
        .addEqualityGroup(Substring.ALL.in("bar"))
        .addEqualityGroup(suffix("bar").in("foobar"), suffix("bar").in("foobar"))
        .addEqualityGroup(prefix("bar").in("barfoo"))
        .addEqualityGroup(prefix("ba").in("barfoo"))
        .addEqualityGroup(prefix("").in("foo"))
        .addEqualityGroup(suffix("").in("foo"))
        .addEqualityGroup(prefix("").in("foobar"))
        .testEquals();
  }
}
