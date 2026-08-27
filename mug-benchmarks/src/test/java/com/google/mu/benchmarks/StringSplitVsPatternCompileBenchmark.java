package com.google.mu.benchmarks;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Splitter;
import com.google.mu.util.StringFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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

/**
 * Benchmarks comparing {@link Pattern#compile(String)} against string splitting and trimming of
 * equal-sized strings.
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class StringSplitVsPatternCompileBenchmark {

  private static final String REGEX_25_CHARS = "[a-zA-Z_][a-zA-Z0-9_]*";
  private static final String REGEX_50_CHARS = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

  private static final String STRING_25_CHARS = "apple, banana, orange, pear";
  private static final String STRING_50_CHARS =
      " user1@domain.com , user2@domain.com , user3@domain.com ";

  private static final String FORMAT_25_CHARS = "{a}, {b}, {c}, {d}";
  private static final String FORMAT_50_CHARS = "user: {user1}, email: {user2}, id: {user3}";

  private static final String SPRINTF_25_CHARS = "%s, %s, %s, %s";
  private static final String SPRINTF_50_CHARS = "user: %s, email: %s, id: %s";

  private static final StringFormat PRECOMPILED_FORMAT_25 = new StringFormat(FORMAT_25_CHARS);
  private static final StringFormat PRECOMPILED_FORMAT_50 = new StringFormat(FORMAT_50_CHARS);

  private static final Pattern PRECOMPILED_COMMA_SPACES = Pattern.compile("\\s*,\\s*");
  private static final Splitter GUAVA_SPLITTER = Splitter.on(',').trimResults();

  @Benchmark
  public String stringPrintf_25Chars() {
    return String.format(SPRINTF_25_CHARS, "apple", "banana", "orange", "pear");
  }

  @Benchmark
  public String stringPrintf_50Chars() {
    return String.format(
        SPRINTF_50_CHARS, "user1@domain.com", "user2@domain.com", "user3@domain.com");
  }

  @Benchmark
  public String stringFormatUsing_25Chars() {
    return StringFormat.using(FORMAT_25_CHARS, "apple", "banana", "orange", "pear");
  }

  @Benchmark
  public String stringFormatUsing_50Chars() {
    return StringFormat.using(
        FORMAT_50_CHARS, "user1@domain.com", "user2@domain.com", "user3@domain.com");
  }

  @Benchmark
  public String stringFormatPrecompiled_25Chars() {
    return PRECOMPILED_FORMAT_25.format("apple", "banana", "orange", "pear");
  }

  @Benchmark
  public String stringFormatPrecompiled_50Chars() {
    return PRECOMPILED_FORMAT_50.format("user1@domain.com", "user2@domain.com", "user3@domain.com");
  }

  @Benchmark
  public StringFormat stringFormatConstruct_25Chars() {
    return new StringFormat(FORMAT_25_CHARS);
  }

  @Benchmark
  public StringFormat stringFormatConstruct_50Chars() {
    return new StringFormat(FORMAT_50_CHARS);
  }

  @Benchmark
  public Pattern patternCompile_25Chars() {
    return Pattern.compile(REGEX_25_CHARS);
  }

  @Benchmark
  public Pattern patternCompile_50Chars() {
    return Pattern.compile(REGEX_50_CHARS);
  }

  @Benchmark
  public String[] jdkSplitAndTrim_25Chars() {
    String[] parts = STRING_25_CHARS.split(",");
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].trim();
    }
    return parts;
  }

  @Benchmark
  public String[] jdkSplitAndTrim_50Chars() {
    String[] parts = STRING_50_CHARS.split(",");
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].trim();
    }
    return parts;
  }

  @Benchmark
  public List<String> guavaSplitter_25Chars() {
    return GUAVA_SPLITTER.splitToList(STRING_25_CHARS);
  }

  @Benchmark
  public List<String> guavaSplitter_50Chars() {
    return GUAVA_SPLITTER.splitToList(STRING_50_CHARS);
  }

  @Benchmark
  public String[] regexSplitInline_25Chars() {
    return STRING_25_CHARS.split("\\s*,\\s*");
  }

  @Benchmark
  public String[] regexSplitPrecompiled_25Chars() {
    return PRECOMPILED_COMMA_SPACES.split(STRING_25_CHARS);
  }

  @Test public void verifyBenchmarkCorrectness() {
    assertThat(stringFormatConstruct_25Chars()).isNotNull();
    assertThat(stringFormatConstruct_50Chars()).isNotNull();
    assertThat(stringPrintf_25Chars()).isEqualTo("apple, banana, orange, pear");
    assertThat(stringPrintf_50Chars())
        .isEqualTo("user: user1@domain.com, email: user2@domain.com, id: user3@domain.com");
    assertThat(stringFormatUsing_25Chars()).isEqualTo("apple, banana, orange, pear");
    assertThat(stringFormatUsing_50Chars())
        .isEqualTo("user: user1@domain.com, email: user2@domain.com, id: user3@domain.com");
    assertThat(stringFormatPrecompiled_25Chars()).isEqualTo("apple, banana, orange, pear");
    assertThat(stringFormatPrecompiled_50Chars())
        .isEqualTo("user: user1@domain.com, email: user2@domain.com, id: user3@domain.com");
    assertThat(patternCompile_25Chars()).isNotNull();
    assertThat(patternCompile_50Chars()).isNotNull();
    assertThat(jdkSplitAndTrim_25Chars())
        .asList()
        .containsExactly("apple", "banana", "orange", "pear")
        .inOrder();
    assertThat(jdkSplitAndTrim_50Chars())
        .asList()
        .containsExactly("user1@domain.com", "user2@domain.com", "user3@domain.com")
        .inOrder();
    assertThat(guavaSplitter_25Chars())
        .containsExactly("apple", "banana", "orange", "pear")
        .inOrder();
    assertThat(guavaSplitter_50Chars())
        .containsExactly("user1@domain.com", "user2@domain.com", "user3@domain.com")
        .inOrder();
    assertThat(regexSplitInline_25Chars())
        .asList()
        .containsExactly("apple", "banana", "orange", "pear")
        .inOrder();
    assertThat(regexSplitPrecompiled_25Chars())
        .asList()
        .containsExactly("apple", "banana", "orange", "pear")
        .inOrder();
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}
