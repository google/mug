package com.google.mu.examples.mapflag.catsparse

import cats.parse.{Numbers, Parser => P, Parser0}

object CatsParseMapFlagParser {
  // .with1 bridges Parser0 (nullable) into P (non-empty); <* and *> discard one side.
  private val ws0: Parser0[Unit] = P.charsWhile0(_.isWhitespace).void
  private def sym(c: Char): P[Unit] = P.char(c) <* ws0
  private def items[A](p: P[A]): Parser0[List[A]] =
    (p.repSep(sym(','))
        <* sym(',').?)
      .?
      .map(_.fold(List.empty[A])(_.toList))

  private val quoted: P[String] =
    (P.char('\\') *> P.anyChar
        | P.charWhere(c => c != '"' && c != '\\'))
      .rep0
      .map(_.mkString)
      .with1.surroundedBy(P.char('"'))

  private val scalar: P[Any] =
    ((Numbers.signedIntString ~ (P.char('.') ~ Numbers.digits).?)
        .string
        .map(s => s.toIntOption.getOrElse(s.toDouble))
      | quoted) <* ws0

  private val map: P[Map[String, Any]] =
    ws0.with1 *> items(
      (P.charIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9') :+ '-' :+ '_')
          .rep
          .string
          <* ws0 <* sym('=')) ~
        (scalar
          | items(scalar)
              .with1.between(sym('['), sym(']')))
    ).with1.between(sym('{'), sym('}'))
      .flatMap(kvs =>
        if (kvs.map(_._1).distinct.size == kvs.size) P.pure(kvs.toMap)
        else P.failWith("Duplicate key"))

  def parse(input: String) = map.parseAll(input)
}
