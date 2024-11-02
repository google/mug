package com.google.mu.safesql;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** A very simple stack with nothing but push-and-pop. */
abstract class Stack<T> {
  abstract T pop();
  abstract void push(T element);
  abstract boolean isEmpty();

  static <T> Stack<T> from(Iterator<? extends T> iterator) {
    requireNonNull(iterator);
    Deque<T> buffer = new ArrayDeque<>();
    return new Stack<T>() {
      @Override T pop() {
        T top = buffer.poll();
        return top == null ? iterator.next() : top;
      }

      @Override void push(T element) {
        requireNonNull(element);
        buffer.push(element);
      }

      @Override boolean isEmpty() {
        return buffer.isEmpty() && !iterator.hasNext();
      }
    };
  }
}
