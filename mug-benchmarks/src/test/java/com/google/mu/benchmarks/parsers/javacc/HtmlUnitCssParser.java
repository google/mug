package com.google.mu.benchmarks.parsers.javacc;

import org.htmlunit.cssparser.dom.CSSStyleSheetImpl;
import org.htmlunit.cssparser.dom.CSSStyleRuleImpl;
import org.htmlunit.cssparser.dom.CSSMediaRuleImpl;
import org.htmlunit.cssparser.dom.CSSImportRuleImpl;
import org.htmlunit.cssparser.dom.CSSCharsetRuleImpl;
import org.htmlunit.cssparser.dom.AbstractCSSRuleImpl;
import org.htmlunit.cssparser.dom.Property;
import org.htmlunit.cssparser.dom.CSSValueImpl;
import org.htmlunit.cssparser.dom.MediaListImpl;
import org.htmlunit.cssparser.parser.media.MediaQuery;
import org.htmlunit.cssparser.parser.LexicalUnit;
import org.htmlunit.cssparser.parser.LexicalUnit.LexicalUnitType;
import org.htmlunit.cssparser.parser.CSSOMParser;
import org.htmlunit.cssparser.parser.InputSource;
import org.htmlunit.cssparser.util.ThrowCssExceptionErrorHandler;
import com.google.mu.benchmarks.parsers.ast.css.*;
import com.google.mu.benchmarks.parsers.ast.css.ComponentValue.*;
import com.google.mu.benchmarks.parsers.ast.css.Rule.*;
import java.io.StringReader;
import java.util.*;

public final class HtmlUnitCssParser {

  private final CSSOMParser parser;

  public HtmlUnitCssParser() {
    this.parser = new CSSOMParser();
    this.parser.setErrorHandler(new ThrowCssExceptionErrorHandler());
  }

