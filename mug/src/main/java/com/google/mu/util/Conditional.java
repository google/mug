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

import static java.util.Objects.requireNonNull;

import com.google.mu.function.CheckedRunnable;
import com.google.mu.function.CheckedSupplier;

/**
 * Provides instances of {@link Premise}.
 * 
 * @since 1.14
 */
enum Conditional implements Premise {
  TRUE {
    @Override public <E extends Throwable> void orElse(CheckedRunnable<E> block) {
      requireNonNull(block);
    }
    @Override public <E extends Throwable> Premise or(CheckedSupplier<? extends Premise, E> alternative) {
      requireNonNull(alternative);
      return this;
    }
  },
  FALSE {
    @Override public <E extends Throwable> void orElse(CheckedRunnable<E> block) throws E {
      block.run();
    }
    @Override public <E extends Throwable> Premise or(CheckedSupplier<? extends Premise, E> alternative)
        throws E {
      return alternative.get();
    }
  }
}
