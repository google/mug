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
package com.google.mu.collect;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
  static <T> Collector<T, ?, List<T>> toImmutableList() {
    return checkingNulls(
        collectingAndThen(toCollection(ArrayList::new), Collections::unmodifiableList));
  }

  static <T> Collector<T, ?, Set<T>> toImmutableSet() {
    return checkingNulls(
        collectingAndThen(Collectors.toCollection(LinkedHashSet::new), Collections::unmodifiableSet));
  }

  static <T, A, R> Collector<T, ?, R> checkingNulls(Collector<T, A, R> downstream) {
    return Collectors.mapping(Objects::requireNonNull, downstream);
  }

}
