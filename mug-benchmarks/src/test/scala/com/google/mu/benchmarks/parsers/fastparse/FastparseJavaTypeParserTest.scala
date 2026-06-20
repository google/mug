package com.google.mu.benchmarks.parsers.fastparse

import org.junit.Test
import org.junit.Assert._
import com.google.mu.benchmarks.parsers.javatype._
import scala.jdk.CollectionConverters._

class FastparseJavaTypeParserTest {

  private def parse(input: String): JavaType = FastparseJavaTypeParser.parse(input)

  @Test
  def testSimpleType(): Unit = {
    val t = parse("String")
    assertEquals("String", t.toString)
    assertTrue(t.packageName.isEmpty)
    assertEquals(1, t.segments.size)
    assertEquals("String", t.segments.get(0).typeName)
    assertTrue(t.segments.get(0).annotations.isEmpty)
  }

  @Test
  def testFullyQualifiedType(): Unit = {
    val t = parse("java.lang.String")
    assertEquals("java.lang.String", t.toString)
    assertEquals(List("java", "lang").asJava, t.packageName)
    assertEquals(1, t.segments.size)
    assertEquals("String", t.segments.get(0).typeName)
  }

  @Test
  def testPrimitiveTypes(): Unit = {
    assertEquals("int", parse("int").toString)
    assertEquals("double[][]", parse("double[][]").toString)
    assertEquals("java.lang.Class<void>", parse("java.lang.Class<void>").toString)
  }

  @Test
  def testNestedType(): Unit = {
    val t = parse("Outer.Inner")
    assertEquals("Outer.Inner", t.toString)
    assertEquals(2, t.segments.size)
    assertEquals("Outer", t.segments.get(0).typeName)
    assertEquals("Inner", t.segments.get(1).typeName)
  }

  @Test
  def testNestedTypesWithGenerics(): Unit = {
    // Static nested type with generics on inner segment only: Foo.Bar<A, B, C>
    val type1 = parse("Foo.Bar<A, B, C>")
    assertEquals("Foo.Bar<A, B, C>", type1.toString)
    assertEquals(2, type1.segments.size)
    assertEquals("Foo", type1.segments.get(0).typeName)
    assertTrue(type1.segments.get(0).typeArguments.isEmpty)
    assertEquals("Bar", type1.segments.get(1).typeName)
    assertEquals(3, type1.segments.get(1).typeArguments.size)

    // Non-static nested type with generics on outer segment: Foo<A, B>.Bar
    val type2 = parse("Foo<A, B>.Bar")
    assertEquals("Foo<A, B>.Bar", type2.toString)
    assertEquals(2, type2.segments.size)
    assertEquals("Foo", type2.segments.get(0).typeName)
    assertEquals(2, type2.segments.get(0).typeArguments.size)
    assertEquals("Bar", type2.segments.get(1).typeName)
    assertTrue(type2.segments.get(1).typeArguments.isEmpty)

    // Generics on both outer and inner segments: Outer<String>.Inner<Integer>
    val type3 = parse("Outer<String>.Inner<Integer>")
    assertEquals("Outer<String>.Inner<Integer>", type3.toString)
  }

  @Test
  def testGenerics(): Unit = {
    val t = parse("List<String>")
    assertEquals("List<String>", t.toString)
    assertEquals(1, t.segments.size)
    val args = t.segments.get(0).typeArguments
    assertEquals(1, args.size)
    assertEquals("String", args.get(0).toString)
  }

  @Test
  def testNestedGenerics(): Unit = {
    val t = parse("Map<String, List<Integer>>")
    assertEquals("Map<String, List<Integer>>", t.toString)
    val args = t.segments.get(0).typeArguments
    assertEquals(2, args.size)
    assertEquals("String", args.get(0).toString)
    assertEquals("List<Integer>", args.get(1).toString)
  }

  @Test
  def testArrayTypes(): Unit = {
    val t = parse("String[][]")
    assertEquals("String[][]", t.toString)
    assertEquals(2, t.arrayDimensions)
  }

  @Test
  def testSimpleAnnotations(): Unit = {
    val t = parse("@NonNull String")
    assertEquals("@NonNull String", t.toString)
    assertEquals(1, t.segments.size)
    val annos = t.segments.get(0).annotations
    assertEquals(1, annos.size)
    assertEquals("NonNull", annos.get(0).name)
    assertTrue(annos.get(0).parameters.isEmpty)
  }

  @Test
  def testNestedAnnotationsOnSegments(): Unit = {
    val t = parse("Outer.@Nullable Inner")
    assertEquals("Outer.@Nullable Inner", t.toString)
    assertEquals(2, t.segments.size)
    assertTrue(t.segments.get(0).annotations.isEmpty)
    assertEquals(1, t.segments.get(1).annotations.size)
    assertEquals("Nullable", t.segments.get(1).annotations.get(0).name)
  }

  @Test
  def testAnnotationParameters(): Unit = {
    val t = parse("@Size(min=-1.5, max=10) String")
    assertEquals("@Size(min=-1.5, max=10) String", t.toString)
    
    val anno = t.segments.get(0).annotations.get(0)
    assertEquals("Size", anno.name)
    assertEquals(2, anno.parameters.size)
    
    val min = anno.parameters.get("min").asInstanceOf[AnnotationValue.NumberValue]
    assertEquals(-1.5, min.value.doubleValue, 0.001)
    
    val max = anno.parameters.get("max").asInstanceOf[AnnotationValue.NumberValue]
    assertEquals(10.0, max.value.doubleValue, 0.001)
  }

  @Test
  def testStringLiteralParametersWithEscaping(): Unit = {
    val t = parse("@Named(value=\"hello \\\"world\\\" \\u2705\") String")
    assertEquals("@Named(\"hello \\\"world\\\" ✅\") String", t.toString)
  }

  @Test
  def testClassLiteralAndArrayParameters(): Unit = {
    val t = parse(
      "@MyAnnotation(classes={java.lang.String.class, int[].class},"
        + " value=@NestedAnno(123)) List<Integer>"
    )
    assertEquals(
      "@MyAnnotation(classes={java.lang.String.class, int[].class},"
        + " value=@NestedAnno(123)) List<Integer>",
      t.toString
    )
  }

  @Test
  def testWhitespace(): Unit = {
    val t = parse(
      "   \n" +
      "java.util.Map<\n" +
      "  String,\n" +
      "  @NonNull List<Integer>[]\n" +
      " >"
    )
    assertEquals("java.util.Map<String, @NonNull List<Integer>[]>", t.toString)
  }
}
