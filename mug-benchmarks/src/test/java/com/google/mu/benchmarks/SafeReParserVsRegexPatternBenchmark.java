package com.google.mu.benchmarks;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.regex.RegexPattern;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
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
 * Benchmarks comparing SafeRE's internal {@code Parser.parse()} AST parser against {@link
 * RegexPattern#of(String)} and JDK {@link Pattern#compile(String)}.
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SafeReParserVsRegexPatternBenchmark {

  static final String SIMPLE_IDENTIFIER = "[a-zA-Z_][a-zA-Z0-9_]*";

  static final String EMAIL = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

  static final String URL = "^https?://[\\w.-]+(:\\d+)?(/[^\\s?]*)?(\\?[^\\s#]*)?(#\\S*)?$";

  static final String ISO_DATE_TIME =
      "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])T([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d(\\.\\d+)?(Z|[+-][01]\\d:[0-5]\\d)?$";

  static final String SEMVER =
      "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$";

  static final String IPV6 =
      "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]+|::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1\\d|[1-9]?\\d))\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]?\\d))|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1\\d|[1-9]?\\d))\\.){3}(25[0-5]|(2[0-4]|1\\d|[1-9]?\\d)))$";

  static final String HTTP_LOG =
      "^(\\S+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(?:GET|POST|PUT|DELETE|HEAD|OPTIONS)"
          + " ([^ ]*) (HTTP/[0-9.]+)\" (\\d{3}) (\\d+|-)(?: \"([^\"]*)\" \"([^\"]*)\")?$";

  static final String COMPLEX_URL_NAMED_GROUPS =
      "^(?<protocol>https?)://(?:(?<user>[a-zA-Z0-9_]+)(?::(?<pass>[^@]+))?@)?(?<host>[a-zA-Z0-9.-]+)(?::(?<port>\\d+))?(?<path>/[^?#]*)?(?:\\?(?<query>[^#]*))?(?:#(?<fragment>.*))?$";

  static final String NESTED_CHAR_CLASS =
      "^[a-z\\p{Digit}&&[^c-g\\p{Upper}]&&[\\p{ASCII}&&[^0-9]]]+$";

  private static final MethodHandle SAFERE_PARSE = getSafeReParser();
  private static final int SAFERE_LIKE_PERL =
      (1 << 2) | (1 << 4) | (1 << 7) | (1 << 8) | (1 << 9) | (1 << 10);

  private static MethodHandle getSafeReParser() {
    try {
      Class<?> parserClass = Class.forName("org.safere.Parser");
      Method parseMethod = parserClass.getDeclaredMethod("parse", String.class, int.class);
      parseMethod.setAccessible(true);
      return MethodHandles.lookup()
          .unreflect(parseMethod)
          .asType(MethodType.methodType(Object.class, String.class, int.class));
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static Object safeReParse(String pattern) {
    try {
      return SAFERE_PARSE.invokeExact(pattern, SAFERE_LIKE_PERL);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  // ---- simpleIdentifier ----
  @Benchmark
  public Object safereParse_simpleIdentifier() {
    return safeReParse(SIMPLE_IDENTIFIER);
  }

  @Benchmark
  public RegexPattern regexPattern_simpleIdentifier() {
    return RegexPattern.of(SIMPLE_IDENTIFIER);
  }

  @Benchmark
  public Pattern patternCompile_simpleIdentifier() {
    return Pattern.compile(SIMPLE_IDENTIFIER);
  }

  // ---- email ----
  @Benchmark
  public Object safereParse_email() {
    return safeReParse(EMAIL);
  }

  @Benchmark
  public RegexPattern regexPattern_email() {
    return RegexPattern.of(EMAIL);
  }

  @Benchmark
  public Pattern patternCompile_email() {
    return Pattern.compile(EMAIL);
  }

  // ---- url ----
  @Benchmark
  public Object safereParse_url() {
    return safeReParse(URL);
  }

  @Benchmark
  public RegexPattern regexPattern_url() {
    return RegexPattern.of(URL);
  }

  @Benchmark
  public Pattern patternCompile_url() {
    return Pattern.compile(URL);
  }

  // ---- isoDateTime ----
  @Benchmark
  public Object safereParse_isoDateTime() {
    return safeReParse(ISO_DATE_TIME);
  }

  @Benchmark
  public RegexPattern regexPattern_isoDateTime() {
    return RegexPattern.of(ISO_DATE_TIME);
  }

  @Benchmark
  public Pattern patternCompile_isoDateTime() {
    return Pattern.compile(ISO_DATE_TIME);
  }

  // ---- semver ----
  @Benchmark
  public Object safereParse_semver() {
    return safeReParse(SEMVER);
  }

  @Benchmark
  public RegexPattern regexPattern_semver() {
    return RegexPattern.of(SEMVER);
  }

  @Benchmark
  public Pattern patternCompile_semver() {
    return Pattern.compile(SEMVER);
  }

  // ---- ipv6 ----
  @Benchmark
  public Object safereParse_ipv6() {
    return safeReParse(IPV6);
  }

  @Benchmark
  public RegexPattern regexPattern_ipv6() {
    return RegexPattern.of(IPV6);
  }

  @Benchmark
  public Pattern patternCompile_ipv6() {
    return Pattern.compile(IPV6);
  }

  // ---- httpLog ----
  @Benchmark
  public Object safereParse_httpLog() {
    return safeReParse(HTTP_LOG);
  }

  @Benchmark
  public RegexPattern regexPattern_httpLog() {
    return RegexPattern.of(HTTP_LOG);
  }

  @Benchmark
  public Pattern patternCompile_httpLog() {
    return Pattern.compile(HTTP_LOG);
  }

  // ---- complexUrlNamedGroups ----
  @Benchmark
  public Object safereParse_complexUrlNamedGroups() {
    return safeReParse(COMPLEX_URL_NAMED_GROUPS);
  }

  @Benchmark
  public RegexPattern regexPattern_complexUrlNamedGroups() {
    return RegexPattern.of(COMPLEX_URL_NAMED_GROUPS);
  }

  @Benchmark
  public Pattern patternCompile_complexUrlNamedGroups() {
    return Pattern.compile(COMPLEX_URL_NAMED_GROUPS);
  }

  // ---- nestedCharClass ----
  @Benchmark
  public Object safereParse_nestedCharClass() {
    return safeReParse(NESTED_CHAR_CLASS);
  }

  @Benchmark
  public RegexPattern regexPattern_nestedCharClass() {
    return RegexPattern.of(NESTED_CHAR_CLASS);
  }

  @Benchmark
  public Pattern patternCompile_nestedCharClass() {
    return Pattern.compile(NESTED_CHAR_CLASS);
  }

  @Test public void verifyBenchmarkCorrectness() {
    assertThat(safereParse_simpleIdentifier()).isNotNull();
    assertThat(regexPattern_simpleIdentifier()).isNotNull();
    assertThat(patternCompile_simpleIdentifier()).isNotNull();

    assertThat(safereParse_email()).isNotNull();
    assertThat(regexPattern_email()).isNotNull();
    assertThat(patternCompile_email()).isNotNull();

    assertThat(safereParse_url()).isNotNull();
    assertThat(regexPattern_url()).isNotNull();
    assertThat(patternCompile_url()).isNotNull();

    assertThat(safereParse_isoDateTime()).isNotNull();
    assertThat(regexPattern_isoDateTime()).isNotNull();
    assertThat(patternCompile_isoDateTime()).isNotNull();

    assertThat(safereParse_semver()).isNotNull();
    assertThat(regexPattern_semver()).isNotNull();
    assertThat(patternCompile_semver()).isNotNull();

    assertThat(safereParse_ipv6()).isNotNull();
    assertThat(regexPattern_ipv6()).isNotNull();
    assertThat(patternCompile_ipv6()).isNotNull();

    assertThat(safereParse_httpLog()).isNotNull();
    assertThat(regexPattern_httpLog()).isNotNull();
    assertThat(patternCompile_httpLog()).isNotNull();

    assertThat(safereParse_complexUrlNamedGroups()).isNotNull();
    assertThat(regexPattern_complexUrlNamedGroups()).isNotNull();
    assertThat(patternCompile_complexUrlNamedGroups()).isNotNull();

    assertThat(safereParse_nestedCharClass()).isNotNull();
    assertThat(regexPattern_nestedCharClass()).isNotNull();
    assertThat(patternCompile_nestedCharClass()).isNotNull();
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}
