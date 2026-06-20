package com.google.mu.benchmarks.parsers.jparsec;

import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Scanners;
import org.jparsec.Terminals;
import org.jparsec.Tokens;
import org.jparsec.pattern.Patterns;
import com.google.mu.benchmarks.parsers.javatype.*;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Idiomatic JParsec-based parser for Java Types using Lexer/Parser separation. */
public final class JparsecJavaTypeParser {

  private static final Set<String> PRIMITIVES = Set.of(
      "int", "double", "float", "long", "short", "byte", "char", "boolean", "void"
  );

  // Define terminals (keywords and operators)
  private static final Terminals TERMS = Terminals
      .operators("@", ".", "<", ">", ",", "[", "]", "=", "{", "}", "[]", "(", ")")
      .words(Scanners.IDENTIFIER)
      .keywords(
          "int", "double", "float", "long", "short", "byte", "char", "boolean", "void", "class")
      .build();

  // =========================================================================
  // 1. Lexer / Tokenizer Scanners
  // =========================================================================

  // Matches double-quoted strings (including C-style escapes and unicode escapes)
  private static final Parser<String> STRING_LITERAL_SCANNER = Scanners.pattern(
      Patterns.regex("\"([^\"\\\\]|\\\\.)*\""),
      "string"
  ).source();

  // Matches decimal and floating-point number literals
  private static final Parser<String> NUMBER_SCANNER = Scanners.pattern(
      Patterns.regex("-?[0-9]+(\\.[0-9]+)?"),
      "number"
  ).source();

  // The master Tokenizer combining our custom tokenizers and JParsec terminals
  private static final Parser<Object> TOKENIZER = Parsers.or(
      STRING_LITERAL_SCANNER.map(s -> Tokens.fragment(s, "string")),
      NUMBER_SCANNER.map(s -> Tokens.fragment(s, "number")),
      TERMS.tokenizer()
  );

  // =========================================================================
  // 2. Token-level Parsers (Operating on Token Stream)
  // =========================================================================

  // Parses a string literal token and unescapes it
  private static final Parser<String> STRING_LITERAL = Parsers.token(token -> {
    if (token.value() instanceof Tokens.Fragment) {
      Tokens.Fragment f = (Tokens.Fragment) token.value();
      if ("string".equals(f.tag())) {
        return unescapeString(f.text());
      }
    }
    return null;
  });

  // Parses a number literal token and converts it to Integer or Double
  private static final Parser<Number> NUMBER_VAL = Parsers.token(token -> {
    if (token.value() instanceof Tokens.Fragment) {
      Tokens.Fragment f = (Tokens.Fragment) token.value();
      if ("number".equals(f.tag())) {
        String s = f.text();
        if (s.contains(".")) {
          return Double.parseDouble(s);
        } else {
          return Integer.parseInt(s);
        }
      }
    }
    return null;
  });

  private static final Parser<String> PRIMITIVE_TYPE = Parsers.or(
      TERMS.token("int").retn("int"),
      TERMS.token("double").retn("double"),
      TERMS.token("float").retn("float"),
      TERMS.token("long").retn("long"),
      TERMS.token("short").retn("short"),
      TERMS.token("byte").retn("byte"),
      TERMS.token("char").retn("char"),
      TERMS.token("boolean").retn("boolean"),
      TERMS.token("void").retn("void")
  );

  // Type name is either a primitive type or an identifier starting with an uppercase letter
  private static final Parser<String> TYPE_NAME = Parsers.or(
      PRIMITIVE_TYPE,
      Terminals.Identifier.PARSER.next(s -> 
          Character.isUpperCase(s.charAt(0)) ? Parsers.constant(s) : Parsers.never()
      )
  );

  // Package segment is a lowercase identifier that is not a primitive type keyword
  private static final Parser<String> PACKAGE_SEGMENT = Terminals.Identifier.PARSER.next(s ->
      (Character.isLowerCase(s.charAt(0)) && !PRIMITIVES.contains(s))
          ? Parsers.constant(s)
          : Parsers.never()
  );

  private static final Parser<List<String>> PACKAGE_PREFIX = 
      PACKAGE_SEGMENT.followedBy(TERMS.token(".")).many();

  // The master recursive parser runner
  private static final Parser<JavaType> PARSER = buildParser();

