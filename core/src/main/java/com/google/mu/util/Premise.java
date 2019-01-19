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
package com.google.mu.util;

import com.google.mu.function.CheckedRunnable;
import com.google.mu.function.CheckedSupplier;

/**
 * Result of a previously evaluated condition. Used to fluently chain API calls like
 * {@code Optionals.ifPresent()}. For example: <pre>
 *   ifPresent(getStory(), Story::tell)
 *       .or(() -> ifPresent(getJoke(), Joke::tell))
 *       .orElse(() -> print("can't tell"));
 * </pre>
 *
 * @since 1.14
 */
public interface Premise {
  /** Evaluates {@code alternative} if {@code this} premise doesn't hold. */
  <E extends Throwable> Premise or(CheckedSupplier<? extends Premise, E> alternative) throws E;

  /** Runs {@code block} if {@code this} premise doesn't hold. */
  <E extends Throwable> void orElse(CheckedRunnable<E> block) throws E;
}
