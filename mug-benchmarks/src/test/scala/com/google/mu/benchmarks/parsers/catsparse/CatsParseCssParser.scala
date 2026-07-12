package com.google.mu.benchmarks.parsers.catsparse

import cats.parse.{Parser => P, Parser0 => P0, Numbers}
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue._
import com.google.mu.benchmarks.parsers.ast.css.Declaration
import com.google.mu.benchmarks.parsers.ast.css.Rule
import com.google.mu.benchmarks.parsers.ast.css.Rule._
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet
import scala.jdk.CollectionConverters._

object CatsParseCssParser {

  private val WHITESPACE: P[Unit] = P.charIn(" \t\r\n").void
  private val BLOCK_COMMENT: P[Unit] = P.string("/*").void *> P.until(P.string("*/")).void <* P.string("*/").void
  private val IGNORED: P0[Unit] = (WHITESPACE | BLOCK_COMMENT).rep0.void

  private def token[A](p: P[A]): P[A] = p <* IGNORED
  private def tokenChar(c: Char): P[Unit] = token(P.char(c).void)
  private def tokenStr(s: String): P[String] = token(P.string(s).as(s))

  private val identStart: P[Char] = P.charIn("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_") | (P.char('\\') *> P.anyChar)
  private val identPart: P[Char] = P.charIn("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_") | (P.char('\\') *> P.anyChar)
  private val identifier: P[String] = token(
    (P.char('-').?.with1 ~ identStart ~ identPart.rep0).string
  )

  private def quoted(quote: Char): P[String] = {
    val esc = P.char('\\') *> P.anyChar
    val literal = P.charWhere(c => c != quote && c != '\\')
    P.char(quote) *> (literal | esc).rep0.string <* P.char(quote)
  }

  private val rawString: P[String] = token(quoted('"') | quoted('\''))
  private val stringLiteral: P[Str] = rawString.map(new Str(_))

  private val urlContent: P0[String] = (rawString.backtrack | P.charWhere(_ != ')').rep.string.backtrack).?.map(_.getOrElse(""))
  private val url: P[Url] = (token(P.ignoreCase("url(")) ~ urlContent ~ tokenChar(')')).map {
    case ((_, content), _) => new Url(content)
  }

  private val sign = P.charIn("+-").?
  private val exponent = (P.charIn("eE") ~ P.charIn("+-").? ~ Numbers.digits).void.backtrack
  private val dotDigits = P.char('.') ~ Numbers.digits
  private val digitsDotDigits = Numbers.digits ~ P.char('.') ~ Numbers.digits

  private val numStr = (
    sign.with1 ~ (
      digitsDotDigits.backtrack |
      dotDigits.backtrack |
      Numbers.digits
    ) ~ exponent.?
  ).string

  private val numberVal: P[Double] = token(numStr.map(_.toDouble))

  private val numericValue: P[ComponentValue] = (
    numberVal ~ (tokenChar('%').as(true) | identifier.map(Some(_)).backtrack | P.pure(None))
  ).map {
    case (num, true) => new Percentage(num)
    case (num, Some(unit: String)) => new Dimension(num, unit)
    case (num, None) => new Num(num)
    case _ => throw new IllegalStateException("unreachable")
  }

  private val hash: P[HashWord] = (tokenChar('#') *> token(P.charIn("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_").rep.string)).map(new HashWord(_))
  private val atWord: P[AtWord] = (tokenChar('@') *> identifier).map(new AtWord(_))
  private val delim: P[Delim] = token(P.string("::").string.backtrack | P.charIn("-#$*+,./:<>^~=").string).map(new Delim(_))

  private val componentValue: P[ComponentValue] = P.recursive[ComponentValue] { recurse =>
    val bracketsBlock = (tokenChar('(') *> recurse.rep0 <* tokenChar(')')).map(list => new BracketsBlock(list.asJava))

    val curlyBracketsBlock = (tokenChar('{') *> recurse.rep0 <* tokenChar('}')).map(list => new CurlyBracketsBlock(list.asJava))

    val squareBracketsBlock = (tokenChar('[') *> recurse.rep0 <* tokenChar(']')).map(list => new SquareBracketsBlock(list.asJava))

    val identOrFunction = (
      identifier ~ (tokenChar('(') *> recurse.rep0 <* tokenChar(')')).?
    ).map {
      case (name, None) => new Ident(name)
      case (name, Some(args)) => new FunctionBlock(name, new BracketsBlock(args.asJava))
    }

    val simpleToken: P[ComponentValue] =
      atWord |
      hash.backtrack |
      url.backtrack |
      numericValue.backtrack |
      stringLiteral |
      identOrFunction |
      delim

    bracketsBlock.backtrack |
    curlyBracketsBlock.backtrack |
    squareBracketsBlock.backtrack |
    simpleToken
  }

  private val declCore = (identifier <* tokenChar(':')).backtrack ~ componentValue.rep0
  private val importantSuffix = (tokenChar('!') *> tokenStr("important")).?

  private val declaration = (declCore ~ importantSuffix).map {
    case ((name, values), Some(_)) => new Declaration(name, values.asJava, true)
    case ((name, values), None) => new Declaration(name, values.asJava, false)
  }

  private val selector = token(P.charWhere(c => c != '{' && c != '}').rep.string.map(_.strip))

  private val rule: P[Rule] = P.recursive[Rule] { recurse =>
    val declListCore = ((declaration <* tokenChar(';')).rep0 ~ declaration.?).map {
      case (list, opt) => list ++ opt.toList
    }
    val declarationList = (tokenChar('{') *> declListCore <* tokenChar('}')).map(_.asJava)

    val ruleList = recurse.rep0
      .between(tokenChar('{'), tokenChar('}'))
      .map(_.asJava)

    val qualifiedRule = (selector ~ declarationList).map {
      case (sel, decls) => new QualifiedRule(sel, decls)
    }

    val atRuleBody =
      tokenChar(';').as(new SimpleBody().asInstanceOf[AtRuleBody]) |
      declarationList.map(decls => new DeclBody(decls).asInstanceOf[AtRuleBody]).backtrack |
      ruleList.map(rules => new RuleBody(rules).asInstanceOf[AtRuleBody])

    val atRule = (
      (tokenChar('@') *> identifier)
        ~ componentValue.rep0
        ~ atRuleBody
    ).map { case ((name, options), body) =>
      new AtRule(name, options.asJava, body.rules(), body.declarations())
    }

    atRule.backtrack | qualifiedRule
  }

  private val stylesheet = rule.rep0.map(list => new Stylesheet(list.asJava))

  def parse(input: String): Stylesheet = {
    val fullParser = IGNORED *> stylesheet <* IGNORED
    fullParser.parse(input) match {
      case Right(("", res)) => res
      case Right((leftover, _)) => throw new IllegalArgumentException(s"Leftover input: $leftover")
      case Left(err) =>
        throw new IllegalArgumentException(err.toString)
    }
  }
}
