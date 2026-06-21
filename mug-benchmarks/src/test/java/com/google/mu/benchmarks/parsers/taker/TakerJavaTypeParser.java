package com.google.mu.benchmarks.parsers.taker;

import com.google.mu.benchmarks.parsers.javatype.*;
import com.google.mu.benchmarks.parsers.BenchmarkInputs;
import io.github.parseworks.taker.Result;
import io.github.parseworks.taker.Taker;
import io.github.parseworks.taker.parsers.Chars;
import io.github.parseworks.taker.parsers.Combinators;
import io.github.parseworks.taker.parsers.Lexical;
import io.github.parseworks.taker.parsers.Numeric;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TakerJavaTypeParser {

  private static final Set<String> PRIMITIVES = Set.of(
      "int", "double", "float", "long", "short", "byte", "char", "boolean", "void"
  );

  private static final Taker<Void> ws = Chars.chr(Character::isWhitespace).zeroOrMore().map(val -> (Void) null);

  private static <T> Taker<T> tok(Taker<T> p) {
    return p.thenSkip(ws);
  }

  private static Taker<Character> tok(char c) {
    return tok(Chars.chr(c));
  }

  private static Taker<String> tok(String s) {
    return tok(Lexical.string(s));
  }

  private static final Taker<String> PRIMITIVE_TYPE =
      Combinators.oneOf(
          PRIMITIVES.stream()
              .map(Lexical::string)
              .toArray(Taker[]::new)
      );

  private static final Taker<String> PACKAGE_SEGMENT =
      Chars.chr(Character::isLowerCase)
          .then(Chars.chr(Character::isLetterOrDigit).zeroOrMore())
          .map((first, rest) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(first);
            rest.forEach(sb::append);
            return sb.toString();
          });

  private static final Taker<String> TYPE_NAME =
      Combinators.oneOf(
          PRIMITIVE_TYPE,
          Chars.chr(Character::isUpperCase)
              .then(Chars.chr(Character::isLetterOrDigit).zeroOrMore())
              .map((first, rest) -> {
                StringBuilder sb = new StringBuilder();
                sb.append(first);
                rest.forEach(sb::append);
                return sb.toString();
              })
      );

  private static final Taker<String> INTEGER_PART =
      Chars.chr('-').optional()
          .then(Numeric.number)
          .map((minusOpt, numStr) -> {
            String sign = minusOpt.isPresent() ? "-" : "";
            return sign + numStr;
          });

  private static final Taker<Number> NUMBER_VAL =
      INTEGER_PART
          .then(Chars.chr('.').then(Numeric.number).map((dot, dec) -> "." + dec).optional())
          .map((integer, decOpt) -> {
            String decimal = decOpt.orElse("");
            if (decimal.isEmpty()) {
              return Integer.parseInt(integer);
            } else {
              return Double.parseDouble(integer + decimal);
            }
          });

  // Raw double-quoted string literal parser that delegates to BenchmarkInputs.unescape() for Unicode support!
  private static final Taker<String> STRING_LITERAL = Chars.chr('"')
      .then(
          Combinators.oneOf(
              Chars.chr(c -> c != '"' && c != '\\').map(String::valueOf),
              Chars.chr('\\').then(Combinators.any()).map((backslash, c) -> "\\" + c)
          ).zeroOrMore()
      )
      .thenSkip(Chars.chr('"'))
      .map((start, body) -> {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        body.forEach(sb::append);
        sb.append('"');
        return BenchmarkInputs.unescape(sb.toString());
      });

  private static final Taker<JavaType> PARSER = buildParser();

  @SuppressWarnings("unchecked")
  private static Taker<JavaType> buildParser() {
    Taker<JavaType> refType = Taker.ref();
    Taker<JavaAnnotation> refAnno = Taker.ref();
    Taker<AnnotationValue> refVal = Taker.ref();

    Taker<AnnotationValue> arrayVal = tok('{')
        .then(
            refVal.then(tok(',').then(refVal).map((comma, v) -> v).zeroOrMore())
                .map((first, rest) -> {
                  List<AnnotationValue> list = new ArrayList<>();
                  list.add(first);
                  list.addAll(rest);
                  return list;
                })
                .optional()
                .map(opt -> opt.orElse(List.of()))
        )
        .thenSkip(tok('}'))
        .map((start, list) -> new AnnotationValue.ArrayValue(list));

    Taker<AnnotationValue> valParser = Combinators.oneOf(
        STRING_LITERAL.map(AnnotationValue.StringValue::new),
        refType.then(tok('.').then(tok("class")).map((dot, cls) -> (Void) null))
            .map((t, cls) -> new AnnotationValue.ClassLiteralValue(t)),
        NUMBER_VAL.map(AnnotationValue.NumberValue::new),
        refAnno.map(AnnotationValue.AnnotationValueHolder::new),
        arrayVal
    );
    refVal.set(valParser);

    Taker<Map.Entry<String, AnnotationValue>> namedParamEntry =
        tok(Chars.chr(Character::isJavaIdentifierStart).then(Chars.chr(Character::isJavaIdentifierPart).zeroOrMore()).map((first, rest) -> {
          StringBuilder sb = new StringBuilder();
          sb.append(first);
          rest.forEach(sb::append);
          return sb.toString();
        }))
        .thenSkip(tok('='))
        .then(refVal)
        .map((name, val) -> new AbstractMap.SimpleImmutableEntry<>(name, val));

    Taker<Map<String, AnnotationValue>> namedParams = namedParamEntry
        .then(tok(',').then(namedParamEntry).map((comma, entry) -> entry).zeroOrMore())
        .map((first, rest) -> {
          Map<String, AnnotationValue> map = new HashMap<>();
          map.put(first.getKey(), first.getValue());
          for (Map.Entry<String, AnnotationValue> entry : rest) {
            map.put(entry.getKey(), entry.getValue());
          }
          return map;
        })
        .between(tok('('), tok(')'));

    Taker<Map<String, AnnotationValue>> singleParam = refVal
        .between(tok('('), tok(')')).map(val -> Map.of("value", val));

    Taker<Map<String, AnnotationValue>> params = Combinators.oneOf(namedParams, singleParam)
        .optional()
        .map((java.util.function.Function<java.util.Optional<Map<String, AnnotationValue>>, Map<String, AnnotationValue>>)
            opt -> opt.orElse(Map.of()));

    Taker<String> annotationName = PACKAGE_SEGMENT.thenSkip(tok('.')).zeroOrMore()
        .then(
            TYPE_NAME.then(tok('.').then(TYPE_NAME).map((dot, name) -> name).zeroOrMore())
                .map((first, rest) -> {
                  List<String> list = new ArrayList<>();
                  list.add(first);
                  list.addAll(rest);
                  return list;
                })
        )
        .map((pkg, types) -> {
          String pkgStr = String.join(".", pkg);
          String typeStr = String.join(".", types);
          return pkgStr.isEmpty() ? typeStr : pkgStr + "." + typeStr;
        });

    // Wrap the entire annoParser in tok() to ensure all trailing whitespace is consumed!
    Taker<JavaAnnotation> annoParser = tok(
        tok('@')
            .then(annotationName)
            .then(params)
            .map((start, name, p) -> new JavaAnnotation(name, p))
    );
    refAnno.set(annoParser);

    Taker<TypeSegment> typeSegment = refAnno.zeroOrMore()
        .then(tok(TYPE_NAME))
        .then(
            refType.then(tok(',').then(refType).map((comma, t) -> t).zeroOrMore())
                .map((first, rest) -> {
                  List<JavaType> list = new ArrayList<>();
                  list.add(first);
                  list.addAll(rest);
                  return list;
                })
                .between(tok('<'), tok('>'))
                .optional()
                .map(opt -> opt.orElse(List.of()))
        )
        .map((annos, name, args) -> new TypeSegment(annos, name, args));

    Taker<JavaType> root = PACKAGE_SEGMENT.thenSkip(tok('.')).zeroOrMore()
        .then(
            typeSegment.then(tok('.').then(typeSegment).map((dot, seg) -> seg).zeroOrMore())
                .map((first, rest) -> {
                  List<TypeSegment> list = new ArrayList<>();
                  list.add(first);
                  list.addAll(rest);
                  return list;
                })
        )
        .then(tok("[]").zeroOrMore().map(List::size))
        .map((pkg, segments, dims) -> new JavaType(pkg, segments, dims));

    refType.set(root);

    return ws.then(root).map((wsVal, r) -> r);
  }

  public static JavaType parse(String input) {
    Result<JavaType> result = PARSER.parseAll(input);
    if (result.matches() && result.input().isEof()) {
      return result.value();
    } else {
      throw new IllegalArgumentException("Parsing failed: " + result.toString());
    }
  }

  private TakerJavaTypeParser() {}
}
