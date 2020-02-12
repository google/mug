package com.google.mu.util.stream;

import static com.google.mu.util.stream.Case.TinyContainer.toTinyContainer;
import static com.google.mu.util.stream.Case.onlyElement;
import static com.google.mu.util.stream.Case.onlyElements;
import static com.google.mu.util.stream.Case.switching;
import static com.google.mu.util.stream.Case.when;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.function.Function.identity;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.testing.NullPointerTester;
import com.google.mu.util.stream.Case.TinyContainer;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CaseTest {
  @Test
  public void when_zeroElement() {
    assertThat(Stream.of(1).collect(when(() -> "zero"))).isEmpty();
    assertThat(Stream.empty().collect(when(() -> "zero"))).hasValue("zero");
  }

  @Test
  public void when_oneElement() {
    assertThat(Stream.of(1).collect(when(i -> i + 1))).hasValue(2);
    assertThat(Stream.of(1, 2).collect(when(i -> i + 1))).isEmpty();
    assertThat(Stream.of(1).collect(when(x -> x == 1, i -> i + 1))).hasValue(2);
    assertThat(Stream.of(1).collect(when(x -> x == 2, i -> i + 1))).isEmpty();
  }

  @Test
  public void when_twoElements() {
    assertThat(Stream.of(2, 3).collect(when((a, b) -> a * b))).hasValue(6);
    assertThat(Stream.of(2, 3, 4).collect(when((a, b) -> a * b))).isEmpty();
    assertThat(Stream.of(2, 3).collect(when((x, y) -> x < y, (a, b) -> a * b))).hasValue(6);
    assertThat(Stream.of(2, 3).collect(when((x, y) -> x > y, (a, b) -> a * b))).isEmpty();
  }

  @Test
  public void only_oneElement() {
    String result = Stream.of("foo").collect(onlyElement());
    assertThat(result).isEqualTo("foo");
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> Stream.of(1, 2, 3).collect(onlyElement()));
    assertThat(thrown).hasMessageThat().contains("size: 3");
  }

  @Test
  public void only_twoElements() {
    int result = Stream.of(2, 3).collect(onlyElements((a, b) -> a * b));
    assertThat(result).isEqualTo(6);
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> Stream.of(1).collect(onlyElements((a, b) -> a * b)));
    assertThat(thrown).hasMessageThat().contains("size: 1");
  }

  @Test
  public void switching_firstCaseMatch() {
    String result = Stream.of("foo", "bar").collect(switching(when((a, b) -> a + b), when(a -> a)));
    assertThat(result).isEqualTo("foobar");
  }

  @Test
  public void switching_secondCaseMatch() {
    String result = Stream.of("foo").collect(switching(when((a, b) -> a + b), when(a -> a)));
    assertThat(result).isEqualTo("foo");
  }

  @Test
  public void switching_noMatchingCase() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> Stream.of(1, 2, 3).collect(switching(when((a, b) -> a + b), when(a -> a))));
    assertThat(thrown).hasMessageThat().contains("size: 3");
  }

  @Test
  public void toTinyContainer_empty() {
    assertThat(Stream.empty().collect(toTinyContainer()).size()).isEqualTo(0);
  }

  @Test
  public void toTinyContainer_oneElement() {
    assertThat(Stream.of("foo").collect(toTinyContainer()).when(x -> true, "got:"::concat))
        .hasValue("got:foo");
  }

  @Test
  public void toTinyContainer_twoElements() {
    assertThat(Stream.of(2, 3).collect(toTinyContainer()).when((x, y) -> true, Integer::max))
        .hasValue(3);
  }

  @Test
  public void tinyContainer_addAll_fromEmpty() {
    TinyContainer<String> empty = new TinyContainer<>();
    assertThat(Stream.empty().collect(toTinyContainer()).addAll(empty).size()).isEqualTo(0);
    assertThat(Stream.of("foo").collect(toTinyContainer()).addAll(empty).size()).isEqualTo(1);
    assertThat(Stream.of("foo", "bar").collect(toTinyContainer()).addAll(empty).size())
        .isEqualTo(2);
    assertThat(Stream.of("foo", "bar", "baz").collect(toTinyContainer()).addAll(empty).size())
        .isEqualTo(3);
  }

  @Test
  public void tinyContainer_addAll_fromOneElement() {
    TinyContainer<String> source = Stream.of("foo").collect(toTinyContainer());
    assertThat(Stream.empty().collect(toTinyContainer()).addAll(source).when(x -> true, identity()))
        .hasValue("foo");
    assertThat(Stream.of("bar").collect(toTinyContainer()).addAll(source).when((x, y) -> true, (a, b) -> a + b))
        .hasValue("barfoo");
    assertThat(Stream.of("a", "b").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(3);
    assertThat(Stream.of("a", "b", "c").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(4);
  }

  @Test
  public void tinyContainer_addAll_fromTwoElements() {
    TinyContainer<String> source = Stream.of("a", "b").collect(toTinyContainer());
    assertThat(Stream.<String>empty().collect(toTinyContainer()).addAll(source).when((x, y) -> true, (a, b) -> a + b))
        .hasValue("ab");
    assertThat(Stream.of("c").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(3);
    assertThat(Stream.of("c", "d").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(4);
    assertThat(Stream.of("c", "d", "e").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(5);
  }

  @Test
  public void tinyContainer_addAll_fromThreeElements() {
    TinyContainer<String> source = Stream.of("a", "b", "c").collect(toTinyContainer());
    assertThat(Stream.empty().collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(3);
    assertThat(Stream.of("c").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(4);
    assertThat(Stream.of("c", "d").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(5);
    assertThat(Stream.of("c", "d", "e").collect(toTinyContainer()).addAll(source).size())
        .isEqualTo(6);
  }

  @Test
  public void nullChecks() {
    new NullPointerTester().testAllPublicStaticMethods(Case.class);
  }
}
