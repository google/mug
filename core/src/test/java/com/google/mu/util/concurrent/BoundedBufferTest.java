package com.google.mu.util.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;

public class BoundedBufferTest {

  @Test public void zeroBufferCapacityDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> new BoundedBuffer<>(0));
  }

  @Test public void negativeBufferCapacityDisallowed() {
    assertThrows(IllegalArgumentException.class, () -> new BoundedBuffer<>(-1));
  }

  @Test public void emptyBuffer() {
    BoundedBuffer<String> buffer = new BoundedBuffer<>(1);
    assertThat(buffer.asList()).isEmpty();
    assertThat(buffer.asList()).containsExactly();
  }

  @Test public void fullBuffer() {
    BoundedBuffer<String> buffer = new BoundedBuffer<>(1);
    buffer.add("hello");
    assertThat(buffer.asList()).containsExactly("hello");
    buffer.add("world");
    assertThat(buffer.asList()).containsExactly("world");
  }

  @Test public void bufferNotFull() {
    BoundedBuffer<String> buffer = new BoundedBuffer<>(2);
    buffer.add("hello");
    assertThat(buffer.asList()).containsExactly("hello");
  }

  @Test public void bufferOverflow() {
    BoundedBuffer<String> buffer = new BoundedBuffer<>(2);
    buffer.add("one");
    buffer.add("two");
    buffer.add("three");
    assertThat(buffer.asList()).containsExactly("two", "three");
    buffer.add("four");
    assertThat(buffer.asList()).containsExactly("three", "four");
    buffer.add("five");
    assertThat(buffer.asList()).containsExactly("four", "five");
  }

  @Test public void asListWithInvalidIndex() {
    BoundedBuffer<String> buffer = new BoundedBuffer<>(2);
    buffer.add("hello");
    assertThrows(IndexOutOfBoundsException.class, () -> buffer.asList().get(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> buffer.asList().get(1));
    assertThrows(IndexOutOfBoundsException.class, () -> buffer.asList().get(2));
  }
}
