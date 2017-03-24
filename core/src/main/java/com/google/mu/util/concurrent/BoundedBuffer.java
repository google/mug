package com.google.mu.util.concurrent;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * A buffer that overwrites old elements when size reaches configured max value.
 * While we don't have Guava's EvictingQueue.
 */
final class BoundedBuffer<T> {
  private final List<T> buffer = new ArrayList<>();
  private final int capacity;
  private int total = 0;

  BoundedBuffer(int capacity) {
    this.capacity = capacity;
    if (capacity <= 0) throw new IllegalArgumentException();
  }

  void add(T element) {
    if (++total > capacity) {
      buffer.set(underlyingIndex(capacity - 1), element);
    } else {
      buffer.add(element);
    }
  }

  List<T> asList() {
    return new AbstractList<T>() {
      @Override public T get(int index) {
        return buffer.get(underlyingIndex(index));
      }

      @Override public int size() {
        return BoundedBuffer.this.size();
      }
    };
  }

  int size() {
    return Math.min(total, capacity);
  }

  private int underlyingIndex(int index) {
    return total <= capacity ? index : (total + index) % capacity;
  }
}