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
import java.util.stream.Collectors;

/**
 * Utility class to perform n-ary functional pattern matching on a list or a stream of input elements.
 *
 * @since 5.3
 */
abstract class ShortListCollector<T, R> extends FixedSizeCollector<T, List<T>, R> {
  @Override public final BiConsumer<List<T>, T> accumulator() {
    return List::add;
  }

  @Override public final BinaryOperator<List<T>> combiner() {
    return (l1, l2) -> {
      l1.addAll(l2);
      return l1;
    };
  }

  @Override public final Function<List<T>, R> finisher() {
    return l -> {
      if (appliesTo(l)) return reduce(l);
      throw new IllegalArgumentException(
          "Not true that input " + showShortList(l, arity() + 1) + " has " + this + ".");
    };
  }

  @Override public final Set<Characteristics> characteristics() {
    return Collections.emptySet();
  }

  @Override public final Supplier<List<T>> supplier() {
    return () -> new ArrayList<T>(arity());
  }

  @Override public String toString() {
    return arity() + " elements";
  }

  static String showShortList(List<?> list, int elementsToShow) {
    return list.size() <= elementsToShow  // If small enough, just show it.
        ? "<" + list + ">"
        : "of size = " + list.size() + " <["
            + list.stream().limit(elementsToShow).map(Object::toString).collect(Collectors.joining(", "))
            + ", ...]>";
  }
}
