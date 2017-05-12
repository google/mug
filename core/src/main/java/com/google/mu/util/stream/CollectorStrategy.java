package com.google.mu.util.stream;

import java.util.Collection;
import java.util.stream.Collector;

/**
 * APIs that need to parameterize the type of collections to use for internal element types
 * can accept a {@code CollectorStrategy} as parameter. For example:
 * {@code theApi(Collectors::toList)} or {@code theApi(ImmutableList::toImmutableList)}.
 */
@FunctionalInterface
public interface CollectorStrategy {
  <T> Collector<T, ?, ? extends Collection<? extends T>> collector();
}