  private static Parser<JavaType> buildParser() {
    Parser.Reference<JavaType> refType = Parser.newReference();
    Parser.Reference<JavaAnnotation> refAnno = Parser.newReference();
    Parser.Reference<AnnotationValue> refVal = Parser.newReference();

    // Array of values: {1, 2, 3}
    Parser<AnnotationValue> arrayVal = refVal.lazy().sepBy(TERMS.token(","))
        .between(TERMS.token("{"), TERMS.token("}"))
        .map(AnnotationValue.ArrayValue::new);

    // Annotation value parser
    Parser<AnnotationValue> valParser = Parsers.or(
        STRING_LITERAL.map(AnnotationValue.StringValue::new),
        refType.lazy()
            .followedBy(TERMS.token("."))
            .followedBy(TERMS.token("class"))
            .map(AnnotationValue.ClassLiteralValue::new),
        NUMBER_VAL.map(AnnotationValue.NumberValue::new),
        refAnno.lazy().map(AnnotationValue.AnnotationValueHolder::new),
        arrayVal
    );
    refVal.set(valParser);

    // Named parameters: @Size(min=1, max=10)
    Parser<Map<String, AnnotationValue>> namedParams = Parsers.sequence(
        Terminals.Identifier.PARSER.followedBy(TERMS.token("=")),
        refVal.lazy(),
        AbstractMap.SimpleImmutableEntry::new
    ).sepBy(TERMS.token(","))
    .between(TERMS.token("("), TERMS.token(")"))
    .map(list -> list.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

    // Shorthand parameter: @Size(10)
    Parser<Map<String, AnnotationValue>> singleParam = refVal.lazy()
        .between(TERMS.token("("), TERMS.token(")"))
        .map(val -> Map.of("value", val));

    Parser<Map<String, AnnotationValue>> params = Parsers.or(namedParams, singleParam)
        .optional(Map.of());

    // Annotation name parser
    Parser<String> annotationName = Parsers.sequence(
        PACKAGE_SEGMENT.followedBy(TERMS.token(".")).many(),
        TYPE_NAME.sepBy1(TERMS.token(".")),
        (pkg, types) -> String.join(".", pkg) + (pkg.isEmpty() ? "" : ".") + String.join(".", types)
    );

    // Full annotation parser
    Parser<JavaAnnotation> annoParser = TERMS.token("@").next(Parsers.sequence(
        annotationName,
        params,
        JavaAnnotation::new
    ));
    refAnno.set(annoParser);

    // Type segment (e.g. @NonNull Outer<String>)
    Parser<TypeSegment> typeSegment = Parsers.sequence(
        refAnno.lazy().many(),
        TYPE_NAME,
        refType.lazy()
            .sepBy1(TERMS.token(","))
            .between(TERMS.token("<"), TERMS.token(">"))
            .optional(List.of()),
        TypeSegment::new
    );

    // Root JavaType parser
    Parser<JavaType> root = Parsers.sequence(
        PACKAGE_PREFIX,
        typeSegment.sepBy1(TERMS.token(".")),
        TERMS.token("[]").many().map(List::size),
        JavaType::new
    );
    refType.set(root);

    // Delimiter scanner for whitespace skipping
    Parser<Void> ignored = Scanners.WHITESPACES.optional();

    // Use lexer/parser separation: tokenize the input, skipping whitespaces, and run parser!
    return root.from(TOKENIZER, ignored);
  }

  /** High-performance Java string literal unescaping helper. */
  private static String unescapeString(String s) {
    // Strip surrounding quotes
    String raw = s.substring(1, s.length() - 1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        char next = raw.charAt(i + 1);
        if (next == 'u' && i + 5 < raw.length()) {
          String hex = raw.substring(i + 2, i + 6);
          sb.append((char) Integer.parseInt(hex, 16));
          i += 5;
        } else {
          switch (next) {
            case 'n': sb.append('\n'); break;
            case 't': sb.append('\t'); break;
            case 'r': sb.append('\r'); break;
            case 'f': sb.append('\f'); break;
            case 'b': sb.append('\b'); break;
            case '"': sb.append('"'); break;
            case '\'': sb.append('\''); break;
            case '\\': sb.append('\\'); break;
            default: sb.append('\\').append(next); break;
          }
          i++;
        }
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Parses a JavaType from the given string using JParsec. */
  public static JavaType parse(String input) {
    return PARSER.parse(input);
  }

  private JparsecJavaTypeParser() {}
}
