package com.google.mu.benchmarks.parsers.parsecj;

import com.google.mu.benchmarks.parsers.javatype.*;
import static com.google.mu.benchmarks.parsers.parsecj.ParsecjJavaTypeParser.parse;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParsecjJavaTypeParserTest {

  @Test
  public void testSimpleType() {
    JavaType type = parse("String");
    assertEquals("String", type.toString());
    assertTrue(type.packageName().isEmpty());
    assertEquals(1, type.segments().size());
    assertEquals("String", type.segments().get(0).typeName());
    assertTrue(type.segments().get(0).annotations().isEmpty());
  }

  @Test
  public void testFullyQualifiedType() {
    JavaType type = parse("java.lang.String");
    assertEquals("java.lang.String", type.toString());
    assertEquals(List.of("java", "lang"), type.packageName());
    assertEquals(1, type.segments().size());
    assertEquals("String", type.segments().get(0).typeName());
  }

  @Test
  public void testPrimitiveTypes() {
    assertEquals("int", parse("int").toString());
    assertEquals("double[][]", parse("double[][]").toString());
    assertEquals("java.lang.Class<void>", parse("java.lang.Class<void>").toString());
  }

  @Test
  public void testNestedType() {
    JavaType type = parse("Outer.Inner");
    assertEquals("Outer.Inner", type.toString());
    assertEquals(2, type.segments().size());
    assertEquals("Outer", type.segments().get(0).typeName());
    assertEquals("Inner", type.segments().get(1).typeName());
  }

  @Test
  public void testNestedTypesWithGenerics() {
    JavaType type1 = parse("Foo.Bar<A, B, C>");
    assertEquals("Foo.Bar<A, B, C>", type1.toString());
    assertEquals(2, type1.segments().size());
    assertEquals("Foo", type1.segments().get(0).typeName());
    assertTrue(type1.segments().get(0).typeArguments().isEmpty());
    assertEquals("Bar", type1.segments().get(1).typeName());
    assertEquals(3, type1.segments().get(1).typeArguments().size());

    JavaType type2 = parse("Foo<A, B>.Bar");
    assertEquals("Foo<A, B>.Bar", type2.toString());
    assertEquals(2, type2.segments().size());
    assertEquals("Foo", type2.segments().get(0).typeName());
    assertEquals(2, type2.segments().get(0).typeArguments().size());
    assertEquals("Bar", type2.segments().get(1).typeName());
    assertTrue(type2.segments().get(1).typeArguments().isEmpty());

    JavaType type3 = parse("Outer<String>.Inner<Integer>");
    assertEquals("Outer<String>.Inner<Integer>", type3.toString());
  }

  @Test
  public void testGenerics() {
    JavaType type = parse("List<String>");
    assertEquals("List<String>", type.toString());
    assertEquals(1, type.segments().size());
    List<JavaType> args = type.segments().get(0).typeArguments();
    assertEquals(1, args.size());
    assertEquals("String", args.get(0).toString());
  }

  @Test
  public void testNestedGenerics() {
    JavaType type = parse("Map<String, List<Integer>>");
    assertEquals("Map<String, List<Integer>>", type.toString());
    List<JavaType> args = type.segments().get(0).typeArguments();
    assertEquals(2, args.size());
    assertEquals("String", args.get(0).toString());
    assertEquals("List<Integer>", args.get(1).toString());
  }

  @Test
  public void testArrayTypes() {
    JavaType type = parse("String[][]");
    assertEquals("String[][]", type.toString());
    assertEquals(2, type.arrayDimensions());
  }

  @Test
  public void testSimpleAnnotations() {
    JavaType type = parse("@NonNull String");
    assertEquals("@NonNull String", type.toString());
    assertEquals(1, type.segments().size());
    List<JavaAnnotation> annos = type.segments().get(0).annotations();
    assertEquals(1, annos.size());
    assertEquals("NonNull", annos.get(0).name());
    assertTrue(annos.get(0).parameters().isEmpty());
  }

  @Test
  public void testNestedAnnotationsOnSegments() {
    JavaType type = parse("Outer.@Nullable Inner");
    assertEquals("Outer.@Nullable Inner", type.toString());
    assertEquals(2, type.segments().size());
    assertTrue(type.segments().get(0).annotations().isEmpty());
    assertEquals(1, type.segments().get(1).annotations().size());
    assertEquals("Nullable", type.segments().get(1).annotations().get(0).name());
  }

  @Test
  public void testAnnotationParameters() {
    JavaType type = parse("@Size(min=-1.5, max=10) String");
    assertEquals("@Size(min=-1.5, max=10) String", type.toString());
    
    JavaAnnotation anno = type.segments().get(0).annotations().get(0);
    assertEquals("Size", anno.name());
    assertEquals(2, anno.parameters().size());
    
    AnnotationValue.NumberValue min = (AnnotationValue.NumberValue) anno.parameters().get("min");
    assertEquals(-1.5, min.value().doubleValue(), 0.001);
    
    AnnotationValue.NumberValue max = (AnnotationValue.NumberValue) anno.parameters().get("max");
    assertEquals(10.0, max.value().doubleValue(), 0.001);
  }

  @Test
  public void testStringLiteralParametersWithEscaping() {
    JavaType type = parse("@Named(value=\"hello \\\"world\\\" \\u2705\") String");
    assertEquals("@Named(\"hello \\\"world\\\" ✅\") String", type.toString());
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
}
