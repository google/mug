package com.google.mu.benchmarks.parsers;

import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.petitparser.parser.Parser;
import org.petitparser.parser.primitive.CharacterParser;
import org.petitparser.parser.primitive.StringParser;

public class InspectPetitParser {
  @Test
  public void inspect() {
    System.out.println("=== CharacterParser Methods ===");
    for (Method m : CharacterParser.class.getDeclaredMethods()) {
      if (Modifier.isPublic(m.getModifiers())) {
        System.out.println(m.toString());
      }
    }
    System.out.println("=== StringParser Methods ===");
    for (Method m : StringParser.class.getDeclaredMethods()) {
      if (Modifier.isPublic(m.getModifiers())) {
        System.out.println(m.toString());
      }
    }
    System.out.println("=== Parser Methods ===");
    for (Method m : Parser.class.getDeclaredMethods()) {
      if (Modifier.isPublic(m.getModifiers())) {
        System.out.println(m.toString());
      }
    }
  }
}
