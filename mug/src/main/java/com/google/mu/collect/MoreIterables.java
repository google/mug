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

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.BiPredicate;

/**
 * Some additional utilities pertaining to {@code Iterable}.
 *
 * @since 8.0
 */
public final class MoreIterables {
  /**
   * Returns true if {@code left} and {@code right} both contain the same number of elements, and
   * each pair of corresponding elements matches according to the {@code matcher} predicate. If both
   * iterables are empty, returns true.
   *
   * <p>Note that this method performs a similar function for matching as {@code
   * com.google.common.collect.Ordering.lexicographical} does for orderings.
   *
   * <p>It can also be used to compare two {@code Optional} objects, by first converting the {@code
   * Optional} into a {@code Set} using {@link com.google.mu.util.Optionals#asSet}. For
   * example:
   *
   * <pre>{@code
   * pairwise(asSet(resultProto1), asSet(resultProto2), differencer::compare)
   * }</pre>
   */
  public static <A, B> boolean pairwise(
      Iterable<A> left, Iterable<B> right, BiPredicate<? super A, ? super B> matcher) {
    requireNonNull(matcher);
    if (left instanceof Collection && right instanceof Collection) {
      int size = ((Collection<?>) left).size();
      if (((Collection<?>) right).size() != size) {
        return false;
      }
      if (isRandomAccessList(left) && isRandomAccessList(right)) {
        List<A> list1 = (List<A>) left;
        List<B> list2 = (List<B>) right;
        for (int i = 0; i < size; i++) {
          if (!matcher.test(list1.get(i), list2.get(i))) {
            return false;
          }
        }
        return true;
      }
    }
    Iterator<A> l = left.iterator();
    Iterator<B> r = right.iterator();
    while (l.hasNext() && r.hasNext()) {
      if (!matcher.test(l.next(), r.next())) {
        return false;
      }
    }
    return l.hasNext() == r.hasNext();
  }

  private static boolean isRandomAccessList(Iterable<?> iterable) {
    return iterable instanceof RandomAccess && iterable instanceof List;
  }

  private MoreIterables() {}
}
