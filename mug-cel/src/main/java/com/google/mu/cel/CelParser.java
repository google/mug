package com.google.mu.cel;

import static com.google.common.labs.parse.Parser.anyOf;
import static com.google.common.labs.parse.Parser.caseInsensitive;
import static com.google.common.labs.parse.Parser.consecutive;
import static com.google.common.labs.parse.Parser.digits;
import static com.google.common.labs.parse.Parser.hexDigits;
import static com.google.common.labs.parse.Parser.literally;
import static com.google.common.labs.parse.Parser.one;
import static com.google.common.labs.parse.Parser.quotedBy;
import static com.google.common.labs.parse.Parser.sequence;
import static com.google.common.labs.parse.Parser.string;
import static com.google.common.labs.parse.Parser.word;
import static com.google.common.labs.parse.Parser.zeroOrMore;
import static com.google.common.labs.parse.Suffix.suffix;
import static com.google.mu.util.CharPredicate.isNot;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.common.labs.parse.OperatorTable;
import com.google.common.labs.parse.Parser;
import com.google.common.labs.parse.Suffix;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.function.Function4;
import com.google.mu.function.TriFunction;
import com.google.mu.util.CharPredicate;

/**
 * Parser for Common Expression Language (CEL) syntax, producing {@link CelExpr} AST
 * records.
 *
 * <p>Use this parser if pattern matching over records (as opposed to protos) is desirable to your
 * use case. It's also about 2x faster than ANTLR-based cel-java parser (as benchmarks show).
 *
 * @since 10.7
 */
@Immutable
public final class CelParser {
  private static final Set<String> KEYWORDS = Set.of(
      "as", "break", "const", "continue", "else", "false", "for", "function", "if",
      "import", "in", "let", "loop", "package", "namespace", "null", "return", "true",
      "var", "void", "while");
  private static final CharPredicate WHITESPACES =
      CharPredicate.anyOf(" \t\r\n\f").precomputeForAscii();
  private static final Parser<?> WHITESPACES_OR_COMMENTS =
      anyOf(consecutive("[ \t\r\n\f]"), sequence(string("//"), zeroOrMore("[^\n]")));

  private static final Parser<Boolean>.OrEmpty OPTIONALITY =
      one('?').thenReturn(true).orElse(false);

  static final Parser<String> IDENTIFIER =
      literally(one("[a-zA-Z_]"), zeroOrMore("[a-zA-Z0-9_]"))
          .source()
          .suchThat(s -> !KEYWORDS.contains(s), "identifier");

  private static final Parser<String> ESC_IDENTIFIER =
      consecutive("[a-zA-Z0-9_./ -]").immediatelyBetween("`", "`");
  private static final Parser<String> ANY_IDENTIFIER = anyOf(IDENTIFIER, ESC_IDENTIFIER);

  private static final Parser<String> RAW_STRING_LITERAL =
      anyOf(
          quotedBy("r\"\"\"", "\"\"\""),
          quotedBy("R\"\"\"", "\"\"\""),
          quotedBy("r'''", "'''"),
          quotedBy("R'''", "'''"),
          quotedBy("r\"", "\""),
          quotedBy("R\"", "\""),
          quotedBy("r'", "'"),
          quotedBy("R'", "'"));

  private static final Parser<String> STRING_LITERAL =
      anyOf(new StringLexer().parser(), RAW_STRING_LITERAL);

  private static final Parser<byte[]> BYTES_LITERAL =
      literally(
          caseInsensitive("b").then(
              anyOf(
                  new BytesLexer().parser(),
                  RAW_STRING_LITERAL.map(s -> s.getBytes(UTF_8)))));

  private static final Parser<String> HEX_DIGITS = consecutive("[0-9a-fA-F]");

  private static final Parser<Long> HEX_INT =
      sequence(
          string("-").orElse(""),
          literally(caseInsensitive("0x").then(HEX_DIGITS)),
          (sign, d) -> parseLong(sign + d, 16));

  private static final Parser<Long> DEC_INT =
      sequence(string("-").orElse(""), digits(), (sign, d) -> parseLong(sign + d));

  private static final Parser<Long> NUM_UINT =
      literally(
          anyOf(
                  string("0x").then(HEX_DIGITS).map(s -> parseUnsignedLong(s, 16)),
                  digits().map(CelParser::parseUnsignedLong))
              .followedBy(caseInsensitive("u")));

