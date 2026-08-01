package com.google.mu.benchmarks.parsers.parboiled2

import org.parboiled2._
import com.google.mu.benchmarks.parsers.javatype._
import scala.jdk.CollectionConverters._

/** Parboiled2 macro-compiled PEG parser for Java Types. */
class Parboiled2JavaTypeParser(val input: ParserInput) extends Parser {

  def ws = rule { quiet(zeroOrMore(anyOf(" \t\r\n"))) }

  def token(s: String) = rule { str(s) ~ ws }



  def primitiveType = rule {
    capture("int" | "double" | "float" | "long" | "short" | "byte" | "char" | "boolean" | "void") ~ ws
  }

  def lowerIdentifier = rule {
    capture(CharPredicate.LowerAlpha ~ zeroOrMore(CharPredicate.AlphaNum)) ~ ws
  }

  def upperIdentifier = rule {
    capture(CharPredicate.UpperAlpha ~ zeroOrMore(CharPredicate.AlphaNum)) ~ ws
  }

  def identifier = rule {
    capture(CharPredicate.Alpha ~ zeroOrMore(CharPredicate.AlphaNum | '_')) ~ ws
  }

  def packageSegment = rule {
    lowerIdentifier ~ test(!Parboiled2JavaTypeParser.primitives.contains(valueStack.peek.asInstanceOf[String]))
  }

  def typeName = rule { primitiveType | upperIdentifier }

  def packagePrefix: Rule1[Seq[String]] = rule {
    zeroOrMore(packageSegment ~ token("."))
  }

  def javaType: Rule1[JavaType] = rule {
    packagePrefix ~ oneOrMore(typeSegment).separatedBy(token(".")) ~ zeroOrMore(capture(token("[") ~ token("]"))) ~> {
      (pkg: Seq[String], segments: Seq[TypeSegment], dims: Seq[String]) =>
        new JavaType(pkg.asJava, segments.asJava, dims.size)
    }
  }

  def typeSegment: Rule1[TypeSegment] = rule {
    zeroOrMore(javaAnnotation) ~ typeName ~ optional(typeArguments) ~> {
      (annos: Seq[JavaAnnotation], name: String, args: Option[Seq[JavaType]]) =>
        new TypeSegment(annos.asJava, name, args.getOrElse(Nil).asJava)
    }
  }

  def typeArguments: Rule1[Seq[JavaType]] = rule {
    token("<") ~ oneOrMore(javaType).separatedBy(token(",")) ~ token(">")
  }

  def javaAnnotation: Rule1[JavaAnnotation] = rule {
    token("@") ~ packagePrefix ~ oneOrMore(typeName).separatedBy(token(".")) ~ optional(annotationParams) ~> {
      (pkg: Seq[String], types: Seq[String], params: Option[java.util.Map[String, AnnotationValue]]) =>
        val name = pkg.mkString(".") + (if (pkg.isEmpty) "" else ".") + types.mkString(".")
        new JavaAnnotation(name, params.getOrElse(java.util.Collections.emptyMap()))
    }
  }

  def annotationParams: Rule1[java.util.Map[String, AnnotationValue]] = rule {
    token("(") ~ (namedParamsList | singleParam) ~ token(")")
  }

  def namedParamsList: Rule1[java.util.Map[String, AnnotationValue]] = rule {
    oneOrMore(
      (identifier ~ token("=") ~ annotationValue ~> ((k: String, v: AnnotationValue) => (k, v)))
    ).separatedBy(token(",")) ~> {
      (list: Seq[(String, AnnotationValue)]) =>
        val map = new java.util.LinkedHashMap[String, AnnotationValue]()
        list.foreach { case (k, v) => map.put(k, v) }
        map: java.util.Map[String, AnnotationValue]
    }
  }

  def singleParam: Rule1[java.util.Map[String, AnnotationValue]] = rule {
    annotationValue ~> { (v: AnnotationValue) =>
      val map = new java.util.LinkedHashMap[String, AnnotationValue]()
      map.put("value", v)
      map: java.util.Map[String, AnnotationValue]
    }
  }

  def hexDigit = rule { CharPredicate.HexDigit }

  def unicodeEscape = rule {
    "u" ~ capture(hexDigit ~ hexDigit ~ hexDigit ~ hexDigit) ~> { (hex: String) =>
      Character.toString(Integer.parseInt(hex, 16).toChar)
    }
  }

  def cStyleEscape = rule {
    capture(anyOf("ntrfb\"'\\")) ~> { (esc: String) =>
      esc match {
        case "n" => "\n"
        case "t" => "\t"
        case "r" => "\r"
        case "f" => "\f"
        case "b" => "\b"
        case "\"" => "\""
        case "'" => "'"
        case "\\" => "\\"
      }
    }
  }

  def escapedChar = rule { "\\" ~ (unicodeEscape | cStyleEscape) }
  def normalChar = rule { capture(noneOf("\"\\")) }

  def stringLiteral: Rule1[String] = rule {
    "\"" ~ zeroOrMore(escapedChar | normalChar) ~ "\"" ~ ws ~> { (seq: Seq[String]) =>
      seq.mkString
    }
  }

  def integerPart = rule { capture(optional("-") ~ oneOrMore(CharPredicate.Digit)) }
  def decimalPart = rule { capture("." ~ oneOrMore(CharPredicate.Digit)) }

  def numberVal: Rule1[Number] = rule {
    integerPart ~ optional(decimalPart) ~ ws ~> { (integer: String, decimal: Option[String]) =>
      decimal match {
        case None => java.lang.Integer.valueOf(integer.toInt): Number
        case Some(d) => java.lang.Double.valueOf((integer + d).toDouble): Number
      }
    }
  }

  def annotationValue: Rule1[AnnotationValue] = rule {
    (stringLiteral ~> (new AnnotationValue.StringValue(_))) |
    (javaType ~ token(".class") ~> (new AnnotationValue.ClassLiteralValue(_))) |
    (numberVal ~> (new AnnotationValue.NumberValue(_))) |
    (javaAnnotation ~> (new AnnotationValue.AnnotationValueHolder(_))) |
    (token("{") ~ zeroOrMore(annotationValue).separatedBy(token(",")) ~ token("}") ~> { (list: Seq[AnnotationValue]) =>
      new AnnotationValue.ArrayValue(list.asJava)
    })
  }

  def entry: Rule1[JavaType] = rule {
    ws ~ javaType ~ EOI
  }
}

object Parboiled2JavaTypeParser {
  private val primitives = Set("int", "double", "float", "long", "short", "byte", "char", "boolean", "void")

  def parse(input: String): JavaType = {
    val parser = new Parboiled2JavaTypeParser(input)
    parser.entry.run() match {
      case scala.util.Success(value) => value
      case scala.util.Failure(error: ParseError) =>
        throw new IllegalArgumentException(parser.formatError(error))
      case scala.util.Failure(error) =>
        throw error
    }
  }
}
