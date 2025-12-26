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

  @Test public void joinEmptyInput() {
    assertThat(Stream.empty().collect(Joiner.on(", ")))
        .isEmpty();
    assertThat(Stream.empty().collect(Joiner.on(", ").between("(", ")")))
        .isEqualTo("()");
  }

  @Test public void joinSingleString() {
    assertThat(Stream.of("str").collect(Joiner.on(", ")))
        .isEqualTo("str");
    assertThat(Stream.of("str").collect(Joiner.on(", ").between("(", ")")))
        .isEqualTo("(str)");
    assertThat(Stream.of("str").collect(Joiner.on(", ").between("", ".")))
        .isEqualTo("str.");
    assertThat(Stream.of("str").collect(Joiner.on(", ").between("$", "")))
        .isEqualTo("$str");
  }


  @Test public void joinTwoObjects() {
    assertThat(
            BiStream.of(1, "one", 2, "two")
                .mapToObj(Joiner.on(", ").between('(', ')')::join)
                .collect(Joiner.on(", ")))
        .isEqualTo("(1, one), (2, two)");
    assertThat(
            BiStream.of(1, "one", 2, "two")
                .mapToObj(Joiner.on(", ").between('(', ')')::join)
                .collect(Joiner.on(", ").between("[", "]")))
        .isEqualTo("[(1, one), (2, two)]");
  }

  @Test public void joinParallel() {
    assertThat(
            BiStream.of(1, "one")
                .mapToObj(Joiner.on(", ").between('(', ')')::join)
                .parallel()
                .collect(Joiner.on(", ")))
        .isEqualTo("(1, one)");
    assertThat(
            BiStream.of(1, "one", 2, "two")
                .mapToObj(Joiner.on(", ").between('(', ')')::join)
                .parallel()
                .collect(Joiner.on(", ")))
        .isEqualTo("(1, one), (2, two)");
    assertThat(
            BiStream.of(1, "one", 2, "two", 3, "three")
                .mapToObj(Joiner.on(", ").between('(', ')')::join)
                .parallel()
                .collect(Joiner.on(", ")))
        .isEqualTo("(1, one), (2, two), (3, three)");
    assertThat(
            BiStream.of(1, "one", 2, "two", 3, "three", 4, "four", 5, "five", 6, "six", 7, "seven", 8, "eight")
                .mapToObj(Joiner.on(", ").between('(', ')')::join)
                .parallel()
                .collect(Joiner.on(", ")))
        .isEqualTo("(1, one), (2, two), (3, three), (4, four), (5, five), (6, six), (7, seven), (8, eight)");
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
