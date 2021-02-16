package com.google.mu.util.patternmatch;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/** A buffer with bounded max size. */
abstract class BoundedBuffer<T> extends AbstractList<T> {
  private final int maxSize;
  final List<T> underlying;

  BoundedBuffer(int maxSize) {
    if (maxSize < 0) throw new IllegalArgumentException("maxSize (" + maxSize + ") < 0");
    this.maxSize = maxSize;
    this.underlying = new ArrayList<>(maxSize);
  }

  @Override public T get(int index) {
    return underlying.get(index);
  }

  @Override public int size() {
    return underlying.size();
  }

  final boolean isFull() {
    return size() >= maxSize;
  }

  static <T> BoundedBuffer<T> retaining(int maxSize) {
    return new BoundedBuffer<T>(maxSize) {
      @Override public boolean add(T e) {
        return !isFull() && underlying.add(e);
      }
    };
  }

  static <T> BoundedBuffer<T> retainingLastElementOnly() {
    return new BoundedBuffer<T>(1) {
      @Override public boolean add(T e) {
        if (isFull()) {
          underlying.set(0, e);
          return true;
        } else {
          return underlying.add(e);
        }
      }
    };
  }
}
