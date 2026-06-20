package com.google.mu.benchmarks.parsers.antlr4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.mu.benchmarks.parsers.javatype.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Antlr4JavaTypeParser}. */
@RunWith(JUnit4.class)
public class Antlr4JavaTypeParserTest {

  @Test
  public void testSimpleClass() {
    JavaType type = parse("String");
    assertEquals("String", type.toString());
  }

  @Test
  public void testPrimitiveType() {
    JavaType type = parse("int");
    assertEquals("int", type.toString());
  }

  @Test
  public void testFullyQualifiedClass() {
    JavaType type = parse("java.lang.String");
    assertEquals("java.lang.String", type.toString());
  }

  @Test
  public void testGenericType() {
    JavaType type = parse("List<Integer>");
    assertEquals("List<Integer>", type.toString());
  }

  @Test
  public void testMultipleTypeArguments() {
    JavaType type = parse("Map<String, Integer>");
    assertEquals("Map<String, Integer>", type.toString());
  }

  @Test
  public void testNestedGenerics() {
    JavaType type = parse("Map<String, List<Integer>>");
    assertEquals("Map<String, List<Integer>>", type.toString());
  }

  @Test
  public void testArrayType() {
    JavaType type = parse("String[]");
    assertEquals("String[]", type.toString());
  }

  @Test
  public void testMultiDimensionalArray() {
    JavaType type = parse("int[][]");
    assertEquals("int[][]", type.toString());
  }

  @Test
  public void testAnnotatedType() {
    JavaType type = parse("@Nullable String");
    assertEquals("@Nullable String", type.toString());
  }


  @Test
  public void testComplexAnnotationParameters() {
    JavaType type = parse("@MyAnnotation(a=1, b=\"hello \\\"world\\\"\", c=@Nested, d={1, 2}) String");
    assertEquals(
        "@MyAnnotation(a=1, b=\"hello \\\"world\\\"\", c=@Nested, d={1, 2}) String",
        type.toString());
  }

  @Test
  public void testClassLiteralAndArrayParameters() {
    JavaType type =
        parse(
            "@MyAnnotation(classes={java.lang.String.class, int[].class},"
                + " value=@NestedAnno(123)) List<Integer>");
    assertEquals(
        "@MyAnnotation(classes={java.lang.String.class, int[].class},"
            + " value=@NestedAnno(123)) List<Integer>",
        type.toString());
  }

  @Test
  public void testWhitespace() {
    JavaType type = parse(
        "   \n" +
        "java.util.Map<\n" +
        "  String,\n" +
        "  @NonNull List<Integer>[]\n" +
        " >"
    );
    assertEquals("java.util.Map<String, @NonNull List<Integer>[]>", type.toString());
  }

  @Test
  public void testInvalidInput() {
    assertThrows(IllegalArgumentException.class, () -> parse(""));
    assertThrows(IllegalArgumentException.class, () -> parse("Map<String"));
    assertThrows(IllegalArgumentException.class, () -> parse("String.class"));
  }

  private static JavaType parse(String s) {
    return Antlr4JavaTypeParser.parse(s);
  }
}
