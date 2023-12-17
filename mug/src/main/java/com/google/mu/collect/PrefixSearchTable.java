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
package com.google.mu.collect;

import static com.google.mu.collect.InternalUtils.checkArgument;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.MoreStreams;

/**
 * A lookup table that stores prefix (a list of keys of type {@code K}) -> value mappings.
 *
 * <p>For example, if the table maps {@code [a, b]} prefix to a value "foo", when you search by
 * {@code [a, b, c]}, it will find the {@code [a, b] -> foo} mapping.
 *
 * <p>Conceptually it's a "Trie" except it searches by a list of key prefixes instead of string
 * prefixes.
 *
 * @since 7.1
 */
public final class PrefixSearchTable<K, V> {
  private final Map<K, Node<K, V>> nodes;

  private PrefixSearchTable(Map<K, Node<K, V>> nodes) {
    this.nodes = nodes;
  }

  /**
   * Searches the table for the longest prefix match of {@code compoundKey}.
   *
   * @return the value mapped to the longest prefix of {@code compoundKey} if present
   * @throws IllegalArgumentException if {@code compoundKey} is empty
   * @throws NullPointerException if {@code compoundKey} is null or any key element is null
   */
  public Optional<V> get(List<? extends K> compoundKey) {
    return search(compoundKey).values().reduce((shorter, longer) -> longer);
  }

  /**
   * Searches the table for prefixes of {@code compoundKey}. If there are multiple prefixes, all of
   * the matches will be returned, in ascending order of match length.
   *
   * <p>If no non-empty prefix exists in the table, an empty BiStream is returned.
   *
   * <p>To get the longest matched prefix, use {@link #get} instead.
   *
   * @return BiStream of the matched prefixes of {@code compoundKey} and the mapped values
   * @throws IllegalArgumentException if {@code compoundKey} is empty
   * @throws NullPointerException if {@code compoundKey} is null or any key element is null
   */
  public BiStream<List<K>, V> search(List<? extends K> compoundKey) {
    checkArgument(compoundKey.size() > 0, "cannot search by empty key");
    return BiStream.fromEntries(
        MoreStreams.whileNotNull(
            new Supplier<Map.Entry<List<K>, V>>() {
              private int index = 0;
              private Map<K, Node<K, V>> remaining = nodes;

              @Override
              public Map.Entry<List<K>, V> get() {
                while (remaining != null && index < compoundKey.size()) {
                  Node<K, V> node = remaining.get(requireNonNull(compoundKey.get(index)));
                  if (node == null) {
                    return null;
                  }
                  index++;
                  remaining = node.children;
                  if (node.value != null) {
                    return new AbstractMap.SimpleImmutableEntry<List<K>, V>(
                        unmodifiableList(compoundKey.subList(0, index)), node.value);
                  }
                }
                return null;
              }
            }));
  }

  /** Returns a new builder. */
  public static <K, V> Builder<K, V> builder() {
    return new Builder<>();
  }

  /** Builder of {@link PrefixSearchTable}. */
  public static final class Builder<K, V> {
    private final Map<K, Node.Builder<K, V>> nodes = new HashMap<>();

    /**
     * Adds the mapping from {@code compoundKey} to {@code value}.
     *
     * @return this builder
     * @throws IllegalArgument if {@code compoundKey} is empty, or it has already been mapped to a
     *     value that's not equal to {@code value}.
     * @throws NullPointerException if {@code compoundKey} is null, any of the key element is null
     *     or {@code value} is null
     */
    public Builder<K, V> add(List<? extends K> compoundKey, V value) {
      int size = compoundKey.size();
      requireNonNull(value);
      checkArgument(size > 0, "empty key not allowed");
      Node.Builder<K, V> node = nodes.computeIfAbsent(compoundKey.get(0), k -> new Node.Builder<>());
      for (int i = 1; i < size; i++) {
        node = node.child(compoundKey.get(i));
      }
      checkArgument(node.set(value), "conflicting key: %s", compoundKey);
      return this;
    }

    public PrefixSearchTable<K, V> build() {
      return new PrefixSearchTable<>(BiStream.from(nodes).mapValues(Node.Builder::build).toMap());
    }

    Builder() {}
  }

  private static class Node<K, V> {
    private final V value;
    private final Map<K, Node<K, V>> children;

    private Node(V value, Map<K, Node<K, V>> children) {
      this.value = value;
      this.children = children;
    }

    static class Builder<K, V> {
      private V value;
      private final Map<K, Builder<K, V>> children = new HashMap<>();

      Builder<K, V> child(K key) {
        requireNonNull(key);
        return children.computeIfAbsent(key, k -> new Builder<K, V>());
      }

      /**
       * Sets the value carried by this node to {@code value} if it's not already set. Return false
       * if the value has already been set to a different value.
       */
      boolean set(V value) {
        if (this.value == null) {
          this.value = value;
          return true;
        } else {
          return value.equals(this.value);
        }
      }

      Node<K, V> build() {
        return new Node<>(value, BiStream.from(children).mapValues(Builder::build).toMap());
      }
    }
  }
}
