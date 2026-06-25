package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.dotparse.JsonValue
import com.google.mu.benchmarks.parsers.dotparse.JsonValue._
import scala.jdk.CollectionConverters._

/** Strictly RFC 8259-compliant Fastparse-based JSON parser using Li Haoyi's optimized patterns. */
object FastparseJsonParser {
  import fastparse._

  // =========================================================================
  // 1. Non-whitespace Parser Scope (for string and number literals)
  // =========================================================================
  private object NoWsParser {
    import fastparse._, NoWhitespace._

    def stringChars(c: Char): Boolean = c != '\"' && c != '\\'

    def hexDigit[$: P]: P[Unit]      = P( CharIn("0-9a-fA-F") )
    def unicodeEscape[$: P]: P[Unit] = P( "u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit )
    def escape[$: P]: P[Unit]        = P( "\\" ~ (CharIn("\"/\\\\bfnrt") | unicodeEscape) )

    def strChars[$: P]: P[Unit]      = P( CharsWhile(stringChars) )

    // Matches quoted string and unescapes strictly with exactly 1 allocation
    def stringLiteral[$: P]: P[String] = P(
      "\"" ~/ (strChars | escape).rep.! ~ "\""
    ).map(strictUnescape)

    // Strict RFC 8259 number parsing using Li Haoyi's optimized block scanning
    def digits[$: P]: P[Unit]        = P( CharsWhileIn("0-9") )
    def exponent[$: P]: P[Unit]      = P( CharIn("eE") ~ CharIn("+\\-").? ~ digits )
    def fractional[$: P]: P[Unit]    = P( "." ~ digits )
    def integral[$: P]: P[Unit]      = P( "0" | CharIn("1-9") ~ digits.? )

    def numberLiteral[$: P]: P[Double] = P(
      P( "-".? ) ~ integral ~ fractional.? ~ exponent.?
    ).!.map(_.toDouble)
  }

  // =========================================================================
  // 2. Custom Whitespace and Comment Skippers
  // =========================================================================
  val strictWhitespace: fastparse.Whitespace = { ctx =>
    import fastparse.NoWhitespace._
    implicit val c = ctx
    CharsWhileIn(" \t\r\n", 0)
  }

  val whitespaceWithComments: fastparse.Whitespace = { ctx =>
    import fastparse.NoWhitespace._
    implicit val c = ctx
    P( (CharsWhileIn(" \t\r\n", 1) | ("//" ~ CharsWhile(c => c != '\n', 0) ~ "\n".?) | ("/*" ~ (!"*/" ~ AnyChar).rep ~ "*/")).rep )
  }

  // =========================================================================
  // 3. Parametric JSON Grammar Class (reusable for both skippers)
  // =========================================================================
  private class JsonGrammar()(implicit val whitespace: fastparse.Whitespace) {
    import fastparse._

    def jsonNull[_: P]: P[JsonNull] = P( "null" ).map(_ => JsonNull.INSTANCE)

    def jsonBoolean[_: P]: P[JsonBoolean] = P(
      P("true").map(_ => JsonBoolean.TRUE) | P("false").map(_ => JsonBoolean.FALSE)
    )

    def jsonNumber[_: P]: P[JsonNumber] = P( NoWsParser.numberLiteral ).map(new JsonNumber(_))

    def jsonString[_: P]: P[JsonString] = P( NoWsParser.stringLiteral ).map(new JsonString(_))

    def jsonValue[_: P]: P[JsonValue] = P(
      jsonNull | jsonBoolean | jsonNumber | jsonString | jsonArray | jsonObject
    )

    def jsonArray[_: P]: P[JsonArray] = P(
      "[" ~/ jsonValue.rep(sep = ",") ~ "]"
    ).map(list => new JsonArray(list.asJava))

    def member[_: P]: P[(String, JsonValue)] = P(
      NoWsParser.stringLiteral ~/ ":" ~ jsonValue
    )

    def jsonObject[_: P]: P[JsonObject] = P(
      "{" ~/ member.rep(sep = ",") ~ "}"
    ).map { list =>
      val map = new java.util.LinkedHashMap[String, JsonValue]()
      list.foreach { case (k, v) => map.put(k, v) }
      new JsonObject(map)
    }

    def root[_: P]: P[JsonValue] = P( jsonValue ~ End )
  }

  private val strictGrammar = new JsonGrammar()(strictWhitespace)
  private val commentsGrammar = new JsonGrammar()(whitespaceWithComments)

  // =========================================================================
  // 4. Public Entry Points
  // =========================================================================
  def parse(input: String): JsonValue = {
    implicit val ws = strictWhitespace
    fastparse.parse(input, strictGrammar.root(_)) match {
      case Parsed.Success(value, _) => value
      case f: Parsed.Failure =>
        throw new IllegalArgumentException(s"Fastparse parsing error: ${f.trace().longMsg}")
    }
  }

  def parseWithComments(input: String): JsonValue = {
    implicit val ws = whitespaceWithComments
    fastparse.parse(input, commentsGrammar.root(_)) match {
      case Parsed.Success(value, _) => value
      case f: Parsed.Failure =>
        throw new IllegalArgumentException(s"Fastparse parsing error: ${f.trace().longMsg}")
    }
  }

  // Strict unescape complying with RFC 8259 Section 7 string constraints
  private def strictUnescape(text: String): String = {
    if (text.indexOf('\\') == -1) {
      var j = 0
      while (j < text.length) {
        val charVal = text.charAt(j)
        if (charVal < 0x20) {
          throw new IllegalArgumentException(s"Unescaped control character: 0x${Integer.toHexString(charVal)}")
        }
        j += 1
      }
      text
    } else {
      val sb = new java.lang.StringBuilder(text.length)
      var i = 0
      while (i < text.length) {
        val c = text.charAt(i)
        if (c == '\\') {
          if (i + 1 >= text.length) {
            throw new IllegalArgumentException("Trailing backslash")
          }
          i += 1
          val esc = text.charAt(i)
          esc match {
            case '"'  => sb.append('"')
            case '\\' => sb.append('\\')
            case '/'  => sb.append('/')
            case 'b'  => sb.append('\b')
            case 'f'  => sb.append('\u000c') // form feed
            case 'n'  => sb.append('\n')
            case 'r'  => sb.append('\r')
            case 't'  => sb.append('\t')
            case 'u'  =>
              if (i + 4 >= text.length) {
                throw new IllegalArgumentException("Invalid unicode escape")
              }
              val hex = text.substring(i + 1, i + 5)
              i += 4
              sb.append(Integer.parseInt(hex, 16).toChar)
            case _ =>
              throw new IllegalArgumentException(s"Invalid escape character: \\$esc")
          }
        } else if (c < 0x20) {
          throw new IllegalArgumentException(s"Unescaped control character: 0x${Integer.toHexString(c)}")
        } else {
          sb.append(c)
        }
        i += 1
      }
      sb.toString
    }
  }
}
