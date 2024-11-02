package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyIterator;
import static org.junit.Assert.assertThrows;

import java.util.NoSuchElementException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StackTest {
  @Test public void from_emptyIterator() {
    Stack<Integer> stack = Stack.from(emptyIterator());
    assertThat(stack.isEmpty()).isTrue();
    assertThrows(NoSuchElementException.class, stack::pop);
  }

  @Test public void from_nonEmptyIterator() {
    Stack<Integer> stack = Stack.from(asList(1, 2).iterator());
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(1);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(2);
    assertThat(stack.isEmpty()).isTrue();
    assertThrows(NoSuchElementException.class, stack::pop);
  }

  @Test public void from_oneElementPushed() {
    Stack<Integer> stack = Stack.from(asList(1).iterator());
    stack.push(2);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(2);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(1);
    assertThat(stack.isEmpty()).isTrue();
    assertThrows(NoSuchElementException.class, stack::pop);
  }

  @Test public void from_twoElementsPushed() {
    Stack<Integer> stack = Stack.from(asList(1).iterator());
    stack.push(2);
    stack.push(3);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(3);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(2);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(1);
    assertThat(stack.isEmpty()).isTrue();
    assertThrows(NoSuchElementException.class, stack::pop);
  }

  @Test public void from_popThenPush() {
    Stack<Integer> stack = Stack.from(asList(1).iterator());
    stack.push(2);
    assertThat(stack.pop()).isEqualTo(2);
    stack.push(4);
    assertThat(stack.pop()).isEqualTo(4);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(1);
    assertThat(stack.isEmpty()).isTrue();
    assertThrows(NoSuchElementException.class, stack::pop);
  }

  @Test public void from_drainThenPush() {
    Stack<Integer> stack = Stack.from(asList(1).iterator());
    assertThat(stack.pop()).isEqualTo(1);
    stack.push(2);
    stack.push(3);
    assertThat(stack.pop()).isEqualTo(3);
    assertThat(stack.isEmpty()).isFalse();
    assertThat(stack.pop()).isEqualTo(2);
    assertThat(stack.isEmpty()).isTrue();
    assertThrows(NoSuchElementException.class, stack::pop);
  }
}
