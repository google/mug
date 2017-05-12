package com.google.mu.util.stream;

import java.util.Collection;
import java.util.stream.Collector;

/**
 * Strategy of collectors to use for APIs that need to parameterize the type of collections to use
 * for internal element types. For example:
 * {@code Collectors::toList} and {@code ImmutableList::toImmutableList} are two different strategies.
 */
@FunctionalInterface
public interface CollectorStrategy {
  <T> Collector<T, ?, ? extends Collection<? extends T>> collector();
}
