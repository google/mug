package com.google.mu.benchmarks.parsers.catsparse

import cats.parse.{Parser => P, Parser0 => P0}
import com.google.mu.benchmarks.parsers.dotparse.JsonValue
import com.google.mu.benchmarks.parsers.dotparse.JsonValue._
import scala.jdk.CollectionConverters._

/** Strictly RFC 8259-compliant Cats-parse-based JSON parser. */
object CatsParseJsonParser {

  private val skip: P0[Unit] = P.charIn(" \t\r\n").rep0.void

  private def token[A](p: P[A]): P[A] = p <* skip
  private def tokenStr(s: String): P[String] = token(P.string(s).as(s))

  // Strict RFC 8259 number parsing constructed with native cats-parse combinators (no regex)
  private val jsonNumber: P[JsonNumber] = token {
    val sign = P.char('-').?.with1
    val zero = P.char('0').string
    val nonZero = (P.charIn('1' to '9') ~ P.charIn('0' to '9').rep0).string
    val integer = zero | nonZero
    val fraction = (P.char('.') ~ P.charIn('0' to '9').rep).string
    val exponent = (P.charIn("eE") ~ P.charIn("+-").? ~ P.charIn('0' to '9').rep).string

    (sign ~ integer ~ fraction.? ~ exponent.?).string.map { s =>
      new JsonNumber(s.toDouble)
    }
  }

  // Matches double-quoted string and unescapes strictly
  private val stringLiteral: P[String] = {
    val quote = P.char('"')
    val normal = P.charWhere(c => c != '"' && c != '\\')
    val escape = P.char('\\') ~ P.anyChar
    val content = (normal | escape).rep0.string
    (quote *> content <* quote).map { body =>
      strictUnescape("\"" + body + "\"")
    }
  }

  private val jsonString: P[JsonString] = token(stringLiteral).map(new JsonString(_))

  private val jsonNull: P[JsonNull] = token(P.string("null")).as(JsonNull.INSTANCE)

  private val jsonBoolean: P[JsonBoolean] = P.oneOf(
    token(P.string("true")).as(JsonBoolean.TRUE) ::
    token(P.string("false")).as(JsonBoolean.FALSE) :: Nil
  )

  // Recursive JSON value parser rules using lazy val and P.defer
  lazy val jsonValue: P[JsonValue] = P.defer {
    jsonNull | jsonBoolean | jsonNumber | jsonString | jsonArray | jsonObject
  }

  lazy val jsonArray: P[JsonArray] = {
    val emptyArray = (tokenStr("[") ~ tokenStr("]")).as(new JsonArray(java.util.List.of()))
    val nonEmptyArray = (
      tokenStr("[") *> jsonValue.repSep(tokenStr(",")) <* tokenStr("]")
    ).map(nel => new JsonArray(nel.toList.asJava))

    emptyArray.backtrack | nonEmptyArray
  }

  lazy val member: P[(String, JsonValue)] = (token(stringLiteral) <* tokenStr(":")) ~ jsonValue

  lazy val jsonObject: P[JsonObject] = {
    val emptyObject = (tokenStr("{") ~ tokenStr("}")).as(new JsonObject(java.util.Map.of()))
    val nonEmptyObject = (
      tokenStr("{") *> member.repSep(tokenStr(",")) <* tokenStr("}")
    ).map { nel =>
      val map = new java.util.LinkedHashMap[String, JsonValue]()
      nel.toList.foreach { case (k, v) => map.put(k, v) }
      new JsonObject(map)
    }

    emptyObject.backtrack | nonEmptyObject
  }

  val root: P[JsonValue] = skip.with1 *> jsonValue <* P.end

  // Main entry point
  def parse(input: String): JsonValue = {
    root.parseAll(input) match {
      case Right(value) => value
      case Left(error) =>
        throw new IllegalArgumentException(s"Cats-parse error: $error")
    }
  }

  // Strict unescape complying with RFC 8259 Section 7 string constraints
  private def strictUnescape(quoted: String): String = {
    val text = quoted.substring(1, quoted.length - 1)
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
