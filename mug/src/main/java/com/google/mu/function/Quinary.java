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
package com.google.mu.function;

import java.util.List;

/** A 5-arg function of the signature of {@code (T, T, T, T, T) -> R}. */
public interface Quinary<T, R> {
  R apply(T a, T b, T c, T d, T e);


  /**
   * A function that accepts 5 or more elements.
   *
   * @since 5.8
   */
  @FunctionalInterface
  interface OrMore<T, R> {
    R apply(T a, T b, T c, T d, T e, List<? extends T> more);
  }
}