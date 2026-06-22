package com.google.mu.benchmarks.parsers.catsparse

import cats.parse.{Parser => P, Parser0 => P0, Numbers}
import cats.parse.strings.Json.delimited.{parser => officialJsonString}
import com.google.mu.benchmarks.parsers.dotparse.JsonValue
import com.google.mu.benchmarks.parsers.dotparse.JsonValue._
import scala.jdk.CollectionConverters._

/** Strictly RFC 8259-compliant Cats-parse-based JSON parser using official library primitives. */
object CatsParseJsonParser {

  private val skip: P0[Unit] = P.charIn(" \t\r\n").rep0.void

  private def token[A](p: P[A]): P[A] = p <* skip
  private def tokenStr(s: String): P[String] = token(P.string(s).as(s))

  // Strict RFC 8259 number parsing using official cats-parse library primitive
  private val jsonNumber: P[JsonNumber] = token(Numbers.jsonNumber).map { s =>
    new JsonNumber(s.toDouble)
  }

  // Double-quoted string parsing and unescaping using official cats-parse library primitive
  private val jsonString: P[JsonString] = token(officialJsonString).map(new JsonString(_))

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

  lazy val member: P[(String, JsonValue)] = (token(officialJsonString) <* tokenStr(":")) ~ jsonValue

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
}
