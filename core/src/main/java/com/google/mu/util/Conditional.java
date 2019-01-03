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
