package com.google.mu.util.graph;

import static java.util.stream.Collectors.joining;

import java.util.List;

/**
 * Thrown when running into any unexpected cycle during traversal.
 *
 * @since 4.3
 */
@SuppressWarnings("serial")
public final class CyclicGraphException extends IllegalArgumentException {
  private final List<?> cyclicPath;

  public CyclicGraphException(List<?> cyclicPath) {
    super("Cyclic path: " + cyclicPath.stream().map(Object::toString).collect(joining("->")));
    this.cyclicPath = cyclicPath;
  }

  /** Returns the cyclic path with the last node being the entry point of the cycle. */
  public final List<?> cyclicPath() {
    return cyclicPath;
  }
}