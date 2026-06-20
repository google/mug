package com.google.mu.benchmarks.parsers.antlr4;

import org.antlr.v4.runtime.*;
import com.google.mu.benchmarks.parsers.javatype.*;
import java.util.*;
import java.util.stream.Collectors;

/** ANTLR4 Java Type parser implementation. */
public final class Antlr4JavaTypeParser {

  private Antlr4JavaTypeParser() {}

  public static JavaType parse(String input) {
    JavaTypeLexer lexer = new JavaTypeLexer(CharStreams.fromString(input));
    lexer.removeErrorListeners();
    lexer.addErrorListener(
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

    CommonTokenStream tokens = new CommonTokenStream(lexer);
    JavaTypeParser parser = new JavaTypeParser(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(
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

    JavaTypeParser.EntryContext entry = parser.entry();
    return (JavaType) new Visitor().visit(entry.javaType());
  }

  private static class Visitor extends JavaTypeBaseVisitor<Object> {
    @Override
    public JavaType visitJavaType(JavaTypeParser.JavaTypeContext ctx) {
      List<String> pkg =
          ctx.packagePrefix() != null
              ? visitPackagePrefix(ctx.packagePrefix())
              : Collections.emptyList();
      List<TypeSegment> segments =
          ctx.typeSegment().stream().map(this::visitTypeSegment).collect(Collectors.toList());
      int dimensions = ctx.arrayDimension().size();
      return new JavaType(pkg, segments, dimensions);
    }

    @Override
    public List<String> visitPackagePrefix(JavaTypeParser.PackagePrefixContext ctx) {
      return ctx.packageSegment().stream().map(RuleContext::getText).collect(Collectors.toList());
    }

    @Override
    public TypeSegment visitTypeSegment(JavaTypeParser.TypeSegmentContext ctx) {
      List<JavaAnnotation> annotations =
          ctx.annotation().stream().map(this::visitAnnotation).collect(Collectors.toList());
      String typeName = ctx.typeName().getText();
      List<JavaType> typeArgs =
          ctx.typeArguments() != null
              ? visitTypeArguments(ctx.typeArguments())
              : Collections.emptyList();
      return new TypeSegment(annotations, typeName, typeArgs);
    }

    @Override
    public List<JavaType> visitTypeArguments(JavaTypeParser.TypeArgumentsContext ctx) {
      return ctx.javaType().stream().map(this::visitJavaType).collect(Collectors.toList());
    }

    @Override
    public JavaAnnotation visitAnnotation(JavaTypeParser.AnnotationContext ctx) {
      String name = ctx.annotationName().getText();
      Map<String, AnnotationValue> params =
          ctx.annotationParams() != null
              ? visitAnnotationParams(ctx.annotationParams())
              : Collections.emptyMap();
      return new JavaAnnotation(name, params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, AnnotationValue> visitAnnotationParams(
        JavaTypeParser.AnnotationParamsContext ctx) {
      if (ctx.namedParams() != null) {
        return visitNamedParams(ctx.namedParams());
      } else {
        Map<String, AnnotationValue> map = new LinkedHashMap<>();
        map.put("value", visitAnnotationValue(ctx.singleParam().annotationValue()));
        return map;
      }
    }

    @Override
    public Map<String, AnnotationValue> visitNamedParams(JavaTypeParser.NamedParamsContext ctx) {
      Map<String, AnnotationValue> map = new LinkedHashMap<>();
      for (JavaTypeParser.NamedParamContext p : ctx.namedParam()) {
        map.put(p.getChild(0).getText(), visitAnnotationValue(p.annotationValue()));
      }
      return map;
    }

    @Override
    public AnnotationValue visitAnnotationValue(JavaTypeParser.AnnotationValueContext ctx) {
      if (ctx.STRING_LITERAL() != null) {
        String literal = ctx.STRING_LITERAL().getText();
        String unescaped = unescapeString(literal.substring(1, literal.length() - 1));
        return new AnnotationValue.StringValue(unescaped);
      } else if (ctx.CLASS() != null) {
        JavaType type = visitJavaType(ctx.javaType());
        return new AnnotationValue.ClassLiteralValue(type);
      } else if (ctx.NUMBER_LITERAL() != null) {
        String text = ctx.NUMBER_LITERAL().getText();
        Number number;
        if (text.contains(".")) {
          number = Double.parseDouble(text);
        } else {
          number = Integer.parseInt(text);
        }
        return new AnnotationValue.NumberValue(number);
      } else if (ctx.annotation() != null) {
        return new AnnotationValue.AnnotationValueHolder(visitAnnotation(ctx.annotation()));
      } else {
        List<AnnotationValue> list =
            ctx.annotationValue().stream().map(this::visitAnnotationValue).collect(Collectors.toList());
        return new AnnotationValue.ArrayValue(list);
      }
    }
  }

  private static String unescapeString(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char next = s.charAt(i + 1);
        if (next == 'u') {
          String hex = s.substring(i + 2, i + 6);
          sb.append((char) Integer.parseInt(hex, 16));
          i += 5;
        } else {
          switch (next) {
            case 'n':
              sb.append('\n');
              break;
            case 't':
              sb.append('\t');
              break;
            case 'r':
              sb.append('\r');
              break;
            case 'f':
              sb.append('\f');
              break;
            case 'b':
              sb.append('\b');
              break;
            default:
              sb.append(next);
              break;
          }
          i++;
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
