package com.google.mu.benchmarks.parsers.antlr4;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import com.google.mu.benchmarks.parsers.ast.css.*;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.*;
import com.google.mu.benchmarks.parsers.ast.css.Rule.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Antlr4CssParser {

  private final css3Lexer lexer;
  private final css3Parser parser;
  private final Visitor visitor;

  public Antlr4CssParser() {
    this.lexer = new css3Lexer(CharStreams.fromString(""));
    this.lexer.removeErrorListeners();
    this.lexer.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            throw new IllegalArgumentException("Lexing error: " + msg);
          }
        });

    this.parser = new css3Parser(new CommonTokenStream(lexer));
    this.parser.removeErrorListeners();
    this.parser.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              Recognizer<?, ?> recognizer,
              Object offendingSymbol,
              int line,
              int charPositionInLine,
              String msg,
              RecognitionException e) {
            throw new IllegalArgumentException("Parsing error: " + msg);
          }
        });
    this.visitor = new Visitor();
  }

  public Stylesheet parse(String input) {
    lexer.setInputStream(CharStreams.fromString(input));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    parser.setTokenStream(tokens);

    css3Parser.StylesheetContext stylesheetCtx = parser.stylesheet();
    return visitor.visitStylesheet(stylesheetCtx);
  }

  private static class Visitor extends css3ParserBaseVisitor<Object> {
    private static final Pattern DIMENSION_PATTERN = Pattern.compile("^([+-]?(?:[0-9]*\\.[0-9]+|[0-9]+)(?:[eE][+-]?[0-9]+)?)(.*)$");

    @Override
    public Stylesheet visitStylesheet(css3Parser.StylesheetContext ctx) {
      List<Rule> rules = new ArrayList<>();
      
      if (ctx.charset() != null) {
        for (css3Parser.CharsetContext c : ctx.charset()) {
          Rule rule = (Rule) visit(c);
          if (rule != null) rules.add(rule);
        }
      }
      if (ctx.imports() != null) {
        for (css3Parser.ImportsContext i : ctx.imports()) {
          Rule rule = (Rule) visit(i);
          if (rule != null) rules.add(rule);
        }
      }
      if (ctx.namespace_() != null) {
        for (css3Parser.Namespace_Context n : ctx.namespace_()) {
          Rule rule = (Rule) visit(n);
          if (rule != null) rules.add(rule);
        }
      }
      if (ctx.nestedStatement() != null) {
        for (css3Parser.NestedStatementContext n : ctx.nestedStatement()) {
          Rule rule = (Rule) visit(n);
          if (rule != null) rules.add(rule);
        }
      }
      return new Stylesheet(rules);
    }

    @Override
    public Rule visitGoodCharset(css3Parser.GoodCharsetContext ctx) {
      String stringText = stripQuotes(ctx.String_().getText());
      return new AtRule("charset", List.of(new Str(stringText)), List.of(), List.of());
    }

    @Override
    public Rule visitGoodImport(css3Parser.GoodImportContext ctx) {
      ComponentValue option;
      if (ctx.String_() != null) {
        option = new Str(stripQuotes(ctx.String_().getText()));
      } else {
        option = new Url(stripUrl(ctx.url().getText()));
      }
      return new AtRule("import", List.of(option), List.of(), List.of());
    }

    @Override
    public Rule visitGoodNamespace(css3Parser.GoodNamespaceContext ctx) {
      List<ComponentValue> options = new ArrayList<>();
      if (ctx.namespacePrefix() != null) {
        options.add(new Ident(ctx.namespacePrefix().getText()));
      }
      if (ctx.String_() != null) {
        options.add(new Str(stripQuotes(ctx.String_().getText())));
      } else if (ctx.url() != null) {
        options.add(new Url(stripUrl(ctx.url().getText())));
      }
      return new AtRule("namespace", options, List.of(), List.of());
    }

    @Override
    public Rule visitMedia(css3Parser.MediaContext ctx) {
      List<ComponentValue> options = visitMediaQueryList(ctx.mediaQueryList());
      List<Rule> rules = new ArrayList<>();
      if (ctx.groupRuleBody() != null && ctx.groupRuleBody().nestedStatement() != null) {
        for (css3Parser.NestedStatementContext n : ctx.groupRuleBody().nestedStatement()) {
          Rule rule = (Rule) visit(n);
          if (rule != null) rules.add(rule);
        }
      }
      return new AtRule("media", options, rules, List.of());
    }

    @Override
    public List<ComponentValue> visitMediaQueryList(css3Parser.MediaQueryListContext ctx) {
      if (ctx == null) return Collections.emptyList();
      List<ComponentValue> options = new ArrayList<>();
      for (int i = 0; i < ctx.mediaQuery().size(); i++) {
        if (i > 0) {
          options.add(new Delim(","));
        }
        options.addAll(visitMediaQuery(ctx.mediaQuery(i)));
      }
      return options;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ComponentValue> visitMediaQuery(css3Parser.MediaQueryContext ctx) {
      List<ComponentValue> list = new ArrayList<>();
      for (ParseTree child : ctx.children) {
        Object val = visit(child);
        if (val instanceof ComponentValue) {
          list.add((ComponentValue) val);
        } else if (val instanceof List) {
          list.addAll((List<ComponentValue>) val);
        } else {
          String txt = child.getText().strip();
          if (!txt.isEmpty()) {
            list.add(new Ident(txt));
          }
        }
      }
      return list;
    }

    @Override
    public ComponentValue visitMediaType(css3Parser.MediaTypeContext ctx) {
      return visitIdent(ctx.ident());
    }

    @Override
    public ComponentValue visitMediaExpression(css3Parser.MediaExpressionContext ctx) {
      List<ComponentValue> inner = new ArrayList<>();
      inner.add(new Ident(ctx.mediaFeature().getText().strip()));
      if (ctx.expr() != null) {
        inner.add(new Delim(":"));
        inner.addAll(visitExpr(ctx.expr()));
      }
      return new BracketsBlock(inner);
    }

    @Override
    public Rule visitNestedStatement(css3Parser.NestedStatementContext ctx) {
      return (Rule) visit(ctx.getChild(0));
    }

    @Override
    public Rule visitKnownRuleset(css3Parser.KnownRulesetContext ctx) {
      String selector = ctx.selectorGroup().getText().strip();
      List<Declaration> decls = visitDeclarationList(ctx.declarationList());
      return new QualifiedRule(selector, decls);
    }

    @Override
    public List<Declaration> visitDeclarationList(css3Parser.DeclarationListContext ctx) {
      if (ctx == null) return Collections.emptyList();
      List<Declaration> decls = new ArrayList<>();
      for (css3Parser.DeclarationContext dCtx : ctx.declaration()) {
        Declaration d = (Declaration) visit(dCtx);
        if (d != null) {
          decls.add(d);
        }
      }
      return decls;
    }

    @Override
    public Declaration visitKnownDeclaration(css3Parser.KnownDeclarationContext ctx) {
      String property = ctx.property_().getText();
      List<ComponentValue> values = visitExpr(ctx.expr());
      boolean important = ctx.prio() != null;
      return new Declaration(property, values, important);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<ComponentValue> visitExpr(css3Parser.ExprContext ctx) {
      if (ctx == null) return Collections.emptyList();
      List<ComponentValue> values = new ArrayList<>();
      for (ParseTree child : ctx.children) {
        Object val = visit(child);
        if (val instanceof ComponentValue) {
          values.add((ComponentValue) val);
        } else if (val instanceof List) {
          values.addAll((List<ComponentValue>) val);
        }
      }
      return values;
    }

    @Override
    public ComponentValue visitKnownTerm(css3Parser.KnownTermContext ctx) {
      return (ComponentValue) visit(ctx.getChild(0));
    }

    @Override
    public ComponentValue visitNumber(css3Parser.NumberContext ctx) {
      double sign = 1.0;
      if (ctx.getChildCount() > 1) {
        if (ctx.getChild(0).getText().equals("-")) {
          sign = -1.0;
        }
      }
      TerminalNode numNode = (TerminalNode) ctx.getChild(ctx.getChildCount() - 1);
      return new Num(sign * Double.parseDouble(numNode.getText()));
    }

    @Override
    public ComponentValue visitPercentage(css3Parser.PercentageContext ctx) {
      double sign = 1.0;
      if (ctx.getChildCount() > 1) {
        if (ctx.getChild(0).getText().equals("-")) {
          sign = -1.0;
        }
      }
      TerminalNode pctNode = (TerminalNode) ctx.getChild(ctx.getChildCount() - 1);
      String pctText = pctNode.getText();
      double val = Double.parseDouble(pctText.substring(0, pctText.length() - 1));
      return new Percentage(sign * val);
    }

    @Override
    public ComponentValue visitDimension(css3Parser.DimensionContext ctx) {
      double sign = 1.0;
      if (ctx.getChildCount() > 1) {
        if (ctx.getChild(0).getText().equals("-")) {
          sign = -1.0;
        }
      }
      TerminalNode dimNode = (TerminalNode) ctx.getChild(ctx.getChildCount() - 1);
      String dimText = dimNode.getText();
      Matcher m = DIMENSION_PATTERN.matcher(dimText);
      if (m.matches()) {
        double val = Double.parseDouble(m.group(1));
        String unit = m.group(2);
        return new Dimension(sign * val, unit);
      }
      throw new IllegalArgumentException("Invalid dimension: " + dimText);
    }

    @Override
    public ComponentValue visitUrl(css3Parser.UrlContext ctx) {
      String text = ctx.getText();
      return new Url(stripUrl(text));
    }

    @Override
    public ComponentValue visitHexcolor(css3Parser.HexcolorContext ctx) {
      String text = ctx.Hash().getText();
      return new HashWord(text.substring(1));
    }

    @Override
    public ComponentValue visitFunction_(css3Parser.Function_Context ctx) {
      String funcText = ctx.Function_().getText();
      String name = funcText.substring(0, funcText.length() - 1);
      List<ComponentValue> args = visitExpr(ctx.expr());
      return new FunctionBlock(name, new BracketsBlock(args));
    }

    @Override
    public ComponentValue visitIdent(css3Parser.IdentContext ctx) {
      return new Ident(ctx.getText());
    }

    @Override
    public ComponentValue visitGoodOperator(css3Parser.GoodOperatorContext ctx) {
      String text = ctx.getChild(0).getText();
      if (text.equals(" ") || text.isEmpty()) return null;
      return new Delim(text);
    }

    @Override
    public ComponentValue visitUnknownTerm(css3Parser.UnknownTermContext ctx) {
      return new Ident(ctx.getText());
    }

    private static String stripQuotes(String s) {
      if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
        return s.substring(1, s.length() - 1);
      }
      return s;
    }

    private static String stripUrl(String s) {
      if (s.toLowerCase().startsWith("url(") && s.endsWith(")")) {
        s = s.substring(4, s.length() - 1).strip();
      }
      return stripQuotes(s);
    }
  }
}
