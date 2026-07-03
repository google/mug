package com.google.mu.benchmarks.parsers.catsparse

import cats.parse.{Parser => P, Parser0 => P0, Numbers}
import cats.parse.strings.Json.delimited.{parser => officialJsonString}
import com.google.mu.benchmarks.parsers.dotparse.JsonValue
import com.google.mu.benchmarks.parsers.dotparse.JsonValue._
import scala.jdk.CollectionConverters._

object CatsParseJsonParser {
  private val STANDARD_SKIP: P0[Unit] =
    P.charIn(" \t\r\n").rep0.void

  private val WHITESPACE: P[Unit] =
    P.charIn(" \t\r\n").void

  private val LINE_COMMENT: P[Unit] =
    P.string("//").void *> P.charsWhile(c => c != '\n').?.void <* P.char('\n').?.void

  private val BLOCK_COMMENT: P[Unit] =
    P.string("/*").void *> P.until(P.string("*/")).void <* P.string("*/").void

  private val SKIP_WITH_COMMENTS: P0[Unit] =
    (P.charIn(" \t\r\n/").peek *> P.oneOf(WHITESPACE :: LINE_COMMENT :: BLOCK_COMMENT :: Nil).rep0).?.void

  // Literal copy of self.scala's parser structure, parameterized by whitespace skipper
  private def makeParser(whitespaces0: P0[Unit]): P[JsonValue] = {
    P.recursive[JsonValue] { recurse =>
      val pnull = P.string("null").as(JsonNull.INSTANCE)
      val bool = P.string("true").as(JsonBoolean.TRUE).orElse(P.string("false").as(JsonBoolean.FALSE))
      val justStr = officialJsonString
      val str = justStr.map(new JsonString(_))
      val num = Numbers.jsonNumber.map { s =>
        if (s.contains(".") || s.contains("e") || s.contains("E")) {
          new JsonNumber(s.toDouble)
        } else {
          new JsonNumber(s.toLong.toDouble)
        }
      }

      val listSep: P[Unit] =
        P.char(',').soft.surroundedBy(whitespaces0).void

      def rep0[A](pa: P[A]): P0[List[A]] =
        pa.repSep0(listSep).surroundedBy(whitespaces0)

      val list = rep0(recurse).with1
        .between(P.char('['), P.char(']'))
        .map { vs => new JsonArray(vs.asJava) }

      val kv: P[(String, JsonValue)] =
        justStr ~ (P.char(':').surroundedBy(whitespaces0) *> recurse)

      val obj = rep0(kv).with1
        .between(P.char('{'), P.char('}'))
        .map { vs =>
          val map = new java.util.LinkedHashMap[String, JsonValue]()
          vs.foreach { case (k, v) => map.put(k, v) }
          new JsonObject(map)
        }

      P.oneOf(str :: num :: list :: obj :: bool :: pnull :: Nil)
    }
  }

  private val STANDARD_PARSER = makeParser(STANDARD_SKIP).between(STANDARD_SKIP, STANDARD_SKIP ~ P.end)
  private val COMMENTS_PARSER = makeParser(SKIP_WITH_COMMENTS).between(SKIP_WITH_COMMENTS, SKIP_WITH_COMMENTS ~ P.end)

  // Main entry points
  def parse(input: String): JsonValue = {
    STANDARD_PARSER.parseAll(input) match {
      case Right(value) => value
      case Left(error) =>
        throw new IllegalArgumentException(s"Cats-parse error: $error")
    }
  }

  def parseWithComments(input: String): JsonValue = {
    COMMENTS_PARSER.parseAll(input) match {
      case Right(value) => value
      case Left(error) =>
        throw new IllegalArgumentException(s"Cats-parse error: $error")
    }
  }
}
