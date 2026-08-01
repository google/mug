package com.google.mu.benchmarks.parsers.parboiled;

import com.google.mu.benchmarks.parsers.ast.css.*;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.*;
import com.google.mu.benchmarks.parsers.ast.css.Rule.*;
import org.parboiled.BaseParser;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.parserunners.BasicParseRunner;
import org.parboiled.support.ParsingResult;

import java.util.ArrayList;
import java.util.List;

public class ParboiledCssParser extends BaseParser<Object> {

  public static Stylesheet parse(String input) {
    if (input == null) {
      throw new NullPointerException("input is null");
    }
    ParboiledCssParser parser = Parboiled.createParser(ParboiledCssParser.class);
    ParsingResult<Object> result = new BasicParseRunner<>(parser.stylesheet()).run(input);
    if (!result.matched) {
      throw new RuntimeException("Parse failed:\n" + org.parboiled.errors.ErrorUtils.printParseErrors(result));
    }
    return (Stylesheet) result.resultValue;
  }

  // =========================================================================
  // Lexer Rules & Grammar Rules
  // =========================================================================

  public Rule stylesheet() {
    return Sequence(
        whitespace(),
        push(new ArrayList<com.google.mu.benchmarks.parsers.ast.css.Rule>()),
        ZeroOrMore(
            rule(),
            addRuleToList()
        ),
        EOI,
        wrapStylesheet()
    );
  }

  public Rule rule() {
    return FirstOf(
        atRule(),
        qualifiedRule()
    );
  }

  public Rule qualifiedRule() {
    return Sequence(
        selector(),
        declarationList(),
        push(createQualifiedRule())
    );
  }

  public Rule selector() {
    return Sequence(
        OneOrMore(NoneOf("{};")),
        push(match().trim())
    );
  }

  public Rule declarationList() {
    return Sequence(
        Symbol("{"),
        push(new ArrayList<Declaration>()),
        ZeroOrMore(
            declaration(),
            addDeclarationToList(),
            Symbol(";")
        ),
        Optional(
            declaration(),
            addDeclarationToList(),
            Optional(Symbol(";"))
        ),
        Symbol("}"),
        push(pop())
    );
  }

  public Rule declarationValue() {
    return FirstOf(
        bracketsBlock(),
        squareBracketsBlock(),
        urlVal(),
        identOrFunction(),
        simpleToken()
    );
  }

  public Rule declaration() {
    return Sequence(
        identString(),
        Symbol(":"),
        push(new ArrayList<ComponentValue>()),
        ZeroOrMore(
            declarationValue(),
            addComponentValueToList()
        ),
        FirstOf(
            Sequence(Symbol("!"), IgnoreCase("important"), whitespace(), push(true)),
            push(false)
        ),
        push(createDeclaration())
    );
  }

  public Rule atRule() {
    return Sequence(
        '@',
        identString(),
        push(new ArrayList<ComponentValue>()),
        ZeroOrMore(
            componentValue(),
            addComponentValueToList()
        ),
        atRuleBody(),
        push(createAtRule())
    );
  }

  public Rule atRuleBody() {
    return FirstOf(
        Sequence(
            Symbol(";"),
            push(new ArrayList<com.google.mu.benchmarks.parsers.ast.css.Rule>()),
            push(new ArrayList<Declaration>())
        ),
        Sequence(
            Symbol("{"),
            push(new ArrayList<com.google.mu.benchmarks.parsers.ast.css.Rule>()),
            ZeroOrMore(
                rule(),
                addRuleToList()
            ),
            Symbol("}"),
            push(new ArrayList<Declaration>())
        ),
        Sequence(
            declarationList(),
            push(new ArrayList<com.google.mu.benchmarks.parsers.ast.css.Rule>()),
            swapAtRuleBodyStack()
        )
    );
  }

  public Rule componentValue() {
    return FirstOf(
        bracketsBlock(),
        curlyBracketsBlock(),
        squareBracketsBlock(),
        urlVal(),
        identOrFunction(),
        simpleToken()
    );
  }

