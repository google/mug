package com.google.mu.benchmarks;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.labs.regex.RegexPattern;
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
 * Benchmarks comparing {@link RegexPattern#of(String)} AST parsing against JDK {@link
 * Pattern#compile(String)}.
 */
@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class RegexPatternBenchmark {

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

  static final String LOOKAROUND_CAMEL_CASE =
      "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|(?<=[0-9])(?=[a-zA-Z])|(?<=[a-zA-Z])(?=[0-9])";

  @Benchmark
  public RegexPattern regexPattern_simpleIdentifier() {
    return RegexPattern.of(SIMPLE_IDENTIFIER);
  }

  @Benchmark
  public Pattern patternCompile_simpleIdentifier() {
    return Pattern.compile(SIMPLE_IDENTIFIER);
  }

  @Benchmark
  public RegexPattern regexPattern_email() {
    return RegexPattern.of(EMAIL);
  }

  @Benchmark
  public Pattern patternCompile_email() {
    return Pattern.compile(EMAIL);
  }

  @Benchmark
  public RegexPattern regexPattern_url() {
    return RegexPattern.of(URL);
  }

  @Benchmark
  public Pattern patternCompile_url() {
    return Pattern.compile(URL);
  }

  @Benchmark
  public RegexPattern regexPattern_isoDateTime() {
    return RegexPattern.of(ISO_DATE_TIME);
  }

  @Benchmark
  public Pattern patternCompile_isoDateTime() {
    return Pattern.compile(ISO_DATE_TIME);
  }

  @Benchmark
  public RegexPattern regexPattern_semver() {
    return RegexPattern.of(SEMVER);
  }

  @Benchmark
  public Pattern patternCompile_semver() {
    return Pattern.compile(SEMVER);
  }

  @Benchmark
  public RegexPattern regexPattern_ipv6() {
    return RegexPattern.of(IPV6);
  }

  @Benchmark
  public Pattern patternCompile_ipv6() {
    return Pattern.compile(IPV6);
  }

  @Benchmark
  public RegexPattern regexPattern_httpLog() {
    return RegexPattern.of(HTTP_LOG);
  }

  @Benchmark
  public Pattern patternCompile_httpLog() {
    return Pattern.compile(HTTP_LOG);
  }

  @Benchmark
  public RegexPattern regexPattern_complexUrlNamedGroups() {
    return RegexPattern.of(COMPLEX_URL_NAMED_GROUPS);
  }

  @Benchmark
  public Pattern patternCompile_complexUrlNamedGroups() {
    return Pattern.compile(COMPLEX_URL_NAMED_GROUPS);
  }

  @Benchmark
  public RegexPattern regexPattern_nestedCharClass() {
    return RegexPattern.of(NESTED_CHAR_CLASS);
  }

  @Benchmark
  public Pattern patternCompile_nestedCharClass() {
    return Pattern.compile(NESTED_CHAR_CLASS);
  }

  @Benchmark
  public RegexPattern regexPattern_lookaroundCamelCase() {
    return RegexPattern.of(LOOKAROUND_CAMEL_CASE);
  }

  @Benchmark
  public Pattern patternCompile_lookaroundCamelCase() {
    return Pattern.compile(LOOKAROUND_CAMEL_CASE);
  }

  @Test public void testSimpleIdentifier_regexPattern() {
    assertThat(regexPattern_simpleIdentifier()).isNotNull();
  }

  @Test public void testSimpleIdentifier_patternCompile() {
    assertThat(patternCompile_simpleIdentifier()).isNotNull();
  }

  @Test public void testEmail_regexPattern() {
    assertThat(regexPattern_email()).isNotNull();
  }

  @Test public void testEmail_patternCompile() {
    assertThat(patternCompile_email()).isNotNull();
  }

  @Test public void testUrl_regexPattern() {
    assertThat(regexPattern_url()).isNotNull();
  }

  @Test public void testUrl_patternCompile() {
    assertThat(patternCompile_url()).isNotNull();
  }

  @Test public void testIsoDateTime_regexPattern() {
    assertThat(regexPattern_isoDateTime()).isNotNull();
  }

  @Test public void testIsoDateTime_patternCompile() {
    assertThat(patternCompile_isoDateTime()).isNotNull();
  }

  @Test public void testSemver_regexPattern() {
    assertThat(regexPattern_semver()).isNotNull();
  }

  @Test public void testSemver_patternCompile() {
    assertThat(patternCompile_semver()).isNotNull();
  }

  @Test public void testIpv6_regexPattern() {
    assertThat(regexPattern_ipv6()).isNotNull();
  }

  @Test public void testIpv6_patternCompile() {
    assertThat(patternCompile_ipv6()).isNotNull();
  }

  @Test public void testHttpLog_regexPattern() {
    assertThat(regexPattern_httpLog()).isNotNull();
  }

  @Test public void testHttpLog_patternCompile() {
    assertThat(patternCompile_httpLog()).isNotNull();
  }

  @Test public void testComplexUrlNamedGroups_regexPattern() {
    assertThat(regexPattern_complexUrlNamedGroups()).isNotNull();
  }

  @Test public void testComplexUrlNamedGroups_patternCompile() {
    assertThat(patternCompile_complexUrlNamedGroups()).isNotNull();
  }

  @Test public void testNestedCharClass_regexPattern() {
    assertThat(regexPattern_nestedCharClass()).isNotNull();
  }

  @Test public void testNestedCharClass_patternCompile() {
    assertThat(patternCompile_nestedCharClass()).isNotNull();
  }

  @Test public void testLookaroundCamelCase_regexPattern() {
    assertThat(regexPattern_lookaroundCamelCase()).isNotNull();
  }

  @Test public void testLookaroundCamelCase_patternCompile() {
    assertThat(patternCompile_lookaroundCamelCase()).isNotNull();
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}
