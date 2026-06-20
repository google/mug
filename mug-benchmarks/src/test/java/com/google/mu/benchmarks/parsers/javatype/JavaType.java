package com.google.mu.benchmarks.parsers.javatype;

import java.util.List;
import java.util.stream.Collectors;

/** Represents a complete Java type (e.g. {@code java.util.Map<java.lang.String, @NonNull List<Integer>[]>}). */
public record JavaType(
    List<String> packageName,
    List<TypeSegment> segments,
    int arrayDimensions) {
  
  public JavaType(List<String> packageName, List<TypeSegment> segments) {
    this(packageName, segments, 0);
  }

  @Override
  public String toString() {
    String pkg = packageName.isEmpty() ? "" : String.join(".", packageName) + ".";
    String path = segments.stream()
        .map(Object::toString)
        .collect(Collectors.joining("."));
    String arrays = "[]".repeat(arrayDimensions);
    return pkg + path + arrays;
  }
}
