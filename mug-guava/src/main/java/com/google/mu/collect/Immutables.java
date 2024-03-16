package com.google.mu.collect;

import java.util.Collections;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableTable;
import com.google.mu.annotations.RequiresGuava;

/**
 * Convenient factory methods of common immutable Guava data types that are concise yet
 * unambiguous when static imported.
 *
 * <p>For example {@code list(1, 2)}, {@code map("key", "value")}, {@code multimap(k1, v1, k2, v2)} etc.
 *
 * <p>These APIs are best static imported universally throughout a code base such that seeing
 * {@code list(1, 2)}, {@code map(k, v)} immediately tells the readers what they are. For new code base,
 * collection literals like {@code list(1, 2)} are more concise than their longer equivalents like
 * {@code ImmutableList.of(1, 2)}. For existing code base, either fully migrate the longer literals
 * or continue using them. Mixing the two styles can cause confusion.
 *
 * <p>Unlike JDK collection literals such as {@code Map.of()}, collection literals returned by this class
 * don't lose the "Immutable" from the type. This allows you to use {@code ImmutableMap},
 * {@code ImmutableList} as return types of public API, helping to make the contract unambiguous.
 * If you use Guava, we recommend continuing to use the immutable collection types and the
 * immutable collection literals in this class over JDK collection literals.
 *
 * @since 6.0
 * @deprecated Please do not use it. It's only used internally.
 */