  public Rule simpleToken() {
    return FirstOf(
        atWordVal(),
        hashVal(),
        numericValue(),
        stringVal(),
        delim()
    );
  }

  public Rule bracketsBlock() {
    return Sequence(
        Symbol("("),
        push(new ArrayList<ComponentValue>()),
        ZeroOrMore(
            componentValue(),
            addComponentValueToList()
        ),
        Symbol(")"),
        push(new BracketsBlock((List<ComponentValue>) pop()))
    );
  }

  public Rule curlyBracketsBlock() {
    return Sequence(
        Symbol("{"),
        push(new ArrayList<ComponentValue>()),
        ZeroOrMore(
            componentValue(),
            addComponentValueToList()
        ),
        Symbol("}"),
        push(new CurlyBracketsBlock((List<ComponentValue>) pop()))
    );
  }

  public Rule squareBracketsBlock() {
    return Sequence(
        Symbol("["),
        push(new ArrayList<ComponentValue>()),
        ZeroOrMore(
            componentValue(),
            addComponentValueToList()
        ),
        Symbol("]"),
        push(new SquareBracketsBlock((List<ComponentValue>) pop()))
    );
  }

  public Rule identOrFunction() {
    return Sequence(
        identString(),
        FirstOf(
            Sequence(
                Symbol("("),
                push(new ArrayList<ComponentValue>()),
                ZeroOrMore(
                    componentValue(),
                    addComponentValueToList()
                ),
                Symbol(")"),
                push(createFunctionBlock())
            ),
            push(new Ident((String) pop()))
        )
      );
  }

  public Rule functionBlock() {
    return Sequence(
        identString(),
        Symbol("("),
        push(new ArrayList<ComponentValue>()),
        ZeroOrMore(
            componentValue(),
            addComponentValueToList()
        ),
        Symbol(")"),
        push(createFunctionBlock())
    );
  }

  public Rule atWordVal() {
    return Sequence(
        '@',
        identString(),
        push(new AtWord((String) pop()))
    );
  }

  public Rule hashVal() {
    return Sequence(
        '#',
        identString(),
        push(new HashWord((String) pop()))
    );
  }

  public Rule urlVal() {
    return Sequence(
        IgnoreCase("url("),
        whitespace(),
        FirstOf(
            Sequence(stringVal(), push(new Url(((Str) pop()).value()))),
            Sequence(
                ZeroOrMore(NoneOf(")")),
                push(new Url(match().trim()))
            )
        ),
        Symbol(")")
    );
  }

  public Rule numericValue() {
    return Sequence(
        numberVal(),
        FirstOf(
            Sequence(
                '%',
                whitespace(),
                push(new Percentage((Double) pop()))
            ),
            Sequence(
                identString(),
                push(createDimension())
            ),
            push(new Num((Double) pop()))
        )
    );
  }

  public Rule stringVal() {
    return Sequence(
        FirstOf(
            Sequence('"', ZeroOrMore(FirstOf(Sequence('\\', ANY), NoneOf("\"\\\r\n"))), '"'),
            Sequence('\'', ZeroOrMore(FirstOf(Sequence('\\', ANY), NoneOf("'\\\r\n"))), '\'')
        ),
        push(new Str(stripQuotes(match()))),
        whitespace()
    );
  }

  public Rule identComponent() {
    return Sequence(identString(), push(new Ident((String) pop())));
  }

  public Rule delim() {
    return Sequence(
        FirstOf(
            "::",
            AnyOf("-#$*+,./:<>^~=.")
        ),
        push(new Delim(match())),
        whitespace()
    );
  }

  public Rule identString() {
    return Sequence(
        Sequence(
            Optional('-'),
            FirstOf(
                CharRange('a', 'z'), CharRange('A', 'Z'), '_',
                Sequence('\\', ANY)
            ),
            ZeroOrMore(
                FirstOf(
                    CharRange('a', 'z'), CharRange('A', 'Z'), CharRange('0', '9'), '_', '-',
                    Sequence('\\', ANY)
                )
            )
        ),
        push(match()),
        whitespace()
    );
  }

