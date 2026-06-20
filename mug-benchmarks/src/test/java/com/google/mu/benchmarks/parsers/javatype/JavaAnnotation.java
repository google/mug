package com.google.mu.benchmarks.parsers.javatype;

import java.util.Map;
import java.util.stream.Collectors;

/** Represents an annotation on a type or type argument (e.g., {@code @NonNull} or {@code @Size(max=10)}). */
public record JavaAnnotation(String name, Map<String, AnnotationValue> parameters) {
  
  public static JavaAnnotation marker(String name) {
    return new JavaAnnotation(name, Map.of());
  }

  public static JavaAnnotation single(String name, AnnotationValue value) {
    return new JavaAnnotation(name, Map.of("value", value));
  }

  @Override
  public String toString() {
    if (parameters.isEmpty()) {
      return "@" + name;
    }
    if (parameters.size() == 1 && parameters.containsKey("value")) {
      return "@" + name + "(" + parameters.get("value") + ")";
    }
    String params = parameters.entrySet().stream()
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining(", "));
    return "@" + name + "(" + params + ")";
  }
}
