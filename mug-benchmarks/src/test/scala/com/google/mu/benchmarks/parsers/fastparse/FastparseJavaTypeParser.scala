package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.javatype._
import scala.jdk.CollectionConverters._

object FastparseJavaTypeParser {
  import fastparse._

  // =========================================================================
  // 1. Non-whitespace Parser Scope (for string and number literals)
  // =========================================================================
  private object NoWsParser {
    import fastparse._, NoWhitespace._

    def hexDigit[_: P] = P( CharIn("0-9a-fA-F") )
    
    def unicodeEscape[_: P]: P[String] = P( "u" ~ hexDigit.rep(exactly = 4).! ).map { hex =>
      Character.toString(Integer.parseInt(hex, 16).toChar)
    }

    def cStyleEscape[_: P]: P[String] = P(
      StringIn("n", "t", "r", "f", "b", "\"", "'", "\\").!
    ).map {
      case "n" => "\n"
      case "t" => "\t"
      case "r" => "\r"
      case "f" => "\f"
      case "b" => "\b"
      case "\"" => "\""
      case "'" => "'"
      case "\\" => "\\\\"
    }

    def escapedChar[_: P]: P[String] = P( "\\" ~ (unicodeEscape | cStyleEscape) )
    def normalChar[_: P]: P[String] = P( CharPred(c => c != '"' && c != '\\').! )

    def stringLiteral[_: P]: P[String] = P(
      "\"" ~ (escapedChar | normalChar).rep ~ "\""
    ).map(_.mkString)

    def integerPart[_: P]: P[String] = P( (P("-").? ~ CharsWhileIn("0-9")).! )
    
    def numberVal[_: P]: P[Number] = P( integerPart ~ (P(".") ~ CharsWhileIn("0-9")).!.? ).map {
      case (integer, None) => java.lang.Integer.valueOf(integer.toInt)
      case (integer, Some(decimal)) => java.lang.Double.valueOf((integer + decimal).toDouble)
    }
  }

  // =========================================================================
  // 2. Main JavaType Parser Scope (with custom whitespace-only skipping)
  // =========================================================================
  implicit val whitespace: fastparse.Whitespace = { ctx =>
    import fastparse.NoWhitespace._
    implicit val c = ctx
    CharsWhileIn(" \t\r\n", 0)
  }

  private val primitives: Set[String] = Set(
    "int", "double", "float", "long", "short", "byte", "char", "boolean", "void"
  )

  private def packageSegment[_: P]: P[String] = P(
    (CharIn("a-z") ~ CharsWhileIn("a-zA-Z0-9", 0)).!
  ).filter(s => !primitives.contains(s))

  private def packagePrefix[_: P]: P[Seq[String]] = P( (packageSegment ~ ".").rep )

  private def identifier[_: P]: P[String] = P( (CharIn("a-zA-Z") ~ CharsWhileIn("a-zA-Z0-9_", 0)).! )

  private def primitiveType[_: P]: P[String] = P( StringIn("int", "double", "float", "long", "short", "byte", "char", "boolean", "void").! )

  private def typeName[_: P]: P[String] = P( primitiveType | (CharIn("A-Z") ~ CharsWhileIn("a-zA-Z0-9", 0)).! )

  // Flat, top-level parser rules to maintain compile-time static macro structures

  private def namedParams[_: P]: P[(String, AnnotationValue)] = P( identifier ~ "=" ~ annotationValue )

  private def namedParamsList[_: P]: P[java.util.Map[String, AnnotationValue]] = P(
    "(" ~ namedParams.rep(sep = ",") ~ ")"
  ).map { list =>
    val map = new java.util.LinkedHashMap[String, AnnotationValue]()
    list.foreach { case (k, v) => map.put(k, v) }
    map
  }

  private def singleParam[_: P]: P[java.util.Map[String, AnnotationValue]] = P(
    "(" ~ annotationValue ~ ")"
  ).map { v =>
    val map = new java.util.LinkedHashMap[String, AnnotationValue]()
    map.put("value", v)
    map
  }

  private def params[_: P]: P[java.util.Map[String, AnnotationValue]] = P( namedParamsList | singleParam )

  private def javaAnnotation[_: P]: P[JavaAnnotation] = P(
    "@" ~ (packageSegment ~ ".").rep ~ typeName.rep(sep = ".") ~ params.?
  ).map {
    case (pkg, types, optParams) =>
      val name = pkg.mkString(".") + (if (pkg.isEmpty) "" else ".") + types.mkString(".")
      new JavaAnnotation(name, optParams.getOrElse(java.util.Collections.emptyMap()))
  }

  private def annotationValue[_: P]: P[AnnotationValue] = P(
    stringVal | nestedAnnoVal | classLiteralVal | numberVal0 | arrayVal
  )

  private def stringVal[_: P]: P[AnnotationValue] = P( NoWsParser.stringLiteral ).map(new AnnotationValue.StringValue(_))

  private def nestedAnnoVal[_: P]: P[AnnotationValue] = P( javaAnnotation ).map(new AnnotationValue.AnnotationValueHolder(_))

  private def classLiteralVal[_: P]: P[AnnotationValue] = P( javaTypeParser ~ ".class" ).map(new AnnotationValue.ClassLiteralValue(_))

  private def numberVal0[_: P]: P[AnnotationValue] = P( NoWsParser.numberVal ).map(new AnnotationValue.NumberValue(_))

  private def arrayVal[_: P]: P[AnnotationValue] = P(
    "{" ~ annotationValue.rep(sep = ",") ~ "}"
  ).map { list =>
    new AnnotationValue.ArrayValue(list.asJava)
  }

  private def typeSegmentWithArgs[_: P]: P[TypeSegment] = P(
    javaAnnotation.rep ~ typeName ~ "<" ~ javaTypeParser.rep(sep = ",") ~ ">"
  ).map {
    case (annos, name, args) =>
      new TypeSegment(annos.asJava, name, args.asJava)
  }

  private def typeSegmentWithoutArgs[_: P]: P[TypeSegment] = P(
    javaAnnotation.rep ~ typeName
  ).map {
    case (annos, name) =>
      new TypeSegment(annos.asJava, name, java.util.Collections.emptyList())
  }

  private def typeSegment[_: P]: P[TypeSegment] = P( typeSegmentWithArgs | typeSegmentWithoutArgs )

  def javaTypeParser[_: P]: P[JavaType] = P(
    packagePrefix ~ typeSegment.rep(min = 1, sep = P( "." ~ !P("class") )) ~ "[]".!.rep
  ).map {
    case (pkg, segments, dims) =>
      new JavaType(pkg.asJava, segments.asJava, dims.size)
  }

  def entry[_: P]: P[JavaType] = P( Start ~ javaTypeParser ~ End )

  /** Parses a JavaType from the given string using Fastparse. */
  def parse(input: String): JavaType = {
    fastparse.parse(input, entry(_)) match {
      case Parsed.Success(value, _) => value
      case Parsed.Failure(label, index, extra) =>
        throw new IllegalArgumentException(
          s"Fastparse parsing error at index $index: $label. "
            + s"Extra: ${extra.trace().msg}"
        )
    }
  }
}