  public Rule numberVal() {
    return Sequence(
        Sequence(
            Optional(AnyOf("+-")),
            FirstOf(
                Sequence(OneOrMore(Digit()), '.', OneOrMore(Digit())),
                Sequence('.', OneOrMore(Digit())),
                OneOrMore(Digit())
            ),
            Optional(AnyOf("eE"), Optional(AnyOf("+-")), OneOrMore(Digit()))
        ),
        push(Double.parseDouble(match())),
        whitespace()
    );
  }

  public Rule Digit() {
    return CharRange('0', '9');
  }

  public Rule Symbol(String s) {
    return Sequence(s, whitespace());
  }

  public Rule whitespace() {
    return ZeroOrMore(
        FirstOf(
            OneOrMore(AnyOf(" \t\r\n")),
            comment()
        )
    );
  }

  public Rule comment() {
    return Sequence("/*", ZeroOrMore(Sequence(TestNot("*/"), ANY)), "*/");
  }

  // =========================================================================
  // Action Helpers
  // =========================================================================

  boolean appendSelectorPart() {
    String part = (String) pop();
    StringBuilder sb = (StringBuilder) peek();
    sb.append(", ").append(part);
    return true;
  }

  boolean addDeclarationToList() {
    Declaration decl = (Declaration) pop();
    @SuppressWarnings("unchecked")
    List<Declaration> list = (List<Declaration>) peek();
    list.add(decl);
    return true;
  }

  boolean addComponentValueToList() {
    ComponentValue val = (ComponentValue) pop();
    @SuppressWarnings("unchecked")
    List<ComponentValue> list = (List<ComponentValue>) peek();
    list.add(val);
    return true;
  }

  boolean addRuleToList() {
    com.google.mu.benchmarks.parsers.ast.css.Rule r = (com.google.mu.benchmarks.parsers.ast.css.Rule) pop();
    @SuppressWarnings("unchecked")
    List<com.google.mu.benchmarks.parsers.ast.css.Rule> list = (List<com.google.mu.benchmarks.parsers.ast.css.Rule>) peek();
    list.add(r);
    return true;
  }

  boolean wrapStylesheet() {
    @SuppressWarnings("unchecked")
    List<com.google.mu.benchmarks.parsers.ast.css.Rule> rules = (List<com.google.mu.benchmarks.parsers.ast.css.Rule>) pop();
    push(new Stylesheet(rules));
    return true;
  }

  Declaration createDeclaration() {
    Boolean isImportant = (Boolean) pop();
    @SuppressWarnings("unchecked")
    List<ComponentValue> values = (List<ComponentValue>) pop();
    String property = (String) pop();
    return new Declaration(property, values, isImportant);
  }

  AtRule createAtRule() {
    @SuppressWarnings("unchecked")
    List<Declaration> decls = (List<Declaration>) pop();
    @SuppressWarnings("unchecked")
    List<com.google.mu.benchmarks.parsers.ast.css.Rule> rules = (List<com.google.mu.benchmarks.parsers.ast.css.Rule>) pop();
    @SuppressWarnings("unchecked")
    List<ComponentValue> options = (List<ComponentValue>) pop();
    String name = (String) pop();
    return new AtRule(name, options, rules, decls);
  }

  QualifiedRule createQualifiedRule() {
    @SuppressWarnings("unchecked")
    List<Declaration> decls = (List<Declaration>) pop();
    String sel = (String) pop();
    return new QualifiedRule(sel, decls);
  }

  FunctionBlock createFunctionBlock() {
    @SuppressWarnings("unchecked")
    List<ComponentValue> list = (List<ComponentValue>) pop();
    String name = (String) pop();
    return new FunctionBlock(name, new BracketsBlock(list));
  }

  Dimension createDimension() {
    String id = (String) pop();
    Double num = (Double) pop();
    return new Dimension(num, id);
  }

  boolean swapAtRuleBodyStack() {
    Object rules = pop();
    Object decls = pop();
    push(rules);
    push(decls);
    return true;
  }

  String stripQuotes(String s) {
    return s.substring(1, s.length() - 1);
  }
}
