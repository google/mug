package com.google.mu.util;

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.BEGINNING;
import static com.google.mu.util.Substring.END;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.consecutive;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.last;
import static com.google.mu.util.Substring.leading;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.spanningInOrder;
import static com.google.mu.util.Substring.suffix;
import static com.google.mu.util.Substring.trailing;
import static com.google.mu.util.Substring.upToIncluding;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static java.util.Collections.nCopies;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import com.google.common.truth.MultimapSubject;
import com.google.mu.util.Substring.Match;
import com.google.mu.util.stream.BiCollector;
import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.Joiner;
import com.google.mu.util.stream.MoreCollectors;

@RunWith(JUnit4.class)
public class SubstringTest {
  private static final CharPredicate ALPHA = CharPredicate.range('a', 'z').orRange('A', 'Z');
  private static final CharPredicate DIGIT = CharPredicate.range('0', '9');

  @Test public void none() {
    assertThat(Substring.NONE.in("foo")).isEmpty();
    assertThat(Substring.NONE.removeFrom("foo")).isEqualTo("foo");
    assertThat(Substring.NONE.replaceFrom("foo", "xyz")).isEqualTo("foo");
  }

  @Test public void none_toString() {
    assertThat(Substring.NONE.toString()).isEqualTo("NONE");
  }

  @Test public void beginning() {
    assertThat(BEGINNING.from("foo")).hasValue("");
    assertThat(BEGINNING.removeFrom("foo")).isEqualTo("foo");
    assertThat(BEGINNING.replaceFrom("foo", "begin ")).isEqualTo("begin foo");
    assertThat(BEGINNING.in("foo").get().before()).isEmpty();
    assertThat(BEGINNING.in("foo").get().after()).isEqualTo("foo");
    assertThat(BEGINNING.repeatedly().from("foo")).containsExactly("", "", "", "");
    assertThat(BEGINNING.repeatedly().from("")).containsExactly("");
  }

  @Test public void beginning_toString() {
    assertThat(BEGINNING.toString()).isEqualTo("BEGINNING");
  }

  @Test public void end() {
    assertThat(END.from("foo")).hasValue("");
    assertThat(END.removeFrom("foo")).isEqualTo("foo");
    assertThat(END.replaceFrom("foo", " end")).isEqualTo("foo end");
    assertThat(END.in("foo").get().before()).isEqualTo("foo");
    assertThat(END.in("foo").get().after()).isEmpty();
    assertThat(END.repeatedly().from("foo")).containsExactly("");
    assertThat(END.repeatedly().from("")).containsExactly("");
  }

  @Test public void end_toString() {
    assertThat(END.toString()).isEqualTo("END");
  }

  @Test public void prefix_toString() {
    assertThat(prefix("foo").toString()).isEqualTo("foo");
  }

  @Test public void prefix_length() {
    assertThat(prefix("foo").length()).isEqualTo(3);
  }

  @Test public void prefix_charAt() {
    assertThat(prefix("foo").charAt(1)).isEqualTo('o');
  }

  @Test public void prefix_subSequence() {
    assertThat(prefix("foo").subSequence(1, 3)).isEqualTo("oo");
  }

  @Test public void prefix_equals() {
    new EqualsTester()
        .addEqualityGroup(prefix("foo"), prefix("foo"))
        .addEqualityGroup(prefix("bar"))
        .addEqualityGroup(suffix("foo"))
        .testEquals();
  }

  @Test public void prefix_noMatch() {
    assertThat(prefix("foo").in("notfoo")).isEmpty();
    assertThat(prefix("foo").in("")).isEmpty();
    assertThat(prefix("foo").repeatedly().match("notfoo")).isEmpty();
    assertThat(prefix("foo").repeatedly().match("")).isEmpty();
    assertThat(prefix("foo").repeatedly().match("notfoo", 1)).isEmpty();
  }

  @Test public void prefix_matchesFullString() {
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
    assertThat(prefix("foo").repeatedly().from("foo")).containsExactly("foo");
  }

  @Test public void prefix_matchesPrefix() {
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
    assertThat(prefix("foo").repeatedly().from("foobar")).containsExactly("foo");
  }

  @Test public void prefix_matchesPrefixFollowedByIdentialSubstring() {
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
    assertThat(prefix("foo").repeatedly().from("foofoobar"))
        .containsExactly("foo", "foo");
    assertThat(prefix("foo").repeatedly().from("foofoo"))
        .containsExactly("foo", "foo");
  }

  @Test public void prefix_emptyPrefix() {
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
    assertThat(prefix("").repeatedly().from("foo")).containsExactly("", "", "", "");
  }

  @Test public void charPrefix_toString() {
    assertThat(prefix('a').toString()).isEqualTo("a");
  }

  @Test public void charPrefix_noMatch() {
    assertThat(prefix('f').in("notfoo")).isEmpty();
    assertThat(prefix('f').in("")).isEmpty();
    assertThat(prefix('f').repeatedly().match("")).isEmpty();
  }

  @Test public void charPrefix_matchesFullString() {
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
    assertThat(prefix('f').repeatedly().from("f")).containsExactly("f");
  }

  @Test public void charPrefix_matchesPrefix() {
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
    assertThat(prefix('f').repeatedly().from("fbar")).containsExactly("f");
  }

  @Test public void charPrefix_matchesPrefixFollowedByIdenticalChar() {
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
    assertThat(prefix('f').repeatedly().from("ffar")).containsExactly("f", "f");
  }

  @Test public void prefix_addToIfAbsent_absent() {
    assertThat(prefix("google3/").addToIfAbsent("java/com")).isEqualTo("google3/java/com");
  }

  @Test public void prefix_addToIfAbsent_present() {
    assertThat(prefix("google3/").addToIfAbsent("google3/java/com")).isEqualTo("google3/java/com");
  }

  @Test public void prefix_addToIfAbsent_builderIsEmpty() {
    StringBuilder builder = new StringBuilder();
    assertThat(prefix("foo").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void prefix_addToIfAbsent_builderHasFewerChars() {
    StringBuilder builder = new StringBuilder("oo");
    assertThat(prefix("foo").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("foooo");
  }

  @Test public void prefix_addToIfAbsent_builderHasSameNumberOfChars_absent() {
    StringBuilder builder = new StringBuilder("ofo");
    assertThat(prefix("obo").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("oboofo");
  }

  @Test public void prefix_addToIfAbsent_builderHasSameNumberOfChars_present() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(prefix("foo").addToIfAbsent(builder)).isFalse();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void prefix_addToIfAbsent_builderHasMoreChars_absent() {
    StringBuilder builder = new StringBuilder("booo");
    assertThat(prefix("bob").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("bobbooo");
  }

  @Test public void prefix_addToIfAbsent_builderHasMoreChars_present() {
    StringBuilder builder = new StringBuilder("booo");
    assertThat(prefix("boo").addToIfAbsent(builder)).isFalse();
    assertThat(builder.toString()).isEqualTo("booo");
  }

  @Test public void prefix_hideFrom_prefixNotFound() {
    String string = new String("foo");
    assertThat(prefix("bar").hideFrom(string)).isSameInstanceAs(string);
  }

  @Test public void prefix_hideFrom_emptyPrefix() {
    String string = new String("foo");
    assertThat(prefix("").hideFrom(string)).isSameInstanceAs(string);
  }

  @Test public void prefix_hideFrom_nonEmptyPrefixFound() {
    assertThat(prefix("foo").hideFrom("foobar").toString()).isEqualTo("bar");
  }

  @Test public void prefix_removeFrom_emptyIsRemovedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(prefix("").removeFrom(builder)).isTrue();
    assertThat(builder.toString()).isEmpty();
  }

  @Test public void prefix_removeFrom_emptyIsRemovedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(prefix("").removeFrom(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void prefix_removeFrom_nonEmptyIsNotRemovedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(prefix("foo").removeFrom(builder)).isFalse();
    assertThat(builder.toString()).isEmpty();
  }

  @Test public void prefix_removeFrom_nonEmptyIsNotRemovedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(prefix("fob").removeFrom(builder)).isFalse();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void prefix_removeFrom_nonEmptyIsRemovedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("food");
    assertThat(prefix("foo").removeFrom(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("d");
  }

  @Test public void prefix_replaceFrom_emptyIsReplacedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(prefix("").replaceFrom(builder, "head")).isTrue();
    assertThat(builder.toString()).isEqualTo("head");
  }

  @Test public void prefix_replaceFrom_emptyIsReplacedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(prefix("").replaceFrom(builder, "/")).isTrue();
    assertThat(builder.toString()).isEqualTo("/foo");
  }

  @Test public void prefix_replaceFrom_nonEmptyIsNotReplacedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(prefix("foo").replaceFrom(builder, "/")).isFalse();
    assertThat(builder.toString()).isEmpty();
  }

  @Test public void prefix_replaceFrom_nonEmptyIsNotReplacedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(prefix("fob").replaceFrom(builder, "bar")).isFalse();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void prefix_replaceFrom_nonEmptyIsReplacedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("food");
    assertThat(prefix("foo").replaceFrom(builder, "$")).isTrue();
    assertThat(builder.toString()).isEqualTo("$d");
  }

  @Test public void prefix_emptyIsInEmptySource() {
    assertThat(prefix("").isIn("")).isTrue();
    assertThat(prefix("").isIn(new StringBuilder())).isTrue();
  }

  @Test public void prefix_emptyIsInNonEmptySource() {
    assertThat(prefix("").isIn("foo")).isTrue();
    assertThat(prefix("").isIn(new StringBuilder().append("foo"))).isTrue();
  }

  @Test public void prefix_nonEmptyNotInEmptySource() {
    assertThat(prefix("foo").isIn("")).isFalse();
    assertThat(prefix("foo").isIn(new StringBuilder())).isFalse();
  }

  @Test public void prefix_sourceHasFewerCharacters() {
    assertThat(prefix("foo").isIn("fo")).isFalse();
    assertThat(prefix("foo").isIn(new StringBuilder().append("fo"))).isFalse();
  }

  @Test public void prefix_sourceHasSameNumberOfCharacters_present() {
    assertThat(prefix("foo").isIn("foo")).isTrue();
    assertThat(prefix("foo").isIn(new StringBuilder().append("foo"))).isTrue();
  }

  @Test public void prefix_sourceHasSameNumberOfCharacters_absent() {
    assertThat(prefix("foo").isIn("fob")).isFalse();
    assertThat(prefix("foo").isIn(new StringBuilder().append("fob"))).isFalse();
  }

  @Test public void prefix_sourceHasMoreCharacters_present() {
    assertThat(prefix("foo").isIn("food")).isTrue();
    assertThat(prefix("foo").isIn(new StringBuilder().append("food"))).isTrue();
  }

  @Test public void prefix_sourceHasMoreCharacters_absent() {
    assertThat(prefix("foo").isIn("fit")).isFalse();
    assertThat(prefix("foo").isIn(new StringBuilder().append("fit"))).isFalse();
  }

  @Test public void suffix_toString() {
    assertThat(suffix("foo").toString()).isEqualTo("foo");
  }

  @Test public void suffix_length() {
    assertThat(suffix("foo").length()).isEqualTo(3);
  }

  @Test public void suffix_charAt() {
    assertThat(suffix("foo").charAt(1)).isEqualTo('o');
  }

  @Test public void suffix_subSequence() {
    assertThat(suffix("foo").subSequence(1, 3)).isEqualTo("oo");
  }

  @Test public void suffix_equals() {
    new EqualsTester()
        .addEqualityGroup(suffix("foo"), suffix("foo"))
        .addEqualityGroup(suffix("bar"))
        .addEqualityGroup(prefix("foo"))
        .testEquals();
  }

  @Test public void suffix_noMatch() {
    assertThat(suffix("foo").in("foonot")).isEmpty();
    assertThat(suffix("foo").in("")).isEmpty();
    assertThat(suffix("foo").repeatedly().match("")).isEmpty();
  }

  @Test public void suffix_matchesFullString() {
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
    assertThat(suffix("foo").repeatedly().from("foo")).containsExactly("foo");
  }

  @Test public void suffix_matchesSuffix() {
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
    assertThat(suffix("bar").repeatedly().from("foobar")).containsExactly("bar");
  }

  @Test public void suffix_matchesSuffixPrecededByIdenticalString() {
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
    assertThat(suffix("bar").repeatedly().from("foobarbar")).containsExactly("bar");
  }

  @Test public void suffix_emptySuffix() {
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
    assertThat(suffix("").repeatedly().from("foo")).containsExactly("");
  }

  @Test public void suffix_addToIfAbsent_absent() {
    assertThat(suffix(".").addToIfAbsent("a")).isEqualTo("a.");
    assertThat(suffix('.').addToIfAbsent("foo")).isEqualTo("foo.");
  }

  @Test public void suffix_addToIfAbsent_present() {
    assertThat(suffix(".").addToIfAbsent("a.")).isEqualTo("a.");
    assertThat(suffix('.').addToIfAbsent("foo.")).isEqualTo("foo.");
  }

  @Test public void suffix_addToIfAbsent_emptyIsNotAddedToEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(suffix("").addToIfAbsent(builder)).isFalse();
    assertThat(builder.toString()).isEmpty();
  }

  @Test public void suffix_addToIfAbsent_builderEmpty() {
    StringBuilder builder = new StringBuilder();
    assertThat(suffix(".").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo(".");
  }

  @Test public void suffix_addToIfAbsent_builderHasFewerCharacters() {
    StringBuilder builder = new StringBuilder("oo");
    assertThat(suffix("foo").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("oofoo");
  }

  @Test public void suffix_addToIfAbsent_builderHasSameNumberOfCharacters_absent() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(suffix("boo").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("fooboo");
  }

  @Test public void suffix_addToIfAbsent_builderHasSameNumberOfCharacters_present() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(suffix("foo").addToIfAbsent(builder)).isFalse();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void suffix_addToIfAbsent_builderHasMoreCharacters_absent() {
    StringBuilder builder = new StringBuilder("booo");
    assertThat(suffix("foo").addToIfAbsent(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("booofoo");
  }

  @Test public void suffix_addToIfAbsent_builderHasMoreCharacters_present() {
    StringBuilder builder = new StringBuilder("booo");
    assertThat(suffix("oo").addToIfAbsent(builder)).isFalse();
    assertThat(builder.toString()).isEqualTo("booo");
  }

  @Test public void suffix_hideFrom_prefixNotFound() {
    String string = new String("foo");
    assertThat(suffix("bar").hideFrom(string)).isSameInstanceAs(string);
  }

  @Test public void suffix_hideFrom_emptyPrefix() {
    String string = new String("foo");
    assertThat(suffix("").hideFrom(string)).isSameInstanceAs(string);
  }

  @Test public void suffix_hideFrom_nonEmptyPrefixFound() {
    assertThat(suffix("bar").hideFrom("foobar").toString()).isEqualTo("foo");
  }

  @Test public void suffix_removeFrom_emptyIsRemovedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(suffix("").removeFrom(builder)).isTrue();
    assertThat(builder.toString()).isEmpty();
  }

  @Test public void suffix_removeFrom_emptyIsRemovedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(suffix("").removeFrom(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void suffix_removeFrom_nonEmptyIsNotRemovedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(suffix("foo").removeFrom(builder)).isFalse();
    assertThat(builder.toString()).isEmpty();
  }

  @Test public void suffix_removeFrom_nonEmptyIsNotRemovedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(suffix("zoo").removeFrom(builder)).isFalse();
    assertThat(builder.toString()).isEqualTo("foo");
  }

  @Test public void suffix_removeFrom_nonEmptyIsRemovedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("boofoo");
    assertThat(suffix("foo").removeFrom(builder)).isTrue();
    assertThat(builder.toString()).isEqualTo("boo");
  }

  @Test public void suffix_replaceFrom_emptyIsReplacedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(suffix("").replaceFrom(builder, ".")).isTrue();
    assertThat(builder.toString()).isEqualTo(".");
  }

  @Test public void suffix_replaceFrom_emptyIsReplacedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("foo");
    assertThat(suffix("").replaceFrom(builder, ".")).isTrue();
    assertThat(builder.toString()).isEqualTo("foo.");
  }

  @Test public void suffix_replaceFrom_nonEmptyIsNotReplacedFromEmptyBuilder() {
    StringBuilder builder = new StringBuilder();
    assertThat(suffix("foo").replaceFrom(builder, ".")).isFalse();
    assertThat(builder.toString()).isEmpty();
  }

  @Test public void suffix_replaceFrom_nonEmptyIsReplacedFromNonEmptyBuilder() {
    StringBuilder builder = new StringBuilder("boofoo");
    assertThat(suffix("foo").replaceFrom(builder, "lala")).isTrue();
    assertThat(builder.toString()).isEqualTo("boolala");
  }

  @Test public void suffix_emptyIsInEmptySource() {
    assertThat(suffix("").isIn("")).isTrue();
    assertThat(suffix("").isIn(new StringBuilder())).isTrue();
  }

  @Test public void suffix_emptyIsInNonEmptySource() {
    assertThat(suffix("").isIn("foo")).isTrue();
    assertThat(suffix("").isIn(new StringBuilder().append("foo"))).isTrue();
  }

  @Test public void suffix_nonEmptyNotInEmptySource() {
    assertThat(suffix("foo").isIn("")).isFalse();
    assertThat(suffix("foo").isIn(new StringBuilder())).isFalse();
  }

  @Test public void suffix_sourceHasFewerCharacters() {
    assertThat(suffix("foo").isIn("fo")).isFalse();
    assertThat(suffix("foo").isIn(new StringBuilder().append("fo"))).isFalse();
  }

  @Test public void suffix_sourceHasSameNumberOfCharacters_present() {
    assertThat(suffix("foo").isIn("foo")).isTrue();
    assertThat(suffix("foo").isIn(new StringBuilder().append("foo"))).isTrue();
  }

  @Test public void suffix_sourceHasSameNumberOfCharacters_absent() {
    assertThat(suffix("foo").isIn("boo")).isFalse();
    assertThat(suffix("foo").isIn(new StringBuilder().append("boo"))).isFalse();
  }

  @Test public void suffix_sourceHasMoreCharacters_present() {
    assertThat(suffix("foo").isIn("ffoo")).isTrue();
    assertThat(suffix("foo").isIn(new StringBuilder().append("ffoo"))).isTrue();
  }

  @Test public void suffix_sourceHasMoreCharacters_absent() {
    assertThat(suffix("foo").isIn("zoo")).isFalse();
    assertThat(suffix("foo").isIn(new StringBuilder().append("zoo"))).isFalse();
  }

  @Test public void charSuffix_toString() {
    assertThat(suffix('f').toString()).isEqualTo("f");
  }

  @Test public void charSuffix_noMatch() {
    assertThat(suffix('f').in("foo")).isEmpty();
    assertThat(suffix('f').in("")).isEmpty();
    assertThat(suffix('f').repeatedly().match("foo")).isEmpty();
    assertThat(suffix('f').repeatedly().match("")).isEmpty();
  }

  @Test public void charSuffix_matchesFullString() {
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
    assertThat(suffix('f').repeatedly().from("f")).containsExactly("f");
  }

  @Test public void charSuffix_matchesSuffix() {
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
    assertThat(suffix('r').repeatedly().from("bar")).containsExactly("r");
  }

  @Test public void charSuffix_matchesSuffixPrecededByIdenticalString() {
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
    assertThat(suffix('r').repeatedly().from("barr")).containsExactly("r");
  }

  @Test public void firstSnippet_toString() {
    assertThat(first("foo").toString()).isEqualTo("first('foo')");
  }

  @Test public void firstSnippet_noMatch() {
    assertThat(first("foo").in("bar")).isEmpty();
    assertThat(first("foo").in("")).isEmpty();
    assertThat(first("foo").repeatedly().match("")).isEmpty();
  }

  @Test public void firstSnippet_matchesFullString() {
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
    assertThat(first("foo").repeatedly().from("foo")).containsExactly("foo");
  }

  @Test public void firstSnippet_matchesPrefix() {
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
    assertThat(first("foo").repeatedly().from("foobar")).containsExactly("foo");
  }

  @Test public void firstSnippet_matchesPrefixFollowedByIdenticalString() {
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
    assertThat(first("foo").repeatedly().from("foofoobar"))
        .containsExactly("foo", "foo");
    assertThat(first("foo").repeatedly().from("foobarfoo"))
        .containsExactly("foo", "foo");
  }

  @Test public void firstSnippet_matchesSuffix() {
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
    assertThat(first("bar").repeatedly().from("foobar")).containsExactly("bar");
  }

  @Test public void firstSnippet_matchesInTheMiddle() {
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
    assertThat(first("bar").repeatedly().from("foobarbaz")).containsExactly("bar");
    assertThat(first("bar").repeatedly().from("foobarbarbazbar"))
        .containsExactly("bar", "bar", "bar");
  }

  @Test public void firstSnippet_emptySnippet() {
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
    assertThat(first("").repeatedly().from("foo")).containsExactly("", "", "", "");
  }

  @Test public void firstSnippet_matchesFirstOccurrence() {
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
    assertThat(first("bar").repeatedly().from("foobarbarbaz"))
        .containsExactly("bar", "bar");
    assertThat(first("bar").repeatedly().from("foobarcarbarbaz"))
        .containsExactly("bar", "bar");
  }

  @Test public void regex_toString() {
    assertThat(first(Pattern.compile(".*x")).toString()).isEqualTo("first(\".*x\", 0)");
  }

  @Test public void regex_noMatch() {
    assertThat(first(Pattern.compile(".*x")).in("bar")).isEmpty();
    assertThat(first(Pattern.compile(".*x")).in("")).isEmpty();
    assertThat(first(Pattern.compile(".*x")).repeatedly().match("")).isEmpty();
  }

  @Test public void regex_matchesFullString() {
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
    assertThat(first(Pattern.compile(".*oo")).repeatedly().from("foo"))
        .containsExactly("foo");
  }

  @Test public void regex_matchesPrefix() {
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
    assertThat(first(Pattern.compile(".*oo")).repeatedly().from("foobar"))
        .containsExactly("foo");
  }

  @Test public void regex_matchesPrefixFollowedBySamePattern() {
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
    assertThat(first(Pattern.compile(".oo")).repeatedly().from("foodoobar"))
        .containsExactly("foo", "doo");
  }

  @Test public void regex_matchesPrefixWithStartingAnchor() {
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
    assertThat(first(Pattern.compile("^.oo")).repeatedly().from("foobar"))
        .containsExactly("foo");
  }

  @Test public void regex_matchesPrefixFollowedBySamePattern_withStartingAnchor() {
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
    assertThat(first(Pattern.compile("^.oo")).repeatedly().from("foodoobar"))
        .containsExactly("foo");
  }

  @Test public void regex_doesNotMatchPrefixDueToStartingAnchor() {
    assertThat(first(Pattern.compile("^oob.")).in("foobar")).isEmpty();
    assertThat(first(Pattern.compile("^oob")).repeatedly().match("foobar")).isEmpty();
  }

  @Test public void regex_matchesSuffix() {
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
    assertThat(first(Pattern.compile("b.*")).repeatedly().from("foobar"))
        .containsExactly("bar");
  }

  @Test public void regex_matchesSuffixWithEndingAnchor() {
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
    assertThat(first(Pattern.compile("b.*$")).repeatedly().from("foobar"))
        .containsExactly("bar");
  }

  @Test public void regex_doesNotMatchPostfixDueToEndingAnchor() {
    assertThat(first(Pattern.compile("b.$")).in("foobar")).isEmpty();
    assertThat(first(Pattern.compile("b.$")).repeatedly().match("foobar")).isEmpty();
  }

  @Test public void regex_matchesInTheMiddle() {
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
    assertThat(first(Pattern.compile(".ar")).repeatedly().from("foobarbaz"))
        .containsExactly("bar");
    assertThat(first(Pattern.compile(".ar")).repeatedly().from("foobarzbarbaz"))
        .containsExactly("bar", "bar");
    assertThat(first(Pattern.compile(".ar")).repeatedly().from("foobarbazbar"))
        .containsExactly("bar", "bar");
  }

  @Test public void regex_emptySnippet() {
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
    assertThat(first(Pattern.compile("")).repeatedly().from("foo"))
        .contains("");
    assertThat(first(Pattern.compile("^")).repeatedly().from("foo"))
        .containsExactly("");
    assertThat(first(Pattern.compile("$")).repeatedly().from("foo"))
        .containsExactly("");
  }

  @Test public void regex_matchesFirstOccurrence() {
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
    assertThat(first(Pattern.compile(".ar")).repeatedly().from("foobarbarbaz"))
        .containsExactly("bar", "bar");
    assertThat(first(Pattern.compile(".ar")).repeatedly().from("foobarbazbar"))
        .containsExactly("bar", "bar");
  }

  @Test public void regexGroup_noMatch() {
    assertThat(first(Pattern.compile("(.*)x"), 1).in("bar")).isEmpty();
    assertThat(first(Pattern.compile("(.*x)"), 1).in("bar")).isEmpty();
    assertThat(first(Pattern.compile(".*(x)"), 1).in("bar")).isEmpty();
    assertThat(first(Pattern.compile("(.*x)"), 1).in("")).isEmpty();
    assertThat(first(Pattern.compile("(.*x)"), 1).repeatedly().match("")).isEmpty();
  }

  @Test public void regexGroup_matchesFirstGroup() {
    Optional<Substring.Match> match = first(Pattern.compile("(f.)b.*"), 1).in("fobar");
    assertThat(match.get().after()).isEqualTo("bar");
    assertThat(match.get().remove()).isEqualTo("bar");
    assertThat(match.get().replaceWith("")).isEqualTo("bar");
    assertThat(match.get().replaceWith("c")).isEqualTo("cbar");
    assertThat(match.get().replaceWith("car")).isEqualTo("carbar");
    assertThat(match.get().length()).isEqualTo(2);
    assertThat(match.get().toString()).isEqualTo("fo");
    assertThat(first(Pattern.compile("(f.)b.*"), 1).repeatedly().from("fobar"))
        .containsExactly("fo");
    assertThat(first(Pattern.compile("(f.)b.."), 1).repeatedly().from("fobar"))
        .containsExactly("fo");
    assertThat(first(Pattern.compile("(f.)b.."), 1).repeatedly().from("fobarfubaz"))
        .containsExactly("fo", "fu");
  }

  @Test public void regexGroup_matchesSecondGroup() {
    Optional<Substring.Match> match = first(Pattern.compile("f(o.)(ba.)"), 2).in("foobarbaz");
    assertThat(match.get().after()).isEqualTo("baz");
    assertThat(match.get().remove()).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("")).isEqualTo("foobaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocbaz");
    assertThat(match.get().replaceWith("car")).isEqualTo("foocarbaz");
    assertThat(match.get().length()).isEqualTo(3);
    assertThat(match.get().toString()).isEqualTo("bar");
    assertThat(first(Pattern.compile("f(o.)(ba.)"), 1).repeatedly().from("foobarbaz"))
        .containsExactly("oo");
    assertThat(
            first(Pattern.compile("f(o.)(ba.);"), 1).repeatedly().match("foobar; foibaz;")
                .map(Object::toString))
        .containsExactly("oo", "oi");
  }

