package com.google.mu.benchmarks.parsers.grappa;

import com.github.fge.grappa.run.ParseEventListener;
import com.github.fge.grappa.run.events.MatchFailureEvent;
import com.github.fge.grappa.run.events.PreParseEvent;

/** Top-level listener to avoid Java 11+ NestMembers bytecode attribute on parser classes. */
public class GrappaFailureListener extends ParseEventListener<Object> {
  private int maxFailIndex = 0;

  @Override
  public void beforeParse(PreParseEvent<Object> event) {
    maxFailIndex = 0;
  }

  @Override
  public void matchFailure(MatchFailureEvent<Object> event) {
    int idx = event.getContext().getCurrentIndex();
    if (idx > maxFailIndex) {
      maxFailIndex = idx;
    }
  }

  public int getMaxFailIndex() {
    return maxFailIndex;
  }
}
