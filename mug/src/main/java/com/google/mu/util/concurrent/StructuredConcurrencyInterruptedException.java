package com.google.mu.util.concurrent;

/**
 * Wraps a {@link InterruptedException}.
 *
 * When this exception is constructed to wrap an InterruptedException, the current thread is re-interrupted
 * to maintain the interruption state.
 */
public final class StructuredConcurrencyInterruptedException extends RuntimeException {
  public StructuredConcurrencyInterruptedException(InterruptedException e) {
    super(e);
    Thread.currentThread().interrupt();
  }
}
