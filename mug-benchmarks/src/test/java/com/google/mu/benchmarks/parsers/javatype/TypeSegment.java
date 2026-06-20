package com.google.mu.benchmarks.parsers.javatype;

import java.util.List;
import java.util.stream.Collectors;

/** Represents a single type segment within a nested type path (e.g., {@code @NonNull Outer<String>}). */
public record TypeSegment(
    List<JavaAnnotation> annotations,
    String typeName,
    List<JavaType> typeArguments) {
  
  public TypeSegment(List<JavaAnnotation> annotations, String typeName) {
    this(annotations, typeName, List.of());
  }

  @Override
  public String toString() {
    String annos = annotations.isEmpty() ? "" : annotations.stream()
        .map(Object::toString)
        .collect(Collectors.joining(" ", "", " "));
    String generics = typeArguments.isEmpty() ? "" : typeArguments.stream()
        .map(Object::toString)
        .collect(Collectors.joining(", ", "<", ">"));
    return annos + typeName + generics;
  }
}