  private static final Parser<String> EXPONENT =
      sequence(caseInsensitive("e"), anyOf("+", "-").optional(), digits()).source();

  private static final Parser<Double> NUM_DOUBLE =
      signedLiteral(
          anyOf(
                  sequence(digits(), one('.'), digits(), EXPONENT.optional()),
                  sequence(digits(), EXPONENT),
                  sequence(one('.'), digits(), EXPONENT.optional()))
              .source()
              .map(CelParser::parseDouble),
          d -> -d);

  private static final Parser<CelExpr> CONSTANT_LITERAL =
      anyOf(
          NUM_DOUBLE.map(CelExpr.DoubleValue::new),
          NUM_UINT.map(CelExpr.UintValue::new),
          HEX_INT.map(CelExpr.LongValue::new),
          DEC_INT.map(CelExpr.LongValue::new),
          STRING_LITERAL.map(CelExpr.StringValue::new),
          BYTES_LITERAL.map(CelExpr.BytesValue::new),
          word("true").thenReturn(new CelExpr.BoolValue(true)),
          word("false").thenReturn(new CelExpr.BoolValue(false)),
          word("null").thenReturn(new CelExpr.NullValue()));

  private static final Parser<CelExpr> IDENT_EXPR =
      sequence(string(".").orElse(""), IDENTIFIER, String::concat).map(CelExpr.Ident::new);

  static final Parser<CelExpr> PARSER = makeParser();

  /** Parses the given CEL expression. */
  CelExpr parse(String input) {
    return PARSER.parseSkipping(WHITESPACES, input);
  }

  /**
   * Parses the given CEL expression, supporting comments.
   *
   * <p>To parse without comments, use {@link CelExpr#of}.
   */
  public CelExpr parseWithComments(String input) {
    return PARSER.parseSkipping(WHITESPACES_OR_COMMENTS, input);
  }

  private static Parser<CelExpr> makeParser() {
    Parser.Rule<CelExpr> regular = new Parser.Rule<>();
    Parser.Rule<CelExpr> expr = new Parser.Rule<>();
    Parser<CelExpr> parenthesized = expr.between("(", ")");

    Parser<CelExpr> listExpr =
        sequence(OPTIONALITY, expr, (opt, val) -> new CelExpr.Element(val, opt))
            .zeroOrMoreDelimitedBy(",")
            .optionallyFollowedBy(",")
            .between("[", "]")
            .map(CelExpr.ListLiteral::new);
    Parser<CelExpr> mapExpr =
        sequence(
                OPTIONALITY, expr.followedBy(":"), expr,
                (opt, key, val) -> new CelExpr.Entry<>(key, val, opt))
            .zeroOrMoreDelimitedBy(",")
            .optionallyFollowedBy(",")
            .between("{", "}")
            .map(CelExpr.MapLiteral::new);
    Parser<CelExpr.Entry<CelExpr.Ident>> messageFieldInit = sequence(
        OPTIONALITY, ANY_IDENTIFIER.followedBy(":"), expr,
        (opt, name, val) -> new CelExpr.Entry<>(new CelExpr.Ident(name), val, opt));
    Parser<CelExpr> myType =
        IDENT_EXPR.withPostfixes(one('.').then(ANY_IDENTIFIER).map(CelParser::select));
    Parser<List<CelExpr>> args = expr.zeroOrMoreDelimitedBy(",").between("(", ")");
    Parser<CelExpr> callOrStructOrIdent =
        myType.optionallyFollowedBy(
            anyOf(
                suffix(
                    messageFieldInit
                        .zeroOrMoreDelimitedBy(",")
                        .optionallyFollowedBy(",")
                        .between("{", "}"),
                    CelParser::structExpr),
                suffix(args, CelParser::call)),
            Suffix::apply);

    // Primary expression
    Parser<CelExpr> primary =
        anyOf(CONSTANT_LITERAL, callOrStructOrIdent, listExpr, mapExpr, parenthesized);
    Parser<CelExpr> memberExpr =
        primary.withPostfixes(
            anyOf(
                one('.').then(
                    anyOf(
                        sequence(ANY_IDENTIFIER, args, (n, a) -> t -> macroOrCall(t, n, a)),
                        one('?').then(ANY_IDENTIFIER).map(CelParser::optionalSelect),
                        ANY_IDENTIFIER.map(CelParser::select))),
                sequence(one('?').thenReturn(true).orElse(false), expr, CelParser::indexCall)
                    .between("[", "]")));
    Parser<CelExpr> unaryExpr =
        anyOf(
            memberExpr.withPrefixes(
                one('!').thenReturn(CelExpr::not)),
            memberExpr.withPrefixes(
                one('-').thenReturn(CelExpr::negative)));
    Parser<CelExpr> binary =
        new OperatorTable<CelExpr>()
            .leftAssociative("*", (l, r) -> binaryExpr(CelExpr.Binary.Op.MULT, l, r), 6)
            .leftAssociative("/", (l, r) -> binaryExpr(CelExpr.Binary.Op.DIV, l, r), 6)
            .leftAssociative("%", (l, r) -> binaryExpr(CelExpr.Binary.Op.MOD, l, r), 6)
            .leftAssociative("+", (l, r) -> binaryExpr(CelExpr.Binary.Op.ADD, l, r), 5)
            .leftAssociative("-", (l, r) -> binaryExpr(CelExpr.Binary.Op.SUB, l, r), 5)
            .leftAssociative("<=", (l, r) -> binaryExpr(CelExpr.Binary.Op.LE, l, r), 4)
            .leftAssociative("<", (l, r) -> binaryExpr(CelExpr.Binary.Op.LT, l, r), 4)
            .leftAssociative(">=", (l, r) -> binaryExpr(CelExpr.Binary.Op.GE, l, r), 4)
            .leftAssociative(">", (l, r) -> binaryExpr(CelExpr.Binary.Op.GT, l, r), 4)
            .leftAssociative("==", (l, r) -> binaryExpr(CelExpr.Binary.Op.EQ, l, r), 4)
            .leftAssociative("!=", (l, r) -> binaryExpr(CelExpr.Binary.Op.NE, l, r), 4)
            .leftAssociative(word("in").thenReturn((l, r) -> binaryExpr(CelExpr.Binary.Op.IN, l, r)), 4)
            .build(unaryExpr);
    binary = associative(binary, "&&", (l, r) -> binaryExpr(CelExpr.Binary.Op.AND, l, r));
    binary = associative(binary, "||", (l, r) -> binaryExpr(CelExpr.Binary.Op.OR, l, r));
    regular.definedAs(binary);
    return expr.definedAs(
        new OperatorTable<CelExpr>()
            .rightAssociative(
                anyOf(parenthesized, regular)
                    .between("?", ":")
                    .map(ifTrue -> (cond, ifFalse) -> cond.ifElse(ifTrue, ifFalse)),
                1)
            .build(anyOf(regular, parenthesized)));
  }

