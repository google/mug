package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;

import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JoinerTest {

  @Test public void join_twoObjects() {
    assertThat(Joiner.on('=').join("one", 1)).isEqualTo("one=1");
    assertThat(Joiner.on('=').join("a", null)).isEqualTo("a=null");
    assertThat(Joiner.on('=').join(null, "a")).isEqualTo("null=a");
    assertThat(Joiner.on('=').join(null, null)).isEqualTo("null=null");
  }

  @Test public void join_bistream() {
    assertThat(BiStream.of(1, "one", 2, "two").mapToObj(Joiner.on('-')::join).collect(Joiner.on(", ")))
        .isEqualTo("1-one, 2-two");
  }

  @Test public void join_streamValues() {
    assertThat(BiStream.of(1, "one", 2, "two").mapValues(Joiner.on('-')::join).values().collect(Joiner.on(", ")))
        .isEqualTo("1-one, 2-two");
  }

  @Test public void between_joinTwoObjects() {
    assertThat(
            BiStream.of(1, "one", 2, "two")
                .mapToObj(Joiner.on(", ").between('(', ')')::join)
                .collect(Joiner.on(", ")))
        .isEqualTo("(1, one), (2, two)");
  }

  @Test public void between_joinStreamOfObjects() {
    assertThat(
            BiStream.of(1, "one", 2, "two")
                .mapToObj(Joiner.on(':')::join)
                .collect(Joiner.on(", ").between('{', '}')))
        .isEqualTo("{1:one, 2:two}");
  }

  @Test public void skipNulls_nullsSkipped() {
    assertThat(Stream.of(1, null, 3).collect(Joiner.on(',').skipNulls()))
        .isEqualTo("1,3");
  }

  @Test public void skipNulls_emptyStringNotSkipped() {
    assertThat(Stream.of(1, "", 3).collect(Joiner.on(',').skipNulls()))
        .isEqualTo("1,,3");
  }

  @Test public void skipEmpties_nullSkipped() {
    assertThat(Stream.of("foo", null, "zoo").collect(Joiner.on(',').skipNulls()))
        .isEqualTo("foo,zoo");
  }

  @Test public void skipEmpties_emptyStringSkipped() {
    assertThat(Stream.of("foo", "", "zoo", "").collect(Joiner.on(',').skipEmpties()))
        .isEqualTo("foo,zoo");
  }
}
