package com.google.mu.benchmarks.parsers.catsparse

import cats.parse.{Parser => P, Parser0 => P0}
import com.google.mu.benchmarks.parsers.javatype._
import scala.jdk.CollectionConverters._

/** Cats-parse-based parser for Java Types. */
object CatsParseJavaTypeParser {

  private val skip: P0[Unit] = P.charIn(" \t\r\n").rep0.void

  private def token[A](p: P[A]): P[A] = p <* skip
  private def tokenStr(s: String): P[String] = token(P.string(s).as(s))

  private val primitives: Set[String] = Set(
    "int", "double", "float", "long", "short", "byte", "char", "boolean", "void"
  )

  private val primitiveType: P[String] = P.oneOf(
    primitives.toList.map(s => P.string(s))
  ).string

  private val typeName: P[String] = token(
    primitiveType | (
      P.charIn('A' to 'Z') ~ P.charIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).rep0
    ).string
  )

  private val packageSegment: P[String] = token(
    (P.charIn('a' to 'z') ~ P.charIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).rep0).string
  ).filter(s => !primitives.contains(s)).backtrack

  private val packagePrefix: P0[List[String]] = (packageSegment <* tokenStr(".")).backtrack.rep0

  private val identifier: P[String] = token(
    (
      P.charIn(('a' to 'z') ++ ('A' to 'Z'))
        ~ P.charIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') :+ '_').rep0
    ).string
  )

  // String Unescaping (reusing the showdown parser)
  private val stringLiteral: P[String] = token(CatsParseShowdown.StringFixture.PARSER)

  // Numbers
  private val integerPart: P[String] = (P.char('-').?.with1 ~ P.charIn('0' to '9').rep).string

  private val numberVal: P[Number] = token(
    (integerPart ~ (P.char('.') *> P.charIn('0' to '9').rep).string.?).map {
      case (integer, None) => Integer.valueOf(integer.toInt)
      case (integer, Some(decimal)) => java.lang.Double.valueOf((integer + decimal).toDouble)
    }
  )

  // Forward-declared / mutually recursive parsers using lazy val and P.defer

  lazy val javaTypeParser: P[JavaType] = {
    val typeSegmentWithArgs = (
      P.defer(javaAnnotation).rep0.with1
        ~ (typeName ~ (tokenStr("<") *> P.defer(javaTypeParser).repSep(tokenStr(",")) <* tokenStr(">")))
    ).map {
      case (annos, (name, args)) =>
        new TypeSegment(annos.asJava, name, args.toList.asJava)
    }

    val typeSegmentWithoutArgs = (P.defer(javaAnnotation).rep0.with1 ~ typeName).map {
      case (annos, name) =>
        new TypeSegment(annos.asJava, name, java.util.Collections.emptyList())
    }

    val typeSegment: P[TypeSegment] = typeSegmentWithArgs.backtrack | typeSegmentWithoutArgs

    val prefixAndSegments =
      packagePrefix.with1 ~ typeSegment.repSep((tokenStr(".") <* !P.string("class")).backtrack)

    (prefixAndSegments ~ tokenStr("[]").rep0).map {
      case ((pkg, segments), dims) =>
        new JavaType(pkg.asJava, segments.toList.asJava, dims.size)
    }
  }

  lazy val javaAnnotation: P[JavaAnnotation] = {
    val namedParams = (identifier <* tokenStr("=")) ~ P.defer(annotationValue)
    val namedParamsList = namedParams.repSep0(tokenStr(","))
      .between(tokenStr("("), tokenStr(")"))
      .map { list =>
        val map = new java.util.LinkedHashMap[String, AnnotationValue]()
        list.foreach { case (k, v) => map.put(k, v) }
        map
      }

    val singleParam = P.defer(annotationValue).between(tokenStr("("), tokenStr(")"))
      .map(v => Map("value" -> v).asJava)

    val params = (namedParamsList.backtrack | singleParam).?

    val annotationName =
      (packageSegment <* P.char('.')).backtrack.rep0 ~ typeName.repSep(P.char('.'))

    tokenStr("@") *> (annotationName ~ params).map {
      case ((pkg, types), optParams) =>
        val name = pkg.mkString(".") + (if (pkg.isEmpty) "" else ".") + types.toList.mkString(".")
        new JavaAnnotation(name, optParams.getOrElse(java.util.Collections.emptyMap()))
    }
  }

  lazy val annotationValue: P[AnnotationValue] = {
    val stringVal = stringLiteral.map(new AnnotationValue.StringValue(_))
    val classLiteralVal =
      (P.defer(javaTypeParser) <* tokenStr(".class")).map(new AnnotationValue.ClassLiteralValue(_))
    val numberVal0 = numberVal.map(new AnnotationValue.NumberValue(_))
    val nestedAnnoVal = P.defer(javaAnnotation).map(new AnnotationValue.AnnotationValueHolder(_))
    val arrayVal =
      (tokenStr("{") *> P.defer(annotationValue).repSep0(tokenStr(",")) <* tokenStr("}")).map { list =>
      new AnnotationValue.ArrayValue(list.asJava)
    }

    stringVal | nestedAnnoVal | classLiteralVal.backtrack | numberVal0 | arrayVal
  }

  private val rootParser: P0[JavaType] = skip *> javaTypeParser

  /** Parses a JavaType from the given string using Cats-parse. */
  def parse(input: String): JavaType = {
    rootParser.parse(input) match {
      case Right(("", value)) => value
      case Right((unparsed, _)) =>
        throw new IllegalArgumentException(s"Unparsed trailing input: $unparsed")
      case Left(err) => throw new IllegalArgumentException(s"Cats-parse parsing error: $err")
    }
  }
}
