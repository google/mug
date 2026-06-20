package com.google.mu.benchmarks.parsers;

import java.util.List;

public final class BenchmarkInputs {
  public static final String IP = "192.168.1.1";
  public static final String STRING_SIMPLE = "\"hello world!\"";
  public static final String STRING_ESCAPED = "\"hello \\\"world\\\"!\"";

  public static final List<String> KEYWORDS = List.of(
      "select", "insert", "update", "delete", "create", "drop",
      "alter",  "where",  "group",  "order",  "having", "limit"
  );

  public static final String CALCULATOR =
      " ( 1000+2 * 3000 - 4000 / (500+600) ) * -700 - 8000 / 9000";
  public static final int CALCULATOR_EXPECTED = -4897900;

  public static final String NESTED_COMMENT = "/* comment /* nested */ */";
  public static final String NESTED_COMMENT_EXPECTED_INNER = " comment /* nested */ ";

  private BenchmarkInputs() {}
}
