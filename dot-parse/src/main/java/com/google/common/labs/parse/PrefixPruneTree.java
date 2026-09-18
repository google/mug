/*****************************************************************************
 * Copyright (C) google.com                                                  *
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
package com.google.common.labs.parse;

import static com.google.mu.collect.MoreCollections.filter;
import static java.lang.Math.min;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.reverseOrder;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.errorprone.annotations.concurrent.LazyInit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A prune tree is used to match the character inputs against known prefixes. Candidates mapped with
 * a prefix will be pruned out if the input doesn't start with the required prefix (from the given
 * index).
 *
 * <p>For example:
 *
 * <pre>{@code
 * PrefixPruneTree<Parser<String>> tree = new PrefixPruneTree.Builder<>()
 *     .addPrefix("", chars(4))
 *     .addPrefix("a", word("a"))
 *     .addPrefix("a", string("a"))
 *     .addPrefix("an", word("an"))
 *     .addPrefix("the",word("the"))
 *     .build();
 *
 * List<Parser<String>> a = tree.pruneByPrefix(CharInput.of("a girl"), 0);
 *     // [chars(4), word("a"), string("a")]
 * List<Parser<String>> an = tree.pruneByPrefix(CharInput.of("an apple"), 0);
 *      // [chars(4), word("a"), string("a"), word("an")]
 * List<Parser<String>> the = tree.pruneByPrefix(CharInput.of("the owl"), 0);
 *      // [chars(4), word("the")]
 * }</pre>
 *
 * <p>Note that false negatives (pruning a parser that shouldn't be pruned) can't happen, but false
 * positive is possible: a parser may still be returned even if strictly its prefix doesn't match
 * the input. If for example you have only one prefix, pruning by pre-scanning the input doesn't
 * really pay off so it's better to be "loose" and not scan any input at all.
 *
 * <p>Only ASCII characters are used for pruning. Non-ASCII characters and characters after a ASCII
 * character are ignored.
 */
@Immutable(containerOf = "V")
record PrefixPruneTree<V>(@SuppressWarnings("Immutable") List<V> survivors, Trie<V> children) {
  static final class Builder<V> {
    private final List<Ordered<V>> survivors = new ArrayList<>(); // in encounter order
    // lower-case -> upper-case -> digits.
    // For the comparison (x == c1 ? child1 : x == c2 ? child2 : null), we want
    // c1 to occur more frequently than c2 for more effective short-circuiting.
    private final SortedMap<Integer, Builder<V>> children = new TreeMap<>(reverseOrder());
    private final Set<V> blocked = new HashSet<>();
    private final AtomicInteger sequence;

    Builder() {
      this(new AtomicInteger());
    }

    private Builder(AtomicInteger sequence) {
      this.sequence = sequence;
    }

    int numSurvivors() {
      return survivors.size();
    }

    /**
     * Adds a candidate that requires the input to start with {@code prefix}, but only up to {@code
     * maxChars} characters are used for pruning.
     */
    @CanIgnoreReturnValue
    Builder<V> addPrefix(String prefix, int maxChars, V candidate) {
      Builder<V> node = this;
      int length = min(prefix.length(), maxChars);
      for (int i = 0; i < length; i++) {
        int c = prefix.charAt(i);
        if (c >= 128) break; // out of range, stop.
        node = node.child(c);
      }
      node.addDefault(candidate);
      return this;
    }

    /** Adds a candidate that requires no prefix matching. */
    private void addDefault(V candidate) {
      survivors.add(new Ordered<>(candidate, sequence.getAndIncrement()));
    }

    /**
     * Registers that if the next char in the input is in {@code blocklist} (and is the first char
     * of at least one top-level prefix), the {@code candidate} can be safely pruned.
     */
    void addBlocklist(BitSet blocklist, V candidate) {
      children.forEach((k, v) -> {
        if (blocklist.get(k)) {
          v.block(candidate);
        }
      });
    }

    private void block(V candidate) {
      blocked.add(candidate);
    }

    private Builder<V> child(int c) {
      return children.computeIfAbsent(c, k -> new Builder<V>(sequence));
    }

    PrefixPruneTree<V> build() {
      return buildWithInheritance(Survivors.none());
    }

