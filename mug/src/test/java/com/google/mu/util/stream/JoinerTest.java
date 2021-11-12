package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;

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

  @Test public void nestedBetween_joinTwoObjects() {
    assertThat(
            BiStream.of(1, "one", 2, "two")
                .mapToObj(Joiner.on(", ").between('(', ')').between("[", "]")::join)
                .collect(Joiner.on(", ")))
        .isEqualTo("[(1, one)], [(2, two)]");
  }

  @Test public void nestedBetween_joinStreamOfObjects() {
    assertThat(
            BiStream.of(1, "one", 2, "two")
                .mapToObj(Joiner.on(':')::join)
                .collect(Joiner.on(", ").between("[", "]").between('{', '}')))
        .isEqualTo("{[1:one, 2:two]}");
  }
}
