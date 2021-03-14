package com.google.mu.util.stream;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BoundedBufferTest {
  @Test public void negativeMaxSize() {
    assertThrows(IllegalArgumentException.class, () -> new BoundedBuffer<>(-1));
  }

  @Test public void retainingZero() {
    List<Integer> buffer = new BoundedBuffer<>(0);
    assertThat(buffer).isEmpty();
    assertThat(buffer.toString()).isEqualTo("[]");
    assertThat(buffer.add(1)).isFalse();
    assertThat(buffer).isEmpty();
    assertThat(buffer.toString()).isEqualTo("[...]");
  }

  @Test public void retaining_empty() {
    List<Integer> buffer = new BoundedBuffer<>(1);
    assertThat(buffer).isEmpty();
  }

  @Test public void retainingOne() {
    List<Integer> buffer = new BoundedBuffer<>(1);
    assertThat(buffer).isEmpty();
    assertThat(buffer.toString()).isEqualTo("[]");
    assertThat(buffer.add(1)).isTrue();
    assertThat(buffer).containsExactly(1);
    assertThat(buffer.toString()).isEqualTo("[1]");
    assertThat(buffer.add(2)).isFalse();
    assertThat(buffer).containsExactly(1);
    assertThat(buffer.toString()).isEqualTo("[1, ...]");
  }

  @Test public void retainingTwo() {
    List<Integer> buffer = new BoundedBuffer<>(2);
    assertThat(buffer).isEmpty();
    assertThat(buffer.toString()).isEqualTo("[]");
    assertThat(buffer.add(1)).isTrue();
    assertThat(buffer).containsExactly(1);
    assertThat(buffer.toString()).isEqualTo("[1]");
    assertThat(buffer.add(2)).isTrue();
    assertThat(buffer).containsExactly(1, 2);
    assertThat(buffer.toString()).isEqualTo("[1, 2]");
    assertThat(buffer.add(3)).isFalse();
    assertThat(buffer).containsExactly(1, 2);
    assertThat(buffer.toString()).isEqualTo("[1, 2, ...]");
  }
}