@Deprecated
@RequiresGuava
public final class Immutables {
  /**
   * Returns the empty immutable list. This list behaves and performs comparably to {@link
   * Collections#emptyList}, and is preferable mainly for consistency and maintainability set your
   * code.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <E> ImmutableList<E> list() {
    return ImmutableList.of();
  }

  /**
   * Returns an immutable list containing a single element. This list behaves and performs
   * comparably to {@link Collections#singletonList}, but will not accept a null element. It is
   * preferable mainly for consistency and maintainability list your code.
   *
   * @throws NullPointerException if {@code element} is null
   */
  public static <E> ImmutableList<E> list(E element) {
    return ImmutableList.of(element);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2) {
    return ImmutableList.of(e1, e2);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2, E e3) {
    return ImmutableList.of(e1, e2, e3);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2, E e3, E e4) {
    return ImmutableList.of(e1, e2, e3, e4);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2, E e3, E e4, E e5) {
    return ImmutableList.of(e1, e2, e3, e4, e5);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2, E e3, E e4, E e5, E e6) {
    return ImmutableList.of(e1, e2, e3, e4, e5, e6);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    return ImmutableList.of(e1, e2, e3, e4, e5, e6, e7);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    return ImmutableList.of(e1, e2, e3, e4, e5, e6, e7, e8);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
    return ImmutableList.of(e1, e2, e3, e4, e5, e6, e7, e8, e9);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(
      E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
    return ImmutableList.of(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10);
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> list(
      E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10, E e11) {
    return ImmutableList.of(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10, e11);
  }

  // These go up to eleven. After that, you just get the varargs form, and
  // whatever warnings might come along with it. :(

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * <p>The array {@code others} must not be longer than {@code Integer.MAX_VALUE - 12}.
   *
   * @throws NullPointerException if any element is null
   */
  @SafeVarargs
  public static <E> ImmutableList<E> list(
      E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10, E e11, E e12, E... others) {
    return ImmutableList.of(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10, e11, e12, others);
  }

  /**
   * Returns the empty immutable set. Preferred over {@link Collections#emptySet} for code
   * consistency, and because the return type conveys the immutability guarantee.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <E> ImmutableSet<E> set() {
    return ImmutableSet.of();
  }

  /**
   * Returns an immutable set containing {@code element}. Preferred over {@link
   * Collections#singleton} for code consistency, {@code null} rejection, and because the return
   * type conveys the immutability guarantee.
   */
  public static <E> ImmutableSet<E> set(E element) {
    return ImmutableSet.of(element);
  }

  /**
   * Returns an immutable set containing the given elements, minus duplicates, in the order each was
   * first specified. That is, if multiple elements are {@linkplain Object#equals equal}, all except
   * the first are ignored.
   */
  public static <E> ImmutableSet<E> set(E e1, E e2) {
    return ImmutableSet.of(e1, e2);
  }

  /**
   * Returns an immutable set containing the given elements, minus duplicates, in the order each was
   * first specified. That is, if multiple elements are {@linkplain Object#equals equal}, all except
   * the first are ignored.
   */
  public static <E> ImmutableSet<E> set(E e1, E e2, E e3) {
    return ImmutableSet.of(e1, e2, e3);
  }

  /**
   * Returns an immutable set containing the given elements, minus duplicates, in the order each was
   * first specified. That is, if multiple elements are {@linkplain Object#equals equal}, all except
   * the first are ignored.
   */
  public static <E> ImmutableSet<E> set(E e1, E e2, E e3, E e4) {
    return ImmutableSet.of(e1, e2, e3, e4);
  }

  /**
   * Returns an immutable set containing the given elements, minus duplicates, in the order each was
   * first specified. That is, if multiple elements are {@linkplain Object#equals equal}, all except
   * the first are ignored.
   */
  public static <E> ImmutableSet<E> set(E e1, E e2, E e3, E e4, E e5) {
    return ImmutableSet.of(e1, e2, e3, e4, e5);
  }

  /**
   * Returns an immutable set containing the given elements, minus duplicates, in the order each was
   * first specified. That is, if multiple elements are {@linkplain Object#equals equal}, all except
   * the first are ignored.
   *
   * <p>The array {@code others} must not be longer than {@code Integer.MAX_VALUE - 6}.
   */
  @SafeVarargs
  public static <E> ImmutableSet<E> set(E e1, E e2, E e3, E e4, E e5, E e6, E... others) {
    return ImmutableSet.of(e1, e2, e3, e4, e5, e6, others);
  }

  /**
   * Returns the empty map. This map behaves and performs comparably to {@link
   * Collections#emptyMap}, and is preferable mainly for consistency and maintainability of your
   * code.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <K, V> ImmutableMap<K, V> map() {
    return ImmutableMap.of();
  }

  /**
   * Returns an immutable map containing a single entry. This map behaves and performs comparably to
   * {@link Collections#singletonMap} but will not accept a null key or value. It is preferable
   * mainly for consistency and maintainability of your code.
   */
  public static <K, V> ImmutableMap<K, V> map(K k1, V v1) {
    return ImmutableMap.of(k1, v1);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(K k1, V v1, K k2, V v2) {
    return ImmutableMap.of(k1, v1, k2, v2);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3) {
    return ImmutableMap.of(k1, v1, k2, v2, k3, v3);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return ImmutableMap.of(k1, v1, k2, v2, k3, v3, k4, v4);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return ImmutableMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
    return ImmutableMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
    return ImmutableMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8) {
    return ImmutableMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8,
      K k9, V v9) {
    return ImmutableMap.of(
        k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys are provided
   */
  public static <K, V> ImmutableMap<K, V> map(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8,
      K k9, V v9,
      K k10, V v10) {
    return ImmutableMap.of(
        k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
  }

  /**
   * Returns the empty sorted map.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <K, V> ImmutableSortedMap<K, V> sortedMap() {
    return ImmutableSortedMap.of();
  }

  /** Returns an immutable map containing a single entry. */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(K k1, V v1) {
    return ImmutableSortedMap.of(k1, v1);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if the two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1, K k2, V v2) {
    return ImmutableSortedMap.of(k1, v1, k2, v2);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1, K k2, V v2, K k3, V v3) {
    return ImmutableSortedMap.of(k1, v1, k2, v2, k3, v3);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return ImmutableSortedMap.of(k1, v1, k2, v2, k3, v3, k4, v4);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return ImmutableSortedMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
    return ImmutableSortedMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
    return ImmutableSortedMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8) {
    return ImmutableSortedMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8,
      K k9, V v9) {
    return ImmutableSortedMap.of(
        k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
  }

  /**
   * Returns an immutable sorted map containing the given entries, sorted by the natural ordering of
   * their keys.
   *
   * @throws IllegalArgumentException if any two keys are equal according to their natural ordering
   */
  public static <K extends Comparable<? super K>, V> ImmutableSortedMap<K, V> sortedMap(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8,
      K k9, V v9,
      K k10, V v10) {
    return ImmutableSortedMap.of(
        k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
  }

  /**
   * Returns the empty multimap.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <K, V> ImmutableListMultimap<K, V> multimap() {
    return ImmutableListMultimap.of();
  }

  /** Returns an immutable multimap containing a single entry. */
  public static <K, V> ImmutableListMultimap<K, V> multimap(K k1, V v1) {
    return ImmutableListMultimap.of(k1, v1);
  }

  /** Returns an immutable multimap containing the given entries, in order. */
  public static <K, V> ImmutableListMultimap<K, V> multimap(K k1, V v1, K k2, V v2) {
    return ImmutableListMultimap.of(k1, v1, k2, v2);
  }

  /** Returns an immutable multimap containing the given entries, in order. */
  public static <K, V> ImmutableListMultimap<K, V> multimap(K k1, V v1, K k2, V v2, K k3, V v3) {
    return ImmutableListMultimap.of(k1, v1, k2, v2, k3, v3);
  }

  /** Returns an immutable multimap containing the given entries, in order. */
  public static <K, V> ImmutableListMultimap<K, V> multimap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return ImmutableListMultimap.of(k1, v1, k2, v2, k3, v3, k4, v4);
  }

  /** Returns an immutable multimap containing the given entries, in order. */
  public static <K, V> ImmutableListMultimap<K, V> multimap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return ImmutableListMultimap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5,v5);
  }

  /**
   * Returns the empty immutable multiset.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <E> ImmutableMultiset<E> multiset() {
    return ImmutableMultiset.of();
  }

  /**
   * Returns an immutable multiset containing a single element.
   *
   * @throws NullPointerException if {@code element} is null
   */
  public static <E> ImmutableMultiset<E> multiset(E element) {
    return ImmutableMultiset.of(element);
  }

  /**
   * Returns an immutable multiset containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableMultiset<E> multiset(E e1, E e2) {
    return ImmutableMultiset.of(e1, e2);
  }

  /**
   * Returns an immutable multiset containing the given elements, in the "grouped iteration order"
   * described in the class documentation.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableMultiset<E> multiset(E e1, E e2, E e3) {
    return ImmutableMultiset.of(e1, e2, e3);
  }

  /**
   * Returns an immutable multiset containing the given elements, in the "grouped iteration order"
   * described in the class documentation.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableMultiset<E> multiset(E e1, E e2, E e3, E e4) {
    return ImmutableMultiset.of(e1, e2, e3, e4);
  }

  /**
   * Returns an immutable multiset containing the given elements, in the "grouped iteration order"
   * described in the class documentation.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableMultiset<E> multiset(E e1, E e2, E e3, E e4, E e5) {
    return ImmutableMultiset.of(e1, e2, e3, e4, e5);
  }

  /**
   * Returns an immutable multiset containing the given elements, in the "grouped iteration order"
   * described in the class documentation.
   *
   * @throws NullPointerException if any element is null
   */
  @SafeVarargs
  public static <E> ImmutableMultiset<E> multiset(E e1, E e2, E e3, E e4, E e5, E e6, E... others) {
    return ImmutableMultiset.of(e1, e2, e3, e4, e5, e6, others);
  }

  /**
   * Returns the empty bimap.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <K, V> ImmutableBiMap<K, V> biMap() {
    return ImmutableBiMap.of();
  }

  /** Returns an immutable bimap containing a single entry. */
  public static <K, V> ImmutableBiMap<K, V> biMap(K k1, V v1) {
    return ImmutableBiMap.of(k1, v1);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(K k1, V v1, K k2, V v2) {
    return ImmutableBiMap.of(k1, v1, k2, v2);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(K k1, V v1, K k2, V v2, K k3, V v3) {
    return ImmutableBiMap.of(k1, v1, k2, v2, k3, v3);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
    return ImmutableBiMap.of(k1, v1, k2, v2, k3, v3, k4, v4);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
    return ImmutableBiMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
    return ImmutableBiMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(
      K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
    return ImmutableBiMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8) {
    return ImmutableBiMap.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8);
  }

  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8,
      K k9, V v9) {
    return ImmutableBiMap.of(
        k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9);
  }
  /**
   * Returns an immutable map containing the given entries, in order.
   *
   * @throws IllegalArgumentException if duplicate keys or values are added
   */
  public static <K, V> ImmutableBiMap<K, V> biMap(
      K k1, V v1,
      K k2, V v2,
      K k3, V v3,
      K k4, V v4,
      K k5, V v5,
      K k6, V v6,
      K k7, V v7,
      K k8, V v8,
      K k9, V v9,
      K k10, V v10) {
    return ImmutableBiMap.of(
        k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9, k10, v10);
  }

  /**
   * Returns an empty immutable table.
   *
   * <p><b>Performance note:</b> the instance returned is a singleton.
   */
  public static <R, C, V> ImmutableTable<R, C, V> table() {
    return ImmutableTable.of();
  }

  /** Returns an immutable table containing a single cell. */
  public static <R, C, V> ImmutableTable<R, C, V> table(R r, C c, V v) {
    return ImmutableTable.of(r, c, v);
  }

  /** Returns an immutable table containing two cells. */
  public static <R, C, V> ImmutableTable<R, C, V> table(R r1, C c1, V v1, R r2, C c2, V v2) {
    return ImmutableTable.<R, C, V>builder().put(r1, c1, v1).put(r2, c2, v2).build();
  }

  private Immutables() {}
}
