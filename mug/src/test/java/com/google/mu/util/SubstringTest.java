package com.google.mu.util;

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.BEGINNING;
import static com.google.mu.util.Substring.END;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.last;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.spanningInOrder;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.Substring.upToIncluding;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Spliterator;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.NullPointerTester;
import com.google.mu.util.Substring.Match;

@RunWith(JUnit4.class)
public class SubstringTest {
  @Test public void none() {
    assertThat(Substring.NONE.in("foo")).isEmpty();
    assertThat(Substring.NONE.removeFrom("foo")).isEqualTo("foo");
    assertThat(Substring.NONE.replaceFrom("foo", "xyz")).isEqualTo("foo");
  }

  @Test public void none_toString() {
    assertThat(Substring.NONE.toString()).isEqualTo("NONE");
  }

  @Test
  public void beginning() {
    assertThat(BEGINNING.from("foo")).hasValue("");
    assertThat(BEGINNING.removeFrom("foo")).isEqualTo("foo");
    assertThat(BEGINNING.replaceFrom("foo", "begin ")).isEqualTo("begin foo");
    assertThat(BEGINNING.in("foo").get().before()).isEmpty();
    assertThat(BEGINNING.in("foo").get().after()).isEqualTo("foo");
    assertThat(BEGINNING.iterateIn("foo").map(Object::toString)).containsExactly("");
    assertThat(BEGINNING.iterateIn("").map(Object::toString)).containsExactly("");
  }

  @Test
  public void beginning_toString() {
    assertThat(BEGINNING.toString()).isEqualTo("BEGINNING");
  }

  @Test
  public void end() {
    assertThat(END.from("foo")).hasValue("");
    assertThat(END.removeFrom("foo")).isEqualTo("foo");
    assertThat(END.replaceFrom("foo", " end")).isEqualTo("foo end");
    assertThat(END.in("foo").get().before()).isEqualTo("foo");
    assertThat(END.in("foo").get().after()).isEmpty();
    assertThat(END.iterateIn("foo").map(Object::toString)).containsExactly("");
    assertThat(END.iterateIn("").map(Object::toString)).containsExactly("");
  }

  @Test
  public void end_toString() {
    assertThat(END.toString()).isEqualTo("END");
  }

  @Test
  public void prefix_toString() {
    assertThat(prefix("foo").toString()).isEqualTo("foo");
  }

  @Test
  public void prefix_noMatch() {
    assertThat(prefix("foo").in("notfoo")).isEmpty();
    assertThat(prefix("foo").in("")).isEmpty();
    assertThat(prefix("foo").iterateIn("notfoo")).isEmpty();
    assertThat(prefix("foo").iterateIn("")).isEmpty();
  }

