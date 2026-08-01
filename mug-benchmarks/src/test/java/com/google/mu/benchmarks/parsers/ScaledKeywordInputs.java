package com.google.mu.benchmarks.parsers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ScaledKeywordInputs {
  public static final int NUM_KEYWORDS = 500;
  public static final List<String> KEYWORDS =
      IntStream.range(0, NUM_KEYWORDS)
          .mapToObj(i -> String.format("kw%04d", i))
          .collect(Collectors.toUnmodifiableList());

  public static final String KEYWORDS_LIST_CS =
      IntStream.range(0, NUM_KEYWORDS * 2)
          .mapToObj(i -> KEYWORDS.get((i * 17) % NUM_KEYWORDS))
          .collect(Collectors.joining(","));

  public static final String KEYWORDS_LIST_INVALID = KEYWORDS_LIST_CS + ",kw9999invalid";

  private ScaledKeywordInputs() {}
}
