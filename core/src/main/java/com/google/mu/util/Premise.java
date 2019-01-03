package com.google.mu.util;

import com.google.mu.function.CheckedRunnable;
import com.google.mu.function.CheckedSupplier;

/**
 * Result of a previously evaluated condition. Used to fluently chain API callss like
 * {@code Optionals.ifElse()}. For example: <pre>
 *   ifPresent(getStory(), Story::tell)
 *       .or(() -> ifPresent(getJoke(), Joke::tell))
 *       .orElse(() -> print("no story"));
 * </pre>
 *
 * @since 1.14
 */
public interface Premise {
  /** Runs {@code block} if {@code this} premise doesn't hold. */
  <E extends Throwable> void orElse(CheckedRunnable<E> block) throws E;

  /** Evaluates {@code alternative} if {@code this} premise doesn't hold. */
  <E extends Throwable> Premise or(CheckedSupplier<? extends Premise, E> alternative) throws E;
}