  private static <T> Parser<T> associative(
      Parser<T> operand, String operator, BinaryOperator<T> combine) {
    return operand
        .atLeastOnceDelimitedBy(operator)
        .map(operands -> balanced(operands, 0, operands.size(), combine));
  }

  private static <T> T balanced(
      List<? extends T> operands, int from, int len, BinaryOperator<T> op) {
    if (len <= 0) {
      throw new IllegalStateException("len must be positive");
    }
    if (len == 1) {
      return operands.get(from);
    }
    int left = (len + 1) / 2;
    int right = len - left;
    return op.apply(balanced(operands, from, left, op), balanced(operands, from + left, right, op));
  }

  private abstract static class SequenceLexer<T> {
    final Parser<T> parser() {
      return anyOf(tripleQuoted('"'), tripleQuoted('\''), singleQuoted('"'), singleQuoted('\''));
    }

    abstract Collector<? super T, ?, T> joiner();
    abstract T fromCodePoint(int codePoint);
    abstract T literal(String raw);
    abstract T literal(char c);
    abstract Parser<T> escaped();

    final Parser<T> charEscape() {
      return anyOf(
          one('a').thenReturn(literal((char) 7)),
          one('b').thenReturn(literal('\b')),
          one('f').thenReturn(literal('\f')),
          one('n').thenReturn(literal('\n')),
          one('r').thenReturn(literal('\r')),
          one('t').thenReturn(literal('\t')),
          one('v').thenReturn(literal((char) 11)),
          one('"').thenReturn(literal('"')),
          one('\'').thenReturn(literal('\'')),
          one('\\').thenReturn(literal('\\')),
          one('?').thenReturn(literal('?')),
          one('`').thenReturn(literal('`')));
    }

    final Parser<T> octEscape() {
      return sequence(one("[0-3]"), one("[0-7]"), one("[0-7]"))
          .source()
          .map(s -> fromCodePoint(Integer.parseInt(s, 8)));
    }

