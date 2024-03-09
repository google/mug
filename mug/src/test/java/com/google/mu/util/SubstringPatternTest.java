package com.google.mu.util;

import static com.google.common.base.Strings.repeat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
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
import static com.google.mu.util.Substring.BoundStyle.EXCLUSIVE;
import static com.google.mu.util.Substring.BoundStyle.INCLUSIVE;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.base.Ascii;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.mu.util.Substring.BoundStyle;
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
    assertPattern(first(','), "b,a,c,a,c,b,d").splitsDistinctTo("b", "a", "c", "d");
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
  public void repeatedly_split_byBetweenInclusivePattern() {
    Substring.Pattern comment = Substring.between(first("/*"), INCLUSIVE, first("*/"), INCLUSIVE);
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
    assertPattern(first(','), "b, a,c,a,c,b,d").splitsThenTrimsDistinctTo("b", "a", "c", "d");
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
  public void repeatedly_cut_noMatch() {
    assertPattern(first("://"), "abc").cutsTo("abc");
  }

  @Test
  public void repeatedly_cut_match() {
    assertPattern(first("//"), "//foo").cutsTo("", "//", "foo");
    assertPattern(first("/"), "foo/bar").cutsTo("foo", "/", "bar");
    assertPattern(first("/"), "foo/bar/").cutsTo("foo", "/", "bar", "/", "");
  }

  @Test
  public void repeatedly_cut_byBetweenPattern() {
    Substring.Pattern comment = Substring.between(before(first("/*")), after(first("*/")));
    assertPattern(comment, "a").cutsTo("a");
    assertPattern(comment, "a/*comment*/").cutsTo("a", "/*comment*/", "");
    assertPattern(comment, "a/*comment*/b").cutsTo("a", "/*comment*/", "b");
    assertPattern(comment, "a/*c1*/b/*c2*/").cutsTo("a", "/*c1*/", "b", "/*c2*/", "");
    assertPattern(comment, "a/*c1*/b/*c2*/c").cutsTo("a", "/*c1*/", "b", "/*c2*/", "c");
  }

  @Test
  public void repeatedly_cut_byBetweenInclusivePattern() {
    Substring.Pattern comment = Substring.between("/*", INCLUSIVE, "*/", INCLUSIVE);
    assertPattern(comment, "a").cutsTo("a");
    assertPattern(comment, "a/*comment*/").cutsTo("a", "/*comment*/", "");
    assertPattern(comment, "a/*comment*/b").cutsTo("a", "/*comment*/", "b");
    assertPattern(comment, "a/*c1*/b/*c2*/").cutsTo("a", "/*c1*/", "b", "/*c2*/", "");
    assertPattern(comment, "a/*c1*/b/*c2*/c").cutsTo("a", "/*c1*/", "b", "/*c2*/", "c");
  }

  @Test
  public void repeatedly_cut_beginning() {
    assertPattern(BEGINNING, "foo").cutsTo("", "", "f", "", "o", "", "o", "", "");
  }

  @Test
  public void repeatedly_cut_end() {
    assertPattern(END, "foo").cutsTo("foo", "", "");
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
  public void between_matchesBetweenSameChar() {
    assertPattern(Substring.between('.', '.'), "..").finds("");
    assertPattern(Substring.between('.', '.'), ".?.").finds("?");
  }

  @Test
  public void between_matchesBetweenDifferentChars() {
    assertPattern(Substring.between('<', '>'), "<>").finds("");
    assertPattern(Substring.between("<", ">"), "<?>").finds("?");
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
  public void betweenInclusive_matchesBetweenSameChar() {
    assertPattern(Substring.between('.', INCLUSIVE, '.', INCLUSIVE), "..").finds("..");
    assertPattern(Substring.between('.', INCLUSIVE, '.', INCLUSIVE), ".?.").finds(".?.");
  }

  @Test
  public void betweenInclusiveExclusive_matchesBetweenSameChar() {
    assertPattern(Substring.between('.', INCLUSIVE, '.', EXCLUSIVE), "..").finds(".");
  }

  @Test
  public void betweenExclusiveInclusive_matchesBetweenSameChar() {
    assertPattern(Substring.between('.', EXCLUSIVE, '.', INCLUSIVE), "..").finds(".");
  }

  @Test
  public void betweenInclusive_matchesBetweenDifferentChars() {
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), "<>").finds("<>");
    assertPattern(Substring.between("<", INCLUSIVE, ">", INCLUSIVE), "<?>").finds("<?>");
  }

  @Test
  public void betweenInclusiveExclusive_matchesBetweenDifferentChars() {
    assertPattern(Substring.between('<', INCLUSIVE, '>', EXCLUSIVE), "<>").finds("<");
    assertPattern(Substring.between("<", INCLUSIVE, ">", EXCLUSIVE), "<?>").finds("<?");
  }

  @Test
  public void betweenExclusiveInclusive_matchesBetweenDifferentChars() {
    assertPattern(Substring.between('<', EXCLUSIVE, '>', INCLUSIVE), "<>").finds(">");
    assertPattern(Substring.between("<", EXCLUSIVE, ">", INCLUSIVE), "<?>").finds("?>");
  }

  @Test
  public void betweenInclusive_nothingBetweenSameChar() {
    assertPattern(Substring.between('.', INCLUSIVE, '.', INCLUSIVE), ".").findsNothing();
    assertPattern(Substring.between('.', INCLUSIVE, '.', INCLUSIVE), ".").findsNothing();
  }

  @Test
  public void betweenInclusive_matchesOpenButNotClose() {
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), "<foo").findsNothing();
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), "<foo").findsNothing();
  }

  @Test
  public void betweenInclusive_matchesCloseButNotOpen() {
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), "foo>").findsNothing();
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), "foo>").findsNothing();
  }

  @Test
  public void betweenInclusive_closeIsBeforeOpen() {
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), ">foo<").findsNothing();
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), ">foo<").findsNothing();
  }

  @Test
  public void betweenInclusive_matchesNone() {
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), "foo").findsNothing();
    assertPattern(Substring.between('<', INCLUSIVE, '>', INCLUSIVE), "foo").findsNothing();
  }

  @Test
  public void betweenInclusive_withLimit() {
    assertThat(
            Substring.between(first("//").limit(1), INCLUSIVE, first("//").limit(1), INCLUSIVE)
                .repeatedly()
                .from("//abc//d////"))
        .containsExactly("//abc/", "//")
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
    assertPattern(prefix("http").peek(prefix("")), "http").finds("http");
    assertPattern(prefix("http").peek(prefix("")), "https").finds("http");
  }

  @Test
  public void peek_match() {
    assertPattern(leading(LOWER).peek(prefix(":")), "http://").finds("http");
    assertPattern(before(first('/')).peek(prefix("/")), "foo/bar/").finds("foo", "bar");
  }

  @Test
  public void peek_firstPatternDoesNotMatch() {
    assertPattern(prefix("http").peek(prefix(":")), "ftp://").findsNothing();
  }

  @Test
  public void peek_peekedPatternDoesNotMatch() {
    assertPattern(prefix("http").peek(prefix(":")), "https://").findsNothing();
    assertPattern(BEGINNING.peek(prefix(":")), "foo").splitsTo("foo");
  }

  @Test
  public void patternFrom_noMatch() {
    assertPattern(prefix("foo"), "").findsNothing();
  }

  @Test
  public void patternFrom_match() {
    assertPattern(first("bar"), "foo bar").finds("bar");
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
            .map(p -> p.peek(prefix(" ")))
            .collect(firstOccurrence());
    assertPattern(pattern, "food ").finds("ood");
  }

  @Test
  public void or_peek_alternativeBackTrackingTriggeredByBoundaryMismatch() {
    Substring.Pattern pattern = first("foo").or(first("food")).peek(prefix(" "));
    assertPattern(pattern, "food ").finds("food");
  }

  @Test
  public void or_separatedBy_alternativeBackTrackingTriggeredByBoundaryMismatch() {
    Substring.Pattern pattern =
        first("foo").or(first("food")).separatedBy(Character::isWhitespace);
    assertPattern(pattern, "food").finds("food");
  }

  @Test
  public void or_peek_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern = first("foo").or(first("food")).peek(prefix(" "));
    assertPattern(pattern, "food ").finds("food");
  }

  @Test
  public void or_limitThenPeek_alternativeBackTrackingTriggeredByPeek() {
    Substring.Pattern pattern = first("foo").or(first("food")).limit(4).peek(prefix(" "));
    assertPattern(pattern, "food ").finds("food");
  }

  @Test
  public void leading_noMatch() {
    assertPattern(leading(ALPHA), " foo").findsNothing();
  }

  @Test
  public void leading_match() {
    assertPattern(leading(ALPHA), "System.out").finds("System");
    assertThat(variant.wrap(leading(ALPHA)).removeFrom("System.out")).isEqualTo(".out");
    assertThat(
            variant
                .wrap(leading(LOWER).then(prefix(':')))
                .in("http://google.com")
                .map(Match::before))
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
  public void separatedBy_first() {
    CharPredicate left = LOWER.not();
    CharPredicate right = LOWER.or(CharPredicate.is('-')).not();
    Substring.Pattern petRock = Substring.first("pet-rock").separatedBy(left, right);
    assertPattern(petRock, "pet-rock").finds("pet-rock");
    assertPattern(petRock, "pet-rock is fun").finds("pet-rock");
    assertPattern(petRock, "love-pet-rock").finds("pet-rock");
    assertPattern(petRock, "pet-rock-not").findsNothing();
    assertPattern(petRock, "muppet-rock").findsNothing();
  }

  @Test
  public void separatedBy_separatorCharSkipped() {
    Substring.Pattern pattern =
        Substring.first("foo").skip(1, 0).separatedBy(CharPredicate.is('f'), CharPredicate.ANY);
    assertPattern(pattern, "nfoo").finds("oo");
  }

  @Test
  public void separatedBy_skipEscape() {
    CharPredicate escape = CharPredicate.is('\\');
    Substring.Pattern unescaped = Substring.first("aaa").separatedBy(escape.not());
    assertPattern(unescaped, "\\aaaa").finds("aaa");
    assertThat(unescaped.in("\\aaaa").get().before()).isEqualTo("\\a");
  }

  @Test
  public void separatedBy_before() {
    CharPredicate boundary = LOWER.not();
    Substring.Pattern dir = Substring.before(first("//")).separatedBy(boundary);
    assertPattern(dir, "foo//bar//zoo").finds("foo", "bar");
  }

  @Test
  public void followedBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").followedBy("...");
    assertPattern(pattern, "").findsNothing();
    assertPattern(pattern, "bar").findsNothing();
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void followedBy_lookaheadAbsent() {
    Substring.Pattern pattern = first("foo").followedBy("...");
    assertPattern(pattern, "foo").findsNothing();
    assertPattern(pattern, "foo").findsNothing();
    assertPattern(pattern, "foo..").findsNothing();
  }

  @Test
  public void followedBy_found() {
    Substring.Pattern pattern = first("foo").followedBy("...");
    assertPattern(pattern, "foo...").finds("foo");
    assertPattern(pattern, "foo...barfoo...").finds("foo", "foo");
  }

  @Test
  public void followedBy_backtracking() {
    Substring.Pattern pattern = Substring.first("--").followedBy("->");
    assertPattern(pattern, "<---->").finds("--");
  }

  @Test
  public void followedBy_prefixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.prefix("--").followedBy("->");
    assertPattern(pattern, "---->").findsNothing();
  }

  @Test
  public void followedBy_beforeHasNoBacktracking() {
    Substring.Pattern pattern = Substring.before(first("--")).followedBy("->");
    assertPattern(pattern, "---->").findsNothing();
  }

  @Test
  public void followedBy_afterHasNoBacktracking() {
    Substring.Pattern pattern = Substring.after(first("--")).followedBy("->");
    assertPattern(pattern, "---->").findsNothing();
  }

  @Test
  public void followedBy_upToIncludingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.upToIncluding(first("--")).followedBy("->");
    assertPattern(pattern, "---->").findsNothing();
  }

  @Test
  public void followedBy_leadingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.leading(CharPredicate.is('-')).followedBy("->");
    assertPattern(pattern, "---->").findsNothing();
  }

  @Test
  public void precededBy_suffixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.suffix("--").immediatelyBetween("<-", "");
    assertPattern(pattern, "<----").findsNothing();
  }

  @Test
  public void precededBy_trailingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.trailing(CharPredicate.is('-')).immediatelyBetween("<-", "");
    assertPattern(pattern, "<----").findsNothing();
  }

  @Test
  public void precededBy_toEndHasNoBacktracking() {
    Substring.Pattern pattern = first("--").toEnd().immediatelyBetween("<-", "");
    assertPattern(pattern, "<---->").findsNothing();
  }

  @Test
  public void precededBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("...", "");
    assertPattern(pattern, "...bar").findsNothing();
  }

  @Test
  public void precededBy_lookbehindAbsent() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("...", "");
    assertPattern(pattern, "foo").findsNothing();
    assertPattern(pattern, "..foo").findsNothing();
  }

  @Test
  public void precededBy_found() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("...", "");
    assertPattern(pattern, "...foo").finds("foo");
    assertPattern(pattern, "bar...foo bar...foo").finds("foo", "foo");
  }

  @Test
  public void between_empty() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("", "");
    assertPattern(pattern, "fo").findsNothing();
    assertPattern(pattern, "foo bar foo").finds("foo", "foo");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void between_patternNotFound() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertPattern(pattern, "").findsNothing();
    assertPattern(pattern, "<-").findsNothing();
    assertPattern(pattern, "<->").findsNothing();
    assertPattern(pattern, "<-->").findsNothing();
    assertPattern(pattern, "<-fo->").findsNothing();
    assertPattern(pattern, "<-fo->").findsNothing();
  }

  @Test
  public void between_lookbehindAbsent() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertPattern(pattern, "<!-foo->").findsNothing();
    assertPattern(pattern, "<-!foo->").findsNothing();
    assertPattern(pattern, "foo->").findsNothing();
  }

  @Test
  public void between_lookaheadAbsent() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo>>").findsNothing();
    assertPattern(pattern, "<-foo>->").findsNothing();
    assertPattern(pattern, "<-foo").findsNothing();
    assertPattern(pattern, "foo").findsNothing();
  }

  @Test
  public void between_found() {
    Substring.Pattern pattern = Substring.word().immediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").finds("foo");
    assertPattern(pattern, "<-foo-> <-bar->").finds("foo", "bar");
  }

  @Test
  public void firstString_between_found() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").finds("foo");
    assertPattern(pattern, "<-foo-> <-foo-> <-foo- -foo->").finds("foo", "foo");
  }

  @Test
  public void firstChar_between_found() {
    Substring.Pattern pattern = first('.').immediatelyBetween("<-", "->");
    assertPattern(pattern, "<-.->").finds(".");
    assertPattern(pattern, "<-.-> <-.-> <-.- -.->").finds(".", ".");
  }

  @Test
  public void between_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern = Substring.between("(", ")").immediatelyBetween("[(", ")]");
    assertPattern(pattern, "[(foo)]").finds("foo");
    assertPattern(pattern, "[(foo)] [(bar)]").finds("foo", "bar");
  }

  @Test
  public void betweenInclusive_betweenBacktrackingStartsWithoutDelimiter() {
    Substring.Pattern pattern = Substring.between("(", INCLUSIVE, ")", INCLUSIVE).immediatelyBetween("[", "]");
    assertPattern(pattern, "[(foo)]").finds("(foo)");
    assertPattern(pattern, "[(foo)] [(bar)]").finds("(foo)", "(bar)");
  }

  @Test
  public void between_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern = Substring.between("oo", "cc").immediatelyBetween("ooo", "ccc");
    assertPattern(pattern, "oofooccooobarccc").finds("bar");
  }

  @Test
  public void betweenInclusive_backtrackingBeforeOpeningDelimiter() {
    Substring.Pattern pattern = Substring.between("oo", INCLUSIVE, "cc", INCLUSIVE).immediatelyBetween("ooo", "ccc");
    assertPattern(pattern, "oofooccooobarccc").findsNothing();
  }

  @Test
  public void between_repetitionStartsFromLookahead() {
    Substring.Pattern pattern = Substring.first("bar").immediatelyBetween("of", "o");
    assertPattern(pattern, "ofbarofbaro").finds("bar", "bar");
  }

  @Test
  public void between_thenBacktracks() {
    Substring.Pattern pattern = Substring.first(':').then(Substring.word()).immediatelyBetween("(", ")");
    assertPattern(pattern, ": (foo)").finds("foo");
    assertPattern(pattern, ": foo (bar)").finds("bar");
    assertPattern(pattern, ": foo (bar) : or (zoo)").finds("bar", "zoo");
    assertPattern(pattern, ": foo (bar) or :(zoo)").finds("bar", "zoo");
  }

  @Test
  public void immediatelyBetween_inclusive_empty(
      @TestParameter BoundStyle leftBound, @TestParameter BoundStyle rightBound) {
    Substring.Pattern pattern = first("foo").immediatelyBetween("", leftBound, "", rightBound);
    assertPattern(pattern, "fo").findsNothing();
    assertPattern(pattern, "foo bar foo").finds("foo", "foo");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void immediatelyBetween_inclusive_patternNotFound(
      @TestParameter BoundStyle leftBound, @TestParameter BoundStyle rightBound) {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", leftBound, "->", rightBound);
    assertPattern(pattern, "").findsNothing();
    assertPattern(pattern, "<-").findsNothing();
    assertPattern(pattern, "<->").findsNothing();
    assertPattern(pattern, "<-->").findsNothing();
    assertPattern(pattern, "<-fo->").findsNothing();
    assertPattern(pattern, "<-fo->").findsNothing();
  }

  @Test
  public void immediatelyBetween_inclusive_lookbehindAbsent(
      @TestParameter BoundStyle leftBound, @TestParameter BoundStyle rightBound) {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", leftBound, "->", rightBound);
    assertPattern(pattern, "<!-foo->").findsNothing();
    assertPattern(pattern, "<-!foo->").findsNothing();
    assertPattern(pattern, "foo->").findsNothing();
  }

  @Test
  public void immediatelyBetween_inclusive_lookaheadAbsent(
      @TestParameter BoundStyle leftBound, @TestParameter BoundStyle rightBound) {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", leftBound, "->", rightBound);
    assertPattern(pattern, "<-foo>>").findsNothing();
    assertPattern(pattern, "<-foo>->").findsNothing();
    assertPattern(pattern, "<-foo").findsNothing();
    assertPattern(pattern, "foo").findsNothing();
  }

  @Test
  public void immediatelyBetween_fullExclusive_found() {
    Substring.Pattern pattern =
        Substring.word().immediatelyBetween("<-", EXCLUSIVE, "->", EXCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("foo");
    assertPattern(pattern, "<-foo-> <-bar->").finds("foo", "bar");
  }

  @Test
  public void immediatelyBetween_leftInclusive_found() {
    Substring.Pattern pattern =
        Substring.word().immediatelyBetween("<-", INCLUSIVE, "->", EXCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("<-foo");
    assertPattern(pattern, "<-foo-> <-bar->").finds("<-foo", "<-bar");
  }

  @Test
  public void immediatelyBetween_rightInclusive_found() {
    Substring.Pattern pattern =
        Substring.word().immediatelyBetween("<-", EXCLUSIVE, "->", INCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("foo->");
    assertPattern(pattern, "<-foo-> <-bar->").finds("foo->", "bar->");
  }

  @Test
  public void immediatelyBetween_fullInclusive_found() {
    Substring.Pattern pattern =
        Substring.word().immediatelyBetween("<-", INCLUSIVE, "->", INCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("<-foo->");
    assertPattern(pattern, "<-foo-> <-bar->").finds("<-foo->", "<-bar->");
  }

  @Test
  public void firstString_immediatelyBetween_fullExclusive_found() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", EXCLUSIVE, "->", EXCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("foo");
    assertPattern(pattern, "<-foo-> <-foo-> <-foo- -foo->").finds("foo", "foo");
  }

  @Test
  public void firstString_immediatelyBetween_leftInclusive_found() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", INCLUSIVE, "->", EXCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("<-foo");
    assertPattern(pattern, "<-foo-> <-foo-> <-foo- -foo->").finds("<-foo", "<-foo");
  }

  @Test
  public void firstString_immediatelyBetween_rightInclusive_found() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", EXCLUSIVE, "->", INCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("foo->");
    assertPattern(pattern, "<-foo-> <-foo-> <-foo- -foo->").finds("foo->", "foo->");
  }

  @Test
  public void firstString_immediatelyBetween_fullInclusive_found() {
    Substring.Pattern pattern = first("foo").immediatelyBetween("<-", INCLUSIVE, "->", INCLUSIVE);
    assertPattern(pattern, "<-foo->").finds("<-foo->");
    assertPattern(pattern, "<-foo-> <-foo-> <-foo- -foo->").finds("<-foo->", "<-foo->");
  }

  @Test
  public void firstChar_immediatelyBetween_fullExclusive_found() {
    Substring.Pattern pattern = first('.').immediatelyBetween("<-", EXCLUSIVE, "->", EXCLUSIVE);
    assertPattern(pattern, "<-.->").finds(".");
    assertPattern(pattern, "<-.-> <-.-> <-.- -.->").finds(".", ".");
  }

  @Test
  public void firstChar_immediatelyBetween_leftInclusive_found() {
    Substring.Pattern pattern = first('.').immediatelyBetween("<-", INCLUSIVE, "->", EXCLUSIVE);
    assertPattern(pattern, "<-.->").finds("<-.");
    assertPattern(pattern, "<-.-> <-.-> <-.- -.->").finds("<-.", "<-.");
  }

  @Test
  public void firstChar_immediatelyBetween_rightInclusive_found() {
    Substring.Pattern pattern = first('.').immediatelyBetween("<-", EXCLUSIVE, "->", INCLUSIVE);
    assertPattern(pattern, "<-.->").finds(".->");
    assertPattern(pattern, "<-.-> <-.-> <-.- -.->").finds(".->", ".->");
  }

  @Test
  public void firstChar_immediatelyBetween_fullInclusive_found() {
    Substring.Pattern pattern = first('.').immediatelyBetween("<-", INCLUSIVE, "->", INCLUSIVE);
    assertPattern(pattern, "<-.->").finds("<-.->");
    assertPattern(pattern, "<-.-> <-.-> <-.- -.->").finds("<-.->", "<-.->");
  }

  @Test
  public void immediatelyBetween_fullExclusive_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern =
        Substring.between("(", ")").immediatelyBetween("[(", EXCLUSIVE, ")]", EXCLUSIVE);
    assertPattern(pattern, "[(foo)]").finds("foo");
    assertPattern(pattern, "[(foo)] [(bar)]").finds("foo", "bar");
  }

  @Test
  public void immediatelyBetween_leftInclusive_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern =
        Substring.between("(", ")").immediatelyBetween("[(", INCLUSIVE, ")]", EXCLUSIVE);
    assertPattern(pattern, "[(foo)]").finds("[(foo");
    assertPattern(pattern, "[(foo)] [(bar)]").finds("[(foo", "[(bar");
  }

  @Test
  public void immediatelyBetween_rightInclusive_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern =
        Substring.between("(", ")").immediatelyBetween("[(", EXCLUSIVE, ")]", INCLUSIVE);
    assertPattern(pattern, "[(foo)]").finds("foo)]");
    assertPattern(pattern, "[(foo)] [(bar)]").finds("foo)]", "bar)]");
  }

  @Test
  public void immediatelyBetween_fullInclusive_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern =
        Substring.between("(", ")").immediatelyBetween("[(", INCLUSIVE, ")]", INCLUSIVE);
    assertPattern(pattern, "[(foo)]").finds("[(foo)]");
    assertPattern(pattern, "[(foo)] [(bar)]").finds("[(foo)]", "[(bar)]");
  }

  @Test
  public void immediatelyBetween_fullExclusive_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern =
        Substring.between("oo", "cc").immediatelyBetween("ooo", EXCLUSIVE, "ccc", EXCLUSIVE);
    assertPattern(pattern, "oofooccooobarccc").finds("bar");
  }

  @Test
  public void immediatelyBetween_leftInclusive_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern =
        Substring.between("oo", "cc").immediatelyBetween("ooo", INCLUSIVE, "ccc", EXCLUSIVE);
    assertPattern(pattern, "oofooccooobarccc").finds("ooobar");
  }

  @Test
  public void immediatelyBetween_rightInclusive_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern =
        Substring.between("oo", "cc").immediatelyBetween("ooo", EXCLUSIVE, "ccc", INCLUSIVE);
    assertPattern(pattern, "oofooccooobarccc").finds("barccc");
  }

  @Test
  public void immediatelyBetween_fullInclusive_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern =
        Substring.between("oo", "cc").immediatelyBetween("ooo", INCLUSIVE, "ccc", INCLUSIVE);
    assertPattern(pattern, "oofooccooobarccc").finds("ooobarccc");
  }

  @Test
  public void immediatelyBetween_fullExclusive_repetitionStartsFromLookahead() {
    Substring.Pattern pattern =
        Substring.first("bar").immediatelyBetween("of", EXCLUSIVE, "o", EXCLUSIVE);
    assertPattern(pattern, "ofbarofbaro").finds("bar", "bar");
  }

  @Test
  public void immediatelyBetween_leftInclusive_repetitionStartsFromLookahead() {
    Substring.Pattern pattern =
        Substring.first("bar").immediatelyBetween("of", INCLUSIVE, "o", EXCLUSIVE);
    assertPattern(pattern, "ofbarofbaro").finds("ofbar", "ofbar");
  }

  @Test
  public void immediatelyBetween_rightInclusive_repetitionStartsFromLookahead() {
    Substring.Pattern pattern =
        Substring.first("bar").immediatelyBetween("of", EXCLUSIVE, "o", INCLUSIVE);
    assertPattern(pattern, "ofbarofbaro").finds("baro");
    assertPattern(pattern, "ofbarofbarofbarofbaro").finds("baro", "baro");
  }

  @Test
  public void immediatelyBetween_fullInclusive_repetitionStartsFromLookahead() {
    Substring.Pattern pattern =
        Substring.first("bar").immediatelyBetween("of", INCLUSIVE, "o", INCLUSIVE);
    assertPattern(pattern, "ofbarofbaro").finds("ofbaro");
    assertPattern(pattern, "ofbarofbarofbarofbaro").finds("ofbaro", "ofbaro");
  }

  @Test
  public void immediatelyBetween_fullExclusive_thenBacktracks() {
    Substring.Pattern pattern =
        Substring.first(':')
            .then(Substring.word())
            .immediatelyBetween("(", EXCLUSIVE, ")", EXCLUSIVE);
    assertPattern(pattern, ": (foo)").finds("foo");
    assertPattern(pattern, ": foo (bar)").finds("bar");
    assertPattern(pattern, ": foo (bar) : or (zoo)").finds("bar", "zoo");
    assertPattern(pattern, ": foo (bar) or :(zoo)").finds("bar", "zoo");
  }

  @Test
  public void immediatelyBetween_leftInclusive_thenBacktracks() {
    Substring.Pattern pattern =
        Substring.first(':')
            .then(Substring.word())
            .immediatelyBetween("(", INCLUSIVE, ")", EXCLUSIVE);
    assertPattern(pattern, ": (foo)").finds("(foo");
    assertPattern(pattern, ": foo (bar)").finds("(bar");
    assertPattern(pattern, ": foo (bar) : or (zoo)").finds("(bar", "(zoo");
    assertPattern(pattern, ": foo (bar) or :(zoo)").finds("(bar", "(zoo");
  }

  @Test
  public void immediatelyBetween_rightInclusive_thenBacktracks() {
    Substring.Pattern pattern =
        Substring.first(':')
            .then(Substring.word())
            .immediatelyBetween("(", EXCLUSIVE, ")", INCLUSIVE);
    assertPattern(pattern, ": (foo)").finds("foo)");
    assertPattern(pattern, ": foo (bar)").finds("bar)");
    assertPattern(pattern, ": foo (bar) : or (zoo)").finds("bar)", "zoo)");
    assertPattern(pattern, ": foo (bar) or :(zoo)").finds("bar)", "zoo)");
  }

  @Test
  public void immediatelyBetween_fullInclusive_thenBacktracks() {
    Substring.Pattern pattern =
        Substring.first(':')
            .then(Substring.word())
            .immediatelyBetween("(", INCLUSIVE, ")", INCLUSIVE);
    assertPattern(pattern, ": (foo)").finds("(foo)");
    assertPattern(pattern, ": foo (bar)").finds("(bar)");
    assertPattern(pattern, ": foo (bar) : or (zoo)").finds("(bar)", "(zoo)");
    assertPattern(pattern, ": foo (bar) or :(zoo)").finds("(bar)", "(zoo)");
  }

  @Test
  public void notFollowedBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").notFollowedBy("...");
    assertPattern(pattern, "").findsNothing();
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void notFollowedBy_lookaheadAbsent() {
    Substring.Pattern pattern = first("foo").notFollowedBy("...");
    assertPattern(pattern, "foo...").findsNothing();
    assertPattern(pattern, "foo....").findsNothing();
  }

  @Test
  public void notFollowedBy_found() {
    Substring.Pattern pattern = first("foo").notFollowedBy("...");
    assertPattern(pattern, "foo").finds("foo");
    assertPattern(pattern, "foo.").finds("foo");
    assertPattern(pattern, "foo..").finds("foo");
    assertPattern(pattern, "foo.barfoo..").finds("foo", "foo");
  }

  @Test
  public void notFollowedBy_backtracking() {
    Substring.Pattern pattern = Substring.first("--").notFollowedBy("->");
    assertPattern(pattern, "<---->--").finds("--", "--", "--");
  }

  @Test
  public void notFollowedBy_prefixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.prefix("--").notFollowedBy("->");
    assertPattern(pattern, "--->--").findsNothing();
  }

  @Test
  public void notFollowedBy_beforeHasNoBacktracking() {
    Substring.Pattern pattern = Substring.before(first("--")).notFollowedBy("--->");
    assertPattern(pattern, "--->--").findsNothing();
  }

  @Test
  public void notFollowedBy_afterHasNoBacktracking() {
    Substring.Pattern pattern = Substring.after(first("--")).notFollowedBy("");
    assertPattern(pattern, "--->--").findsNothing();
  }

  @Test
  public void notFollowedBy_upToIncludingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.upToIncluding(first("--")).notFollowedBy("->");
    assertPattern(pattern, "--->--").findsNothing();
  }

  @Test
  public void notFollowedBy_leadingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.leading(CharPredicate.is('-')).notFollowedBy(">");
    assertPattern(pattern, "--->--").findsNothing();
  }

  @Test
  public void notPrecededBy_suffixHasNoBacktracking() {
    Substring.Pattern pattern = Substring.suffix("--").notImmediatelyBetween("<-", "");
    assertPattern(pattern, "--<---").findsNothing();
  }

  @Test
  public void notPrecededBy_trailingHasNoBacktracking() {
    Substring.Pattern pattern = Substring.trailing(CharPredicate.is('-')).notImmediatelyBetween("<", "");
    assertPattern(pattern, "--<---").findsNothing();
  }

  @Test
  public void notPrecededBy_toEndHasNoBacktracking() {
    Substring.Pattern pattern = first("--").toEnd().notImmediatelyBetween("<", "");
    assertPattern(pattern, "<--(---->").findsNothing();
  }

  @Test
  public void notPrecededBy_patternNotFound() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("...", "");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void notPrecededBy_lookbehindAbsent() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("...", "");
    assertPattern(pattern, "...foo").findsNothing();
    assertPattern(pattern, "....foo").findsNothing();
    assertPattern(pattern, "...foo").findsNothing();
  }

  @Test
  public void notPrecededBy_found() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("...", "");
    assertPattern(pattern, "..foo").finds("foo");
    assertPattern(pattern, "bar..foo bar.foo").finds("foo", "foo");
  }

  @Test
  public void notImmediatelyBetween_empty() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("", "");
    assertPattern(pattern, "foo").findsNothing();
    assertPattern(pattern, " foo ").findsNothing();
    assertPattern(pattern, "foo").findsNothing();
  }

  @Test
  public void notImmediatelyBetween_patternNotFound() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<fo>").findsNothing();
  }

  @Test
  public void notImmediatelyBetween_lookbehindAbsent() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").findsNothing();
  }

  @Test
  public void notImmediatelyBetween_lookaheadAbsent() {
    Substring.Pattern pattern = first("foo").notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").findsNothing();
  }

  @Test
  public void notImmediatelyBetween_bothLookbehindAndLookaheadPresent() {
    Substring.Pattern pattern = Substring.word().notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo-->").finds("foo");
    assertPattern(pattern, "<-foo--> bar").finds("foo", "bar");
  }

  @Test
  public void notImmediatelyBetween_neitherLookbehindNorLookaheadPresent() {
    Substring.Pattern pattern = Substring.word().notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<<foo>").finds("foo");
    assertPattern(pattern, "<--foo-->").finds("foo");
    assertPattern(pattern, "foo").finds("foo");
    assertPattern(pattern, "<<foo>> bar").finds("foo", "bar");
  }

  @Test
  public void notImmediatelyBetween_betweenBacktrackingStartsFromDelimiter() {
    Substring.Pattern pattern = Substring.between("(", ")").notImmediatelyBetween("[(", ")]");
    assertPattern(pattern, "{(foo)}").finds("foo");
    assertPattern(pattern, "{(foo)} {(bar)}").finds("foo", "bar");
  }

  @Test
  public void notImmediatelyBetween_backtrackingAtOpeningDelimiter() {
    Substring.Pattern pattern = Substring.between("oa", "ac").notImmediatelyBetween("ooa", "acc");
    assertPattern(pattern, "ooafooaccoabarac").finds("ccoabar");
  }

  @Test
  public void notImmediatelyBetween_thenBacktracks() {
    Substring.Pattern pattern = Substring.first(':').then(Substring.word()).notImmediatelyBetween("((", "))");
    assertPattern(pattern, ": foo").finds("foo");
    assertPattern(pattern, ": ((foo)) [bar]").finds("bar");
    assertPattern(pattern, ": ((foo)) bar : <zoo>").finds("bar", "zoo");
    assertPattern(pattern, ": ((foo)) bar :(zoo)").finds("bar", "zoo");
  }

  @Test
  public void lastEmpty_separatedBy_separatorNotFound() {
    assertPattern(Substring.last("").separatedBy(Character::isWhitespace), "/").findsNothing();
  }

  @Test
  public void lastEmpty_separatedBy_separatorFound() {
    assertPattern(Substring.last("").separatedBy(Character::isWhitespace), "  ").finds("");
    assertPattern(Substring.last("").separatedBy(Character::isWhitespace), "/ ").finds("");
    assertPattern(Substring.last("").separatedBy(Character::isWhitespace), " /").finds("");
  }

  @Test
  public void lastEmpty_separatedBy_allEmpty() {
    assertPattern(Substring.last("").separatedBy(Character::isWhitespace), "").finds("");
  }

  @Test
  public void last_separatedBy_patternNotFound() {
    Substring.Pattern pattern = Substring.last("foo").separatedBy(CharPredicate.is('('), CharPredicate.is(')'));
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void last_separatedBy_patternFoundButNotSeparatedBySeparator() {
    Substring.Pattern pattern = Substring.last("foo").separatedBy(Character::isWhitespace);
    assertPattern(pattern, "(foo)").findsNothing();
  }

  @Test
  public void last_separatedBy_found() {
    Substring.Pattern pattern = Substring.last("foo").separatedBy(Character::isWhitespace);
    assertPattern(pattern, "foo (foo)").findsBetween("", " (foo)");
  }

  @Test
  public void lastChar_separatedBy_patternNotFound() {
    Substring.Pattern pattern = Substring.last('?').separatedBy(CharPredicate.is('('), CharPredicate.is(')'));
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void lastChar_separatedBy_patternFoundButNotSeparatedBySeparator() {
    Substring.Pattern pattern = Substring.last('?').separatedBy(Character::isWhitespace);
    assertPattern(pattern, "(?)").findsNothing();
  }

  @Test
  public void lastChar_separatedBy_found() {
    assertPattern(Substring.last('?').separatedBy(Character::isWhitespace), "? (?)").findsBetween("", " (?)");
    assertPattern(Substring.last('?').separatedBy(Character::isWhitespace), " ? (?)").findsBetween(" ", " (?)");
  }

  @Test
  public void lastCharMatcher_separatedBy_patternNotFound() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).separatedBy(CharPredicate.is('('), CharPredicate.is(')'));
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void lastCharMatcher_separatedBy_patternFoundButNotSeparatedBySeparator() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).separatedBy(Character::isWhitespace);
    assertPattern(pattern, "(?)").findsNothing();
  }

  @Test
  public void lastCharMatcher_separatedBy_found() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).separatedBy(Character::isWhitespace);
    assertPattern(pattern, "? (?)").findsBetween("", " (?)");
  }

  @Test
  public void last_followedBy_patternNotFound() {
    Substring.Pattern pattern = Substring.last("foo").followedBy(":");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void last_followedBy_patternFoundButLookaheadNotFound() {
    Substring.Pattern pattern = Substring.last("foo").followedBy(":");
    assertPattern(pattern, "foo ").findsNothing();
  }

  @Test
  public void last_followedBy_found() {
    assertPattern(Substring.last("foo").followedBy(":"), "foo: foo").findsBetween("", ": foo");
    assertPattern(Substring.last("a").followedBy("ab"), "aaab").findsBetween("a", "ab");
  }

  @Test
  public void last_immediatelyBetween_patternNotFound() {
    Substring.Pattern pattern = Substring.last("foo").immediatelyBetween("(", ")");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void last_immediatelyBetween_patternFoundButLookbehindNotFound() {
    Substring.Pattern pattern = Substring.last("foo").immediatelyBetween("(", ")");
    assertPattern(pattern, "foo)").findsNothing();
  }

  @Test
  public void last_immediatelyBetween_found() {
    Substring.Pattern pattern = Substring.last("foo").immediatelyBetween("(", ")");
    assertPattern(pattern, "(foo) (foo").findsBetween("(", ") (foo");
  }

  @Test
  public void lastChar_followedBy_patternNotfound() {
    Substring.Pattern pattern = Substring.last('?').followedBy(":");
    assertPattern(pattern, ":").findsNothing();
  }

  @Test
  public void lastChar_followedBy_lookaheadNotfound() {
    Substring.Pattern pattern = Substring.last('?').followedBy(":");
    assertPattern(pattern, "? :").findsNothing();
  }

  @Test
  public void lastChar_followedBy_found() {
    Substring.Pattern pattern = Substring.last('?').followedBy(":");
    assertPattern(pattern, "?:?").findsBetween("", ":?");
  }

  @Test
  public void lastChar_immediatelyBetween_patternNotFound() {
    Substring.Pattern pattern = Substring.last('?').immediatelyBetween("(", ")");
    assertPattern(pattern, "?)").findsNothing();
  }

  @Test
  public void lastChar_immediatelyBetween_lookbehindNotFound() {
    Substring.Pattern pattern = Substring.last('?').immediatelyBetween("(", ")");
    assertPattern(pattern, "(/)").findsNothing();
  }

  @Test
  public void lastChar_immediatelyBetween_found() {
    Substring.Pattern pattern = Substring.last('?').immediatelyBetween("(", ")");
    assertPattern(pattern, "(?):?").findsBetween("(", "):?");
  }

  @Test
  public void lastCharMatcher_followedBy_patternNotfound() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).followedBy(":");
    assertPattern(pattern, ":").findsNothing();
  }

  @Test
  public void lastCharMatcher_followedBy_lookaheadNotfound() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).followedBy(":");
    assertPattern(pattern, "? :").findsNothing();
  }

  @Test
  public void lastCharMatcher_followedBy_found() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).followedBy(":");
    assertPattern(pattern, "?:?").findsBetween("", ":?");
  }

  @Test
  public void lastCharMatcher_immediatelyBetween_patternNotFound() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).immediatelyBetween("(", ")");
    assertPattern(pattern, "?)").findsNothing();
  }

  @Test
  public void lastCharMatcher_immediatelyBetween_lookbehindNotFound() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).immediatelyBetween("(", ")");
    assertPattern(pattern, "(/)").findsNothing();
  }

  @Test
  public void lastCharMatcher_immediatelyBetween_found() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).immediatelyBetween("(", ")");
    assertPattern(pattern, "(?):?").findsBetween("(", "):?");
  }

  @Test
  public void last_notFollowedBy_patternNotFound() {
    Substring.Pattern pattern = Substring.last("foo").notFollowedBy(":");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void last_notFollowedBy_patternFoundButLookaheadFollows() {
    Substring.Pattern pattern = Substring.last("foo").notFollowedBy(":");
    assertPattern(pattern, "foo:").findsNothing();
  }

  @Test
  public void last_notFollowedBy_found() {
    Substring.Pattern pattern = Substring.last("foo").notFollowedBy(":");
    assertPattern(pattern, "foo foo:").findsBetween("", " foo:");
  }

  @Test
  public void last_notImmediatelyBetween_patternNotFound() {
    Substring.Pattern pattern = Substring.last("foo").notImmediatelyBetween("(", ")");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void last_notImmediatelyBetween_patternFoundButLookaroundPresent() {
    Substring.Pattern pattern = Substring.last("foo").notImmediatelyBetween("(", ")");
    assertPattern(pattern, "(foo)").findsNothing();
  }

  @Test
  public void last_notImmediatelyBetween_found() {
    Substring.Pattern pattern = Substring.last("foo").notImmediatelyBetween("(", ")");
    assertPattern(pattern, "foo) (foo)").findsBetween("", ") (foo)");
  }

  @Test
  public void lastChar_notFollowedBy_patternNotfound() {
    Substring.Pattern pattern = Substring.last('?').notFollowedBy(":");
    assertPattern(pattern, "x").findsNothing();
  }

  @Test
  public void lastChar_notFollowedBy_lookaheadPresent() {
    Substring.Pattern pattern = Substring.last('?').notFollowedBy(":");
    assertPattern(pattern, "?:").findsNothing();
  }

  @Test
  public void lastChar_notFollowedBy_found() {
    Substring.Pattern pattern = Substring.last('?').notFollowedBy(":");
    assertPattern(pattern, "?/?:").findsBetween("", "/?:");
  }

  @Test
  public void lastChar_notImmediatelyBetween_patternNotFound() {
    Substring.Pattern pattern = Substring.last('?').notImmediatelyBetween("(", ")");
    assertPattern(pattern, "(x)").findsNothing();
  }

  @Test
  public void lastChar_notImmediatelyBetween_lookaroundPresent() {
    Substring.Pattern pattern = Substring.last('?').notImmediatelyBetween("(", ")");
    assertPattern(pattern, "(?)").findsNothing();
  }

  @Test
  public void lastChar_notImmediatelyBetween_found() {
    Substring.Pattern pattern = Substring.last('?').notImmediatelyBetween("(", ")");
    assertPattern(pattern, "(?:(?)").findsBetween("(", ":(?)");
  }

  @Test
  public void lastCharMatcher_notFollowedBy_patternNotFound() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).notFollowedBy(":");
    assertPattern(pattern, "/").findsNothing();
  }

  @Test
  public void lastCharMatcher_notFollowedBy_lookaheadPresent() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).notFollowedBy(":");
    assertPattern(pattern, "?:").findsNothing();
  }

  @Test
  public void lastCharMatcher_notFollowedBy_found() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).notFollowedBy(":");
    assertPattern(pattern, "? ?:").findsBetween("", " ?:");
  }

  @Test
  public void lastCharMatcher_notImmediatelyBetween_patternNotFound() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).notImmediatelyBetween("(", ")");
    assertPattern(pattern, "x").findsNothing();
  }

  @Test
  public void lastCharMatcher_notImmediatelyBetween_lookaroundPresent() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).notImmediatelyBetween("(", ")");
    assertPattern(pattern, "(?)").findsNothing();
  }

  @Test
  public void lastCharMatcher_notImmediatelyBetween_found() {
    Substring.Pattern pattern = Substring.last(CharPredicate.is('?')).notImmediatelyBetween("(", ")");
    assertPattern(pattern, "?:(?)").findsBetween("", ":(?)");
  }

  @Test
  public void regex_followedBy_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).followedBy("...");
    assertPattern(pattern, "foo").findsNothing();
    assertPattern(pattern, "foo..").findsNothing();
  }

  @Test
  public void regex_followedBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).followedBy("...");
    assertPattern(pattern, "foo...").finds("foo");
    assertPattern(pattern, "foo...bar zoo...").finds("foo", "zoo");
  }

  @Test
  public void regex_followedBy_backtracking() {
    Substring.Pattern pattern = Substring.first(Pattern.compile("--")).followedBy("->");
    assertPattern(pattern, "<---->").finds("--");
  }

  @Test
  public void regex_notFollowedBy_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notFollowedBy("...");
    assertPattern(pattern, "foo...").finds("fo");
    assertPattern(pattern, "foo....bar").finds("fo", "bar");
  }

  @Test
  public void regex_notFollowedBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notFollowedBy("...");
    assertPattern(pattern, "foo").finds("foo");
    assertPattern(pattern, "foo.").finds("foo");
    assertPattern(pattern, "foo..").finds("foo");
    assertPattern(pattern, "foo.barfoo..").finds("foo", "barfoo");
  }

  @Test
  public void regex_notFollowedBy_backtracking() {
    Substring.Pattern pattern = Substring.first(Pattern.compile("--")).notFollowedBy("->");
    assertPattern(pattern, "<---->--").finds("--", "--", "--");
  }

  @Test
  public void regex_precededBy_patternNotFound() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).immediatelyBetween("...", "");
    assertPattern(pattern, "...bar").findsNothing();
  }

  @Test
  public void regex_precededBy_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("...", "");
    assertPattern(pattern, "..foo").findsNothing();
    assertPattern(pattern, "foo").findsNothing();
    assertPattern(pattern, "..foo").findsNothing();
  }

  @Test
  public void regex_precededBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("...", "");
    assertPattern(pattern, "...foo").finds("foo");
    assertPattern(pattern, "bar...foo bar...foo").finds("foo", "foo");
  }

  @Test
  public void regex_notPrecededBy_patternNotFound() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notImmediatelyBetween("...", "");
    assertPattern(pattern, "bar").findsNothing();
  }

  @Test
  public void regex_notPrecededBy_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notImmediatelyBetween("...", "");
    assertPattern(pattern, "...f").findsNothing();
    assertPattern(pattern, "....f").findsNothing();
    assertPattern(pattern, "...f").findsNothing();
  }

  @Test
  public void regex_notPrecededBy_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notImmediatelyBetween("...", "");
    assertPattern(pattern, "..foo").finds("foo");
    assertPattern(pattern, "bar..foo bar...foo").finds("bar", "foo", "bar", "oo");
  }

  @Test
  public void regex_between_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("<-", "->");
    assertPattern(pattern, "<!-foo->").findsNothing();
    assertPattern(pattern, "<-!foo->").findsNothing();
    assertPattern(pattern, "foo->").findsNothing();
  }

  @Test
  public void regex_between_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo>>").findsNothing();
    assertPattern(pattern, "<-foo>->").findsNothing();
    assertPattern(pattern, "<-foo").findsNothing();
    assertPattern(pattern, "foo").findsNothing();
  }

  @Test
  public void regex_between_found() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").finds("foo");
    assertPattern(pattern, "<-foo-> <-bar->").finds("foo", "bar");
  }

  @Test
  public void regex_between_reluctance() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("a", "b");
    assertPattern(pattern, "afoob").finds("foo");
  }

  @Test
  public void regex_between_empty() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).immediatelyBetween("", "");
    assertPattern(pattern, "<-foo->").finds("foo");
    assertPattern(pattern, "<-foo-> <-bar->").finds("foo", "bar");
  }

  @Test
  public void regex_notImmediatelyBetween_lookbehindAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").findsNothing();
  }

  @Test
  public void regex_notImmediatelyBetween_lookaheadAbsent() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").findsNothing();
  }

  @Test
  public void regex_notImmediatelyBetween_bothLookbehindAndLookaheadPresent() {
    Substring.Pattern pattern = first(Pattern.compile("foo")).notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<-foo->").findsNothing();
  }

  @Test
  public void regex_notImmediatelyBetween_neitherLookbehindNorLookaheadPresent() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notImmediatelyBetween("<-", "->");
    assertPattern(pattern, "<<foo>>").finds("foo");
    assertPattern(pattern, "foo").finds("foo");
    assertPattern(pattern, "<-food->").finds("ood");
    assertPattern(pattern, "foo").finds("foo");
    assertPattern(pattern, "<<foo>> bar").finds("foo", "bar");
  }

  @Test
  public void regex_notImmediatelyBetween_empty() {
    Substring.Pattern pattern = first(Pattern.compile("\\w+")).notImmediatelyBetween("", "");
    assertPattern(pattern, "<foo>").findsNothing();
    assertPattern(pattern, "").findsNothing();
    assertPattern(pattern, "foo").findsNothing();
  }

  @Test
  public void firstOccurrence_noPattern() {
    Substring.Pattern pattern = Stream.<Substring.Pattern>empty().collect(firstOccurrence());
    assertPattern(pattern, "string").findsNothing();
  }

  @Test
  public void firstOccurrence_singlePattern_noMatch() {
    Substring.Pattern pattern = Stream.of(first("foo")).collect(firstOccurrence());
    assertPattern(pattern, "string").findsNothing();
  }

  @Test
  public void firstOccurrence_singlePattern_match() {
    Substring.Pattern pattern = Stream.of(Substring.word()).collect(firstOccurrence());
    assertPattern(pattern, "foo bar").finds("foo", "bar");
  }

  @Test
  public void firstOccurrence_twoPatterns_noneMatch() {
    Substring.Pattern pattern = Stream.of(first("foo"), first("bar")).collect(firstOccurrence());
    assertPattern(pattern, "what").findsNothing();
  }

  @Test
  public void firstOccurrence_twoPatterns_firstMatches() {
    Substring.Pattern pattern = Stream.of(first("foo"), first("bar")).collect(firstOccurrence());
    assertPattern(pattern, "foo bar").finds("foo", "bar");
  }

  @Test
  public void firstOccurrence_twoPatterns_secondMatches() {
    Substring.Pattern pattern = Stream.of(first("foo"), first("bar")).collect(firstOccurrence());
    assertPattern(pattern, "bar foo").finds("bar", "foo");
  }

  @Test
  public void firstOccurrence_threePatterns_noneMatch() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertPattern(pattern, "what").findsNothing();
  }

  @Test
  public void firstOccurrence_threePatterns_firstMatches() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertPattern(pattern, "foo zoo bar").finds("foo", "zoo", "bar");
  }

  @Test
  public void firstOccurrence_threePatterns_secondMatches() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertPattern(pattern, "bar zoo foo").finds("bar", "zoo", "foo");
  }

  @Test
  public void firstOccurrence_threePatterns_thirdMatches() {
    Substring.Pattern pattern =
        Stream.of(first("foo"), first("bar"), first("zoo")).collect(firstOccurrence());
    assertPattern(pattern, "zoo bar foo").finds("zoo", "bar", "foo");
  }

  @Test
  public void firstOccurrence_overlappingCandidatePatterns() {
    Substring.Pattern pattern =
        Stream.of("oop", "foo", "op", "pool", "load", "oad")
            .map(Substring::first)
            .collect(firstOccurrence());
    assertPattern(pattern, "foopooload").finds("foo", "pool", "oad");
    assertPattern(pattern, Strings.repeat("foopooload", 10))
        .findsDistinct("foo", "pool", "oad");
  }

  @Test
  public void firstOccurrence_beforePatternRepetitionIndexRespected() {
    assume().that(variant).isEqualTo(SubstringPatternVariant.AS_IS);
    Substring.Pattern pattern =
        Stream.of(first("foo"), before(first("/")), first('/'), first("zoo"))
            .collect(firstOccurrence());
    assertPattern(pattern, "food/bar/baz/zoo")
        .finds("foo", "d", "/", "bar", "/", "baz", "/", "zoo");
  }

  @Test
  public void firstOccurrence_beforePatternWithMoreThanOneCharacters() {
    assume().that(variant).isEqualTo(SubstringPatternVariant.AS_IS);
    Substring.Pattern pattern =
        Stream.of(before(first("//")), first("//")).collect(firstOccurrence());
    assertPattern(pattern, "foo//bar//baz//zoo").finds("foo", "//", "bar", "//", "baz", "//");
  }

  @Test
  public void firstOccurrence_beforePatternRepetition() {
    Substring.Pattern pattern = Stream.of(before(first("//"))).collect(firstOccurrence());
    assertPattern(pattern, "foo//bar//baz//zoo").finds("foo", "bar", "baz");
  }

  @Test
  public void firstOccurrence_zeroLimitPatternInterleavedWithZeroLimitPattern() {
    assume().that(variant).isEqualTo(SubstringPatternVariant.AS_IS);
    Substring.Pattern pattern =
        Stream.of(first("kook").limit(0), first("ok").limit(0)).collect(firstOccurrence());
    assertPattern(pattern, "okookook").finds("", "", "", "");
    assertPattern(pattern, "kookooko").finds("", "", "");
  }

  @Test
  public void firstOccurrence_limitPatternInterleavedWithLimitPattern() {
    assume().that(variant).isEqualTo(SubstringPatternVariant.AS_IS);
    Substring.Pattern pattern =
        Stream.of(first("koook").limit(3), first("ok").limit(1)).collect(firstOccurrence());
    assertPattern(pattern, "okoookoook").finds("o", "koo", "o", "o");
    assertPattern(pattern, "koookoooko").finds("koo", "o", "o");
  }

  @Test
  public void firstOccurrenceThenLimit_interleaved() {
    Substring.Pattern pattern =
        Stream.of(first("koook"), first("ok")).collect(firstOccurrence()).limit(3);
    assertPattern(pattern, "koookoook").finds("koo", "ok");
  }

  @Test
  public void firstOccurrence_beforePatternInterleavedWithBeforePattern() {
    assume().that(variant).isEqualTo(SubstringPatternVariant.AS_IS);
    Substring.Pattern pattern =
        Stream.of(before(first("/")), before(first("//"))).collect(firstOccurrence());
    assertPattern(pattern, "foo//bar//baz//zoo").finds("foo", "", "", "bar", "", "", "baz", "", "");
  }

  @Test
  public void firstOccurrence_breaksTieByCandidatePatternOrder() {
    Substring.Pattern pattern =
        Stream.of("foo", "food", "dog", "f", "fo", "d", "do")
            .map(Substring::first)
            .collect(firstOccurrence());
    assertPattern(pattern, "foodog").finds("foo", "dog");
    assertPattern(pattern, repeat("foodog", 10)).findsDistinct("foo", "dog");
  }

  @Test
  public void firstOccurrence_word() {
    Substring.Pattern pattern =
        Stream.of("food", "dog", "f", "fo", "d", "do")
            .map(Substring::word)
            .collect(firstOccurrence());
    assertPattern(pattern, "foodog").findsNothing();
    assertPattern(pattern, "dog foo dog food catfood").finds("dog", "dog", "food");
  }

  @Test
  public void firstOccurrence_separatedBy() {
    Substring.Pattern pattern =
        Stream.of("food", "dog", "f", "fo", "d", "do")
            .map(Substring::first)
            .collect(firstOccurrence())
            .separatedBy(Character::isWhitespace);
    assertPattern(pattern, "foodog").findsNothing();
    assertPattern(pattern, "dog foo dog food catfood").finds("dog", "dog", "food");
  }

  @Test
  public void firstOccurrence_separatedBy_tieBrokenByBoundary() {
    Substring.Pattern pattern =
        Stream.of("foo", "food")
            .map(Substring::first)
            .collect(firstOccurrence())
            .separatedBy(Character::isWhitespace);
    assertPattern(pattern, "food").finds("food");
  }

  @Test
  public void firstOccurrence_between_tieBrokenByBoundary() {
    Substring.Pattern pattern =
        Stream.of("foo", "food").map(Substring::first).collect(firstOccurrence()).immediatelyBetween("(", ")");
    assertPattern(pattern, "(food)").finds("food");
  }

  @Test
  public void firstOccurrence_notImmediatelyBetween_tieBrokenByBoundary() {
    Substring.Pattern pattern =
        Stream.of("food", "foo")
            .map(Substring::first)
            .collect(firstOccurrence())
            .notImmediatelyBetween("(", ")");
    assertPattern(pattern, "(food)").finds("foo");
  }

  @Test
  public void firstOccurrence_peek_alternativeBackTrackingNotTriggeredByPeek() {
    Substring.Pattern pattern =
        Stream.of("foo", "ood").map(Substring::first).collect(firstOccurrence()).peek(prefix(" "));
    assertPattern(pattern, "food ").findsNothing();
    assertPattern(pattern, "food ").findsNothing();
  }

  @Test
  public void firstOccurrence_splitKeyValues_withFixedSetOfKeys_noReservedDelimiter() {
    Substring.Pattern delim =
        Stream.of("a", "artist", "playlist id", "foo bar")
            .map(k -> first(" " + k + ":"))
            .collect(firstOccurrence())
            .limit(1);
    String input = "playlist id:foo bar artist: another name a: my name:age";
    ImmutableMap<String, String> keyValues =
        variant.wrap(delim)
            .repeatedly()
            .splitThenTrimKeyValuesAround(first(':'), input)
            .collect(ImmutableMap::toImmutableMap);
    assertThat(keyValues)
        .containsExactly("playlist id", "foo bar", "artist", "another name", "a", "my name:age")
        .inOrder();
  }

  private SubstringPatternAssertion assertPattern(Substring.Pattern pattern, String input) {
    return new SubstringPatternAssertion(variant.wrap(pattern), input);
  }
}
