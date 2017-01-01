package org.mu.util;

import java.time.Duration;

import org.mu.util.Retryer.Delay;

/**
 * Looks like Mockito is having trouble to spy Delay.of(), which returns an anonymous class
 * that happens to be final.
 */
class DelayForMock<E> extends Delay<E> {
  private final Duration duration;

  DelayForMock(Duration duration) {
    this.duration = duration;
  }

  @Override public Duration duration() {
    return duration;
  }
}