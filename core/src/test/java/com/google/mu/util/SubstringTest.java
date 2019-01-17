package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.testing.ClassSanityTester;
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
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void prefix_matchesPrefix() {
    Optional<Substring> match = Substring.prefix("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void prefix_emptyPrefix() {
    Optional<Substring> match = Substring.prefix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void suffix_noMatch() {
    assertThat(Substring.suffix("foo").in("foonot")).isEmpty();
    assertThat(Substring.suffix("foo").in("")).isEmpty();
  }

  @Test public void suffix_matchesFullString() {
    Optional<Substring> match = Substring.suffix("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void suffix_matchesPostfix() {
    Optional<Substring> match = Substring.suffix("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void suffix_emptyPrefix() {
    Optional<Substring> match = Substring.suffix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void firstSnippet_noMatch() {
    assertThat(Substring.first("foo").in("bar")).isEmpty();
    assertThat(Substring.first("foo").in("")).isEmpty();
  }

  @Test public void firstSnippet_matchesFullString() {
    Optional<Substring> match = Substring.first("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstSnippet_matchesPrefix() {
    Optional<Substring> match = Substring.first("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void firstSnippet_matchesPostfix() {
    Optional<Substring> match = Substring.first("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstSnippet_matchesInTheMiddle() {
    Optional<Substring> match = Substring.first("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void firstSnippet_emptySnippet() {
    Optional<Substring> match = Substring.first("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void firstSnippet_matchesFirstOccurrence() {
    Optional<Substring> match = Substring.first("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regex_noMatch() {
    assertThat(Substring.regex(".*x").in("bar")).isEmpty();
    assertThat(Substring.regex(".*x").in("")).isEmpty();
  }

  @Test public void regex_matchesFullString() {
    Optional<Substring> match = Substring.regex(".*oo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void regex_matchesPrefix() {
    Optional<Substring> match = Substring.regex(".*oo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void regex_matchesPostfix() {
    Optional<Substring> match = Substring.regex("b.*").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regex_matchesInTheMiddle() {
    Optional<Substring> match = Substring.regex(".ar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void regex_emptySnippet() {
    Optional<Substring> match = Substring.regex("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void regex_matchesFirstOccurrence() {
    Optional<Substring> match = Substring.regex(".ar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_noMatch() {
    assertThat(Substring.last("foo").in("bar")).isEmpty();
    assertThat(Substring.last("foo").in("")).isEmpty();
  }

  @Test public void lastSnippet_matchesFullString() {
    Optional<Substring> match = Substring.last("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void lastSnippet_matchesPrefix() {
    Optional<Substring> match = Substring.last("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void lastSnippet_matchesPostfix() {
    Optional<Substring> match = Substring.last("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_matchesInTheMiddle() {
    Optional<Substring> match = Substring.last("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_matchesLastOccurrence() {
    Optional<Substring> match = Substring.last("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foobar");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().toString()).isEqualTo("bar");
  }

  @Test public void lastSnippet_emptySnippet() {
    Optional<Substring> match = Substring.last("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().toString()).isEmpty();
  }

  @Test public void firstChar_noMatch() {
    assertThat(Substring.first('f').in("bar")).isEmpty();
    assertThat(Substring.first('f').in("")).isEmpty();
  }

  @Test public void firstChar_matchesFullString() {
    Optional<Substring> match = Substring.first('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void firstChar_matchesPrefix() {
    Optional<Substring> match = Substring.first('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void firstChar_matchesPostfix() {
    Optional<Substring> match = Substring.first('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("fooba");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().toString()).isEqualTo("r");
  }

  @Test public void firstChar_matchesFirstOccurrence() {
    Optional<Substring> match = Substring.first('b').in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("arbarbaz");
    assertThat(match.get().remove()).isEqualTo("fooarbarbaz");
    assertThat(match.get().toString()).isEqualTo("b");
  }

  @Test public void lastChar_noMatch() {
    assertThat(Substring.last('f').in("bar")).isEmpty();
    assertThat(Substring.last('f').in("")).isEmpty();
  }

  @Test public void lastChar_matchesFullString() {
    Optional<Substring> match = Substring.last('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void lastChar_matchesPrefix() {
    Optional<Substring> match = Substring.last('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().toString()).isEqualTo("f");
  }

  @Test public void lastChar_matchesPostfix() {
    Optional<Substring> match = Substring.last('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("fooba");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().toString()).isEqualTo("r");
  }

  @Test public void lastChar_matchesLastOccurrence() {
    Optional<Substring> match = Substring.last('b').in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foobar");
    assertThat(match.get().after()).isEqualTo("az");
    assertThat(match.get().remove()).isEqualTo("foobaraz");
    assertThat(match.get().toString()).isEqualTo("b");
  }

  @Test public void removeFrom() {
    assertThat(Substring.first('f').removeFrom("bar")).isEqualTo("bar");
    assertThat(Substring.first('f').removeFrom("foo")).isEqualTo("oo");
  }

  @Test public void or_firstMatcherMatches() {
    Optional<Substring> match =
        Substring.first('b').or(Substring.first("foo")).in("bar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("ar");
    assertThat(match.get().remove()).isEqualTo("ar");
    assertThat(match.get().toString()).isEqualTo("b");
  }

  @Test public void or_secondMatcherMatches() {
    Optional<Substring> match =
        Substring.first('b').or(Substring.first("foo")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().toString()).isEqualTo("foo");
  }

  @Test public void or_neitherMatches() {
    assertThat(Substring.first("bar").or(Substring.first("foo")).in("baz"))
        .isEmpty();
  }

  @Test public void testNulls() throws Exception {
    new ClassSanityTester().testNulls(Substring.class);
    new ClassSanityTester().forAllPublicStaticMethods(Substring.class).testNulls();
  }
}
