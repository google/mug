package com.google.mu.benchmarks;

import static com.google.common.truth.Truth.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.List;
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

import com.google.common.labs.email.EmailAddress;
import com.sanctionco.jmail.JMail;
import jakarta.mail.internet.InternetAddress;

@RunWith(JUnit4.class)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class EmailAddressBenchmark {
  private static final String VALID_INPUT =
      "a@b.com, \"John Doe\" <john.doe@example.com>, user@company.co.uk, normal.address@domain.org";
  private static final String MIXED_INPUT =
      "a@b.com, invalid-address, \"John Doe\" <john.doe@example.com>, wrong@@domain.com, normal.address@domain.org";

  @Benchmark
  public List<EmailAddress> parseValidList() {
    return EmailAddress.parseAddressList(VALID_INPUT);
  }

  @Benchmark
  public EmailAddress parseSinglePlainAddress() {
    return EmailAddress.of("user@company.com");
  }

  @Benchmark
  public Object jmail_parseSinglePlainAddress() {
    return JMail.tryParse("user@company.com");
  }

  @Benchmark
  public Object jakarta_parseSinglePlainAddress() throws Exception {
    return new InternetAddress("user@company.com", true);
  }

  @Benchmark
  public List<EmailAddress> parseMixedList() {
    return EmailAddress.parseAddressList(MIXED_INPUT, discarded -> {});
  }

  @Test public void testBenchmark() {
    assertThat(parseValidList()).hasSize(4);
    assertThat(parseMixedList()).hasSize(3);
  }

  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }
}
