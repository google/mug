package com.google.mu.benchmarks.parsers.petitparser;

import com.google.mu.benchmarks.parsers.javatype.*;
import org.petitparser.context.Context;
import org.petitparser.context.Result;
import org.petitparser.parser.Parser;
import org.petitparser.parser.combinators.SettableParser;
import org.petitparser.parser.primitive.CharacterParser;
import org.petitparser.parser.primitive.StringParser;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Production-grade PetitParser implementation for Java Type Signatures. */
public final class PetitParserJavaTypeParser {

  private static final Set<String> PRIMITIVES = Set.of(
      "int", "double", "float", "long", "short", "byte", "char", "boolean", "void"
  );

  private static final class NonPrimitiveParser extends Parser {
    private final Parser delegate;

    NonPrimitiveParser(Parser delegate) {
      this.delegate = delegate;
    }

    @Override
    public Result parseOn(Context context) {
      Result result = delegate.parseOn(context);
      if (result.isSuccess()) {
        String val = (String) result.get();
        if (PRIMITIVES.contains(val)) {
          return context.failure("package segment cannot be a primitive: " + val, context.getPosition());
        }
      }
      return result;
    }

    @Override
    public Parser copy() {
      return new NonPrimitiveParser(delegate);
    }
  }

  private static <T> List<T> getElements(List<Object> separatedList) {
    if (separatedList == null) return List.of();
    List<T> elements = new ArrayList<>();
    for (int i = 0; i < separatedList.size(); i += 2) {
      elements.add((T) separatedList.get(i));
    }
    return elements;
  }

  private static Parser primitiveType() {
    return StringParser.of("int")
        .or(StringParser.of("double"))
        .or(StringParser.of("float"))
        .or(StringParser.of("long"))
        .or(StringParser.of("short"))
        .or(StringParser.of("byte"))
        .or(StringParser.of("char"))
        .or(StringParser.of("boolean"))
        .or(StringParser.of("void"))
        .trim();
  }

  private static Parser typeName() {
    Parser uppercase = CharacterParser.pattern("A-Z");
    Parser letterOrDigit = CharacterParser.pattern("a-zA-Z0-9");
    Parser identifier = uppercase.seq(letterOrDigit.star()).flatten().trim();
    return primitiveType().or(identifier);
  }

  private static Parser packageSegment() {
    Parser lowercase = CharacterParser.pattern("a-z");
    Parser letterOrDigit = CharacterParser.pattern("a-zA-Z0-9");
    Parser identifier = lowercase.seq(letterOrDigit.star()).flatten().trim();
    return new NonPrimitiveParser(identifier);
  }

  private static Parser packagePrefix() {
    return packageSegment().seq(CharacterParser.of('.').trim()).pick(0).star();
  }

  private static Parser stringLiteral() {
    return PetitParserShowdown.StringFixture.PARSER.trim();
  }

  private static Parser integerPart() {
    Parser digits = CharacterParser.digit().plus().flatten();
    return CharacterParser.of('-').optional().seq(digits).flatten();
  }

  private static Parser numberVal() {
    Parser dec = CharacterParser.of('.').seq(CharacterParser.digit().plus()).flatten().optional();
    return integerPart().seq(dec).map((List<Object> x) -> {
      String integer = (String) x.get(0);
      String decimal = (String) x.get(1);
      if (decimal == null) {
        return Integer.parseInt(integer);
      } else {
        return Double.parseDouble(integer + decimal);
      }
    }).trim();
  }

  private static final Parser PARSER = buildParser();

