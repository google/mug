package com.google.mu.benchmarks.parsers.scratch;

import com.google.common.labs.parse.Parser;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class InspectDotParse {
  public static void main(String[] args) throws Exception {
    System.out.println("=== com.google.common.labs.parse.Parser METHODS ===");
    for (Method method : Parser.class.getDeclaredMethods()) {
      if (Modifier.isStatic(method.getModifiers()) || Modifier.isPublic(method.getModifiers())) {
        System.out.println(method.toString());
      }
    }
  }
}
