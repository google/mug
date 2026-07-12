package com.google.mu.benchmarks.parsers.ast.css;

import java.util.List;

public interface ComponentValue {
  record Ident(String name) implements ComponentValue {
    public ComponentValue toFunctionCall(List<ComponentValue> args) {
      return new FunctionBlock(name, new BracketsBlock(args));
    }
  }
  record AtWord(String name) implements ComponentValue {}
  record HashWord(String name) implements ComponentValue {}
  record Str(String value) implements ComponentValue {}
  record Url(String url) implements ComponentValue {}
  record Num(double value) implements ComponentValue {
    public ComponentValue asPercentage() {
      return new Percentage(value);
    }
    public ComponentValue asDimension(String unit) {
      return new Dimension(value, unit);
    }
  }
  record Dimension(double value, String unit) implements ComponentValue {}
  record Percentage(double value) implements ComponentValue {}
  record UnicodeRange(String left, String right) implements ComponentValue {}
  record Delim(String delim) implements ComponentValue {}

  record BracketsBlock(List<ComponentValue> values) implements ComponentValue {}
  record CurlyBracketsBlock(List<ComponentValue> values) implements ComponentValue {}
  record SquareBracketsBlock(List<ComponentValue> values) implements ComponentValue {}
  record FunctionBlock(String name, BracketsBlock args) implements ComponentValue {}
}
