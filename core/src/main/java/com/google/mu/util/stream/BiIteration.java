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

import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.Map;

import com.google.mu.util.stream.Iteration.Continuation;

/**
 * Similar to {@link Iteration}, but is used to iteratively {@link #yeild yield()} pairs into a
 * lazy {@link BiStream}.
 */
public class BiIteration<L, R> {
  private final Iteration<Map.Entry<L, R>> iteration = new Iteration<>();

  /** Yields the pair of {@code left} and {@code right} to the result {@code BiStream}. */
  public final BiIteration<L, R> yield(L left, R right) {
    iteration.yield(
        new AbstractMap.SimpleImmutableEntry<>(requireNonNull(left), requireNonNull(right)));
    return this;
  }

  /**
   * Yields to the result {@code BiStream} a recursive iteration or lazy side-effect wrapped in
   * {@code continuation}.
   */
  public final BiIteration<L, R> yield(Continuation continuation) {
    iteration.yield(continuation);
    return this;
  }

  /** @deprecated Use {@link #start} instead. */
  @Deprecated
  public final BiStream<L, R> stream() {
    return start();
  }

  /**
   * Starts iteration over the {@link #yield yielded} pairs.
   *
   * <p>Because a {@code BiIteration} instance is stateful and mutable, {@code start()} can be
   * called at most once per instance.
   *
   * @throws IllegalStateException if {@code start()} has already been called.
   * @since 4.5
   */
  public final BiStream<L, R> start() {
    return BiStream.from(iteration.start());
  }
}
