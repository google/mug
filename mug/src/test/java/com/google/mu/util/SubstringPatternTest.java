package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.BEGINNING;
import static com.google.mu.util.Substring.END;
import static com.google.mu.util.Substring.NONE;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.before;
import static com.google.mu.util.Substring.consecutive;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.firstOccurrence;
import static com.google.mu.util.Substring.last;
import static com.google.mu.util.Substring.leading;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.Substring.spanningInOrder;
import static com.google.mu.util.Substring.trailing;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.Ascii;
import com.google.mu.util.Substring.Match;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class SubstringPatternTest {
  private static final CharPredicate LOWER = CharPredicate.range('a', 'z');
  private static final CharPredicate UPPER = CharPredicate.range('A', 'Z');
  private static final CharPredicate ALPHA = LOWER.or(UPPER);
  private static final CharPredicate DIGIT = CharPredicate.range('0', '9');

  @TestParameter private SubstringPatternVariant variant;

  @Test
  public void none() {
    assertPattern(NONE, "foo").findsNothing();
    assertPattern(NONE, "").findsNothing();
  }

  @Test
  public void reduceWithNone() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar")).reduce(NONE, Substring.Pattern::or);
    assertPattern(pattern, "").findsNothing();
    assertPattern(pattern, "foo").finds("foo");
    assertPattern(pattern, "bar").finds("bar");
  }

  @Test
  public void twoWaySplit_first() {
    assertPattern(first('='), " foo=bar").twoWaySplitsTo(" foo", "bar");
    assertPattern(first('='), " foo:bar").findsNothing();
  }

  @Test
  public void twoWaySplit_begin() {
    assertPattern(BEGINNING, " foo").twoWaySplitsTo("", " foo");
  }

  @Test
  public void twoWaySplit_end() {
    assertPattern(END, " foo").twoWaySplitsTo(" foo", "");
  }

  @Test
  public void twoWaySplitThenTrim_first() {
    assertPattern(first('='), " foo =bar").twoWaySplitsThenTrimsTo("foo", "bar");
  }

  @Test
  public void twoWaySplitThenTrim_beginning() {
    assertPattern(BEGINNING, " foo").twoWaySplitsThenTrimsTo("", "foo");
  }

  @Test
  public void splitThenTrim_end() {
    assertPattern(END, " foo ").twoWaySplitsThenTrimsTo("foo", "");
  }

  @Test
  public void limit_noMatch() {
    assertPattern(first("foo").limit(1), "fur").findsNothing();
  }

  @Test
  public void limit_smallerThanSize() {
    assertPattern(first("foo").limit(1), "my food").finds("f");
    assertPattern(first("foo").limit(1), "my ffoof").finds("f");
    assertPattern(first("fff").limit(1), "fffff").finds("f");
    assertPattern(first("fff").limit(2), "ffffff").finds("ff", "ff");
  }

  @Test
  public void limit_equalToSize() {
    assertPattern(first("foo").limit(3), "my food").finds("foo");
    assertPattern(first("foo").limit(3), "my ffoof").finds("foo");
    assertPattern(first("fff").limit(3), "fffff").finds("fff");
  }

  @Test
  public void limit_greaterThanSize() {
    assertPattern(first("foo").limit(Integer.MAX_VALUE), "my food").finds("foo");
    assertPattern(first("foo").limit(Integer.MAX_VALUE), "my ffoof").finds("foo");
    assertPattern(first("fff").limit(Integer.MAX_VALUE), "ffffff").finds("fff", "fff");
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
    assertPattern(pattern.skip(1, 0), "fur").findsNothing();
  }

  @Test
  public void skipFromBeginning_smallerThanSize() {
    assertPattern(first("foo").skip(1, 0), "my food").finds("oo");
    assertPattern(first("foo").skip(1, 0), "my ffoof").finds("oo");
    assertPattern(first("fff").skip(1, 0), "fffff").finds("ff");
    assertPattern(first("fff").skip(2, 0), "ffffff").finds("f", "f");
  }

  @Test
  public void skipFromBeginning_equalToSize() {
    assertPattern(first("foo").skip(3, 0), "my food").finds("");
    assertPattern(first("foo").skip(3, 0), "my ffoof").finds("");
    assertPattern(first("fff").skip(3, 0), "fffff").finds("");
  }

  @Test
  public void skipFromBeginning_greaterThanSize() {
    assertPattern(first("foo").skip(Integer.MAX_VALUE, 0), "my food").finds("");
    assertPattern(first("foo").skip(Integer.MAX_VALUE, 0), "my ffoof").finds("");
    assertPattern(first("fff").skip(Integer.MAX_VALUE, 0), "ffffff").finds("", "");
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
    assertPattern(pattern.skip(0, 1), "fur").findsNothing();
  }

  @Test
  public void skipFromEnd_smallerThanSize() {
    assertPattern(first("foo").skip(0, 1), "my food").finds("fo");
    assertPattern(first("foo").skip(0, 1), "my ffoof").finds("fo");
    assertPattern(first("fff").skip(0, 1), "fffff").finds("ff");
    assertPattern(first("fff").skip(0, 2), "ffffff").finds("f", "f");
  }

  @Test
  public void skipFromEnd_equalToSize() {
    assertPattern(first("foo").skip(0, 3), "my food").finds("");
    assertPattern(first("foo").skip(0, 3), "my ffoof").finds("");
    assertPattern(first("fff").skip(0, 3), "fffff").finds("");
  }

  @Test
  public void skipFromEnd_greaterThanSize() {
    assertPattern(first("foo").skip(0, Integer.MAX_VALUE), "my food").finds("");
    assertPattern(first("foo").skip(0, Integer.MAX_VALUE), "my ffoof").finds("");
    assertPattern(first("fff").skip(0, Integer.MAX_VALUE), "ffffff").finds("", "");
  }

  @Test
  public void or_neitherMatches() {
    assertPattern(first("bar").or(first("foo")), "baz").findsNothing();
    assertPattern(prefix("bar").or(first("foo")), "baz").findsNothing();
  }

  @Test
  public void upToIncluding_noMatch() {
    assertPattern(Substring.upToIncluding(first("://")), "abc").findsNothing();
    assertPattern(Substring.upToIncluding(first("://")), "abc").findsNothing();
  }

  @Test
  public void upToIncluding_matchAtPrefix() {
    assertThat(variant.wrap(Substring.upToIncluding(first("://"))).removeFrom("://foo"))
        .isEqualTo("foo");
    assertPattern(Substring.upToIncluding(first("://")), "://foo").finds("://");
  }

  @Test
  public void upToIncluding_matchInTheMiddle() {
    assertThat(variant.wrap(Substring.upToIncluding(first("://"))).removeFrom("http://foo"))
        .isEqualTo("foo");
    assertPattern(Substring.upToIncluding(first("://")), "http://foo").finds("http://");
  }

  @Test
  public void upToIncluding_delimitedByRegexGroup() {
    assertPattern(Substring.upToIncluding(first(Pattern.compile("(/.)/"))), "foo/1/bar/2/")
        .finds("foo/1/", "bar/2/");
  }

  @Test
  public void toEnd_noMatch() {
    assertPattern(first("//").toEnd(), "abc").findsNothing();
    assertPattern(first("//").toEnd(), "abc").findsNothing();
  }

  @Test
  public void toEnd_matchAtSuffix() {
    assertThat(variant.wrap(first("//").toEnd()).removeFrom("foo//")).isEqualTo("foo");
    assertPattern(first("//").toEnd(), "foo//").finds("//");
  }

  @Test
  public void toEnd_matchInTheMiddle() {
    assertThat(variant.wrap(first("//").toEnd()).removeFrom("foo // bar")).isEqualTo("foo ");
    assertPattern(first("//").toEnd(), "foo // bar //").finds("// bar //");
  }

  @Test
  public void before_noMatch() {
    assertPattern(Substring.before(first("://")), "abc").findsNothing();
    assertPattern(Substring.before(first("://")), "abc").findsNothing();
  }

  @Test
  public void before_matchAtPrefix() {
    assertThat(variant.wrap(Substring.before(first("//"))).removeFrom("//foo")).isEqualTo("//foo");
    assertPattern(Substring.before(first("//")), "//foo").finds("");
    assertPattern(Substring.before(first("//")), "//foo//").finds("", "foo");
  }

  @Test
  public void before_matchInTheMiddle() {
    assertThat(variant.wrap(Substring.before(first("//"))).removeFrom("http://foo"))
        .isEqualTo("//foo");
    assertPattern(Substring.before(first("//")), "http://foo").finds("http:");
  }

  @Test
  public void before_limit() {
    assertThat(variant.wrap(Substring.before(first("//")).limit(4)).removeFrom("http://foo"))
        .isEqualTo("://foo");
    assertPattern(Substring.before(first("/")).limit(4), "http://foo/barbara/")
        .finds("http", "", "foo", "barb");
  }

  @Test
  public void repeatedly_split_distinct() {
    assertThat(
            variant
                .wrap(first(','))
                .repeatedly()
                .split("b,a,c,a,c,b,d")
                .map(Match::toString)
                .distinct())
        .containsExactly("b", "a", "c", "d")
        .inOrder();
  }

  @Test
  public void repeatedly_split_noMatch() {
    assertPattern(first("://"), "abc").splitsTo("abc");
  }

  @Test
  public void repeatedly_split_match() {
    assertPattern(first("//"), "//foo").splitsTo("", "foo");
    assertPattern(first("/"), "foo/bar").splitsTo("foo", "bar");
    assertPattern(first("/"), "foo/bar/").splitsTo("foo", "bar", "");
  }

  @Test
  public void repeatedly_split_byBetweenPattern() {
    Substring.Pattern comment = Substring.between(before(first("/*")), after(first("*/")));
    assertPattern(comment, "a").splitsTo("a");
    assertPattern(comment, "a/*comment*/").splitsTo("a", "");
    assertPattern(comment, "a/*comment*/b").splitsTo("a", "b");
    assertPattern(comment, "a/*c1*/b/*c2*/").splitsTo("a", "b", "");
    assertPattern(comment, "a/*c1*/b/*c2*/c").splitsTo("a", "b", "c");
  }

  @Test
  public void repeatedly_split_beginning() {
    assertPattern(BEGINNING, "foo").splitsTo("", "f", "o", "o", "");
  }

  @Test
  public void repeatedly_split_end() {
    assertPattern(END, "foo").splitsTo("foo", "");
    ;
  }

  @Test
  public void repeatedly_split_empty() {
    assertPattern(first(""), "foo").splitsTo("", "f", "o", "o", "");
  }

  @Test
  public void repeatedly_splitThenTrim_distinct() {
    assertThat(
            variant
                .wrap(first(','))
                .repeatedly()
                .splitThenTrim("b, a,c,a,c,b,d")
                .map(Match::toString)
                .distinct())
        .containsExactly("b", "a", "c", "d")
        .inOrder();
  }

  @Test
  public void repeatedly_splitThenTrim_noMatch() {
    assertPattern(first("://"), "abc").splitsThenTrimsTo("abc");
  }

  @Test
  public void repeatedly_splitThenTrim_match() {
    assertPattern(first("//"), "// foo").splitsThenTrimsTo("", "foo");
    assertPattern(first("/"), "foo / bar").splitsThenTrimsTo("foo", "bar");
    assertPattern(first("/"), " foo/bar/").splitsThenTrimsTo("foo", "bar", "");
  }

  @Test
  public void after_noMatch() {
    assertPattern(Substring.after(first("//")), "abc").findsNothing();
    assertPattern(Substring.after(first("//")), "abc").findsNothing();
  }

  @Test
  public void after_matchAtSuffix() {
    assertThat(variant.wrap(Substring.after(last('.'))).removeFrom("foo.")).isEqualTo("foo.");
    assertPattern(Substring.after(last('.')), "foo.").finds("");
  }

  @Test
  public void after_matchInTheMiddle() {
    assertThat(variant.wrap(Substring.after(last('.'))).repeatedly().removeAllFrom("foo. bar"))
        .isEqualTo("foo.");
    assertPattern(Substring.after(last('.')), "foo. bar").finds(" bar");
  }

  @Test
  public void between_nothingBetweenSameChar() {
    assertPattern(Substring.between('.', '.'), ".").findsNothing();
    assertPattern(Substring.between('.', '.'), ".").findsNothing();
  }

  @Test
  public void between_matchesOpenButNotClose() {
    assertPattern(Substring.between('<', '>'), "<foo").findsNothing();
    assertPattern(Substring.between('<', '>'), "<foo").findsNothing();
  }

  @Test
  public void between_matchesCloseButNotOpen() {
    assertPattern(Substring.between('<', '>'), "foo>").findsNothing();
    assertPattern(Substring.between('<', '>'), "foo>").findsNothing();
  }

  @Test
  public void between_closeIsBeforeOpen() {
    assertPattern(Substring.between('<', '>'), ">foo<").findsNothing();
    assertPattern(Substring.between('<', '>'), ">foo<").findsNothing();
  }

  @Test
  public void between_matchesNone() {
    assertPattern(Substring.between('<', '>'), "foo").findsNothing();
    assertPattern(Substring.between('<', '>'), "foo").findsNothing();
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

  @Test
  public void then_match() {
    assertPattern(first("GET").then(prefix(" ")), "GET http").twoWaySplitsTo("GET", "http");
    assertThat(
            variant
                .wrap(before(first('/')).then(prefix("")))
                .repeatedly()
                .match("foo/bar/")
                .map(Match::before))
        .containsExactly("foo", "foo/bar");
  }

  @Test
  public void then_firstPatternDoesNotMatch() {
    assertPattern(first("GET").then(prefix(" ")), "GE http").findsNothing();
  }

  @Test
  public void then_secondPatternDoesNotMatch() {
    assertPattern(first("GET").then(prefix(" ")), "GET: http").findsNothing();
  }

  @Test
  public void peek_empty() {
    assertPattern(prefix("http").peek(""), "http").finds("http");
    assertPattern(prefix("http").peek(""), "https").finds("http");
  }

  @Test
  public void peek_match() {
    assertPattern(leading(LOWER).peek(":"), "http://").finds("http");
    assertPattern(before(first('/')).peek("/"), "foo/bar/").finds("foo", "bar");
  }

  @Test
  public void peek_firstPatternDoesNotMatch() {
    assertPattern(prefix("http").peek(":"), "ftp://").findsNothing();
  }

  @Test
  public void peek_peekedPatternDoesNotMatch() {
    assertPattern(prefix("http").peek(":"), "https://").findsNothing();
    assertPattern(BEGINNING.peek(":"), "foo").splitsTo("foo");
  }

  @Test
  public void not_match() {
    assertPattern(prefix("http").peek(prefix(':').not()), "https://").finds("http");
    assertPattern(before(first('/')).peek(prefix('?').not()), "foo/bar/").finds("foo", "bar");
  }

  @Test
  public void not_noMatch() {
    assertPattern(prefix("http").not(), "http://").findsNothing();
  }

  @Test
  public void notOfNot_match() {
    assertThat(variant.wrap(prefix("http").not().not()).in("http").map(Match::after))
        .hasValue("http");
    assertPattern(prefix("http").not().not(), "http").finds("");
  }

  @Test
  public void notOfNot_noMatch() {
    assertPattern(prefix("http").not().not(), "ftp").findsNothing();
  }

  @Test
  public void patternFrom_noMatch() {
    assertPattern(prefix("foo"), "").findsNothing();
  }

  @Test
  public void patternFrom_match() {
    assertThat(Substring.first("bar").from("foo bar")).hasValue("bar");
  }

  @Test
  public void repeatedlyMatch_getLinesPreservingNewLineChar() {
    String text = "line1\nline2\nline3";
    assertPattern(Substring.upToIncluding(first('\n').or(END)), text)
        .finds("line1\n", "line2\n", "line3");
  }

  @Test
  public void spanningInOrder_twoStops_firstPatternDoesNotMatch() {
    assertPattern(spanningInOrder("o", "bar"), "far bar car").findsNothing();
  }

  @Test
  public void spanningInOrder_twoStops_secondPatternDoesNotMatch() {
    assertPattern(spanningInOrder("o", "bar"), "foo car").findsNothing();
  }

  @Test
  public void spanningInOrder_threeStops_firstPatternDoesNotMatch() {
    assertPattern(spanningInOrder("o", "bar", "car"), "far bar car").findsNothing();
  }

  @Test
  public void spanningInOrder_threeStops_secondPatternDoesNotMatch() {
    assertPattern(spanningInOrder("o", "bar", "car"), "foo boo car").findsNothing();
  }

  @Test
  public void spanningInOrder_threeStops_thirdPatternDoesNotMatch() {
    assertPattern(spanningInOrder("o", "bar", "car"), "foo bar cat").findsNothing();
  }

  @Test
  public void peekThenFirstOccurrence_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern =
        Stream.of("foo", "ood")
            .map(Substring::first)
            .map(p -> p.peek(" "))
            .collect(firstOccurrence());
    assertPattern(pattern, "food ").finds("ood");
  }

  @Test
  public void or_peek_alternativeBackTrackingTriggeredByBoundaryMismatch() {
    Substring.Pattern pattern = first("foo").or(first("food")).peek(" ");
    assertPattern(pattern, "food ").finds("food");
  }

  @Test
  public void or_withBoundary_alternativeBackTrackingTriggeredByBoundaryMismatch() {
    Substring.Pattern pattern =
        first("foo").or(first("food")).withBoundary(Character::isWhitespace);
    assertPattern(pattern, "food").finds("food");
  }

  @Test
  public void or_peek_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern = first("foo").or(first("food")).peek(" ");
    assertPattern(pattern, "food ").finds("food");
  }

  @Test
  public void or_limitThenPeek_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern = first("foo").or(first("food")).limit(4).peek(" ");
    assertPattern(pattern, "food ").finds("food");
  }

  @Test
  public void leading_noMatch() {
    assertPattern(leading(ALPHA), " foo").findsNothing();
  }

  @Test
  public void leading_match() {
    assertThat(leading(ALPHA).from("System.out")).hasValue("System");
    assertThat(leading(ALPHA).removeFrom("System.out")).isEqualTo(".out");
    assertThat(leading(LOWER).then(prefix(':')).in("http://google.com").map(Match::before))
        .hasValue("http");
  }

  @Test
  public void leading_match_repeatedly() {
    assertThat(variant.wrap(leading(ALPHA)).repeatedly().removeAllFrom("System.out"))
        .isEqualTo(".out");
    assertPattern(leading(ALPHA), "System.out").finds("System");
    assertPattern(leading(ALPHA), "out").finds("out");
  }

  @Test
  public void trailing_noMatch() {
    assertPattern(trailing(DIGIT), "123.").findsNothing();
  }

  @Test
  public void trailing_match() {
    assertPattern(trailing(DIGIT), "12>11").finds("11");
    assertThat(variant.wrap(trailing(DIGIT)).removeFrom("12>11")).isEqualTo("12>");
  }

  @Test
  public void trailing_match_repeatedly() {
    assertThat(variant.wrap(trailing(DIGIT)).repeatedly().removeAllFrom("12>11")).isEqualTo("12>");
    assertPattern(trailing(DIGIT), "12>11").finds("11");
    assertPattern(trailing(DIGIT), "11").finds("11");
  }

  @Test
  public void consecutive_noMatch() {
    assertPattern(consecutive(ALPHA), ".").findsNothing();
    assertPattern(consecutive(ALPHA), "").findsNothing();
    assertPattern(consecutive(ALPHA), " ").findsNothing();
  }

  @Test
  public void consecutive_match() {
    assertPattern(consecutive(ALPHA), " foo.").finds("foo");
    assertThat(variant.wrap(consecutive(ALPHA)).removeFrom(" foo.")).isEqualTo(" .");
    assertThat(variant.wrap(consecutive(ALPHA)).repeatedly().removeAllFrom(" foo."))
        .isEqualTo(" .");
  }

  @Test
  public void consecutive_match_repeatedly() {
    assertPattern(consecutive(ALPHA), " foo.").finds("foo");
    assertPattern(consecutive(ALPHA), "(System.out)").finds("System", "out");
    assertThat(
            variant
                .wrap(consecutive(ALPHA))
                .repeatedly()
                .replaceAllFrom("(System.out)", Ascii::toLowerCase))
        .isEqualTo("(system.out)");
  }

  @Test
  public void word_notFound() {
    assertPattern(Substring.word("cat"), "dog").findsNothing();
    assertPattern(Substring.word("cat"), "").findsNothing();
  }

  @Test
  public void word_onlyWord() {
    assertPattern(Substring.word("word"), "word").finds("word");
  }

  @Test
  public void word_partialWord() {
    assertPattern(Substring.word("cat"), "catchie").findsNothing();
    assertPattern(Substring.word("cat"), "bobcat").findsNothing();
  }

  @Test
  public void word_startedByWord() {
    assertPattern(Substring.word("cat"), "cat loves dog").finds("cat");
  }

  @Test
  public void word_endedByWord() {
    assertPattern(Substring.word("word"), "hello word").finds("word");
  }

  @Test
  public void word_multipleWords() {
    assertThat(Substring.word("cat").from("bobcat is not a cat, or is it a cat?")).hasValue("cat");
    assertThat(Substring.word("cat").in("bobcat is not a cat, or is it a cat?").get().before())
        .isEqualTo("bobcat is not a ");
    assertPattern(Substring.word("cat"), "bobcat is not a cat, or is it a cat?")
        .finds("cat", "cat");
  }

  @Test
  public void word_emptyWord() {
    assertPattern(Substring.word(""), "a.b").findsNothing();
    assertThat(
            variant
                .wrap(Substring.word(""))
                .repeatedly()
                .match("ab..cd,,ef,")
                .map(Substring.Match::index))
        .containsExactly(3, 7, 11)
        .inOrder();
  }

  @Test
  public void word_noMatch() {
    assertPattern(Substring.word(), "").findsNothing();
    assertPattern(Substring.word(), "./> ").findsNothing();
  }

  @Test
  public void word_matches() {
    assertPattern(Substring.word(), "hello world").finds("hello", "world");
  }

  @Test
  public void withBoundary_first() {
    CharPredicate left = LOWER.not();
    CharPredicate right = LOWER.or(CharPredicate.is('-')).not();
    Substring.Pattern petRock = Substring.first("pet-rock").withBoundary(left, right);
    assertPattern(petRock, "pet-rock").finds("pet-rock");
    assertPattern(petRock, "pet-rock is fun").finds("pet-rock");
    assertPattern(petRock, "love-pet-rock").finds("pet-rock");
    assertPattern(petRock, "pet-rock-not").findsNothing();
    assertPattern(petRock, "muppet-rock").findsNothing();
  }

  @Test
  public void withBoundary_skipEscape() {
    CharPredicate escape = CharPredicate.is('\\');
    Substring.Pattern unescaped = Substring.first("aaa").withBoundary(escape.not());
    assertPattern(unescaped, "\\aaaa").finds("aaa");
    assertThat(unescaped.in("\\aaaa").get().before()).isEqualTo("\\a");
  }

  @Test
  public void withBoundary_before() {
    CharPredicate boundary = LOWER.not();
    Substring.Pattern dir = Substring.before(first("//")).withBoundary(boundary);
    assertPattern(dir, "foo//bar//zoo").finds("foo", "bar");
  }

  private SubstringPatternAssertion assertPattern(Substring.Pattern pattern, String input) {
    return new SubstringPatternAssertion(variant.wrap(pattern), input);
  }
}
