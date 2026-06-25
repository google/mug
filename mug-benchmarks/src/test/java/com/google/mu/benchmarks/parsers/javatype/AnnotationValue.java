package com.google.mu.benchmarks.parsers.javatype;

import java.util.List;

/** Represents a parameter value inside a type annotation. */
public interface AnnotationValue {
  
  record NumberValue(Number value) implements AnnotationValue {
    @Override
    public String toString() {
      return value.toString();
    }
  }
  
  record StringValue(String value) implements AnnotationValue {
    @Override
    public String toString() {
      return "\"" + value.replace("\"", "\\\"") + "\"";
    }
  }
  
  record ClassLiteralValue(JavaType type) implements AnnotationValue {
    @Override
    public String toString() {
      return type.toString() + ".class";
    }
  }
  
  record ArrayValue(List<AnnotationValue> values) implements AnnotationValue {
    @Override
    public String toString() {
      return "{" + String.join(", ", values.stream().map(Object::toString).toList()) + "}";
    }
  }
  
  record AnnotationValueHolder(JavaAnnotation annotation) implements AnnotationValue {
    @Override
    public String toString() {
      return annotation.toString();
    }
  }
}