    final Parser<T> hexEscape() {
      return caseInsensitive("x").then(fromHex(2));
    }

    final Parser<T> fromHex(int numDigits) {
      return hexDigits(numDigits).map(hex -> fromCodePoint(Integer.parseInt(hex, 16)));
    }

    private Parser<T> singleQuoted(char quoteChar) {
      String quote = Character.toString(quoteChar);
      return escapable(quoteChar).zeroOrMore(joiner()).immediatelyBetween(quote, quote);
    }

    private Parser<T> tripleQuoted(char quoteChar) {
      String quote = Character.toString(quoteChar);
      String tripleQuote = quote + quote + quote;
      return anyOf(
              escapable(quoteChar),
              anyOf(quote + quote, quote).notFollowedBy(quote).map(this::literal))
          .zeroOrMore(joiner())
          .immediatelyBetween(tripleQuote, tripleQuote);
    }

    private Parser<T> escapable(char quoteChar) {
      return anyOf(
          consecutive(isNot('\\').and(isNot(quoteChar)).precomputeForAscii(), "normal chars")
              .map(this::literal),
          one('\\').then(escaped()));
    }
  }

  private static final class StringLexer extends SequenceLexer<String> {
    @Override Collector<CharSequence, ?, String> joiner() {
      return Collectors.joining();
    }

    @Override String fromCodePoint(int codePoint) {
      return Character.toString(codePoint);
    }

    @Override String literal(String raw) {
      return raw;
    }

    @Override String literal(char c) {
      return Character.toString(c);
    }

    @Override Parser<String> escaped() {
      return anyOf(
          charEscape(), octEscape(), hexEscape(),
          one('u').then(fromHex(4)), one('U').then(fromHex(8)));
    }
  }

  private static final class BytesLexer extends SequenceLexer<byte[]> {
    @Override Collector<byte[], ?, byte[]> joiner() {
      return Collector.of(
          ByteArrayOutputStream::new,
          ByteArrayOutputStream::writeBytes,
          (b1, b2) -> {
            b1.writeBytes(b2.toByteArray());
            return b1;
          },
          ByteArrayOutputStream::toByteArray);
    }

    @Override byte[] fromCodePoint(int codePoint) {
      return new byte[] {(byte) codePoint};
    }

    @Override byte[] literal(String raw) {
      return raw.getBytes(UTF_8);
    }

    @Override byte[] literal(char c) {
      return new byte[] {(byte) c};
    }

    @Override Parser<byte[]> escaped() {
      return anyOf(charEscape(), octEscape(), hexEscape());
    }
  }

  private static UnaryOperator<CelExpr> optionalSelect(String field) {
    return receiver -> new CelExpr.Call(
        Optional.of(receiver), "optionalSelect", List.of(new CelExpr.StringValue(field)));
  }

  private static UnaryOperator<CelExpr> select(String field) {
    return receiver -> new CelExpr.Select(receiver, field);
  }

  private static UnaryOperator<CelExpr> indexCall(boolean optional, CelExpr index) {
    return optional
        ? receiver -> new CelExpr.Call(Optional.of(receiver), "optionalIndex", List.of(index))
        : receiver -> new CelExpr.Index(receiver, index);
  }

  private static CelExpr call(CelExpr target, List<CelExpr> args) {
    return switch (target) {
      case CelExpr.Ident ident -> macroOrCall(ident.name(), args);
      case CelExpr.Select select -> macroOrCall(select.operand(), select.field(), args);
      default -> throw new AssertionError("Invalid call receiver: " + target);
    };
  }

  private static CelExpr macroOrCall(String method, List<CelExpr> args) {
    return switch (method) {
      case "has" -> {
        checkSyntax(args.size() == 1, "has() expects 1 arg, %s provided", args.size());
        CelExpr.Select select =
            expect(CelExpr.Select.class, args.get(0), "has() expects 1 select argument");
        yield new CelExpr.Macro.Has(select);
      }
      default -> new CelExpr.Call(Optional.empty(), method, args);
    };
  }

