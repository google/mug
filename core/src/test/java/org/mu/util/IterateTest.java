package org.mu.util;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;

public class IterateTest {
  @Test public void testNulls() {
    assertThrows(NullPointerException.class, () -> Iterate.through(null));
  }
}
