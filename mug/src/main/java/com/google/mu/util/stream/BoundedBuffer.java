/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.util.stream;

import static java.util.stream.Collectors.joining;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/** A buffer with bounded max size. */
final class BoundedBuffer<T> extends AbstractList<T> {
  private boolean overflown = false;
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

  @Override public boolean add(T e) {
    if (isFull()) {
      overflown = true;
      return false;
    }
    return underlying.add(e);
  }

  @Override public String toString() {
    return overflown
        ? "[" + underlying.stream().map(e -> e + ", ").collect(joining("")) + "...]"
            : underlying.toString();
  }
}
