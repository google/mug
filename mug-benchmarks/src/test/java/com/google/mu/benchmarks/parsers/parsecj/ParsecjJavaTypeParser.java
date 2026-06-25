package com.google.mu.benchmarks.parsers.parsecj;

import static org.javafp.parsecj.Combinators.*;
import static org.javafp.parsecj.Text.*;

import com.google.mu.benchmarks.parsers.javatype.*;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.javafp.parsecj.Parser;
import org.javafp.parsecj.Reply;
import org.javafp.parsecj.input.Input;
import org.javafp.data.IList;

public final class ParsecjJavaTypeParser {

  private static final Set<String> PRIMITIVES = Set.of(
      "int", "double", "float", "long", "short", "byte", "char", "boolean", "void"
  );

  private static <T> Parser<Character, T> tok(Parser<Character, T> p) {
    return p.bind(x -> wspaces.then(retn(x)));
  }

  private static Parser<Character, String> tok(String s) {
    return tok(string(s));
  }

  private static final Parser<Character, String> PRIMITIVE_TYPE =
      choice(
          string("int"), string("double"), string("float"), string("long"),
          string("short"), string("byte"), string("char"), string("boolean"),
          string("void")
      );

  private static final Parser<Character, String> PACKAGE_SEGMENT =
      regex("[a-z][a-zA-Z0-9]*").bind(s ->
          PRIMITIVES.contains(s) ? satisfy((Character c) -> false).then(retn("")) : retn(s)
      );

  private static final Parser<Character, String> TYPE_NAME =
      or(
          PRIMITIVE_TYPE,
          regex("[A-Z][a-zA-Z0-9]*")
      );

  private static final Parser<Character, Number> NUMBER_VAL =
      regex("-?[0-9]+(\\.[0-9]+)?").map(s -> {
        if (s.contains(".")) {
          return Double.parseDouble(s);
        } else {
          return Integer.parseInt(s);
        }
      });

  private static final Parser<Character, String> STRING_LITERAL =
      regex("\"([^\"\\\\]|\\\\.)*\"").map(BenchmarkInputs::unescape);

  private static final Parser<Character, JavaType> PARSER = buildParser();

  @SuppressWarnings("unchecked")
  private static Parser<Character, JavaType> buildParser() {
    Parser.Ref<Character, JavaType> refType = Parser.ref();
    Parser.Ref<Character, JavaAnnotation> refAnno = Parser.ref();
    Parser.Ref<Character, AnnotationValue> refVal = Parser.ref();

    Parser<Character, AnnotationValue> arrayVal =
        tok(chr('{'))
            .then(refVal.sepBy(tok(chr(','))))
            .bind((IList<AnnotationValue> list) -> tok(chr('}')).then(retn(new AnnotationValue.ArrayValue(convertList(list)))));

    Parser<Character, AnnotationValue> valParser =
        choice(
            STRING_LITERAL.map(AnnotationValue.StringValue::new),
            refType.bind(t -> tok(chr('.')).then(tok("class")).then(retn(new AnnotationValue.ClassLiteralValue(t)))).attempt(),
            NUMBER_VAL.map(AnnotationValue.NumberValue::new),
            refAnno.map(AnnotationValue.AnnotationValueHolder::new),
            arrayVal
        );
    refVal.set(valParser);

    Parser<Character, Map<String, AnnotationValue>> namedParams =
        tok(regex("[a-zA-Z_][a-zA-Z0-9_]*"))
            .bind((String name) -> tok(chr('=')).then(refVal).map((AnnotationValue val) -> (Map.Entry<String, AnnotationValue>) new AbstractMap.SimpleImmutableEntry<>(name, val)))
            .sepBy(tok(chr(',')))
            .map((IList<Map.Entry<String, AnnotationValue>> list) -> {
              Map<String, AnnotationValue> map = new HashMap<>();
              for (Map.Entry<String, AnnotationValue> entry : list) {
                map.put(entry.getKey(), entry.getValue());
              }
              return map;
            })
            .between(tok(chr('(')), tok(chr(')')));

    Parser<Character, Map<String, AnnotationValue>> singleParam =
        refVal.between(tok(chr('(')), tok(chr(')')))
            .map(val -> Map.of("value", val));

    Parser<Character, Map<String, AnnotationValue>> params =
        or(or(namedParams.attempt(), singleParam), retn(Map.of()));

    Parser<Character, String> annotationName =
        PACKAGE_SEGMENT.bind(pkgSeg -> tok(chr('.')).then(retn(pkgSeg))).many()
            .bind((IList<String> pkg) -> TYPE_NAME.bind(first -> tok(chr('.')).then(TYPE_NAME).many().map(rest -> {
              List<String> types = new ArrayList<>();
              types.add(first);
              for (String s : rest) {
                types.add(s);
              }
              return types;
            })).map((List<String> types) -> {
              List<String> pkgList = convertList(pkg);
              String pkgStr = String.join(".", pkgList);
              String typeStr = String.join(".", types);
              return pkgStr.isEmpty() ? typeStr : pkgStr + "." + typeStr;
            }));

    Parser<Character, JavaAnnotation> annoParser =
        tok(
            tok(chr('@'))
                .then(annotationName)
                .bind(name -> params.map(p -> new JavaAnnotation(name, p)))
        );
    refAnno.set(annoParser);

    // Explicit generic typeArgs parser with robust backtracking
    Parser<Character, List<JavaType>> typeArgs =
        or(
            tok(chr('<'))
                .then(
                    refType.bind(first -> tok(chr(',')).then(refType).many().map(rest -> {
                      List<JavaType> args = new ArrayList<>();
                      args.add(first);
                      for (JavaType t : rest) {
                        args.add(t);
                      }
                      return args;
                    }))
                )
                .bind(args -> tok(chr('>')).then(retn(args)))
                .attempt(),
            retn(List.of())
        );

    Parser<Character, TypeSegment> typeSegment =
        refAnno.many()
            .bind((IList<JavaAnnotation> annos) -> tok(TYPE_NAME)
                .bind((String name) -> typeArgs.map((List<JavaType> args) -> new TypeSegment(convertList(annos), name, args))));

    Parser<Character, JavaType> root =
        PACKAGE_SEGMENT.bind(pkgSeg -> tok(chr('.')).then(retn(pkgSeg))).attempt().many()
            .bind((IList<String> pkg) -> typeSegment.bind(first -> tok(chr('.')).then(typeSegment).attempt().many().map(rest -> {
              List<TypeSegment> segments = new ArrayList<>();
              segments.add(first);
              for (TypeSegment s : rest) {
                segments.add(s);
              }
              return segments;
            }))
            .bind((List<TypeSegment> segments) -> tok("[]").attempt().many()
                .map((IList<String> dims) -> new JavaType(convertList(pkg), segments, dims.size()))));

    refType.set(root);

    return wspaces.then(root).between(retn(null), eof());
  }

  private static <T> List<T> convertList(IList<T> ilist) {
    List<T> list = new ArrayList<>();
    for (T x : ilist) {
      list.add(x);
    }
    return list;
  }

  public static JavaType parse(String input) {
    try {
      Reply<Character, JavaType> reply = PARSER.parse(Input.of(input));
      if (reply.isOk()) {
        return reply.getResult();
      } else {
        throw new IllegalArgumentException("Parsing failed: " + reply.toString());
      }
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  private ParsecjJavaTypeParser() {}
}
