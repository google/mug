package com.google.mu.benchmarks.parsers.fastparse

import com.google.mu.benchmarks.parsers.ast.css.ComponentValue
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue._
import com.google.mu.benchmarks.parsers.ast.css.Declaration
import com.google.mu.benchmarks.parsers.ast.css.Rule
import com.google.mu.benchmarks.parsers.ast.css.Rule._
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet
import scala.jdk.CollectionConverters._

object FastparseCssParser {
  import fastparse._

  // =========================================================================
  // 1. Whitespace & Comment Skipping
  // =========================================================================
  implicit val whitespace: fastparse.Whitespace = { ctx =>
    import fastparse.NoWhitespace._
    implicit val c = ctx
    def space[_: P] = CharsWhileIn(" \t\r\n", 0)
    def comment[_: P] = P( "/*" ~ (!"*/" ~ AnyChar).rep ~ "*/" )
    (space ~ (comment ~ space).rep).map(_ => ())
  }

  // =========================================================================
  // 2. Non-whitespace Lexer Rules
  // =========================================================================
  private object NoWsParser {
    import fastparse._, NoWhitespace._

    def identStart[_: P]: P[String] = P( CharPred(c => c.isLetter || c == '_').! | ("\\" ~ AnyChar.!) )
    def identPart[_: P]: P[String] = P( CharPred(c => c.isLetterOrDigit || c == '_' || c == '-').! | ("\\" ~ AnyChar.!) )
    def identifier[_: P]: P[String] = P( "-".?.! ~ identStart ~ identPart.rep ).map {
      case (optDash, first, rest) => optDash + first + rest.mkString
    }

    def esc[_: P]: P[String] = P( "\\" ~ AnyChar.! )
    def quoted[_: P](quote: Char): P[String] = P(
      quote.toString ~ (CharsWhile(c => c != quote && c != '\\').! | esc).rep ~ quote.toString
    ).map(_.mkString)

    def rawString[_: P]: P[String] = P( quoted('"') | quoted('\'') )

    def urlContent[_: P]: P[String] = P(
      rawString | CharsWhile(c => c != ')').!
    ).?.map(_.getOrElse(""))

    def sign[_: P]: P[String] = P( CharPred(c => c == '+' || c == '-').! )
    def digits[_: P]: P[String] = P( CharsWhileIn("0-9").! )
    def exponent[_: P]: P[String] = P( CharPred(c => c == 'e' || c == 'E').! ~ CharPred(c => c == '+' || c == '-').?.! ~ digits ).map {
      case (e, optSign, d) => e + optSign + d
    }
    def dotDigits[_: P]: P[String] = P( "." ~ digits ).map("." + _)
    def digitsDotDigits[_: P]: P[String] = P( digits ~ "." ~ digits ).map {
      case (d1, d2) => d1 + "." + d2
    }

    def numStr[_: P]: P[String] = P(
      sign.?.! ~ (digitsDotDigits | dotDigits | digits) ~ exponent.?.!
    ).map {
      case (optSign, num, optExp) => optSign + num + optExp
    }

    def numberVal[_: P]: P[Double] = P( numStr ).map(_.toDouble)
  }

  // =========================================================================
  // 3. Main CSS Parser Rules
  // =========================================================================
  private def url[_: P]: P[Url] = P(
    IgnoreCase("url(") ~ NoWsParser.urlContent ~ ")"
  ).map(new Url(_))

  private def percentage[_: P]: P[Percentage] = P( NoWsParser.numberVal ~ "%" ).map(new Percentage(_))

  private def dimension[_: P]: P[Dimension] = P( NoWsParser.numberVal ~ NoWsParser.identifier ).map {
    case (n, id) => new Dimension(n, id)
  }

  private def number[_: P]: P[Num] = P( NoWsParser.numberVal ).map(new Num(_))

  private def numericValue[_: P]: P[ComponentValue] = P( percentage | dimension | number )

  private def hash[_: P]: P[HashWord] = P( "#" ~ NoWsParser.identifier ).map(new HashWord(_))

  private def atWord[_: P]: P[AtWord] = P( "@" ~ NoWsParser.identifier ).map(new AtWord(_))

  private def delim[_: P]: P[Delim] = P( "::".! | CharPred("-#$*+,./:<>^~=".contains(_)).! ).map(new Delim(_))

  private def identVal[_: P]: P[Ident] = P( NoWsParser.identifier ).map(new Ident(_))

