package com.google.mu.benchmarks;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.dotparse.StringInParser;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StringInParserTest {

  @Test
  public void stringIn_longestMatchFirst() {
    Parser<String> parser = StringInParser.stringIn(List.of("foo", "foobar", "foobaz", "bar"));

    assertThat(parser.parse("foobar")).isEqualTo("foobar");
    assertThat(parser.parse("foobaz")).isEqualTo("foobaz");
    assertThat(parser.parse("bar")).isEqualTo("bar");
    assertThat(parser.parse("foo")).isEqualTo("foo");

    // "foobat" should fail to parse because "foo" matches but leaves "bat", which is not EOF.
    assertThrows(Parser.ParseException.class, () -> parser.parse("foobat"));
  }

  @Test
  public void oneOf_firstMatchFirst() {
    // In oneOf, "foo" is before "foobar". So "foobar" will match "foo", leaving "bar".
    // Since "bar" is left over, parser.parse("foobar") will fail because it's not EOF.
    Parser<?> parser1 = StringInParser.oneOf(List.of("foo", "foobar"));
    assertThrows(Parser.ParseException.class, () -> parser1.parse("foobar"));

    // If we put "foobar" first, it should succeed.
    Parser<?> parser2 = StringInParser.oneOf(List.of("foobar", "foo"));
    assertThat(parser2.parse("foobar")).isNull();
  }
}
