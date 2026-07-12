package com.google.mu.benchmarks.parsers.jparsec;

import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.Terminals;
import org.jparsec.Tokens;
import org.jparsec.pattern.Patterns;
import com.google.mu.benchmarks.parsers.ast.css.*;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.*;
import com.google.mu.benchmarks.parsers.ast.css.Rule.*;
import java.util.ArrayList;
import java.util.List;

public final class JparsecCssParser {

  // =========================================================================
  // 1. Terminals & Lexer Specification
  // =========================================================================

  private static final Terminals OPS = Terminals
      .operators(
          ":", ";", "!", "{", "}", "(", ")", ",", "%", "[", "]",
          "#", "@", "::", "-", "$", "*", "+", "/", "<", ">", "^", "~", "=", ".", "\\"
      );

  private static final Terminals WORDS = Terminals
      .caseSensitive(
          Scanners.pattern(Patterns.regex("-?([a-zA-Z_]|\\.)([a-zA-Z0-9_-]|\\.)*"), "identifier").source(),
          new String[]{"important"},
          new String[0]
      );

  private static final Terminals TERMS = OPS;

  private static final Parser<Object> TOKENIZER = Parsers.or(
      Scanners.pattern(Patterns.regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'"), "string").source().map(s -> Tokens.fragment(s, "string")),
      Scanners.pattern(Patterns.regex("[+-]?([0-9]+\\.[0-9]+|\\.[0-9]+|[0-9]+)([eE][+-]?[0-9]+)?"), "number").source().map(s -> Tokens.fragment(s, "number")),
      Scanners.pattern(Patterns.regex("(?i)url\\(([^)]*)\\)"), "url").source().map(s -> Tokens.fragment(s, "url")),
      WORDS.tokenizer(),
      OPS.tokenizer()
  );

  // =========================================================================
  // 2. Token-level Parsers (Matching Tokens from Tokenizer)
  // =========================================================================

  private static final Parser<String> SELECTOR_TOKEN = Parsers.token(token -> {
    if (token.value() instanceof Tokens.Fragment fragment) {
      String text = fragment.text();
      if (text.equals("{") || text.equals("}") || text.equals(";")) return null;
      return text;
    }
    String text = token.value().toString();
    if (text.equals("{") || text.equals("}") || text.equals(";")) return null;
    return text;
  });

  private static final Parser<String> SELECTOR = SELECTOR_TOKEN.many1().map(list -> {
    StringBuilder sb = new StringBuilder();
    for (String x : list) {
      if (x.equals(",")) {
        sb.append(", ");
      } else {
        sb.append(x);
      }
    }
    return sb.toString().trim();
  });

  private static final Parser<String> STRING = Terminals.fragment("string")
      .map(s -> s.substring(1, s.length() - 1));

  private static final Parser<Double> NUMBER = Terminals.fragment("number")
      .map(Double::parseDouble);

  private static final Parser<Url> URL = Terminals.fragment("url").map(text -> {
    String content = text.substring(4, text.length() - 1).strip();
    if ((content.startsWith("\"") && content.endsWith("\"")) ||
        (content.startsWith("'") && content.endsWith("'"))) {
      content = content.substring(1, content.length() - 1);
    }
    return new Url(content);
  });

  private static final Parser<Percentage> PERCENTAGE = Parsers.sequence(
      NUMBER,
      TERMS.token("%"),
      (n, op) -> new Percentage(n)
  );

  private static final Parser<Dimension> DIMENSION = Parsers.sequence(
      NUMBER,
      Terminals.Identifier.PARSER,
      Dimension::new
  );

  private static final Parser<ComponentValue> NUMERIC_VALUE = Parsers.or(
      PERCENTAGE,
      DIMENSION,
      NUMBER.map(Num::new)
  );

  private static final Parser<HashWord> hash = Parsers.sequence(
      TERMS.token("#"),
      Terminals.Identifier.PARSER,
      (op, id) -> new HashWord(id)
  );

  private static final Parser<AtWord> atWord = Parsers.sequence(
      TERMS.token("@"),
      Terminals.Identifier.PARSER,
      (op, id) -> new AtWord(id)
  );

  private static final Parser<Delim> delim = Parsers.or(
      TERMS.token("::").retn(new Delim("::")),
      Parsers.or(
          TERMS.token(":"), TERMS.token("-"), TERMS.token("#"), TERMS.token("$"), TERMS.token("*"), TERMS.token("+"),
          TERMS.token("/"), TERMS.token("<"), TERMS.token(">"), TERMS.token("^"), TERMS.token("~"),
          TERMS.token("="), TERMS.token(","), TERMS.token("."), TERMS.token("\\")
      ).map(token -> new Delim(token.toString()))
  );

  // =========================================================================
  // 3. Recursive Grammar Definitions
  // =========================================================================

  private static final Parser.Reference<ComponentValue> refComponentValue = Parser.newReference();

  private static final Parser<BracketsBlock> bracketsBlock = refComponentValue.lazy().many()
      .between(TERMS.token("("), TERMS.token(")"))
      .map(BracketsBlock::new);

  private static final Parser<CurlyBracketsBlock> curlyBracketsBlock = refComponentValue.lazy().many()
      .between(TERMS.token("{"), TERMS.token("}"))
      .map(CurlyBracketsBlock::new);

  private static final Parser<SquareBracketsBlock> squareBracketsBlock = refComponentValue.lazy().many()
      .between(TERMS.token("["), TERMS.token("]"))
      .map(SquareBracketsBlock::new);

  private static final Parser<FunctionBlock> functionBlock = Parsers.sequence(
      Terminals.Identifier.PARSER,
      refComponentValue.lazy().many().between(TERMS.token("("), TERMS.token(")")),
      (name, list) -> new FunctionBlock(name, new BracketsBlock(list))
  );

  private static final Parser<ComponentValue> simpleToken = Parsers.or(
      atWord,
      hash,
      URL,
      NUMERIC_VALUE,
      STRING.map(Str::new),
      Terminals.Identifier.PARSER.map(Ident::new),
      delim
  );

  private static final Parser<ComponentValue> componentValue = Parsers.or(
      bracketsBlock,
      curlyBracketsBlock,
      squareBracketsBlock,
      functionBlock,
      simpleToken
  );

  static {
    refComponentValue.set(componentValue);
  }

  private static final Parser<ComponentValue> declarationValue = Parsers.or(
      bracketsBlock,
      squareBracketsBlock,
      functionBlock,
      simpleToken
  );

  private static final Parser<Declaration> declaration = Parsers.sequence(
      Terminals.Identifier.PARSER.followedBy(TERMS.token(":")),
      declarationValue.many(),
      Parsers.sequence(TERMS.token("!"), WORDS.token("important")).optional().map(opt -> opt != null),
      Declaration::new
  );

  private static final Parser.Reference<Rule> refRule = Parser.newReference();

  private static final Parser<List<Declaration>> declarationList = declaration
      .followedBy(TERMS.token(";")).many()
      .next(list -> declaration.optional().map(opt -> {
        List<Declaration> copy = new ArrayList<>(list);
        if (opt != null) {
          copy.add(opt);
        }
        return copy;
      }))
      .between(TERMS.token("{"), TERMS.token("}"));

  private static final Parser<QualifiedRule> qualifiedRule = Parsers.sequence(
      SELECTOR,
      declarationList,
      QualifiedRule::new
  );

  private interface AtRuleBody {
    List<Rule> rules();
    List<Declaration> declarations();
  }

  private static final Parser<AtRuleBody> atRuleBody = Parsers.or(
      TERMS.token(";").retn(new AtRuleBody() {
        public List<Rule> rules() { return List.of(); }
        public List<Declaration> declarations() { return List.of(); }
      }),
      refRule.lazy().many().between(TERMS.token("{"), TERMS.token("}")).map(rules -> new AtRuleBody() {
        public List<Rule> rules() { return rules; }
        public List<Declaration> declarations() { return List.of(); }
      }),
      declarationList.map(decls -> new AtRuleBody() {
        public List<Rule> rules() { return List.of(); }
        public List<Declaration> declarations() { return decls; }
      })
  );

  private static final Parser<AtRule> atRule = Parsers.sequence(
      TERMS.token("@").next(Terminals.Identifier.PARSER),
      declarationValue.many(),
      atRuleBody,
      (name, options, body) -> new AtRule(name, options, body.rules(), body.declarations())
  );

  private static final Parser<Rule> rule = Parsers.or(atRule, qualifiedRule);

  static {
    refRule.set(rule);
  }

  private static final Parser<Stylesheet> stylesheet = rule.many().map(Stylesheet::new);
  private static final Parser<Stylesheet> parser = stylesheet.from(
      TOKENIZER,
      Parsers.or(Scanners.WHITESPACES, Scanners.blockComment("/*", "*/")).skipMany()
  );

  public static Stylesheet parse(String input) {
    if (input == null) {
      throw new NullPointerException("input is null");
    }
    return parser.parse(input);
  }

  private JparsecCssParser() {}
}
