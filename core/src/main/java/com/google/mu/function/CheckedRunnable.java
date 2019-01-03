package com.google.mu.function;

/*
 * A runnable that can throw checked exceptions.
 *
 * @since 1.14
 */
@FunctionalInterface
public interface CheckedRunnable<E extends Throwable> {
  void run() throws E;
}
