package org.mu.util;

import java.util.stream.Stream;

/**
 * WARNING: you should never see a variable or parameter of this type; nor should you pass an
 * instance of it to any method that expects {@link Iterable}. The sole use case this class aims for
 * is to be able to fluently write a {@code for()} loop through a {@link Stream}.
 *
 * <p>The {@code Iterable} returned by {@link #through} is single-use-only!
 */
public interface Iterate<T> extends Iterable<T> {

  /**
   * Iterates through {@code stream}. For example:
   *
   * <pre>{@code
   *   for (Foo foo : Iterate.through(fooStream)) {
   *     ...
   *   }
   * }</pre>
   */
  static <T> Iterate<T> through(Stream<T> stream) {
    return stream::iterator;
  }
}