  @Test
  public void prefix_matchesFullString() {
    Optional<Substring.Match> match = prefix("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(prefix("foo").iterateIn("foo").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void prefix_matchesPrefix() {
    Optional<Substring.Match> match = prefix("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("a")).isEqualTo("abar");
    assertThat(match.get().replaceWith("at")).isEqualTo("atbar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(prefix("foo").iterateIn("foobar").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void prefix_matchesPrefixFollowedByIdentialSubstring() {
    Optional<Substring.Match> match = prefix("foo").in("foofoobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foobar");
    assertThat(match.get().remove()).isEqualTo("foobar");
    assertThat(match.get().replaceWith("")).isEqualTo("foobar");
    assertThat(match.get().replaceWith("a")).isEqualTo("afoobar");
    assertThat(match.get().replaceWith("at")).isEqualTo("atfoobar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(prefix("foo").iterateIn("foofoobar").map(Object::toString))
        .containsExactly("foo", "foo");
    assertThat(prefix("foo").iterateIn("foofoo").map(Object::toString))
        .containsExactly("foo", "foo");
  }

  @Test
  public void prefix_emptyPrefix() {
    Optional<Substring.Match> match = prefix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("b")).isEqualTo("bfoo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
    assertThat(prefix("").iterateIn("foo").map(Object::toString)).containsExactly("");
  }

  @Test
  public void charPrefix_toString() {
    assertThat(prefix('a').toString()).isEqualTo("a");
  }

  @Test
  public void charPrefix_noMatch() {
    assertThat(prefix('f').in("notfoo")).isEmpty();
    assertThat(prefix('f').in("")).isEmpty();
    assertThat(prefix('f').iterateIn("")).isEmpty();
  }

  @Test
  public void charPrefix_matchesFullString() {
    Optional<Substring.Match> match = prefix('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(prefix('f').iterateIn("f").map(Object::toString)).containsExactly("f");
  }

  @Test
  public void charPrefix_matchesPrefix() {
    Optional<Substring.Match> match = prefix("f").in("fbar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("a")).isEqualTo("abar");
    assertThat(match.get().replaceWith("at")).isEqualTo("atbar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(prefix('f').iterateIn("fbar").map(Object::toString)).containsExactly("f");
  }

  @Test
  public void charPrefix_matchesPrefixFollowedByIdenticalChar() {
    Optional<Substring.Match> match = prefix("f").in("ffar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("far");
    assertThat(match.get().remove()).isEqualTo("far");
    assertThat(match.get().replaceWith("")).isEqualTo("far");
    assertThat(match.get().replaceWith("a")).isEqualTo("afar");
    assertThat(match.get().replaceWith("at")).isEqualTo("atfar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(prefix('f').iterateIn("ffar").map(Object::toString)).containsExactly("f", "f");
  }

  @Test
  public void suffix_toString() {
    assertThat(suffix("foo").toString()).isEqualTo("foo");
  }

  @Test
  public void suffix_noMatch() {
    assertThat(suffix("foo").in("foonot")).isEmpty();
    assertThat(suffix("foo").in("")).isEmpty();
    assertThat(suffix("foo").iterateIn("")).isEmpty();
  }

  @Test
  public void suffix_matchesFullString() {
    Optional<Substring.Match> match = suffix("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(suffix("foo").iterateIn("foo").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void suffix_matchesSuffix() {
    Optional<Substring.Match> match = suffix("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("c")).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(suffix("bar").iterateIn("foobar").map(Object::toString)).containsExactly("bar");
  }

  @Test
  public void suffix_matchesSuffixPrecededByIdenticalString() {
    Optional<Substring.Match> match = suffix("bar").in("foobarbar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foobar");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foobar");
    assertThat(match.get().replaceWith("")).isEqualTo("foobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("foobarc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobarcar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(suffix("bar").iterateIn("foobarbar").map(Object::toString)).containsExactly("bar");
  }

  @Test
  public void suffix_emptySuffix() {
    Optional<Substring.Match> match = suffix("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("b")).isEqualTo("foob");
    assertThat(match.get().replaceWith("bar")).isEqualTo("foobar");
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
    assertThat(suffix("").iterateIn("foo").map(Object::toString)).containsExactly("");
  }

  @Test
  public void charSuffix_toString() {
    assertThat(suffix('f').toString()).isEqualTo("f");
  }

  @Test
  public void charSuffix_noMatch() {
    assertThat(suffix('f').in("foo")).isEmpty();
    assertThat(suffix('f').in("")).isEmpty();
    assertThat(suffix('f').iterateIn("foo")).isEmpty();
    assertThat(suffix('f').iterateIn("")).isEmpty();
  }

  @Test
  public void charSuffix_matchesFullString() {
    Optional<Substring.Match> match = suffix('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(suffix('f').iterateIn("f").map(Object::toString)).containsExactly("f");
  }

  @Test
  public void charSuffix_matchesSuffix() {
    Optional<Substring.Match> match = suffix('r').in("bar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("ba");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("ba");
    assertThat(match.get().replaceWith("")).isEqualTo("ba");
    assertThat(match.get().replaceWith("c")).isEqualTo("bac");
    assertThat(match.get().replaceWith("car")).isEqualTo("bacar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
    assertThat(suffix('r').iterateIn("bar").map(Object::toString)).containsExactly("r");
  }

  @Test
  public void charSuffix_matchesSuffixPrecededByIdenticalString() {
    Optional<Substring.Match> match = suffix('r').in("barr");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("bar");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("c")).isEqualTo("barc");
    assertThat(match.get().replaceWith("car")).isEqualTo("barcar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
    assertThat(suffix('r').iterateIn("barr").map(Object::toString)).containsExactly("r");
  }

  @Test
  public void firstSnippet_toString() {
    assertThat(first("foo").toString()).isEqualTo("first('foo')");
  }

  @Test
  public void firstSnippet_noMatch() {
    assertThat(first("foo").in("bar")).isEmpty();
    assertThat(first("foo").in("")).isEmpty();
    assertThat(first("foo").iterateIn("")).isEmpty();
  }

  @Test
  public void firstSnippet_matchesFullString() {
    Optional<Substring.Match> match = first("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first("foo").iterateIn("foo").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void firstSnippet_matchesPrefix() {
    Optional<Substring.Match> match = first("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first("foo").iterateIn("foobar").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void firstSnippet_matchesPrefixFollowedByIdenticalString() {
    Optional<Substring.Match> match = first("foo").in("foofoobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foobar");
    assertThat(match.get().remove()).isEqualTo("foobar");
    assertThat(match.get().replaceWith("")).isEqualTo("foobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cfoobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carfoobar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first("foo").iterateIn("foofoobar").map(Object::toString))
        .containsExactly("foo", "foo");
    assertThat(first("foo").iterateIn("foobarfoo").map(Object::toString))
        .containsExactly("foo", "foo");
  }

  @Test
  public void firstSnippet_matchesSuffix() {
    Optional<Substring.Match> match = first("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("c")).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first("bar").iterateIn("foobar").map(Object::toString)).containsExactly("bar");
  }

  @Test
  public void firstSnippet_matchesInTheMiddle() {
    Optional<Substring.Match> match = first("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first("bar").iterateIn("foobarbaz").map(Object::toString)).containsExactly("bar");
    assertThat(first("bar").iterateIn("foobarbarbazbar").map(Object::toString))
        .containsExactly("bar", "bar", "bar");
  }

  @Test
  public void firstSnippet_emptySnippet() {
    Optional<Substring.Match> match = first("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("b")).isEqualTo("bfoo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
    assertThat(first("").iterateIn("foo").map(Object::toString)).containsExactly("");
  }

  @Test
  public void firstSnippet_matchesFirstOccurrence() {
    Optional<Substring.Match> match = first("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocbarbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first("bar").iterateIn("foobarbarbaz").map(Object::toString))
        .containsExactly("bar", "bar");
    assertThat(first("bar").iterateIn("foobarcarbarbaz").map(Object::toString))
        .containsExactly("bar", "bar");
  }

  @Test
  public void regex_toString() {
    assertThat(first(Pattern.compile(".*x")).toString()).isEqualTo("first(\".*x\", 0)");
  }

  @Test
  public void regex_noMatch() {
    assertThat(first(Pattern.compile(".*x")).in("bar")).isEmpty();
    assertThat(first(Pattern.compile(".*x")).in("")).isEmpty();
    assertThat(first(Pattern.compile(".*x")).iterateIn("")).isEmpty();
  }

  @Test
  public void regex_matchesFullString() {
    Optional<Substring.Match> match = first(Pattern.compile(".*oo")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first(Pattern.compile(".*oo")).iterateIn("foo").map(Object::toString))
        .containsExactly("foo");
  }

  @Test
  public void regex_matchesPrefix() {
    Optional<Substring.Match> match = first(Pattern.compile(".*oo")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first(Pattern.compile(".*oo")).iterateIn("foobar").map(Object::toString))
        .containsExactly("foo");
  }

  @Test
  public void regex_matchesPrefixFollowedBySamePattern() {
    Optional<Substring.Match> match = first(Pattern.compile(".oo")).in("foodoobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("doobar");
    assertThat(match.get().remove()).isEqualTo("doobar");
    assertThat(match.get().replaceWith("")).isEqualTo("doobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cdoobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("cardoobar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first(Pattern.compile(".oo")).iterateIn("foodoobar").map(Object::toString))
        .containsExactly("foo", "doo");
  }

  @Test
  public void regex_matchesPrefixWithStartingAnchor() {
    Optional<Substring.Match> match = first(Pattern.compile("^.oo")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first(Pattern.compile("^.oo")).iterateIn("foobar").map(Object::toString))
        .containsExactly("foo");
  }

  @Test
  public void regex_matchesPrefixFollowedBySamePattern_withStartingAnchor() {
    Optional<Substring.Match> match = first(Pattern.compile("^.oo")).in("foodoobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("doobar");
    assertThat(match.get().remove()).isEqualTo("doobar");
    assertThat(match.get().replaceWith("")).isEqualTo("doobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cdoobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("cardoobar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(first(Pattern.compile("^.oo")).iterateIn("foodoobar").map(Object::toString))
        .containsExactly("foo");
  }

  @Test
  public void regex_doesNotMatchPrefixDueToStartingAnchor() {
    assertThat(first(Pattern.compile("^oob.")).in("foobar")).isEmpty();
    assertThat(first(Pattern.compile("^oob")).iterateIn("foobar")).isEmpty();
  }

  @Test
  public void regex_matchesSuffix() {
    Optional<Substring.Match> match = first(Pattern.compile("b.*")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("c")).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first(Pattern.compile("b.*")).iterateIn("foobar").map(Object::toString))
        .containsExactly("bar");
  }

  @Test
  public void regex_matchesSuffixWithEndingAnchor() {
    Optional<Substring.Match> match = first(Pattern.compile("b.*$")).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("c")).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first(Pattern.compile("b.*$")).iterateIn("foobar").map(Object::toString))
        .containsExactly("bar");
  }

  @Test
  public void regex_doesNotMatchPostfixDueToEndingAnchor() {
    assertThat(first(Pattern.compile("b.$")).in("foobar")).isEmpty();
    assertThat(first(Pattern.compile("b.$")).iterateIn("foobar")).isEmpty();
  }

  @Test
  public void regex_matchesInTheMiddle() {
    Optional<Substring.Match> match = first(Pattern.compile(".ar")).in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first(Pattern.compile(".ar")).iterateIn("foobarbaz").map(Object::toString))
        .containsExactly("bar");
    assertThat(first(Pattern.compile(".ar")).iterateIn("foobarzbarbaz").map(Object::toString))
        .containsExactly("bar", "bar");
    assertThat(first(Pattern.compile(".ar")).iterateIn("foobarbazbar").map(Object::toString))
        .containsExactly("bar", "bar");
  }

  @Test
  public void regex_emptySnippet() {
    Optional<Substring.Match> match = first(Pattern.compile("")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("foo");
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("b")).isEqualTo("bfoo");
    assertThat(match.get().replaceWith("bar")).isEqualTo("barfoo");
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
    assertThat(first(Pattern.compile("")).iterateIn("foo").map(Object::toString))
        .containsExactly("");
    assertThat(first(Pattern.compile("^")).iterateIn("foo").map(Object::toString))
        .containsExactly("");
    assertThat(first(Pattern.compile("$")).iterateIn("foo").map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void regex_matchesFirstOccurrence() {
    Optional<Substring.Match> match = first(Pattern.compile(".ar")).in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("barbaz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocbarbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first(Pattern.compile(".ar")).iterateIn("foobarbarbaz").map(Object::toString))
        .containsExactly("bar", "bar");
    assertThat(first(Pattern.compile(".ar")).iterateIn("foobarbazbar").map(Object::toString))
        .containsExactly("bar", "bar");
  }

  @Test
  public void regexGroup_noMatch() {
    assertThat(first(Pattern.compile("(.*)x"), 1).in("bar")).isEmpty();
    assertThat(first(Pattern.compile("(.*x)"), 1).in("bar")).isEmpty();
    assertThat(first(Pattern.compile(".*(x)"), 1).in("bar")).isEmpty();
    assertThat(first(Pattern.compile("(.*x)"), 1).in("")).isEmpty();
    assertThat(first(Pattern.compile("(.*x)"), 1).iterateIn("")).isEmpty();
  }

  @Test
  public void regexGroup_matchesFirstGroup() {
    Optional<Substring.Match> match = first(Pattern.compile("(f.)b.*"), 1).in("fobar");
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().length()).isEqualTo(2);
    assertThat(match.get().toString()).isEqualTo("fo");
    assertThat(first(Pattern.compile("(f.)b.*"), 1).iterateIn("fobar").map(Object::toString))
        .containsExactly("fo");
    assertThat(first(Pattern.compile("(f.)b.."), 1).iterateIn("fobar").map(Object::toString))
        .containsExactly("fo");
    assertThat(first(Pattern.compile("(f.)b.."), 1).iterateIn("fobarfubaz").map(Object::toString))
        .containsExactly("fo", "fu");
  }

  @Test
  public void regexGroup_matchesSecondGroup() {
    Optional<Substring.Match> match = first(Pattern.compile("f(o.)(ba.)"), 2).in("foobarbaz");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first(Pattern.compile("f(o.)(ba.)"), 1).iterateIn("foobarbaz").map(Object::toString))
        .containsExactly("oo");
    assertThat(
            first(Pattern.compile("f(o.)(ba.);"), 1)
                .iterateIn("foobar; foibaz;")
                .map(Object::toString))
        .containsExactly("oo", "oi");
  }

  @Test
  public void regexGroup_group0() {
    Optional<Substring.Match> match = first(Pattern.compile("f(o.)(ba.).*"), 0).in("foobarbaz");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("c")).isEqualTo("c");
    assertThat(match.get().replaceWith("car")).isEqualTo("car");
    assertThat(match.get().length()).isEqualTo(9);
    assertThat(match.get().toString()).isEqualTo("foobarbaz");
    assertThat(
            first(Pattern.compile("f(o.)(ba.).*"), 0).iterateIn("foobarbaz").map(Object::toString))
        .containsExactly("foobarbaz");
  }

  @Test
  public void regexGroup_negativeGroup() {
    assertThrows(IndexOutOfBoundsException.class, () -> first(Pattern.compile("."), -1));
  }

  @Test
  public void regexGroup_invalidGroupIndex() {
    assertThrows(IndexOutOfBoundsException.class, () -> first(Pattern.compile("f(o.)(ba.)"), 3));
  }

  @Test
  public void lastSnippet_toString() {
    assertThat(last("foo").toString()).isEqualTo("last('foo')");
  }

  @Test
  public void lastSnippet_noMatch() {
    assertThat(last("foo").in("bar")).isEmpty();
    assertThat(last("foo").in("")).isEmpty();
    assertThat(last("foo").iterateIn("bar")).isEmpty();
    assertThat(last("f").iterateIn("")).isEmpty();
  }

  @Test
  public void lastSnippet_matchesFullString() {
    Optional<Substring.Match> match = last("foo").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(last("foo").iterateIn("foo").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void lastSnippet_matchesPrefix() {
    Optional<Substring.Match> match = last("foo").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(last("foo").iterateIn("foobar").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void lastSnippet_matchesPrefixPrecededBySamePattern() {
    Optional<Substring.Match> match = last("foo").in("foobarfoobaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foobar");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foobarcbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobarcarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(last("foo").iterateIn("foobarfoobaz").map(Object::toString)).containsExactly("foo");
  }

  @Test
  public void lastSnippet_matchesSuffix() {
    Optional<Substring.Match> match = last("bar").in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("c")).isEqualTo("fooc");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(last("bar").iterateIn("foobar").map(Object::toString)).containsExactly("bar");
  }

  @Test
  public void lastSnippet_matchesInTheMiddle() {
    Optional<Substring.Match> match = last("bar").in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(last("bar").iterateIn("foobarbaz").map(Object::toString)).containsExactly("bar");
  }

  @Test
  public void lastSnippet_matchesLastOccurrence() {
    Optional<Substring.Match> match = last("bar").in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foobar");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobarbaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foobarcbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobarcarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(last("bar").iterateIn("barfoobarbaz").map(Object::toString)).containsExactly("bar");
  }

  @Test
  public void lastSnippet_emptySnippet() {
    Optional<Substring.Match> match = last("").in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("foo");
    assertThat(match.get().replaceWith("")).isEqualTo("foo");
    assertThat(match.get().replaceWith("b")).isEqualTo("foob");
    assertThat(match.get().replaceWith("bar")).isEqualTo("foobar");
    assertThat(match.get().length()).isEqualTo(0);
    assertThat(match.get().toString()).isEmpty();
    assertThat(last("").iterateIn("foo").map(Object::toString)).containsExactly("");
  }

  @Test
  public void firstChar_toString() {
    assertThat(first('f').toString()).isEqualTo("first('f')");
  }

  @Test
  public void firstChar_noMatch() {
    assertThat(first('f').in("bar")).isEmpty();
    assertThat(first('f').in("")).isEmpty();
    assertThat(first('f').iterateIn("bar")).isEmpty();
    assertThat(first('f').iterateIn("")).isEmpty();
  }

  @Test
  public void firstChar_matchesFullString() {
    Optional<Substring.Match> match = first('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(first('f').iterateIn("f").map(Object::toString)).containsExactly("f");
  }

  @Test
  public void firstChar_matchesPrefix() {
    Optional<Substring.Match> match = first('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith("")).isEqualTo("oobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("coobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(first('f').iterateIn("foobar").map(Object::toString)).containsExactly("f");
  }

  @Test
  public void firstChar_matchesPrefixFollowedBySameChar() {
    Optional<Substring.Match> match = first('f').in("foofar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("oofar");
    assertThat(match.get().remove()).isEqualTo("oofar");
    assertThat(match.get().replaceWith("")).isEqualTo("oofar");
    assertThat(match.get().replaceWith("c")).isEqualTo("coofar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroofar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(first('f').iterateIn("foofar").map(Object::toString)).containsExactly("f", "f");
  }

  @Test
  public void firstChar_matchesSuffix() {
    Optional<Substring.Match> match = first('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("fooba");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().replaceWith("")).isEqualTo("fooba");
    assertThat(match.get().replaceWith("c")).isEqualTo("foobac");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobacar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
    assertThat(first('r').iterateIn("foofar").map(Object::toString)).containsExactly("r");
  }

  @Test
  public void firstChar_matchesFirstOccurrence() {
    Optional<Substring.Match> match = first('b').in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("arbarbaz");
    assertThat(match.get().remove()).isEqualTo("fooarbarbaz");
    assertThat(match.get().replaceWith("")).isEqualTo("fooarbarbaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocarbarbaz");
    assertThat(match.get().replaceWith("coo")).isEqualTo("foocooarbarbaz");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
    assertThat(first('b').iterateIn("foobarbarbaz").map(Object::toString))
        .containsExactly("b", "b", "b");
  }

  @Test
  public void lastChar_noMatch() {
    assertThat(last('f').in("bar")).isEmpty();
    assertThat(last('f').in("")).isEmpty();
    assertThat(last('f').iterateIn("bar")).isEmpty();
    assertThat(last('f').iterateIn("")).isEmpty();
    assertThat(last("f").in("bar")).isEmpty();
    assertThat(last("f").in("")).isEmpty();
    assertThat(last("f").iterateIn("bar")).isEmpty();
    assertThat(last("f").iterateIn("")).isEmpty();
  }

  @Test
  public void lastChar_matchesFullString() {
    Optional<Substring.Match> match = last('f').in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(last('f').iterateIn("f").map(Object::toString)).containsExactly("f");
    assertThat(last("f").iterateIn("f").map(Object::toString)).containsExactly("f");
  }

  @Test
  public void lastChar_matchesPrefix() {
    Optional<Substring.Match> match = last('f').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith("")).isEqualTo("oobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("coobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(last('f').iterateIn("foobar").map(Object::toString)).containsExactly("f");
    assertThat(last("f").iterateIn("foobar").map(Object::toString)).containsExactly("f");
  }

  @Test
  public void lastChar_matchesSuffix() {
    Optional<Substring.Match> match = last('r').in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("fooba");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().replaceWith("")).isEqualTo("fooba");
    assertThat(match.get().replaceWith("c")).isEqualTo("foobac");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobacar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
    assertThat(last('r').iterateIn("farbar").map(Object::toString)).containsExactly("r");
    assertThat(last("r").iterateIn("farbar").map(Object::toString)).containsExactly("r");
  }

  @Test
  public void lastChar_matchesLastOccurrence() {
    Optional<Substring.Match> match = last('b').in("foobarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foobar");
    assertThat(match.get().after()).isEqualTo("az");
    assertThat(match.get().remove()).isEqualTo("foobaraz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobaraz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foobarcaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobarcaraz");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
    assertThat(last('b').iterateIn("farbarbaz").map(Object::toString)).containsExactly("b");
    assertThat(last("b").iterateIn("farbarbaz").map(Object::toString)).containsExactly("b");
  }

  @Test
  public void removeFrom_noMatch() {
    assertThat(first('f').removeFrom("bar")).isEqualTo("bar");
  }

  @Test
  public void removeFrom_match() {
    assertThat(first('f').removeFrom("foo")).isEqualTo("oo");
  }

  @Test
  public void removeAllFrom_noMatch() {
    assertThat(first('f').removeAllFrom("bar")).isEqualTo("bar");
  }

  @Test
  public void removeAllFrom_oneMatch() {
    assertThat(first('f').removeAllFrom("foo")).isEqualTo("oo");
    assertThat(first('f').removeAllFrom("afoo")).isEqualTo("aoo");
    assertThat(first('f').removeAllFrom("oof")).isEqualTo("oo");
  }

  @Test
  public void removeAllFrom_twoMatches() {
    assertThat(first('o').removeAllFrom("foo")).isEqualTo("f");
    assertThat(first('o').removeAllFrom("ofo")).isEqualTo("f");
    assertThat(first('o').removeAllFrom("oof")).isEqualTo("f");
  }

  @Test
  public void removeAllFrom_threeMatches() {
    assertThat(first("x").removeAllFrom("fox x bxr")).isEqualTo("fo  br");
    assertThat(first("x").removeAllFrom("xaxbxxxcxx")).isEqualTo("abc");
  }

  @Test
  public void replaceFrom_noMatch() {
    assertThat(first('f').replaceFrom("bar", "")).isEqualTo("bar");
    assertThat(first('f').replaceFrom("bar", "x")).isEqualTo("bar");
    assertThat(first('f').replaceFrom("bar", "xyz")).isEqualTo("bar");
  }

  @Test
  public void replaceFrom_match() {
    assertThat(first('f').replaceFrom("foo", "")).isEqualTo("oo");
    assertThat(first('f').replaceFrom("foo", "b")).isEqualTo("boo");
    assertThat(first('f').replaceFrom("foo", "bar")).isEqualTo("baroo");
  }

  @Test
  public void replaceAllFrom_noMatch() {
    assertThat(first('f').replaceAllFrom("bar", m -> "x")).isEqualTo("bar");
    assertThat(first('f').replaceAllFrom("bar", m -> "xyz")).isEqualTo("bar");
  }

  @Test
  public void replaceAllFrom_oneMatch() {
    assertThat(first('f').replaceAllFrom("foo", m -> "xx")).isEqualTo("xxoo");
    assertThat(first('f').replaceAllFrom("afoo", m -> "xx")).isEqualTo("axxoo");
    assertThat(first('f').replaceAllFrom("oof", m -> "xx")).isEqualTo("ooxx");
  }

  @Test
  public void replaceAllFrom_twoMatches() {
    assertThat(first('o').replaceAllFrom("foo", m -> "xx")).isEqualTo("fxxxx");
    assertThat(first('o').replaceAllFrom("ofo", m -> "xx")).isEqualTo("xxfxx");
    assertThat(first('o').replaceAllFrom("oof", m -> "xx")).isEqualTo("xxxxf");
  }

  @Test
  public void replaceAllFrom_threeMatches() {
    assertThat(first("x").replaceAllFrom("fox x bxr", m -> "yy")).isEqualTo("foyy yy byyr");
    assertThat(first("x").replaceAllFrom("xaxbxxcx", m -> "yy")).isEqualTo("yyayybyyyycyy");
  }

  @Test
  public void replaceAllFrom_placeholderSubstitution() {
    Substring.Pattern placeholder = Substring.between(before(first('{')), after(first('}')));
    ImmutableMap<String, String> dictionary = ImmutableMap.of("{key}", "foo", "{value}", "bar");
    assertThat(
            placeholder.replaceAllFrom(
                "/{key}:{value}/", match -> dictionary.get(match.toString())))
        .isEqualTo("/foo:bar/");
  }

  @Test
  public void replaceAllFrom_replacementFunctionReturnsNull() {
    Substring.Pattern placeholder = Substring.between(before(first('{')), after(first('}')));
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> placeholder.replaceAllFrom("{unknown}", match -> null));
    assertThat(thrown).hasMessageThat().contains("{unknown}");
  }

  @Test
  public void replaceAllFrom_replacementFunctionReturnsNonEmpty() {
    assertThat(Substring.first("var").replaceAllFrom("var=x", m -> "v")).isEqualTo("v=x");
  }

  @Test
  public void delimit() {
    assertThat(first(',').delimit("foo").map(Match::toString))
        .containsExactly("foo");
    assertThat(first(',').delimit("foo, bar").map(Match::toString))
        .containsExactly("foo", " bar");
    assertThat(first(',').delimit("foo,").map(Match::toString))
        .containsExactly("foo", "");
    assertThat(first(',').delimit("foo,bar, ").map(Match::toString))
        .containsExactly("foo", "bar", " ");
  }

  @Test
  public void delimit_beginning() {
    assertThrows(IllegalStateException.class, () -> BEGINNING.delimit("foo"));
  }

  @Test
  public void delimit_end() {
    assertThrows(IllegalStateException.class, () -> END.delimit("foo"));
  }

  @Test
  public void delimit_empty() {
    assertThrows(IllegalStateException.class, () -> first("").delimit("foo"));
  }

  @Test
  public void split_cannotSplit() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> first('=').split("foo:bar", String::concat));
    assertThat(thrown).hasMessageThat().isEqualTo("Pattern first('=') not found in 'foo:bar'.");
  }

  @Test
  public void split_canSplit() {
    assertThat(first('=').split(" foo=bar", (String k, String v) -> k)).isEqualTo(" foo");
    assertThat(first('=').split("foo=bar ", (String k, String v) -> v)).isEqualTo("bar ");
    assertThat(first('=').split(" foo=bar", (String k, String v) -> k)).isEqualTo(" foo");
  }

  @Test
  public void splitThenTrim_intoTwoParts_cannotSplit() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> first('=').splitThenTrim("foo:bar", String::concat));
    assertThat(thrown).hasMessageThat().contains("foo:bar");
  }

  @Test
  public void splitThenTrim_intoTwoParts_canSplit() {
    assertThat(first('=').splitThenTrim(" foo =bar", (String k, String v) -> k)).isEqualTo("foo");
    assertThat(first('=').splitThenTrim("foo = bar ", (String k, String v) -> v)).isEqualTo("bar");
  }

  @Test
  public void matchAsCharSequence() {
    CharSequence match = first(" >= ").in("foo >= bar").get();
    assertThat(match.length()).isEqualTo(4);
    assertThat(match.charAt(0)).isEqualTo(' ');
    assertThat(match.charAt(1)).isEqualTo('>');
    assertThat(match.charAt(2)).isEqualTo('=');
    assertThat(match.charAt(3)).isEqualTo(' ');
    assertThat(match.toString()).isEqualTo(" >= ");
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(4));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(5, 5));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 5));
  }

  @Test
  public void matchAsCharSequence_subSequence() {
    CharSequence match = first(" >= ").in("foo >= bar").get().subSequence(1, 3);
    assertThat(match.length()).isEqualTo(2);
    assertThat(match.charAt(0)).isEqualTo('>');
    assertThat(match.charAt(1)).isEqualTo('=');
    assertThat(match.toString()).isEqualTo(">=");
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(2));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(3, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 3));
  }

  @Test
  public void matchAsCharSequence_subSequence_emptyAtHead() {
    CharSequence match = first(" >= ").in("foo >= bar").get().subSequence(0, 0);
    assertThat(match.length()).isEqualTo(0);
    assertThat(match.toString()).isEmpty();
    assertThat(match.subSequence(0, 0).toString()).isEmpty();
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(0));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 1));
  }

  @Test
  public void matchAsCharSequence_subSequence_emptyInTheMiddle() {
    CharSequence match = first(">=").in("foo >= bar").get().subSequence(1, 1);
    assertThat(match.length()).isEqualTo(0);
    assertThat(match.toString()).isEmpty();
    assertThat(match.subSequence(0, 0).toString()).isEmpty();
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(0));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 1));
  }

  @Test
  public void matchAsCharSequence_subSequence_emptyAtEnd() {
    CharSequence match = first(">=").in("foo >= bar").get().subSequence(2, 2);
    assertThat(match.length()).isEqualTo(0);
    assertThat(match.toString()).isEmpty();
    assertThat(match.subSequence(0, 0).toString()).isEmpty();
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(0));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 1));
  }

  @Test
  public void matchAsCharSequence_subSequence_full() {
    CharSequence match = first(">=").in("foo >= bar").get().subSequence(0, 2);
    assertThat(match.length()).isEqualTo(2);
    assertThat(match.charAt(0)).isEqualTo('>');
    assertThat(match.charAt(1)).isEqualTo('=');
    assertThat(match.toString()).isEqualTo(">=");
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(2));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(3, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 3));
  }

  @Test
  public void matchAsCharSequence_subSequence_partial() {
    CharSequence match = first(" >= ").in("foo >= bar").get().subSequence(1, 3);
    assertThat(match.length()).isEqualTo(2);
    assertThat(match.charAt(0)).isEqualTo('>');
    assertThat(match.charAt(1)).isEqualTo('=');
    assertThat(match.toString()).isEqualTo(">=");
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(2));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(3, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 3));
  }

  @Test
  public void or_toString() {
    assertThat(first("foo").or(last("bar")).toString()).isEqualTo("first('foo').or(last('bar'))");
  }

  @Test
  public void or_firstMatcherMatches() {
    Optional<Substring.Match> match = first('b').or(first("foo")).in("bar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("ar");
    assertThat(match.get().remove()).isEqualTo("ar");
    assertThat(match.get().replaceWith("")).isEqualTo("ar");
    assertThat(match.get().replaceWith("c")).isEqualTo("car");
    assertThat(match.get().replaceWith("coo")).isEqualTo("cooar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
    assertThat(first('b').or(first("foo")).iterateIn("barfoo").map(Object::toString))
        .containsExactly("b", "foo");
  }

  @Test
  public void or_secondMatcherMatches() {
    Optional<Substring.Match> match = first('b').or(first("foo")).in("foo");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("foo");
    assertThat(prefix("bar").or(first("foo")).iterateIn("foobar").map(Object::toString))
        .containsExactly("foo", "bar");
  }

  @Test
  public void or_neitherMatches() {
    assertThat(first("bar").or(first("foo")).in("baz")).isEmpty();
    assertThat(prefix("bar").or(first("foo")).iterateIn("baz")).isEmpty();
  }

  @Test
  public void uptoIncluding_toString() {
    assertThat(upToIncluding(last("bar")).toString()).isEqualTo("upToIncluding(last('bar'))");
  }

  @Test
  public void upToIncluding_noMatch() {
    assertThat(Substring.upToIncluding(first("://")).in("abc")).isEmpty();
    assertThat(Substring.upToIncluding(first("://")).iterateIn("abc")).isEmpty();
  }

  @Test
  public void upToIncluding_matchAtPrefix() {
    assertThat(Substring.upToIncluding(first("://")).removeFrom("://foo")).isEqualTo("foo");
    assertThat(Substring.upToIncluding(first("://")).iterateIn("://foo").map(Object::toString))
        .containsExactly("://");
  }

  @Test
  public void upToIncluding_matchInTheMiddle() {
    assertThat(Substring.upToIncluding(first("://")).removeFrom("http://foo")).isEqualTo("foo");
    assertThat(Substring.upToIncluding(first("://")).iterateIn("http://foo").map(Object::toString))
        .containsExactly("http://");
  }

  @Test
  public void upToIncluding_delimitedByRegexGroup() {
    assertThat(
            Substring.upToIncluding(first(Pattern.compile("(/.)/")))
                .iterateIn("foo/1/bar/2/")
                .map(Object::toString))
        .containsExactly("foo/1/", "bar/2/");
  }

  @Test
  public void toEnd_toString() {
    assertThat(first("//").toEnd().toString()).isEqualTo("first('//').toEnd()");
  }

  @Test
  public void toEnd_noMatch() {
    assertThat(first("//").toEnd().in("abc")).isEmpty();
    assertThat(first("//").toEnd().iterateIn("abc")).isEmpty();
  }

  @Test
  public void toEnd_matchAtSuffix() {
    assertThat(first("//").toEnd().removeFrom("foo//")).isEqualTo("foo");
    assertThat(first("//").toEnd().iterateIn("foo//").map(Object::toString)).containsExactly("//");
  }

  @Test
  public void toEnd_matchInTheMiddle() {
    assertThat(first("//").toEnd().removeFrom("foo // bar")).isEqualTo("foo ");
    assertThat(first("//").toEnd().iterateIn("foo // bar //").map(Object::toString))
        .containsExactly("// bar //");
  }

  @Test
  public void before_toString() {
    assertThat(Substring.before(first("://")).toString()).isEqualTo("before(first('://'))");
  }

  @Test
  public void before_noMatch() {
    assertThat(Substring.before(first("://")).in("abc")).isEmpty();
    assertThat(Substring.before(first("://")).iterateIn("abc")).isEmpty();
  }

  @Test
  public void before_matchAtPrefix() {
    assertThat(Substring.before(first("//")).removeFrom("//foo")).isEqualTo("//foo");
    assertThat(Substring.before(first("//")).iterateIn("//foo").map(Object::toString))
        .containsExactly("");
    assertThat(Substring.before(first("//")).iterateIn("//foo//").map(Object::toString))
        .containsExactly("", "foo");
  }

  @Test
  public void before_matchInTheMiddle() {
    assertThat(Substring.before(first("//")).removeFrom("http://foo")).isEqualTo("//foo");
    assertThat(Substring.before(first("//")).iterateIn("http://foo").map(Object::toString))
        .containsExactly("http:");
  }

  @Test
  public void delimite_noMatch() {
    assertThat(first("://").delimit("abc").map(Match::toString))
        .containsExactly("abc");
  }

  @Test
  public void delimit_match() {
    assertThat(first("//").delimit("//foo").map(Match::toString))
        .containsExactly("", "foo");
    assertThat(first("/").delimit("foo/bar").map(Match::toString))
        .containsExactly("foo", "bar");
    assertThat(first("/").delimit("foo/bar/").map(Match::toString))
        .containsExactly("foo", "bar", "");
  }

  @Test
  public void delimit_byBetweenPattern() {
    Substring.Pattern comment = Substring.between(before(first("/*")), after(first("*/")));
    assertThat(comment.delimit("a").map(Match::toString))
        .containsExactly("a")
        .inOrder();
    assertThat(comment.delimit("a/*comment*/").map(Match::toString))
        .containsExactly("a", "")
        .inOrder();
    assertThat(comment.delimit("a/*comment*/b").map(Match::toString))
        .containsExactly("a", "b")
        .inOrder();
    assertThat(comment.delimit("a/*c1*/b/*c2*/").map(Match::toString))
        .containsExactly("a", "b", "")
        .inOrder();
    assertThat(comment.delimit("a/*c1*/b/*c2*/c").map(Match::toString))
        .containsExactly("a", "b", "c")
        .inOrder();
  }

  @Test
  public void after_toString() {
    assertThat(Substring.after(first("//")).toString()).isEqualTo("after(first('//'))");
  }

  @Test
  public void after_noMatch() {
    assertThat(Substring.after(first("//")).in("abc")).isEmpty();
    assertThat(Substring.after(first("//")).iterateIn("abc")).isEmpty();
  }

  @Test
  public void after_matchAtSuffix() {
    assertThat(Substring.after(last('.')).removeFrom("foo.")).isEqualTo("foo.");
    assertThat(Substring.after(last('.')).iterateIn("foo.").map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void after_matchInTheMiddle() {
    assertThat(Substring.after(last('.')).removeFrom("foo. bar")).isEqualTo("foo.");
    assertThat(Substring.after(last('.')).iterateIn("foo. bar").map(Object::toString))
        .containsExactly(" bar");
  }

  @Test
  public void between_toString() {
    assertThat(Substring.between(last("<"), last(">")).toString())
        .isEqualTo("between(last('<'), last('>'))");
  }

  @Test
  public void between_matchedInTheMiddle() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("foo<bar>baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo("foo<");
    assertThat(match.after()).isEqualTo(">baz");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(last('<'), last('>')).iterateIn("foo<bar>baz").map(Object::toString))
        .containsExactly("bar");
    assertThat(
            Substring.between(first('/'), first('/')).iterateIn("/foo/bar/").map(Object::toString))
        .containsExactly("foo", "bar");
  }

  @Test
  public void between_emptyMatch() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("foo<>baz").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("foo<");
    assertThat(match.after()).isEqualTo(">baz");
    assertThat(match.length()).isEqualTo(0);
    assertThat(Substring.between(last('<'), last('>')).iterateIn("foo<>baz").map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void between_consecutiveFirstChar() {
    Substring.Pattern delimiter = first('-');
    Substring.Match match = Substring.between(delimiter, delimiter).in("foo-bar-baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo("foo-");
    assertThat(match.after()).isEqualTo("-baz");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(delimiter, delimiter)
                .iterateIn("-foo-bar-baz-")
                .map(Object::toString))
        .containsExactly("foo", "bar", "baz");
  }

  @Test
  public void between_matchedFully() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("<foo>").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEqualTo("<");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(Substring.between(last('<'), last('>')).iterateIn("<foo>").map(Object::toString))
        .containsExactly("foo");
  }

  @Test
  public void between_outerMatchesEmpty() {
    Substring.Match match = Substring.between(first(""), last('.')).in("foo.").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEmpty();
    assertThat(match.after()).isEqualTo(".");
    assertThat(match.length()).isEqualTo(3);
    assertThat(Substring.between(first(""), last('.')).iterateIn("foo.").map(Object::toString))
        .contains("foo");
  }

  @Test
  public void between_innerMatchesEmpty() {
    Substring.Match match = Substring.between(first(":"), last("")).in("hello:world").get();
    assertThat(match.toString()).isEqualTo("world");
    assertThat(match.before()).isEqualTo("hello:");
    assertThat(match.after()).isEmpty();
    assertThat(match.length()).isEqualTo(5);
    assertThat(Substring.between(first(":"), last("")).iterateIn("foo:bar").map(Object::toString))
        .containsExactly("bar");
  }

  @Test
  public void between_matchedLastOccurrence() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("<foo><bar> <baz>").get();
    assertThat(match.toString()).isEqualTo("baz");
    assertThat(match.before()).isEqualTo("<foo><bar> <");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(last('<'), last('>'))
                .iterateIn("<foo><bar> <baz>")
                .map(Object::toString))
        .containsExactly("baz");
  }

  @Test
  public void between_matchedIncludingDelimiters() {
    Substring.Match match =
        Substring.between(before(last('<')), after(last('>'))).in("begin<foo>end").get();
    assertThat(match.toString()).isEqualTo("<foo>");
    assertThat(match.before()).isEqualTo("begin");
    assertThat(match.after()).isEqualTo("end");
    assertThat(match.length()).isEqualTo(5);
    assertThat(
            Substring.between(before(last('<')), after(last('>')))
                .iterateIn("begin<foo>end")
                .map(Object::toString))
        .containsExactly("<foo>");
  }

  @Test
  public void between_nothingBetweenSameChar() {
    assertThat(Substring.between(first('.'), first('.')).in(".")).isEmpty();
    assertThat(Substring.between(first('.'), first('.')).iterateIn(".")).isEmpty();
  }

  @Test
  public void between_matchesOpenButNotClose() {
    assertThat(Substring.between(first('<'), first('>')).in("<foo")).isEmpty();
    assertThat(Substring.between(first('<'), first('>')).iterateIn("<foo")).isEmpty();
  }

  @Test
  public void between_matchesCloseButNotOpen() {
    assertThat(Substring.between(first('<'), first('>')).in("foo>")).isEmpty();
    assertThat(Substring.between(first('<'), first('>')).iterateIn("foo>")).isEmpty();
  }

  @Test
  public void between_closeIsBeforeOpen() {
    assertThat(Substring.between(first('<'), first('>')).in(">foo<")).isEmpty();
    assertThat(Substring.between(first('<'), first('>')).iterateIn(">foo<")).isEmpty();
  }

  @Test
  public void between_closeBeforeOpenDoesNotCount() {
    Substring.Match match = Substring.between(first('<'), first('>')).in("><foo>").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEqualTo("><");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(Substring.between(first('<'), first('>')).iterateIn("><foo>").map(Object::toString))
        .containsExactly("foo");
  }

  @Test
  public void between_matchesNone() {
    assertThat(Substring.between(first('<'), first('>')).in("foo")).isEmpty();
    assertThat(Substring.between(first('<'), first('>')).iterateIn("foo")).isEmpty();
  }

  @Test
  public void between_closeUsesBefore() {
    Substring.Pattern open = first("-");
    Substring.Pattern close = Substring.before(first("-"));
    Substring.Match match = Substring.between(open, close).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), before(first("-")))
                .iterateIn("-foo-")
                .map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void between_closeUsesUpToIncluding() {
    Substring.Match match =
        Substring.between(first("-"), Substring.upToIncluding(first("-"))).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), upToIncluding(first("-")))
                .iterateIn("-foo-")
                .map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void between_closeUsesRegex() {
    Substring.Match match =
        Substring.between(first("-"), first(Pattern.compile(".*-"))).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), first(Pattern.compile(".*-")))
                .iterateIn("-foo-")
                .map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void between_closeOverlapsWithOpen() {
    assertThat(Substring.between(first("abc"), last("cde")).in("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), last("cde")).iterateIn("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), last('c')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), last('c')).iterateIn("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix("cde")).in("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix("cde")).iterateIn("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix('c')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix('c')).iterateIn("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix("a")).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix("a")).iterateIn("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix('a')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix('a')).iterateIn("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first("a")).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first("a")).iterateIn("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first('a')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first('a')).iterateIn("abc")).isEmpty();
  }

  @Test
  public void between_betweenInsideBetween() {
    Substring.Match match =
        Substring.between(first("-"), Substring.between(first(""), first('-'))).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), Substring.between(first(""), first('-')))
                .iterateIn("-foo-")
                .map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void between_emptyOpen() {
    Substring.Match match = Substring.between(first(""), first(", ")).in("foo, bar").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEmpty();
    assertThat(match.after()).isEqualTo(", bar");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(first(""), first(", ")).iterateIn("foo, bar").map(Object::toString))
        .contains("foo");
  }

  @Test
  public void between_emptyClose() {
    Substring.Match match = Substring.between(first(":"), first("")).in("foo:bar").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("foo:");
    assertThat(match.after()).isEqualTo("bar");
    assertThat(match.length()).isEqualTo(0);
    assertThat(Substring.between(first(":"), first("")).iterateIn("foo:bar").map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void between_emptyOpenAndClose() {
    Substring.Match match = Substring.between(first(""), first("")).in("foo").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEmpty();
    assertThat(match.after()).isEqualTo("foo");
    assertThat(match.length()).isEqualTo(0);
    assertThat(Substring.between(first(""), first("")).iterateIn("foo").map(Object::toString))
        .containsExactly("");
  }

  @Test
  public void between_openAndCloseAreEqual() {
    Substring.Match match = Substring.between(first("-"), first("-")).in("foo-bar-baz-duh").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo("foo-");
    assertThat(match.after()).isEqualTo("-baz-duh");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(first("-"), first("-"))
                .iterateIn("foo-bar-baz-duh")
                .map(Object::toString))
        .containsExactly("bar", "baz");
  }

  @Test
  public void between_closeBeforeOpenIgnored() {
    Substring.Match match = Substring.between(first("<"), first(">")).in(">foo<bar>").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo(">foo<");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(first("<"), first(">"))
                .iterateIn(">foo<bar>h<baz>")
                .map(Object::toString))
        .containsExactly("bar", "baz");
  }

  @Test
  public void patternFrom_noMatch() {
    assertThat(prefix("foo").from("")).isEmpty();
  }

  @Test
  public void patternFrom_match() {
    assertThat(Substring.first("bar").from("foo bar")).hasValue("bar");
  }

  @Test
  public void matcher_index() {
    assertThat(Substring.first("foo").in("foobar").get().index()).isEqualTo(0);
    assertThat(Substring.first("bar").in("foobar").get().index()).isEqualTo(3);
    assertThat(END.in("foobar").get().index()).isEqualTo(6);
  }

  @Test
  public void matcher_fullString() {
    assertThat(Substring.first("bar").in("foobar").get().fullString()).isEqualTo("foobar");
  }

  @Test
  public void iterateIn_example() {
    String text = "{x:1}, {y:2}, {z:3}";
    ImmutableListMultimap<String, String> dictionary =
        Substring.between(first('{'), first('}'))
            .iterateIn(text)
            .map(Object::toString)
            .map(Substring.first(':')::in)
            .map(Optional::get)
            .collect(toImmutableListMultimap(Substring.Match::before, Substring.Match::after));
    assertThat(dictionary).containsExactly("x", "1", "y", "2", "z", "3").inOrder();
  }

  @Test
  public void iterateIn_getLinesPreservingNewLineChar() {
    String text = "line1\nline2\nline3";
    assertThat(Substring.upToIncluding(first('\n').or(END)).iterateIn(text).map(Object::toString))
        .containsExactly("line1\n", "line2\n", "line3")
        .inOrder();
  }

  @Test
  public void iterateIn_characteristics() {
    Spliterator<?> spliterator = BEGINNING.iterateIn("test").spliterator();
    assertThat(spliterator.characteristics() & Spliterator.NONNULL).isEqualTo(Spliterator.NONNULL);
  }

  @Test
  public void spanningInOrder_toString() {
    assertThat(spanningInOrder("o", "bar").toString()).isEqualTo("spanningInOrder('o', 'bar')");
  }

  @Test
  public void spanningInOrder_twoStops_matches() {
    Substring.Match match = spanningInOrder("o", "bar").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(1);
    assertThat(match.length()).isEqualTo(6);
  }

  @Test
  public void spanningInOrder_twoStops_firstPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar").in("far bar car")).isEmpty();
  }

  @Test
  public void spanningInOrder_twoStops_secondPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar").in("foo car")).isEmpty();
  }

  @Test
  public void spanningInOrder_twoStops_firstStopIsEmpty() {
    Substring.Match match = spanningInOrder("", "foo").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(0);
    assertThat(match.length()).isEqualTo(3);
  }

  @Test
  public void spanningInOrder_twoStops_secondStopIsEmpty() {
    Substring.Match match = spanningInOrder("foo", "").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(0);
    assertThat(match.length()).isEqualTo(3);
  }

  @Test
  public void spanningInOrder_twoStops_bothStopsAreEmpty() {
    Substring.Match match = spanningInOrder("", "").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(0);
    assertThat(match.length()).isEqualTo(0);
  }

  @Test
  public void spanningInOrder_threeStops_matches() {
    Substring.Match match = spanningInOrder("o", "bar", "bar").in("foo barcarbar").get();
    assertThat(match.toString()).isEqualTo("oo barcarbar");
    assertThat(match.index()).isEqualTo(1);
    assertThat(match.length()).isEqualTo(12);
  }

  @Test
  public void spanningInOrder_threeStops_firstPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar", "car").in("far bar car")).isEmpty();
  }

  @Test
  public void spanningInOrder_threeStops_secondPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar", "car").in("foo boo car")).isEmpty();
  }

  @Test
  public void spanningInOrder_threeStops_thirdPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar", "car").in("foo bar cat")).isEmpty();
  }

  @Test
  public void testNulls() throws Exception {
    new NullPointerTester().testAllPublicInstanceMethods(prefix("foo").in("foobar").get());
    newClassSanityTester().testNulls(Substring.class);
    newClassSanityTester().forAllPublicStaticMethods(Substring.class).testNulls();
  }

  private static ClassSanityTester newClassSanityTester() {
    return new ClassSanityTester()
        .setDefault(int.class, 0)
        .setDefault(String.class, "sub")
        .setDefault(Pattern.class, Pattern.compile("testpattern"))
        .setDefault(Substring.Pattern.class, prefix("foo"));
  }
}