  @Test public void regexGroup_group0() {
    Optional<Substring.Match> match = first(Pattern.compile("f(o.)(ba.).*"), 0).in("foobarbaz");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("c")).isEqualTo("c");
    assertThat(match.get().replaceWith("car")).isEqualTo("car");
    assertThat(match.get().length()).isEqualTo(9);
    assertThat(match.get().toString()).isEqualTo("foobarbaz");
    assertThat(
            first(Pattern.compile("f(o.)(ba.).*"), 0).repeatedly().from("foobarbaz"))
        .containsExactly("foobarbaz");
  }

  @Test public void regexGroup_negativeGroup() {
    assertThrows(IndexOutOfBoundsException.class, () -> first(Pattern.compile("."), -1));
  }

  @Test public void regexGroup_invalidGroupIndex() {
    assertThrows(IndexOutOfBoundsException.class, () -> first(Pattern.compile("f(o.)(ba.)"), 3));
  }

  @Test public void regexGroup_optionalGroupNotParticipating() {
    // Pattern: (a)?(b) - first group optional
    // Input: "b" - group 1 doesn't participate, should return empty Optional
    assertThat(first(Pattern.compile("(a)?(b)"), 1).in("b")).isEmpty();
    // Group 2 still participates
    assertThat(first(Pattern.compile("(a)?(b)"), 2).in("b").get().toString()).isEqualTo("b");
  }

  @Test public void regexGroup_optionalGroupParticipates() {
    // When optional group does participate
    assertThat(first(Pattern.compile("(a)?(b)"), 1).in("ab").get().toString()).isEqualTo("a");
    assertThat(first(Pattern.compile("(a)?(b)"), 2).in("ab").get().toString()).isEqualTo("b");
  }

  @Test public void regexGroup_optionalGroupNotParticipating_repeatedly() {
    // repeatedly() stops when first match has non-participating group
    assertThat(first(Pattern.compile("(a)?(b)"), 1).repeatedly().from("b"))
        .isEmpty();
    assertThat(first(Pattern.compile("(a)?(b)"), 1).repeatedly().from("bb"))
        .isEmpty();
  }

  @Test public void regexGroup_decimalNumberOptional() {
    // Pattern: \d+(\.\d+)? - optional decimal part
    // "price: 42" - no decimal part, group 1 doesn't participate
    assertThat(first(Pattern.compile("\\d+(\\.\\d+)?"), 1).in("price: 42")).isEmpty();
    // "price: 42.99" - has decimal, should return ".99"
    Substring.Match match = first(Pattern.compile("\\d+(\\.\\d+)?"), 1).in("price: 42.99").get();
    assertThat(match.toString()).isEqualTo(".99");
    assertThat(match.before()).isEqualTo("price: 42");
    assertThat(match.after()).isEmpty();
  }

  @Test public void regexGroup_multipleOptionalGroups() {
    // Pattern: (a)?(b)?(c) - two optional groups
    // Only group 3 participates
    assertThat(first(Pattern.compile("(a)?(b)?(c)"), 1).in("c")).isEmpty();
    assertThat(first(Pattern.compile("(a)?(b)?(c)"), 2).in("c")).isEmpty();
    assertThat(first(Pattern.compile("(a)?(b)?(c)"), 3).in("c").get().toString()).isEqualTo("c");
    // Groups 1 and 3 participate, group 2 doesn't
    assertThat(first(Pattern.compile("(a)?(b)?(c)"), 1).in("ac").get().toString()).isEqualTo("a");
    assertThat(first(Pattern.compile("(a)?(b)?(c)"), 2).in("ac")).isEmpty();
    assertThat(first(Pattern.compile("(a)?(b)?(c)"), 3).in("ac").get().toString()).isEqualTo("c");
  }

  @Test public void regexGroup_nestedOptionalGroups() {
    // Pattern with nested optional groups: ((a)b)?(c)
    // "c" - outer group doesn't participate, inner group (c) does
    assertThat(first(Pattern.compile("((a)b)?(c)"), 1).in("c")).isEmpty();
  }

  @Test public void regexGroup_alternationWithOptionalGroup() {
    // Pattern: (a)?b|c - group 1 is optional in first alternative
    assertThat(first(Pattern.compile("(a)?b|c"), 1).in("b")).isEmpty();
    assertThat(first(Pattern.compile("(a)?b|c"), 1).in("ab").get().toString()).isEqualTo("a");
    // "c" matches second alternative, group 1 doesn't participate
    assertThat(first(Pattern.compile("(a)?b|c"), 1).in("c")).isEmpty();
  }

  @Test public void regexGroup_zeroLengthMatch() {
    // Pattern with * quantifier that can match zero times
    // a*b* - both can match zero times (but still participate)
    // When pattern matches but group is empty, it should still return a match
    assertThat(first(Pattern.compile("(a*)(b*)"), 1).in("c").get().toString()).isEmpty();
    assertThat(first(Pattern.compile("(a*)(b*)"), 2).in("c").get().toString()).isEmpty();
    assertThat(first(Pattern.compile("(a*)(b*)"), 1).in("aa").get().toString()).isEqualTo("aa");
    assertThat(first(Pattern.compile("(a*)(b*)"), 2).in("aa").get().toString()).isEmpty();
  }

  @Test public void lastSnippet_toString() {
    assertThat(last("foo").toString()).isEqualTo("last('foo')");
  }

  @Test public void lastSnippet_noMatch() {
    assertThat(last("foo").in("bar")).isEmpty();
    assertThat(last("foo").in("")).isEmpty();
    assertThat(last("foo").repeatedly().match("bar")).isEmpty();
    assertThat(last("f").repeatedly().match("")).isEmpty();
  }

  @Test public void lastSnippet_matchesFullString() {
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
    assertThat(last("foo").repeatedly().from("foo")).containsExactly("foo");
  }

  @Test public void lastSnippet_matchesPrefix() {
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
    assertThat(last("foo").repeatedly().from("foobar")).containsExactly("foo");
  }

  @Test public void lastSnippet_matchesPrefixPrecededBySamePattern() {
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
    assertThat(last("foo").repeatedly().from("foobarfoobaz")).containsExactly("foo");
  }

  @Test public void lastSnippet_matchesSuffix() {
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
    assertThat(last("bar").repeatedly().from("foobar")).containsExactly("bar");
  }

  @Test public void lastSnippet_matchesInTheMiddle() {
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
    assertThat(last("bar").repeatedly().from("foobarbaz")).containsExactly("bar");
  }

  @Test public void lastSnippet_matchesLastOccurrence() {
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
    assertThat(last("bar").repeatedly().from("barfoobarbaz")).containsExactly("bar");
  }

  @Test public void lastSnippet_emptySnippet() {
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
    assertThat(last("").repeatedly().from("foo")).containsExactly("");
  }

  @Test public void firstChar_toString() {
    assertThat(first('f').toString()).isEqualTo("first('f')");
  }

  @Test public void firstChar_noMatch() {
    assertThat(first('f').in("bar")).isEmpty();
    assertThat(first('f').in("")).isEmpty();
    assertThat(first('f').repeatedly().match("bar")).isEmpty();
    assertThat(first('f').repeatedly().match("")).isEmpty();
  }

  @Test public void firstChar_matchesFullString() {
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
    assertThat(first('f').repeatedly().from("f")).containsExactly("f");
  }

  @Test public void firstChar_matchesPrefix() {
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
    assertThat(first('f').repeatedly().from("foobar")).containsExactly("f");
  }

  @Test public void firstChar_matchesPrefixFollowedBySameChar() {
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
    assertThat(first('f').repeatedly().from("foofar")).containsExactly("f", "f");
  }

  @Test public void firstChar_matchesSuffix() {
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
    assertThat(first('r').repeatedly().from("foofar")).containsExactly("r");
  }

  @Test public void firstChar_matchesFirstOccurrence() {
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
    assertThat(first('b').repeatedly().from("foobarbarbaz"))
        .containsExactly("b", "b", "b");
  }

  @Test
  public void firstCharMatcher_toString() {
    assertThat(first(CharPredicate.ASCII).toString())
        .isEqualTo("first(ASCII)");
  }

  @Test
  public void firstCharMatcher_noMatch() {
    assertThat(first(CharPredicate.ANY).in("")).isEmpty();
    assertThat(first(CharPredicate.ANY).repeatedly().match("")).isEmpty();
  }

  @Test
  public void firstCharMatcher_matchesFullString() {
    Optional<Substring.Match> match = first(CharPredicate.ANY).in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(first(CharPredicate.ANY).repeatedly().from("f")).containsExactly("f");
  }

  @Test
  public void firstCharMatcher_matchesPrefix() {
    Optional<Substring.Match> match = first(CharPredicate.is('f')).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith("")).isEqualTo("oobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("coobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(first(CharPredicate.ANY).repeatedly().from("foo")).containsExactly("f", "o", "o");
  }

  @Test
  public void firstCharMatcher_matchesSuffix() {
    Optional<Substring.Match> match = first(CharPredicate.is('r')).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("fooba");
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEqualTo("fooba");
    assertThat(match.get().replaceWith("")).isEqualTo("fooba");
    assertThat(match.get().replaceWith("c")).isEqualTo("foobac");
    assertThat(match.get().replaceWith("car")).isEqualTo("foobacar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("r");
    assertThat(first(CharPredicate.is('r')).repeatedly().from("bar")).containsExactly("r");
  }

  @Test
  public void firstCharMatcher_matchesFirstOccurrence() {
    Optional<Substring.Match> match = first(CharPredicate.is('b')).in("foobarbarbaz");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEqualTo("foo");
    assertThat(match.get().after()).isEqualTo("arbarbaz");
    assertThat(match.get().remove()).isEqualTo("fooarbarbaz");
    assertThat(match.get().replaceWith("")).isEqualTo("fooarbarbaz");
    assertThat(match.get().replaceWith("c")).isEqualTo("foocarbarbaz");
    assertThat(match.get().replaceWith("coo")).isEqualTo("foocooarbarbaz");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("b");
    assertThat(first(CharPredicate.is('b')).repeatedly().from("foobarbarbaz"))
        .containsExactly("b", "b", "b");
  }

  @Test public void lastChar_noMatch() {
    assertThat(last('f').in("bar")).isEmpty();
    assertThat(last('f').in("")).isEmpty();
    assertThat(last('f').repeatedly().match("bar")).isEmpty();
    assertThat(last('f').repeatedly().match("")).isEmpty();
    assertThat(last("f").in("bar")).isEmpty();
    assertThat(last("f").in("")).isEmpty();
    assertThat(last("f").repeatedly().match("bar")).isEmpty();
    assertThat(last("f").repeatedly().match("")).isEmpty();
  }

  @Test public void lastChar_matchesFullString() {
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
    assertThat(last('f').repeatedly().from("f")).containsExactly("f");
    assertThat(last("f").repeatedly().from("f")).containsExactly("f");
  }

  @Test public void lastChar_matchesPrefix() {
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
    assertThat(last('f').repeatedly().from("foobar")).containsExactly("f");
    assertThat(last("f").repeatedly().from("foobar")).containsExactly("f");
  }

  @Test public void lastChar_matchesSuffix() {
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
    assertThat(last('r').repeatedly().from("farbar")).containsExactly("r");
    assertThat(last("r").repeatedly().from("farbar")).containsExactly("r");
  }

  @Test public void lastChar_matchesLastOccurrence() {
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
    assertThat(last('b').repeatedly().from("farbarbaz")).containsExactly("b");
    assertThat(last("b").repeatedly().from("farbarbaz")).containsExactly("b");
  }

  @Test
  public void lastCharMatcher_toString() {
    assertThat(last(CharPredicate.ASCII).toString())
        .isEqualTo("last(ASCII)");
  }

  @Test
  public void lastCharMatcher_noMatch() {
    assertThat(last(CharPredicate.ANY).in("")).isEmpty();
    assertThat(last(CharPredicate.ANY).repeatedly().match("")).isEmpty();
  }

  @Test
  public void lastCharMatcher_matchesFullString() {
    Optional<Substring.Match> match = last(CharPredicate.ANY).in("f");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEmpty();
    assertThat(match.get().remove()).isEmpty();
    assertThat(match.get().replaceWith("")).isEmpty();
    assertThat(match.get().replaceWith("b")).isEqualTo("b");
    assertThat(match.get().replaceWith("bar")).isEqualTo("bar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(last(CharPredicate.ANY).repeatedly().from("bar")).contains("r");
  }

  @Test
  public void lastCharMatcher_matchesPrefix() {
    Optional<Substring.Match> match = last(CharPredicate.is('f')).in("foobar");
    assertThat(match).isPresent();
    assertThat(match.get().before()).isEmpty();
    assertThat(match.get().after()).isEqualTo("oobar");
    assertThat(match.get().remove()).isEqualTo("oobar");
    assertThat(match.get().replaceWith("")).isEqualTo("oobar");
    assertThat(match.get().replaceWith("c")).isEqualTo("coobar");
    assertThat(match.get().replaceWith("car")).isEqualTo("caroobar");
    assertThat(match.get().length()).isEqualTo(1);
    assertThat(match.get().toString()).isEqualTo("f");
    assertThat(last(CharPredicate.is('f')).repeatedly().from("foobar")).contains("f");
  }

  @Test public void removeFrom_noMatch() {
    assertThat(first('f').removeFrom("bar")).isEqualTo("bar");
  }

  @Test public void removeFrom_match() {
    assertThat(first('f').removeFrom("foo")).isEqualTo("oo");
  }

  @Test public void removeAllFrom_noMatch() {
    assertThat(first('f').repeatedly().removeAllFrom("bar")).isEqualTo("bar");
  }

  @Test public void removeAllFrom_oneMatch() {
    assertThat(first('f').repeatedly().removeAllFrom("foo")).isEqualTo("oo");
    assertThat(first('f').repeatedly().removeAllFrom("afoo")).isEqualTo("aoo");
    assertThat(first('f').repeatedly().removeAllFrom("oof")).isEqualTo("oo");
  }

  @Test public void removeAllFrom_twoMatches() {
    assertThat(first('o').repeatedly().removeAllFrom("foo")).isEqualTo("f");
    assertThat(first('o').repeatedly().removeAllFrom("ofo")).isEqualTo("f");
    assertThat(first('o').repeatedly().removeAllFrom("oof")).isEqualTo("f");
  }

  @Test public void removeAllFrom_threeMatches() {
    assertThat(first("x").repeatedly().removeAllFrom("fox x bxr")).isEqualTo("fo  br");
    assertThat(first("x").repeatedly().removeAllFrom("xaxbxxxcxx")).isEqualTo("abc");
  }

  @Test public void replaceFrom_noMatch() {
    assertThat(first('f').replaceFrom("bar", "")).isEqualTo("bar");
    assertThat(first('f').replaceFrom("bar", "x")).isEqualTo("bar");
    assertThat(first('f').replaceFrom("bar", "xyz")).isEqualTo("bar");
  }

  @Test public void replaceFrom_match() {
    assertThat(first('f').replaceFrom("foo", "")).isEqualTo("oo");
    assertThat(first('f').replaceFrom("foo", "b")).isEqualTo("boo");
    assertThat(first('f').replaceFrom("foo", "bar")).isEqualTo("baroo");
  }

  @Test public void replaceFrom_withFunction_noMatch() {
    assertThat(first('f').replaceFrom("bar", m -> "")).isEqualTo("bar");
    assertThat(first('f').replaceFrom("bar", m -> "x")).isEqualTo("bar");
    assertThat(first('f').replaceFrom("bar", m -> "xyz")).isEqualTo("bar");
  }

  @Test public void replaceFrom_withFunction_match() {
    assertThat(first('f').replaceFrom("foo", m -> "")).isEqualTo("oo");
    assertThat(first('f').replaceFrom("foo", m -> "b")).isEqualTo("boo");
    assertThat(first('f').replaceFrom("foo", m -> "bar")).isEqualTo("baroo");
  }

  @Test public void replaceFrom_withFunction_functionReturnsNull() {
    assertThrows(NullPointerException.class, () -> first('f').replaceFrom("foo", m -> null));
  }

  @Test public void replaceAllFrom_noMatch() {
    assertThat(first('f').repeatedly().replaceAllFrom("bar", m -> "x")).isEqualTo("bar");
    assertThat(first('f').repeatedly().replaceAllFrom("bar", m -> "xyz")).isEqualTo("bar");
  }

  @Test public void replaceAllFrom_oneMatch() {
    assertThat(first('f').repeatedly().replaceAllFrom("foo", m -> "xx")).isEqualTo("xxoo");
    assertThat(first('f').repeatedly().replaceAllFrom("afoo", m -> "xx")).isEqualTo("axxoo");
    assertThat(first('f').repeatedly().replaceAllFrom("oof", m -> "xx")).isEqualTo("ooxx");
  }

  @Test public void replaceAllFrom_twoMatches() {
    assertThat(first('o').repeatedly().replaceAllFrom("foo", m -> "xx")).isEqualTo("fxxxx");
    assertThat(first('o').repeatedly().replaceAllFrom("ofo", m -> "xx")).isEqualTo("xxfxx");
    assertThat(first('o').repeatedly().replaceAllFrom("oof", m -> "xx")).isEqualTo("xxxxf");
  }

  @Test public void replaceAllFrom_threeMatches() {
    assertThat(first("x").repeatedly().replaceAllFrom("fox x bxr", m -> "yy")).isEqualTo("foyy yy byyr");
    assertThat(first("x").repeatedly().replaceAllFrom("xaxbxxcx", m -> "yy")).isEqualTo("yyayybyyyycyy");
  }

  @Test public void replaceAllFrom_placeholderSubstitution() {
    Substring.Pattern placeholder = Substring.between(before(first('{')), after(first('}')));
    ImmutableMap<String, String> dictionary = ImmutableMap.of("{key}", "foo", "{value}", "bar");
    assertThat(
            placeholder.repeatedly().replaceAllFrom("/{key}:{value}/", match -> dictionary.get(match.toString())))
        .isEqualTo("/foo:bar/");
  }

  @Test public void replaceAllFrom_replacementFunctionReturnsNull() {
    Substring.Pattern placeholder = Substring.between(before(first('{')), after(first('}')));
    NullPointerException thrown =
        assertThrows(
            NullPointerException.class,
            () -> placeholder.repeatedly().replaceAllFrom("{unknown}", match -> null));
    assertThat(thrown).hasMessageThat().contains("{unknown}");
  }

  @Test public void replaceAllFrom_replacementFunctionReturnsNonEmpty() {
    assertThat(Substring.first("var").repeatedly().replaceAllFrom("var=x", m -> "v")).isEqualTo("v=x");
  }

  @Test public void delimit() {
    assertThat(first(',').repeatedly().split("foo").map(Match::toString))
        .containsExactly("foo");
    assertThat(first(',').repeatedly().split("foo, bar").map(Match::toString))
        .containsExactly("foo", " bar");
    assertThat(first(',').repeatedly().split("foo,").map(Match::toString))
        .containsExactly("foo", "");
    assertThat(first(',').repeatedly().split("foo,bar, ").map(Match::toString))
        .containsExactly("foo", "bar", " ");
  }

  @Test public void repeatedly_split_beginning() {
    assertThat(BEGINNING.repeatedly().split("foo").map(Object::toString))
        .containsExactly("", "f", "o", "o", "")
        .inOrder();
  }

  @Test public void repeatedly_split_end() {
    assertThat(END.repeatedly().split("foo").map(Object::toString))
        .containsExactly("foo", "")
        .inOrder();
  }

  @Test public void repeatedly_split_empty() {
    assertThat(first("").repeatedly().split("foo").map(Object::toString))
        .containsExactly("", "f", "o", "o", "")
        .inOrder();
  }

  @Test public void repeatedly_split_distinct() {
    assertThat(first(',').repeatedly().split("b,a,c,a,c,b,d").map(Match::toString).distinct())
        .containsExactly("b", "a", "c", "d")
        .inOrder();
  }

  @Test public void repeatedly_split_ordered() {
    assertThat(first(',').repeatedly().split("a,b").spliterator().characteristics() & Spliterator.ORDERED)
        .isEqualTo(Spliterator.ORDERED);
  }

  @Test public void repeatedly_split_nonNull() {
    assertThat(first(',').repeatedly().split("a,b").spliterator().characteristics() & Spliterator.NONNULL)
        .isEqualTo(Spliterator.NONNULL);
  }

  @Test public void repeatedly_splitThenTrim_noMatch() {
    assertThat(first("://").repeatedly().splitThenTrim("abc").map(Match::toString))
        .containsExactly("abc");
  }

  @Test public void repeatedly_splitThenTrim_match() {
    assertThat(first("//").repeatedly().splitThenTrim("// foo").map(Match::toString))
        .containsExactly("", "foo");
    assertThat(first("/").repeatedly().splitThenTrim("foo / bar").map(Match::toString))
        .containsExactly("foo", "bar");
    assertThat(first("/").repeatedly().splitThenTrim(" foo/bar/").map(Match::toString))
        .containsExactly("foo", "bar", "");
  }

  @Test public void repeatedly_splitKeyValuesAround_empty() {
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), ""))
        .isEmpty();
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), ",,"))
        .isEmpty();
  }

  @Test public void repeatedly_splitKeyValuesAround() {
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), "k1=v1,k2=v2"))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), "k1=v1,,"))
        .containsExactly("k1", "v1")
        .inOrder();
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), ",k1 = v1 =x,"))
        .containsExactly("k1 ", " v1 =x")
        .inOrder();
  }

  @Test public void repeatedly_splitKeyValuesAround_emptyKey() {
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), "= v1"))
        .containsExactly("", " v1")
        .inOrder();
  }

  @Test public void repeatedly_splitKeyValuesAround_emptyValue() {
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), " k =,"))
        .containsExactly(" k ", "")
        .inOrder();
  }

  @Test public void repeatedly_splitKeyValuesAround_emptyKeyValue() {
    assertKeyValues(first(',').repeatedly().splitKeyValuesAround(first('='), ",=,"))
        .containsExactly("", "")
        .inOrder();
  }

  @Test public void repeatedly_splitKeyValuesAround_keyValueSeparatorNotFound() {
    BiStream<String, String> kvs =
        first(',').repeatedly().splitKeyValuesAround(first('='), "k=v, ");
    assertThrows(IllegalArgumentException.class, () -> kvs.toMap());
  }

  @Test public void repeatedly_splitThenTrimKeyValuesAround_empty() {
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), ""))
        .isEmpty();
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), " "))
        .isEmpty();
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), ", ,"))
        .isEmpty();
  }

  @Test public void repeatedly_splitThenTrimKeyValuesAround() {
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), "k1 = v1, k2=v2"))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), "k1=v1,,"))
        .containsExactly("k1", "v1")
        .inOrder();
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), ", k1=v1=x ,"))
        .containsExactly("k1", "v1=x")
        .inOrder();
  }

  @Test public void repeatedly_splitThenTrimKeyValuesAround_emptyKey() {
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), " = v1"))
        .containsExactly("", "v1")
        .inOrder();
  }

  @Test public void repeatedly_splitThenTrimKeyValuesAround_emptyValue() {
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), " k =,"))
        .containsExactly("k", "")
        .inOrder();
  }

  @Test public void repeatedly_splitThenTrimKeyValuesAround_emptyKeyValue() {
    assertKeyValues(first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), ",=,"))
        .containsExactly("", "")
        .inOrder();
  }

  @Test public void repeatedly_splitThenTrimKeyValuesAround_keyValueSeparatorNotFound() {
    BiStream<String, String> kvs =
        first(',').repeatedly().splitThenTrimKeyValuesAround(first('='), "k:v");
    assertThrows(IllegalArgumentException.class, () -> kvs.toMap());
  }

  @Test public void repeatedly_alternationFrom_empty() {
    assertKeyValues(first(',').repeatedly().alternationFrom(""))
        .isEmpty();
  }

  @Test public void repeatedly_alternationFrom() {
    Substring.Pattern bulletNumber = consecutive(CharPredicate.range('0', '9'))
        .separatedBy(CharPredicate.WORD.not(), CharPredicate.is(':'));
    Map<Integer, String> bulleted = bulletNumber.repeatedly()
        .alternationFrom("1: go home;2: feed 2 cats 3: sleep tight.")
        .mapKeys(n -> Integer.parseInt(n))
        .mapValues(withColon -> prefix(":").removeFrom(withColon.toString()).trim())
        .toMap();
    assertThat(bulleted).containsExactly(1, "go home;", 2, "feed 2 cats", 3, "sleep tight.");
  }

  @Test public void repeatedly_splitThenTrim_distinct() {
    assertThat(first(',').repeatedly().splitThenTrim("b, a,c,a,c,b,d").map(Match::toString).distinct())
        .containsExactly("b", "a", "c", "d")
        .inOrder();
  }

  @Test public void repeatedly_splitThenTrim_ordered() {
    assertThat(first(',').repeatedly().splitThenTrim("a,b").spliterator().characteristics() & Spliterator.ORDERED)
        .isEqualTo(Spliterator.ORDERED);
  }

  @Test
  public void split_cannotSplit() {
    assertThat(first('=').split("foo:bar")).isEqualTo(BiOptional.empty());
    assertThat(first('=').split("foo:bar", (k, v) -> k + v)).isEmpty();
  }

  @Test
  public void split_canSplit() {
    assertThat(first('=').split(" foo=bar").map((String k, String v) -> k)).hasValue(" foo");
    assertThat(first('=').split("foo=bar ").map((String k, String v) -> v)).hasValue("bar ");
    assertThat(first('=').split(" foo=bar", (k, v) -> k)).hasValue(" foo");
    assertThat(first('=').split("foo=bar ", (k, v) -> v)).hasValue("bar ");
  }

  @Test
  public void split_beginning() {
    assertThat(BEGINNING.split(" foo").map((String k, String v) -> k)).hasValue("");
    assertThat(BEGINNING.split(" foo").map((String k, String v) -> v)).hasValue(" foo");
    assertThat(BEGINNING.split(" foo", (k, v) -> k)).hasValue("");
    assertThat(BEGINNING.split(" foo", (k, v) -> v)).hasValue(" foo");
  }

  @Test
  public void split_end() {
    assertThat(END.split(" foo").map((String k, String v) -> k)).hasValue(" foo");
    assertThat(END.split(" foo").map((String k, String v) -> v)).hasValue("");
    assertThat(END.split(" foo", (k, v) -> k)).hasValue(" foo");
    assertThat(END.split(" foo", (k, v) -> v)).hasValue("");
  }

  @Test
  public void splitThenTrim_cannotSplit() {
    assertThat(first('=').splitThenTrim("foo:bar")).isEqualTo(BiOptional.empty());
    assertThat(first('=').splitThenTrim("foo:bar", (k, v) -> k + v)).isEmpty();
  }

  @Test
  public void splitThenTrim_canSplit() {
    assertThat(first('=').splitThenTrim(" foo =bar").map((String k, String v) -> k))
        .hasValue("foo");
    assertThat(first('=').splitThenTrim("foo = bar ").map((String k, String v) -> v))
        .hasValue("bar");
    assertThat(first('=').splitThenTrim(" foo =bar", (k, v) -> k)).hasValue("foo");
    assertThat(first('=').splitThenTrim("foo = bar ", (k, v) -> v)).hasValue("bar");
  }

  @Test
  public void splitThenTrim_beginning() {
    assertThat(BEGINNING.splitThenTrim(" foo").map((String k, String v) -> k)).hasValue("");
    assertThat(BEGINNING.splitThenTrim(" foo").map((String k, String v) -> v)).hasValue("foo");
    assertThat(BEGINNING.splitThenTrim(" foo", (k, v) -> k)).hasValue("");
    assertThat(BEGINNING.splitThenTrim(" foo", (k, v) -> v)).hasValue("foo");
  }

  @Test
  public void splitThenTrim_end() {
    assertThat(END.splitThenTrim(" foo ").map((String k, String v) -> k)).hasValue("foo");
    assertThat(END.splitThenTrim(" foo").map((String k, String v) -> v)).hasValue("");
    assertThat(END.splitThenTrim(" foo ", (k, v) -> k)).hasValue("foo");
    assertThat(END.splitThenTrim(" foo", (k, v) -> v)).hasValue("");
  }

  @Test public void splitThenTrim_intoTwoParts_cannotSplit() {
    assertThat(first('=').splitThenTrim("foo:bar")).isEqualTo(BiOptional.empty());
  }

  @Test public void splitThenTrim_intoTwoParts_canSplit() {
    assertThat(first('=').splitThenTrim(" foo =bar").map((String k, String v) -> k)).hasValue("foo");
    assertThat(first('=').splitThenTrim("foo = bar ").map((String k, String v) -> v)).hasValue("bar");
  }

  @Test public void matchAsCharSequence() {
    CharSequence match = first(" >= ").in("foo >= bar").get();
    assertThat(match.length()).isEqualTo(4);
    assertThat(match.charAt(0)).isEqualTo(' ');
    assertThat(match.charAt(1)).isEqualTo('>');
    assertThat(match.charAt(2)).isEqualTo('=');
    assertThat(match.charAt(3)).isEqualTo(' ');
    assertThat(match.toString()).isEqualTo(" >= ");
    assertThat(match.toString()).isEqualTo(" >= ");
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(4));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MAX_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.charAt(Integer.MIN_VALUE));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(5, 5));
    assertThrows(IndexOutOfBoundsException.class, () -> match.subSequence(0, 5));
  }

  @Test public void matchAsCharSequence_subSequence() {
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

  @Test public void matchAsCharSequence_subSequence_emptyAtHead() {
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

  @Test public void matchAsCharSequence_subSequence_emptyInTheMiddle() {
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

  @Test public void matchAsCharSequence_subSequence_emptyAtEnd() {
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

  @Test public void matchAsCharSequence_subSequence_full() {
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

  @Test public void matchAsCharSequence_subSequence_partial() {
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
  public void matchLimit_negative() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThrows(IllegalArgumentException.class, () -> match.limit(-1));
    assertThrows(IllegalArgumentException.class, () -> match.limit(Integer.MIN_VALUE));
  }

  @Test
  public void matchLimit_zero() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.limit(0).toString()).isEmpty();
  }

  @Test
  public void matchLimit_smallerThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.limit(1).toString()).isEqualTo("f");
  }

  @Test
  public void matchLimit_equalToLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.limit(3)).isSameInstanceAs(match);
  }

  @Test
  public void matchLimit_greaterThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.limit(4)).isSameInstanceAs(match);
    assertThat(match.limit(5)).isSameInstanceAs(match);
    assertThat(match.limit(Integer.MAX_VALUE)).isSameInstanceAs(match);
  }

  @Test
  public void limit_negative() {
    Substring.Pattern pattern = first("foo");
    assertThrows(IllegalArgumentException.class, () -> pattern.limit(-1));
    assertThrows(IllegalArgumentException.class, () -> pattern.limit(Integer.MIN_VALUE));
  }

  @Test
  public void limit_noMatch() {
    Substring.Pattern pattern = first("foo");
    assertThat(pattern.limit(1).from("fur")).isEmpty();
  }

  @Test
  public void limit_smallerThanSize() {
    assertThat(first("foo").limit(1).from("my food")).hasValue("f");
    assertThat(first("foo").limit(1).repeatedly().from("my food")).containsExactly("f");
    assertThat(first("foo").limit(1).repeatedly().from("my ffoof")).containsExactly("f");
    assertThat(first("fff").limit(1).repeatedly().from("fffff")).containsExactly("f");
    assertThat(first("fff").limit(2).repeatedly().from("ffffff")).containsExactly("ff", "ff");
  }

  @Test
  public void limit_equalToSize() {
    assertThat(first("foo").limit(3).from("my food")).hasValue("foo");
    assertThat(first("foo").limit(3).repeatedly().from("my food")).containsExactly("foo");
    assertThat(first("foo").limit(3).repeatedly().from("my ffoof")).containsExactly("foo");
    assertThat(first("fff").limit(3).repeatedly().from("fffff")).containsExactly("fff");
  }

  @Test
  public void limit_greaterThanSize() {
    assertThat(first("foo").limit(Integer.MAX_VALUE).from("my food")).hasValue("foo");
    assertThat(first("foo").limit(Integer.MAX_VALUE).repeatedly().from("my food"))
        .containsExactly("foo");
    assertThat(first("foo").limit(Integer.MAX_VALUE).repeatedly().from("my ffoof"))
        .containsExactly("foo");
    assertThat(first("fff").limit(Integer.MAX_VALUE).repeatedly().from("ffffff"))
        .containsExactly("fff", "fff");
  }

  @Test
  public void limit_toString() {
    assertThat(first("foo").limit(2).toString()).isEqualTo("first('foo').limit(2)");
  }

  @Test
  public void matchExpand_zeroExpansion() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.expand(0, 0).toString()).isEqualTo("foo");
  }

  @Test
  public void matchExpand_negativeExpansionDisallowed() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThrows(IllegalArgumentException.class, () -> match.expand(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> match.expand(0, -1));
  }

  @Test
  public void matchExpand_expandingBeyondScopeDisallowed() {
    Substring.Match match = first("bar").in(" foo bar ").get();
    assertThrows(IllegalStateException.class, () -> match.expand(6, 0));
    assertThrows(IllegalStateException.class, () -> match.expand(0, 2));
  }

  @Test
  public void matchExpand_expandingToLeft() {
    Substring.Match match = first("bar").in(" foo bar ").get();
    assertThat(match.expand(2, 0).toString()).isEqualTo("o bar");
  }

  @Test
  public void matchExpand_expandingToRight() {
    Substring.Match match = first("foo").in(" foo bar ").get();
    assertThat(match.expand(0, 3).toString()).isEqualTo("foo ba");
  }

  @Test
  public void matchExpand_expandingBothDirections() {
    Substring.Match match = first("foo").in(" foo bar ").get();
    assertThat(match.expand(1, 2).toString()).isEqualTo(" foo b");
  }

  @Test
  public void skipFromBeginning_negative() {
    Substring.Pattern pattern = first("foo");
    assertThrows(IllegalArgumentException.class, () -> pattern.skip(-1, 1));
    assertThrows(IllegalArgumentException.class, () -> pattern.skip(Integer.MIN_VALUE, 1));
  }

  @Test
  public void skipFromBeginning_noMatch() {
    Substring.Pattern pattern = first("foo");
    assertThat(pattern.skip(1, 0).from("fur")).isEmpty();
  }

  @Test
  public void skipFromBeginning_smallerThanSize() {
    assertThat(first("foo").skip(1, 0).from("my food")).hasValue("oo");
    assertThat(first("foo").skip(1, 0).repeatedly().from("my food")).containsExactly("oo");
    assertThat(first("foo").skip(1, 0).repeatedly().from("my ffoof")).containsExactly("oo");
    assertThat(first("fff").skip(1, 0).repeatedly().from("fffff")).containsExactly("ff");
    assertThat(first("fff").skip(2, 0).repeatedly().from("ffffff")).containsExactly("f", "f");
  }

  @Test
  public void skipFromBeginning_equalToSize() {
    assertThat(first("foo").skip(3, 0).from("my food")).hasValue("");
    assertThat(first("foo").skip(3, 0).repeatedly().from("my food")).containsExactly("");
    assertThat(first("foo").skip(3, 0).repeatedly().from("my ffoof")).containsExactly("");
    assertThat(first("fff").skip(3, 0).repeatedly().from("fffff")).containsExactly("");
  }

  @Test
  public void skipFromBeginning_greaterThanSize() {
    assertThat(first("foo").skip(Integer.MAX_VALUE, 0).from("my food")).hasValue("");
    assertThat(first("foo").skip(Integer.MAX_VALUE, 0).repeatedly().from("my food"))
        .containsExactly("");
    assertThat(first("foo").skip(Integer.MAX_VALUE, 0).repeatedly().from("my ffoof"))
        .containsExactly("");
    assertThat(first("fff").skip(Integer.MAX_VALUE, 0).repeatedly().from("ffffff"))
        .containsExactly("", "");
  }

  @Test
  public void skip_backtrackFromNextChar() {
    Substring.Pattern pattern = Substring.first("ttl").skip(2, 0).immediatelyBetween("t", "o");
    assertThat(pattern.from("tttlo")).hasValue("l");
    assertThat(pattern.repeatedly().from("(tttlo)")).containsExactly("l");
  }

  @Test
  public void skip_toString() {
    assertThat(first("foo").skip(2, 3).toString()).isEqualTo("first('foo').skip(2, 3)");
  }

  @Test
  public void matchSkipFromBeginning_zero() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(0, 0).toString()).isEqualTo("foo");
  }

  @Test
  public void matchSkipFromBeginning_smallerThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(1, 0).toString()).isEqualTo("oo");
  }

  @Test
  public void matchSkipFromBeginning_equalToLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(3, 0).toString()).isEmpty();
  }

  @Test
  public void matchSkipFromBeginning_greaterThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();

    Substring.Match shrinked = match.skip(4, 0);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(4);

    shrinked = match.skip(5, 0);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(4);

    shrinked = match.skip(Integer.MAX_VALUE, 0);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(4);
  }

  @Test
  public void matchSkipFromEnd_negative() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThrows(IllegalArgumentException.class, () -> match.skip(1, -1));
    assertThrows(IllegalArgumentException.class, () -> match.skip(1, Integer.MIN_VALUE));
  }

  @Test
  public void matchSkipFromEnd_zero() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(0, 0).toString()).isEqualTo("foo");
  }

  @Test
  public void matchSkipFromEnd_smallerThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(0, 1).toString()).isEqualTo("fo");
  }

  @Test
  public void matchSkipFromEnd_equalToLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(0, 3).toString()).isEmpty();
  }

  @Test
  public void matchSkipFromEnd_greaterThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();

    Substring.Match shrinked = match.skip(0, 4);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(1);

    shrinked = match.skip(0, 5);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(1);

    shrinked = match.skip(0, Integer.MAX_VALUE);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(1);
  }

  @Test
  public void matchSkipFromBothEnds_smallerThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(1, 1).toString()).isEqualTo("o");
  }

  @Test
  public void matchSkipFromBothEnds_equalToLength() {
    Substring.Match match = first("foo").in(" foo bar").get();
    assertThat(match.skip(1, 2).toString()).isEmpty();
    assertThat(match.skip(2, 1).toString()).isEmpty();
  }

  @Test
  public void matchSkipFromBothEnds_greaterThanLength() {
    Substring.Match match = first("foo").in(" foo bar").get();

    Substring.Match shrinked = match.skip(2, 2);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(3);

    shrinked = match.skip(Integer.MAX_VALUE, Integer.MAX_VALUE);
    assertThat(shrinked.toString()).isEmpty();
    assertThat(shrinked.index()).isEqualTo(4);
  }

  @Test
  public void skipFromEnd_negative() {
    Substring.Pattern pattern = first("foo");
    assertThrows(IllegalArgumentException.class, () -> pattern.skip(1, -1));
    assertThrows(IllegalArgumentException.class, () -> pattern.skip(1, Integer.MIN_VALUE));
  }

  @Test
  public void skipFromEnd_noMatch() {
    Substring.Pattern pattern = first("foo");
    assertThat(pattern.skip(0, 1).from("fur")).isEmpty();
  }

  @Test
  public void skipFromEnd_smallerThanSize() {
    assertThat(first("foo").skip(0, 1).from("my food")).hasValue("fo");
    assertThat(first("foo").skip(0, 1).repeatedly().from("my food")).containsExactly("fo");
    assertThat(first("foo").skip(0, 1).repeatedly().from("my ffoof")).containsExactly("fo");
    assertThat(first("fff").skip(0, 1).repeatedly().from("fffff")).containsExactly("ff");
    assertThat(first("fff").skip(0, 2).repeatedly().from("ffffff")).containsExactly("f", "f");
  }

  @Test
  public void skipFromEnd_equalToSize() {
    assertThat(first("foo").skip(0, 3).from("my food")).hasValue("");
    assertThat(first("foo").skip(0, 3).repeatedly().from("my food")).containsExactly("");
    assertThat(first("foo").skip(0, 3).repeatedly().from("my ffoof")).containsExactly("");
    assertThat(first("fff").skip(0, 3).repeatedly().from("fffff")).containsExactly("");
  }

  @Test
  public void skipFromEnd_greaterThanSize() {
    assertThat(first("foo").skip(0, Integer.MAX_VALUE).from("my food")).hasValue("");
    assertThat(first("foo").skip(0, Integer.MAX_VALUE).repeatedly().from("my food"))
        .containsExactly("");
    assertThat(first("foo").skip(0, Integer.MAX_VALUE).repeatedly().from("my ffoof"))
        .containsExactly("");
    assertThat(first("fff").skip(0, Integer.MAX_VALUE).repeatedly().from("ffffff"))
        .containsExactly("", "");
  }

  @Test
  public void skipFromEnd_toString() {
    assertThat(first("foo").skip(0, 2).toString()).isEqualTo("first('foo').skip(0, 2)");
  }

  @Test public void or_toString() {
    assertThat(first("foo").or(last("bar")).toString()).isEqualTo("first('foo').or(last('bar'))");
  }

  @Test public void or_firstMatcherMatches() {
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
    assertThat(first('b').or(first("foo")).repeatedly().from("barfoo"))
        .containsExactly("b", "foo");
  }

  @Test public void or_secondMatcherMatches() {
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
    assertThat(prefix("bar").or(first("foo")).repeatedly().from("foobar"))
        .containsExactly("foo", "bar");
  }

  @Test public void or_neitherMatches() {
    assertThat(first("bar").or(first("foo")).in("baz")).isEmpty();
    assertThat(prefix("bar").or(first("foo")).repeatedly().match("baz")).isEmpty();
  }

  @Test public void uptoIncluding_toString() {
    assertThat(upToIncluding(last("bar")).toString()).isEqualTo("upToIncluding(last('bar'))");
  }

  @Test public void upToIncluding_noMatch() {
    assertThat(Substring.upToIncluding(first("://")).in("abc")).isEmpty();
    assertThat(Substring.upToIncluding(first("://")).repeatedly().match("abc")).isEmpty();
  }

  @Test public void upToIncluding_matchAtPrefix() {
    assertThat(Substring.upToIncluding(first("://")).removeFrom("://foo")).isEqualTo("foo");
    assertThat(Substring.upToIncluding(first("://")).repeatedly().from("://foo"))
        .containsExactly("://");
  }

  @Test public void upToIncluding_matchInTheMiddle() {
    assertThat(Substring.upToIncluding(first("://")).removeFrom("http://foo")).isEqualTo("foo");
    assertThat(Substring.upToIncluding(first("://")).repeatedly().from("http://foo"))
        .containsExactly("http://");
  }

  @Test public void upToIncluding_delimitedByRegexGroup() {
    assertThat(
            Substring.upToIncluding(first(Pattern.compile("(/.)/"))).repeatedly().match("foo/1/bar/2/")
                .map(Object::toString))
        .containsExactly("foo/1/", "bar/2/");
  }

  @Test public void toEnd_toString() {
    assertThat(first("//").toEnd().toString()).isEqualTo("first('//').toEnd()");
  }

  @Test public void toEnd_noMatch() {
    assertThat(first("//").toEnd().in("abc")).isEmpty();
    assertThat(first("//").toEnd().repeatedly().match("abc")).isEmpty();
  }

  @Test public void toEnd_matchAtSuffix() {
    assertThat(first("//").toEnd().removeFrom("foo//")).isEqualTo("foo");
    assertThat(first("//").toEnd().repeatedly().from("foo//")).containsExactly("//");
  }

  @Test public void toEnd_matchInTheMiddle() {
    assertThat(first("//").toEnd().removeFrom("foo // bar")).isEqualTo("foo ");
    assertThat(first("//").toEnd().repeatedly().from("foo // bar //"))
        .containsExactly("// bar //");
  }

  @Test public void before_toString() {
    assertThat(Substring.before(first("://")).toString()).isEqualTo("before(first('://'))");
  }

  @Test public void before_noMatch() {
    assertThat(Substring.before(first("://")).in("abc")).isEmpty();
    assertThat(Substring.before(first("://")).repeatedly().match("abc")).isEmpty();
  }

  @Test public void before_matchAtPrefix() {
    assertThat(Substring.before(first("//")).removeFrom("//foo")).isEqualTo("//foo");
    assertThat(Substring.before(first("//")).repeatedly().from("//foo"))
        .containsExactly("");
    assertThat(Substring.before(first("//")).repeatedly().from("//foo//"))
        .containsExactly("", "foo");
  }

  @Test public void before_matchInTheMiddle() {
    assertThat(Substring.before(first("//")).removeFrom("http://foo")).isEqualTo("//foo");
    assertThat(Substring.before(first("//")).repeatedly().from("http://foo"))
        .containsExactly("http:");
  }

  @Test public void before_repeatedly_split() {
    assertThat(Substring.before(first("/")).repeatedly().from("a/b/cd"))
        .containsExactly("a", "b").inOrder();
    assertThat(Substring.before(first("/")).repeatedly().split("a/b/cd").map(Substring.Match::toString))
        .containsExactly("", "/", "/cd");
  }

  @Test
  public void before_limit() {
    assertThat(Substring.before(first("//")).limit(4).removeFrom("http://foo")).isEqualTo("://foo");
    assertThat(Substring.before(first("/")).limit(4).repeatedly().from("http://foo/barbara/"))
        .containsExactly("http", "", "foo", "barb");
  }

  @Test public void repeatedly_split_noMatch() {
    assertThat(first("://").repeatedly().split("abc").map(Match::toString))
        .containsExactly("abc");
  }

  @Test public void repeatedly_split_match() {
    assertThat(first("//").repeatedly().split("//foo").map(Match::toString))
        .containsExactly("", "foo");
    assertThat(first("/").repeatedly().split("foo/bar").map(Match::toString))
        .containsExactly("foo", "bar");
    assertThat(first("/").repeatedly().split("foo/bar/").map(Match::toString))
        .containsExactly("foo", "bar", "");
  }

  @Test public void repeatedly_split_byBetweenPattern() {
    Substring.Pattern comment = Substring.between("/*", INCLUSIVE, "*/", INCLUSIVE);
    assertThat(comment.repeatedly().split("a").map(Match::toString))
        .containsExactly("a")
        .inOrder();
    assertThat(comment.repeatedly().split("a/*comment*/").map(Match::toString))
        .containsExactly("a", "")
        .inOrder();
    assertThat(comment.repeatedly().split("a/*comment*/b").map(Match::toString))
        .containsExactly("a", "b")
        .inOrder();
    assertThat(comment.repeatedly().split("a/*c1*/b/*c2*/").map(Match::toString))
        .containsExactly("a", "b", "")
        .inOrder();
    assertThat(comment.repeatedly().split("a/*c1*/b/*c2*/c").map(Match::toString))
        .containsExactly("a", "b", "c")
        .inOrder();
  }

  @Test public void after_toString() {
    assertThat(Substring.after(first("//")).toString()).isEqualTo("after(first('//'))");
  }

  @Test public void after_noMatch() {
    assertThat(Substring.after(first("//")).in("abc")).isEmpty();
    assertThat(Substring.after(first("//")).repeatedly().match("abc")).isEmpty();
  }

  @Test public void after_matchAtSuffix() {
    assertThat(Substring.after(last('.')).removeFrom("foo.")).isEqualTo("foo.");
    assertThat(Substring.after(last('.')).repeatedly().from("foo."))
        .containsExactly("");
  }

  @Test public void after_matchInTheMiddle() {
    assertThat(Substring.after(last('.')).removeFrom("foo. bar")).isEqualTo("foo.");
    assertThat(Substring.after(last('.')).repeatedly().from("foo. bar"))
        .containsExactly(" bar");
  }

  @Test public void between_toString() {
    assertThat(Substring.between(last("<"), last(">")).toString())
        .isEqualTo("between(last('<'), last('>'))");
  }

  @Test public void betweenInclusive_toString() {
    assertThat(Substring.between(last("<"), INCLUSIVE, last(">"), INCLUSIVE).toString())
        .isEqualTo("between(last('<'), INCLUSIVE, last('>'), INCLUSIVE)");
  }

  @Test public void between_matchedInTheMiddle() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("foo<bar>baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo("foo<");
    assertThat(match.after()).isEqualTo(">baz");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(last('<'), last('>')).repeatedly().from("foo<bar>baz"))
        .containsExactly("bar");
    assertThat(
            Substring.between('/', '/').repeatedly().from("/foo/bar/"))
        .containsExactly("foo", "bar");
  }

  @Test public void between_emptyMatch() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("foo<>baz").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("foo<");
    assertThat(match.after()).isEqualTo(">baz");
    assertThat(match.length()).isEqualTo(0);
    assertThat(Substring.between(last('<'), last('>')).repeatedly().from("foo<>baz"))
        .containsExactly("");
  }

  @Test public void between_consecutiveFirstChar() {
    Substring.Pattern delimiter = first('-');
    Substring.Match match = Substring.between(delimiter, delimiter).in("foo-bar-baz").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo("foo-");
    assertThat(match.after()).isEqualTo("-baz");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(delimiter, delimiter).repeatedly().match("-foo-bar-baz-")
                .map(Object::toString))
        .containsExactly("foo", "bar", "baz")
        .inOrder();
    assertThat(
            Substring.between(delimiter, delimiter).repeatedly().match("-foo-bar-baz-", 1)
                .map(Object::toString))
        .containsExactly("bar", "baz")
        .inOrder();;
  }

  @Test public void between_matchedFully() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("<foo>").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEqualTo("<");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(Substring.between(last('<'), last('>')).repeatedly().from("<foo>"))
        .containsExactly("foo");
  }

  @Test public void between_outerMatchesEmpty() {
    Substring.Match match = Substring.between(first(""), last('.')).in("foo.").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEmpty();
    assertThat(match.after()).isEqualTo(".");
    assertThat(match.length()).isEqualTo(3);
    assertThat(Substring.between(first(""), last('.')).repeatedly().from("foo."))
        .contains("foo");
  }

  @Test public void between_innerMatchesEmpty() {
    Substring.Match match = Substring.between(first(":"), last("")).in("hello:world").get();
    assertThat(match.toString()).isEqualTo("world");
    assertThat(match.before()).isEqualTo("hello:");
    assertThat(match.after()).isEmpty();
    assertThat(match.length()).isEqualTo(5);
    assertThat(Substring.between(first(":"), last("")).repeatedly().from("foo:bar"))
        .containsExactly("bar");
  }

  @Test public void between_matchedLastOccurrence() {
    Substring.Match match = Substring.between(last('<'), last('>')).in("<foo><bar> <baz>").get();
    assertThat(match.toString()).isEqualTo("baz");
    assertThat(match.before()).isEqualTo("<foo><bar> <");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(last('<'), last('>')).repeatedly().match("<foo><bar> <baz>")
                .map(Object::toString))
        .containsExactly("baz");
    assertThat(
            Substring.between(last('<'), last('>')).repeatedly().match("<foo><bar> <baz>", 11)
                .map(Object::toString))
        .containsExactly("baz");
    assertThat(
            Substring.between(last('<'), last('>')).repeatedly().match("<foo><bar> <baz>", 12)
                .map(Object::toString))
        .isEmpty();
  }

  @Test public void between_matchedIncludingDelimiters() {
    Substring.Match match =
        Substring.between(before(last('<')), after(last('>'))).in("begin<foo>end").get();
    assertThat(match.toString()).isEqualTo("<foo>");
    assertThat(match.before()).isEqualTo("begin");
    assertThat(match.after()).isEqualTo("end");
    assertThat(match.length()).isEqualTo(5);
    assertThat(
            Substring.between(before(last('<')), after(last('>'))).repeatedly().match("begin<foo>end")
                .map(Object::toString))
        .containsExactly("<foo>");
  }

  @Test public void betweenInclusive_matchedIncludingDelimiters() {
    Substring.Match match =
        Substring.between(last('<'), INCLUSIVE, last('>'), INCLUSIVE).in("begin<foo>end").get();
    assertThat(match.toString()).isEqualTo("<foo>");
    assertThat(match.before()).isEqualTo("begin");
    assertThat(match.after()).isEqualTo("end");
    assertThat(match.length()).isEqualTo(5);
    assertThat(
            Substring.between(last('<'), INCLUSIVE, last('>'), INCLUSIVE).repeatedly().match("begin<foo>end")
                .map(Object::toString))
        .containsExactly("<foo>");
    assertThat(
            Substring.between(last('<'), INCLUSIVE, last('>'), INCLUSIVE).repeatedly().match("begin<foo>end", 5)
                .map(Object::toString))
        .containsExactly("<foo>");
    assertThat(
            Substring.between(last('<'), INCLUSIVE, last('>'), INCLUSIVE).repeatedly().match("begin<foo>end", 6)
                .map(Object::toString))
        .isEmpty();
  }

  @Test public void between_nothingBetweenSameChar() {
    assertThat(Substring.between('.', '.').in(".")).isEmpty();
    assertThat(Substring.between('.', '.').repeatedly().match(".")).isEmpty();
  }

  @Test public void between_matchesOpenButNotClose() {
    assertThat(Substring.between('<', '>').in("<foo")).isEmpty();
    assertThat(Substring.between('<', '>').repeatedly().match("<foo")).isEmpty();
  }

  @Test public void between_matchesCloseButNotOpen() {
    assertThat(Substring.between('<', '>').in("foo>")).isEmpty();
    assertThat(Substring.between('<', '>').repeatedly().match("foo>")).isEmpty();
  }

  @Test public void between_closeIsBeforeOpen() {
    assertThat(Substring.between('<', '>').in(">foo<")).isEmpty();
    assertThat(Substring.between('<', '>').repeatedly().match(">foo<")).isEmpty();
  }

  @Test public void between_closeBeforeOpenDoesNotCount() {
    Substring.Match match = Substring.between('<', '>').in("><foo>").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEqualTo("><");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(Substring.between('<', '>').repeatedly().from("><foo>"))
        .containsExactly("foo");
  }

  @Test public void between_matchesNone() {
    assertThat(Substring.between('<', '>').in("foo")).isEmpty();
    assertThat(Substring.between('<', '>').repeatedly().match("foo")).isEmpty();
  }

  @Test public void between_closeUsesBefore() {
    Substring.Pattern open = first("-");
    Substring.Pattern close = Substring.before(first("-"));
    Substring.Match match = Substring.between(open, close).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), before(first("-"))).repeatedly().match("-foo-")
                .map(Object::toString))
        .containsExactly("");
  }

  @Test public void between_closeUsesUpToIncluding() {
    Substring.Match match =
        Substring.between(first("-"), Substring.upToIncluding(first("-"))).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), upToIncluding(first("-"))).repeatedly().match("-foo-")
                .map(Object::toString))
        .containsExactly("");
  }

  @Test public void between_closeUsesRegex() {
    Substring.Match match =
        Substring.between(first("-"), first(Pattern.compile(".*-"))).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), first(Pattern.compile(".*-")))
                .repeatedly().from("-foo-"))
        .containsExactly("");
  }

  @Test public void between_closeOverlapsWithOpen() {
    assertThat(Substring.between(first("abc"), last("cde")).in("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), last("cde")).repeatedly().match("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), last("cde")).repeatedly().match("abcde", 1)).isEmpty();
    assertThat(Substring.between(first("abc"), last('c')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), last('c')).repeatedly().match("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix("cde")).in("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix("cde")).repeatedly().match("abcde")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix("cde")).repeatedly().match("abcde", 2)).isEmpty();
    assertThat(Substring.between(first("abc"), suffix('c')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), suffix('c')).repeatedly().match("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix("a")).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix("a")).repeatedly().match("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix('a')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix('a')).repeatedly().match("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), prefix('a')).repeatedly().match("abc", 3)).isEmpty();
    assertThat(Substring.between("abc", "a").in("abc")).isEmpty();
    assertThat(Substring.between("abc", "a").repeatedly().match("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first('a')).in("abc")).isEmpty();
    assertThat(Substring.between(first("abc"), first('a')).repeatedly().match("abc")).isEmpty();
  }

  @Test public void between_betweenInsideBetween() {
    Substring.Match match =
        Substring.between(first("-"), Substring.between(first(""), first('-'))).in("-foo-").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("-");
    assertThat(match.after()).isEqualTo("foo-");
    assertThat(match.length()).isEqualTo(0);
    assertThat(
            Substring.between(first("-"), Substring.between(first(""), first('-'))).repeatedly().match("-foo-")
                .map(Object::toString))
        .containsExactly("");
  }

  @Test public void between_emptyOpen() {
    Substring.Match match = Substring.between(first(""), first(", ")).in("foo, bar").get();
    assertThat(match.toString()).isEqualTo("foo");
    assertThat(match.before()).isEmpty();
    assertThat(match.after()).isEqualTo(", bar");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between(first(""), first(", ")).repeatedly().from("foo, bar"))
        .contains("foo");
  }

  @Test public void between_emptyClose() {
    Substring.Match match = Substring.between(first(":"), first("")).in("foo:bar").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEqualTo("foo:");
    assertThat(match.after()).isEqualTo("bar");
    assertThat(match.length()).isEqualTo(0);
    assertThat(Substring.between(first(":"), first("")).repeatedly().from("foo:bar"))
        .containsExactly("");
  }

  @Test public void between_emptyOpenAndClose() {
    Substring.Match match = Substring.between(first(""), first("")).in("foo").get();
    assertThat(match.toString()).isEmpty();
    assertThat(match.before()).isEmpty();
    assertThat(match.after()).isEqualTo("foo");
    assertThat(match.length()).isEqualTo(0);
    assertThat(Substring.between(first(""), first("")).repeatedly().from("foo"))
        .containsExactly("", "", "", "");
  }

  @Test public void between_openAndCloseAreEqual() {
    Substring.Match match = Substring.between("-", "-").in("foo-bar-baz-duh").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo("foo-");
    assertThat(match.after()).isEqualTo("-baz-duh");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between("-", "-").repeatedly().match("foo-bar-baz-duh")
                .map(Object::toString))
        .containsExactly("bar", "baz");
    assertThat(
            Substring.between("-", "-").repeatedly().match("foo-bar-baz-duh", 1)
                .map(Object::toString))
        .containsExactly("bar", "baz");
    assertThat(
            Substring.between("-", "-").repeatedly().match("foo-bar-baz-duh", 3)
                .map(Object::toString))
        .containsExactly("bar", "baz");
    assertThat(
            Substring.between("-", "-").repeatedly().match("foo-bar-baz-duh", 4)
                .map(Object::toString))
        .containsExactly("baz");
  }

  @Test public void between_closeBeforeOpenIgnored() {
    Substring.Match match = Substring.between("<", ">").in(">foo<bar>").get();
    assertThat(match.toString()).isEqualTo("bar");
    assertThat(match.before()).isEqualTo(">foo<");
    assertThat(match.after()).isEqualTo(">");
    assertThat(match.length()).isEqualTo(3);
    assertThat(
            Substring.between("<", ">").repeatedly().match(">foo<bar>h<baz>")
                .map(Object::toString))
        .containsExactly("bar", "baz");
  }

  @Test
  public void between_withLimit() {
    assertThat(
            Substring.between(first("//").limit(1), first("//").limit(1))
                .repeatedly()
                .from("//abc//d////"))
        .containsExactly("/abc", "/d", "")
        .inOrder();
  }

  @Test public void then_toString() {
    assertThat(first("(").then(first("<")).toString())
        .isEqualTo("first('(').then(first('<'))");
  }

  @Test public void then_match() {
    assertThat(first("GET").then(prefix(" ")).split("GET http").map(Joiner.on(':')::join))
        .hasValue("GET:http");
    assertThat(
            before(first('/')).then(prefix("")).repeatedly().match("foo/bar/").map(Match::before))
        .containsExactly("foo", "foo/bar");
    assertThat(
            before(first('/')).then(prefix("")).repeatedly().match("foo/bar/", 1).map(Match::before))
        .containsExactly("foo", "foo/bar");
  }

  @Test public void then_firstPatternDoesNotMatch() {
    assertThat(first("GET").then(prefix(" ")).split("GE http").map(Joiner.on(':')::join))
        .isEmpty();
  }

  @Test public void then_secondPatternDoesNotMatch() {
    assertThat(first("GET").then(prefix(" ")).split("GET: http").map(Joiner.on('-')::join))
        .isEmpty();
  }

  @Test public void peek_toString() {
    assertThat(first("(").peek(first("<")).toString())
        .isEqualTo("first('(').peek(first('<'))");
  }

  @Test
  public void peek_empty() {
    assertThat(prefix("http").peek(prefix("")).from("http")).hasValue("http");
    assertThat(prefix("http").peek(prefix("")).from("https")).hasValue("http");
  }

  @Test
  public void peek_match() {
    assertThat(prefix("http").peek(prefix(":")).from("http://")).hasValue("http");
    assertThat(prefix("http").peek(prefix(":")).repeatedly().from("http://")).containsExactly("http");
    assertThat(before(first('/')).peek(prefix("/")).repeatedly().from("foo/bar/"))
        .containsExactly("foo", "bar").inOrder();
  }

  @Test public void peek_firstPatternDoesNotMatch() {
    assertThat(prefix("http").peek(prefix(":")).from("ftp://")).isEmpty();
    assertThat(prefix("http").peek(prefix(":")).repeatedly().from("ftp://")).isEmpty();
  }

  @Test public void peek_peekedPatternDoesNotMatch() {
    assertThat(prefix("http").peek(prefix(":")).from("https://")).isEmpty();
    assertThat(prefix("http").peek(prefix(":")).repeatedly().from("https://")).isEmpty();
    assertThat(BEGINNING.peek(prefix(":")).repeatedly().split("foo").map(Match::toString))
        .containsExactly("foo");
  }

  @Test public void patternFrom_noMatch() {
    assertThat(prefix("foo").from("")).isEmpty();
  }

  @Test public void patternFrom_match() {
    assertThat(Substring.first("bar").from("foo bar")).hasValue("bar");
  }

  @Test public void matcher_index() {
    assertThat(Substring.first("foo").in("foobar").get().index()).isEqualTo(0);
    assertThat(Substring.first("bar").in("foobar").get().index()).isEqualTo(3);
    assertThat(END.in("foobar").get().index()).isEqualTo(6);
  }

  @Test public void matcher_fullString() {
    assertThat(Substring.first("bar").in("foobar").get().fullString()).isEqualTo("foobar");
  }

  @Test public void iterateIn_example() {
    String text = "{x:1}, {y:2}, {z:3}";
    ImmutableListMultimap<String, String> dictionary =
        Substring.between('{', '}').repeatedly().match(text)
            .map(Object::toString)
            .map(Substring.first(':')::in)
            .map(Optional::get)
            .collect(toImmutableListMultimap(Substring.Match::before, Substring.Match::after));
    assertThat(dictionary).containsExactly("x", "1", "y", "2", "z", "3").inOrder();
  }

  @Test public void iterateIn_getLinesPreservingNewLineChar() {
    String text = "line1\nline2\nline3";
    assertThat(Substring.upToIncluding(first('\n').or(END)).repeatedly().from(text))
        .containsExactly("line1\n", "line2\n", "line3")
        .inOrder();
  }

  @Test public void iterateIn_characteristics() {
    Spliterator<?> spliterator = BEGINNING.repeatedly().match("test").spliterator();
    assertThat(spliterator.characteristics() & Spliterator.NONNULL).isEqualTo(Spliterator.NONNULL);
  }

  @Test public void spanningInOrder_toString() {
    assertThat(spanningInOrder("o", "bar").toString())
        .isEqualTo("first('o').extendTo(first('bar'))");
  }

  @Test public void spanningInOrder_twoStops_matches() {
    Substring.Match match = spanningInOrder("o", "bar").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(1);
    assertThat(match.length()).isEqualTo(6);
  }

  @Test public void spanningInOrder_twoStops_firstPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar").in("far bar car")).isEmpty();
  }

  @Test public void spanningInOrder_twoStops_secondPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar").in("foo car")).isEmpty();
  }

  @Test public void spanningInOrder_twoStops_firstStopIsEmpty() {
    Substring.Match match = spanningInOrder("", "foo").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(0);
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void spanningInOrder_twoStops_secondStopIsEmpty() {
    Substring.Match match = spanningInOrder("foo", "").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(0);
    assertThat(match.length()).isEqualTo(3);
  }

  @Test public void spanningInOrder_twoStops_bothStopsAreEmpty() {
    Substring.Match match = spanningInOrder("", "").in("foo bar car").get();
    assertThat(match.index()).isEqualTo(0);
    assertThat(match.length()).isEqualTo(0);
  }

  @Test public void spanningInOrder_threeStops_matches() {
    Substring.Match match = spanningInOrder("o", "bar", "bar").in("foo barcarbar").get();
    assertThat(match.toString()).isEqualTo("oo barcarbar");
    assertThat(match.index()).isEqualTo(1);
    assertThat(match.length()).isEqualTo(12);
  }

  @Test public void spanningInOrder_threeStops_firstPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar", "car").in("far bar car")).isEmpty();
  }

  @Test public void spanningInOrder_threeStops_secondPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar", "car").in("foo boo car")).isEmpty();
  }

  @Test public void spanningInOrder_threeStops_thirdPatternDoesNotMatch() {
    assertThat(spanningInOrder("o", "bar", "car").in("foo bar cat")).isEmpty();
  }

  @Test
  public void firstOccurrence_noPattern() {
    Substring.Pattern pattern = Stream.<Substring.Pattern>empty().collect(firstOccurrence());
    assertThat(pattern.from("string")).isEmpty();
    assertThat(pattern.repeatedly().from("string")).isEmpty();
  }

  @Test
  public void firstOccurrence_emptyPattern() {
    Substring.Pattern pattern = Stream.of(first("")).collect(firstOccurrence());
    assertThat(pattern.from("")).hasValue("");
    assertThat(pattern.repeatedly().from("").limit(100)).containsExactly("");
    assertThat(pattern.repeatedly().from("ab").limit(100)).containsExactly("", "", "");
  }

  @Test
  public void firstOccurrence_singlePattern_noMatch() {
    Substring.Pattern pattern = Stream.of(first("foo")).collect(firstOccurrence());
    assertThat(pattern.from("string")).isEmpty();
    assertThat(pattern.repeatedly().from("string")).isEmpty();
  }

  @Test
  public void firstOccurrence_singlePattern_match() {
    Substring.Pattern pattern = Stream.of(Substring.word()).collect(firstOccurrence());
    assertThat(pattern.from("foo bar")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void firstOccurrence_twoPatterns_noneMatch() {
    Substring.Pattern pattern = Stream.of(first("foo"), first("bar")).collect(firstOccurrence());
    assertThat(pattern.from("what")).isEmpty();
    assertThat(pattern.repeatedly().from("what")).isEmpty();
  }

  @Test
  public void firstOccurrence_twoPatterns_firstMatches() {
    Substring.Pattern pattern = Stream.of(first("foo"), first("bar")).collect(firstOccurrence());
    assertThat(pattern.from("foo bar")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo bar")).containsExactly("foo", "bar").inOrder();
  }

  @Test
  public void firstOccurrence_twoPatterns_secondMatches() {
    Substring.Pattern pattern = Stream.of(first("foo"), first("bar")).collect(firstOccurrence());
    assertThat(pattern.from("bar foo")).hasValue("bar");
    assertThat(pattern.repeatedly().from("bar foo")).containsExactly("bar", "foo").inOrder();
  }

  @Test
  public void firstOccurrence_threePatterns_noneMatch() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertThat(pattern.from("what")).isEmpty();
    assertThat(pattern.repeatedly().from("what")).isEmpty();
  }

  @Test
  public void firstOccurrence_threePatterns_firstMatches() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertThat(pattern.from("foo zoo bar")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo zoo bar"))
        .containsExactly("foo", "zoo", "bar")
        .inOrder();
  }

  @Test
  public void firstOccurrence_threePatterns_secondMatches() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertThat(pattern.from("bar zoo foo")).hasValue("bar");
    assertThat(pattern.repeatedly().from("bar zoo foo"))
        .containsExactly("bar", "zoo", "foo")
        .inOrder();
  }

  @Test
  public void firstOccurrence_threePatterns_thirdMatches() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertThat(pattern.from("zoo bar foo")).hasValue("zoo");
    assertThat(pattern.repeatedly().from("zoo bar foo"))
        .containsExactly("zoo", "bar", "foo")
        .inOrder();
  }

  @Test public void firstOccurrence_splitKeyValues_withFixedSetOfKeys_noReservedDelimiter() {
    String input = "playlist id:foo bar artist: another name a: my name:age";
    Substring.Pattern delim =
        Stream.of("a", "artist", "playlist id", "foo bar")
            .map(k -> first(" " + k + ":"))
            .collect(firstOccurrence())
            .limit(1);
    ImmutableMap<String, String> keyValues =
        delim.repeatedly()
            .splitThenTrim(input)
            .collect(MoreCollectors.mapping(
                seg -> first(':')
                    .splitThenTrim(seg)
                    .orElseThrow(() -> new IllegalArgumentException("Bad seg: '" + seg + "'")),
                ImmutableMap::toImmutableMap));
    assertThat(keyValues)
        .containsExactly("playlist id", "foo bar", "artist", "another name", "a", "my name:age")
        .inOrder();
  }

  @Test
  public void firstOccurrence_overlappingCandidatePatterns() {
    Substring.Pattern pattern =
        Stream.of("oop", "foo", "op", "pool", "load", "oad")
            .map(Substring::first)
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("foopooload"))
        .containsExactly("foo", "pool", "oad")
        .inOrder();
    assertThat(pattern.repeatedly().from(repeat("foopooload", 10)).distinct())
        .containsExactly("foo", "pool", "oad")
        .inOrder();
  }

  @Test
  public void firstOccurrence_beforePatternRepetitionIndexRespected() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), before(first("/")), first('/'), first("zoo"))
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("food/bar/baz/zoo"))
        .containsExactly("foo", "d", "/", "bar", "/", "baz", "/", "zoo")
        .inOrder();
  }

  @Test
  public void firstOccurrence_beforePatternRepetition() {
    Substring.Pattern pattern =
        Stream.of(before(first("//")))
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("foo//bar//baz//zoo"))
        .containsExactly("foo", "bar", "baz")
        .inOrder();
  }

  @Test
  public void firstOccurrence_limitPatternInterleavedWithLimitPattern() {
    Substring.Pattern pattern =
        Stream.of(first("koook").limit(3), first("ok").limit(1))
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("okoookoook"))
        .containsExactly("o", "koo", "o", "o")
        .inOrder();
    assertThat(pattern.repeatedly().from("koookoooko"))
        .containsExactly("koo", "o", "o")
        .inOrder();
  }

  @Test
  public void firstOccurrence_zeroLimitPatternInterleavedWithZeroLimitPattern() {
    Substring.Pattern pattern =
        Stream.of(first("kook").limit(0), first("ok").limit(0))
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("okookook"))
        .containsExactly("", "", "", "")
        .inOrder();
    assertThat(pattern.repeatedly().from("kookooko"))
        .containsExactly("", "", "")
        .inOrder();
  }

  @Test
  public void firstOccurrenceThenLimit_interleaved() {
    Substring.Pattern pattern =
        Stream.of(first("koook"), first("ok"))
            .collect(firstOccurrence())
            .limit(3);
    assertThat(pattern.repeatedly().from("koookoook"))
        .containsExactly("koo", "ok")
        .inOrder();
  }

  @Test
  public void firstOccurrence_beforePatternInterleavedWithBeforePattern() {
    Substring.Pattern pattern =
        Stream.of(before(first("/")), before(first("//")))
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("foo//bar//baz//zoo"))
        .containsExactly("foo", "", "", "bar", "", "", "baz", "", "")
        .inOrder();
  }

  @Test
  public void firstOccurrence_beforePatternInterleavedWithFirst() {
    Substring.Pattern pattern =
        Stream.of(before(first("//")), first("//"))
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("foo//bar//baz//zoo"))
        .containsExactly("foo", "//", "bar", "//", "baz", "//")
        .inOrder();
  }

  @Test
  public void firstOccurrence_breaksTieByCandidatePatternOrder() {
    Substring.Pattern pattern =
        Stream.of("foo", "food", "dog", "f", "fo", "d", "do")
            .map(Substring::first)
            .collect(firstOccurrence());
    assertThat(pattern.repeatedly().from("foodog")).containsExactly("foo", "dog").inOrder();
    assertThat(pattern.repeatedly().from(repeat("foodog", 10)).distinct())
        .containsExactly("foo", "dog");
  }

  @Test
  public void firstOccurrence_word() {
    Substring.Pattern pattern =
        Stream.of("food", "dog", "f", "fo", "d", "do")
        .map(Substring::word)
        .collect(firstOccurrence());
    assertThat(pattern.from("foodog")).isEmpty();
    assertThat(pattern.repeatedly().from("foodog")).isEmpty();
    assertThat(pattern.repeatedly().from("dog foo dog food catfood"))
        .containsExactly("dog", "dog", "food")
        .inOrder();
  }

  @Test
  public void firstOccurrence_separatedBy() {
    Substring.Pattern pattern =
        Stream.of("food", "dog", "f", "fo", "d", "do")
        .map(Substring::first)
        .collect(firstOccurrence())
        .separatedBy(Character::isWhitespace);
    assertThat(pattern.from("foodog")).isEmpty();
    assertThat(pattern.repeatedly().from("foodog")).isEmpty();
    assertThat(pattern.repeatedly().from("dog foo dog food catfood"))
        .containsExactly("dog", "dog", "food")
        .inOrder();
  }

  @Test
  public void firstOccurrence_separatedBy_tieBrokenByBoundary() {
    Substring.Pattern pattern =
        Stream.of("foo", "food")
            .map(Substring::first)
            .collect(firstOccurrence())
            .separatedBy(Character::isWhitespace);
    assertThat(pattern.from("food")).hasValue("food");
    assertThat(pattern.repeatedly().from("food")).containsExactly("food");
  }

  @Test
  public void firstOccurrence_between_tieBrokenByBoundary() {
    Substring.Pattern pattern =
        Stream.of("foo", "food")
            .map(Substring::first)
            .collect(firstOccurrence())
            .immediatelyBetween("(", ")");
    assertThat(pattern.from("(food)")).hasValue("food");
    assertThat(pattern.repeatedly().from("(food)")).containsExactly("food");
  }

  @Test
  public void firstOccurrence_notBetween_tieBrokenByBoundary() {
    Substring.Pattern pattern =
        Stream.of("food", "foo")
            .map(Substring::first)
            .collect(firstOccurrence())
            .notImmediatelyBetween("(", ")");
    assertThat(pattern.from("(food)")).hasValue("foo");
    assertThat(pattern.repeatedly().from("(food)")).containsExactly("foo");
  }

  @Test
  public void firstOccurrence_peek_alternativeBackTrackingNotTriggeredByPeek() {
    Substring.Pattern pattern =
        Stream.of("foo", "ood").map(Substring::first).collect(firstOccurrence()).peek(prefix(" "));
    assertThat(pattern.from("food ")).isEmpty();
    assertThat(pattern.repeatedly().from("food ")).isEmpty();
  }

  @Test
  public void peekThenFirstOccurrence_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern =
        Stream.of("foo", "ood").map(Substring::first).map(p -> p.peek(prefix(" "))).collect(firstOccurrence());
    assertThat(pattern.from("food ")).hasValue("ood");
    assertThat(pattern.repeatedly().from("food ")).containsExactly("ood");
  }

  @Test
  public void or_separatedBy_alternativeBackTrackingTriggeredByBoundaryMismatch() {
    Substring.Pattern pattern = first("foo").or(first("food")).separatedBy(Character::isWhitespace);
    assertThat(pattern.from("food")).hasValue("food");
    assertThat(pattern.repeatedly().from("food")).containsExactly("food");
  }

  @Test
  public void or_between_alternativeBackTrackingTriggeredByBoundaryMismatch() {
    Substring.Pattern pattern = first("foo").or(first("food")).immediatelyBetween("(", ")");
    assertThat(pattern.from("(food)")).hasValue("food");
    assertThat(pattern.repeatedly().from("(food)")).containsExactly("food");
  }

  @Test
  public void or_notBetween_alternativeBackTrackingTriggeredByBoundaryMismatch() {
    Substring.Pattern pattern = first("foo").or(first("(foo)")).notImmediatelyBetween("(", ")");
    assertThat(pattern.from("(foo)")).hasValue("(foo)");
    assertThat(pattern.repeatedly().from("(foo)")).containsExactly("(foo)");
  }

  @Test
  public void or_peek_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern = first("foo").or(first("food")).peek(prefix(" "));
    assertThat(pattern.from("food ")).hasValue("food");
    assertThat(pattern.repeatedly().from("food ")).containsExactly("food");
  }

  @Test
  public void or_limitThenPeek_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern = first("foo").or(first("food")).limit(4).peek(prefix(" "));
    assertThat(pattern.from("food ")).hasValue("food");
    assertThat(pattern.repeatedly().from("food ")).containsExactly("food");
  }

  @Test
  public void leading_noMatch() {
    assertThat(leading(ALPHA).from(" foo")).isEmpty();
    assertThat(leading(ALPHA).from("")).isEmpty();
    assertThat(leading(ALPHA).repeatedly().from(" foo")).isEmpty();
  }

  @Test
  public void leading_match() {
    assertThat(leading(ALPHA).from("System.out")).hasValue("System");
    assertThat(leading(ALPHA).removeFrom("System.out")).isEqualTo(".out");
    assertThat(
            leading(CharMatcher.inRange('a', 'z')::matches)
                .then(prefix(':'))
                .in("http://google.com")
                .map(Match::before))
        .hasValue("http");
  }

  @Test
  public void leading_match_repeatedly() {
    assertThat(leading(ALPHA).repeatedly().removeAllFrom("System.out")).isEqualTo(".out");
    assertThat(leading(ALPHA).repeatedly().from("System.out")).containsExactly("System");
    assertThat(leading(ALPHA).repeatedly().from("out")).containsExactly("out");
  }

  @Test
  public void trailing_noMatch() {
    assertThat(trailing(DIGIT).from("123.")).isEmpty();
    assertThat(trailing(DIGIT).from("")).isEmpty();
    assertThat(trailing(DIGIT).repeatedly().from("123.")).isEmpty();
  }

  @Test
  public void trailing_match() {
    assertThat(trailing(DIGIT).from("12>11")).hasValue("11");
    assertThat(trailing(DIGIT).removeFrom("12>11")).isEqualTo("12>");
  }

  @Test
  public void trailing_match_repeatedly() {
    assertThat(trailing(DIGIT).repeatedly().removeAllFrom("12>11")).isEqualTo("12>");
    assertThat(trailing(DIGIT).repeatedly().from("12>11")).containsExactly("11");
    assertThat(trailing(DIGIT).repeatedly().from("11")).containsExactly("11");
  }

  @Test
  public void consecutive_noMatch() {
    assertThat(consecutive(ALPHA).from(" ")).isEmpty();
    assertThat(consecutive(ALPHA).from("")).isEmpty();
    assertThat(consecutive(ALPHA).from(".")).isEmpty();
    assertThat(consecutive(ALPHA).repeatedly().from(".")).isEmpty();
  }

  @Test
  public void consecutive_match() {
    assertThat(consecutive(ALPHA).from(" foo.")).hasValue("foo");
    assertThat(consecutive(ALPHA).removeFrom(" foo.")).isEqualTo(" .");
    assertThat(consecutive(ALPHA).repeatedly().removeAllFrom(" foo.")).isEqualTo(" .");
  }

  @Test
  public void consecutive_match_repeatedly() {
    assertThat(consecutive(ALPHA).repeatedly().from(" foo.")).containsExactly("foo");
    assertThat(consecutive(ALPHA).repeatedly().from("(System.out)"))
        .containsExactly("System", "out")
        .inOrder();
    assertThat(consecutive(ALPHA).repeatedly().replaceAllFrom("(System.out)", Ascii::toLowerCase))
        .isEqualTo("(system.out)");
  }

  @Test
  public void word_notFound() {
    assertThat(Substring.word("cat").from("dog")).isEmpty();
    assertThat(Substring.word("cat").from("")).isEmpty();
  }

  @Test
  public void word_onlyWord() {
    assertThat(Substring.word("word").from("word")).hasValue("word");
    assertThat(Substring.word("word").repeatedly().from("word")).containsExactly("word");
  }

  @Test
  public void word_partialWord() {
    assertThat(Substring.word("cat").from("catchie")).isEmpty();
    assertThat(Substring.word("cat").repeatedly().from("catchie")).isEmpty();
    assertThat(Substring.word("cat").from("bobcat")).isEmpty();
    assertThat(Substring.word("cat").repeatedly().from("bobcat")).isEmpty();
  }

  @Test
  public void word_startedByWord() {
    assertThat(Substring.word("cat").from("cat loves dog")).hasValue("cat");
    assertThat(Substring.word("cat").repeatedly().from("cat loves dog")).containsExactly("cat");
  }

  @Test
  public void word_endedByWord() {
    assertThat(Substring.word("word").from("hello word")).hasValue("word");
    assertThat(Substring.word("word").repeatedly().from("hello word")).containsExactly("word");
  }

  @Test
  public void word_multipleWords() {
    assertThat(Substring.word("cat").from("bobcat is not a cat, or is it a cat?")).hasValue("cat");
    assertThat(Substring.word("cat").in("bobcat is not a cat, or is it a cat?").get().before())
        .isEqualTo("bobcat is not a ");
    assertThat(Substring.word("cat").repeatedly().from("bobcat is not a cat, or is it a cat?"))
        .containsExactly("cat", "cat");
  }

  @Test
  public void word_emptyWord() {
    assertThat(Substring.word("").repeatedly().from("a.b")).isEmpty();
    assertThat(Substring.word("").from("a.b")).isEmpty();
    assertThat(Substring.word("").repeatedly().from("ab..cd,,ef,")).containsExactly("", "", "");
  }

  @Test
  public void word_noMatch() {
    assertThat(Substring.word().from("")).isEmpty();
    assertThat(Substring.word().repeatedly().from("")).isEmpty();
    assertThat(Substring.word().from("./> ")).isEmpty();
    assertThat(Substring.word().repeatedly().from("./> ")).isEmpty();
  }

  @Test
  public void word_matches() {
    assertThat(Substring.word().from("hello world")).hasValue("hello");
    assertThat(Substring.word().repeatedly().from("hello world")).containsExactly("hello", "world");
  }

  @Test
  public void separatedBy_first() {
    CharPredicate left = CharPredicate.range('a', 'z').not();
    CharPredicate right = CharPredicate.range('a', 'z').or('-').not();
    Substring.Pattern petRock = Substring.first("pet-rock").separatedBy(left, right);
    assertThat(petRock.from("pet-rock")).hasValue("pet-rock");
    assertThat(petRock.from("pet-rock is fun")).hasValue("pet-rock");
    assertThat(petRock.from("love-pet-rock")).hasValue("pet-rock");
    assertThat(petRock.from("pet-rock-not")).isEmpty();
    assertThat(petRock.from("muppet-rock")).isEmpty();
  }

  @Test
  public void separatedBy_skipEscape() {
    CharPredicate escape = CharPredicate.is('\\');
    Substring.Pattern unescaped = Substring.first("aaa").separatedBy(escape.not());
    assertThat(unescaped.from("\\aaaa")).hasValue("aaa");
    assertThat(unescaped.repeatedly().from("\\aaaa")).containsExactly("aaa");
    assertThat(unescaped.in("\\aaaa").get().before()).isEqualTo("\\a");
  }

  @Test
  public void separatedBy_before() {
    CharPredicate boundary = CharPredicate.range('a', 'z').not();
    Substring.Pattern dir = Substring.before(first("//")).separatedBy(boundary);
    assertThat(dir.from("foo//bar//zoo")).hasValue("foo");
    assertThat(dir.repeatedly().from("foo//bar//zoo")).containsExactly("foo", "bar").inOrder();
  }

  @Test public void followedBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").followedBy("...");
    assertThat(pattern.from("")).isEmpty();
    assertThat(pattern.from("bar")).isEmpty();
    assertThat(pattern.repeatedly().from("bar")).isEmpty();
  }

  @Test public void followedBy_lookaheadNoFound() {
    Substring.Pattern pattern = first("foo").followedBy("...");
    assertThat(pattern.from("foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo..")).isEmpty();
  }

  @Test public void followedBy_found() {
    Substring.Pattern pattern = first("foo").followedBy("...");
    assertThat(pattern.from("foo...")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo...")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("foo...barfoo...")).containsExactly("foo", "foo");
  }

  @Test public void followedBy_backtracking() {
    Substring.Pattern pattern = Substring.first("--").followedBy("->");
    assertThat(pattern.from("<---->")).hasValue("--");
  }

  @Test public void followedBy_prefixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.prefix("--").followedBy("->");
    assertThat(pattern.from("---->")).isEmpty();
    assertThat(pattern.repeatedly().from("---->")).isEmpty();
  }

  @Test public void followedBy_beforeHasNoBacktracking() {
    Substring.Pattern pattern = Substring.before(first("--")).followedBy("->");
    assertThat(pattern.from("---->")).isEmpty();
    assertThat(pattern.repeatedly().from("---->")).isEmpty();
  }

  @Test public void followedBy_afterHasNoBacktracking() {
    Substring.Pattern pattern = Substring.after(first("--")).followedBy("->");
    assertThat(pattern.from("---->")).isEmpty();
    assertThat(pattern.repeatedly().from("---->")).isEmpty();
  }

  @Test public void followedBy_upToIncludingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.upToIncluding(first("--")).followedBy("->");
    assertThat(pattern.from("---->")).isEmpty();
    assertThat(pattern.repeatedly().from("---->")).isEmpty();
  }

  @Test public void followedBy_leadingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.leading(CharPredicate.is('-')).followedBy("->");
    assertThat(pattern.from("---->")).isEmpty();
    assertThat(pattern.repeatedly().from("---->")).isEmpty();
  }

  @Test public void precededBy_suffixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.suffix("--").precededBy("<-");
    assertThat(pattern.from("<----")).isEmpty();
    assertThat(pattern.repeatedly().from("<----")).isEmpty();
  }

  @Test public void precededBy_trailingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.trailing(CharPredicate.is('-')).precededBy("<-");
    assertThat(pattern.from("<----")).isEmpty();
    assertThat(pattern.repeatedly().from("<----")).isEmpty();
  }

  @Test public void precededBy_toEndHasNoBacktracking() {
    Substring.Pattern pattern = first("--").toEnd().precededBy("<-");
    assertThat(pattern.from("<---->")).isEmpty();
    assertThat(pattern.repeatedly().from("<---->")).isEmpty();
  }

  @Test public void precededBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").precededBy("...");
    assertThat(pattern.from("...bar")).isEmpty();
    assertThat(pattern.repeatedly().from("...bar")).isEmpty();
  }

  @Test public void precededBy_lookbehindNoFound() {
    Substring.Pattern pattern = first("foo").precededBy("...");
    assertThat(pattern.from("..foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo")).isEmpty();
    assertThat(pattern.repeatedly().from("..foo")).isEmpty();
  }

  @Test public void precededBy_found() {
    Substring.Pattern pattern = first("foo").precededBy("...");
    assertThat(pattern.from("...foo")).hasValue("foo");
    assertThat(pattern.repeatedly().from("...foo")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("bar...foo bar...foo")).containsExactly("foo", "foo");
  }

  @Test public void between_empty() {
    Substring.Pattern pattern = first("foo").precededBy("");
    assertThat(pattern.from("foo")).hasValue("foo");
    assertThat(pattern.from("fo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo bar foo")).containsExactly("foo", "foo");
    assertThat(pattern.repeatedly().from("bar")).isEmpty();
  }

  @Test public void between_patternNotFound() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertThat(pattern.from("<-fo->")).isEmpty();
    assertThat(pattern.repeatedly().from("<-fo->")).isEmpty();
  }

  @Test public void between_lookbehindAbsent() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertThat(pattern.from("<!-foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("<!-foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("foo->")).isEmpty();
  }

  @Test public void between_lookaheadAbsent() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo>>")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo>>")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo")).isEmpty();
  }

  @Test public void between_found() {
    Substring.Pattern pattern = Substring.word().immediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo->")).hasValue("foo");
    assertThat(pattern.repeatedly().from("<-foo->")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<-foo-> <-bar->")).containsExactly("foo", "bar");
  }

  @Test public void firstString_between_found() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo->")).hasValue("foo");
    assertThat(pattern.repeatedly().from("<-foo->")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<-foo-> <-foo-> <-foo- -foo->"))
        .containsExactly("foo", "foo");
  }

  @Test public void firstChar_between_found() {
    Substring.Pattern pattern = first('.').immediatelyBetween("<-", "->");
    assertThat(pattern.from("<-.->")).hasValue(".");
    assertThat(pattern.repeatedly().from("<-.->")).containsExactly(".");
    assertThat(pattern.repeatedly().from("<-.-> <-.-> <-.- -.->")).containsExactly(".", ".");
  }

  @Test public void between_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern = Substring.between("(", ")").immediatelyBetween("[(", ")]");
    assertThat(pattern.from("[(foo)]")).hasValue("foo");
    assertThat(pattern.repeatedly().from("[(foo)]")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("[(foo)] [(bar)]")).containsExactly("foo", "bar");
  }

  @Test public void between_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern = Substring.between("oo", "cc").immediatelyBetween("ooo", "ccc");
    assertThat(pattern.from("oofooccooobarccc")).hasValue("bar");
    assertThat(pattern.repeatedly().from("oofooccooobarccc")).containsExactly("bar");
  }

  @Test public void between_repetitionStartsFromLookahead() {
    Substring.Pattern pattern = Substring.first("bar").immediatelyBetween("of", "o");
    assertThat(pattern.from("ofbarofbaro")).hasValue("bar");
    assertThat(pattern.repeatedly().from("ofbarofbaro"))
        .containsExactly("bar", "bar");
  }

  @Test public void between_thenBacktracks() {
    Substring.Pattern pattern = Substring.first(':').then(Substring.word()).immediatelyBetween("(", ")");
    assertThat(pattern.from(": (foo)")).hasValue("foo");
    assertThat(pattern.from(": foo (bar)")).hasValue("bar");
    assertThat(pattern.repeatedly().from(": foo (bar) : or (zoo)")).containsExactly("bar", "zoo");
    assertThat(pattern.repeatedly().from(": foo (bar) or :(zoo)")).containsExactly("bar", "zoo");
  }

  @Test public void notFollowedBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").notFollowedBy("...");
    assertThat(pattern.from("")).isEmpty();
    assertThat(pattern.from("bar")).isEmpty();
    assertThat(pattern.repeatedly().from("bar")).isEmpty();
  }

  @Test public void notFollowedBy_lookaheadNoFound() {
    Substring.Pattern pattern = first("foo").notFollowedBy("...");
    assertThat(pattern.from("foo...")).isEmpty();
    assertThat(pattern.repeatedly().from("foo...")).isEmpty();
    assertThat(pattern.repeatedly().from("foo....")).isEmpty();
  }

  @Test public void notFollowedBy_found() {
    Substring.Pattern pattern = first("foo").notFollowedBy("...");
    assertThat(pattern.from("foo")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo.")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("foo..")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("foo.barfoo..")).containsExactly("foo", "foo");
  }

  @Test public void notFollowedBy_backtracking() {
    Substring.Pattern pattern = Substring.first("--").notFollowedBy("->");
    assertThat(pattern.from("<---->--")).hasValue("--");
  }

  @Test public void notFollowedBy_prefixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.prefix("--").notFollowedBy("->");
    assertThat(pattern.from("--->--")).isEmpty();
    assertThat(pattern.repeatedly().from("--->--")).isEmpty();
  }

  @Test public void notFollowedBy_beforeHasNoBacktracking() {
    Substring.Pattern pattern = Substring.before(first("--")).notFollowedBy("--->");
    assertThat(pattern.from("--->--")).isEmpty();
    assertThat(pattern.repeatedly().from("--->--")).isEmpty();
  }

  @Test public void notFollowedBy_afterHasNoBacktracking() {
    Substring.Pattern pattern = Substring.after(first("--")).notFollowedBy("");
    assertThat(pattern.from("--->--")).isEmpty();
    assertThat(pattern.repeatedly().from("--->--")).isEmpty();
  }

  @Test public void notFollowedBy_upToIncludingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.upToIncluding(first("--")).notFollowedBy("->");
    assertThat(pattern.from("--->--")).isEmpty();
    assertThat(pattern.repeatedly().from("--->--")).isEmpty();
  }

  @Test public void notFollowedBy_leadingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.leading(CharPredicate.is('-')).notFollowedBy(">");
    assertThat(pattern.from("--->--")).isEmpty();
    assertThat(pattern.repeatedly().from("--->--")).isEmpty();
  }

  @Test public void notPrecededBy_suffixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.suffix("--").notPrecededBy("<-");
    assertThat(pattern.from("--<---")).isEmpty();
    assertThat(pattern.repeatedly().from("--<---")).isEmpty();
  }

  @Test public void notPrecededBy_trailingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.trailing(CharPredicate.is('-')).notPrecededBy("<");
    assertThat(pattern.from("--<---")).isEmpty();
    assertThat(pattern.repeatedly().from("--<---")).isEmpty();
  }

  @Test public void notPrecededBy_toEndHasNoBacktracking() {
    Substring.Pattern pattern = first("--").toEnd().notPrecededBy("<");
    assertThat(pattern.from("<--(---->")).isEmpty();
    assertThat(pattern.repeatedly().from("<--(---->")).isEmpty();
  }

  @Test public void notPrecededBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").notPrecededBy("...");
    assertThat(pattern.from("bar")).isEmpty();
    assertThat(pattern.repeatedly().from("bar")).isEmpty();
  }

  @Test public void notPrecededBy_lookbehindAbsent() {
    Substring.Pattern pattern = first("foo").notPrecededBy("...");
    assertThat(pattern.from("...foo")).isEmpty();
    assertThat(pattern.repeatedly().from("....foo")).isEmpty();
    assertThat(pattern.repeatedly().from("...foo")).isEmpty();
  }

  @Test public void notPrecededBy_found() {
    Substring.Pattern pattern = first("foo").notPrecededBy("...");
    assertThat(pattern.from("..foo")).hasValue("foo");
    assertThat(pattern.repeatedly().from("..foo")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("bar..foo bar.foo")).containsExactly("foo", "foo");
  }

  @Test public void notBetween_empty() {
    Substring.Pattern pattern = first("foo").notPrecededBy("");
    assertThat(pattern.from("foo")).isEmpty();
    assertThat(pattern.repeatedly().from(" foo ")).isEmpty();
    assertThat(pattern.repeatedly().from("foo")).isEmpty();
  }

  @Test public void notBetween_patternNotFound() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<fo>")).isEmpty();
    assertThat(pattern.repeatedly().from("<fo>")).isEmpty();
  }

  @Test public void notBetween_lookaheadAbsent() {
    Substring.Pattern pattern = Substring.word().notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo-->")).hasValue("foo");
    assertThat(pattern.repeatedly().from("<-foo-->")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<-foo--> bar"))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test public void notBetween_lookbehindAbsent() {
    Substring.Pattern pattern = Substring.word().notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<--foo->")).hasValue("foo");
    assertThat(pattern.repeatedly().from("<--foo->")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<--foo-> bar->"))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test public void notBetween_bothLookbehindAndLookaheadPresent() {
    Substring.Pattern pattern = Substring.word().notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo-->")).hasValue("foo");
    assertThat(pattern.repeatedly().from("<-foo-->")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<-foo--> bar"))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test public void notBetween_neitherLookbehindNorLookaheadPresent() {
    Substring.Pattern pattern = Substring.word().notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<<foo>")).hasValue("foo");
    assertThat(pattern.from("<--foo-->")).hasValue("foo");
    assertThat(pattern.from("foo")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<<foo>> bar")).containsExactly("foo", "bar");
  }

  @Test public void notBetween_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern = Substring.between("(", ")").notImmediatelyBetween("[(", ")]");
    assertThat(pattern.from("{(foo)}")).hasValue("foo");
    assertThat(pattern.repeatedly().from("{(foo)}")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("{(foo)} {(bar)}")).containsExactly("foo", "bar");
  }

  @Test public void notBetween_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern = Substring.between("oa", "ac").notImmediatelyBetween("ooa", "acc");
    assertThat(pattern.from("ooafooaccoabarac")).hasValue("ccoabar");
  }

  @Test public void notBetween_thenBacktracks() {
    Substring.Pattern pattern = Substring.first(':').then(Substring.word()).notImmediatelyBetween("((", "))");
    assertThat(pattern.from(": foo")).hasValue("foo");
    assertThat(pattern.from(": ((foo)) [bar]")).hasValue("bar");
    assertThat(pattern.repeatedly().from(": ((foo)) bar : <zoo>")).containsExactly("bar", "zoo");
    assertThat(pattern.repeatedly().from(": ((foo)) bar :(zoo)")).containsExactly("bar", "zoo");
  }

  @Test
  public void regex_followedBy_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).followedBy("...");
    assertThat(pattern.from("foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo..")).isEmpty();
  }

  @Test
  public void regex_followedBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).followedBy("...");
    assertThat(pattern.from("foo...")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo...")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("foo...bar zoo...")).containsExactly("foo", "zoo");
  }

  @Test
  public void regex_followedBy_backtracking() {
    Substring.Pattern pattern = Substring.first(Pattern.compile("--")).followedBy("->");
    assertThat(pattern.from("<---->")).hasValue("--");
  }

  @Test
  public void regex_notFollowedBy_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notFollowedBy("...");
    assertThat(pattern.from("foo...")).hasValue("fo");
    assertThat(pattern.repeatedly().from("foo...")).containsExactly("fo");
    assertThat(pattern.repeatedly().from("foo....bar")).containsExactly("fo", "bar");
  }

  @Test
  public void regex_notFollowedBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notFollowedBy("...");
    assertThat(pattern.from("foo")).hasValue("foo");
    assertThat(pattern.repeatedly().from("foo.")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("foo..")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("foo.barfoo..")).containsExactly("foo", "barfoo");
  }

  @Test
  public void regex_notFollowedBy_backtracking() {
    Substring.Pattern pattern = Substring.first(Pattern.compile("--")).notFollowedBy("->");
    assertThat(pattern.from("<---->--")).hasValue("--");
  }

  @Test
  public void regex_precededBy_patternNotFound() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).precededBy("...");
    assertThat(pattern.from("...bar")).isEmpty();
    assertThat(pattern.repeatedly().from("...bar")).isEmpty();
  }

  @Test
  public void regex_precededBy_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).precededBy("...");
    assertThat(pattern.from("..foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo")).isEmpty();
    assertThat(pattern.repeatedly().from("..foo")).isEmpty();
  }

  @Test
  public void regex_precededBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).precededBy("...");
    assertThat(pattern.from("...foo")).hasValue("foo");
    assertThat(pattern.repeatedly().from("...foo")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("bar...foo bar...foo")).containsExactly("foo", "foo");
  }

  @Test
  public void regex_notPrecededBy_patternNotFound() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notPrecededBy("...");
    assertThat(pattern.from("bar")).isEmpty();
    assertThat(pattern.repeatedly().from("bar")).isEmpty();
  }

  @Test
  public void regex_notPrecededBy_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notPrecededBy("...");
    assertThat(pattern.from("...f")).isEmpty();
    assertThat(pattern.repeatedly().from("....f")).isEmpty();
    assertThat(pattern.repeatedly().from("...f")).isEmpty();
  }

  @Test
  public void regex_notPrecededBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notPrecededBy("...");
    assertThat(pattern.from("..foo")).hasValue("foo");
    assertThat(pattern.repeatedly().from("..foo")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("bar..foo bar...foo"))
        .containsExactly("bar", "foo", "bar", "oo")
        .inOrder();
  }

  @Test
  public void regex_between_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("<-", "->");
    assertThat(pattern.from("<!-foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("<!-foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("<-!foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("foo->")).isEmpty();
  }

  @Test
  public void regex_between_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo>>")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo>>")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo>->")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo")).isEmpty();
    assertThat(pattern.repeatedly().from("foo")).isEmpty();
  }

  @Test
  public void regex_between_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo->")).hasValue("foo");
    assertThat(pattern.repeatedly().from("<-foo->")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<-foo-> <-bar->")).containsExactly("foo", "bar");
  }

  @Test
  public void regex_between_reluctance() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("a", "b");
    assertThat(pattern.from("afoob")).hasValue("foo");
  }

  @Test
  public void regex_between_empty() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).precededBy("");
    assertThat(pattern.from("<-foo->")).hasValue("foo");
    assertThat(pattern.repeatedly().from("<-foo-> <-bar->")).containsExactly("foo", "bar");
  }

  @Test
  public void regex_notBetween_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo->")).isEmpty();
  }

  @Test
  public void regex_notBetween_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo->")).isEmpty();
  }

  @Test
  public void regex_notBetween_bothLookbehindAndLookaheadPresent() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<-foo->")).isEmpty();
    assertThat(pattern.repeatedly().from("<-foo->")).isEmpty();
  }

  @Test
  public void regex_notBetween_neitherLookbehindNorLookaheadPresent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notImmediatelyBetween("<-", "->");
    assertThat(pattern.from("<<foo>>")).hasValue("foo");
    assertThat(pattern.from("foo")).hasValue("foo");
    assertThat(pattern.from("<-food->")).hasValue("ood");
    assertThat(pattern.repeatedly().from("foo")).containsExactly("foo");
    assertThat(pattern.repeatedly().from("<<foo>> bar")).containsExactly("foo", "bar");
  }

  @Test
  public void regex_notBetween_empty() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notPrecededBy("");
    assertThat(pattern.from("<foo>")).isEmpty();
    assertThat(pattern.from("")).isEmpty();
    assertThat(pattern.from("foo")).isEmpty();
  }

  @Test
  public void splitBeforeDelimiter() {
    assertThat(
            Substring.first(".").skip(0, 1).repeatedly().split("a.b.c.de").map(Object::toString))
        .containsExactly("a", ".b", ".c", ".de")
        .inOrder();
  }

  @Test
  public void splitAfterDelimiter() {
    assertThat(
            Substring.first(".").skip(1, 0).repeatedly().split("a.b.c.de").map(Object::toString))
        .containsExactly("a.", "b.", "c.", "de")
        .inOrder();
  }

  @Test public void testRegexTopLevelGroups_noGroup() {
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("f+")).from("fff"))
        .containsExactly("fff");
  }

  @Test public void testRegexTopLevelGroups_singleGroup() {
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)")).from("fffdef"))
        .containsExactly("fff");
  }

  @Test public void testRegexTopLevelGroups_twoGroups() {
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)")).from("fffcde"))
        .containsExactly("fff", "cde");
  }

  @Test public void testRegexTopLevelGroups_repeatingGroup() {
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("((f){2,3})")).from("fffcde"))
        .containsExactly("fff");
  }

  @Test public void testRegexTopLevelGroups_nestedGroup() {
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("((ab)(cd)+)ef")).from("abcdcdef"))
        .containsExactly("abcdcd");
  }

  @Test public void testRegexTopLevelGroups_noMatch() {
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("((ab)(cd)+)ef")).from("cdef"))
        .isEmpty();
  }

  @Test public void testRegexTopLevelGroups_matchWithFromIndex() {
    assertThat(
            Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
                .match("fffcde", 1)
                .map(Object::toString))
        .containsExactly("ff", "cde");
    assertThat(
            Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
                .match("fffcde", 2)
                .map(Object::toString))
        .containsExactly("f", "cde");
  }

  @Test public void testRegexTopLevelGroups_beforeAfter_withFromIndex() {
    // Test that before() and after() return text relative to ORIGINAL input, not the substring
    // Original: "abcfffcde123"
    // From index 3: "fffcde" -> matches "(f+)(cde)" -> ["fff", "cde"]
    // The first group is "fff" at index 3-6 in the original input
    // before() should be "abc" (text before index 3)
    // after() should be "cde123" (text after index 6, including the second group)
    Substring.Match match =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match("abcfffcde123", 3)
            .findFirst()
            .get();
    assertThat(match.before()).isEqualTo("abc");
    assertThat(match.after()).isEqualTo("cde123");
    assertThat(match.toString()).isEqualTo("fff");

    // Test with match at the beginning (fromIndex = 0)
    Substring.Match matchAtStart =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match("fffcde123", 0)
            .findFirst()
            .get();
    assertThat(matchAtStart.before()).isEmpty();
    assertThat(matchAtStart.after()).isEqualTo("cde123");

    // Test with match at the end
    Substring.Match matchAtEnd =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match("123fffcde", 3)
            .findFirst()
            .get();
    assertThat(matchAtEnd.before()).isEqualTo("123");
    assertThat(matchAtEnd.after()).isEqualTo("cde");
  }

  @Test public void testRegexTopLevelGroups_remove_withFromIndex() {
    // Test that remove() correctly removes the first group match from the ORIGINAL input
    String input = "abcfffcde123";
    Substring.Match match =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match(input, 3)
            .findFirst()
            .get();
    // Should remove "fff" (first group) from the original, leaving "abccde123"
    assertThat(match.remove()).isEqualTo("abccde123");

    // Test with match at the beginning
    String input2 = "fffcde123";
    Substring.Match match2 =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match(input2, 0)
            .findFirst()
            .get();
    assertThat(match2.remove()).isEqualTo("cde123");

    // Test with match at the end
    String input3 = "123fffcde";
    Substring.Match match3 =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match(input3, 3)
            .findFirst()
            .get();
    assertThat(match3.remove()).isEqualTo("123cde");

    // Test with match in the middle only
    String input4 = "xxxfffcdeyyy";
    Substring.Match match4 =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match(input4, 3)
            .findFirst()
            .get();
    assertThat(match4.remove()).isEqualTo("xxxcdeyyy");
  }

  @Test public void testRegexTopLevelGroups_fullString_withFromIndex() {
    // Test that fullString() returns the ORIGINAL input, not the substring
    String input = "abcfffcde123";
    Substring.Match match =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match(input, 3)
            .findFirst()
            .get();
    assertThat(match.fullString()).isEqualTo(input);

    // Even when matching from middle of string, fullString() returns original
    String input2 = "start-fffcde-end";
    Substring.Match match2 =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(fff)(cde)"))
            .match(input2, 6)
            .findFirst()
            .get();
    assertThat(match2.fullString()).isEqualTo(input2);
  }

  @Test public void testRegexTopLevelGroups_index_withFromIndex() {
    // Test that index() and length() are absolute positions in ORIGINAL input
    String input = "abcfffcde123";
    Substring.Match match =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match(input, 3)
            .findFirst()
            .get();
    // First group "fff" starts at index 3 and has length 3
    assertThat(match.index()).isEqualTo(3);
    assertThat(match.length()).isEqualTo(3);

    // Test with match at different positions
    String input2 = "xfffcde";
    Substring.Match match2 =
        Substring.topLevelGroups(java.util.regex.Pattern.compile("(f+)(cde)"))
            .match(input2, 1)
            .findFirst()
            .get();
    assertThat(match2.index()).isEqualTo(1);
    assertThat(match2.length()).isEqualTo(3);

    // Verify indices are consistent with before() and after()
    assertThat(match2.before()).isEqualTo(input2.substring(0, match2.index()));
    assertThat(match2.after()).isEqualTo(input2.substring(match2.index() + match2.length()));
  }

  @Test public void testRegexTopLevelGroups_optionalGroupNotParticipating() {
    // Pattern: (a)?(b) - first group optional
    // Input: "b" - group 1 doesn't participate, should skip it and return only group 2
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)")).from("b"))
        .containsExactly("b");
  }

  @Test public void testRegexTopLevelGroups_optionalGroupParticipating() {
    // When optional group does participate, both groups are returned
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)")).from("ab"))
        .containsExactly("a", "b");
  }

  @Test public void testRegexTopLevelGroups_multipleOptionalGroups() {
    // Pattern: (a)?(b)?(c) - two optional groups
    // Only group 3 participates
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)?(c)")).from("c"))
        .containsExactly("c");
    // Groups 1 and 3 participate, group 2 doesn't
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)?(c)")).from("ac"))
        .containsExactly("a", "c");
    // All groups participate
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)?(c)")).from("abc"))
        .containsExactly("a", "b", "c");
  }

  @Test public void testRegexTopLevelGroups_nestedOptionalGroups() {
    // Pattern: ((a)b)?(c) - outer group is optional
    // "c" - outer group doesn't participate, only group 2
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("((a)b)?(c)")).from("c"))
        .containsExactly("c");
    // "abc" - both groups participate
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("((a)b)?(c)")).from("abc"))
        .containsExactly("ab", "c");
  }

  @Test public void testRegexTopLevelGroups_trailingOptionalGroup() {
    // Pattern: (\d+)(\.\d+)? - optional decimal part
    // "42" - no decimal part
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(\\d+)(\\.\\d+)?")).from("42"))
        .containsExactly("42");
    // "42.99" - has decimal part
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(\\d+)(\\.\\d+)?")).from("42.99"))
        .containsExactly("42", ".99");
  }

  @Test public void testRegexTopLevelGroups_optionalGroupInMiddle() {
    // Pattern: (a+)(b)?(c+) - optional middle group
    // "ac" - middle group doesn't participate
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a+)(b)?(c+)")).from("ac"))
        .containsExactly("a", "c");
    // "abc" - all groups participate
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a+)(b)?(c+)")).from("abc"))
        .containsExactly("a", "b", "c");
  }

  @Test public void testRegexTopLevelGroups_allOptionalGroups() {
    // Pattern with all top-level groups optional: (a)?(b)?
    // Only "b" participates
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)?")).from("b"))
        .containsExactly("b");
    // Only "a" participates
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)?")).from("a"))
        .containsExactly("a");
    // Both participate
    assertThat(Substring.topLevelGroups(java.util.regex.Pattern.compile("(a)?(b)?")).from("ab"))
        .containsExactly("a", "b");
  }

  @Test
  public void match_contentEquals_false() {
    Substring.Match match = first("bar").in("foobarbaz").get();
    assertThat(match.contentEquals("barb")).isFalse();
    assertThat(match.contentEquals("obar")).isFalse();
    assertThat(match.contentEquals("ba")).isFalse();
    assertThat(match.contentEquals("ar")).isFalse();
  }

  @Test
  public void match_contentEquals_true() {
    Substring.Match match = first("bar").in("foobarbaz").get();
    assertThat(match.contentEquals("bar")).isTrue();
  }

  @Test
  public void match_startsWith_false() {
    Substring.Match match = first("bar").in("foobarbaz").get();
    assertThat(match.startsWith("barb")).isFalse();
    assertThat(match.startsWith("barbaz")).isFalse();
    assertThat(match.startsWith("barbaz1")).isFalse();
    assertThat(match.startsWith("arb")).isFalse();
  }

  @Test
  public void match_startsWith_true() {
    Substring.Match match = first("bar").in("foobarbaz").get();
    assertThat(match.startsWith("")).isTrue();
    assertThat(match.startsWith("b")).isTrue();
    assertThat(match.startsWith("ba")).isTrue();
    assertThat(match.startsWith("bar")).isTrue();
  }

  @Test
  public void match_endsWith_false() {
    Substring.Match match = first("bar").in("foobarbaz").get();
    assertThat(match.endsWith("obar")).isFalse();
    assertThat(match.endsWith("rb")).isFalse();
    assertThat(match.endsWith("foobar")).isFalse();
    assertThat(match.endsWith("baz")).isFalse();
  }

  @Test
  public void match_endsWith_true() {
    Substring.Match match = first("bar").in("foobarbaz").get();
    assertThat(match.endsWith("bar")).isTrue();
    assertThat(match.endsWith("ar")).isTrue();
    assertThat(match.endsWith("r")).isTrue();
    assertThat(match.endsWith("")).isTrue();
  }

  @Test public void match_isFollowedBy_notAtTheEnd() {
    Substring.Match match = first("foo").in("foobar").get();
    assertThat(match.isFollowedBy("a")).isFalse();
    assertThat(match.isFollowedBy("ob")).isFalse();
    assertThat(match.isFollowedBy("bar")).isTrue();
    assertThat(match.isFollowedBy("ba")).isTrue();
    assertThat(match.isFollowedBy("b")).isTrue();
    assertThat(match.isFollowedBy("")).isTrue();
  }

  @Test public void match_isFollowedBy_atTheEnd() {
    Substring.Match match = first("bar").in("foobar").get();
    assertThat(match.isFollowedBy("r")).isFalse();
    assertThat(match.isFollowedBy("")).isTrue();
  }

  @Test public void match_isFollowedByChar_notAtTheEnd() {
    Substring.Match match = first("foo").in("foobar").get();
    assertThat(match.isFollowedBy(c -> c == 'a')).isFalse();
    assertThat(match.isFollowedBy(c -> c == 'b')).isTrue();
  }

  @Test public void match_isFollowedByByChar_atTheEnd() {
    Substring.Match match = first("bar").in("foobar").get();
    assertThat(match.isFollowedBy(c -> true)).isFalse();
  }

  @Test public void match_isPrecededBy_notAtTheBeginning() {
    Substring.Match match = first("bar").in("foobar").get();
    assertThat(match.isPrecededBy("b")).isFalse();
    assertThat(match.isPrecededBy("foob")).isFalse();
    assertThat(match.isPrecededBy("fo")).isFalse();
    assertThat(match.isPrecededBy("foo")).isTrue();
    assertThat(match.isPrecededBy("oo")).isTrue();
    assertThat(match.isPrecededBy("o")).isTrue();
    assertThat(match.isPrecededBy("")).isTrue();
  }

  @Test public void match_isPrecededBy_atTheBeginning() {
    Substring.Match match = first("foo").in("foobar").get();
    assertThat(match.isPrecededBy("f")).isFalse();
    assertThat(match.isPrecededBy("foo")).isFalse();
    assertThat(match.isPrecededBy("bar")).isFalse();
    assertThat(match.isPrecededBy("")).isTrue();
  }

  @Test public void match_isPrecededByChar_notAtTheBeginning() {
    Substring.Match match = first("bar").in("foobar").get();
    assertThat(match.isPrecededBy(c -> c == 'b')).isFalse();
    assertThat(match.isPrecededBy(c -> c == 'o')).isTrue();
  }

  @Test public void match_isPrecededByChar_atTheBeginning() {
    Substring.Match match = first("foo").in("foobar").get();
    assertThat(match.isPrecededBy(c -> true)).isFalse();
  }

  @Test public void match_isImmediatelyBetween_inTheMiddle() {
    Substring.Match match = first("or").in("hello world").get();
    assertThat(match.isImmediatelyBetween("w", "d")).isFalse();
    assertThat(match.isImmediatelyBetween("wo", "rld")).isFalse();
    assertThat(match.isImmediatelyBetween(" ", "l")).isFalse();
    assertThat(match.isImmediatelyBetween("w", "l")).isTrue();
    assertThat(match.isImmediatelyBetween("", "l")).isTrue();
    assertThat(match.isImmediatelyBetween("w", "")).isTrue();
    assertThat(match.isImmediatelyBetween("hello w", "")).isTrue();
    assertThat(match.isImmediatelyBetween("", "ld")).isTrue();
    assertThat(match.isImmediatelyBetween("", "")).isTrue();
    assertThat(match.isImmediatelyBetween("hello w", "ld")).isTrue();
  }

  @Test public void match_isImmediatelyBetween_fullString() {
    Substring.Match match = first("foo").in("foo").get();
    assertThat(match.isImmediatelyBetween("f", "o")).isFalse();
    assertThat(match.isImmediatelyBetween("f", "")).isFalse();
    assertThat(match.isImmediatelyBetween("", "o")).isFalse();
    assertThat(match.isImmediatelyBetween("", "")).isTrue();
  }

  @Test public void match_isSeparatedBy_fullString() {
    Substring.Match match = first("foo").in("foo").get();
    assertThat(match.isSeparatedBy(Character::isWhitespace, Character::isWhitespace)).isTrue();
  }

  @Test public void match_isSeparatedBy_atEnd() {
    Substring.Match match = first("foo").in(" foo").get();
    assertThat(match.isSeparatedBy(Character::isWhitespace, Character::isWhitespace)).isTrue();
    assertThat(match.isSeparatedBy(Character::isLowerCase, Character::isWhitespace)).isFalse();
  }

  @Test public void match_isSeparatedBy_atBeginning() {
    Substring.Match match = first("foo").in("food").get();
    assertThat(match.isSeparatedBy(Character::isLowerCase, Character::isLowerCase)).isTrue();
    assertThat(match.isSeparatedBy(Character::isLowerCase, Character::isWhitespace)).isFalse();
  }

  @Test public void match_isSeparatedBy_middle() {
    Substring.Match match = first("foo").in("afood").get();
    assertThat(match.isSeparatedBy(Character::isLowerCase, Character::isLowerCase)).isTrue();
    assertThat(match.isSeparatedBy(Character::isLowerCase, Character::isWhitespace)).isFalse();
    assertThat(match.isSeparatedBy(Character::isWhitespace, Character::isLowerCase)).isFalse();
  }

  @Test public void match_notEmpty() {
    Substring.Match match = first("foo").in("afood").get();
    assertThat(match.isEmpty()).isFalse();
    assertThat(match.isNotEmpty()).isTrue();
  }

  @Test public void match_empty() {
    Substring.Match match = first("").in("afood").get();
    assertThat(match.isEmpty()).isTrue();
    assertThat(match.isNotEmpty()).isFalse();
  }

  @Test public void repeatingPattern_matchWithNegativeFromIndex() {
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> Substring.between('-', '-').repeatedly().match("-foo-bar", -1));
  }

  @Test public void repeatingPattern_matchWithTooLargeFromIndex() {
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> Substring.between('-', '-').repeatedly().match("foo", 4));
  }

  @Test public void testNulls() throws Exception {
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

  private static<K,V> MultimapSubject assertKeyValues(BiStream<K, V> stream) {
    Multimap<?, ?> multimap = stream.collect(new BiCollector<K, V, Multimap<K, V>>() {
      @Override
      public <E> Collector<E, ?, Multimap<K, V>> collectorOf(Function<E, K> toKey, Function<E, V> toValue) {
        return SubstringTest.toLinkedListMultimap(toKey,toValue);
      }
    });
    return assertThat(multimap);
  }

  private static <T, K, V> Collector<T, ?, Multimap<K, V>> toLinkedListMultimap(
      Function<? super T, ? extends K> toKey, Function<? super T, ? extends V> toValue) {
    return Collector.of(
        LinkedListMultimap::create,
        (m, e) -> m.put(toKey.apply(e), toValue.apply(e)),
        (m1, m2) -> {
          m1.putAll(m2);
          return m1;
        });
  }

  private static String repeat(String s, int times) {
    return String.join("", nCopies(times, s));
  }
}
