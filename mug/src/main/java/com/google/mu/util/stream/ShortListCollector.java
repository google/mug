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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Utility class to perform n-ary functional pattern matching on a list or a stream of input elements.
 *
 * @since 5.3
 */
abstract class ShortListCollector<T, R> implements Collector<T, List<T>, R> {
  abstract int arity();
  abstract R map(List<? extends T> list);

  @Override public final BiConsumer<List<T>, T> accumulator() {
    return List::add;
  }

  @Override public final BinaryOperator<List<T>> combiner() {
    return (l1, l2) -> {
      return l1;
    };
  }

  @Override public final Function<List<T>, R> finisher() {
    return l -> {
      if (l.size() == arity()) {
        return map(l);
      }
      throw new IllegalArgumentException(
          "Not true that input " + showShortList(l) + " has " + this + ".");
    };
  }

  @Override public final Set<Characteristics> characteristics() {
    return Collections.emptySet();
  }

  @Override public final Supplier<List<T>> supplier() {
    return ArrayList::new;
  }

  @Override public String toString() {
    return arity() + " elements";
  }

  private String showShortList(List<?> list) {
    return list.size() <= arity() + 1  // If small enough, just show it.
        ? "(" + list + ")"
        : "of size = " + list.size() + " (["
            + list.stream().limit(arity() + 1).map(Object::toString).collect(Collectors.joining(", "))
            + ", ...])";
  }
}
