package com.google.mu.errorprone;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.mu.util.Substring;
import com.google.mu.util.stream.BiStream;
import static com.google.mu.util.stream.GuavaCollectors.toImmutableMap;

/** Abstract class providing convenience to BugChecker implementations. */
abstract class AbstractBugChecker extends BugChecker {
  private static final Substring.Pattern PLACEHOLDER = Substring.between('{', '}');
  private static final ImmutableMap<?, ?> MAP = BiStream.empty().collect(toImmutableMap());

}