  private static Parser buildParser() {
    SettableParser javaType = CharacterParser.none().settable();
    SettableParser javaAnnotation = CharacterParser.none().settable();
    SettableParser annotationValue = CharacterParser.none().settable();

    // 1. Annotation Value
    Parser stringVal = stringLiteral().map(AnnotationValue.StringValue::new);
    Parser classLiteralVal = javaType.seq(StringParser.of(".class").trim()).pick(0).map(AnnotationValue.ClassLiteralValue::new);
    Parser numberVal = numberVal().map(AnnotationValue.NumberValue::new);
    Parser nestedAnnoVal = javaAnnotation.map(AnnotationValue.AnnotationValueHolder::new);
    Parser arrayVal = CharacterParser.of('{').trim()
        .seq(annotationValue.separatedBy(CharacterParser.of(',').trim()).optional())
        .seq(CharacterParser.of('}').trim())
        .map((List<Object> x) -> {
          List<Object> rawList = (List<Object>) x.get(1);
          List<AnnotationValue> list = getElements(rawList);
          return new AnnotationValue.ArrayValue(list);
        });

    annotationValue.set(stringVal.or(classLiteralVal).or(numberVal).or(nestedAnnoVal).or(arrayVal));

    // 2. Annotation Parameters
    Parser namedParam = CharacterParser.pattern("a-zA-Z0-9_").plus().flatten().trim()
        .seq(CharacterParser.of('=').trim())
        .seq(annotationValue)
        .map((List<Object> x) -> new AbstractMap.SimpleImmutableEntry<>((String) x.get(0), (AnnotationValue) x.get(2)));

    Parser namedParams = CharacterParser.of('(').trim()
        .seq(namedParam.separatedBy(CharacterParser.of(',').trim()))
        .seq(CharacterParser.of(')').trim())
        .map((List<Object> x) -> {
          List<Object> rawList = (List<Object>) x.get(1);
          List<Map.Entry<String, AnnotationValue>> entries = getElements(rawList);
          Map<String, AnnotationValue> map = new LinkedHashMap<>();
          for (Map.Entry<String, AnnotationValue> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
          }
          return map;
        });

    Parser singleParam = CharacterParser.of('(').trim()
        .seq(annotationValue)
        .seq(CharacterParser.of(')').trim())
        .map((List<Object> x) -> Map.of("value", (AnnotationValue) x.get(1)));

    Parser params = namedParams.or(singleParam).optional();

    // 3. Annotation Name
    Parser annotationName = packagePrefix()
        .seq(typeName().separatedBy(CharacterParser.of('.').trim()))
        .map((List<Object> x) -> {
          List<String> pkg = (List<String>) x.get(0);
          List<Object> typesRaw = (List<Object>) x.get(1);
          List<String> types = getElements(typesRaw);

          String pkgStr = String.join(".", pkg);
          String typeStr = String.join(".", types);
          return pkgStr + (pkgStr.isEmpty() ? "" : ".") + typeStr;
        });

    // 4. Java Annotation
    Parser annoParser = CharacterParser.of('@').trim()
        .seq(annotationName)
        .seq(params)
        .map((List<Object> x) -> {
          String name = (String) x.get(1);
          Map<String, AnnotationValue> map = (Map<String, AnnotationValue>) x.get(2);
          return new JavaAnnotation(name, map == null ? Map.of() : map);
        });
    javaAnnotation.set(annoParser);

    // 5. Type Segment
    Parser typeArgs = CharacterParser.of('<').trim()
        .seq(javaType.separatedBy(CharacterParser.of(',').trim()))
        .seq(CharacterParser.of('>').trim())
        .pick(1);

    Parser typeSegment = javaAnnotation.star()
        .seq(typeName())
        .seq(typeArgs.optional())
        .map((List<Object> x) -> {
          List<JavaAnnotation> annos = (List<JavaAnnotation>) x.get(0);
          String name = (String) x.get(1);
          List<Object> argsRaw = (List<Object>) x.get(2);
          List<JavaType> args = getElements(argsRaw);
          return new TypeSegment(annos, name, args);
        });

    // 6. Root JavaType
    Parser rootParser = packagePrefix()
        .seq(typeSegment.separatedBy(CharacterParser.of('.').trim()))
        .seq(StringParser.of("[]").trim().star())
        .map((List<Object> x) -> {
          List<String> pkg = (List<String>) x.get(0);
          List<Object> segmentsRaw = (List<Object>) x.get(1);
          List<TypeSegment> segments = getElements(segmentsRaw);
          List<Object> dims = (List<Object>) x.get(2);
          return new JavaType(pkg, segments, dims.size());
        });
    javaType.set(rootParser);

    return javaType.end();
  }

  /** Parses a JavaType from the given string, skipping whitespaces. */
  public static JavaType parse(String input) {
    Result result = PARSER.parse(input);
    if (result.isFailure()) {
      throw new IllegalArgumentException("PetitParser parsing error: " + result.getMessage());
    }
    return (JavaType) result.get();
  }

  private PetitParserJavaTypeParser() {}
}
