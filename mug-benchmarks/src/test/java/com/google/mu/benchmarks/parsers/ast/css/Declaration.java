package com.google.mu.benchmarks.parsers.ast.css;

import java.util.List;

public record Declaration(String name, List<ComponentValue> values, boolean isImportant) {
  public Declaration(String name, List<ComponentValue> values) {
    this(name, values, false);
  }

  public Declaration important() {
    return new Declaration(name, values, true);
  }
}