  public Stylesheet parse(String input) {
    Objects.requireNonNull(input);
    try {
      InputSource source = new InputSource(new StringReader(input));
      CSSStyleSheetImpl sheet = parser.parseStyleSheet(source, null);
      List<Rule> rules = new ArrayList<>();
      for (AbstractCSSRuleImpl r : sheet.getCssRules().getRules()) {
        Rule translated = translateRule(r);
        if (translated != null) {
          rules.add(translated);
        }
      }
      return new Stylesheet(rules);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static Rule translateRule(AbstractCSSRuleImpl rule) {
    if (rule instanceof CSSStyleRuleImpl) {
      CSSStyleRuleImpl styleRule = (CSSStyleRuleImpl) rule;
      String selector = styleRule.getSelectorText().strip();
      List<Declaration> declarations = new ArrayList<>();
      for (Property p : styleRule.getStyle().getProperties()) {
        declarations.add(new Declaration(
            p.getName(),
            translateCSSValue(p.getValue()),
            p.isImportant()));
      }
      return new QualifiedRule(selector, declarations);
    } else if (rule instanceof CSSMediaRuleImpl) {
      CSSMediaRuleImpl mediaRule = (CSSMediaRuleImpl) rule;
      List<ComponentValue> options = translateMediaList(mediaRule.getMediaList());
      List<Rule> rules = new ArrayList<>();
      for (AbstractCSSRuleImpl subRule : mediaRule.getCssRules().getRules()) {
        Rule r = translateRule(subRule);
        if (r != null) {
          rules.add(r);
        }
      }
      return new AtRule("media", options, rules, List.of());
    } else if (rule instanceof CSSImportRuleImpl) {
      CSSImportRuleImpl importRule = (CSSImportRuleImpl) rule;
      List<ComponentValue> options = new ArrayList<>();
      options.add(new Url(importRule.getHref()));
      options.addAll(translateMediaList(importRule.getMedia()));
      return new AtRule("import", options, List.of(), List.of());
    } else if (rule instanceof CSSCharsetRuleImpl) {
      CSSCharsetRuleImpl charsetRule = (CSSCharsetRuleImpl) rule;
      return new AtRule("charset", List.of(new Str(charsetRule.getEncoding())), List.of(), List.of());
    }
    return null;
  }

  private static List<ComponentValue> translateMediaList(MediaListImpl mediaList) {
    if (mediaList == null) return List.of();
    List<ComponentValue> options = new ArrayList<>();
    for (int i = 0; i < mediaList.getLength(); i++) {
      if (i > 0) {
        options.add(new Delim(","));
      }
      MediaQuery mq = mediaList.mediaQuery(i);
      if (mq.isOnly()) {
        options.add(new Ident("only"));
      }
      if (mq.isNot()) {
        options.add(new Ident("not"));
      }
      if (mq.getMedia() != null && !mq.getMedia().isEmpty()) {
        options.add(new Ident(mq.getMedia()));
      }
      for (Property p : mq.getProperties()) {
        List<ComponentValue> inner = new ArrayList<>();
        inner.add(new Ident(p.getName()));
        if (p.getValue() != null) {
          inner.add(new Delim(":"));
          inner.addAll(translateCSSValue(p.getValue()));
        }
        options.add(new BracketsBlock(inner));
      }
    }
    return options;
  }

  private static List<ComponentValue> translateCSSValue(CSSValueImpl cssValue) {
    if (cssValue == null) return List.of();
    Object obj = cssValue.getValue();
    if (obj instanceof LexicalUnit) {
      return translateLexicalUnits((LexicalUnit) obj);
    } else if (obj instanceof List) {
      List<ComponentValue> list = new ArrayList<>();
      for (Object item : (List<?>) obj) {
        if (item instanceof CSSValueImpl) {
          list.addAll(translateCSSValue((CSSValueImpl) item));
        }
      }
      return list;
    } else if (obj != null) {
      return List.of(new Ident(cssValue.getCssText()));
    }
    return List.of();
  }

  private static List<ComponentValue> translateLexicalUnits(LexicalUnit lu) {
    if (lu == null) return List.of();
    List<ComponentValue> list = new ArrayList<>();
    while (lu != null) {
      ComponentValue val = translateLexicalUnit(lu);
      if (val != null) {
        list.add(val);
      }
      lu = lu.getNextLexicalUnit();
    }
    return list;
  }

  private static ComponentValue translateLexicalUnit(LexicalUnit lu) {
    switch (lu.getLexicalUnitType()) {
      case IDENT:
        return new Ident(lu.getStringValue());
      case STRING_VALUE:
        return new Str(lu.getStringValue());
      case INTEGER:
        return new Num(lu.getIntegerValue());
      case REAL:
        return new Num(lu.getDoubleValue());
      case PERCENTAGE:
        return new Percentage(lu.getDoubleValue());
      case URI:
        return new Url(lu.getStringValue());
      case FUNCTION:
        return new FunctionBlock(
            lu.getFunctionName(),
            new BracketsBlock(translateLexicalUnits(lu.getParameters())));
      case OPERATOR_COMMA:
        return new Delim(",");
      case OPERATOR_SLASH:
        return new Delim("/");
      case OPERATOR_PLUS:
        return new Delim("+");
      case OPERATOR_MINUS:
        return new Delim("-");
      case OPERATOR_MULTIPLY:
        return new Delim("*");
      case OPERATOR_TILDE:
        return new Delim("~");
      case EM:
      case REM:
      case EX:
      case CH:
      case VW:
      case VH:
      case VMIN:
      case VMAX:
      case PIXEL:
      case INCH:
      case CENTIMETER:
      case MILLIMETER:
      case POINT:
      case PICA:
      case DEGREE:
      case RADIAN:
      case TURN:
      case SECOND:
      case MILLISECOND:
      case DIMENSION:
        return new Dimension(lu.getDoubleValue(), lu.getDimensionUnitText());
      default:
        return new Ident(lu.toString());
    }
  }
}
