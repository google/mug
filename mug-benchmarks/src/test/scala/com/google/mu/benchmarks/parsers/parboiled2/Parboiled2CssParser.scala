package com.google.mu.benchmarks.parsers.parboiled2

import org.parboiled2._
import com.google.mu.benchmarks.parsers.ast.css._
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue._
import com.google.mu.benchmarks.parsers.ast.css.Rule._
import scala.jdk.CollectionConverters._

class Parboiled2CssParser(val input: ParserInput) extends Parser {

  def comment = rule { "/*" ~ zeroOrMore(!("*/") ~ ANY) ~ "*/" }
  def whitespace = rule { quiet(zeroOrMore(oneOrMore(anyOf(" \t\r\n")) | comment)) }
  def Symbol(s: String) = rule { str(s) ~ whitespace }

  def digit = rule { CharPredicate.Digit }
  def numberStr = rule {
    capture(
      optional(anyOf("+-")) ~ (
        (oneOrMore(digit) ~ "." ~ oneOrMore(digit)) |
        ("." ~ oneOrMore(digit)) |
        oneOrMore(digit)
      ) ~ optional(anyOf("eE") ~ optional(anyOf("+-")) ~ oneOrMore(digit))
    )
  }
  def numberVal: Rule1[Double] = rule {
    numberStr ~ whitespace ~> ((s: String) => s.toDouble)
  }

  def identStart = rule { CharPredicate.Alpha | "_" | ("\\" ~ ANY) }
  def identPart = rule { CharPredicate.AlphaNum | "_" | "-" | ("\\" ~ ANY) }
  def identString: Rule1[String] = rule {
    capture(optional("-") ~ identStart ~ zeroOrMore(identPart)) ~ whitespace
  }

  def delim: Rule1[Delim] = rule {
    capture("::" | anyOf("-#$*+,./:<>^~=.")) ~ whitespace ~> (new Delim(_))
  }

  def hashVal: Rule1[HashWord] = rule {
    "#" ~ identString ~> (new HashWord(_))
  }

  def atWordVal: Rule1[AtWord] = rule {
    "@" ~ identString ~> (new AtWord(_))
  }

  def stringVal: Rule1[Str] = rule {
    (
      ("\"" ~ capture(zeroOrMore(("\\" ~ ANY) | noneOf("\"\\\r\n"))) ~ "\"") |
      ("'" ~ capture(zeroOrMore(("\\" ~ ANY) | noneOf("'\\\r\n"))) ~ "'")
    ) ~ whitespace ~> ((s: String) => new Str(s))
  }

  def urlVal: Rule1[Url] = rule {
    CharPredicate("uU") ~ CharPredicate("rR") ~ CharPredicate("lL") ~ "(" ~ whitespace ~ (
      (stringVal ~> ((s: Str) => new Url(s.value))) |
      (capture(zeroOrMore(noneOf(")")))) ~> ((s: String) => new Url(s.trim))
    ) ~ Symbol(")")
  }

  def numericValue: Rule1[ComponentValue] = rule {
    numberVal ~ (
      ("%" ~ whitespace ~> ((num: Double) => new Percentage(num).asInstanceOf[ComponentValue])) |
      (identString ~> ((num: Double, unit: String) => new Dimension(num, unit).asInstanceOf[ComponentValue])) |
      (MATCH ~> ((num: Double) => new Num(num).asInstanceOf[ComponentValue]))
    )
  }

  def bracketsBlock: Rule1[BracketsBlock] = rule {
    Symbol("(") ~ zeroOrMore(componentValue) ~ Symbol(")") ~> ((list: Seq[ComponentValue]) => new BracketsBlock(list.asJava))
  }

  def curlyBracketsBlock: Rule1[CurlyBracketsBlock] = rule {
    Symbol("{") ~ zeroOrMore(componentValue) ~ Symbol("}") ~> ((list: Seq[ComponentValue]) => new CurlyBracketsBlock(list.asJava))
  }

  def squareBracketsBlock: Rule1[SquareBracketsBlock] = rule {
    Symbol("[") ~ zeroOrMore(componentValue) ~ Symbol("]") ~> ((list: Seq[ComponentValue]) => new SquareBracketsBlock(list.asJava))
  }

