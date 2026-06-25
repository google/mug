package com.google.mu.benchmarks.parsers.antlr4;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.TerminalNode;
import com.google.mu.benchmarks.parsers.javatype.*;
import java.util.*;
import java.util.stream.Collectors;

/** ANTLR4 Java Type parser implementation. */
public final class Antlr4JavaTypeParser {

  private final JavaTypeLexer lexer;
  private final JavaTypeParser parser;
  private final Visitor visitor;

  public Antlr4JavaTypeParser() {
    this.lexer = new JavaTypeLexer(CharStreams.fromString(""));
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

    this.parser = new JavaTypeParser(new CommonTokenStream(lexer));
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

  public JavaType parse(String input) {
    lexer.setInputStream(CharStreams.fromString(input));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    parser.setTokenStream(tokens);

    JavaTypeParser.EntryContext entry = parser.entry();
    return (JavaType) visitor.visit(entry.javaType());
  }

  private static class Visitor extends JavaTypeBaseVisitor<Object> {
    @Override
    public JavaType visitJavaType(JavaTypeParser.JavaTypeContext ctx) {
      List<String> pkg;
      if (ctx.packagePrefix() != null) {
        pkg = visitPackagePrefix(ctx.packagePrefix());
      } else {
        pkg = Collections.emptyList();
      }
      List<JavaTypeParser.TypeSegmentContext> segmentCtxs = ctx.typeSegment();
      List<TypeSegment> segments = new ArrayList<>(segmentCtxs.size());
      for (int i = 0; i < segmentCtxs.size(); i++) {
        segments.add(visitTypeSegment(segmentCtxs.get(i)));
      }
      int dimensions = ctx.arrayDimension().size();
      return new JavaType(pkg, segments, dimensions);
    }

    @Override
    public List<String> visitPackagePrefix(JavaTypeParser.PackagePrefixContext ctx) {
      List<JavaTypeParser.PackageSegmentContext> segmentCtxs = ctx.packageSegment();
      List<String> pkg = new ArrayList<>(segmentCtxs.size());
      for (int i = 0; i < segmentCtxs.size(); i++) {
        pkg.add(segmentCtxs.get(i).start.getText());
      }
      return pkg;
    }

    @Override
    public TypeSegment visitTypeSegment(JavaTypeParser.TypeSegmentContext ctx) {
      List<JavaTypeParser.AnnotationContext> annotationCtxs = ctx.annotation();
      List<JavaAnnotation> annotations = new ArrayList<>(annotationCtxs.size());
      for (int i = 0; i < annotationCtxs.size(); i++) {
        annotations.add(visitAnnotation(annotationCtxs.get(i)));
      }
      String typeName = ctx.typeName().start.getText();
      List<JavaType> typeArgs;
      if (ctx.typeArguments() != null) {
        typeArgs = visitTypeArguments(ctx.typeArguments());
      } else {
        typeArgs = Collections.emptyList();
      }
      return new TypeSegment(annotations, typeName, typeArgs);
    }

    @Override
    public List<JavaType> visitTypeArguments(JavaTypeParser.TypeArgumentsContext ctx) {
      List<JavaTypeParser.JavaTypeContext> typeCtxs = ctx.javaType();
      List<JavaType> typeArgs = new ArrayList<>(typeCtxs.size());
      for (int i = 0; i < typeCtxs.size(); i++) {
        typeArgs.add(visitJavaType(typeCtxs.get(i)));
      }
      return typeArgs;
    }

    @Override
    public JavaAnnotation visitAnnotation(JavaTypeParser.AnnotationContext ctx) {
      String name = ctx.annotationName().start.getText();
      Map<String, AnnotationValue> params;
      if (ctx.annotationParams() != null) {
        params = visitAnnotationParams(ctx.annotationParams());
      } else {
        params = Collections.emptyMap();
      }
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
      List<JavaTypeParser.NamedParamContext> params = ctx.namedParam();
      Map<String, AnnotationValue> map = new LinkedHashMap<>((int) (params.size() / 0.75f) + 1);
      for (int i = 0; i < params.size(); i++) {
        JavaTypeParser.NamedParamContext p = params.get(i);
        String key = ((TerminalNode) p.getChild(0)).getSymbol().getText();
        map.put(key, visitAnnotationValue(p.annotationValue()));
      }
      return map;
    }

    @Override
    public AnnotationValue visitAnnotationValue(JavaTypeParser.AnnotationValueContext ctx) {
      if (ctx.STRING_LITERAL() != null) {
        String literal = ctx.STRING_LITERAL().getSymbol().getText();
        String unescaped = unescapeString(literal.substring(1, literal.length() - 1));
        return new AnnotationValue.StringValue(unescaped);
      } else if (ctx.CLASS() != null) {
        JavaType type = visitJavaType(ctx.javaType());
        return new AnnotationValue.ClassLiteralValue(type);
      } else if (ctx.NUMBER_LITERAL() != null) {
        String text = ctx.NUMBER_LITERAL().getSymbol().getText();
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
        List<JavaTypeParser.AnnotationValueContext> valCtxs = ctx.annotationValue();
        List<AnnotationValue> list = new ArrayList<>(valCtxs.size());
        for (int i = 0; i < valCtxs.size(); i++) {
          list.add(visitAnnotationValue(valCtxs.get(i)));
        }
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