  private def bracketsBlock[_: P]: P[BracketsBlock] = P(
    "(" ~ componentValue.rep ~ ")"
  ).map(list => new BracketsBlock(list.asJava))

  private def curlyBracketsBlock[_: P]: P[CurlyBracketsBlock] = P(
    "{" ~ componentValue.rep ~ "}"
  ).map(list => new CurlyBracketsBlock(list.asJava))

  private def squareBracketsBlock[_: P]: P[SquareBracketsBlock] = P(
    "[" ~ componentValue.rep ~ "]"
  ).map(list => new SquareBracketsBlock(list.asJava))

  private def functionBlock[_: P]: P[FunctionBlock] = P(
    NoWsParser.identifier.filter(name => !name.equalsIgnoreCase("url")) ~ "(" ~ componentValue.rep ~ ")"
  ).map {
    case (name, list) => new FunctionBlock(name, new BracketsBlock(list.asJava))
  }

  private def stringLiteral[_: P]: P[Str] = P( NoWsParser.rawString ).map(new Str(_))

  private def simpleToken[_: P]: P[ComponentValue] = P(
    atWord | hash | url | numericValue | stringLiteral | identVal | delim
  )

  private def componentValue[_: P]: P[ComponentValue] = P(
    bracketsBlock | curlyBracketsBlock | squareBracketsBlock | functionBlock | simpleToken
  )

  private def declCore[_: P]: P[(String, Seq[ComponentValue])] = P(
    NoWsParser.identifier ~ ":" ~ componentValue.rep
  )

  private def importantSuffix[_: P]: P[Unit] = P( "!" ~ IgnoreCase("important") )

  private def declaration[_: P]: P[Declaration] = P(
    declCore ~ importantSuffix.?.!
  ).map {
    case (name, values, optImportant) =>
      new Declaration(name, values.asJava, optImportant.nonEmpty)
  }

  private def selector[_: P]: P[String] = P(
    CharsWhile(c => c != '{' && c != '}').!
  ).map(_.strip)

  private def declListCore[_: P]: P[Seq[Declaration]] = P(
    (declaration ~ ";").rep ~ declaration.?
  ).map {
    case (list, opt) => list ++ opt.toList
  }

  private def declarationList[_: P]: P[java.util.List[Declaration]] = P(
    "{" ~ declListCore ~ "}"
  ).map(_.asJava)

  private trait AtRuleBody {
    def rules(): java.util.List[Rule]
    def declarations(): java.util.List[Declaration]
  }

  private class SimpleBody extends AtRuleBody {
    override def rules() = java.util.Collections.emptyList()
    override def declarations() = java.util.Collections.emptyList()
  }

  private class DeclBody(decls: java.util.List[Declaration]) extends AtRuleBody {
    override def rules() = java.util.Collections.emptyList()
    override def declarations() = decls
  }

  private class RuleBody(r: java.util.List[Rule]) extends AtRuleBody {
    override def rules() = r
    override def declarations() = java.util.Collections.emptyList()
  }

  private def ruleList[_: P]: P[java.util.List[Rule]] = P(
    "{" ~ rule.rep ~ "}"
  ).map(_.asJava)

  private def qualifiedRule[_: P]: P[QualifiedRule] = P(
    selector ~ declarationList
  ).map {
    case (sel, decls) => new QualifiedRule(sel, decls)
  }

  private def atRuleBody[_: P]: P[AtRuleBody] = P(
    P(";").map(_ => new SimpleBody().asInstanceOf[AtRuleBody]) |
    declarationList.map(decls => new DeclBody(decls).asInstanceOf[AtRuleBody]) |
    ruleList.map(rules => new RuleBody(rules).asInstanceOf[AtRuleBody])
  )

  private def atRule[_: P]: P[AtRule] = P(
    "@" ~ NoWsParser.identifier ~ componentValue.rep ~ atRuleBody
  ).map {
    case (name, options, body) =>
      new AtRule(name, options.asJava, body.rules(), body.declarations())
  }

  private def rule[_: P]: P[Rule] = P( atRule | qualifiedRule )

  private def stylesheet[_: P]: P[Stylesheet] = P(
    rule.rep
  ).map(list => new Stylesheet(list.asJava))

  private def entry[_: P]: P[Stylesheet] = P( Start ~ stylesheet ~ End )

  /** Parses a CSS Stylesheet using Fastparse. */
  def parse(input: String): Stylesheet = {
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
