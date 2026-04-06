package com.google.common.labs.parse;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.labs.parse.CharacterSet.charsIn;
import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.bmpCodeUnit;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.caseInsensitiveWord;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.first;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.or;
import static com.google.common.labs.parse.Parser.quotedBy;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.labs.parse.Parser.zeroOrMoreDelimited;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.CharPredicate.ANY;
import static com.google.mu.util.CharPredicate.anyOf;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.isNot;
import static com.google.mu.util.CharPredicate.noneOf;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static java.util.stream.Stream.concat;
import static org.junit.Assert.assertThrows;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.labs.parse.Parser.ParseException;
import com.google.common.testing.NullPointerTester;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.mu.util.CharPredicate;

@RunWith(JUnit4.class)
public class ParserTest {
  private static final CharacterSet DIGIT = charsIn("[0-9]");

  @Test
  public void first_atBeginning_followedBy() {
    assertThat(first("foo").followedBy(string("bar")).parse("foobar")).isEqualTo("foo");
    assertThat(first("foo").followedBy(string("bar")).matches("foobar")).isTrue();
  }

  @Test
  public void first_severalCharsIn_followedBy() {
    assertThat(first("foo").followedBy(string("bar")).parse("skip foobar")).isEqualTo("foo");
    assertThat(first("foo").followedBy(string("bar")).matches("skip foobar")).isTrue();
  }

  @Test
  public void first_notFound() {
    ParseException thrown =
        assertThrows(
            ParseException.class, () -> first("skip").then(first("foo")).parse("skip fobar"));
    assertThat(first("skip").then(first("foo")).matches("skip fobar")).isFalse();
    assertThat(thrown).hasMessageThat().contains("1:5");
    assertThat(thrown).hasMessageThat().contains("expecting <foo>");
  }

  @Test
  public void first_withReader_targetInSecondPage() {
    CharInput input = CharInput.from(new StringReader("0123456789foo_"), 10, 5);
    assertThat(first("foo").followedBy("_").parseToStream(input, 0)).containsExactly("foo");
  }

  @Test
  public void first_withReader_targetInThirdPage() {
    CharInput input = CharInput.from(new StringReader("01234567890123456789foo_"), 10, 5);
    assertThat(first("foo").followedBy("_").parseToStream(input, 0)).containsExactly("foo");
  }

  @Test
  public void first_withReader_targetAcrossPageBoundary() {
    CharInput input = CharInput.from(new StringReader("0123456789012345678foobar_"), 10, 5);
    assertThat(first("foobar").followedBy("_").parseToStream(input, 0)).containsExactly("foobar");
  }

  @Test
  public void first_skippingWhitespace() {
    assertThat(first(" foo").skipping(whitespace()).parse("    foo")).isEqualTo(" foo");
    assertThat(first(" foo").skipping(whitespace()).matches("    foo")).isTrue();
  }

  @Test
  public void first_withReader_largeInput_notFound() {
    String page = "fo".repeat(8192);
    Reader reader = new StringReader(page + page + "bar");
    ParseException thrown =
        assertThrows(ParseException.class, () -> first("foo").parseToStream(reader).count());
    assertThat(thrown).hasMessageThat().contains("1:1");
    assertThat(thrown).hasMessageThat().contains("expecting <foo>");
  }

  @Test
  public void first_withReader_largeInput_foundNearEnd() {
    String page = "fo".repeat(8192);
    Reader reader = new StringReader(page + page + "foo");
    assertThat(first("foo").parseToStream(reader)).containsExactly("foo");
  }

  @Test
  public void first_emptyString_throws() {
    assertThrows(IllegalArgumentException.class, () -> first(""));
  }

