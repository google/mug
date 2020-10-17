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

import java.util.function.BiConsumer;

/** Interface modeling a builder funcction that accepts two parameters. */
@FunctionalInterface
public interface BiAccumulator<C, L, R> {
  void accumulate(C container, L left, R right);


  /** Returns a {@link BiConsumer} that accumulates pairs into {@code container}. */
  default BiConsumer<L, R> into(C container) {
    return (l, r) -> accumulate(container, l, r);
  }
}