  def identOrFunction: Rule1[ComponentValue] = rule {
    identString ~ (
      (Symbol("(") ~ zeroOrMore(componentValue) ~ Symbol(")") ~> ((name: String, args: Seq[ComponentValue]) => new FunctionBlock(name, new BracketsBlock(args.asJava)).asInstanceOf[ComponentValue])) |
      (MATCH ~> ((name: String) => new Ident(name).asInstanceOf[ComponentValue]))
    )
  }

  def simpleToken: Rule1[ComponentValue] = rule {
    atWordVal | hashVal | numericValue | stringVal | identOrFunction | delim
  }

  def componentValue: Rule1[ComponentValue] = rule {
    bracketsBlock | curlyBracketsBlock | squareBracketsBlock | urlVal | simpleToken
  }

  def declarationValue: Rule1[ComponentValue] = rule {
    bracketsBlock | squareBracketsBlock | urlVal | simpleToken
  }

  def declaration: Rule1[Declaration] = rule {
    identString ~ Symbol(":") ~ zeroOrMore(declarationValue) ~ (
      (Symbol("!") ~ CharPredicate("iI") ~ CharPredicate("mM") ~ CharPredicate("pP") ~ CharPredicate("oO") ~ CharPredicate("rR") ~ CharPredicate("tT") ~ CharPredicate("aA") ~ CharPredicate("nN") ~ CharPredicate("tT") ~ whitespace ~> (() => true)) |
      (MATCH ~> (() => false))
    ) ~> ((prop: String, vals: Seq[ComponentValue], imp: Boolean) => new Declaration(prop, vals.asJava, imp))
  }

  def declarationList: Rule1[java.util.List[Declaration]] = rule {
    Symbol("{") ~ zeroOrMore(declaration).separatedBy(Symbol(";")) ~ optional(Symbol(";")) ~ Symbol("}") ~> ((decls: Seq[Declaration]) => decls.asJava)
  }

  def selector: Rule1[String] = rule {
    capture(oneOrMore(noneOf("{};"))) ~> ((s: String) => s.trim)
  }

  def qualifiedRule: Rule1[QualifiedRule] = rule {
    selector ~ declarationList ~> ((sel: String, decls: java.util.List[Declaration]) => new QualifiedRule(sel, decls))
  }

  trait AtRuleBody {
    def rules: java.util.List[com.google.mu.benchmarks.parsers.ast.css.Rule]
    def declarations: java.util.List[Declaration]
  }
  class SimpleBody extends AtRuleBody {
    def rules = java.util.Collections.emptyList()
    def declarations = java.util.Collections.emptyList()
  }
  class DeclBody(val declarations: java.util.List[Declaration]) extends AtRuleBody {
    def rules = java.util.Collections.emptyList()
  }
  class RuleBody(val rules: java.util.List[com.google.mu.benchmarks.parsers.ast.css.Rule]) extends AtRuleBody {
    def declarations = java.util.Collections.emptyList()
  }

  def atRuleBody: Rule1[AtRuleBody] = rule {
    (Symbol(";") ~> (() => new SimpleBody().asInstanceOf[AtRuleBody])) |
    (declarationList ~> ((decls: java.util.List[Declaration]) => new DeclBody(decls).asInstanceOf[AtRuleBody])) |
    (Symbol("{") ~ zeroOrMore(cssRule) ~ Symbol("}") ~> ((rules: Seq[com.google.mu.benchmarks.parsers.ast.css.Rule]) => new RuleBody(rules.asJava).asInstanceOf[AtRuleBody]))
  }

  def atRule: Rule1[AtRule] = rule {
    "@" ~ identString ~ zeroOrMore(declarationValue) ~ atRuleBody ~> {
      (name: String, options: Seq[ComponentValue], body: AtRuleBody) =>
        new AtRule(name, options.asJava, body.rules, body.declarations)
    }
  }

  def cssRule: Rule1[com.google.mu.benchmarks.parsers.ast.css.Rule] = rule {
    atRule | qualifiedRule
  }

  def stylesheet: Rule1[Stylesheet] = rule {
    whitespace ~ zeroOrMore(cssRule) ~ EOI ~> ((rules: Seq[com.google.mu.benchmarks.parsers.ast.css.Rule]) => new Stylesheet(rules.asJava))
  }
}

object Parboiled2CssParser {
  def parse(input: String): Stylesheet = {
    val parser = new Parboiled2CssParser(input)
    parser.stylesheet.run() match {
      case scala.util.Success(value) => value
      case scala.util.Failure(error: ParseError) =>
        throw new IllegalArgumentException(parser.formatError(error))
      case scala.util.Failure(error) =>
        throw error
    }
  }
}
