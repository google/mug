package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.dotparse.JsonValue
import com.google.mu.benchmarks.parsers.dotparse.JsonValue._
import scala.jdk.CollectionConverters._
import java.io.Reader
import java.io.BufferedReader
import java.util.function.Consumer

/**
 * Fastparse-based streaming JSON parser.
 * Reads record-by-record from a Reader, invoking a callback for each parsed record,
 * and compacting the input buffer using Fastparse Cuts to maintain O(1) memory.
 */
object FastparseStreamingParser {
  import fastparse._

  // Re-use NoWsParser from FastparseJsonParser for string and number literals
  private object NoWsParser {
    import fastparse._, NoWhitespace._

    def stringChars(c: Char): Boolean = c != '\"' && c != '\\' && c >= 0x20

    def hexDigit[$: P]: P[Unit]      = P( CharIn("0-9a-fA-F") )
    def unicodeEscape[$: P]: P[Unit] = P( "u" ~ hexDigit ~ hexDigit ~ hexDigit ~ hexDigit )
    def escape[$: P]: P[Unit]        = P( "\\" ~ (CharIn("\"/\\\\bfnrt") | unicodeEscape) )

    def strChars[$: P]: P[Unit]      = P( CharsWhile(stringChars) )

    def stringLiteral[$: P]: P[String] = P(
      "\"" ~/ (strChars | escape).rep.! ~ "\""
    ).map(strictUnescape)

    def digits[$: P]: P[Unit]        = P( CharsWhileIn("0-9") )
    def exponent[$: P]: P[Unit]      = P( CharIn("eE") ~ CharIn("+\\-").? ~ digits )
    def fractional[$: P]: P[Unit]    = P( "." ~ digits )
    def integral[$: P]: P[Unit]      = P( "0" | CharIn("1-9") ~ digits.? )

    def numberLiteral[$: P]: P[Double] = P(
      P( "-".? ) ~ integral ~ fractional.? ~ exponent.?
    ).!.map { s =>
      if (s.contains(".") || s.contains("e") || s.contains("E")) {
        s.toDouble
      } else {
        s.toLong.toDouble
      }
    }
  }

  val strictWhitespace: fastparse.Whitespace = { ctx =>
    import fastparse.NoWhitespace._
    implicit val c = ctx
    CharsWhileIn(" \t\r\n", 0)
  }

  // Parameterized streaming grammar
  private class StreamingJsonGrammar(consumer: Consumer[JsonValue])(implicit val whitespace: fastparse.Whitespace) {
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

    // Wrap the record parser: invoke callback and return Unit to prevent AST accumulation
    def record[_: P]: P[Unit] = P( jsonValue ).map { valObj =>
      consumer.accept(valObj)
      ()
    }

    // High-frequency Cut (~/) allows the underlying character buffer to drop old text
    def fileStreamParser[_: P]: P[Unit] = P( (record ~/ Pass).rep.map(_ => ()) )
  }

  /**
   * Parses JSON records sequentially from the Reader, invoking the consumer for each.
   * This is fully compatible with Java and runs in O(1) memory.
   */
  def parse(reader: Reader, consumer: Consumer[JsonValue]): Unit = {
    val bufferedReader = reader match {
      case br: BufferedReader => br
      case _ => new BufferedReader(reader)
    }
    val linesIterator: Iterator[String] = bufferedReader.lines().iterator().asScala
    val grammar = new StreamingJsonGrammar(consumer)(strictWhitespace)
    
    implicit val ws = strictWhitespace
    fastparse.parse(linesIterator, grammar.fileStreamParser(_)) match {
      case Parsed.Success(_, _) => // Done
      case f: Parsed.Failure =>
        throw new IllegalArgumentException(s"Fastparse streaming error: ${f.trace().longMsg}")
    }
  }

  // Strict unescape complying with RFC 8259 Section 7 string constraints
  private def strictUnescape(text: String): String = {
    if (text.indexOf('\\') == -1) {
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
            case 'f'  => sb.append('\u000c')
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
