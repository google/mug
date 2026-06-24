package com.google.mu.benchmarks.parsers.dotparse;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.bmpCodeUnit;
import static com.google.common.labs.parse.Parser.chars;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedByWithEscapes;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.labs.parse.Parser.zeroOrMoreDelimited;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.joining;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.labs.parse.Parser;
import com.google.mu.benchmarks.parsers.javatype.AnnotationValue;
import com.google.mu.benchmarks.parsers.javatype.JavaAnnotation;
import com.google.mu.benchmarks.parsers.javatype.JavaType;
import com.google.mu.benchmarks.parsers.javatype.TypeSegment;
import com.google.mu.util.stream.BiCollectors;

/** Standalone dot-parse parser for Java Types. */
public final class JavaTypeParser {

  private static final Set<String> PRIMITIVES =
      Set.of("int", "double", "float", "long", "short", "byte", "char", "boolean", "void");

  private static final Parser<String> PRIMITIVE_TYPE = anyOf(
      string("int"), string("double"), string("float"), string("long"),
      string("short"), string("byte"), string("char"), string("boolean"),
      string("void"));

  private static final Parser<String> TYPE_NAME = anyOf(
      PRIMITIVE_TYPE,
      sequence(one("[A-Z]"), zeroOrMore("[a-zA-Z0-9_]")).source()
  );

  private static final Parser<String> PACKAGE_SEGMENT =
      sequence(one("[a-z]"), zeroOrMore("[a-zA-Z0-9_]"))
          .source()
          .suchThat(s -> !PRIMITIVES.contains(s), "package segment");

  // Unicode escape parsing (\u1234)
  private static final Parser<String> UNICODE_ESCAPE = string("u")
      .then(bmpCodeUnit())
      .map(code -> Character.toString((char) code.intValue()));

  // Standard backslash escapes
  private static final Parser<String> C_STYLE_ESCAPE = anyOf(
      string("n").thenReturn("\n"),
      string("t").thenReturn("\t"),
      string("r").thenReturn("\r"),
      string("f").thenReturn("\f"),
      string("b").thenReturn("\b"),
      string("\"").thenReturn("\""),
      string("'").thenReturn("'"),
      string("\\").thenReturn("\\")
  );

  private static final Parser<String> ESCAPED = anyOf(UNICODE_ESCAPE, C_STYLE_ESCAPE, chars(1));
  private static final Parser<String> STRING_LITERAL = quotedByWithEscapes('"', '"', ESCAPED);

  // Decimal & negative number parsing (no zero-width string("") calls)
  private static final Parser<?> INTEGER_PART =
      anyOf(sequence(string("-"), digits()), digits());

  private static final Parser<Number> NUMBER_VAL =
      sequence(INTEGER_PART, string(".").then(digits()).orNull())
          .source()
          .map(s -> s.contains(".") ? Double.parseDouble(s) : Integer.parseInt(s));

  // The master recursive JavaType parser
  public static final Parser<JavaType> JAVA_TYPE = Parser.define(selfType -> {

    // Annotation parser (mutually recursive with selfType)
    Parser<JavaAnnotation> annotation = Parser.define(selfAnno -> {

      // Annotation name (e.g. java.lang.SuppressWarnings)
      Parser<String> annotationName = sequence(
          PACKAGE_SEGMENT.followedBy(".").zeroOrMore(joining(".")),
          TYPE_NAME.atLeastOnceDelimitedBy(".", joining(".")),
          (pkg, t) -> pkg + (pkg.isEmpty() ? "" : ".") + t
      );

      // Annotation parameter values
      Parser<AnnotationValue> annotationValue = Parser.define(selfVal -> {
        Parser<AnnotationValue> stringVal = STRING_LITERAL.map(AnnotationValue.StringValue::new);
        Parser<AnnotationValue> numberVal = NUMBER_VAL.map(AnnotationValue.NumberValue::new);
        Parser<AnnotationValue> classLiteralVal = selfType.followedBy(".class").map(AnnotationValue.ClassLiteralValue::new);
        Parser<AnnotationValue> nestedAnnoVal = selfAnno.map(AnnotationValue.AnnotationValueHolder::new);
        Parser<AnnotationValue> arrayVal = selfVal.atLeastOnceDelimitedBy(",")
            .orElse(List.<AnnotationValue>of())
            .between("{", "}")
            .map(AnnotationValue.ArrayValue::new);
        return anyOf(stringVal, classLiteralVal, numberVal, nestedAnnoVal, arrayVal);
      });

      // Named parameters: @Size(min=1, max=10)
      Parser<Map<String, AnnotationValue>> namedParams = zeroOrMoreDelimited(
          word().followedBy("="),
          annotationValue,
          ",",
          BiCollectors.toMap()
      ).between("(", ")");

      // Shorthand single parameter: @Size(10)
      Parser<Map<String, AnnotationValue>> singleParam = annotationValue
          .between("(", ")")
          .map(val -> Map.of("value", val));
      Parser<Map<String, AnnotationValue>> params = anyOf(namedParams, singleParam);
      return string("@")
          .then(sequence(annotationName, params.orElse(Map.of()), new JavaAnnotation::new));
    });

    // Type segment (e.g. @NonNull Outer<String>)
    // We combine the optional prefix (annotations) and mandatory type name using sequence(OrEmpty, Parser, BiFunction)
    Parser<Map.Entry<List<JavaAnnotation>, String>> annosAndName = sequence(
        annotation.zeroOrMore(),
        TYPE_NAME,
        AbstractMap.SimpleImmutableEntry::new);

    Parser<TypeSegment> typeSegment = sequence(
        annosAndName,
        selfType.atLeastOnceDelimitedBy(",").between("<", ">").orElse(List.of()),
        (entry, args) -> new TypeSegment(entry.getKey(), entry.getValue(), args));

    // Prefix and type segments combined using sequence(OrEmpty, Parser, BiFunction)
    Parser<Map.Entry<List<String>, List<TypeSegment>>> prefixAndSegments = sequence(
        PACKAGE_SEGMENT.followedBy(".").zeroOrMore(),
        typeSegment.atLeastOnceDelimitedBy("."),
        AbstractMap.SimpleImmutableEntry::new);

    return sequence(
        prefixAndSegments,
        string("[]").zeroOrMore(counting()),
        (entry, dims) -> new JavaType(entry.getKey(), entry.getValue(), dims.intValue()));
  });

  /** Parses a JavaType from the given string, skipping whitespaces. */
  public static JavaType parse(String input) {
    return JAVA_TYPE.parseSkipping(Character::isWhitespace, input);
  }

  private JavaTypeParser() {}
}
