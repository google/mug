package com.google.mu.benchmarks.parsers.parboiled2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import com.google.mu.benchmarks.parsers.javatype.JavaType

/** Tests for {@link Parboiled2JavaTypeParser}. */
@RunWith(classOf[JUnit4])
class Parboiled2JavaTypeParserTest {

  @Test
  def testSimpleClass(): Unit = {
    val t = parse("String")
    assertEquals("String", t.toString)
  }

  @Test
  def testPrimitiveType(): Unit = {
    val t = parse("int")
    assertEquals("int", t.toString)
  }

  @Test
  def testFullyQualifiedClass(): Unit = {
    val t = parse("java.lang.String")
    assertEquals("java.lang.String", t.toString)
  }

  @Test
  def testGenericType(): Unit = {
    val t = parse("List<Integer>")
    assertEquals("List<Integer>", t.toString)
  }

  @Test
  def testMultipleTypeArguments(): Unit = {
    val t = parse("Map<String, Integer>")
    assertEquals("Map<String, Integer>", t.toString)
  }

  @Test
  def testNestedGenerics(): Unit = {
    val t = parse("Map<String, List<Integer>>")
    assertEquals("Map<String, List<Integer>>", t.toString)
  }

  @Test
  def testArrayType(): Unit = {
    val t = parse("String[]")
    assertEquals("String[]", t.toString)
  }

  @Test
  def testMultiDimensionalArray(): Unit = {
    val t = parse("int[][]")
    assertEquals("int[][]", t.toString)
  }

  @Test
  def testAnnotatedType(): Unit = {
    val t = parse("@Nullable String")
    assertEquals("@Nullable String", t.toString)
  }


  @Test
  def testComplexAnnotationParameters(): Unit = {
    val t = parse("@MyAnnotation(a=1, b=\"hello \\\"world\\\"\", c=@Nested, d={1, 2}) String")
    assertEquals(
      "@MyAnnotation(a=1, b=\"hello \\\"world\\\"\", c=@Nested, d={1, 2}) String",
      t.toString
    )
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

  @Test
  def testInvalidInput(): Unit = {
    assertThrows(classOf[IllegalArgumentException], () => parse(""))
    assertThrows(classOf[IllegalArgumentException], () => parse("Map<String"))
    assertThrows(classOf[IllegalArgumentException], () => parse("String.class"))
  }

  private def parse(s: String): JavaType = {
    Parboiled2JavaTypeParser.parse(s)
  }
}
