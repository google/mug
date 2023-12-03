package com.google.mu.bigquery;

import static com.google.common.truth.Truth.assertThat;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.EqualsTester;

@RunWith(JUnit4.class)
public class TrustedSqlTest {
  @Test public void testJoining() {
    assertThat(Stream.of(TrustedSql.of("a"), TrustedSql.of("b")).collect(TrustedSql.joining(", ")))
        .isEqualTo(TrustedSql.of("a, b"));
  }
  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(TrustedSql.of("foo"), TrustedSql.of("foo"))
        .addEqualityGroup(TrustedSql.of("bar"))
        .testEquals();
  }
}