    private PrefixPruneTree<V> buildWithInheritance(Survivors<V> inherited) {
      Survivors<V> effective = inherited.excluding(blocked).concat(survivors);
      if (children.isEmpty()) {
        return new PrefixPruneTree<>(effective.unwrap(), null);
      }

      int[] chars = new int[children.size()];
      @SuppressWarnings({"rawtypes", "unchecked"}) // generic array of built subtrees
      PrefixPruneTree<V>[] subtrees = new PrefixPruneTree[chars.length];
      int i = 0;
      for (Map.Entry<Integer, Builder<V>> child : children.entrySet()) {
        chars[i] = child.getKey();
        subtrees[i] = child.getValue().buildWithInheritance(effective);
        i++;
      }
      if (subtrees.length == 1 && survivors.isEmpty()) { // collapse lone leaf child
        PrefixPruneTree<V> loneChild = subtrees[0];
        if (loneChild.isLeaf()) return loneChild;
      }
      return new PrefixPruneTree<>(effective.unwrap(), Trie.from(chars, subtrees));
    }
  }

  /**
   * Prunes the candidate values and returns the survivors after pruning according to the character
   * input from the given {@code index}.
   *
   * <p>Values are returned strictly in the order they were added.
   *
   * <p>This will run in a hot loop, so performance is critical.
   */
  List<V> pruneByPrefix(CharInput input, int index) {
    PrefixPruneTree<V> node = this;
    for (int i = index; ; i++) {
      Trie<V> children = node.children;
      if (children == null) break;
      int c = input.charAtOrEof(i);
      if (c < 0) break;
      PrefixPruneTree<V> child = children.child((char) c);
      if (child == null) break;
      node = child;
    }
    return node.survivors;
  }

  private boolean isLeaf() {
    return children == null;
  }

  @Immutable(containerOf = "V")
  private interface Trie<V> {
    PrefixPruneTree<V> child(char c);

    /**
     * {@code chars} and {@code children} are parallel arrays, ordered as the Builder sorted them.
     */
    static <V> Trie<V> from(int[] chars, PrefixPruneTree<V>[] children) {
      return switch (chars.length) {
        case 1 -> of(chars[0], children[0]);
        case 2 -> of(chars[0], children[0], chars[1], children[1]);
        case 3 -> of(chars[0], children[0], chars[1], children[1], chars[2], children[2]);
        default -> forAscii(chars, children);
      };
    }

    @SuppressWarnings("Immutable")
    static <V> Trie<V> of(int c, PrefixPruneTree<V> child) {
      return x -> x == c ? child : null;
    }

    @SuppressWarnings("Immutable")
    static <V> Trie<V> of(int c1, PrefixPruneTree<V> child1, int c2, PrefixPruneTree<V> child2) {
      return x -> x == c1 ? child1 : x == c2 ? child2 : null;
    }

    @SuppressWarnings("Immutable")
    static <V> Trie<V> of(
        int c1, PrefixPruneTree<V> child1, int c2, PrefixPruneTree<V> child2, int c3,
        PrefixPruneTree<V> child3) {
      return x -> x == c1 ? child1 : x == c2 ? child2 : x == c3 ? child3 : null;
    }

    @SuppressWarnings({"rawtypes", "unchecked", "Immutable"})
    static <V> Trie<V> forAscii(int[] chars, PrefixPruneTree<V>[] children) {
      var table = new PrefixPruneTree[128];
      for (int i = 0; i < chars.length; i++) {
        table[chars[i]] = children[i];
      }
      return c -> c < 128 ? table[c] : null;
    }
  }

  private static final class Survivors<V> {
    private final List<Ordered<V>> ordered;
    // Unwrapped once per Survivors and shared by every node that inherits it.
    @LazyInit private volatile List<V> unwrapped;

    static <V> Survivors<V> none() {
      return new Survivors<>(List.of());
    }

    Survivors(List<Ordered<V>> ordered) {
      this.ordered = ordered;
    }

    List<V> unwrap() {
      if (isEmpty()) return List.of();
      List<V> result = unwrapped;
      if (result == null) {
        result = new ArrayList<>(ordered.size());
        for (Ordered<V> element : ordered) {
          result.add(element.value);
        }
        unwrapped = result = List.copyOf(result);
      }
      return result;
    }

    Survivors<V> concat(List<Ordered<V>> that) {
      if (that.isEmpty()) return this;
      if (this.isEmpty()) return new Survivors<>(that);
      return new Survivors<V>(
          Stream.concat(ordered.stream(), that.stream())
              .sorted(comparingInt(Ordered::order))
              .toList());
    }

    Survivors<V> excluding(Set<? super V> blocked) {
      if (blocked.isEmpty() || ordered.isEmpty()) return this;
      return new Survivors<>(filter(ordered, v -> !blocked.contains(v.value)));
    }

    boolean isEmpty() {
      return ordered.isEmpty();
    }
  }

  private record Ordered<V>(V value, int order) {}
}
