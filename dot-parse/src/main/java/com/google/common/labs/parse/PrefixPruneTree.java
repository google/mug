package com.google.common.labs.parse;

import static java.lang.Math.min;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.util.stream.BiStream;

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
 * <p>Only ASCII characters are used for pruning. Non-ASCII characters and characters after a ASCII
 * character are ignored.
 */
@Immutable(containerOf = "V")
record PrefixPruneTree<V>(
    @SuppressWarnings("Immutable") List<V> survivors, Trie<V> children) {
  static final class Builder<V> {
    private final List<Ordered<V>> survivors = new ArrayList<>();
    private final Map<Integer, Builder<V>> children = new HashMap<>();
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

    /** Adds a candidate that requires no prefix matching. */
    private void addDefault(V candidate) {
      survivors.add(new Ordered<>(candidate, sequence.getAndIncrement()));
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
        if (c >= 128) { // out of range, stop.
          break;
        }
        node = node.children.computeIfAbsent(c, k -> new Builder<V>(sequence));
      }
      node.addDefault(candidate);
      return this;
    }

    PrefixPruneTree<V> build() {
      return buildWithHierarchy(List.of(), List.of());
    }

    private PrefixPruneTree<V> buildWithHierarchy(
        List<Ordered<V>> orderedAncestors, List<V> ancestorSurvivors) {
      List<Ordered<V>> ancestorsIncludingMe;
      List<V> effectiveSurvivors;
      if (survivors.isEmpty()) {
        ancestorsIncludingMe = orderedAncestors;
        effectiveSurvivors = ancestorSurvivors;
      } else {
        ancestorsIncludingMe =
            Stream.concat(orderedAncestors.stream(), survivors.stream())
                .sorted(comparingInt(Ordered::order))
                .collect(toUnmodifiableList());
        effectiveSurvivors =
            ancestorsIncludingMe.stream().map(Ordered::value).collect(toUnmodifiableList());
      }
      if (children.isEmpty()) {
        return new PrefixPruneTree<>(effectiveSurvivors, null);
      }
      return new PrefixPruneTree<>(
          effectiveSurvivors,
          Trie.from(
              BiStream.from(children)
                 .mapValues(
                     builder -> builder.buildWithHierarchy(ancestorsIncludingMe, effectiveSurvivors))
                 .toMap()));
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
    for (int i = index; !input.isEof(i); i++) {
      if (node.children == null) {
        break;
      }
      PrefixPruneTree<V> child = node.children.child(input.charAt(i));
      if (child == null) {
        break;
      }
      node = child;
    }
    return node.survivors;
  }

  @Immutable(containerOf = "V")
  private interface Trie<V> {
    PrefixPruneTree<V> child(char c);

    static <V> Trie<V> from(Map<Integer, PrefixPruneTree<V>> children) {
      return switch (children.size()) {
        case 1 -> singleChild(children);
        case 2 -> twoChildren(children);
        case 3 -> threeChildren(children);
        default -> forAscii(children);
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
        int c1,
        PrefixPruneTree<V> child1,
        int c2,
        PrefixPruneTree<V> child2,
        int c3,
        PrefixPruneTree<V> child3) {
      return x -> x == c1 ? child1 : x == c2 ? child2 : x == c3 ? child3 : null;
    }

    static <V> Trie<V> singleChild(Map<Integer, PrefixPruneTree<V>> map) {
      return of(map.keySet().iterator().next(), map.values().iterator().next());
    }

    static <V> Trie<V> twoChildren(Map<Integer, PrefixPruneTree<V>> map) {
      var keys = map.keySet().iterator();
      var values = map.values().iterator();
      return of(keys.next(), values.next(), keys.next(), values.next());
    }

    static <V> Trie<V> threeChildren(Map<Integer, PrefixPruneTree<V>> map) {
      var keys = map.keySet().iterator();
      var values = map.values().iterator();
      return of(keys.next(), values.next(), keys.next(), values.next(), keys.next(), values.next());
    }

    @SuppressWarnings({"rawtypes", "unchecked", "Immutable"})
    static <V> Trie<V> forAscii(Map<Integer, PrefixPruneTree<V>> map) {
      var children = new PrefixPruneTree[128];
      map.forEach((c, v) -> children[c] = v);
      return c -> c < 128 ? children[c] : null;
    }
  }

  private record Ordered<V>(V value, int order) {}
}