  private static CelExpr macroOrCall(CelExpr target, String method, List<CelExpr> args) {
    return switch (method) {
      case "all" -> toMacro(target, method, args, CelExpr.Macro.All::new);
      case "exists" -> toMacro(target, method, args, CelExpr.Macro.Exists::new);
      case "exists_one" -> toMacro(target, method, args, CelExpr.Macro.ExistsOne::new);
      case "filter" -> toMacro(target, method, args, CelExpr.Macro.Filter::new);
      case "map" -> switch (args.size()) {
        case 2 -> toMacro(target, method, args, CelExpr.Macro.Map::new);
        case 3 -> toMacro(target, method, args, CelExpr.Macro.FilterMap::new);
        default ->
          throw Parser.fail("map() macro expects 2 or 3 args, " + args.size() + " provided");
      };
      default -> new CelExpr.Call(Optional.of(target), method, args);
    };
  }

  private static <T extends CelExpr.Macro> T toMacro(
      CelExpr target, String method, List<CelExpr> args,
      TriFunction<CelExpr, String, CelExpr, T> construct) {
    checkSyntax(args.size() == 2, "%s() expects 2 args, %s provided", method, args.size());
    CelExpr.Ident placeholder =
        expect(CelExpr.Ident.class, args.get(0), "identifier expected for the 1st arg of %s()", method);
    return construct.apply(target, placeholder.name(), args.get(1));
  }

  private static <T extends CelExpr.Macro> T toMacro(
      CelExpr target, String method, List<CelExpr> args,
      Function4<CelExpr, String, CelExpr, CelExpr, T> construct) {
    checkSyntax(args.size() == 3, "%s() expects 3 args, %s provided", method, args.size());
    CelExpr.Ident placeholder =
        expect(CelExpr.Ident.class, args.get(0), "identifier expected for the 1st arg of %s()", method);
    return construct.apply(target, placeholder.name(), args.get(1), args.get(2));
  }

  private static CelExpr structExpr(CelExpr receiver, List<CelExpr.Entry<CelExpr.Ident>> fields) {
    Set<String> keys = new HashSet<>();
    for (CelExpr.Entry<CelExpr.Ident> field : fields) {
      String name = field.key().name();
      checkSyntax(keys.add(name), "duplicate field name: %s", name);
    }
    return new CelExpr.StructLiteral(toTypeName(receiver), fields);
  }

  private static String toTypeName(CelExpr expr) {
    return switch (expr) {
      case CelExpr.Ident ident -> ident.name();
      case CelExpr.Select select ->
          toTypeName(select.operand()) + "." + select.field();
      default -> throw new AssertionError("Invalid struct receiver: " + expr);
    };
  }

  private static CelExpr binaryExpr(CelExpr.Binary.Op op, CelExpr left, CelExpr right) {
    return new CelExpr.Binary(left, op, right);
  }

  private static <N> Parser<N> signedLiteral(Parser<N> positive, UnaryOperator<N> negate) {
    Parser<N> literal = literally(positive);
    return anyOf(one('-').then(literal).map(negate), literal);
  }

  @FormatMethod
  private static <T> T expect(Class<T> type, Object value, String message, Object... args) {
    checkSyntax(type.isInstance(value), message, args);
    return type.cast(value);
  }

  @FormatMethod
  private static <T> T expect(Class<T> type, Object value, String message) {
    checkSyntax(type.isInstance(value), message);
    return type.cast(value);
  }

  @FormatMethod
  private static void checkSyntax(boolean condition, String message, Object... args) {
    if (!condition) {
      throw Parser.fail(String.format(message, args));
    }
  }

  @FormatMethod
  private static void checkSyntax(boolean condition, String message) {
    if (!condition) {
      throw Parser.fail(message);
    }
  }

  private static long parseLong(String s, int radix) {
    try {
      return Long.parseLong(s, radix);
    } catch (NumberFormatException e) {
      throw Parser.fail("integer overflow: " + s);
    }
  }

  private static long parseLong(String s) {
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      throw Parser.fail("integer overflow: " + s);
    }
  }

  private static long parseUnsignedLong(String s, int radix) {
    try {
      return Long.parseUnsignedLong(s, radix);
    } catch (NumberFormatException e) {
      throw Parser.fail("integer overflow: " + s);
    }
  }

  private static long parseUnsignedLong(String s) {
    try {
      return Long.parseUnsignedLong(s);
    } catch (NumberFormatException e) {
      throw Parser.fail("integer overflow: " + s);
    }
  }

  private static double parseDouble(String s) {
    try {
      return Double.parseDouble(s);
    } catch (NumberFormatException e) {
      throw Parser.fail("double overflow: " + s);
    }
  }
}
