package com.google.mu.benchmarks;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.CharPredicate.is;
import static com.google.mu.util.CharPredicate.noneOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.google.common.labs.parse.Parser;

@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MarkdownLinkBenchmark {
  private static final Parser<MarkdownLink> LINK =
      sequence(
          quotedByWithEscapes('[', ']', chars(1)),
          literally(quotedByWithEscapes('(', ')', chars(1))),
          MarkdownLink::new);

  private static final Parser<?> IGNORED = anyOf(
      one(is('\\'), "escape").followedBy(chars(1)),
      consecutive(is('`'), "backticks").flatMap(Parser::first),
      one(noneOf("\\[`"), "ignored char"));

  private static Stream<MarkdownLink> parseMarkdownLinks(String markdown) {
    return LINK.skipping(IGNORED).parseToStream(markdown);
  }

  @Test
  public void testParseLink() {
    assertThat(parseMarkdownLinks("[text](url)"))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testIgnoreCodeBlock() {
    assertThat(parseMarkdownLinks("```[text](url)```")).isEmpty();
  }

  @Test
  public void testIgnoreCodeSpan() {
    assertThat(parseMarkdownLinks("`[text](url)`")).isEmpty();
  }

  @Test
  public void testIgnoreDoubleBacktickCodeSpan() {
    assertThat(parseMarkdownLinks("``[text](url)``")).isEmpty();
  }

  @Test
  public void testIgnoreArbitraryBacktickCodeSpan() {
    assertThat(parseMarkdownLinks("````[text](url)````")).isEmpty();
  }

  @Test
  public void testQuotedMarkdownLink() {
    assertThat(parseMarkdownLinks("\"[text](url)\""))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testEscapeThenMarkdownLink() {
    assertThat(parseMarkdownLinks("\\[text](url)\"")).isEmpty();
  }

  @Test
  public void testDoubleEscapeThenMarkdownLink() {
    assertThat(parseMarkdownLinks("\\\\[text](url)\""))
        .containsExactly(new MarkdownLink("text", "url"));
  }

  @Test
  public void testEscaping() {
    assertThat(parseMarkdownLinks("[x\\[y\\]](url)"))
        .containsExactly(new MarkdownLink("x[y]", "url"));
  }

  @Test
  public void testUnclosedBackticks() {
    assertThrows(Parser.ParseException.class, () -> parseMarkdownLinks("`unclosed [link](url)").toList());
  }

  private static final String INPUT = "This is a [link](http://example.com) and `[ignored](link)` and ```[ignored too](link)```.";
  private static final int SCALE = 1000;
  private static final String LARGE_INPUT = String.join(" ", java.util.Collections.nCopies(SCALE, INPUT));

  @Benchmark
  public long dotParse() {
    return parseMarkdownLinks(LARGE_INPUT).count();
  }

  @Test public void testDotParseBenchmark() {
    assertThat(dotParse()).isEqualTo(SCALE);
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }

  record MarkdownLink(String text, String url) {}
}
