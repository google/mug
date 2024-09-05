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
package com.google.mu.util.concurrent;

import static java.util.Objects.requireNonNull;

import java.util.AbstractList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Some relatively trivial re-invented wheels as cost of 0-dependency. */
final class Utils {

  static void checkState(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalStateException(String.format(message, args));
    }
  }

  static <T> Stream<T> stream(Iterable<T> iterable) {
    return StreamSupport.stream(iterable.spliterator(), false);
  }

  /** Only need it because we don't have Guava Lists.transform(). */
  static <F, T> List<T> mapList(List<F> list, Function<? super F, ? extends T> mapper) {
    requireNonNull(list);
    requireNonNull(mapper);
    return new AbstractList<T>() {
      @Override public int size() {
        return list.size();
      }
      @Override public T get(int index) {
        return mapper.apply(list.get(index));
      }
    };
  }

  static <T> Predicate<Object> typed(Class<T> type, Predicate<? super T> condition) {
    requireNonNull(type);
    requireNonNull(condition);
    return x -> type.isInstance(x) && condition.test(type.cast(x));
  }

  /**
   * Casts {@code object} to {@code type} if it's a non-null instance of {@code T}, or else
   * returns {@code Optional.empty()}.
   */
  static <T> Optional<T> cast(Object object, Class<T> type) {
    return type.isInstance(object) ? Optional.of(type.cast(object)) : Optional.empty();
  }

  static CompletionStage<?> ifCancelled(
      CompletionStage<?> stage, Consumer<? super CancellationException> action) {
    requireNonNull(action);
    return stage.exceptionally(e -> {
      cast(e, CancellationException.class).ifPresent(action);
      return null;
    });
  }

  /** Propagates cancellation from {@code outer} to {@code inner}. */
  static <T> CompletionStage<T> propagateCancellation(
      CompletionStage<T> outer, CompletionStage<?> inner) {
    requireNonNull(inner);
    ifCancelled(outer, e -> {
      // Even if this isn't supported, the worst is that we don't propagate cancellation.
      // But that's fine because without a Future we cannot propagate anyway.
      inner.toCompletableFuture().completeExceptionally(e);
    });
    return outer;
  }
}
