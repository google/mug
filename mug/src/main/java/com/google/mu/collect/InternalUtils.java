package com.google.mu.collect;

import static java.util.stream.Collectors.collectingAndThen;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

final class InternalUtils {
  static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  static <T> Collector<T, ?, Set<T>> toImmutableSet() {
    return checkingNulls(
        collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
  }

  static <T, A, R> Collector<T, ?, R> checkingNulls(Collector<T, A, R> downstream) {
    return Collectors.mapping(Objects::requireNonNull, downstream);
  }

}
