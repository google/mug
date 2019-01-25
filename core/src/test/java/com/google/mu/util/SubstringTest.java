package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
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
    assertThat(Substring.prefix("foo").in("notfoo")).isEmpty();
    assertThat(Substring.prefix("foo").in("")).isEmpty();
  }

  @Test public void prefix_matchesFullString() {
    Optional<Substring> match = Substring.prefix("foo").in("foo");
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
    Optional<Substring> match = Substring.prefix("foo").in("foobar");
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
    Optional<Substring> match = Substring.prefix("").in("foo");
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
    assertThat(Substring.prefix('f').in("notfoo")).isEmpty();
    assertThat(Substring.prefix('f').in("")).isEmpty();
  }

  @Test public void charPrefix_matchesFullString() {
    Optional<Substring> match = Substring.prefix('f').in("f");
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
    Optional<Substring> match = Substring.prefix("f").in("fbar");
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
    assertThat(Substring.suffix("foo").in("foonot")).isEmpty();
    assertThat(Substring.suffix("foo").in("")).isEmpty();
  }

  @Test public void suffix_matchesFullString() {
    Optional<Substring> match = Substring.suffix("foo").in("foo");
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
    Optional<Substring> match = Substring.suffix("bar").in("foobar");
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
    Optional<Substring> match = Substring.suffix("").in("foo");
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
    assertThat(Substring.suffix('f').in("foo")).isEmpty();
    assertThat(Substring.suffix('f').in("")).isEmpty();
  }

  @Test public void charSuffix_matchesFullString() {
    Optional<Substring> match = Substring.suffix('f').in("f");
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
    Optional<Substring> match = Substring.suffix('r').in("bar");
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
    assertThat(Substring.first("foo").in("bar")).isEmpty();
    assertThat(Substring.first("foo").in("")).isEmpty();
  }

  @Test public void firstSnippet_matchesFullString() {
    Optional<Substring> match = Substring.first("foo").in("foo");
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
    Optional<Substring> match = Substring.first("foo").in("foobar");
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
    Optional<Substring> match = Substring.first("bar").in("foobar");
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
    Optional<Substring> match = Substring.first("bar").in("foobarbaz");
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
    Optional<Substring> match = Substring.first("").in("foo");
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
    Optional<Substring> match = Substring.first("bar").in("foobarbarbaz");
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
    assertThat(Substring.last("foo").in("bar")).isEmpty();
    assertThat(Substring.last("foo").in("")).isEmpty();
  }

  @Test public void lastSnippet_matchesFullString() {
    Optional<Substring> match = Substring.last("foo").in("foo");
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
    Optional<Substring> match = Substring.last("foo").in("foobar");
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
    Optional<Substring> match = Substring.last("bar").in("foobar");
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
    Optional<Substring> match = Substring.last("bar").in("foobarbaz");
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
    Optional<Substring> match = Substring.last("bar").in("foobarbarbaz");
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
    Optional<Substring> match = Substring.last("").in("foo");
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
    assertThat(Substring.first('f').in("bar")).isEmpty();
    assertThat(Substring.first('f').in("")).isEmpty();
  }

  @Test public void firstChar_matchesFullString() {
    Optional<Substring> match = Substring.first('f').in("f");
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
    Optional<Substring> match = Substring.first('f').in("foobar");
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
    Optional<Substring> match = Substring.first('r').in("foobar");
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
    Optional<Substring> match = Substring.first('b').in("foobarbarbaz");
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
    assertThat(Substring.last('f').in("bar")).isEmpty();
    assertThat(Substring.last('f').in("")).isEmpty();
  }

  @Test public void lastChar_matchesFullString() {
    Optional<Substring> match = Substring.last('f').in("f");
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
    Optional<Substring> match = Substring.last('f').in("foobar");
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
    Optional<Substring> match = Substring.last('r').in("foobar");
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
    Optional<Substring> match = Substring.last('b').in("foobarbaz");
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
    assertThat(Substring.first('f').removeFrom("bar")).isEqualTo("bar");
  }

  @Test public void removeFrom_match() {
    assertThat(Substring.first('f').removeFrom("foo")).isEqualTo("oo");
  }

  @Test public void replaceFrom_noMatch() {
    assertThat(Substring.first('f').replaceFrom("bar", 'x')).isEqualTo("bar");
    assertThat(Substring.first('f').replaceFrom("bar", "xyz")).isEqualTo("bar");
  }

  @Test public void replaceFrom_match() {
    assertThat(Substring.first('f').replaceFrom("foo", 'b')).isEqualTo("boo");
    assertThat(Substring.first('f').replaceFrom("foo", "bar")).isEqualTo("baroo");
  }

  @Test public void or_firstMatcherMatches() {
    Optional<Substring> match =
        Substring.first('b').or(Substring.first("foo")).in("bar");
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
        Substring.first('b').or(Substring.first("foo")).in("foo");
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
    assertThat(Substring.first("bar").or(Substring.first("foo")).in("baz"))
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
    new ClassSanityTester().testNulls(Substring.class);
    new ClassSanityTester()
        .setDefault(Substring.class, Substring.of(""))
        .forAllPublicStaticMethods(Substring.class).testNulls();
  }

  @Test public void testSerializable() throws Exception {
    new ClassSanityTester()
        .forAllPublicStaticMethods(Substring.class)
        .thatReturn(Substring.Pattern.class)
        .testSerializable();
  }

  @Test public void andBefore_noMatch() {
    assertThat(Substring.first("://").andBefore().in("abc")).isEmpty();
  }

  @Test public void andBefore_matchAtPrefix() {
    assertThat(Substring.first("://").andBefore().removeFrom("://foo")).isEqualTo("foo");
  }

  @Test public void andBefore_matchInTheMiddle() {
    assertThat(Substring.first("://").andBefore().removeFrom("http://foo")).isEqualTo("foo");
  }

  @Test public void andAfter_noMatch() {
    assertThat(Substring.first("//").andAfter().in("abc")).isEmpty();
  }

  @Test public void andAfter_matchAtSuffix() {
    assertThat(Substring.first("//").andAfter().removeFrom("foo//")).isEqualTo("foo");
  }

  @Test public void andAfter_matchInTheMiddle() {
    assertThat(Substring.first("//").andAfter().removeFrom("foo // bar")).isEqualTo("foo ");
  }

  @Test public void before_noMatch() {
    assertThat(Substring.first("://").before().in("abc")).isEmpty();
  }

  @Test public void before_matchAtPrefix() {
    assertThat(Substring.first("//").before().removeFrom("//foo")).isEqualTo("//foo");
  }

  @Test public void before_matchInTheMiddle() {
    assertThat(Substring.first("//").before().removeFrom("http://foo")).isEqualTo("//foo");
  }

  @Test public void after_noMatch() {
    assertThat(Substring.first("//").after().in("abc")).isEmpty();
  }

  @Test public void after_matchAtSuffix() {
    assertThat(Substring.last('.').after().removeFrom("foo.")).isEqualTo("foo.");
  }

  @Test public void after_matchInTheMiddle() {
    assertThat(Substring.last('.').after().removeFrom("foo. bar")).isEqualTo("foo.");
  }

  @Test public void trivialSubstring() {
    Substring substring = Substring.of("bar");
    assertThat(substring.toString()).isEqualTo("bar");
    assertThat(substring.getIndex()).isEqualTo(0);
    assertThat(substring.length()).isEqualTo(3);
    assertThat(substring.remove()).isEmpty();
    assertThat(substring.getBefore()).isEmpty();
    assertThat(substring.getAfter()).isEmpty();
    assertThat(substring.charAt(0)).isEqualTo('b');
    assertThat(substring.charAt(1)).isEqualTo('a');
    assertThat(substring.charAt(2)).isEqualTo('r');
  }

  @Test public void charAt_fullSubstring_invalidIndex() {
    Substring full = Substring.of("bar");
    assertThrows(IndexOutOfBoundsException.class, () -> full.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> full.charAt(3));
  }

  @Test public void charAt_partialSubstring_invalidIndex() {
    Substring sub = Substring.of("bar").subSequence(1, 2);
    assertThrows(IndexOutOfBoundsException.class, () -> sub.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> sub.charAt(1));
  }

  @Test public void charAt_validIndex() {
    Substring sub = Substring.of("bar").subSequence(1, 3);
    assertThat(sub.charAt(0)).isEqualTo('a');
    assertThat(sub.charAt(1)).isEqualTo('r');
  }

  @Test public void subSequence_invalidIndex() {
    Substring full = Substring.of("bar");
    assertThrows(IndexOutOfBoundsException.class, () -> full.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> full.subSequence(0, 4));
    assertThrows(IndexOutOfBoundsException.class, () -> full.subSequence(1, 0));
    Substring sub = full.subSequence(1, 2);
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(-1, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(0, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> sub.subSequence(1, 0));
  }

  @Test public void subSequence_emptySubSequence() {
    Substring full = Substring.of("bar");
    assertThat(full.subSequence(0, 0).toString()).isEmpty();
    assertThat(full.subSequence(1, 1).toString()).isEmpty();
    assertThat(full.subSequence(2, 2).toString()).isEmpty();
  }

  @Test public void subSequence_fullSubSequence() {
    Substring full = Substring.of("bar").subSequence(0, 3);
    assertThat(full.charAt(0)).isEqualTo('b');
    assertThat(full.charAt(1)).isEqualTo('a');
    assertThat(full.charAt(2)).isEqualTo('r');
    assertThat(full.toString()).isEqualTo("bar");
    assertThat(full.length()).isEqualTo(3);
    assertThat(full.getIndex()).isEqualTo(0);
  }

  @Test public void subSequence_partialSubSequence() {
    Substring full = Substring.of("bar").subSequence(1, 2);
    assertThat(full.charAt(0)).isEqualTo('a');
    assertThat(full.toString()).isEqualTo("a");
    assertThat(full.length()).isEqualTo(1);
    assertThat(full.getIndex()).isEqualTo(1);
  }

  @Test public void subSequenceOfSubSequence() {
    Substring sub = Substring.of("barak").subSequence(1, 3).subSequence(1, 2);
    assertThat(sub.charAt(0)).isEqualTo('r');
    assertThat(sub.toString()).isEqualTo("r");
    assertThat(sub.length()).isEqualTo(1);
    assertThat(sub.getIndex()).isEqualTo(2);
    assertThat(sub.getBefore()).isEqualTo("a");
    assertThat(sub.getAfter()).isEmpty();
    assertThat(sub.before().remove()).isEqualTo("r");
    assertThat(sub.after().remove()).isEqualTo("ar");
    Substring sub2 = sub.subSequence(0, 0);
    assertThat(sub2.toString()).isEqualTo("");
    assertThat(sub2.before().toString()).isEmpty();
    assertThat(sub2.after().toString()).isEqualTo("r");
  }

  @Test public void subSequenceOfSubSequence_usedInRegexMatcher() {
    Substring sub = Substring.of("(foo123]").subSequence(0, 7).subSequence(1, 7);
    assertThat(java.util.regex.Pattern.compile("^((\\w){3})((\\d){3})$").matcher(sub).replaceAll("$3$1"))
        .isEqualTo("123foo");
  }

  @Test public void emptySubSequence_atEnd() {
    Substring sub = Substring.of("bar").subSequence(3, 3);
    assertThat(sub.toString()).isEmpty();
    assertThat(sub.before().toString()).isEqualTo("bar");
    assertThat(sub.after().toString()).isEmpty();
  }

  @Test public void emptySubSequence_atBeginning() {
    Substring sub = Substring.of("bar").subSequence(0, 0);
    assertThat(sub.toString()).isEmpty();
    assertThat(sub.before().toString()).isEmpty();
    assertThat(sub.after().toString()).isEqualTo("bar");
  }

  @Test public void emptySubSequence_inTheMiddle() {
    Substring sub = Substring.of("bar").subSequence(1, 1);
    assertThat(sub.toString()).isEmpty();
    assertThat(sub.before().toString()).isEqualTo("b");
    assertThat(sub.after().toString()).isEqualTo("ar");
  }
  
  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(Substring.ALL.in("foo"), Substring.ALL.in("foo"))
        .addEqualityGroup(Substring.ALL.in("bar"))
        .addEqualityGroup(Substring.suffix("bar").in("foobar"), Substring.suffix("bar").in("foobar"))
        .addEqualityGroup(Substring.prefix("bar").in("barfoo"))
        .addEqualityGroup(Substring.prefix("ba").in("barfoo"))
        .addEqualityGroup(Substring.prefix("").in("foo"))
        .addEqualityGroup(Substring.suffix("").in("foo"))
        .addEqualityGroup(Substring.prefix("").in("foobar"))
        .testEquals();
  }
}