  @Test
  public void string_success() {
    Parser<String> parser = string("foo");
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.matches("foo")).isTrue();
    assertThat(parser.parseToStream("foo")).containsExactly("foo");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void string_success_source() {
    Parser<String> parser = string("foo");
    assertThat(parser.source().parse("foo")).isEqualTo("foo");
    assertThat(parser.source().matches("foo")).isTrue();
    assertThat(parser.source().parseToStream("foo")).containsExactly("foo");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void string_failure_withLeftover() {
    assertThrows(ParseException.class, () -> string("foo").parse("fooa"));
    assertThat(string("foo").matches("fooa")).isFalse();
    assertThrows(ParseException.class, () -> string("foo").parseToStream("fooa").toList());
  }

  @Test
  public void string_failure() {
    assertThrows(ParseException.class, () -> string("foo").parse("fo"));
    assertThat(string("foo").matches("fo")).isFalse();
    assertThrows(ParseException.class, () -> string("foo").parseToStream("fo").toList());
    assertThrows(ParseException.class, () -> string("foo").parse("food"));
    assertThat(string("foo").matches("food")).isFalse();
    assertThrows(ParseException.class, () -> string("foo").parseToStream("food").toList());
    assertThrows(ParseException.class, () -> string("foo").parse("bar"));
    assertThat(string("foo").matches("bar")).isFalse();
    assertThrows(ParseException.class, () -> string("foo").parseToStream("bar").toList());
  }

  @Test
  public void string_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> string(""));
  }

  @Test
  public void caseInsensitive_success() {
    Parser<String> parser = caseInsensitive("foo").source();
    assertThat(parser.parse("FoO")).isEqualTo("FoO");
    assertThat(parser.matches("FoO")).isTrue();
    assertThat(parser.parseToStream("fOo")).containsExactly("fOo");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void caseInsensitive_success_source() {
    Parser<String> parser = caseInsensitive("foo").source();
    assertThat(parser.source().parse("FoO")).isEqualTo("FoO");
    assertThat(parser.source().matches("FoO")).isTrue();
    assertThat(parser.source().parseToStream("fOo")).containsExactly("fOo");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void caseInsensitive_failure_withLeftover() {
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parse("Fooa"));
    assertThat(caseInsensitive("foo").matches("Fooa")).isFalse();
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parseToStream("Fooa").toList());
  }

  @Test
  public void caseInsensitive_failure() {
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parse("fo"));
    assertThat(caseInsensitive("foo").matches("fo")).isFalse();
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parseToStream("fo").toList());
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parse("Food"));
    assertThat(caseInsensitive("foo").matches("Food")).isFalse();
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parseToStream("Food").toList());
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parse("bar"));
    assertThat(caseInsensitive("foo").matches("bar")).isFalse();
    assertThrows(ParseException.class, () -> caseInsensitive("foo").parseToStream("bar").toList());
  }

  @Test
  public void caseInsensitive_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> caseInsensitive(""));
  }

  @Test
  public void word_success() {
    assertThat(word("foo").parse("foo")).isEqualTo("foo");
    assertThat(word("foo").matches("foo")).isTrue();
  }

  @Test
  public void word_failIfFollowedByWordChar() {
    assertThrows(ParseException.class, () -> word("foo").parse("foobar"));
    assertThat(word("foo").matches("foobar")).isFalse();
    assertThrows(ParseException.class, () -> word("foo").parse("foo_bar"));
    assertThat(word("foo").matches("foo_bar")).isFalse();
    assertThrows(ParseException.class, () -> word("foo").parse("foo1"));
    assertThat(word("foo").matches("foo1")).isFalse();
  }

  @Test
  public void word_successIfNotFollowedByWordChar() {
    assertThat(word("foo").probe("foo?")).containsExactly("foo");
    assertThat(word("foo").probe("foo-")).containsExactly("foo");
  }

  @Test
  public void word_skipping_success() {
    assertThat(word("foo").skipping(whitespace()).parseToStream("foo")).containsExactly("foo");
    assertThat(word("foo").skipping(whitespace()).parseToStream("foo foo"))
        .containsExactly("foo", "foo");
    assertThat(word("foo").skipping(whitespace()).probe(" foo-foo")).containsExactly("foo");
  }

  @Test
  public void word_skipping_failIfFollowedByWordChar() {
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(whitespace(), "foobar"));
    assertThat(word("foo").skipping(whitespace()).matches("foobar")).isFalse();
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(whitespace(), "foo_bar"));
    assertThat(word("foo").skipping(whitespace()).matches("foo_bar")).isFalse();
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(whitespace(), "foo1"));
    assertThat(word("foo").skipping(whitespace()).matches("foo1")).isFalse();
    assertThrows(ParseException.class, () -> word("foo").parseSkipping(whitespace(), " foo1"));
    assertThat(word("foo").skipping(whitespace()).matches(" foo1")).isFalse();
  }

  @Test
  public void caseInsensitiveWord_success() {
    assertThat(caseInsensitiveWord("foo").source().parse("FoO")).isEqualTo("FoO");
    assertThat(caseInsensitiveWord("foo").source().matches("FoO")).isTrue();
  }

  @Test
  public void caseInsensitiveWord_failIfFollowedByWordChar() {
    assertThrows(ParseException.class, () -> caseInsensitiveWord("foo").parse("FoObar"));
    assertThat(caseInsensitiveWord("foo").matches("FoObar")).isFalse();
    assertThrows(ParseException.class, () -> caseInsensitiveWord("foo").parse("FoO_bar"));
    assertThat(caseInsensitiveWord("foo").matches("FoO_bar")).isFalse();
    assertThrows(ParseException.class, () -> caseInsensitiveWord("foo").parse("FoO1"));
    assertThat(caseInsensitiveWord("foo").matches("FoO1")).isFalse();
  }

  @Test
  public void caseInsensitiveWord_successIfNotFollowedByWordChar() {
    assertThat(caseInsensitiveWord("foo").source().probe("FoO?")).containsExactly("FoO");
    assertThat(caseInsensitiveWord("foo").source().probe("FoO-")).containsExactly("FoO");
  }

  @Test
  public void caseInsensitiveWord_skipping_success() {
    assertThat(caseInsensitiveWord("foo").source().skipping(whitespace()).parseToStream("fOo"))
        .containsExactly("fOo");
    assertThat(caseInsensitiveWord("foo").source().skipping(whitespace()).parseToStream("FoO fOO"))
        .containsExactly("FoO", "fOO");
    assertThat(caseInsensitiveWord("foo").source().skipping(whitespace()).probe(" FoO-fOo"))
        .containsExactly("FoO");
  }

  @Test
  public void caseInsensitiveWord_skipping_failIfFollowedByWordChar() {
    assertThrows(
        ParseException.class,
        () -> caseInsensitiveWord("foo").parseSkipping(whitespace(), "FoObar"));
    assertThat(caseInsensitiveWord("foo").skipping(whitespace()).matches("FoObar")).isFalse();
    assertThrows(
        ParseException.class,
        () -> caseInsensitiveWord("foo").parseSkipping(whitespace(), "FoO_bar"));
    assertThat(caseInsensitiveWord("foo").skipping(whitespace()).matches("FoO_bar")).isFalse();
    assertThrows(
        ParseException.class, () -> caseInsensitiveWord("foo").parseSkipping(whitespace(), "FoO1"));
    assertThat(caseInsensitiveWord("foo").skipping(whitespace()).matches("FoO1")).isFalse();
    assertThrows(
        ParseException.class,
        () -> caseInsensitiveWord("foo").parseSkipping(whitespace(), " FoO1"));
    assertThat(caseInsensitiveWord("foo").skipping(whitespace()).matches(" FoO1")).isFalse();
  }

  @Test
  public void quotedBy_emptyDelimiters_throws() {
    assertThrows(IllegalArgumentException.class, () -> quotedBy("", "}"));
    assertThrows(IllegalArgumentException.class, () -> quotedBy("{", ""));
  }

  @Test
  public void quotedBy_emptyContent() {
    assertThat(quotedBy('{', '}').parse("{}")).isEmpty();
    assertThat(quotedBy('{', '}').matches("{}")).isTrue();
    assertThat(quotedBy('{', '}').source().parse("{}")).isEqualTo("{}");
    assertThat(quotedBy('{', '}').source().matches("{}")).isTrue();
  }

  @Test
  public void quotedBy_success() {
    assertThat(quotedBy('{', '}').parse("{foo}")).isEqualTo("foo");
    assertThat(quotedBy('{', '}').matches("{foo}")).isTrue();
    assertThat(quotedBy('{', '}').source().parse("{foo}")).isEqualTo("{foo}");
    assertThat(quotedBy('{', '}').source().matches("{foo}")).isTrue();
  }

  @Test
  public void quotedBy_beforeNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy('{', '}').parse("a}"));
    assertThat(quotedBy('{', '}').matches("a}")).isFalse();
    assertThat(e).hasMessageThat().contains("1:1");
    assertThat(e).hasMessageThat().contains("expecting <{>");
  }

  @Test
  public void quotedBy_afterNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy('{', '}').parse("{a"));
    assertThat(quotedBy('{', '}').matches("{a")).isFalse();
    assertThat(e).hasMessageThat().contains("1:2");
    assertThat(e).hasMessageThat().contains("expecting <}>");
  }

  @Test
  public void quotedBy_withSkipping() {
    assertThat(quotedBy('{', '}').parseSkipping(whitespace(), " { foo } ")).isEqualTo(" foo ");
    assertThat(quotedBy('{', '}').skipping(whitespace()).matches(" { foo } ")).isTrue();
    assertThat(quotedBy('{', '}').source().parseSkipping(whitespace(), " { foo } "))
        .isEqualTo("{ foo }");
    assertThat(quotedBy('{', '}').source().skipping(whitespace()).matches(" { foo } ")).isTrue();
  }

  @Test
  public void quotedBy_stringDelimiters_success() {
    assertThat(quotedBy("{{", "}}").parse("{{foo}}")).isEqualTo("foo");
    assertThat(quotedBy("{{", "}}").matches("{{foo}}")).isTrue();
    assertThat(quotedBy("{{", "}}").source().parse("{{foo}}")).isEqualTo("{{foo}}");
    assertThat(quotedBy("{{", "}}").source().matches("{{foo}}")).isTrue();
  }

  @Test
  public void quotedBy_stringDelimiters_partialDelimiterInContent() {
    assertThat(quotedBy("{{", "}}").parse("{{f}o{o}}")).isEqualTo("f}o{o");
    assertThat(quotedBy("{{", "}}").matches("{{f}o{o}}")).isTrue();
    assertThat(quotedBy("{{", "}}").source().parse("{{f}o{o}}")).isEqualTo("{{f}o{o}}");
    assertThat(quotedBy("{{", "}}").source().matches("{{f}o{o}}")).isTrue();
  }

  @Test
  public void quotedBy_stringDelimiters_emptyContent() {
    assertThat(quotedBy("{{", "}}").parse("{{}}")).isEmpty();
    assertThat(quotedBy("{{", "}}").matches("{{}}")).isTrue();
    assertThat(quotedBy("{{", "}}").source().parse("{{}}")).isEqualTo("{{}}");
    assertThat(quotedBy("{{", "}}").source().matches("{{}}")).isTrue();
  }

  @Test
  public void quotedBy_stringDelimiters_beforeNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy("{{", "}}").parse("{a}}"));
    assertThat(quotedBy("{{", "}}").matches("{a}}")).isFalse();
    assertThat(e).hasMessageThat().contains("1:1");
    assertThat(e).hasMessageThat().contains("expecting <{{>");
  }

  @Test
  public void quotedBy_stringDelimiters_afterNotMatched() {
    ParseException e = assertThrows(ParseException.class, () -> quotedBy("{{", "}}").parse("{{a"));
    assertThat(quotedBy("{{", "}}").matches("{{a")).isFalse();
    assertThat(e).hasMessageThat().contains("1:3");
    assertThat(e).hasMessageThat().contains("expecting <}}>");
  }

  @Test
  public void quotedBy_stringDelimiters_withSkipping() {
    assertThat(quotedBy("{{", "}}").parseSkipping(whitespace(), " {{ foo }} ")).isEqualTo(" foo ");
    assertThat(quotedBy("{{", "}}").skipping(whitespace()).matches(" {{ foo }} ")).isTrue();
    assertThat(quotedBy("{{", "}}").source().parseSkipping(whitespace(), " {{ foo }} "))
        .isEqualTo("{{ foo }}");
    assertThat(quotedBy("{{", "}}").source().skipping(whitespace()).matches(" {{ foo }} "))
        .isTrue();
  }

  @Test
  public void quotedByWithEscapes_singleQuote_success() {
    Parser<String> singleQuoted = Parser.quotedByWithEscapes('\'', '\'', chars(1));
    assertThat(singleQuoted.parse("''")).isEmpty();
    assertThat(singleQuoted.matches("''")).isTrue();
    assertThat(singleQuoted.parse("'foo'")).isEqualTo("foo");
    assertThat(singleQuoted.matches("'foo'")).isTrue();
    assertThat(singleQuoted.parse("'foo\\'s'")).isEqualTo("foo's");
    assertThat(singleQuoted.matches("'foo\\'s'")).isTrue();
    assertThat(singleQuoted.parse("'foo\\\\bar'")).isEqualTo("foo\\bar");
    assertThat(singleQuoted.matches("'foo\\\\bar'")).isTrue();
    assertThat(singleQuoted.parse("'\\''")).isEqualTo("'");
    assertThat(singleQuoted.matches("'\\''")).isTrue();
    assertThat(singleQuoted.parse("'\\\\'")).isEqualTo("\\");
    assertThat(singleQuoted.matches("'\\\\'")).isTrue();
  }

  @Test
  public void quotedByWithEscapes_doubleQuote_success() {
    Parser<String> doubleQuoted = Parser.quotedByWithEscapes('"', '"', chars(1));
    assertThat(doubleQuoted.parse("\"\"")).isEmpty();
    assertThat(doubleQuoted.matches("\"\"")).isTrue();
    assertThat(doubleQuoted.parse("\"bar\"")).isEqualTo("bar");
    assertThat(doubleQuoted.matches("\"bar\"")).isTrue();
    assertThat(doubleQuoted.parse("\"bar\\\"baz\"")).isEqualTo("bar\"baz");
    assertThat(doubleQuoted.matches("\"bar\\\"baz\"")).isTrue();
    assertThat(doubleQuoted.parse("\"bar\\\\baz\"")).isEqualTo("bar\\baz");
    assertThat(doubleQuoted.matches("\"bar\\\\baz\"")).isTrue();
  }

  @Test
  public void quotedByWithEscapes_differentBeforeAndAfterChars_success() {
    Parser<String> parser = Parser.quotedByWithEscapes('<', '>', chars(1));
    assertThat(parser.parse("<foo>")).isEqualTo("foo");
    assertThat(parser.matches("<foo>")).isTrue();
    assertThat(parser.parse("<foo<bar>")).isEqualTo("foo<bar");
    assertThat(parser.matches("<foo<bar>")).isTrue();
    assertThat(parser.parse("<foo\\>bar>")).isEqualTo("foo>bar");
    assertThat(parser.matches("<foo\\>bar>")).isTrue();
  }

  @Test
  public void quotedByWithEscapes_failures() {
    Parser<String> singleQuoted = Parser.quotedByWithEscapes('\'', '\'', chars(1));
    assertThrows(ParseException.class, () -> singleQuoted.parse("'foo")); // unclosed
    assertThat(singleQuoted.matches("'foo")).isFalse();
    assertThrows(ParseException.class, () -> singleQuoted.parse("'foo'bar")); // leftover
    assertThat(singleQuoted.matches("'foo'bar")).isFalse();
    assertThrows(ParseException.class, () -> singleQuoted.parse("'foo\\")); // dangling escape
    assertThat(singleQuoted.matches("'foo\\'")).isFalse();
  }

  @Test
  public void quotedByWithEscapes_invalidQuoteChar_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> Parser.quotedByWithEscapes('"', '\\', chars(1)));
    assertThrows(
        IllegalArgumentException.class, () -> Parser.quotedByWithEscapes('"', '\n', chars(1)));
    assertThrows(
        IllegalArgumentException.class, () -> Parser.quotedByWithEscapes('"', '\r', chars(1)));
    assertThrows(
        IllegalArgumentException.class, () -> Parser.quotedByWithEscapes('"', '\t', chars(1)));
  }

  @Test
  public void quotedByWithEscapes_unicodeEscape_success() {
    Parser<String> unicodeEscaped = string("u").then(bmpCodeUnit()).map(Character::toString);
    Parser<String> quotedString =
        Parser.quotedByWithEscapes('\'', '\'', unicodeEscaped.or(chars(1)));
    assertThat(quotedString.parse("''")).isEmpty();
    assertThat(quotedString.matches("''")).isTrue();
    assertThat(quotedString.parse("'emoji: \\uD83D\\uDe00'")).isEqualTo("emoji: 😀");
    assertThat(quotedString.matches("'emoji: \\uD83D\\uDe00'")).isTrue();
  }

  @Test
  public void quotedByWithEscapes_stringBeforeCharAfter_success() {
    Parser<String> parser = Parser.quotedByWithEscapes("<<", '>', chars(1));
    assertThat(parser.parse("<<foo>")).isEqualTo("foo");
    assertThat(parser.matches("<<foo>")).isTrue();
    assertThat(parser.parse("<<foo<bar>")).isEqualTo("foo<bar");
    assertThat(parser.matches("<<foo<bar>")).isTrue();
    assertThat(parser.parse("<<foo\\>bar>")).isEqualTo("foo>bar");
    assertThat(parser.matches("<<foo\\>bar>")).isTrue();
  }

  @Test
  public void quotedByWithEscapes_stringBeforeCharAfter_failures() {
    Parser<String> parser = Parser.quotedByWithEscapes("<<", '>', chars(1));
    assertThrows(ParseException.class, () -> parser.parse("<<foo")); // unclosed
    assertThat(parser.matches("<<foo")).isFalse();
    assertThrows(ParseException.class, () -> parser.parse("<<foo>bar")); // leftover
    assertThat(parser.matches("<<foo>bar")).isFalse();
    assertThrows(ParseException.class, () -> parser.parse("<<foo\\")); // dangling escape
    assertThat(parser.matches("<<foo\\>")).isFalse();
  }

  @Test
  public void quotedByWithEscapes_stringBeforeCharAfter_unicodeEscape_success() {
    Parser<String> unicodeEscaped = string("u").then(bmpCodeUnit()).map(Character::toString);
    Parser<String> quotedString =
        Parser.quotedByWithEscapes("begin:", ';', unicodeEscaped.or(chars(1)));
    assertThat(quotedString.parse("begin:;")).isEmpty();
    assertThat(quotedString.matches("begin:;")).isTrue();
    assertThat(quotedString.parse("begin:emoji: \\uD83D\\uDe00;")).isEqualTo("emoji: 😀");
    assertThat(quotedString.matches("begin:emoji: \\uD83D\\uDe00;")).isTrue();
  }

  @Test
  public void quotedByWithEscapes_markdownLinkWithEscape() {
    record MarkdownLink(String text, String url) {}
    Parser<String> escapedChar =
        Parser.one(anyOf("!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~\\"), "escapable char")
            .map(c -> Character.toString(c))
            .or(one(ANY, "non-escapable char").map(c -> "\\" + c));
    Parser<MarkdownLink> parser =
        Parser.sequence(
            Parser.quotedByWithEscapes("![", ']', escapedChar),
            Parser.quotedByWithEscapes("(http://", ')', escapedChar),
            MarkdownLink::new);
    assertThat(parser.parse("![text](http://\\)url)")).isEqualTo(new MarkdownLink("text", ")url"));
    assertThat(parser.parse("![text\\a](http://\\)url)"))
        .isEqualTo(new MarkdownLink("text\\a", ")url"));
  }

  @Test
  public void bmpCodeUnit_emoji() {
    assertThat(bmpCodeUnit().map(Character::toString).zeroOrMore(joining()).parse("d83dDE00"))
        .isEqualTo("😀");
    assertThat(bmpCodeUnit().map(Character::toString).zeroOrMore(joining()).matches("d83dDE00"))
        .isTrue();
  }

  @Test
  public void testNulls() throws Exception {
    NullPointerTester tester =
        new NullPointerTester()
            .setDefault(Parser.class, string("a"))
            .setDefault(Parser.OrEmpty.class, string("a").orElse("default"))
            .setDefault(String.class, "test")
            .setDefault(char.class, '`');
    tester.testAllPublicStaticMethods(Parser.class);
    tester
        .ignore(Parser.class.getMethod("orElse", Object.class))
        .ignore(Parser.class.getMethod("thenReturn", Object.class))
        .testAllPublicInstanceMethods(string("a"));
  }

  @Test
  public void thenReturn_success() {
    Parser<Integer> parser1 = string("one").thenReturn(1);
    assertThat(parser1.parse("one")).isEqualTo(1);
    assertThat(parser1.matches("one")).isTrue();
    assertThat(parser1.parseToStream("one")).containsExactly(1);
    assertThat(parser1.parseToStream("")).isEmpty();

    Parser<String> parser2 = string("two").thenReturn("deux");
    assertThat(parser2.parse("two")).isEqualTo("deux");
    assertThat(parser2.matches("two")).isTrue();
    assertThat(parser2.parseToStream("two")).containsExactly("deux");
    assertThat(parser2.parseToStream("")).isEmpty();
  }

  @Test
  public void thenReturn_success_source() {
    Parser<Integer> parser1 = string("one").thenReturn(1);
    assertThat(parser1.source().parse("one")).isEqualTo("one");
    assertThat(parser1.source().matches("one")).isTrue();
    assertThat(parser1.source().parseToStream("one")).containsExactly("one");
    assertThat(parser1.source().parseToStream("")).isEmpty();

    Parser<String> parser2 = string("two").thenReturn("deux");
    assertThat(parser2.source().parse("two")).isEqualTo("two");
    assertThat(parser2.source().matches("two")).isTrue();
    assertThat(parser2.source().parseToStream("two")).containsExactly("two");
    assertThat(parser2.source().parseToStream("")).isEmpty();
  }

  @Test
  public void thenReturn_failure_withLeftover() {
    assertThrows(ParseException.class, () -> string("one").thenReturn(1).parse("onea"));
    assertThat(string("one").thenReturn(1).matches("onea")).isFalse();
    assertThrows(
        ParseException.class, () -> string("one").thenReturn(1).parseToStream("onea").toList());
  }

  @Test
  public void thenReturn_failure() {
    assertThrows(ParseException.class, () -> string("one").thenReturn(1).parse("two"));
    assertThat(string("one").thenReturn(1).matches("two")).isFalse();
    assertThrows(
        ParseException.class, () -> string("one").thenReturn(1).parseToStream("two").toList());
  }

  @Test
  public void map_success() {
    Parser<Integer> parser1 = string("123").map(Integer::parseInt);
    assertThat(parser1.parse("123")).isEqualTo(123);
    assertThat(parser1.matches("123")).isTrue();
    assertThat(parser1.parseToStream("123")).containsExactly(123);
    assertThat(parser1.parseToStream("")).isEmpty();

    Parser<Boolean> parser2 = string("true").map(Boolean::parseBoolean);
    assertThat(parser2.parse("true")).isTrue();
    assertThat(parser2.matches("true")).isTrue();
    assertThat(parser2.parseToStream("true")).containsExactly(true);
    assertThat(parser2.parseToStream("")).isEmpty();
  }

  @Test
  public void map_success_source() {
    Parser<Integer> parser1 = string("123").map(Integer::parseInt);
    assertThat(parser1.source().parse("123")).isEqualTo("123");
    assertThat(parser1.source().matches("123")).isTrue();
    assertThat(parser1.source().parseToStream("123")).containsExactly("123");
    assertThat(parser1.source().parseToStream("")).isEmpty();

    Parser<Boolean> parser2 = string("true").map(Boolean::parseBoolean);
    assertThat(parser2.source().parse("true")).isEqualTo("true");
    assertThat(parser2.source().matches("true")).isTrue();
    assertThat(parser2.source().parseToStream("true")).containsExactly("true");
    assertThat(parser2.source().parseToStream("")).isEmpty();
  }

  @Test
  public void map_failure_withLeftover() {
    assertThrows(ParseException.class, () -> string("123").map(Integer::parseInt).parse("123a"));
    assertThat(string("123").map(Integer::parseInt).matches("123a")).isFalse();
    assertThrows(
        ParseException.class,
        () -> string("123").map(Integer::parseInt).parseToStream("123a").toList());
  }

  @Test
  public void map_failure() {
    assertThrows(ParseException.class, () -> string("abc").map(Integer::parseInt).parse("def"));
    assertThat(string("abc").map(Integer::parseInt).matches("def")).isFalse();
    assertThrows(
        ParseException.class,
        () -> string("abc").map(Integer::parseInt).parseToStream("def").toList());
  }

  @Test
  public void suchThat_parserFails() {
    ImmutableSet<String> keywords = ImmutableSet.of("if", "else");
    Parser<String> parser = word().suchThat(keywords::contains, "keyword");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThat(parser.matches("b")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 1:1: expecting <keyword>, encountered [b]");
  }

  @Test
  public void suchThat_conditionSucceeds() {
    ImmutableSet<String> magicNumbers = ImmutableSet.of("888", "911");
    Parser<String> parser = digits().suchThat(magicNumbers::contains, "magic");
    assertThat(parser.parse("888")).isEqualTo("888");
    assertThat(parser.matches("888")).isTrue();
    assertThat(parser.skipping(whitespace()).parseToStream("911 888"))
        .containsExactly("911", "888")
        .inOrder();
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void suchThat_conditionFails() {
    Parser<Integer> parser =
        string("23").map(Integer::parseInt).suchThat(i -> i > 100, "larger than 100");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("23"));
    assertThat(parser.matches("23")).isFalse();
    assertThat(thrown)
        .hasMessageThat()
        .contains("at 1:1: expecting <larger than 100>, encountered [23]");
  }

  @Test
  public void flatMap_success() {
    Parser<String> parser = digits().flatMap(number -> string("=" + number));
    assertThat(parser.parse("123=123")).isEqualTo("=123");
    assertThat(parser.matches("123=123")).isTrue();
    assertThat(parser.parseToStream("123=123")).containsExactly("=123");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void flatMap_success_source() {
    Parser<String> parser = digits().flatMap(number -> string("=" + number));
    assertThat(parser.source().parse("123=123")).isEqualTo("123=123");
    assertThat(parser.source().matches("123=123")).isTrue();
    assertThat(parser.source().parseToStream("123=123")).containsExactly("123=123");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void flatMap_failure_withLeftover() {
    Parser<String> parser = digits().flatMap(number -> string("=" + number));
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("123=123???"));
    assertThat(parser.matches("123=123???")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 1:8: expecting <EOF>, encountered [???]");
    assertThrows(ParseException.class, () -> parser.parseToStream("123=123???").toList());
  }

  @Test
  public void flatMap_failure() {
    Parser<String> parser = digits().flatMap(number -> string("=" + number));
    assertThrows(ParseException.class, () -> parser.parse("=123"));
    assertThat(parser.matches("=123")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("=123").toList());
    assertThrows(ParseException.class, () -> parser.parse("123=124"));
    assertThat(parser.matches("123=124")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("123=124").toList());
  }

  @Test
  public void then_success() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThat(parser.parse("value:123")).isEqualTo(123);
    assertThat(parser.matches("value:123")).isTrue();
    assertThat(parser.parseToStream("value:123")).containsExactly(123);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void then_success_source() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThat(parser.source().parse("value:123")).isEqualTo("value:123");
    assertThat(parser.source().matches("value:123")).isTrue();
    assertThat(parser.source().parseToStream("value:123")).containsExactly("value:123");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void then_failure_withLeftover() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:123a"));
    assertThat(parser.matches("value:123a")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("value:123a").toList());
  }

  @Test
  public void then_failure() {
    Parser<Integer> parser = string("value:").then(string("123").map(Integer::parseInt));
    assertThrows(ParseException.class, () -> parser.parse("value:abc"));
    assertThat(parser.matches("value:abc")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("value:abc").toList());
    assertThrows(ParseException.class, () -> parser.parse("val:123"));
    assertThat(parser.matches("val:123")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("val:123").toList());
  }

  @Test
  public void then_orEmpty_p1Fails() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThrows(ParseException.class, () -> parser.parse("c"));
    assertThat(parser.matches("c")).isFalse();
  }

  @Test
  public void then_orEmpty_p2MatchesZeroTimes() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("a")).isEmpty();
    assertThat(parser.matches("a")).isTrue();
  }

  @Test
  public void then_orEmpty_p2MatchesZeroTimes_source() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.source().parse("a")).isEqualTo("a");
    assertThat(parser.source().matches("a")).isTrue();
  }

  @Test
  public void then_orEmpty_p2MatchesOnce() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("ab")).containsExactly("b");
    assertThat(parser.matches("ab")).isTrue();
  }

  @Test
  public void then_orEmpty_p2MatchesOnce_source() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.source().parse("ab")).isEqualTo("ab");
    assertThat(parser.source().matches("ab")).isTrue();
  }

  @Test
  public void then_orEmpty_p2MatchesMultipleTimes() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.parse("abb")).containsExactly("b", "b");
    assertThat(parser.matches("abb")).isTrue();
  }

  @Test
  public void then_orEmpty_p2MatchesMultipleTimes_source() {
    Parser<List<String>> parser = string("a").then(string("b").zeroOrMore());
    assertThat(parser.source().parse("abb")).isEqualTo("abb");
    assertThat(parser.source().matches("abb")).isTrue();
  }

  @Test
  public void followedBy_success() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThat(parser.parse("123บาท")).isEqualTo("123");
    assertThat(parser.matches("123บาท")).isTrue();
    assertThat(parser.parseToStream("123บาท")).containsExactly("123");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void followedBy_success_source() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThat(parser.source().parse("123บาท")).isEqualTo("123บาท");
    assertThat(parser.source().matches("123บาท")).isTrue();
    assertThat(parser.source().parseToStream("123บาท")).containsExactly("123บาท");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void followedBy_failure_withLeftover() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123บาทa"));
    assertThat(parser.matches("123บาทa")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("123บาทa").toList());
  }

  @Test
  public void followedBy_failure() {
    Parser<String> parser = string("123").followedBy(string("บาท"));
    assertThrows(ParseException.class, () -> parser.parse("123baht"));
    assertThat(parser.matches("123baht")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("123baht").toList());
    assertThrows(ParseException.class, () -> parser.parse("456บาท"));
    assertThat(parser.matches("456บาท")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("456บาท").toList());
  }

  @Test
  public void followedByOrEof_suffixMatches() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    assertThat(parser.parse("foobar")).isEqualTo("foo");
    assertThat(parser.matches("foobar")).isTrue();
  }

  @Test
  public void followedByOrEof_eofMatches() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.matches("foo")).isTrue();
  }

  @Test
  public void followedByOrEof_neitherMatches() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("foobaz"));
    assertThat(parser.matches("foobaz")).isFalse();
    assertThat(e).hasMessageThat().contains("at 1:4:");
    assertThat(e).hasMessageThat().contains("expecting <bar>");
    assertThat(e).hasMessageThat().contains("encountered [baz]");
  }

  @Test
  public void followedByOrEof_mainParserFails() {
    Parser<String> parser = string("foo").followedByOrEof(string("bar"));
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("fobar"));
    assertThat(parser.matches("fobar")).isFalse();
    assertThat(e).hasMessageThat().contains("expecting <foo>");
  }

  @Test
  public void optionallyFollowedBy_suffixCannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> string("123").optionallyFollowedBy(""));
  }

  @Test
  public void optionallyFollowedBy_success() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    assertThat(parser.parse("123++")).isEqualTo(124);
    assertThat(parser.matches("123++")).isTrue();
    assertThat(parser.parseToStream("123++")).containsExactly(124);
    assertThat(parser.parse("123")).isEqualTo(123);
    assertThat(parser.matches("123")).isTrue();
    assertThat(parser.parseToStream("123")).containsExactly(123);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void optionallyFollowedBy_success_source() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    assertThat(parser.source().parse("123++")).isEqualTo("123++");
    assertThat(parser.source().matches("123++")).isTrue();
    assertThat(parser.source().parseToStream("123++")).containsExactly("123++");
    assertThat(parser.source().parse("123")).isEqualTo("123");
    assertThat(parser.source().matches("123")).isTrue();
    assertThat(parser.source().parseToStream("123")).containsExactly("123");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void optionallyFollowedBy_unconsumedInput() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("123+"));
    assertThat(parser.matches("123+")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 1:4: expecting <EOF>, encountered [+]");
    assertThrows(ParseException.class, () -> parser.parseToStream("123+").toList());
  }

  @Test
  public void optionallyFollowedBy_failedToMatch() {
    Parser<Integer> parser =
        string("123").map(Integer::parseInt).optionallyFollowedBy("++", n -> n + 1);
    Parser.ParseException thrown =
        assertThrows(Parser.ParseException.class, () -> parser.parse("abc"));
    assertThat(parser.matches("abc")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 1:1: expecting <123>, encountered [abc]");
    assertThrows(ParseException.class, () -> parser.parseToStream("abc").toList());
  }

  @Test
  public void optionallyFollowedBy_parserSuffix_suffixExists() {
    Parser<Integer> parser =
        string("123")
            .map(Integer::parseInt)
            .optionallyFollowedBy(
                string("+").then(digits()).map(Integer::parseInt), (n, i) -> n + i);
    assertThat(parser.parse("123+1")).isEqualTo(124);
    assertThat(parser.matches("123+1")).isTrue();
    assertThat(parser.parseToStream("123+2")).containsExactly(125);
  }

  @Test
  public void optionallyFollowedBy_parserSuffix_suffixDoesNotExist() {
    Parser<Integer> parser =
        string("123")
            .map(Integer::parseInt)
            .optionallyFollowedBy(
                string("+").then(digits()).map(Integer::parseInt), (n, i) -> n + i);
    assertThat(parser.parse("123")).isEqualTo(123);
    assertThat(parser.matches("123")).isTrue();
    assertThat(parser.parseToStream("123+3")).containsExactly(126);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void notFollowedBy_emptySuffix_throws() {
    assertThrows(IllegalArgumentException.class, () -> string("a").notFollowedBy(""));
  }

  @Test
  public void notFollowedBy_selfFailsToMatch() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("c"));
    assertThat(string("a").notFollowedBy("b").matches("c")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 1:1: expecting <a>, encountered [c]");
  }

  @Test
  public void notFollowedBy_suffixFollows() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("ab"));
    assertThat(string("a").notFollowedBy("b").matches("ab")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 1:2: unexpected `b` – [b]");
  }

  @Test
  public void notFollowedBy_suffixDoesNotFollow() {
    assertThat(string("a").notFollowedBy("b").parse("a")).isEqualTo("a");
    assertThat(string("a").notFollowedBy("b").matches("a")).isTrue();
    assertThrows(ParseException.class, () -> string("a").notFollowedBy("b").parse("ac"));
    assertThat(string("a").notFollowedBy("b").matches("ac")).isFalse();
  }

  @Test
  public void notFollowedBy_suffixDoesNotFollow_source() {
    Parser<String> parser = string("a").notFollowedBy("b");
    assertThat(parser.parse("a")).isEqualTo("a");
    assertThat(parser.matches("a")).isTrue();
    assertThrows(ParseException.class, () -> parser.parse("ac"));
    assertThat(parser.matches("ac")).isFalse();
  }

  @Test
  public void notImmediatelyFollowedBy_selfFailsToMatch() {
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("c"));
    assertThat(string("a").notImmediatelyFollowedBy(is('b'), "b").matches("c")).isFalse();
  }

  @Test
  public void notImmediatelyFollowedBy_suffixFollows() {
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("ab"));
    assertThat(string("a").notImmediatelyFollowedBy(is('b'), "b").matches("ab")).isFalse();
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotFollow() {
    assertThat(string("a").notImmediatelyFollowedBy(is('b'), "b").parse("a")).isEqualTo("a");
    assertThat(string("a").notImmediatelyFollowedBy(is('b'), "b").matches("a")).isTrue();
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("ac"));
    assertThat(string("a").notImmediatelyFollowedBy(is('b'), "b").matches("ac")).isFalse();
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotFollow_source() {
    Parser<String> parser = string("a").notImmediatelyFollowedBy(is('b'), "b");
    assertThat(parser.parse("a")).isEqualTo("a");
    assertThat(parser.matches("a")).isTrue();
    assertThrows(
        ParseException.class, () -> string("a").notImmediatelyFollowedBy(is('b'), "b").parse("ac"));
    assertThat(string("a").notImmediatelyFollowedBy(is('b'), "b").matches("ac")).isFalse();
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotLiterallyFollow() {
    assertThat(
            string("a")
                .notImmediatelyFollowedBy(is('b'), "b")
                .followedBy("b")
                .parseSkipping(whitespace(), "a b"))
        .isEqualTo("a");
    assertThat(
            string("a")
                .notImmediatelyFollowedBy(is('b'), "b")
                .followedBy("b")
                .skipping(whitespace())
                .matches("a b"))
        .isTrue();
  }

  @Test
  public void notImmediatelyFollowedBy_suffixDoesNotLiterallyFollow_source() {
    assertThat(
            string("a")
                .notImmediatelyFollowedBy(is('b'), "b")
                .followedBy("b")
                .source()
                .parseSkipping(whitespace(), "a b"))
        .isEqualTo("a b");
    assertThat(
            string("a")
                .notImmediatelyFollowedBy(is('b'), "b")
                .followedBy("b")
                .source()
                .skipping(whitespace())
                .matches("a b"))
        .isTrue();
  }

  @Test
  public void notFollowedBy_skipping_suffixFollows() {
    assertThrows(
        ParseException.class,
        () -> string("a").notFollowedBy("b").parseSkipping(whitespace(), "a b"));
    assertThat(string("a").notFollowedBy("b").skipping(whitespace()).matches("a b")).isFalse();
  }

  @Test
  public void notFollowedBy_skipping_suffixDoesNotFollow() {
    assertThat(string("a").notFollowedBy("b").parseSkipping(whitespace(), "a")).isEqualTo("a");
    assertThat(string("a").notFollowedBy("b").skipping(whitespace()).matches("a")).isTrue();
    assertThrows(
        ParseException.class,
        () -> string("a").notFollowedBy("b").parseSkipping(whitespace(), "a c"));
    assertThat(string("a").notFollowedBy("b").skipping(whitespace()).matches("a c")).isFalse();
  }

  @Test
  public void notFollowedBy_skipping_suffixDoesNotFollow_source() {
    Parser<String> parser = string("a").notFollowedBy("b");
    assertThat(parser.source().parseSkipping(whitespace(), "a")).isEqualTo("a");
    assertThat(parser.source().skipping(whitespace()).matches("a")).isTrue();
    assertThrows(
        ParseException.class,
        () -> string("a").notFollowedBy("b").parseSkipping(whitespace(), "a c"));
    assertThat(string("a").notFollowedBy("b").skipping(whitespace()).matches("a c")).isFalse();
  }

  @Test
  public void expecting_eof() {
    Parser<String> parser = string("f");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(parser.matches("")).isFalse();
    assertThat(thrown).hasMessageThat().contains("expecting <f>, encountered <EOF>");
  }

  @Test
  public void expecting_differentChar() {
    Parser<String> parser = string("foo");
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("bar"));
    assertThat(parser.matches("bar")).isFalse();
    assertThat(thrown).hasMessageThat().contains("expecting <foo>, encountered [bar]");
  }

  @Test
  public void simpleCalculator_expectingClosingParen_eof() {
    Parser<Integer> parser = simpleCalculator();
    ParseException thrown =
        assertThrows(
            ParseException.class, () -> parser.parseSkipping(whitespace(), "(1 + \n( 2 + 3)"));
    assertThat(parser.skipping(whitespace()).matches("(1 + \n( 2 + 3)")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 2:9: expecting <)>, encountered <EOF>.");
  }

  @Test
  public void simpleCalculator_expectingClosingParen_differentChar() {
    Parser<Integer> parser = simpleCalculator();
    ParseException thrown =
        assertThrows(
            ParseException.class, () -> parser.parseSkipping(whitespace(), "(1 + \n( 2 ? 3)"));
    assertThat(parser.skipping(whitespace()).matches("(1 + \n( 2 ? 3)")).isFalse();
    assertThat(thrown).hasMessageThat().contains("at 2:5");
    assertThat(thrown).hasMessageThat().contains("encountered [?...].");
  }

  @Test
  public void sequence_success() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThat(parser.parse("onetwo")).isEqualTo("1+2=3");
    assertThat(parser.matches("onetwo")).isTrue();
    assertThat(parser.parseToStream("onetwo")).containsExactly("1+2=3");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void sequence_success_source() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThat(parser.source().parse("onetwo")).isEqualTo("onetwo");
    assertThat(parser.source().matches("onetwo")).isTrue();
    assertThat(parser.source().parseToStream("onetwo")).containsExactly("onetwo");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void sequence_failure_withLeftover() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("onetwoa"));
    assertThat(parser.matches("onetwoa")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("onetwoa").toList());
  }

  @Test
  public void sequence_failure() {
    Parser<String> parser =
        sequence(
            string("one").map(s -> 1),
            string("two").map(s -> 2),
            (a, b) -> String.format("%d+%d=%d", a, b, a + b));
    assertThrows(ParseException.class, () -> parser.parse("one-two"));
    assertThat(parser.matches("one-two")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("one-two").toList());
  }

  @Test
  public void sequence_orEmpty_leftFails() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThrows(ParseException.class, () -> parser.parse("c"));
    assertThat(parser.matches("c")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("c").toList());
  }

  @Test
  public void sequence_orEmpty_rightIsEmpty() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.parse("a")).isEqualTo("a[]");
    assertThat(parser.matches("a")).isTrue();
    assertThat(parser.parseToStream("a")).containsExactly("a[]");
  }

  @Test
  public void sequence_orEmpty_rightIsEmpty_source() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.source().parse("a")).isEqualTo("a");
    assertThat(parser.source().matches("a")).isTrue();
    assertThat(parser.source().parseToStream("a")).containsExactly("a");
  }

  @Test
  public void sequence_orEmpty_bothSucceed() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.parse("ab")).isEqualTo("a[b]");
    assertThat(parser.matches("ab")).isTrue();
    assertThat(parser.parseToStream("ab")).containsExactly("a[b]");
    assertThat(parser.parse("abb")).isEqualTo("a[b, b]");
    assertThat(parser.matches("abb")).isTrue();
    assertThat(parser.parseToStream("abb")).containsExactly("a[b, b]");
  }

  @Test
  public void sequence_orEmpty_bothSucceed_source() {
    Parser<String> parser = sequence(string("a"), string("b").zeroOrMore(), (a, list) -> a + list);
    assertThat(parser.source().parse("ab")).isEqualTo("ab");
    assertThat(parser.source().matches("ab")).isTrue();
    assertThat(parser.source().parseToStream("ab")).containsExactly("ab");
    assertThat(parser.source().parse("abb")).isEqualTo("abb");
    assertThat(parser.source().matches("abb")).isTrue();
    assertThat(parser.source().parseToStream("abb")).containsExactly("abb");
  }

  @Test
  public void sequence_leftOrEmpty_bothSucceed() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.parse("ab")).isEqualTo("[a]b");
    assertThat(parser.matches("ab")).isTrue();
    assertThat(parser.parseToStream("ab")).containsExactly("[a]b");
    assertThat(parser.parse("aab")).isEqualTo("[a, a]b");
    assertThat(parser.matches("aab")).isTrue();
    assertThat(parser.parseToStream("aab")).containsExactly("[a, a]b");
  }

  @Test
  public void sequence_leftOrEmpty_bothSucceed_source() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.source().parse("ab")).isEqualTo("ab");
    assertThat(parser.source().matches("ab")).isTrue();
    assertThat(parser.source().parseToStream("ab")).containsExactly("ab");
    assertThat(parser.source().parse("aab")).isEqualTo("aab");
    assertThat(parser.source().matches("aab")).isTrue();
    assertThat(parser.source().parseToStream("aab")).containsExactly("aab");
  }

  @Test
  public void sequence_leftOrEmpty_leftIsEmpty() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.parse("b")).isEqualTo("[]b");
    assertThat(parser.matches("b")).isTrue();
    assertThat(parser.parseToStream("b")).containsExactly("[]b");
  }

  @Test
  public void sequence_leftOrEmpty_leftIsEmpty_source() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThat(parser.source().parse("b")).isEqualTo("b");
    assertThat(parser.source().matches("b")).isTrue();
    assertThat(parser.source().parseToStream("b")).containsExactly("b");
  }

  @Test
  public void sequence_leftOrEmpty_rightFails() {
    Parser<String> parser = sequence(string("a").zeroOrMore(), string("b"), (list, b) -> list + b);
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThat(parser.matches("a")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("c"));
    assertThat(parser.matches("c")).isFalse();
    assertThrows(ParseException.class, () -> parser.parseToStream("c").toList());
  }

  @Test
  public void sequence_bothOrEmpty_bothSucceed() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.parse("ab")).isEqualTo("a:b");
    assertThat(parser.matches("ab")).isTrue();
  }

  @Test
  public void sequence_bothOrEmpty_bothSucceed_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.parse("ab")).isEqualTo("a:b");
    assertThat(parser.matches("ab")).isTrue();
  }

  @Test
  public void sequence_bothOrEmpty_leftIsEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("b")).isEqualTo("default-a:b");
  }

  @Test
  public void sequence_bothOrEmpty_leftIsEmpty_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("b")).isEqualTo("default-a:b");
  }

  @Test
  public void sequence_bothOrEmpty_rightIsEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("a")).isEqualTo("a:default-b");
  }

  @Test
  public void sequence_bothOrEmpty_rightIsEmpty_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThat(parser.notEmpty().parse("a")).isEqualTo("a:default-b");
  }

  @Test
  public void sequence_bothOrEmpty_bothEmpty() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").orElse("default-a"),
            string("b").orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
  }

  @Test
  public void sequence_bothOrEmpty_bothEmpty_source() {
    Parser<String>.OrEmpty parser =
        sequence(
            string("a").source().orElse("default-a"),
            string("b").source().orElse("default-b"),
            (a, b) -> a + ":" + b);
    assertThrows(ParseException.class, () -> parser.notEmpty().parse(""));
  }

  @Test
  public void sequence3_success() {
    Parser<String> parser = sequence(string("a"), string("b"), string("c"), (a, b, c) -> a + b + c);
    assertThat(parser.parse("abc")).isEqualTo("abc");
    assertThat(parser.matches("abc")).isTrue();
    assertThat(parser.parseToStream("abc")).containsExactly("abc");
    assertThat(parser.probe("abc")).containsExactly("abc");
    assertThat(parser.parseToStream("")).isEmpty();
    assertThat(parser.probe("")).isEmpty();
  }

  @Test
  public void sequence3_success_source() {
    Parser<String> parser = sequence(string("a"), string("b"), string("c"), (a, b, c) -> a + b + c);
    assertThat(parser.source().parse("abc")).isEqualTo("abc");
    assertThat(parser.source().matches("abc")).isTrue();
    assertThat(parser.source().parseToStream("abc")).containsExactly("abc");
    assertThat(parser.source().probe("abc")).containsExactly("abc");
    assertThat(parser.source().parseToStream("")).isEmpty();
    assertThat(parser.source().probe("")).isEmpty();
  }

  @Test
  public void sequence3_failure() {
    Parser<String> parser = sequence(string("a"), string("b"), string("c"), (a, b, c) -> a + b + c);
    assertThrows(ParseException.class, () -> parser.parse("xbc"));
    assertThrows(ParseException.class, () -> parser.parse("axc"));
    assertThrows(ParseException.class, () -> parser.parse("abx"));
    assertThat(parser.matches("xbc")).isFalse();
    assertThat(parser.matches("axc")).isFalse();
    assertThat(parser.matches("abx")).isFalse();
    assertThat(parser.probe("xbc")).isEmpty();
    assertThat(parser.probe("axc")).isEmpty();
    assertThat(parser.probe("abx")).isEmpty();
  }

  @Test
  public void sequence4_success() {
    Parser<String> parser =
        sequence(string("a"), string("b"), string("c"), string("d"), (a, b, c, d) -> a + b + c + d);
    assertThat(parser.parse("abcd")).isEqualTo("abcd");
    assertThat(parser.matches("abcd")).isTrue();
    assertThat(parser.parseToStream("abcd")).containsExactly("abcd");
    assertThat(parser.probe("abcd")).containsExactly("abcd");
    assertThat(parser.parseToStream("")).isEmpty();
    assertThat(parser.probe("")).isEmpty();
  }

  @Test
  public void sequence4_success_source() {
    Parser<String> parser =
        sequence(string("a"), string("b"), string("c"), string("d"), (a, b, c, d) -> a + b + c + d);
    assertThat(parser.source().parse("abcd")).isEqualTo("abcd");
    assertThat(parser.source().matches("abcd")).isTrue();
    assertThat(parser.source().parseToStream("abcd")).containsExactly("abcd");
    assertThat(parser.source().probe("abcd")).containsExactly("abcd");
    assertThat(parser.source().parseToStream("")).isEmpty();
    assertThat(parser.source().probe("")).isEmpty();
  }

  @Test
  public void sequence4_failure() {
    Parser<String> parser =
        sequence(string("a"), string("b"), string("c"), string("d"), (a, b, c, d) -> a + b + c + d);
    assertThrows(ParseException.class, () -> parser.parse("xbcd"));
    assertThrows(ParseException.class, () -> parser.parse("axcd"));
    assertThrows(ParseException.class, () -> parser.parse("abxd"));
    assertThrows(ParseException.class, () -> parser.parse("abcx"));
    assertThat(parser.matches("xbcd")).isFalse();
    assertThat(parser.matches("axcd")).isFalse();
    assertThat(parser.matches("abxd")).isFalse();
    assertThat(parser.matches("abcx")).isFalse();
    assertThat(parser.probe("xbcd")).isEmpty();
    assertThat(parser.probe("axcd")).isEmpty();
    assertThat(parser.probe("abxd")).isEmpty();
    assertThat(parser.probe("abcx")).isEmpty();
  }

  @Test
  public void orEmpty_delimitedBy_bothSides() {
    assertThat(word().orElse("").delimitedBy(",").parse("foo,bar"))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(word().orElse("").delimitedBy(",").matches("foo,bar")).isTrue();
    assertThat(word().orElse("").delimitedBy(",").notEmpty().parse("foo,bar"))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_bothSides_source() {
    assertThat(word().source().orElse("").delimitedBy(",").parse("foo,bar"))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(word().source().orElse("").delimitedBy(",").matches("foo,bar")).isTrue();
    assertThat(word().source().orElse("").delimitedBy(",").notEmpty().parse("foo,bar"))
        .containsExactly("foo", "bar")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_single() {
    assertThat(word().orElse("").delimitedBy(",").parse("foo")).containsExactly("foo");
    assertThat(word().orElse("").delimitedBy(",").matches("foo")).isTrue();
    assertThat(word().orElse("").delimitedBy(",").notEmpty().parse("foo")).containsExactly("foo");
  }

  @Test
  public void orEmpty_delimitedBy_single_source() {
    assertThat(word().source().orElse("").delimitedBy(",").parse("foo")).containsExactly("foo");
    assertThat(word().source().orElse("").delimitedBy(",").matches("foo")).isTrue();
    assertThat(word().source().orElse("").delimitedBy(",").notEmpty().parse("foo"))
        .containsExactly("foo");
  }

  @Test
  public void orEmpty_delimitedBy_trailingEmpty() {
    assertThat(word().orElse("").delimitedBy(",").parse("foo,"))
        .containsExactly("foo", "")
        .inOrder();
    assertThat(word().orElse("").delimitedBy(",").matches("foo,")).isTrue();
    assertThat(word().orElse("").delimitedBy(",").notEmpty().parse("foo,"))
        .containsExactly("foo", "")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_trailingEmpty_source() {
    assertThat(word().source().orElse("").delimitedBy(",").parse("foo,"))
        .containsExactly("foo", "")
        .inOrder();
    assertThat(word().source().orElse("").delimitedBy(",").matches("foo,")).isTrue();
    assertThat(word().source().orElse("").delimitedBy(",").notEmpty().parse("foo,"))
        .containsExactly("foo", "")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_leadingEmpty() {
    assertThat(word().orElse("").delimitedBy(",").parse(",bar"))
        .containsExactly("", "bar")
        .inOrder();
    assertThat(word().orElse("").delimitedBy(",").matches(",bar")).isTrue();
    assertThat(word().orElse("").delimitedBy(",").notEmpty().parse(",bar"))
        .containsExactly("", "bar")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_leadingEmpty_source() {
    assertThat(word().source().orElse("").delimitedBy(",").parse(",bar"))
        .containsExactly("", "bar")
        .inOrder();
    assertThat(word().source().orElse("").delimitedBy(",").matches(",bar")).isTrue();
    assertThat(word().source().orElse("").delimitedBy(",").notEmpty().parse(",bar"))
        .containsExactly("", "bar")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_kitchenSink() {
    assertThat(word().orElse("").delimitedBy(",").parse(",foo,bar,,"))
        .containsExactly("", "foo", "bar", "", "")
        .inOrder();
    assertThat(word().orElse("").delimitedBy(",").matches(",foo,bar,,")).isTrue();
    assertThat(word().orElse("").delimitedBy(",").notEmpty().parse(",foo,bar,,"))
        .containsExactly("", "foo", "bar", "", "")
        .inOrder();
  }

  @Test
  public void orEmpty_delimitedBy_kitchenSink_source() {
    assertThat(word().source().orElse("").delimitedBy(",").parse(",foo,bar,,"))
        .containsExactly("", "foo", "bar", "", "")
        .inOrder();
    assertThat(word().source().orElse("").delimitedBy(",").matches(",foo,bar,,")).isTrue();
    assertThat(word().source().orElse("").delimitedBy(",").notEmpty().parse(",foo,bar,,"))
        .containsExactly("", "foo", "bar", "", "");
  }

  @Test
  public void orEmpty_delimitedBy_allEmpty() {
    assertThat(word().orElse("").delimitedBy(",").parse(",,,")).containsExactly("", "", "", "");
    assertThat(word().orElse("").delimitedBy(",").matches(",,,")).isTrue();
    assertThat(word().orElse("").delimitedBy(",").notEmpty().parse(",,,"))
        .containsExactly("", "", "", "");
  }

  @Test
  public void orEmpty_delimitedBy_allEmpty_source() {
    assertThat(word().source().orElse("").delimitedBy(",").parse(",,,"))
        .containsExactly("", "", "", "");
    assertThat(word().source().orElse("").delimitedBy(",").matches(",,,")).isTrue();
    assertThat(word().source().orElse("").delimitedBy(",").notEmpty().parse(",,,"))
        .containsExactly("", "", "", "");
  }

  @Test
  public void orEmpty_delimitedBy_emptyInput() {
    assertThat(word().orElse("").delimitedBy(",").parse("")).containsExactly("");
    assertThat(word().orElse("").delimitedBy(",").matches("")).isTrue();
    assertThrows(
        ParseException.class, () -> word().orElse("").delimitedBy(",").notEmpty().parse(""));
  }

  @Test
  public void orEmpty_delimitedBy_emptyInput_source() {
    assertThat(word().source().orElse("").delimitedBy(",").parse("")).containsExactly("");
    assertThat(word().source().orElse("").delimitedBy(",").matches("")).isTrue();
    assertThrows(
        ParseException.class,
        () -> word().source().orElse("").delimitedBy(",").notEmpty().parse(""));
  }

  @Test
  public void orEmpty_then_parser_orEmptyMatches() {
    Parser<String> parser = string("a").orElse("x").then(string("b"));
    assertThat(parser.parse("ab")).isEqualTo("b");
  }

  @Test
  public void orEmpty_then_parser_orEmptyEmitsDefault() {
    Parser<String> parser = string("a").orElse("x").then(string("b"));
    assertThat(parser.parse("b")).isEqualTo("b");
  }

  @Test
  public void orEmpty_then_parser_suffixFails() {
    Parser<String> parser = string("a").orElse("x").then(string("b"));
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("c"));
  }

  @Test
  public void orEmpty_followedBy_parser_orEmptyMatches() {
    Parser<String> parser = string("a").orElse("x").followedBy(string("b"));
    assertThat(parser.parse("ab")).isEqualTo("a");
  }

  @Test
  public void orEmpty_followedBy_parser_orEmptyEmitsDefault() {
    Parser<String> parser = string("a").orElse("x").followedBy(string("b"));
    assertThat(parser.parse("b")).isEqualTo("x");
  }

  @Test
  public void orEmpty_followedBy_parser_suffixFails() {
    Parser<String> parser = string("a").orElse("x").followedBy(string("b"));
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("c"));
  }

  @Test
  public void orEmpty_parseSkipping_emptyInput() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), "")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), "")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_emptyInput_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), "")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), "")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSingleSkip() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), " ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), " ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSingleSkip_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), " ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), " ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithMultipleSkips() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), "   ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), "   ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithMultipleSkips_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), "   ")).isEqualTo("bar");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), "   ")).isEqualTo("bar");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSkipsAndValue() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), " foo ")).isEqualTo("foo");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), " foo ")).isEqualTo("foo");
  }

  @Test
  public void orEmpty_parseSkipping_inputWithSkipsAndValue_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThat(parser.parseSkipping(whitespace(), " foo ")).isEqualTo("foo");
    assertThat(parser.parseSkipping(consecutive(whitespace(), "skip"), " foo ")).isEqualTo("foo");
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithoutSkips() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "fo"));
    assertThrows(
        ParseException.class, () -> parser.parseSkipping(consecutive(whitespace(), "skip"), "fo"));
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithoutSkips_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "fo"));
    assertThrows(
        ParseException.class, () -> parser.parseSkipping(consecutive(whitespace(), "skip"), "fo"));
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithSkips() {
    Parser<String>.OrEmpty parser = string("foo").orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " fo "));
    assertThrows(
        ParseException.class,
        () -> parser.parseSkipping(consecutive(whitespace(), "skip"), " fo "));
  }

  @Test
  public void orEmpty_parseSkipping_invalidInputWithSkips_source() {
    Parser<String>.OrEmpty parser = string("foo").source().orElse("bar");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " fo "));
    assertThrows(
        ParseException.class,
        () -> parser.parseSkipping(consecutive(whitespace(), "skip"), " fo "));
  }

  @Test
  public void or_success() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.parseToStream("foo")).containsExactly("foo");
    assertThat(parser.parse("bar")).isEqualTo("bar");
    assertThat(parser.parseToStream("bar")).containsExactly("bar");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void or_success_source() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThat(parser.source().parse("foo")).isEqualTo("foo");
    assertThat(parser.source().parseToStream("foo")).containsExactly("foo");
    assertThat(parser.source().parse("bar")).isEqualTo("bar");
    assertThat(parser.source().parseToStream("bar")).containsExactly("bar");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void or_failure_withLeftover() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThrows(ParseException.class, () -> parser.parse("fooa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("fooa").toList());
    assertThrows(ParseException.class, () -> parser.parse("bara"));
    assertThrows(ParseException.class, () -> parser.parseToStream("bara").toList());
  }

  @Test
  public void or_failure() {
    Parser<String> parser = string("foo").or(string("bar"));
    assertThrows(ParseException.class, () -> parser.parse("baz"));
    assertThrows(ParseException.class, () -> parser.parseToStream("baz").toList());
  }

  @Test
  public void or_collectorWithSingleParser_returnsSameInstance() {
    Parser<String> parser = string("a");
    assertThat(Stream.of(parser).collect(Parser.or())).isSameInstanceAs(parser);
  }

  @Test
  public void or_collectorWithEmptyStream_throws() {
    assertThrows(
        IllegalArgumentException.class, () -> Stream.<Parser<String>>of().collect(Parser.or()));
  }

  @Test
  public void or_withOrEmpty() {
    Parser<String>.OrEmpty parser = string("foo").or(string("bar").orElse("default"));
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThat(parser.matches("foo")).isTrue();
    assertThat(parser.parse("bar")).isEqualTo("bar");
    assertThat(parser.matches("bar")).isTrue();
    assertThat(parser.parse("")).isEqualTo("default");
    assertThat(parser.matches("")).isTrue();
  }

  @Test
  public void anyOf_success() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThat(parser.parse("one")).isEqualTo("one");
    assertThat(parser.parseToStream("one")).containsExactly("one");
    assertThat(parser.parse("two")).isEqualTo("two");
    assertThat(parser.parseToStream("two")).containsExactly("two");
    assertThat(parser.parse("three")).isEqualTo("three");
    assertThat(parser.parseToStream("three")).containsExactly("three");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void anyOf_success_source() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThat(parser.source().parse("one")).isEqualTo("one");
    assertThat(parser.source().parseToStream("one")).containsExactly("one");
    assertThat(parser.source().parse("two")).isEqualTo("two");
    assertThat(parser.source().parseToStream("two")).containsExactly("two");
    assertThat(parser.source().parse("three")).isEqualTo("three");
    assertThat(parser.source().parseToStream("three")).containsExactly("three");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void anyOf_failure_withLeftover() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThrows(ParseException.class, () -> parser.parse("onea"));
    assertThrows(ParseException.class, () -> parser.parseToStream("onea").toList());
    assertThrows(ParseException.class, () -> parser.parse("twoa"));
    assertThrows(ParseException.class, () -> parser.parseToStream("twoa").toList());
    assertThrows(ParseException.class, () -> parser.parse("threea"));
    assertThrows(ParseException.class, () -> parser.parseToStream("threea").toList());
  }

  @Test
  public void anyOf_failure() {
    Parser<String> parser = anyOf(string("one"), string("two"), string("three"));
    assertThrows(ParseException.class, () -> parser.parse("four"));
    assertThrows(ParseException.class, () -> parser.parseToStream("four").toList());
  }

  @Test
  public void anyOf_pruningTriggered_withTwoNonPrunableCandidates() {
    // 9 prunable strings + 2 non-prunable candidates = 11 total.
    // Pruning should be triggered because numSurvivors(3) * 2 < 11.
    Parser<String> parser =
        anyOf(
            string("a1"),
            string("a2"),
            string("a3"),
            string("a4"),
            string("a5"),
            string("a6"),
            string("a7"),
            string("a8"),
            string("a9"),
            digits(),
            chars(2));

    assertThat(parser.parse("a1")).isEqualTo("a1");
    assertThat(parser.parse("a9")).isEqualTo("a9");
    assertThat(parser.parse("xy")).isEqualTo("xy"); // chars(2)
    assertThat(parser.parse("123")).isEqualTo("123"); // digits()

    // Failure with pruning. farthestFailure is the first candidate ("a1")
    ParseException e1 = assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThat(e1).hasMessageThat().contains("expecting <2 char(s)>");

    // Failure with completely mismatched input. "ba" matches chars(2), leftovers "r" causes EOF
    // error.
    ParseException e2 = assertThrows(ParseException.class, () -> parser.parse("bar"));
    assertThat(e2).hasMessageThat().contains("expecting <EOF>");
  }

  @Test
  public void anyOf_pruningSuppressed_withManyNonPrunableCandidates() {
    // 3 prunable strings + 8 non-prunable candidates = 11 total.
    // Pruning NOT triggered because numSurvivors(9) * 2 > 11.
    Parser<String> parser =
        anyOf(
            string("a1"),
            string("a2"),
            string("a3"),
            chars(8),
            chars(7),
            chars(6),
            chars(5),
            chars(4),
            chars(3),
            chars(2),
            chars(1));

    assertThat(parser.parse("a1")).isEqualTo("a1");
    assertThat(parser.parse("a")).isEqualTo("a"); // chars(1)
    assertThat(parser.parse("abcdefgh")).isEqualTo("abcdefgh"); // chars(8)

    // Failure without pruning. Reporting the first candidate ("a1").
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(e).hasMessageThat().contains("expecting <a1>");
  }

  @Test
  public void anyOf_pruningSuppressed_withMixedSkippingBehavior() {
    // 10 prunable skippable strings + 1 non-prunable non-skippable candidate = 11 total.
    // Pruning NOT triggered because honorsSkipping varies among candidates.
    Parser<String> parser =
        anyOf(
            string("a1"),
            string("a2"),
            string("a3"),
            string("a4"),
            string("a5"),
            string("a6"),
            string("a7"),
            string("a8"),
            string("a9"),
            string("a10"),
            literally(string("b")));

    assertThat(parser.parse("a1")).isEqualTo("a1");
    assertThat(parser.parse("b")).isEqualTo("b");

    // Failure with mixed skipping. Reporting the first candidate ("a1").
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("c"));
    assertThat(e).hasMessageThat().contains("expecting <a1>");
  }

  @Test
  public void anyOf_commonPrefixPruning() {
    // 11 candidates sharing a common prefix "prefix".
    // Use prefix01...prefix11 so none is a prefix of another.
    Parser<String> parser =
        anyOf(
            string("prefix01"),
            string("prefix02"),
            string("prefix03"),
            string("prefix04"),
            string("prefix05"),
            string("prefix06"),
            string("prefix07"),
            string("prefix08"),
            string("prefix09"),
            string("prefix10"),
            string("prefix11"));

    assertThat(parser.parse("prefix01")).isEqualTo("prefix01");
    assertThat(parser.parse("prefix11")).isEqualTo("prefix11");

    // Failure with common prefix. Reporting the first candidate "prefix01" because pruning is used.
    ParseException e1 = assertThrows(ParseException.class, () -> parser.parse("prefix"));
    assertThat(e1).hasMessageThat().contains("expecting <prefix01>");

    // Failure with completely different input.
    ParseException e2 = assertThrows(ParseException.class, () -> parser.parse("other"));
    assertThat(e2).hasMessageThat().contains("expecting <prefix01>");
  }

  @Test
  public void anyOf_orderSensitivityWithPruning() {
    // 11 candidates, where some are prefixes of others to test order sensitivity.
    Parser<String> parser =
        anyOf(
            string("abc").followedBy("d"),
            string("ab").followedBy("cd").thenReturn("ab_cd"),
            string("a"),
            string("x1"),
            string("x2"),
            string("x3"),
            string("x4"),
            string("x5"),
            string("x6"),
            string("x7"),
            string("x8"));

    assertThat(parser.parse("abcd")).isEqualTo("abc");

    // "ab" followed by "cd" would match "abcd" too, but "abc" is earlier.
    // If input is "ax", "a" matches but followed by leftover "x", so it fails.
    assertThrows(ParseException.class, () -> parser.parse("ax"));

    // Test that "ab_cd" can be matched if the first one fails.
    assertThat(
            anyOf(
                    string("abc").followedBy("e"),
                    string("ab").followedBy("cd").thenReturn("ab_cd"),
                    string("a"))
                .parse("abcd"))
        .isEqualTo("ab_cd");
  }

  @Test
  public void anyOf_nestedAnyOf_nonePrunable() {
    Parser<String> nested = anyOf(caseInsensitive("foo"), chars(2)).map(Object::toString);
    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("a" + i)), Stream.of(nested)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("a1")).isEqualTo("a1");
    assertThat(outer.parse("a9")).isEqualTo("a9");
    assertThat(outer.parse("bc")).isEqualTo("bc");
    assertThat(outer.parse("FOO")).isEqualTo("foo");

    ParseException e = assertThrows(ParseException.class, () -> outer.parse("x"));
    assertThat(e).hasMessageThat().contains("expecting <foo>");
  }

  @Test
  public void anyOf_nestedAnyOf_oneNotPrunable() {
    Parser<String> nested = anyOf(chars(2), string("b")).map(Object::toString);
    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("a" + i)), Stream.of(nested)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("a1")).isEqualTo("a1");
    assertThat(outer.parse("b ")).isEqualTo("b ");
    assertThat(outer.parse("bc")).isEqualTo("bc");

    ParseException e = assertThrows(ParseException.class, () -> outer.parse("x"));
    assertThat(e).hasMessageThat().contains("expecting <2 char(s)>");
  }

  @Test
  public void anyOf_nestedAnyOf_allPrunable() {
    List<Parser<String>> parsers =
        concat(
                range(0, 10).mapToObj(i -> string("a" + i)),
                Stream.of(anyOf(string("b1"), string("b2")).map(Object::toString)))
            .toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("a1")).isEqualTo("a1");
    assertThat(outer.parse("b1")).isEqualTo("b1");
    assertThat(outer.parse("b2")).isEqualTo("b2");

    ParseException e = assertThrows(ParseException.class, () -> outer.parse("b3"));
    assertThat(e).hasMessageThat().contains("expecting <a0>");
  }

  @Test
  public void anyOf_nestedAnyOf_allHonorSkipping() {
    Parser<String> nested = anyOf(string("b1"), string("b2")).map(Object::toString);
    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("a" + i)), Stream.of(nested)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parseSkipping(whitespace(), "  b1")).isEqualTo("b1");
    assertThat(outer.parseSkipping(whitespace(), "  a1")).isEqualTo("a1");
  }

  @Test
  public void anyOf_nestedAnyOf_mixedSkippingSuppressesPruning() {
    Parser<String> nested = anyOf(literally(string("b1")), string("b2")).map(Object::toString);
    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("a" + i)), Stream.of(nested)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parseSkipping(whitespace(), "  b2")).isEqualTo("b2");
    assertThat(outer.parseSkipping(whitespace(), "b1")).isEqualTo("b1");
    assertThrows(ParseException.class, () -> outer.parseSkipping(whitespace(), "  b1"));
  }

  @Test
  public void anyOf_nestedAnyOf_allLiterallyPrunable() {
    Parser<String> nested =
        anyOf(literally(string("b1")), literally(string("b2"))).map(Object::toString);
    List<Parser<String>> parsers =
        concat(
                range(0, 10).mapToObj(i -> literally(string("a" + i)).map(Object::toString)),
                Stream.of(nested))
            .toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("b1")).isEqualTo("b1");
    assertThat(outer.parse("a1")).isEqualTo("a1");
    assertThrows(ParseException.class, () -> outer.parseSkipping(whitespace(), "  b1"));
    assertThrows(ParseException.class, () -> outer.parseSkipping(whitespace(), "  a1"));
  }

  @Test
  public void anyOf_pruning_propagatesThroughSequence() {
    Parser<String> p = sequence(string("a"), string("b"), (a, b) -> a + b);
    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("x" + i)), Stream.of(p)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("ab")).isEqualTo("ab");
    assertThat(outer.parse("x1")).isEqualTo("x1");
    assertThrows(ParseException.class, () -> outer.parse("ac"));
  }

  @Test
  public void anyOf_pruning_propagatesThroughAtLeastOnce() {
    Parser<String> p = string("a").atLeastOnce().map(list -> list.get(0));
    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("x" + i)), Stream.of(p)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("aaa")).isEqualTo("a");
    assertThat(outer.parse("x1")).isEqualTo("x1");
  }

  @Test
  public void anyOf_pruning_withLargeNestedPrefixSet() {
    // Nested anyOf with multiple prefixes: ["a", "b"]
    Parser<String> nested = anyOf(string("a"), string("b")).map(Object::toString);
    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("x" + i)), Stream.of(nested)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("a")).isEqualTo("a");
    assertThat(outer.parse("b")).isEqualTo("b");
    assertThat(outer.parse("x1")).isEqualTo("x1");
    assertThrows(ParseException.class, () -> outer.parse("c"));
  }

  @Test
  public void anyOf_pruning_withOneNestedCandidateHavingNoPrefix() {
    // Nested anyOf where one candidate used to have no prefix (digits)
    // Now digits() exposes prefixes '0'-'9'.
    Parser<String> nested = anyOf(string("a"), digits()).map(Object::toString);
    assertThat(nested.getPrefixes())
        .containsExactly("a", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

    List<Parser<String>> parsers =
        concat(range(0, 10).mapToObj(i -> string("x" + i)), Stream.of(nested)).toList();
    Parser<String> outer = parsers.stream().collect(or());

    assertThat(outer.parse("a")).isEqualTo("a");
    assertThat(outer.parse("123")).isEqualTo("123");
    assertThat(outer.parse("x1")).isEqualTo("x1");
  }

  @Test
  public void anyOf_pruning_withDigits() {
    Parser<String> parser = anyOf(digits(), string("abc"));
    assertThat(parser.getPrefixes())
        .containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "abc");
    assertThat(parser.parse("123")).isEqualTo("123");
    assertThat(parser.parse("abc")).isEqualTo("abc");
    assertThrows(ParseException.class, () -> parser.parse("x"));
  }

  @Test
  public void anyOf_pruningTriggered_withDigits() {
    // 10 prunable strings + digits() = 11 total candidates.
    // Pruning should be triggered.
    Parser<String> parser =
        anyOf(
            string("a"),
            string("b"),
            string("c"),
            string("d"),
            string("e"),
            string("f"),
            string("g"),
            string("h"),
            string("i"),
            string("j"),
            digits());

    assertThat(parser.parse("a")).isEqualTo("a");
    assertThat(parser.parse("123")).isEqualTo("123");

    // Failure with pruning. Should report the first candidate ("a").
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("x"));
    assertThat(e).hasMessageThat().contains("expecting <a>");
  }

  @Test
  public void anyOf_pruning_withWordNoArg() {
    Parser<String> parser = anyOf(word(), string("!"));
    assertThat(parser.getPrefixes()).contains("a");
    assertThat(parser.getPrefixes()).contains("Z");
    assertThat(parser.getPrefixes()).contains("0");
    assertThat(parser.getPrefixes()).contains("_");
    assertThat(parser.getPrefixes()).contains("!");

    assertThat(parser.parse("hello")).isEqualTo("hello");
    assertThat(parser.parse("!")).isEqualTo("!");

    assertThrows(ParseException.class, () -> parser.parse("@"));
  }

  @Test
  public void anyOf_commonPrefixPruning_withWord() {
    // 11 candidates sharing a common prefix "word".
    Parser<String> parser =
        anyOf(
            word("word01"),
            word("word02"),
            word("word03"),
            word("word04"),
            word("word05"),
            word("word06"),
            word("word07"),
            word("word08"),
            word("word09"),
            word("word10"),
            word("word11"));

    assertThat(parser.parse("word01")).isEqualTo("word01");
    assertThat(parser.parse("word11")).isEqualTo("word11");

    // Failure with common prefix. Should report the first candidate "word01".
    ParseException e1 = assertThrows(ParseException.class, () -> parser.parse("word"));
    assertThat(e1).hasMessageThat().contains("expecting <word01>");
  }

  @Test
  public void anyOf_pruning_withNegativeCharacterSet() {
    // 10 prunable strings + consecutive(negativeCharacterSet) = 11 total.
    // Pruning should be triggered.
    Parser<String> parser =
        anyOf(
            string("a1"),
            string("a2"),
            string("a3"),
            string("a4"),
            string("a5"),
            string("a6"),
            string("a7"),
            string("a8"),
            string("a9"),
            string("a10"),
            consecutive(charsIn("[^a]"), "not-a"));

    // "b" is not 'a'. It should be matched by consecutive(charsIn("[^a]"))
    assertThat(parser.parse("b")).isEqualTo("b");

    // "a1" should be matched by string("a1")
    assertThat(parser.parse("a1")).isEqualTo("a1");

    // "a" followed by nothing fails all.
    assertThrows(ParseException.class, () -> parser.parse("a"));
  }

  @Test
  public void anyOf_pruning_withConsecutiveRange_matching() {
    // 10 prunable strings + consecutive("[a-a]") = 11 total.
    // Pruning should be triggered.
    Parser<String> parser =
        anyOf(
            string("b1"),
            string("b2"),
            string("b3"),
            string("b4"),
            string("b5"),
            string("b6"),
            string("b7"),
            string("b8"),
            string("b9"),
            string("b10"),
            consecutive(charsIn("[a-a]")));

    assertThat(parser.parse("a")).isEqualTo("a");
    assertThat(parser.parse("aa")).isEqualTo("aa");

    // Failure with completely mismatched input.
    // Should fallback to first candidate because of pruning.
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("x"));
    assertThat(e).hasMessageThat().contains("expecting <b1>");
  }

  @Test
  public void anyOf_pruning_withConsecutiveRange_neverMatching() {
    // 10 prunable strings + consecutive("[1-0]") = 11 total.
    // consecutive("[1-0]") should never match.
    // Pruning should be triggered for the other 10.
    Parser<String> parser =
        anyOf(
            string("a1"),
            string("a2"),
            string("a3"),
            string("a4"),
            string("a5"),
            string("a6"),
            string("a7"),
            string("a8"),
            string("a9"),
            string("a10"),
            consecutive(charsIn("[1-0]")));

    // "a1" should be matched by string("a1")
    assertThat(parser.parse("a1")).isEqualTo("a1");

    // "1" should fail and report the first candidate because of pruning.
    ParseException e1 = assertThrows(ParseException.class, () -> parser.parse("1"));
    assertThat(e1).hasMessageThat().contains("expecting <a1>");

    // "b" fails all.
    ParseException e2 = assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThat(e2).hasMessageThat().contains("expecting <a1>");
  }

  @Test
  public void anyOf_pruning_withConsecutiveSet_containingCaret() {
    // 10 prunable strings + consecutive("[ab^c]") = 11 total.
    // Pruning should be triggered.
    Parser<String> parser =
        anyOf(
            string("x1"),
            string("x2"),
            string("x3"),
            string("x4"),
            string("x5"),
            string("x6"),
            string("x7"),
            string("x8"),
            string("x9"),
            string("x10"),
            consecutive(charsIn("[ab^c]")));

    assertThat(parser.parse("a")).isEqualTo("a");
    assertThat(parser.parse("b")).isEqualTo("b");
    assertThat(parser.parse("^")).isEqualTo("^");
    assertThat(parser.parse("c")).isEqualTo("c");

    // Failure with completely mismatched input.
    // Should fallback to first candidate because of pruning.
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("y"));
    assertThat(e).hasMessageThat().contains("expecting <x1>");
  }

  @Test
  public void word_getPrefixes() {
    assertThat(word().getPrefixes())
        .containsExactly(
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
            "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "_");
  }

  @Test
  public void digits_getPrefixes() {
    assertThat(digits().getPrefixes())
        .containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
  }

  @Test
  public void anyOf_pruning_withOneCharacterSet_matching() {
    Parser<Character> parser =
        anyOf(
            one('0'),
            one('1'),
            one('2'),
            one('3'),
            one('4'),
            one('5'),
            one('6'),
            one('7'),
            one('8'),
            one('9'),
            one(charsIn("[a-a]")));

    assertThat(parser.parse("a")).isEqualTo('a');

    ParseException e = assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThat(e).hasMessageThat().contains("expecting <0>");
  }

  @Test
  public void anyOf_pruning_withOneCharacterSet_neverMatching() {
    Parser<Character> parser =
        anyOf(
            one('0'),
            one('1'),
            one('2'),
            one('3'),
            one('4'),
            one('5'),
            one('6'),
            one('7'),
            one('8'),
            one('9'),
            one(charsIn("[1-0]"))); // never matches

    assertThat(parser.parse("0")).isEqualTo('0');

    ParseException e = assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThat(e).hasMessageThat().contains("expecting <0>");
  }

  @Test
  public void anyOf_pruning_withMixedPrunableAndUnprunable() {
    Parser<String> unprunable = one(c -> c == 'u', "u").map(Object::toString);
    Parser<String> prunable = string("p");

    // 8 consecutive("[]") + 1 unprunable + 1 prunable = 10 total.
    Parser<String> parser =
        anyOf(
            unprunable,
            consecutive(charsIn("[]")),
            consecutive(charsIn("[]")),
            consecutive(charsIn("[]")),
            consecutive(charsIn("[]")),
            consecutive(charsIn("[]")),
            consecutive(charsIn("[]")),
            consecutive(charsIn("[]")),
            consecutive(charsIn("[]")),
            prunable);

    // Prunable succeeds.
    assertThat(parser.parse("p")).isEqualTo("p");

    // Unprunable succeeds because it's the first candidate and used as fallback when no prefix matches.
    assertThat(parser.parse("u")).isEqualTo("u");

    // Failure case. Falls back to first candidate (unprunable) and fails.
    ParseException e = assertThrows(ParseException.class, () -> parser.parse("z"));
    assertThat(e).hasMessageThat().contains("expecting <u>");
  }

  @Test
  public void anyOf_pruning_withCaseInsensitive() {
    List<Parser<String>> parsers =
        range(0, 10).mapToObj(i -> caseInsensitive("a" + i).map(Object::toString)).toList();
    Parser<String> parser = parsers.stream().collect(or());

    assertThat(parser.parse("A5")).isEqualTo("a5");
    assertThat(parser.parse("a5")).isEqualTo("a5");
    // verify pruning by providing an input that doesn't start with any 'a' or 'A'
    assertThrows(ParseException.class, () -> parser.parse("B1"));
  }

  @Test
  public void anyOf_pruning_withCaseInsensitiveWord() {
    List<Parser<String>> parsers =
        range(0, 10).mapToObj(i -> caseInsensitiveWord("word" + i).map(Object::toString)).toList();
    Parser<String> parser = parsers.stream().collect(or());

    assertThat(parser.parse("WORD5")).isEqualTo("word5");
    assertThat(parser.parse("word5")).isEqualTo("word5");
    // verify pruning
    assertThrows(ParseException.class, () -> parser.parse("A1"));
  }

  @Test
  public void anyOf_pruning_withLongInputAndShortPrefix() {
    List<Parser<String>> parsers = range(0, 100).mapToObj(i -> string("a" + i)).toList();
    Parser<String> parser = parsers.stream().collect(or());

    // Very long trailing data should not affect prefix pruning efficiency.
    String longInput = "a1" + "z".repeat(10000);
    Parser<String> p = parser.followedBy(zeroOrMore(is('z'), "z"));
    assertThat(p.parse(longInput)).isEqualTo("a1");
  }

  @Test
  public void anyOf_pruning_withWord() {
    List<Parser<String>> parsers = range(0, 10).mapToObj(i -> word("word" + i)).toList();
    Parser<String> parser = parsers.stream().collect(or());

    assertThat(parser.parse("word5")).isEqualTo("word5");
    // verify that word("word5") doesn't match "word5x"
    assertThrows(ParseException.class, () -> parser.parse("word5x"));
    // verify that word("word5") works as a prefix for "word5 "
    assertThat(parser.parseSkipping(whitespace(), "word5 ")).isEqualTo("word5");
  }

  @Test
  public void anyOf_pruning_withOneChar() {
    List<Parser<Character>> parsers = range(0, 20).mapToObj(i -> one((char) ('a' + i))).toList();
    Parser<Character> parser = parsers.stream().collect(or());

    assertThat(parser.parse("f")).isEqualTo('f');
    assertThat(parser.parse("s")).isEqualTo('s');
    assertThrows(ParseException.class, () -> parser.parse("z"));
    assertThrows(ParseException.class, () -> parser.parse("!"));
  }

  @Test
  public void atLeastOnce_success() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThat(parser.parse("a")).containsExactly("a");
    assertThat(parser.parseToStream("a")).containsExactly(List.of("a"));
    assertThat(parser.parse("aa")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("aa")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("aaa")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("aaa"))
        .containsExactly(List.of("a", "a", "a"))
        .inOrder();
    assertThat(parser.parseToStream("")).isEmpty();

    assertThat(digits().atLeastOnce().parse("1230")).containsExactly("1230");
    assertThat(digits().atLeastOnce().parseToStream("1230"))
        .containsExactly(List.of("1230"));
    assertThat(digits().atLeastOnce().parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnce_success_source() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThat(parser.source().parse("a")).isEqualTo("a");
    assertThat(parser.source().parseToStream("a")).containsExactly("a");
    assertThat(parser.source().parse("aa")).isEqualTo("aa");
    assertThat(parser.source().parseToStream("aa")).containsExactly("aa");
    assertThat(parser.source().parse("aaa")).isEqualTo("aaa");
    assertThat(parser.source().parseToStream("aaa")).containsExactly("aaa");
    assertThat(parser.source().parseToStream("")).isEmpty();

    assertThat(digits().atLeastOnce().source().parse("1230")).isEqualTo("1230");
    assertThat(digits().atLeastOnce().source().parseToStream("1230")).containsExactly("1230");
    assertThat(digits().atLeastOnce().source().parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnce_failure_withLeftover() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse("ab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("ab").toList());
    assertThrows(ParseException.class, () -> parser.parse("aab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("aab").toList());
  }

  @Test
  public void atLeastOnce_failure() {
    Parser<List<String>> parser = string("a").atLeastOnce();
    assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThrows(ParseException.class, () -> parser.parseToStream("b").toList());
    assertThrows(ParseException.class, () -> parser.parse("aab"));
    assertThrows(ParseException.class, () -> parser.parseToStream("aab").toList());
  }

  @Test
  public void atLeastOnce_withReducer_success() {
    Parser<Integer> integer = one(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnce((a, b) -> a - b);
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parse("12")).isEqualTo(-1);
    assertThat(parser.parse("123")).isEqualTo(-4);
  }

  @Test
  public void atLeastOnce_withReducer_failure_withLeftover() {
    Parser<Integer> integer = one(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnce((a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parse("12a"));
  }

  @Test
  public void atLeastOnce_withReducer_failure() {
    Parser<Integer> integer = one(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnce((a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse(""));
    assertThrows(ParseException.class, () -> parser.parse("a"));
  }

  @Test
  public void zeroOrMore_between_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_between_zeroMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMore_between_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_between_oneMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMore_between_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThat(parser.parse("[aa]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[aa]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[aaa]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[aaa]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_between_multipleMatches_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().between("[", "]");
    assertThat(parser.source().parse("[aa]")).isEqualTo("[aa]");
    assertThat(parser.source().parseToStream("[aa]")).containsExactly("[aa]");
    assertThat(parser.source().parse("[aaa]")).isEqualTo("[aaa]");
    assertThat(parser.source().parseToStream("[aaa]")).containsExactly("[aaa]");
  }

  @Test
  public void zeroOrMore_followedBy_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_followedBy_zeroMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMore_followedBy_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_followedBy_oneMatch_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMore_followedBy_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(";");
    assertThat(parser.parse("aa;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("aa;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("aaa;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("aaa;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_followedBy_multipleMatches_source() {
    Parser<List<String>> parser = string("a").source().zeroOrMore().followedBy(";");
    assertThat(parser.source().parse("aa;")).isEqualTo("aa;");
    assertThat(parser.source().parseToStream("aa;")).containsExactly("aa;");
    assertThat(parser.source().parse("aaa;")).isEqualTo("aaa;");
    assertThat(parser.source().parseToStream("aaa;")).containsExactly("aaa;");
  }

  @Test
  public void zeroOrMore_betweenParsers_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_betweenParsers_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMore_betweenParsers_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_betweenParsers_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMore_betweenParsers_multipleMatches() {
    Parser<List<String>> parser =
        string("a").zeroOrMore().between(string("["), string("]"));
    assertThat(parser.parse("[aa]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[aa]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[aaa]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[aaa]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_betweenParsers_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().between(string("["), string("]"));
    assertThat(parser.source().parse("[aa]")).isEqualTo("[aa]");
    assertThat(parser.source().parseToStream("[aa]")).containsExactly("[aa]");
    assertThat(parser.source().parse("[aaa]")).isEqualTo("[aaa]");
    assertThat(parser.source().parseToStream("[aaa]")).containsExactly("[aaa]");
  }

  @Test
  public void zeroOrMore_followedByParser_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMore_followedByParser_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMore_followedByParser_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMore_followedByParser_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMore_followedByParser_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMore().followedBy(string(";"));
    assertThat(parser.parse("aa;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("aa;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("aaa;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("aaa;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMore_followedByParser_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMore().followedBy(string(";"));
    assertThat(parser.source().parse("aa;")).isEqualTo("aa;");
    assertThat(parser.source().parseToStream("aa;")).containsExactly("aa;");
    assertThat(parser.source().parse("aaa;")).isEqualTo("aaa;");
    assertThat(parser.source().parseToStream("aaa;")).containsExactly("aaa;");
  }

  @Test
  public void zeroOrMore_between_failure() {
    Parser<List<String>> parser = string("a").zeroOrMore().between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[ab]"));
    assertThrows(ParseException.class, () -> parser.parse("[a]b"));
  }

  @Test
  public void zeroOrMore_parseEmpty() {
    assertThat(string("a").zeroOrMore().parse("")).isEmpty();
  }

  @Test
  public void zeroOrMore_parseEmpty_source() {
    assertThat(string("a").source().zeroOrMore().parse("")).isEmpty();
  }

  @Test
  public void zeroOrMore_parseNonEmpty() {
    assertThat(string("a").zeroOrMore().parse("aa")).containsExactly("a", "a").inOrder();
  }

  @Test
  public void zeroOrMore_parseNonEmpty_source() {
    assertThat(string("a").source().zeroOrMore().parse("aa")).containsExactly("a", "a").inOrder();
  }

  @Test
  public void zeroOrMore_parseFail() {
    assertThrows(ParseException.class, () -> string("a").zeroOrMore().parse("b"));
    assertThat(string("a").zeroOrMore().matches("b")).isFalse();
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.parse("[a,a]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[a,a,a]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a,a]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThat(parser.source().parse("[a,a]")).isEqualTo("[a,a]");
    assertThat(parser.source().parseToStream("[a,a]")).containsExactly("[a,a]");
    assertThat(parser.source().parse("[a,a,a]")).isEqualTo("[a,a,a]");
    assertThat(parser.source().parseToStream("[a,a,a]")).containsExactly("[a,a,a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_zeroMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_oneMatch() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_multipleMatches() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.parse("a,a;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("a,a;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("a,a,a;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("a,a,a;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedBy_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(";");
    assertThat(parser.source().parse("a,a;")).isEqualTo("a,a;");
    assertThat(parser.source().parseToStream("a,a;")).containsExactly("a,a;");
    assertThat(parser.source().parse("a,a,a;")).isEqualTo("a,a,a;");
    assertThat(parser.source().parseToStream("a,a,a;")).containsExactly("a,a,a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[a]")).containsExactly("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_multipleMatches() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.parse("[a,a]")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a]")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("[a,a,a]")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("[a,a,a]")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_betweenParsers_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").between(string("["), string("]"));
    assertThat(parser.source().parse("[a,a]")).isEqualTo("[a,a]");
    assertThat(parser.source().parseToStream("[a,a]")).containsExactly("[a,a]");
    assertThat(parser.source().parse("[a,a,a]")).isEqualTo("[a,a,a]");
    assertThat(parser.source().parseToStream("[a,a,a]")).containsExactly("[a,a,a]");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_zeroMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(List.of());
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_zeroMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_oneMatch() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse("a;")).containsExactly("a");
    assertThat(parser.parseToStream("a;")).containsExactly(List.of("a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_oneMatch_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_multipleMatches() {
    Parser<List<String>> parser =
        string("a").zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.parse("a,a;")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("a,a;")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("a,a,a;")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("a,a,a;")).containsExactly(List.of("a", "a", "a"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_followedByParser_multipleMatches_source() {
    Parser<List<String>> parser =
        string("a").source().zeroOrMoreDelimitedBy(",").followedBy(string(";"));
    assertThat(parser.source().parse("a,a;")).isEqualTo("a,a;");
    assertThat(parser.source().parseToStream("a,a;")).containsExactly("a,a;");
    assertThat(parser.source().parse("a,a,a;")).isEqualTo("a,a,a;");
    assertThat(parser.source().parseToStream("a,a,a;")).containsExactly("a,a,a;");
  }

  @Test
  public void zeroOrMoreDelimitedBy_between_failure() {
    Parser<List<String>> parser = string("a").zeroOrMoreDelimitedBy(",").between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[a,b]"));
    assertThrows(ParseException.class, () -> parser.parse("[a,]"));
    assertThrows(ParseException.class, () -> parser.parse("[a,a,]"));
    assertThrows(ParseException.class, () -> parser.parse("[,a]"));
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseEmpty() {
    assertThat(string("a").zeroOrMoreDelimitedBy(",").parse("")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseEmpty_source() {
    assertThat(string("a").source().zeroOrMoreDelimitedBy(",").parse("")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseNonEmpty() {
    assertThat(string("a").zeroOrMoreDelimitedBy(",").parse("a,a"))
        .containsExactly("a", "a")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseNonEmpty_source() {
    assertThat(string("a").source().zeroOrMoreDelimitedBy(",").parse("a,a"))
        .containsExactly("a", "a")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parseFail() {
    assertThrows(ParseException.class, () -> string("a").zeroOrMoreDelimitedBy(",").parse("b"));
    assertThat(string("a").zeroOrMoreDelimitedBy(",").matches("b")).isFalse();
  }

  @Test
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter() {
    Parser<List<String>> parser =
        digits().zeroOrMoreDelimitedBy(",").followedBy(string(",").optional()).notEmpty();
    assertThat(parser.parse(",")).isEmpty();
    assertThat(parser.parse("1")).containsExactly("1");
    assertThat(parser.parse("1,")).containsExactly("1");
    assertThat(parser.parse("1,2")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1,2,")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1,2,3")).containsExactly("1", "2", "3").inOrder();
    assertThat(parser.parse("1,2,3,")).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter_source() {
    Parser<List<String>> parser =
        digits().zeroOrMoreDelimitedBy(",").followedBy(string(",").optional()).notEmpty();
    assertThat(parser.source().parse(",")).isEqualTo(",");
    assertThat(parser.source().parse("1")).isEqualTo("1");
    assertThat(parser.source().parse("1,")).isEqualTo("1,");
    assertThat(parser.source().parse("1,2")).isEqualTo("1,2");
    assertThat(parser.source().parse("1,2,")).isEqualTo("1,2,");
    assertThat(parser.source().parse("1,2,3")).isEqualTo("1,2,3");
    assertThat(parser.source().parse("1,2,3,")).isEqualTo("1,2,3,");
  }

  @Test
  public void zeroOrMoreDelimitedBy_withOptionalTrailingDelimiter_failOnEmpty() {
    Parser<List<String>> parser =
        digits().zeroOrMoreDelimitedBy(",").followedBy(string(",").optional()).notEmpty();
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <digits>, encountered <EOF>");
  }

  @Test
  public void zeroOrMoreDelimited_empty() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedByWithEscapes('"', '"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.parse("{}")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimited_single() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedByWithEscapes('"', '"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.parse("{k:\"v\"}")).containsExactly("k", "v");
  }

  @Test
  public void zeroOrMoreDelimited_multiple() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedByWithEscapes('"', '"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.parse("{k1:\"v1\",k2:\"v2\"}"))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
  }

  @Test
  public void zeroOrMoreDelimited_skippingWhitespace() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedByWithEscapes('"', '"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .between("{", "}");
    assertThat(parser.skipping(whitespace()).parse(" { k1 : \"v1\" , k2 : \"v2\" } "))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
    assertThat(parser.skipping(whitespace()).matches(" { k1 : \"v1\" , k2 : \"v2\" } ")).isTrue();
  }

  @Test
  public void zeroOrMoreDelimited_withTrailingComma() {
    Parser<ImmutableListMultimap<String, String>> parser =
        zeroOrMoreDelimited(
                word().followedBy(string(":")),
                Parser.quotedByWithEscapes('"', '"', chars(1)),
                ",",
                ImmutableListMultimap::toImmutableListMultimap)
            .followedBy(string(",").optional())
            .between("{", "}");
    assertThat(parser.skipping(whitespace()).parse(" { k1 : \"v1\" , k2 : \"v2\", } "))
        .containsExactly("k1", "v1", "k2", "v2")
        .inOrder();
    assertThat(parser.skipping(whitespace()).matches(" { k1 : \"v1\" , k2 : \"v2\", } ")).isTrue();
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_empty() {
    Parser<ImmutableMap<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                ImmutableMap::toImmutableMap)
            .between("{", "}");
    assertThat(parser.parse("{}")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_singleWithNoValue() {
    Parser<ImmutableMap<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                ImmutableMap::toImmutableMap)
            .between("{", "}");
    assertThat(parser.parse("{a}")).containsExactly("a", 0);
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_singleWithValue() {
    Parser<ImmutableMap<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                ImmutableMap::toImmutableMap)
            .between("{", "}");
    assertThat(parser.parse("{a=1}")).containsExactly("a", 1);
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_multiple() {
    Parser<ImmutableMap<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                ImmutableMap::toImmutableMap)
            .between("{", "}");
    assertThat(parser.parse("{a=1,b,c=3}")).containsExactly("a", 1, "b", 0, "c", 3);
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_skippingWhitespace() {
    Parser<ImmutableMap<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                ImmutableMap::toImmutableMap)
            .between("{", "}");
    assertThat(parser.skipping(whitespace()).parse(" { a=1 , b , c=3 } "))
        .containsExactly("a", 1, "b", 0, "c", 3);
    assertThat(parser.skipping(whitespace()).matches(" { a=1 , b , c=3 } ")).isTrue();
  }

  @Test
  public void zeroOrMoreDelimited_withOptionalValue_withTrailingComma() {
    Parser<ImmutableMap<String, Integer>> parser =
        zeroOrMoreDelimited(
                word(),
                string("=").then(digits()).map(Integer::parseInt).orElse(0),
                ",",
                ImmutableMap::toImmutableMap)
            .followedBy(string(",").optional())
            .between("{", "}");
    assertThat(parser.skipping(whitespace()).parse(" { a=1 , b , c=3, } "))
        .containsExactly("a", 1, "b", 0, "c", 3)
        .inOrder();
    assertThat(parser.skipping(whitespace()).matches(" { a=1 , b , c=3, } ")).isTrue();
  }

  @Test
  public void optional_between_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_between_zeroMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void optional_between_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().between("[", "]");
    assertThat(parser.parse("[a]")).hasValue("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_between_oneMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void optional_followedBy_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(";");
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_followedBy_zeroMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void optional_followedBy_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(";");
    assertThat(parser.parse("a;")).hasValue("a");
    assertThat(parser.parseToStream("a;")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_followedBy_oneMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void optional_betweenParsers_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_betweenParsers_zeroMatch_source() {
    Parser<Optional<String>> parser =
        string("a").source().optional().between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void optional_betweenParsers_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().between(string("["), string("]"));
    assertThat(parser.parse("[a]")).hasValue("a");
    assertThat(parser.parseToStream("[a]")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_betweenParsers_oneMatch_source() {
    Parser<Optional<String>> parser =
        string("a").source().optional().between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void optional_followedByParser_zeroMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(string(";"));
    assertThat(parser.parse(";")).isEmpty();
    assertThat(parser.parseToStream(";")).containsExactly(Optional.empty());
  }

  @Test
  public void optional_followedByParser_zeroMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void optional_followedByParser_oneMatch() {
    Parser<Optional<String>> parser = string("a").optional().followedBy(string(";"));
    assertThat(parser.parse("a;")).hasValue("a");
    assertThat(parser.parseToStream("a;")).containsExactly(Optional.of("a"));
  }

  @Test
  public void optional_followedByParser_oneMatch_source() {
    Parser<Optional<String>> parser = string("a").source().optional().followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void optional_parseEmpty() {
    assertThat(string("a").optional().parse("")).isEmpty();
  }

  @Test
  public void optional_parseEmpty_source() {
    assertThat(string("a").source().optional().parse("")).isEmpty();
  }

  @Test
  public void optional_parseNonEmpty() {
    assertThat(string("a").optional().parse("a")).hasValue("a");
  }

  @Test
  public void optional_parseNonEmpty_source() {
    assertThat(string("a").source().optional().parse("a")).hasValue("a");
  }

  @Test
  public void optional_parseFail() {
    assertThrows(ParseException.class, () -> string("a").optional().parse("b"));
    assertThat(string("a").optional().matches("b")).isFalse();
  }

  @Test
  public void optional_withLeftOverInput() {
    ParseException thrown =
        assertThrows(ParseException.class, () -> string("a").optional().parse("a bc"));
    assertThat(string("a").optional().matches("a bc")).isFalse();
    assertThat(thrown).hasMessageThat().contains("1:2: expecting <EOF>, encountered [ bc]");
  }

  @Test
  public void orElse_between_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").between("[", "]");
    assertThat(parser.parse("[]")).isEqualTo("default");
    assertThat(parser.parseToStream("[]")).containsExactly("default");
  }

  @Test
  public void orElse_between_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void orElse_between_oneMatch() {
    Parser<String> parser = string("a").orElse("default").between("[", "]");
    assertThat(parser.parse("[a]")).isEqualTo("a");
    assertThat(parser.parseToStream("[a]")).containsExactly("a");
  }

  @Test
  public void orElse_between_oneMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").between("[", "]");
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void orElse_followedBy_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(";");
    assertThat(parser.parse(";")).isEqualTo("default");
    assertThat(parser.parseToStream(";")).containsExactly("default");
  }

  @Test
  public void orElse_followedBy_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(";");
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void orElse_followedBy_oneMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(";");
    assertThat(parser.parse("a;")).isEqualTo("a");
    assertThat(parser.parseToStream("a;")).containsExactly("a");
  }

  @Test
  public void orElse_followedBy_oneMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(";");
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void orElse_betweenParsers_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").between(string("["), string("]"));
    assertThat(parser.parse("[]")).isEqualTo("default");
    assertThat(parser.parseToStream("[]")).containsExactly("default");
  }

  @Test
  public void orElse_betweenParsers_zeroMatch_source() {
    Parser<String> parser =
        string("a").source().orElse("default").between(string("["), string("]"));
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void orElse_betweenParsers_oneMatch() {
    Parser<String> parser = string("a").orElse("default").between(string("["), string("]"));
    assertThat(parser.parse("[a]")).isEqualTo("a");
    assertThat(parser.parseToStream("[a]")).containsExactly("a");
  }

  @Test
  public void orElse_betweenParsers_oneMatch_source() {
    Parser<String> parser =
        string("a").source().orElse("default").between(string("["), string("]"));
    assertThat(parser.source().parse("[a]")).isEqualTo("[a]");
    assertThat(parser.source().parseToStream("[a]")).containsExactly("[a]");
  }

  @Test
  public void orElse_followedByParser_zeroMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(string(";"));
    assertThat(parser.parse(";")).isEqualTo("default");
    assertThat(parser.parseToStream(";")).containsExactly("default");
  }

  @Test
  public void orElse_followedByParser_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(string(";"));
    assertThat(parser.source().parse(";")).isEqualTo(";");
    assertThat(parser.source().parseToStream(";")).containsExactly(";");
  }

  @Test
  public void orElse_followedByParser_oneMatch() {
    Parser<String> parser = string("a").orElse("default").followedBy(string(";"));
    assertThat(parser.parse("a;")).isEqualTo("a");
    assertThat(parser.parseToStream("a;")).containsExactly("a");
  }

  @Test
  public void orElse_followedByParser_oneMatch_source() {
    Parser<String> parser = string("a").source().orElse("default").followedBy(string(";"));
    assertThat(parser.source().parse("a;")).isEqualTo("a;");
    assertThat(parser.source().parseToStream("a;")).containsExactly("a;");
  }

  @Test
  public void orElse_parseEmpty() {
    assertThat(string("a").orElse("default").parse("")).isEqualTo("default");
  }

  @Test
  public void orElse_parseEmpty_source() {
    assertThat(string("a").source().orElse("default").parse("")).isEqualTo("default");
  }

  @Test
  public void orElse_parseNonEmpty() {
    assertThat(string("a").orElse("default").parse("a")).isEqualTo("a");
  }

  @Test
  public void orElse_parseNonEmpty_source() {
    assertThat(string("a").source().orElse("default").parse("a")).isEqualTo("a");
  }

  @Test
  public void orElse_parseFail() {
    assertThrows(ParseException.class, () -> string("a").orElse("default").parse("b"));
    assertThat(string("a").orElse("default").matches("b")).isFalse();
  }

  @Test
  public void orElse_nullDefault_between_zeroMatch() {
    Parser<String> parser = string("a").orElse(null).between("[", "]");
    assertThat(parser.parse("[]")).isNull();
    assertThat(parser.parseToStream("[]")).containsExactly((String) null);
  }

  @Test
  public void orElse_nullDefault_between_zeroMatch_source() {
    Parser<String> parser = string("a").source().orElse(null).between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
  }

  @Test
  public void orElse_nullDefault_parseEmpty() {
    assertThat(string("a").orElse(null).parse("")).isNull();
  }

  @Test
  public void orElse_nullDefault_parseEmpty_source() {
    assertThat(string("a").source().orElse(null).parse("")).isNull();
  }

  @Test
  public void atLeastOnceDelimitedBy_success() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThat(parser.parse("a")).containsExactly("a");
    assertThat(parser.parseToStream("a")).containsExactly(List.of("a"));
    assertThat(parser.parse("a,a")).containsExactly("a", "a").inOrder();
    assertThat(parser.parseToStream("a,a")).containsExactly(List.of("a", "a"));
    assertThat(parser.parse("a,a,a")).containsExactly("a", "a", "a").inOrder();
    assertThat(parser.parseToStream("a,a,a"))
        .containsExactly(List.of("a", "a", "a"))
        .inOrder();
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnceDelimitedBy_success_source() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThat(parser.source().parse("a")).isEqualTo("a");
    assertThat(parser.source().parseToStream("a")).containsExactly("a");
    assertThat(parser.source().parse("a,a")).isEqualTo("a,a");
    assertThat(parser.source().parseToStream("a,a")).containsExactly("a,a");
    assertThat(parser.source().parse("a,a,a")).isEqualTo("a,a,a");
    assertThat(parser.source().parseToStream("a,a,a")).containsExactly("a,a,a").inOrder();
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void atLeastOnceDelimitedBy_failure_withLeftover() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThrows(ParseException.class, () -> parser.parse("aa"));
    assertThrows(ParseException.class, () -> parser.parse("a,ab"));
  }

  @Test
  public void atLeastOnceDelimitedBy_failure() {
    Parser<List<String>> parser = string("a").atLeastOnceDelimitedBy(",");
    assertThrows(ParseException.class, () -> parser.parse(""));
    assertThrows(ParseException.class, () -> parser.parse("b"));
    assertThrows(ParseException.class, () -> parser.parseToStream("b").toList());
    assertThrows(ParseException.class, () -> parser.parse("a,"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a,").toList());
    assertThrows(ParseException.class, () -> parser.parse(",a"));
    assertThrows(ParseException.class, () -> parser.parseToStream(",a").toList());
    assertThrows(ParseException.class, () -> parser.parse("a,b"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a,b").toList());
    assertThrows(ParseException.class, () -> parser.parse("a,,a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a,,a").toList());
  }

  @Test
  public void atLeastOnceDelimitedBy_cannotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> string("a").atLeastOnceDelimitedBy(""));
  }

  @Test
  public void atLeastOnceDelimitedBy_withReducer_success() {
    Parser<Integer> integer = one(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnceDelimitedBy(",", (a, b) -> a - b);
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parse("1,2")).isEqualTo(-1);
    assertThat(parser.parse("1,2,3")).isEqualTo(-4);
  }

  @Test
  public void atLeastOnceDelimitedBy_withReducer_failure_withLeftover() {
    Parser<Integer> integer = one(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnceDelimitedBy(",", (a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parse("1,2a"));
  }

  @Test
  public void atLeastOnceDelimitedBy_withReducer_failure() {
    Parser<Integer> integer = one(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> parser = integer.atLeastOnceDelimitedBy(",", (a, b) -> a - b);
    assertThrows(ParseException.class, () -> parser.parse(""));
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parse("1,"));
    assertThrows(ParseException.class, () -> parser.parse(",1"));
    assertThrows(ParseException.class, () -> parser.parse("1,,2"));
  }

  @Test
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    assertThat(parser.parse("12")).containsExactly("12");
    assertThat(parser.parse("12,")).containsExactly("12");
    assertThat(parser.parse("1,23")).containsExactly("1", "23").inOrder();
    assertThat(parser.parse("1,23,")).containsExactly("1", "23").inOrder();
    assertThat(parser.parse("1,2,3")).containsExactly("1", "2", "3").inOrder();
    assertThat(parser.parse("1,2,3,")).containsExactly("1", "2", "3").inOrder();
    assertThat(parser.parse("1,2,3,4")).containsExactly("1", "2", "3", "4").inOrder();
    assertThat(parser.parse("1,2,3,4,")).containsExactly("1", "2", "3", "4").inOrder();
  }

  @Test
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter_source() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    assertThat(parser.source().parse("12")).isEqualTo("12");
    assertThat(parser.source().parse("12,")).isEqualTo("12,");
    assertThat(parser.source().parse("1,23")).isEqualTo("1,23");
    assertThat(parser.source().parse("1,23,")).isEqualTo("1,23,");
    assertThat(parser.source().parse("1,2,3")).isEqualTo("1,2,3");
    assertThat(parser.source().parse("1,2,3,")).isEqualTo("1,2,3,");
    assertThat(parser.source().parse("1,2,3,4")).isEqualTo("1,2,3,4");
    assertThat(parser.source().parse("1,2,3,4,")).isEqualTo("1,2,3,4,");
  }

  @Test
  public void atLeastOnceDelimitedBy_withOptionalTrailingDelimiter_onlyTrailingDelimiter() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(","));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <digits>, encountered [,]");
  }

  @Test
  public void atLeastOnceDelimitedBy_withTrailingDelimiter_emptyInput() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(",").optionallyFollowedBy(",");
    ParseException e = assertThrows(ParseException.class, () -> parser.parse(""));
    assertThat(e).hasMessageThat().contains("at 1:1: expecting <digits>, encountered <EOF>");
  }

  @Test
  public void zeroOrMoreDelimitedBy_parserDelimiter_success() {
    var parser = digits().zeroOrMoreDelimitedBy(consecutive(is('.'), "dot"), toImmutableList());
    assertThat(parser.parse("1")).containsExactly("1");
    assertThat(parser.parse("1.2")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1..2")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1...2..3")).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parserDelimiter_empty() {
    var parser = digits().zeroOrMoreDelimitedBy(consecutive(is('.'), "dot"), toImmutableList());
    assertThat(parser.parse("")).isEmpty();
  }

  @Test
  public void zeroOrMoreDelimitedBy_parserDelimiter_failure() {
    var parser = digits().zeroOrMoreDelimitedBy(consecutive(is('.'), "dot"), toImmutableList());
    assertThrows(ParseException.class, () -> parser.parse("1_2"));
    assertThrows(ParseException.class, () -> parser.parse("."));
    assertThrows(ParseException.class, () -> parser.parse("1."));
  }

  @Test
  public void atLeastOnceDelimitedBy_parserDelimiter_success() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(consecutive(is('.'), "dot"), toImmutableList());
    assertThat(parser.parse("1")).containsExactly("1");
    assertThat(parser.parse("1.2")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1..2")).containsExactly("1", "2").inOrder();
    assertThat(parser.parse("1...2..3")).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void atLeastOnceDelimitedBy_parserDelimiter_failure() {
    Parser<List<String>> parser =
        digits().atLeastOnceDelimitedBy(consecutive(is('.'), "dot"), toImmutableList());
    assertThrows(ParseException.class, () -> parser.parse("1_2"));
    assertThrows(ParseException.class, () -> parser.parse("1."));
  }

  @Test
  public void between_success() {
    Parser<String> parser = string("content").between("[", "]");
    assertThat(parser.parse("[content]")).isEqualTo("content");
    assertThat(parser.parseToStream("[content]")).containsExactly("content");
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void between_success_source() {
    Parser<String> parser = string("content").between("[", "]");
    assertThat(parser.source().parse("[content]")).isEqualTo("[content]");
    assertThat(parser.source().parseToStream("[content]")).containsExactly("[content]");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void between_failure_withLeftover() {
    Parser<String> parser = string("content").between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[content]a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("[content]a").toList());
  }

  @Test
  public void between_failure() {
    Parser<String> parser = string("content").between("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("content]"));
    assertThrows(ParseException.class, () -> parser.parseToStream("content]").toList());
    assertThrows(ParseException.class, () -> parser.parse("[content"));
    assertThrows(ParseException.class, () -> parser.parseToStream("[content").toList());
    assertThrows(ParseException.class, () -> parser.parse("content"));
    assertThrows(ParseException.class, () -> parser.parseToStream("content").toList());
    assertThrows(ParseException.class, () -> parser.parse("[wrong]"));
    assertThrows(ParseException.class, () -> parser.parseToStream("[wrong]").toList());
    assertThrows(ParseException.class, () -> parser.parse(" [content]"));
    assertThrows(ParseException.class, () -> parser.parseToStream(" [content]").toList());
  }

  @Test
  public void between_cannotBeEmpty() {
    Parser<String> parser = string("content");
    assertThrows(IllegalArgumentException.class, () -> parser.between("", "]"));
    assertThrows(IllegalArgumentException.class, () -> parser.between("[", ""));
  }

  @Test
  public void between_orEmpty_success() {
    Parser<String> parser =
        string("content")
            .between(zeroOrMore(whitespace(), "ignore"), zeroOrMore(whitespace(), "ignore"));
    assertThat(parser.parse("content")).isEqualTo("content");
    assertThat(parser.parse(" content")).isEqualTo("content");
    assertThat(parser.parse("content ")).isEqualTo("content");
    assertThat(parser.parse(" content ")).isEqualTo("content");
    assertThat(parser.parse("  content  ")).isEqualTo("content");
  }

  @Test
  public void between_orEmpty_success_source() {
    Parser<String> parser =
        string("content")
            .between(zeroOrMore(whitespace(), "ignore"), zeroOrMore(whitespace(), "ignore"));
    assertThat(parser.source().parse("content")).isEqualTo("content");
    assertThat(parser.source().parse(" content")).isEqualTo(" content");
    assertThat(parser.source().parse("content ")).isEqualTo("content ");
    assertThat(parser.source().parse(" content ")).isEqualTo(" content ");
    assertThat(parser.source().parse("  content  ")).isEqualTo("  content  ");
  }

  @Test
  public void between_orEmpty_failure() {
    Parser<String> parser =
        string("content")
            .between(zeroOrMore(whitespace(), "ignore"), zeroOrMore(whitespace(), "ignore"));
    assertThrows(ParseException.class, () -> parser.parse("Content"));
    assertThrows(ParseException.class, () -> parser.parse(" contentX"));
  }

  @Test
  public void orEmpty_between_orEmpty_success() {
    Parser<String>.OrEmpty parser =
        zeroOrMore(is('a'), "a's")
            .between(zeroOrMore(whitespace(), "ignore"), zeroOrMore(whitespace(), "ignore"));
    assertThat(parser.parse("aa")).isEqualTo("aa");
    assertThat(parser.parse(" aa")).isEqualTo("aa");
    assertThat(parser.parse("aa ")).isEqualTo("aa");
    assertThat(parser.parse(" aa ")).isEqualTo("aa");
    assertThat(parser.parse("  ")).isEmpty();
    assertThat(parser.parse("")).isEmpty();
  }

  @Test
  public void orEmpty_between_orEmpty_failure() {
    Parser<String>.OrEmpty parser =
        zeroOrMore(is('a'), "a's")
            .between(zeroOrMore(whitespace(), "ignore"), zeroOrMore(whitespace(), "ignore"));
    assertThrows(ParseException.class, () -> parser.parse("a a"));
    assertThat(parser.matches("a a")).isFalse();
  }

  @Test
  public void orEmpty_immediatelyBetween_success() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[foo]")).isEqualTo("foo");
  }

  @Test
  public void orEmpty_immediatelyBetween_success_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parse("[foo]")).isEqualTo("[foo]");
  }

  @Test
  public void orEmpty_immediatelyBetween_emptyContent() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
  }

  @Test
  public void orEmpty_immediatelyBetween_emptyContent_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
  }

  @Test
  public void orEmpty_immediatelyBetween_prefixMismatch_throws() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("foo]"));
  }

  @Test
  public void orEmpty_immediatelyBetween_suffixMismatch_throws() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[foo"));
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_aroundQuotes() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(whitespace(), " [foo] ")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).matches(" [foo] ")).isTrue();
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_aroundQuotes_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), " [foo] ")).isEqualTo("[foo]");
    assertThat(parser.source().skipping(whitespace()).matches(" [foo] ")).isTrue();
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spacesInsideQuotes() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(whitespace(), " [ foo ] ")).isEqualTo(" foo ");
    assertThat(parser.skipping(whitespace()).matches(" [ foo ] ")).isTrue();
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spacesInsideQuotes_source() {
    Parser<String> parser = zeroOrMore(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), " [ foo ] ")).isEqualTo("[ foo ]");
    assertThat(parser.source().skipping(whitespace()).matches(" [ foo ] ")).isTrue();
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spaceFollowingPrefixNotIgnored() {
    Parser<String> parser = zeroOrMore(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " [ foo] "));
    assertThat(thrown).hasMessageThat().contains("1:3");
    assertThat(thrown).hasMessageThat().contains("encountered [ foo]...");
    assertThat(parser.skipping(whitespace()).matches(" [ foo] ")).isFalse();
  }

  @Test
  public void orEmpty_immediatelyBetween_withSkipping_spacePrecedingSuffixNotIgnored() {
    Parser<String> parser = zeroOrMore(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " [foo ] "));
    assertThat(thrown).hasMessageThat().contains("1:6");
    assertThat(thrown).hasMessageThat().contains("encountered [ ]...");
    assertThat(parser.skipping(whitespace()).matches(" [foo ] ")).isFalse();
  }

  @Test
  public void orEmpty_optionallyFollowedBy_suffix() {
    Parser<String> parser = zeroOrMore(is('a'), "a's").optionallyFollowedBy(",").between("[", "]");
    assertThat(parser.parse("[aa,]")).isEqualTo("aa");
    assertThat(parser.parse("[aa]")).isEqualTo("aa");
    assertThat(parser.parse("[,]")).isEmpty();
    assertThat(parser.parse("[]")).isEmpty();
  }

  @Test
  public void orEmpty_optionallyFollowedBy_suffix_source() {
    Parser<String> parser = zeroOrMore(is('a'), "a's").optionallyFollowedBy(",").between("[", "]");
    assertThat(parser.source().parse("[aa,]")).isEqualTo("[aa,]");
    assertThat(parser.source().parse("[aa]")).isEqualTo("[aa]");
    assertThat(parser.source().parse("[,]")).isEqualTo("[,]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
  }

  @Test
  public void parser_immediatelyBetween_success() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parse("[foo]")).isEqualTo("foo");
  }

  @Test
  public void parser_immediatelyBetween_success_source() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parse("[foo]")).isEqualTo("[foo]");
  }

  @Test
  public void parser_immediatelyBetween_mainParserFails_throws() {
    assertThrows(ParseException.class, () -> word().immediatelyBetween("[", "]").parse("[!123]"));
  }

  @Test
  public void parser_immediatelyBetween_prefixMismatch_throws() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("foo]"));
  }

  @Test
  public void parser_immediatelyBetween_suffixMismatch_throws() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThrows(ParseException.class, () -> parser.parse("[foo"));
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_aroundQuotes() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(whitespace(), " [foo] ")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).matches(" [foo] ")).isTrue();
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_aroundQuotes_source() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), " [foo] ")).isEqualTo("[foo]");
    assertThat(parser.source().skipping(whitespace()).matches(" [foo] ")).isTrue();
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spacesInsideQuotes() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.parseSkipping(whitespace(), " [ foo ] ")).isEqualTo(" foo ");
    assertThat(parser.skipping(whitespace()).matches(" [ foo ] ")).isTrue();
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spacesInsideQuotes_source() {
    Parser<String> parser = consecutive(noneOf("[]"), "content").immediatelyBetween("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), " [ foo ] ")).isEqualTo("[ foo ]");
    assertThat(parser.source().skipping(whitespace()).matches(" [ foo ] ")).isTrue();
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spaceFollowingPrefixNotIgnored() {
    Parser<String> parser = consecutive(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " [ foo] "));
    assertThat(thrown).hasMessageThat().contains("1:3");
    assertThat(thrown).hasMessageThat().contains("encountered [ foo]...");
    assertThat(parser.skipping(whitespace()).matches(" [ foo] ")).isFalse();
  }

  @Test
  public void parser_immediatelyBetween_withSkipping_spacePrecedingSuffixNotIgnored() {
    Parser<String> parser = consecutive(noneOf("[ ]"), "content").immediatelyBetween("[", "]");
    ParseException thrown =
        assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " [foo ] "));
    assertThat(thrown).hasMessageThat().contains("1:6");
    assertThat(thrown).hasMessageThat().contains("encountered [ ]...");
    assertThat(parser.skipping(whitespace()).matches(" [foo ] ")).isFalse();
  }

  @Test
  public void one_char_success() {
    Parser<Character> parser = one('x');
    assertThat(parser.parse("x")).isEqualTo('x');
    assertThat(parser.parseToStream("x")).containsExactly('x');
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void one_char_success_source() {
    Parser<Character> parser = one('x');
    assertThat(parser.source().parse("x")).isEqualTo("x");
    assertThat(parser.source().parseToStream("x")).containsExactly("x");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void one_char_failure_withLeftover() {
    Parser<Character> parser = one('x');
    assertThrows(ParseException.class, () -> parser.parse("xy"));
    assertThrows(ParseException.class, () -> parser.parseToStream("xy").toList());
  }

  @Test
  public void one_char_failure() {
    Parser<Character> parser = one('x');
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("xx"));
  }

  @Test
  public void one_success() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.parse("1")).isEqualTo('1');
    assertThat(parser.parseToStream("1")).containsExactly('1');
    assertThat(parser.parse("9")).isEqualTo('9');
    assertThat(parser.parseToStream("9")).containsExactly('9');
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void one_success_source() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.source().parse("1")).isEqualTo("1");
    assertThat(parser.source().parseToStream("1")).containsExactly("1");
    assertThat(parser.source().parse("9")).isEqualTo("9");
    assertThat(parser.source().parseToStream("9")).containsExactly("9");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void one_failure_withLeftover() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parse("1a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("1a").toList());
  }

  @Test
  public void one_failure() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("12"));
  }

  @Test
  public void one_characterSet_success() {
    Parser<Character> parser = one(charsIn("[0-9]"));
    assertThat(parser.parse("1")).isEqualTo('1');
    assertThat(parser.parseToStream("1")).containsExactly('1');
    assertThat(parser.parse("9")).isEqualTo('9');
    assertThat(parser.parseToStream("9")).containsExactly('9');
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void one_characterSet_failure() {
    Parser<Character> parser = one(charsIn("[0-9]"));
    assertThrows(ParseException.class, () -> parser.parse("a"));
  }

  @Test
  public void consecutive_success() {
    assertThat(digits().parse("1")).isEqualTo("1");
    assertThat(digits().parseToStream("1")).containsExactly("1");
    assertThat(digits().parse("123")).isEqualTo("123");
    assertThat(digits().parseToStream("123")).containsExactly("123");
    assertThat(digits().parseToStream("")).isEmpty();
  }

  @Test
  public void consecutive_success_source() {
    assertThat(digits().source().parse("1")).isEqualTo("1");
    assertThat(digits().source().parseToStream("1")).containsExactly("1");
    assertThat(digits().source().parse("123")).isEqualTo("123");
    assertThat(digits().source().parseToStream("123")).containsExactly("123");
    assertThat(digits().source().parseToStream("")).isEmpty();
  }

  @Test
  public void consecutive_failure_withLeftover() {
    assertThrows(ParseException.class, () -> digits().parse("1a"));
    assertThrows(ParseException.class, () -> digits().parseToStream("1a").toList());
    assertThrows(ParseException.class, () -> digits().parse("123a"));
    assertThrows(ParseException.class, () -> digits().parseToStream("123a").toList());
  }

  @Test
  public void consecutive_failure() {
    assertThrows(ParseException.class, () -> digits().parse("a"));
    assertThrows(ParseException.class, () -> digits().parseToStream("a").toList());
    assertThrows(ParseException.class, () -> digits().parse("12a"));
    assertThrows(ParseException.class, () -> digits().parseToStream("12a").toList());
    assertThrows(ParseException.class, () -> digits().parse(""));
  }

  @Test
  public void consecutive_charClass_success() {
    assertThat(consecutive(charsIn("[0-9]")).parse("1")).isEqualTo("1");
    assertThat(consecutive(charsIn("[0-9]")).parseToStream("1")).containsExactly("1");
    assertThat(consecutive(charsIn("[0-9]")).parse("123")).isEqualTo("123");
    assertThat(consecutive(charsIn("[0-9]")).parseToStream("123")).containsExactly("123");
    assertThat(consecutive(charsIn("[0-9]")).parseToStream("")).isEmpty();
  }

  @Test
  public void consecutive_charClass_success_source() {
    assertThat(consecutive(charsIn("[0-9]")).source().parse("1")).isEqualTo("1");
    assertThat(consecutive(charsIn("[0-9]")).source().parseToStream("1")).containsExactly("1");
    assertThat(consecutive(charsIn("[0-9]")).source().parse("123")).isEqualTo("123");
    assertThat(consecutive(charsIn("[0-9]")).source().parseToStream("123")).containsExactly("123");
    assertThat(consecutive(charsIn("[0-9]")).source().parseToStream("")).isEmpty();
  }

  @Test
  public void consecutive_charClass_failure_withLeftover() {
    assertThrows(ParseException.class, () -> consecutive(charsIn("[0-9]")).parse("1a"));
    assertThrows(
        ParseException.class, () -> consecutive(charsIn("[0-9]")).parseToStream("1a").toList());
    assertThrows(ParseException.class, () -> consecutive(charsIn("[0-9]")).parse("123a"));
    assertThrows(
        ParseException.class, () -> consecutive(charsIn("[0-9]")).parseToStream("123a").toList());
  }

  @Test
  public void consecutive_charClass_failure() {
    assertThrows(ParseException.class, () -> consecutive(charsIn("[0-9]")).parse("a"));
    assertThrows(
        ParseException.class, () -> consecutive(charsIn("[0-9]")).parseToStream("a").toList());
    assertThrows(ParseException.class, () -> consecutive(charsIn("[0-9]")).parse("12a"));
    assertThrows(
        ParseException.class, () -> consecutive(charsIn("[0-9]")).parseToStream("12a").toList());
    assertThrows(ParseException.class, () -> consecutive(charsIn("[0-9]")).parse(""));
  }

  @Test
  public void chars_unicodeEscapeExample() {
    CharPredicate hexDigit = CharPredicate.range('0', '9').orRange('A', 'F');
    Parser<Integer> uncodeEscape =
        string("\\u")
            .then(chars(4).suchThat(hexDigit::matchesAllOf, "4 hex"))
            .map(hex -> Integer.parseInt(hex, 16));
    assertThat(uncodeEscape.parseToStream("\\uD83D\\uDE00"))
        .containsExactly(0xD83D, 0xDE00)
        .inOrder();
  }

  @Test
  public void chars_zeroTimes_fails() {
    assertThrows(IllegalArgumentException.class, () -> chars(0));
  }

  @Test
  public void chars_notSufficientChars_fails() {
    Parser<String> parser = chars(2);
    ParseException thrown = assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThat(thrown).hasMessageThat().contains("1:1: expecting <2 char(s)>, encountered [a]");
  }

  @Test
  public void chars_sufficientChars_succeeds() {
    assertThat(chars(2).parse("ab")).isEqualTo("ab");
  }

  @Test
  public void chars_moreThanSufficientChars_succeeds() {
    assertThat(chars(2).parseToStream("abcd")).containsExactly("ab", "cd").inOrder();
  }

  @Test
  public void chars_source_moreThanSufficientChars_succeeds() {
    assertThat(chars(2).source().parseToStream("abcd")).containsExactly("ab", "cd").inOrder();
  }

  @Test
  public void chars_skipping() {
    assertThat(chars(2).skipping(whitespace()).parseToStream(" ab cd"))
        .containsExactly("ab", "cd")
        .inOrder();
    assertThat(chars(2).parseToStream(" ab cd")).containsExactly(" a", "b ", "cd").inOrder();
    assertThat(literally(chars(2)).skipping(whitespace()).parseToStream(" ab cd"))
        .containsExactly(" a", "b ", "cd")
        .inOrder();
  }

  @Test
  public void withPrefixes_zeroOperator_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.withPrefixes(neg);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10")).containsExactly(10);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void withPrefixes_zeroOperator_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.withPrefixes(neg);
    assertThat(parser.source().parse("10")).isEqualTo("10");
    assertThat(parser.source().parseToStream("10")).containsExactly("10");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void withPrefixes_oneOperator_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.withPrefixes(neg);
    assertThat(parser.parse("-10")).isEqualTo(-10);
    assertThat(parser.parseToStream("-10")).containsExactly(-10);
  }

  @Test
  public void withPrefixes_oneOperator_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.withPrefixes(neg);
    assertThat(parser.source().parse("-10")).isEqualTo("-10");
    assertThat(parser.source().parseToStream("-10")).containsExactly("-10");
  }

  @Test
  public void withPrefixes_multipleOperators_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<UnaryOperator<Integer>> plus = string("+").thenReturn(i -> i);
    Parser<UnaryOperator<Integer>> flip = string("~").thenReturn(i -> ~i);
    Parser<UnaryOperator<Integer>> op = anyOf(neg, plus, flip);
    Parser<Integer> parser = number.withPrefixes(op);
    assertThat(parser.parse("--10")).isEqualTo(10);
    assertThat(parser.parse("-~10")).isEqualTo(-(~10));
    assertThat(parser.parse("~-10")).isEqualTo(~(-10));
    assertThat(parser.parseToStream("--10")).containsExactly(10);
    assertThat(parser.parse("-+10")).isEqualTo(-10);
    assertThat(parser.parseToStream("-+10")).containsExactly(-10);
    assertThat(parser.parse("+-10")).isEqualTo(-10);
    assertThat(parser.parseToStream("+-10")).containsExactly(-10);
  }

  @Test
  public void withPrefixes_multipleOperators_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<UnaryOperator<Integer>> plus = string("+").thenReturn(i -> i);
    Parser<UnaryOperator<Integer>> flip = string("~").thenReturn(i -> ~i);
    Parser<UnaryOperator<Integer>> op = anyOf(neg, plus, flip);
    Parser<Integer> parser = number.withPrefixes(op);
    assertThat(parser.source().parse("--10")).isEqualTo("--10");
    assertThat(parser.source().parse("-~10")).isEqualTo("-~10");
    assertThat(parser.source().parse("~-10")).isEqualTo("~-10");
    assertThat(parser.source().parseToStream("--10")).containsExactly("--10");
    assertThat(parser.source().parse("-+10")).isEqualTo("-+10");
    assertThat(parser.source().parseToStream("-+10")).containsExactly("-+10");
    assertThat(parser.source().parse("+-10")).isEqualTo("+-10");
    assertThat(parser.source().parseToStream("+-10")).containsExactly("+-10");
  }

  @Test
  public void withPrefixes_operandParseFails() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.withPrefixes(neg);
    assertThrows(ParseException.class, () -> parser.parse("a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-a").toList());
  }

  @Test
  public void withPrefixes_failure_withLeftover() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> neg = string("-").thenReturn(i -> -i);
    Parser<Integer> parser = number.withPrefixes(neg);
    assertThrows(ParseException.class, () -> parser.parse("10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10a").toList());
    assertThrows(ParseException.class, () -> parser.parse("-10a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("-10a").toList());
  }

  @Test
  public void withPostfixes_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.withPostfixes(op);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parseToStream("10")).containsExactly(10);
    assertThat(parser.parse("10++")).isEqualTo(11);
    assertThat(parser.parseToStream("10++")).containsExactly(11);
    assertThat(parser.parse("10--")).isEqualTo(9);
    assertThat(parser.parseToStream("10--")).containsExactly(9);
    assertThat(parser.parse("10++--++")).isEqualTo(11);
    assertThat(parser.parseToStream("10++--++")).containsExactly(11);
    assertThat(parser.parseToStream("")).isEmpty();
  }

  @Test
  public void withPostfixes_success_source() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.withPostfixes(op);
    assertThat(parser.source().parse("10")).isEqualTo("10");
    assertThat(parser.source().parseToStream("10")).containsExactly("10");
    assertThat(parser.source().parse("10++")).isEqualTo("10++");
    assertThat(parser.source().parseToStream("10++")).containsExactly("10++");
    assertThat(parser.source().parse("10--")).isEqualTo("10--");
    assertThat(parser.source().parseToStream("10--")).containsExactly("10--");
    assertThat(parser.source().parse("10++--++")).isEqualTo("10++--++");
    assertThat(parser.source().parseToStream("10++--++")).containsExactly("10++--++");
    assertThat(parser.source().parseToStream("")).isEmpty();
  }

  @Test
  public void withPostfixes_failure() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.withPostfixes(op);
    assertThrows(ParseException.class, () -> parser.parse("a++"));
    assertThrows(ParseException.class, () -> parser.parseToStream("a++").toList());
    assertThrows(ParseException.class, () -> parser.parse("10+"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10+").toList());
  }

  @Test
  public void withPostfixes_failure_withLeftover() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<UnaryOperator<Integer>> inc = string("++").thenReturn(i -> i + 1);
    Parser<UnaryOperator<Integer>> dec = string("--").thenReturn(i -> i - 1);
    Parser<UnaryOperator<Integer>> op = anyOf(inc, dec);
    Parser<Integer> parser = number.withPostfixes(op);
    assertThrows(ParseException.class, () -> parser.parse("10++a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10++a").toList());
    assertThrows(ParseException.class, () -> parser.parse("10 a"));
    assertThrows(ParseException.class, () -> parser.parseToStream("10 a").toList());
  }

  @Test
  public void withPostfixes_withBiFunction_success() {
    Parser<Integer> parser =
        digits().map(Integer::parseInt).withPostfixes(string("++").map(s -> 1), (a, b) -> a + b);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parse("10++")).isEqualTo(11);
    assertThat(parser.parse("10++++")).isEqualTo(12);
  }

  @Test
  public void withPostfixes_withBiFunction_failure() {
    Parser<Integer> parser =
        digits().map(Integer::parseInt).withPostfixes(string("!").map(s -> 1), (a, b) -> a + b);
    assertThrows(ParseException.class, () -> parser.parse("10!a"));
  }

  @Test
  public void withPostfixes_unaryOperator_success() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = number.withPostfixes("++", i -> i + 1);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parse("10++")).isEqualTo(11);
    assertThat(parser.parse("10++++")).isEqualTo(12);
  }

  @Test
  public void withPostfixes_unaryOperator_failure() {
    Parser<Integer> number = digits().map(Integer::parseInt);
    Parser<Integer> parser = number.withPostfixes("++", i -> i + 1);
    assertThrows(ParseException.class, () -> parser.parse("10++a"));
  }

  @Test
  public void parse_fromIndex() {
    assertThat(string("bar").parse("foobar", 3)).isEqualTo("bar");
    assertThat(string("bar").source().parse("foobar", 3)).isEqualTo("bar");
    assertThat(digits().skipping(whitespace()).parse("a 123", 1)).isEqualTo("123");
    assertThat(digits().source().skipping(whitespace()).parse("a 123", 1)).isEqualTo("123");
  }

  @Test
  public void parse_fromIndex_atEnd() {
    assertThrows(ParseException.class, () -> string("a").parse("a", 1));
    assertThrows(ParseException.class, () -> string("a").skipping(whitespace()).parse("a  ", 1));
  }

  @Test
  public void parse_fromIndex_outOfBounds() {
    assertThrows(IndexOutOfBoundsException.class, () -> string("a").parse("a", 2));
    assertThrows(
        IndexOutOfBoundsException.class, () -> string("a").skipping(whitespace()).parse("a", 2));
  }

  @Test
  public void skipping_aroundIdentifier() {
    Parser<String> parser = string("foo");
    assertThat(parser.parseSkipping(whitespace(), "foo")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).parseToStream("foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(whitespace(), " foo")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(" foo")).containsExactly("foo");
    assertThat(parser.parseSkipping(whitespace(), "foo \n ")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).parseToStream("foo \n  ")).containsExactly("foo");
    assertThat(parser.parseSkipping(whitespace(), " foo ")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(" foo ")).containsExactly("foo");
    assertThat(parser.parseSkipping(whitespace(), "   foo   ")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).parseToStream("   foo   ")).containsExactly("foo");
  }

  @Test
  public void skipping_aroundIdentifier_withReader() {
    Parser<String> parser = string("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(new StringReader("foo")))
        .containsExactly("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(new StringReader(" foo")))
        .containsExactly("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(new StringReader("foo \n  ")))
        .containsExactly("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(new StringReader(" foo ")))
        .containsExactly("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(new StringReader("   foo   ")))
        .containsExactly("foo");
  }

  @Test
  public void skipping_aroundIdentifier_source() {
    Parser<String> parser = string("foo");
    assertThat(parser.source().parseSkipping(whitespace(), "foo")).isEqualTo("foo");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foo")).containsExactly("foo");
    assertThat(parser.source().parseSkipping(whitespace(), " foo")).isEqualTo("foo");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" foo")).containsExactly("foo");
    assertThat(parser.source().parseSkipping(whitespace(), "foo \n ")).isEqualTo("foo");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foo \n  "))
        .containsExactly("foo");
    assertThat(parser.source().parseSkipping(whitespace(), " foo ")).isEqualTo("foo");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" foo "))
        .containsExactly("foo");
    assertThat(parser.source().parseSkipping(whitespace(), "   foo   ")).isEqualTo("foo");
    assertThat(parser.source().skipping(whitespace()).parseToStream("   foo   "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_parseToStream_allCharactersSkipped() {
    assertThat(digits().skipping(whitespace()).parseToStream("     ")).isEmpty();
  }

  @Test
  public void skipping_parseToStream_reader_allCharactersSkipped() {
    assertThat(digits().skipping(whitespace()).parseToStream(new StringReader("     "))).isEmpty();
  }

  @Test
  public void skipping_parseToStream_allSkippablePatternsSkipped() {
    assertThat(
            digits()
                .skipping(
                    string("#")
                        .then(consecutive(isNot('\n'), "comment"))
                        .optionallyFollowedBy("\n"))
                .parseToStream("#comment1\n#comment2"))
        .isEmpty();
  }

  @Test
  public void skipping_parseToStream_reader_allSkippablePatternsSkipped() {
    assertThat(
            digits()
                .skipping(
                    string("#")
                        .then(consecutive(isNot('\n'), "comment"))
                        .optionallyFollowedBy("\n"))
                .parseToStream(new StringReader("#comment1\n#comment2")))
        .isEmpty();
  }

  @Test
  public void skipping_aroundIdentifier_failure() {
    Parser<String> parser = string("foo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " foobar "));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream(" foobar ").toList());
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " foo bar "));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream(" foo bar ").toList());
  }

  @Test
  public void skipping_aroundIdentifier_reader_failure() {
    Parser<String> parser = string("foo");
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream(new StringReader(" foobar ")).toList());
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream(new StringReader(" foo bar ")).toList());
  }

  @Test
  public void skipping_withAnyOf() {
    Parser<String> foobar = anyOf(string("foo"), string("bar"));
    assertThat(foobar.parseSkipping(whitespace(), " foo ")).isEqualTo("foo");
    assertThat(foobar.skipping(whitespace()).parseToStream(" foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_withAnyOf_source() {
    Parser<String> foobar = anyOf(string("foo"), string("bar"));
    assertThat(foobar.source().parseSkipping(whitespace(), " foo ")).isEqualTo("foo");
    assertThat(foobar.source().skipping(whitespace()).parseToStream(" foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughOr() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = foo.or(bar);
    assertThat(parser.skipping(whitespace()).parseToStream("foobar")).containsExactly("foo", "bar");
    assertThat(parser.skipping(whitespace()).parseToStream("foo bar"))
        .containsExactly("foo", "bar");
    assertThat(parser.skipping(whitespace()).parseToStream(" foo bar "))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughOr_source() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = foo.or(bar);
    assertThat(parser.source().skipping(whitespace()).parseToStream("foobar"))
        .containsExactly("foo", "bar");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foo bar"))
        .containsExactly("foo", "bar");
  }

  @Test
  public void skipping_propagatesThroughSequence() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = sequence(foo, bar, (f, b) -> f + b);
    assertThat(parser.parseSkipping(whitespace(), "foobar")).isEqualTo("foobar");
    assertThat(parser.skipping(whitespace()).parseToStream("foobar")).containsExactly("foobar");
    assertThat(parser.parseSkipping(whitespace(), "foo bar")).isEqualTo("foobar");
    assertThat(parser.skipping(whitespace()).parseToStream("foo bar")).containsExactly("foobar");
    assertThat(parser.parseSkipping(whitespace(), " foo bar ")).isEqualTo("foobar");
    assertThat(parser.skipping(whitespace()).parseToStream(" foo bar ")).containsExactly("foobar");
    assertThat(parser.parseSkipping(whitespace(), " foo   bar ")).isEqualTo("foobar");
    assertThat(parser.skipping(whitespace()).parseToStream(" foo   bar "))
        .containsExactly("foobar");
  }

  @Test
  public void skipping_propagatesThroughSequence_source() {
    Parser<String> foo = string("foo");
    Parser<String> bar = string("bar");
    Parser<String> parser = sequence(foo, bar, (f, b) -> f + b);
    assertThat(parser.source().parseSkipping(whitespace(), "foobar")).isEqualTo("foobar");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foobar"))
        .containsExactly("foobar");
    assertThat(parser.source().parseSkipping(whitespace(), "foo bar")).isEqualTo("foo bar");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foo bar"))
        .containsExactly("foo bar");
    assertThat(parser.source().parseSkipping(whitespace(), " foo bar ")).isEqualTo("foo bar");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" foo bar "))
        .containsExactly("foo bar");
    assertThat(parser.source().parseSkipping(whitespace(), " foo   bar ")).isEqualTo("foo   bar");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" foo   bar "))
        .containsExactly("foo   bar");
  }

  @Test
  public void skipping_propagatesThroughConsecutive() {
    assertThat(digits().parseSkipping(whitespace(), "123")).isEqualTo("123");
    assertThat(digits().skipping(whitespace()).parseToStream("123")).containsExactly("123");
    assertThat(digits().parseSkipping(whitespace(), " 123 ")).isEqualTo("123");
    assertThat(digits().skipping(whitespace()).parseToStream(" 123 ")).containsExactly("123");
    assertThrows(ParseException.class, () -> digits().parseSkipping(whitespace(), "123a"));
    assertThrows(ParseException.class, () -> digits().parseSkipping(whitespace(), "1 23"));
  }

  @Test
  public void skipping_propagatesThroughConsecutive_source() {
    assertThat(digits().source().parseSkipping(whitespace(), "123")).isEqualTo("123");
    assertThat(digits().source().skipping(whitespace()).parseToStream("123"))
        .containsExactly("123");
    assertThat(digits().source().parseSkipping(whitespace(), " 123 ")).isEqualTo("123");
    assertThat(digits().source().skipping(whitespace()).parseToStream(" 123 "))
        .containsExactly("123");
    assertThrows(ParseException.class, () -> digits().source().parseSkipping(whitespace(), "123a"));
    assertThrows(ParseException.class, () -> digits().source().parseSkipping(whitespace(), "1 23"));
  }

  @Test
  public void skipping_propagatesThroughSingle() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.parseSkipping(whitespace(), "1")).isEqualTo('1');
    assertThat(parser.skipping(whitespace()).parseToStream("1")).containsExactly('1');
    assertThat(parser.parseSkipping(whitespace(), " 1 ")).isEqualTo('1');
    assertThat(parser.skipping(whitespace()).parseToStream(" 1 ")).containsExactly('1');
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "12"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "a"));
  }

  @Test
  public void skipping_propagatesThroughSingle_source() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.source().parseSkipping(whitespace(), "1")).isEqualTo("1");
    assertThat(parser.source().skipping(whitespace()).parseToStream("1")).containsExactly("1");
    assertThat(parser.source().parseSkipping(whitespace(), " 1 ")).isEqualTo("1");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" 1 ")).containsExactly("1");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), "12"));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), "a"));
  }

  @Test
  public void skipping_propagatesThroughAtLeastOnce() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.atLeastOnce();
    assertThat(parser.parseSkipping(whitespace(), "foofoo"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream("foofoo"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(whitespace(), "foo foo"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream("foo foo"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(whitespace(), " foo   foo "))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream(" foo   foo "))
        .containsExactly(List.of("foo", "foo"));
  }

  @Test
  public void skipping_propagatesThroughAtLeastOnce_source() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.atLeastOnce();
    assertThat(parser.source().parseSkipping(whitespace(), "foofoo")).isEqualTo("foofoo");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foofoo"))
        .containsExactly("foofoo");
    assertThat(parser.source().parseSkipping(whitespace(), "foo foo")).isEqualTo("foo foo");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foo foo"))
        .containsExactly("foo foo");
    assertThat(parser.source().parseSkipping(whitespace(), " foo   foo ")).isEqualTo("foo   foo");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" foo   foo "))
        .containsExactly("foo   foo");
  }

  @Test
  public void skipping_propagatesThroughDelimitedBy() {
    Parser<List<String>> parser = word().atLeastOnceDelimitedBy(",");
    assertThat(parser.parseSkipping(whitespace(), "foo,bar"))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream("foo,bar"))
        .containsExactly(List.of("foo", "bar"));
    assertThat(parser.parseSkipping(whitespace(), " foo, bar "))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.parseSkipping(whitespace(), " foo , bar "))
        .containsExactly("foo", "bar")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream(" foo, bar "))
        .containsExactly(List.of("foo", "bar"));
    assertThat(parser.parseSkipping(whitespace(), " bar,foo, bar "))
        .containsExactly("bar", "foo", "bar")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream(" bar,foo, bar "))
        .containsExactly(List.of("bar", "foo", "bar"));
  }

  @Test
  public void skipping_propagatesThroughDelimitedBy_source() {
    Parser<List<String>> parser = word().atLeastOnceDelimitedBy(",");
    assertThat(parser.source().parseSkipping(whitespace(), "foo,bar")).isEqualTo("foo,bar");
    assertThat(parser.source().skipping(whitespace()).parseToStream("foo,bar"))
        .containsExactly("foo,bar");
    assertThat(parser.source().parseSkipping(whitespace(), " foo, bar ")).isEqualTo("foo, bar");
    assertThat(parser.source().parseSkipping(whitespace(), " foo , bar ")).isEqualTo("foo , bar");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" foo, bar "))
        .containsExactly("foo, bar");
    assertThat(parser.source().parseSkipping(whitespace(), " bar,foo, bar "))
        .isEqualTo("bar,foo, bar");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" bar,foo, bar "))
        .containsExactly("bar,foo, bar");
  }

  @Test
  public void skipping_propagatesThroughFlatMap() {
    Parser<Integer> parser =
        digits().flatMap(number -> string("=").then(string(number).map(Integer::parseInt)));
    assertThat(parser.parseSkipping(whitespace(), "123=123")).isEqualTo(123);
    assertThat(parser.skipping(whitespace()).parseToStream("123=123")).containsExactly(123);
    assertThat(parser.parseSkipping(whitespace(), "123 =123")).isEqualTo(123);
    assertThat(parser.skipping(whitespace()).parseToStream("123 =123")).containsExactly(123);
    assertThat(parser.parseSkipping(whitespace(), "123 = 123 ")).isEqualTo(123);
    assertThat(parser.skipping(whitespace()).parseToStream("123 = 123 ")).containsExactly(123);
    assertThat(parser.parseSkipping(whitespace(), " 123  =123 ")).isEqualTo(123);
    assertThat(parser.skipping(whitespace()).parseToStream(" 123  =123 ")).containsExactly(123);
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "123 == 123"));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream("123 == 123").toList());
  }

  @Test
  public void skipping_propagatesThroughFlatMap_source() {
    Parser<Integer> parser =
        digits().flatMap(number -> string("=").then(string(number).map(Integer::parseInt)));
    assertThat(parser.source().parseSkipping(whitespace(), "123=123")).isEqualTo("123=123");
    assertThat(parser.source().skipping(whitespace()).parseToStream("123=123"))
        .containsExactly("123=123");
    assertThat(parser.source().parseSkipping(whitespace(), "123 =123")).isEqualTo("123 =123");
    assertThat(parser.source().skipping(whitespace()).parseToStream("123 =123"))
        .containsExactly("123 =123");
    assertThat(parser.source().parseSkipping(whitespace(), "123 = 123 ")).isEqualTo("123 = 123");
    assertThat(parser.source().skipping(whitespace()).parseToStream("123 = 123 "))
        .containsExactly("123 = 123");
    assertThat(parser.source().parseSkipping(whitespace(), " 123  =123 ")).isEqualTo("123  =123");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" 123  =123 "))
        .containsExactly("123  =123");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "123 == 123"));
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream("123 == 123").toList());
  }

  @Test
  public void skipping_propagatesThroughOrElse() {
    Parser<String> foo = string("foo");
    Parser<String> parser = foo.orElse("default").between("[", "]");
    assertThat(parser.parseSkipping(whitespace(), "[foo]")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).parseToStream("[foo]")).containsExactly("foo");
    assertThat(parser.parseSkipping(whitespace(), " [ foo ] ")).isEqualTo("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(" [ foo ] ")).containsExactly("foo");
    assertThat(parser.parseSkipping(whitespace(), "[]")).isEqualTo("default");
    assertThat(parser.skipping(whitespace()).parseToStream("[]")).containsExactly("default");
    assertThat(parser.parseSkipping(whitespace(), "[ ]")).isEqualTo("default");
    assertThat(parser.skipping(whitespace()).parseToStream("[ ]")).containsExactly("default");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " "));
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "[bar]"));
  }

  @Test
  public void skipping_propagatesThroughOrElse_source() {
    Parser<String> foo = string("foo");
    Parser<String> parser = foo.orElse("default").between("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), "[foo]")).isEqualTo("[foo]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[foo]"))
        .containsExactly("[foo]");
    assertThat(parser.source().parseSkipping(whitespace(), " [ foo ] ")).isEqualTo("[ foo ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [ foo ] "))
        .containsExactly("[ foo ]");
    assertThat(parser.source().parseSkipping(whitespace(), "[]")).isEqualTo("[]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ ]")).containsExactly("[ ]");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), ""));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), " "));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), "[bar]"));
  }

  @Test
  public void skipping_propagatesThroughZeroOrMore() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.zeroOrMore().between("[", "]");
    assertThat(parser.parseSkipping(whitespace(), "[foo foo]"))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream("[foo foo]"))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(whitespace(), " [ foo foo ] "))
        .containsExactly("foo", "foo")
        .inOrder();
    assertThat(parser.skipping(whitespace()).parseToStream(" [ foo foo ] "))
        .containsExactly(List.of("foo", "foo"));
    assertThat(parser.parseSkipping(whitespace(), "[]")).isEmpty();
    assertThat(parser.skipping(whitespace()).parseToStream("[]"))
        .containsExactly(List.of());
    assertThat(parser.parseSkipping(whitespace(), "[ ]")).isEmpty();
    assertThat(parser.skipping(whitespace()).parseToStream("[ ]"))
        .containsExactly(List.of());
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " "));
  }

  @Test
  public void skipping_propagatesThroughZeroOrMore_source() {
    Parser<String> foo = string("foo");
    Parser<List<String>> parser = foo.zeroOrMore().between("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), "[foo foo]")).isEqualTo("[foo foo]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[foo foo]"))
        .containsExactly("[foo foo]");
    assertThat(parser.source().parseSkipping(whitespace(), " [ foo foo ] "))
        .isEqualTo("[ foo foo ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [ foo foo ] "))
        .containsExactly("[ foo foo ]");
    assertThat(parser.source().parseSkipping(whitespace(), "[]")).isEqualTo("[]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ ]")).containsExactly("[ ]");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), ""));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), " "));
  }

  @Test
  public void zeroOrMore_CharPredicate_matchesZeroTimes() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly("");
    assertThat(parser.parseSkipping(whitespace(), "[ ]")).isEmpty();
    assertThat(parser.skipping(whitespace()).parseToStream("[ ]")).containsExactly("");
  }

  @Test
  public void zeroOrMore_CharPredicate_matchesZeroTimes_source() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ ]")).containsExactly("[ ]");
  }

  @Test
  public void zeroOrMore_CharPredicate_matchesOneTime() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[1]")).isEqualTo("1");
    assertThat(parser.parseToStream("[1]")).containsExactly("1");
    assertThat(parser.parseSkipping(whitespace(), "[ 1 ]")).isEqualTo("1");
    assertThat(parser.skipping(whitespace()).parseToStream("[ 1 ]")).containsExactly("1");
  }

  @Test
  public void zeroOrMore_CharPredicate_matchesOneTime_source() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.source().parse("[1]")).isEqualTo("[1]");
    assertThat(parser.source().parseToStream("[1]")).containsExactly("[1]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ 1 ]")).isEqualTo("[ 1 ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ 1 ]"))
        .containsExactly("[ 1 ]");
  }

  @Test
  public void zeroOrMore_CharPredicate_matchesMultipleTimes() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.parse("[123]")).isEqualTo("123");
    assertThat(parser.parseToStream("[123]")).containsExactly("123");
    assertThat(parser.parseSkipping(whitespace(), "[ 123 ]")).isEqualTo("123");
    assertThat(parser.skipping(whitespace()).parseToStream("[ 123 ]")).containsExactly("123");
  }

  @Test
  public void zeroOrMore_CharPredicate_matchesMultipleTimes_source() {
    Parser<String> parser = zeroOrMore(DIGIT, "digit").between("[", "]");
    assertThat(parser.source().parse("[123]")).isEqualTo("[123]");
    assertThat(parser.source().parseToStream("[123]")).containsExactly("[123]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ 123 ]")).isEqualTo("[ 123 ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ 123 ]"))
        .containsExactly("[ 123 ]");
  }

  @Test
  public void zeroOrMore_characterClass_matchesZeroTimes() {
    Parser<String> parser = zeroOrMore(charsIn("[0-9]")).between("[", "]");
    assertThat(parser.parse("[]")).isEmpty();
    assertThat(parser.parseToStream("[]")).containsExactly("");
    assertThat(parser.parseSkipping(whitespace(), "[ ]")).isEmpty();
    assertThat(parser.skipping(whitespace()).parseToStream("[ ]")).containsExactly("");
  }

  @Test
  public void zeroOrMore_characterClass_matchesZeroTimes_source() {
    Parser<String> parser = zeroOrMore(charsIn("[0-9]")).between("[", "]");
    assertThat(parser.source().parse("[]")).isEqualTo("[]");
    assertThat(parser.source().parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ ]")).containsExactly("[ ]");
  }

  @Test
  public void zeroOrMore_characterClass_matchesOneTime() {
    Parser<String> parser = zeroOrMore(charsIn("[0-9]")).between("[", "]");
    assertThat(parser.parse("[1]")).isEqualTo("1");
    assertThat(parser.parseToStream("[1]")).containsExactly("1");
    assertThat(parser.parseSkipping(whitespace(), "[ 1 ]")).isEqualTo("1");
    assertThat(parser.skipping(whitespace()).parseToStream("[ 1 ]")).containsExactly("1");
  }

  @Test
  public void zeroOrMore_characterClass_matchesOneTime_source() {
    Parser<String> parser = zeroOrMore(charsIn("[0-9]")).between("[", "]");
    assertThat(parser.source().parse("[1]")).isEqualTo("[1]");
    assertThat(parser.source().parseToStream("[1]")).containsExactly("[1]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ 1 ]")).isEqualTo("[ 1 ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ 1 ]"))
        .containsExactly("[ 1 ]");
  }

  @Test
  public void zeroOrMore_characterClass_matchesMultipleTimes() {
    Parser<String> parser = zeroOrMore(charsIn("[0-9]")).between("[", "]");
    assertThat(parser.parse("[123]")).isEqualTo("123");
    assertThat(parser.parseToStream("[123]")).containsExactly("123");
    assertThat(parser.parseSkipping(whitespace(), "[ 123 ]")).isEqualTo("123");
    assertThat(parser.skipping(whitespace()).parseToStream("[ 123 ]")).containsExactly("123");
  }

  @Test
  public void zeroOrMore_characterClass_matchesMultipleTimes_source() {
    Parser<String> parser = zeroOrMore(charsIn("[0-9]")).between("[", "]");
    assertThat(parser.source().parse("[123]")).isEqualTo("[123]");
    assertThat(parser.source().parseToStream("[123]")).containsExactly("[123]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ 123 ]")).isEqualTo("[ 123 ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ 123 ]"))
        .containsExactly("[ 123 ]");
  }

  @Test
  public void skipping_propagatesThroughOptional() {
    Parser<String> foo = string("foo");
    Parser<Optional<String>> parser = foo.optional().between("[", "]");
    assertThat(parser.parseSkipping(whitespace(), "[foo]")).hasValue("foo");
    assertThat(parser.skipping(whitespace()).parseToStream("[foo]"))
        .containsExactly(Optional.of("foo"));
    assertThat(parser.parseSkipping(whitespace(), " [ foo ] ")).hasValue("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(" [ foo ] "))
        .containsExactly(Optional.of("foo"));
    assertThat(parser.parseSkipping(whitespace(), "[]")).isEmpty();
    assertThat(parser.skipping(whitespace()).parseToStream("[]")).containsExactly(Optional.empty());
    assertThat(parser.parseSkipping(whitespace(), "[ ]")).isEmpty();
    assertThat(parser.skipping(whitespace()).parseToStream("[ ]"))
        .containsExactly(Optional.empty());
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), ""));
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), " "));
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "[bar]"));
  }

  @Test
  public void skipping_propagatesThroughOptional_source() {
    Parser<String> foo = string("foo");
    Parser<Optional<String>> parser = foo.optional().between("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), "[foo]")).isEqualTo("[foo]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[foo]"))
        .containsExactly("[foo]");
    assertThat(parser.source().parseSkipping(whitespace(), " [ foo ] ")).isEqualTo("[ foo ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [ foo ] "))
        .containsExactly("[ foo ]");
    assertThat(parser.source().parseSkipping(whitespace(), "[]")).isEqualTo("[]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().parseSkipping(whitespace(), "[ ]")).isEqualTo("[ ]");
    assertThat(parser.source().skipping(whitespace()).parseToStream("[ ]")).containsExactly("[ ]");
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), ""));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), " "));
    assertThrows(ParseException.class, () -> parser.source().parseSkipping(whitespace(), "[bar]"));
  }

  @Test
  public void skipping_simpleLanguage() {
    Parser<?> lineComment = string("//").then(consecutive(isNot('\n'), "line comment"));
    Parser<?> blockComment =
        anyOf(
                consecutive(isNot('*'), "block comment"),
                one(is('*'), "*").notFollowedBy("/").map(Object::toString))
            .zeroOrMore(joining())
            .between("/*", "*/");
    Parser<String> quotedLiteral = zeroOrMore(isNot('\''), "quoted").immediatelyBetween("'", "'");
    Parser<String> language = anyOf(quotedLiteral, digits(), word(), string("("), string(")"));
    Parser<?> skippable = anyOf(consecutive(whitespace(), "whitespace"), lineComment, blockComment);

    assertThat(language.skipping(skippable).parseToStream("foo123(bar)"))
        .containsExactly("foo123", "(", "bar", ")")
        .inOrder();
    assertThat(language.skipping(skippable).parseToStream(" ' foo 123 ' (bar) "))
        .containsExactly(" foo 123 ", "(", "bar", ")")
        .inOrder();
    assertThat(language.skipping(skippable).parseToStream("foo 123 ( bar )"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
    assertThat(
            language
                .skipping(skippable)
                .parseToStream(
                    "foo // ignore this\n123 /* ignore this */ ( bar\n" + "/* and * also */)"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
  }

  @Test
  public void skipping_simpleLanguage_source() {
    Parser<?> lineComment = string("//").then(consecutive(isNot('\n'), "line comment"));
    Parser<?> blockComment =
        anyOf(
                consecutive(isNot('*'), "block comment"),
                one(is('*'), "*").notFollowedBy("/").map(Object::toString))
            .zeroOrMore(joining())
            .between("/*", "*/");
    Parser<String> quotedLiteral = zeroOrMore(isNot('\''), "quoted").immediatelyBetween("'", "'");
    Parser<String> language = anyOf(quotedLiteral, digits(), word(), string("("), string(")"));
    Parser<?> skippable = anyOf(consecutive(whitespace(), "whitespace"), lineComment, blockComment);

    assertThat(language.source().skipping(skippable).parseToStream("foo123(bar)"))
        .containsExactly("foo123", "(", "bar", ")")
        .inOrder();
    assertThat(language.source().skipping(skippable).parseToStream(" ' foo 123 ' (bar) "))
        .containsExactly("' foo 123 '", "(", "bar", ")")
        .inOrder();
    assertThat(language.source().skipping(skippable).parseToStream("foo 123 ( bar )"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
    assertThat(
            language
                .source()
                .skipping(skippable)
                .parseToStream(
                    "foo // ignore this\n123 /* ignore this */ ( bar\n" + "/* and * also */)"))
        .containsExactly("foo", "123", "(", "bar", ")")
        .inOrder();
  }

  @Test
  public void literally_doesNotSkip() {
    assertThat(literally(digits()).parseSkipping(whitespace(), "123")).isEqualTo("123");
    assertThat(literally(digits()).parseSkipping(whitespace(), "123 ")).isEqualTo("123");
    assertThrows(
        ParseException.class, () -> literally(digits()).parseSkipping(whitespace(), " 123"));
    assertThrows(
        ParseException.class, () -> literally(digits()).parseSkipping(whitespace(), " 123 "));

    Parser<List<String>> numbers = literally(digits()).atLeastOnceDelimitedBy(",");
    assertThat(numbers.skipping(whitespace()).parseToStream("1,23"))
        .containsExactly(List.of("1", "23"));
    assertThrows(
        ParseException.class,
        () -> numbers.skipping(whitespace()).parseToStream("1 , 23").toList());
    assertThrows(
        ParseException.class,
        () -> numbers.skipping(whitespace()).parseToStream(" 1 , 23 ").toList());
  }

  @Test
  public void literally_doesNotSkip_source() {
    assertThat(literally(digits()).source().parseSkipping(whitespace(), "123")).isEqualTo("123");
    assertThat(literally(digits()).source().parseSkipping(whitespace(), "123 ")).isEqualTo("123");
    assertThrows(
        ParseException.class,
        () -> literally(digits()).source().parseSkipping(whitespace(), " 123"));
    assertThrows(
        ParseException.class,
        () -> literally(digits()).source().parseSkipping(whitespace(), " 123 "));

    Parser<List<String>> numbers =
        literally(digits()).source().atLeastOnceDelimitedBy(",");
    assertThat(numbers.parseSkipping(whitespace(), "1,23")).containsExactly("1", "23").inOrder();
    assertThat(numbers.skipping(whitespace()).parseToStream("1,23"))
        .containsExactly(asList("1", "23"));
    assertThrows(
        ParseException.class,
        () -> numbers.source().skipping(whitespace()).parseToStream("1 , 23").toList());
    assertThrows(
        ParseException.class,
        () -> numbers.source().skipping(whitespace()).parseToStream(" 1 , 23 ").toList());
  }

  @Test
  public void zeroOrMoreChars_literally_between_zeroMatch() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.parseSkipping(whitespace(), "[]")).isEmpty();
    assertThat(parser.parseSkipping(whitespace(), " [] ")).isEmpty();
    assertThat(parser.parseSkipping(whitespace(), " [ ] ")).isEqualTo(" ");

    assertThat(parser.skipping(whitespace()).parseToStream("[]")).containsExactly("");
    assertThat(parser.skipping(whitespace()).parseToStream(" [] ")).containsExactly("");
    assertThat(parser.skipping(whitespace()).parseToStream(" [  ] ")).containsExactly("  ");
  }

  @Test
  public void zeroOrMoreChars_literally_between_zeroMatch_source() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), "[]")).isEqualTo("[]");
    assertThat(parser.source().parseSkipping(whitespace(), " [] ")).isEqualTo("[]");
    assertThat(parser.source().parseSkipping(whitespace(), " [ ] ")).isEqualTo("[ ]");

    assertThat(parser.source().skipping(whitespace()).parseToStream("[]")).containsExactly("[]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [] ")).containsExactly("[]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [  ] "))
        .containsExactly("[  ]");
  }

  @Test
  public void zeroOrMoreChars_literally_between_oneMatch() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.parseSkipping(whitespace(), "[foo]")).isEqualTo("foo");
    assertThat(parser.parseSkipping(whitespace(), " [foo] ")).isEqualTo("foo");
    assertThat(parser.parseSkipping(whitespace(), " [ foo ] ")).isEqualTo(" foo ");

    assertThat(parser.skipping(whitespace()).parseToStream("[foo]")).containsExactly("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(" [foo] ")).containsExactly("foo");
    assertThat(parser.skipping(whitespace()).parseToStream(" [ foo ] ")).containsExactly(" foo ");
  }

  @Test
  public void zeroOrMoreChars_literally_between_oneMatch_source() {
    Parser<String> parser = literally(zeroOrMore(noneOf("[]"), "name")).between("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), "[foo]")).isEqualTo("[foo]");
    assertThat(parser.source().parseSkipping(whitespace(), " [foo] ")).isEqualTo("[foo]");
    assertThat(parser.source().parseSkipping(whitespace(), " [ foo ] ")).isEqualTo("[ foo ]");

    assertThat(parser.source().skipping(whitespace()).parseToStream("[foo]"))
        .containsExactly("[foo]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [foo] "))
        .containsExactly("[foo]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [ foo ] "))
        .containsExactly("[ foo ]");
  }

  @Test
  public void zeroOrMoreChars_literally_between_multipleMatches() {
    Parser<String> parser = literally(word().orElse("")).between("[", "]");
    assertThat(parser.parseSkipping(whitespace(), "[foofoo]")).isEqualTo("foofoo");
    assertThat(parser.parseSkipping(whitespace(), " [foofoo] ")).isEqualTo("foofoo");
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "[ foofoo]"));
    assertThrows(ParseException.class, () -> parser.parseSkipping(whitespace(), "[foo foo]"));
    assertThat(parser.skipping(whitespace()).parseToStream("[foofoo]")).containsExactly("foofoo");
    assertThat(parser.skipping(whitespace()).parseToStream(" [foofoo] ")).containsExactly("foofoo");
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream("[ foofoo]").toList());
    assertThrows(
        ParseException.class,
        () -> parser.skipping(whitespace()).parseToStream("[foo foo]").toList());
  }

  @Test
  public void zeroOrMoreChars_literally_between_multipleMatches_source() {
    Parser<String> parser = literally(word().orElse("")).between("[", "]");
    assertThat(parser.source().parseSkipping(whitespace(), "[foofoo]")).isEqualTo("[foofoo]");
    assertThat(parser.source().parseSkipping(whitespace(), " [foofoo] ")).isEqualTo("[foofoo]");
    assertThrows(
        ParseException.class, () -> parser.source().parseSkipping(whitespace(), "[ foofoo]"));
    assertThrows(
        ParseException.class, () -> parser.source().parseSkipping(whitespace(), "[foo foo]"));
    assertThat(parser.source().skipping(whitespace()).parseToStream("[foofoo]"))
        .containsExactly("[foofoo]");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" [foofoo] "))
        .containsExactly("[foofoo]");
    assertThrows(
        ParseException.class,
        () -> parser.source().skipping(whitespace()).parseToStream("[ foofoo]").toList());
    assertThrows(
        ParseException.class,
        () -> parser.source().skipping(whitespace()).parseToStream("[foo foo]").toList());
  }

  @Test
  public void notEmpty_twoOptionalParsers_firstOptionalParserFails() {
    var numbers =
        digits().orElse("").delimitedBy(",").followedBy(string(".").optional()).notEmpty();
    assertThat(numbers.parse(".")).containsExactly("");
  }

  @Test
  public void notEmpty_twoOptionalParsers_firstOptionalParserFails_source() {
    var numbers =
        digits().orElse("").delimitedBy(",").followedBy(string(".").optional()).notEmpty();
    assertThat(numbers.source().parse(".")).isEqualTo(".");
  }

  @Test
  public void notEmpty_twoOptionalParsers_secondOptionalParserFails() {
    var numbers =
        digits().orElse("").delimitedBy(",").followedBy(string(".").optional()).notEmpty();
    assertThat(numbers.parse(",123,,")).containsExactly("", "123", "", "").inOrder();
  }

  @Test
  public void notEmpty_twoOptionalParsers_secondOptionalParserFails_source() {
    var numbers =
        digits().orElse("").delimitedBy(",").followedBy(string(".").optional()).notEmpty();
    assertThat(numbers.source().parse(",123,,")).isEqualTo(",123,,");
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothOptionalParsersMatch() {
    var numbers =
        digits().orElse("").delimitedBy(",").followedBy(string(".").optional()).notEmpty();
    assertThat(numbers.parse(",123,,456.")).containsExactly("", "123", "", "456").inOrder();
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothOptionalParsersMatch_source() {
    var numbers =
        digits().orElse("").delimitedBy(",").followedBy(string(".").optional()).notEmpty();
    assertThat(numbers.source().parse(",123,,456.")).isEqualTo(",123,,456.");
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothFail_firstErrorIsFarther() {
    var numbers =
        digits().orElse("").delimitedBy(",").followedBy(string(".").optional()).notEmpty();
    ParseException thrown = assertThrows(ParseException.class, () -> numbers.parse("123,a."));
    assertThat(thrown).hasMessageThat().contains("at 1:5");
    assertThat(thrown).hasMessageThat().contains(" encountered [a.]");
  }

  @Test
  public void notEmpty_twoOptionalParsers_bothFail_secondErrorIsFarther() {
    var numbers =
        digits()
            .orElse("")
            .delimitedBy(",")
            .followedBy(string("abc,1").followedBy("!").optional())
            .notEmpty();
    ParseException thrown = assertThrows(ParseException.class, () -> numbers.parse("abc,1."));
    assertThat(thrown).hasMessageThat().contains("at 1:6: expecting <!>, encountered [.]");
  }

  @Test
  public void skipping_anyOfWithLiterally() {
    assertThat(anyOf(string("foo"), literally(digits())).parseSkipping(whitespace(), " foo"))
        .isEqualTo("foo");
    assertThat(
            anyOf(string("foo"), literally(digits())).skipping(whitespace()).parseToStream(" foo"))
        .containsExactly("foo");
    assertThrows(
        ParseException.class,
        () -> anyOf(string("foo"), literally(digits())).parseSkipping(whitespace(), " 123"));
    assertThrows(
        ParseException.class,
        () ->
            anyOf(string("foo"), literally(digits()))
                .skipping(whitespace())
                .parseToStream(" 123")
                .toList());
  }

  @Test
  public void skipping_anyOfWithLiterally_source() {
    assertThat(
            anyOf(string("foo"), literally(digits())).source().parseSkipping(whitespace(), " foo"))
        .isEqualTo("foo");
    assertThat(
            anyOf(string("foo"), literally(digits()))
                .source()
                .skipping(whitespace())
                .parseToStream(" foo"))
        .containsExactly("foo");
    assertThrows(
        ParseException.class,
        () ->
            anyOf(string("foo"), literally(digits())).source().parseSkipping(whitespace(), " 123"));
    assertThrows(
        ParseException.class,
        () ->
            anyOf(string("foo"), literally(digits()))
                .source()
                .skipping(whitespace())
                .parseToStream(" 123")
                .toList());
  }

  @Test
  public void skipping_anyOfWithoutLiterally() {
    assertThat(anyOf(string("foo"), digits()).parseSkipping(whitespace(), " foo")).isEqualTo("foo");
    assertThat(anyOf(string("foo"), digits()).skipping(whitespace()).parseToStream(" foo"))
        .containsExactly("foo");
    assertThat(anyOf(string("foo"), digits()).parseSkipping(whitespace(), " 123")).isEqualTo("123");
    assertThat(anyOf(string("foo"), digits()).skipping(whitespace()).parseToStream(" 123"))
        .containsExactly("123");
  }

  @Test
  public void skipping_anyOfWithoutLiterally_source() {
    assertThat(anyOf(string("foo"), digits()).source().parseSkipping(whitespace(), " foo"))
        .isEqualTo("foo");
    assertThat(anyOf(string("foo"), digits()).source().skipping(whitespace()).parseToStream(" foo"))
        .containsExactly("foo");
    assertThat(anyOf(string("foo"), digits()).source().parseSkipping(whitespace(), " 123"))
        .isEqualTo("123");
    assertThat(anyOf(string("foo"), digits()).source().skipping(whitespace()).parseToStream(" 123"))
        .containsExactly("123");
  }

  @Test
  public void skipping_propagatesThroughRuleParser() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.parseSkipping(whitespace(), " ( 2 ) + 3 ")).isEqualTo(5);
    assertThat(parser.skipping(whitespace()).parseToStream(" ( 2 ) + 3 ")).containsExactly(5);
    assertThat(parser.parseSkipping(whitespace(), " ( 2 + ( 3 + 4 ) ) ")).isEqualTo(9);
    assertThat(parser.skipping(whitespace()).parseToStream(" ( 2 + ( 3 + 4 ) ) "))
        .containsExactly(9);
  }

  @Test
  public void skipping_propagatesThroughRuleParser_source() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.source().parseSkipping(whitespace(), " ( 2 ) + 3 ")).isEqualTo("( 2 ) + 3");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" ( 2 ) + 3 "))
        .containsExactly("( 2 ) + 3");
    assertThat(parser.source().parseSkipping(whitespace(), " ( 2 + ( 3 + 4 ) ) "))
        .isEqualTo("( 2 + ( 3 + 4 ) )");
    assertThat(parser.source().skipping(whitespace()).parseToStream(" ( 2 + ( 3 + 4 ) ) "))
        .containsExactly("( 2 + ( 3 + 4 ) )");
  }

  @Test
  public void recursiveGrammar() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parseToStream("1")).containsExactly(1);
    assertThat(parser.parse("(2)")).isEqualTo(2);
    assertThat(parser.parseToStream("(2)")).containsExactly(2);
    assertThat(parser.parse("(2)+3")).isEqualTo(5);
    assertThat(parser.parseToStream("(2)+3")).containsExactly(5);
    assertThat(parser.parse("(2)+3+(4)")).isEqualTo(9);
    assertThat(parser.parseToStream("(2)+3+(4)")).containsExactly(9);
    assertThat(parser.parse("(2+(3+4))")).isEqualTo(9);
    assertThat(parser.parseToStream("(2+(3+4))")).containsExactly(9);
  }

  private static Parser<Integer> simpleCalculator() {
    Parser.Rule<Integer> rule = new Parser.Rule<>();
    Parser<Integer> num = Parser.one(DIGIT, "digit").map(c -> c - '0');
    Parser<Integer> atomic = rule.between("(", ")").or(num);
    Parser<Integer> expr =
        atomic.atLeastOnceDelimitedBy("+").map(nums -> nums.stream().mapToInt(n -> n).sum());
    return rule.definedAs(expr);
  }

  @Test
  public void recursiveGrammar_source() {
    Parser<Integer> parser = simpleCalculator();
    assertThat(parser.source().parse("1")).isEqualTo("1");
    assertThat(parser.source().parseToStream("1")).containsExactly("1");
    assertThat(parser.source().parse("(2)")).isEqualTo("(2)");
    assertThat(parser.source().parseToStream("(2)")).containsExactly("(2)");
    assertThat(parser.source().parse("(2)+3")).isEqualTo("(2)+3");
    assertThat(parser.source().parseToStream("(2)+3")).containsExactly("(2)+3");
    assertThat(parser.source().parse("(2)+3+(4)")).isEqualTo("(2)+3+(4)");
    assertThat(parser.source().parseToStream("(2)+3+(4)")).containsExactly("(2)+3+(4)");
    assertThat(parser.source().parse("(2+(3+4))")).isEqualTo("(2+(3+4))");
    assertThat(parser.source().parseToStream("(2+(3+4))")).containsExactly("(2+(3+4))");
  }

  @Test
  public void rule_setTwice_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    rule.definedAs(string("a"));
    assertThrows(IllegalStateException.class, () -> rule.definedAs(string("b")));
  }

  @Test
  public void rule_setNull_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    Parser<String> parser = null;
    assertThrows(NullPointerException.class, () -> rule.definedAs(parser));
  }

  @Test
  public void rule_ruleParseBeforeDefined_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    assertThrows(IllegalStateException.class, () -> rule.parse("a"));
    assertThrows(IllegalStateException.class, () -> rule.parseToStream("a").toList());
  }

  @Test
  public void rule_definedAsRule_throws() {
    Parser.Rule<String> rule = new Parser.Rule<>();
    Parser<String> actuallyRule = rule;
    assertThrows(IllegalArgumentException.class, () -> rule.definedAs(actuallyRule));
  }

  @Test
  public void parseToStream_success() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.parseToStream("123")).containsExactly('1', '2', '3').inOrder();
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void parseToStream_reader_success() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.parseToStream(new StringReader("123")))
        .containsExactly('1', '2', '3')
        .inOrder();
    assertThat(parser.parseToStream(new StringReader("")).toList()).isEmpty();
  }

  @Test
  public void parseToStream_withCompactingReader() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    assertThat(one(DIGIT, "digit").parseToStream(input, 0))
        .containsExactly('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        .inOrder();
  }

  @Test
  public void parseToStream_withCompactingReader_fails() {
    CharInput input = CharInput.from(new StringReader("01 \n234 \n567 \n89 x"), 4, 3);
    ParseException e =
        assertThrows(
            ParseException.class,
            () -> digits().skipping(whitespace()).parseToStream(input, 0).count());
    assertThat(e).hasMessageThat().contains("at 17: expecting <digits>, encountered [x]");
  }

  @Test
  public void parseToStream_success_source() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.source().parseToStream("123")).containsExactly("1", "2", "3").inOrder();
  }

  @Test
  public void parseToStream_emptyInput() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.parseToStream("").toList()).isEmpty();
  }

  @Test
  public void parseToStream_fromIndex() {
    assertThat(digits().skipping(string(",")).parseToStream("1,2,3,4", 2))
        .containsExactly("2", "3", "4");
    assertThat(digits().source().skipping(string(",")).parseToStream("1,2,3,4", 2))
        .containsExactly("2", "3", "4");
  }

  @Test
  public void parseToStream_fromIndex_atEnd() {
    assertThat(digits().parseToStream("123", 3)).isEmpty();
    assertThat(digits().skipping(whitespace()).parseToStream("123  ", 3)).isEmpty();
  }

  @Test
  public void parseToStream_fromIndex_outOfBounds() {
    assertThrows(IndexOutOfBoundsException.class, () -> digits().parseToStream("123", 4));
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> digits().skipping(whitespace()).parseToStream("123 ", 5));
  }

  @Test
  public void parseToStream_reader_emptyInput() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThat(parser.parseToStream(new StringReader("")).toList()).isEmpty();
  }

  @Test
  public void parseToStream_fail() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThrows(ParseException.class, () -> parser.parseToStream("1a2").toList());
  }

  @Test
  public void parseToStream_reader_fail() {
    Parser<Character> parser = one(DIGIT, "digit");
    assertThrows(
        ParseException.class, () -> parser.parseToStream(new StringReader("1a2")).toList());
  }

  @Test
  public void probe_emptyInput_returnsEmpty() {
    assertThat(string("foo").probe("")).isEmpty();
  }

  @Test
  public void probe_singleMatch_returnsValue() {
    assertThat(string("foo").probe("foo")).containsExactly("foo");
  }

  @Test
  public void probe_singleMatch_returnsValue_source() {
    assertThat(string("foo").source().probe("foo")).containsExactly("foo");
  }

  @Test
  public void probe_multipleMatches_returnsValue() {
    assertThat(string("foo").probe("foofoo")).containsExactly("foo", "foo");
  }

  @Test
  public void probe_reader_singleMatch_returnsValue() {
    assertThat(string("foo").probe(new StringReader("foo"))).containsExactly("foo");
  }

  @Test
  public void probe_reader_multipleMatches_returnsValue() {
    assertThat(string("foo").probe(new StringReader("foofoo"))).containsExactly("foo", "foo");
  }

  @Test
  public void probe_withCompactingReader() {
    CharInput input = CharInput.from(new StringReader("0123456789"), 10, 5);
    assertThat(one(DIGIT, "digit").probe(input, 0))
        .containsExactly('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
        .inOrder();
  }

  @Test
  public void probe_multipleMatches_returnsValue_source() {
    assertThat(string("foo").source().probe("foofoo")).containsExactly("foo", "foo");
  }

  @Test
  public void probe_fromIndex() {
    assertThat(digits().skipping(string(",")).probe("1,2,3,4", 2)).containsExactly("2", "3", "4");
    assertThat(digits().source().skipping(string(",")).probe("1,2,3,4", 2))
        .containsExactly("2", "3", "4");
  }

  @Test
  public void probe_fromIndex_atEnd() {
    assertThat(digits().probe("123", 3)).isEmpty();
    assertThat(digits().skipping(whitespace()).probe("123  ", 3)).isEmpty();
  }

  @Test
  public void probe_fromIndex_outOfBounds() {
    assertThrows(IndexOutOfBoundsException.class, () -> digits().probe("123", 4));
    assertThrows(
        IndexOutOfBoundsException.class, () -> digits().skipping(whitespace()).probe("123 ", 5));
  }

  @Test
  public void probe_prefixMatch_returnsValue() {
    assertThat(string("foo").probe("foobar")).containsExactly("foo");
  }

  @Test
  public void probe_reader_prefixMatch_returnsValue() {
    assertThat(string("foo").probe(new StringReader("foobar"))).containsExactly("foo");
  }

  @Test
  public void probe_prefixMatch_returnsValue_source() {
    assertThat(string("foo").source().probe("foobar")).containsExactly("foo");
  }

  @Test
  public void probe_noMatch_returnsEmpty() {
    assertThat(string("foo").probe("bar")).isEmpty();
  }

  @Test
  public void probe_reader_noMatch_returnsEmpty() {
    assertThat(string("foo").probe(new StringReader("bar"))).isEmpty();
  }

  @Test
  public void probe_noMatch_returnsEmpty_source() {
    assertThat(string("foo").source().probe("bar")).isEmpty();
  }

  @Test
  public void isPrefixOf_matchesPrefix() {
    assertThat(Parser.string("a").isPrefixOf("a")).isTrue();
    assertThat(Parser.string("a").isPrefixOf("ab")).isTrue();
    assertThat(Parser.string("b").isPrefixOf("ab")).isFalse();
    assertThat(Parser.string("a").isPrefixOf("")).isFalse();
  }

  @Test
  public void isPrefixOf_emptyString() {
    assertThat(Parser.string("a").isPrefixOf("")).isFalse();
  }

  @Test
  public void isPrefixOf_exactMatch() {
    assertThat(Parser.string("abc").isPrefixOf("abc")).isTrue();
  }

  @Test
  public void isPrefixOf_notAPrefix() {
    assertThat(Parser.string("abc").isPrefixOf("ab")).isFalse();
  }

  @Test
  public void skipping_probeCharPredicate_emptyInput_returnsEmpty() {
    assertThat(string("foo").skipping(whitespace()).probe(" ")).isEmpty();
  }

  @Test
  public void skipping_probeReader_emptyInput_returnsEmpty() {
    assertThat(string("foo").skipping(whitespace()).probe(new StringReader(" "))).isEmpty();
  }

  @Test
  public void skipping_probeCharPredicate_emptyInput_returnsEmpty_source() {
    assertThat(string("foo").source().skipping(whitespace()).probe(" ")).isEmpty();
  }

  @Test
  public void skipping_probeCharPredicate_singleMatch_returnsValue() {
    assertThat(string("foo").skipping(whitespace()).probe(" foo ")).containsExactly("foo");
  }

  @Test
  public void skipping_probeReader_singleMatch_returnsValue() {
    assertThat(string("foo").skipping(whitespace()).probe(new StringReader(" foo ")))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_singleMatch_returnsValue_source() {
    assertThat(string("foo").source().skipping(whitespace()).probe(" foo ")).containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_multipleMatches_returnsValues() {
    assertThat(digits().skipping(whitespace()).probe(" 123  456 "))
        .containsExactly("123", "456")
        .inOrder();
  }

  @Test
  public void skipping_probeReader_multipleMatches_returnsValues() {
    assertThat(digits().skipping(whitespace()).probe(new StringReader(" 123  456 ")))
        .containsExactly("123", "456")
        .inOrder();
  }

  @Test
  public void skipping_probeCharPredicate_multipleMatches_returnsValues_source() {
    assertThat(digits().source().skipping(whitespace()).probe(" 123  456 "))
        .containsExactly("123", "456")
        .inOrder();
  }

  @Test
  public void skipping_probeCharPredicate_prefixMatchWithSkipping_returnsValue() {
    assertThat(string("foo").skipping(whitespace()).probe(" foobar ")).containsExactly("foo");
  }

  @Test
  public void skipping_probeReader_prefixMatchWithSkipping_returnsValue() {
    assertThat(string("foo").skipping(whitespace()).probe(new StringReader(" foobar ")))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_prefixMatchWithSkipping_returnsValue_source() {
    assertThat(string("foo").source().skipping(whitespace()).probe(" foobar "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeCharPredicate_noMatch_returnsEmpty() {
    assertThat(string("foo").skipping(whitespace()).probe("bar")).isEmpty();
  }

  @Test
  public void skipping_probeReader_noMatch_returnsEmpty() {
    assertThat(string("foo").skipping(whitespace()).probe(new StringReader("bar"))).isEmpty();
  }

  @Test
  public void skipping_probeCharPredicate_noMatch_returnsEmpty_source() {
    assertThat(string("foo").source().skipping(whitespace()).probe("bar")).isEmpty();
  }

  @Test
  public void skipping_probeParser_singleMatch_returnsValue() {
    assertThat(string("foo").skipping(consecutive(whitespace(), "skip")).probe(" \n foo "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_singleMatch_returnsValue_source() {
    assertThat(string("foo").source().skipping(consecutive(whitespace(), "skip")).probe(" \n foo "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_multipleMatches() {
    assertThat(word().skipping(consecutive(whitespace(), "skip")).probe(" \n foo 123"))
        .containsExactly("foo", "123")
        .inOrder();
  }

  @Test
  public void skipping_probeParser_multipleMatches_source() {
    assertThat(word().source().skipping(consecutive(whitespace(), "skip")).probe(" \n foo 123"))
        .containsExactly("foo", "123")
        .inOrder();
  }

  @Test
  public void skipping_probeParser_prefixMatchWithSkipping_returnsValue() {
    assertThat(string("foo").skipping(consecutive(whitespace(), "skip")).probe(" foobar "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_prefixMatchWithSkipping_returnsValue_source() {
    assertThat(string("foo").source().skipping(consecutive(whitespace(), "skip")).probe(" foobar "))
        .containsExactly("foo");
  }

  @Test
  public void skipping_probeParser_noMatch_returnsEmpty() {
    assertThat(string("foo").skipping(consecutive(whitespace(), "skip")).probe("bar")).isEmpty();
  }

  @Test
  public void skipping_probeParser_noMatch_returnsEmpty_source() {
    assertThat(string("foo").source().skipping(consecutive(whitespace(), "skip")).probe("bar"))
        .isEmpty();
  }

  @Test
  public void parse_fromIndex_negative_throws() {
    Parser<String> parser = string("foo");
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> parser.parse("foo", -1));
    assertThat(e).hasMessageThat().isEqualTo("fromIndex (-1) must be in range of [0..3]");
  }

  @Test
  public void parse_fromIndex_negative_skipping_throws() {
    Parser<String>.Lexical lexical = string("foo").skipping(whitespace());
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> lexical.parse("foo", -1));
    assertThat(e).hasMessageThat().isEqualTo("fromIndex (-1) must be in range of [0..3]");
  }

  @Test
  public void parseToStream_fromIndex_negative_throws() {
    Parser<String> parser = string("foo");
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> parser.parseToStream("foo", -1));
    assertThat(e).hasMessageThat().isEqualTo("fromIndex (-1) must be in range of [0..3]");
  }

  @Test
  public void parseToStream_fromIndex_negative_skipping_throws() {
    Parser<String>.Lexical lexical = string("foo").skipping(whitespace());
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> lexical.parseToStream("foo", -1));
    assertThat(e).hasMessageThat().contains("fromIndex (-1)");
  }

  @Test
  public void probe_fromIndex_negative_throws() {
    Parser<String> parser = string("foo");
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> parser.probe("foo", -1));
    assertThat(e).hasMessageThat().isEqualTo("fromIndex (-1) must be in range of [0..3]");
  }

  @Test
  public void probe_fromIndex_negative_skipping_throws() {
    Parser<String>.Lexical lexical = string("foo").skipping(whitespace());
    IndexOutOfBoundsException e =
        assertThrows(IndexOutOfBoundsException.class, () -> lexical.probe("foo", -1));
    assertThat(e).hasMessageThat().isEqualTo("fromIndex (-1) must be in range of [0..3]");
  }

  @Test
  public void testNestedPlaceholderGrammar() {
    assertThat(Format.parse("a{b=xy{foo=bar}z}d{e=f}{{not a placeholder}}"))
        .isEqualTo(
            new Format(
                "a{b}d{e}{not a placeholder}",
                List.of(
                    new Format.Placeholder(
                        "b",
                        new Format(
                            "xy{foo}z",
                            List.of(
                                new Format.Placeholder(
                                    "foo", new Format("bar", List.of()))))),
                    new Format.Placeholder("e", new Format("f", List.of())))));
  }

  @Test
  public void testNestedPlaceholderGrammar_source() {
    String input = "a{b=xy{foo=bar}z}d{e=f}{{not a placeholder}}";
    assertThat(Format.parser().source().parse(input)).isEqualTo(input);
  }

  @Test public void testDigits_getPrefixes() {
    assertThat(Parser.digits().getPrefixes()).containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
  }

  /** An example nested placeholder grammar for demo purpose. */
  private record Format(String template, List<Placeholder> placeholders) {

    record Placeholder(String name, Format format) {}

    static class Builder {
      private final ImmutableList.Builder<Placeholder> placeholders = ImmutableList.builder();
      private final StringBuilder template = new StringBuilder();

      @CanIgnoreReturnValue
      Builder append(String text) {
        template.append(text);
        return this;
      }

      @CanIgnoreReturnValue
      Builder append(Placeholder placeholder) {
        template.append("{").append(placeholder.name()).append("}");
        placeholders.add(placeholder);
        return this;
      }

      @CanIgnoreReturnValue
      Builder addAll(Builder that) {
        template.append(that.template);
        placeholders.addAll(that.placeholders.build());
        return this;
      }

      Format build() {
        return new Format(template.toString(), placeholders.build());
      }
    }

    static Parser<Format> parser() {
      Parser.Rule<Format> rule = new Parser.Rule<>();
      Parser<String> placeholderName = consecutive(charsIn("[a-z]"));
      Parser<Placeholder> placeholder =
          Parser.sequence(placeholderName.followedBy("="), rule, Placeholder::new)
              .between("{", "}");
      Parser<Format> parser =
          anyOf(
                  placeholder,
                  Parser.string("{{").thenReturn("{"), // escape {
                  Parser.string("}}").thenReturn("}"), // escape }
                  consecutive(noneOf("{}"), "literal text"))
              .atLeastOnce(
                  Collector.of(
                      Format.Builder::new,
                      (Format.Builder b, Object v) -> {
                        if (v instanceof Placeholder p) {
                          b.append(p);
                        } else {
                          b.append((String) v);
                        }
                      },
                      Format.Builder::addAll,
                      Format.Builder::build));
      return rule.definedAs(parser);
    }

    static Format parse(String format) {
      return parser().parse(format);
    }
  }

  /** An example resource name pattern for demo purpose. */
  record ResourceNamePattern(List<PathElement> path, String revision) {
    ResourceNamePattern(List<PathElement> path) {
      this(path, null);
    }

    ResourceNamePattern withRevision(String revision) {
      return new ResourceNamePattern(path, revision);
    }

    static Parser<ResourceNamePattern> parser() {
      Parser<String> name = Parser.word();
      Parser<String> revision = string("@").then(name);
      Parser<PathElement.Subpath> subpath =
          Parser.sequence(
                  name.followedBy("="),
                  Parser.<PathElement>anyOf(
                          name.map(PathElement.Literal::new),
                          string("**").thenReturn(new PathElement.SubpathWildcard()),
                          string("*").thenReturn(new PathElement.PathElementWildcard()))
                      .atLeastOnceDelimitedBy("/")
                      .map(ResourceNamePattern::new),
                  PathElement.Subpath::new)
              .between("{", "}");
      return Parser.<PathElement>anyOf(
              name.map(PathElement.Literal::new),
              name.between("{", "}").map(PathElement.Placeholder::new),
              subpath)
          .atLeastOnceDelimitedBy("/")
          .map(ResourceNamePattern::new)
          .optionalPostfix(revision.map(v -> pattern -> pattern.withRevision(v)));
    }

    static ResourceNamePattern parse(String path) {
      return parser().parse(path);
    }
  }

  private sealed interface PathElement
      permits PathElement.Literal,
          PathElement.Placeholder,
          PathElement.Subpath,
          PathElement.PathElementWildcard,
          PathElement.SubpathWildcard {
    record Literal(String value) implements PathElement {}

    record Placeholder(String name) implements PathElement {}

    record Subpath(String name, ResourceNamePattern pattern) implements PathElement {}

    record PathElementWildcard() implements PathElement {}

    record SubpathWildcard() implements PathElement {}
  }

  @Test
  public void resourceNamePattern_noPlaceholder() {
    assertThat(ResourceNamePattern.parse("users"))
        .isEqualTo(new ResourceNamePattern(List.of(new PathElement.Literal("users"))));
    assertThat(ResourceNamePattern.parser().matches("users")).isTrue();
  }

  @Test
  public void resourceNamePattern_noPlaceholder_source() {
    String input = "users";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
    assertThat(ResourceNamePattern.parser().source().matches(input)).isTrue();
  }

  @Test
  public void resourceNamePattern_withSimplePlaceholder() {
    assertThat(ResourceNamePattern.parse("users/{userId}/messages/{messageId}"))
        .isEqualTo(
            new ResourceNamePattern(
                List.of(
                    new PathElement.Literal("users"),
                    new PathElement.Placeholder("userId"),
                    new PathElement.Literal("messages"),
                    new PathElement.Placeholder("messageId"))));
    assertThat(ResourceNamePattern.parser().matches("users/{userId}/messages/{messageId}"))
        .isTrue();
  }

  @Test
  public void resourceNamePattern_withSimplePlaceholder_source() {
    String input = "users/{userId}/messages/{messageId}";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
    assertThat(ResourceNamePattern.parser().source().matches(input)).isTrue();
  }

  @Test
  public void resourceNamePattern_withSubpathPlaceholder() {
    assertThat(ResourceNamePattern.parse("v1/{name=projects/*/locations/*}/messages"))
        .isEqualTo(
            new ResourceNamePattern(
                List.of(
                    new PathElement.Literal("v1"),
                    new PathElement.Subpath(
                        "name",
                        new ResourceNamePattern(
                            List.of(
                                new PathElement.Literal("projects"),
                                new PathElement.PathElementWildcard(),
                                new PathElement.Literal("locations"),
                                new PathElement.PathElementWildcard()))),
                    new PathElement.Literal("messages"))));
    assertThat(ResourceNamePattern.parser().matches("v1/{name=projects/*/locations/*}/messages"))
        .isTrue();
  }

  @Test
  public void resourceNamePattern_withSubpathPlaceholder_source() {
    String input = "v1/{name=projects/*/locations/*}/messages";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
    assertThat(ResourceNamePattern.parser().source().matches(input)).isTrue();
  }

  @Test
  public void resourceNamePattern_withSubpathWildcard() {
    assertThat(ResourceNamePattern.parse("v1/{name=projects/**}/messages"))
        .isEqualTo(
            new ResourceNamePattern(
                List.of(
                    new PathElement.Literal("v1"),
                    new PathElement.Subpath(
                        "name",
                        new ResourceNamePattern(
                            List.of(
                                new PathElement.Literal("projects"),
                                new PathElement.SubpathWildcard()))),
                    new PathElement.Literal("messages"))));
    assertThat(ResourceNamePattern.parser().matches("v1/{name=projects/**}/messages")).isTrue();
  }

  @Test
  public void resourceNamePattern_withSubpathWildcard_source() {
    String input = "v1/{name=projects/**}/messages";
    assertThat(ResourceNamePattern.parser().source().parse(input)).isEqualTo(input);
    assertThat(ResourceNamePattern.parser().source().matches(input)).isTrue();
  }

  @Test
  public void byStringsFrom_success() {
    Parser<IntOperator> parser = Parser.byStringsFrom(IntOperator.values());
    assertThat(parser.parse("+")).isEqualTo(IntOperator.PLUS);
    assertThat(parser.parse("-")).isEqualTo(IntOperator.MINUS);
    assertThat(parser.parse("++")).isEqualTo(IntOperator.INCREMENT);
    assertThat(parser.parse("--")).isEqualTo(IntOperator.DECREMENT);
  }

  @Test
  public void byStringsFrom_failure() {
    Parser<IntOperator> parser = Parser.byStringsFrom(IntOperator.values());
    assertThrows(ParseException.class, () -> parser.parse("="));
  }

  @Test
  public void byStringsFrom_pruning() {
    Parser<IntOperator> parser = Parser.byStringsFrom(IntOperator.values());
    assertThat(parser.getPrefixes()).containsExactly("+", "-", "*", "/");

    Parser<Object> anyOfParser = anyOf(string("x1"), parser, chars(4));

    // Input "+" should match Operator.PLUS.
    assertThat(anyOfParser.parse("+")).isEqualTo(IntOperator.PLUS);

    // Input "z" should prune x1 and parser. Only chars(4) tried.
    ParseException e = assertThrows(ParseException.class, () -> anyOfParser.parse("z"));
    assertThat(e).hasMessageThat().contains("expecting <4 char(s)>");
  }

  @Test
  public void byStringsFrom_shuffled_success() {
    // Specifically shuffle them so that we have ++ before + but -- after -
    Parser<IntOperator> parser =
        Parser.byStringsFrom(
            IntOperator.INCREMENT,
            IntOperator.PLUS,
            IntOperator.DIVIDE,
            IntOperator.MINUS,
            IntOperator.DECREMENT,
            IntOperator.MULTIPLY);
    assertThat(parser.parse("++")).isEqualTo(IntOperator.INCREMENT);
    assertThat(parser.parse("+")).isEqualTo(IntOperator.PLUS);
    assertThat(parser.parse("--")).isEqualTo(IntOperator.DECREMENT);
    assertThat(parser.parse("-")).isEqualTo(IntOperator.MINUS);
    assertThat(parser.parse("*")).isEqualTo(IntOperator.MULTIPLY);
    assertThat(parser.parse("/")).isEqualTo(IntOperator.DIVIDE);
  }

  @Test
  public void byStringsFrom_nonEnum_success() {
    Parser<Integer> parser = Parser.byStringsFrom(1, 10, 100);
    assertThat(parser.parse("1")).isEqualTo(1);
    assertThat(parser.parse("10")).isEqualTo(10);
    assertThat(parser.parse("100")).isEqualTo(100);
  }

  @Test
  public void byStringsFrom_oneElement_success() {
    Parser<String> parser = Parser.byStringsFrom("foo");
    assertThat(parser.parse("foo")).isEqualTo("foo");
    assertThrows(ParseException.class, () -> parser.parse("bar"));
    assertThat(parser.getPrefixes()).containsExactly("foo");
  }

  @Test
  public void byStringsFrom_emptyString_failure() {
    assertThrows(IllegalArgumentException.class, () -> Parser.byStringsFrom(""));
  }

  @Test
  public void byStringsFrom_anyEmptyString_failure() {
    assertThrows(IllegalArgumentException.class, () -> Parser.byStringsFrom("foo", ""));
  }

  @Test
  public void byStringsFrom_duplicate_failure() {
    assertThrows(IllegalArgumentException.class, () -> Parser.byStringsFrom(100, 100));
  }

  @Test
  public void byStringsFrom_empty_failure() {
    assertThrows(IllegalArgumentException.class, () -> Parser.byStringsFrom());
  }

  @Test
  public void byStringsFrom_nullValue_failure() {
    assertThrows(NullPointerException.class, () -> Parser.byStringsFrom(null, 100));
  }

  private enum IntOperator {
    PLUS("+"),
    MINUS("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    INCREMENT("++"),
    DECREMENT("--"),
    ;

    private final String s;

    IntOperator(String s) {
      this.s = s;
    }

    @Override
    public String toString() {
      return s;
    }
  }

  private static CharPredicate whitespace() {
    return Character::isWhitespace;
  }
}

