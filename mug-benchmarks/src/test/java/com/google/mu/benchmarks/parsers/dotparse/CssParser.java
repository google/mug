package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.define;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.fail;
import static com.google.common.labs.parse.Parser.first;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.zeroOrMore;

import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.AtWord;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.BracketsBlock;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.CurlyBracketsBlock;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Delim;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Dimension;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.FunctionBlock;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.HashWord;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Ident;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Num;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Percentage;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.SquareBracketsBlock;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Str;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.Url;
import com.google.mu.benchmarks.parsers.ast.css.Declaration;
import com.google.mu.benchmarks.parsers.ast.css.Rule;
import com.google.mu.benchmarks.parsers.ast.css.Rule.AtRule;
import com.google.mu.benchmarks.parsers.ast.css.Rule.AtRuleBody;
import com.google.mu.benchmarks.parsers.ast.css.Rule.DeclBody;
import com.google.mu.benchmarks.parsers.ast.css.Rule.QualifiedRule;
import com.google.mu.benchmarks.parsers.ast.css.Rule.RuleBody;
import com.google.mu.benchmarks.parsers.ast.css.Rule.SimpleBody;
import com.google.mu.benchmarks.parsers.ast.css.Stylesheet;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class CssParser {
  private static final Parser<String> IDENTIFIER =
      literally(sequence(
          one('-').optional(),
          anyOf(one("[a-zA-Z_]"), one('\\').then(chars(1))),
          anyOf(consecutive("[-a-zA-Z0-9_]"), one('\\').then(chars(1)))
              .zeroOrMore()))
      .source();

  private static final Parser<Str> STRING_LITERAL =
      anyOf(quotedByWithEscapes('"', '"', chars(1)), quotedByWithEscapes('\'', '\'', chars(1)))
          .map(Str::new);

  private static final Parser<Url> URL =
      anyOf(STRING_LITERAL.map(Str::value), consecutive("[^)]"))
          .orElse("")
          .between(caseInsensitive("url("), one(')'))
          .map(Url::new);
  
  private static final Parser<?> POINT_DIGITS = sequence(one('.'), digits());

  private static final Parser<Num> NUM =
      literally(sequence(
          one("[+-]").optional(),
          anyOf(sequence(digits(), POINT_DIGITS.optional()), POINT_DIGITS),
          sequence(caseInsensitive("e"), one("[+-]").optional(), digits())
              .optional()))
      .source()
      .map(s -> new Num(Double.parseDouble(s)));

  private static final Parser<ComponentValue> NUMERIC_VALUE =
      anyOf(
          sequence(NUM, IDENTIFIER, Num::asDimension),
          NUM.followedBy("%").map(Num::asPercentage),
          NUM);

  private static final Parser<HashWord> HASH =
      one('#').then(consecutive("[a-zA-Z0-9_-]")).map(HashWord::new);

  private static final Parser<AtWord> AT_WORD = one('@').then(IDENTIFIER).map(AtWord::new);

  private static final Parser<Delim> DELIM =
      anyOf(string("::"), one("[-#$*+,./:<>^~=]").source()).map(Delim::new);

  private static final Parser<ComponentValue> COMPONENT_VALUE = define(
      me -> {
        Parser<ComponentValue> bracketsBlock = me.zeroOrMore()
            .between("(", ")")
            .map(BracketsBlock::new);
        Parser<ComponentValue> curlyBracketsBlock = me.zeroOrMore()
            .between("{", "}")
            .map(CurlyBracketsBlock::new);
        Parser<ComponentValue> squareBracketsBlock = me.zeroOrMore()
            .between("[", "]")
            .map(SquareBracketsBlock::new);
        Parser<ComponentValue> identOrFunction = sequence(
            IDENTIFIER.map(Ident::new),
            me.zeroOrMore().between("(", ")").optional(),
            (name, args) -> args.map(name::toFunctionCall).orElse(name));
        Parser<ComponentValue> simpleToken =
            anyOf(URL, identOrFunction, NUMERIC_VALUE, STRING_LITERAL, HASH, AT_WORD, DELIM);
        return anyOf(simpleToken, bracketsBlock, curlyBracketsBlock, squareBracketsBlock);
      });

  private static final Parser<Declaration> DECLARATION =
      sequence(
          IDENTIFIER.followedBy(":"),
          COMPONENT_VALUE.suchThat(val -> !(val instanceof CurlyBracketsBlock), "not curly block").zeroOrMore(),
          Declaration::new)
        .optionallyFollowedBy(
            one('!').followedBy("important"),
            (decl, suffix) -> decl.important());

  private static final Parser<String> SELECTOR = consecutive("[^{}]").map(String::strip);

  private static final Parser<Stylesheet>.OrEmpty PARSER = Parser.<Rule>define(
      me -> {
        Parser<List<Declaration>> declarationList = DECLARATION
            .optionallyFollowedBy(";")
            .zeroOrMore()
            .between("{", "}");
        Parser<List<Rule>> ruleList = me.zeroOrMore().between("{", "}");
        Parser<QualifiedRule> qualifiedRule = sequence(SELECTOR, declarationList, QualifiedRule::new);
        Parser<AtRuleBody> atRuleBody = anyOf(
            one(';').thenReturn(new SimpleBody()),
            ruleList.map(RuleBody::new),
            declarationList.map(DeclBody::new));
        Parser<AtRule> atRule = sequence(
            one('@').then(IDENTIFIER),
            COMPONENT_VALUE.suchThat(val -> !(val instanceof CurlyBracketsBlock), "not curly block").zeroOrMore(),
            atRuleBody,
            (name, options, body) -> new AtRule(name, options, body.rules(), body.declarations()));
        return anyOf(atRule, qualifiedRule);
      }).zeroOrMore().map(Stylesheet::new);

  private static final Parser<?> IGNORED =
      anyOf(consecutive("[ \t\r\n]"), sequence(string("/*"), first("*/")));

  public static Stylesheet parse(String input) {
    return PARSER.parseSkipping(IGNORED, input);
  }
  
  private interface NumberSuffix {
    ComponentValue apply(Num number);
  }
}
