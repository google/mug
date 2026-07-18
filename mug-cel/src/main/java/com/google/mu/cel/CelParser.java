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
import static com.google.mu.util.CharPredicate.isNot;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.expr.v1alpha1.ParsedExpr;
import com.google.common.labs.parse.OperatorTable;
import com.google.common.labs.parse.Parser;
import com.google.common.labs.parse.Suffix;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.Immutable;
import com.google.mu.function.Function4;
import com.google.mu.function.TriFunction;
import com.google.mu.util.CharPredicate;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Parser for Common Expression Language (CEL) syntax, producing {@link CelExpr} AST records.
 *
 * <p>Use this parser if pattern matching over records (as opposed to protos) is desirable to your
 * use case. It's also about 2x faster than ANTLR-based cel-java parser (as benchmarks show).
 *
 * @since 10.7
 */
@Immutable
public final class CelParser {
  private static final Set<String> KEYWORDS =
      Set.of(
          "as",
          "break",
          "const",
          "continue",
          "else",
          "false",
          "for",
          "function",
          "if",
          "import",
          "in",
          "let",
          "loop",
          "package",
          "namespace",
          "null",
          "return",
          "true",
          "var",
          "void",
          "while");
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
          caseInsensitive("b")
              .then(
                  anyOf(
                      new BytesLexer().parser(), RAW_STRING_LITERAL.map(s -> s.getBytes(UTF_8)))));

  private static final Parser<String> HEX_DIGITS = consecutive("[0-9a-fA-F]");

  private static final Parser<CelExpr.LongValue> hexInt =
      sequence(
              one('-').optional(),
              literally(caseInsensitive("0x").then(HEX_DIGITS)),
              (sign, d) -> {
                String valStr = sign.map(Object::toString).orElse("") + d;
                return parseLong(valStr, 16);
              })
          .mapWithIndex((val, begin, end) -> new CelExpr.LongValue(begin, val));

  private static final Parser<CelExpr.LongValue> decInt =
      sequence(
              one('-').optional(),
              digits(),
              (sign, d) -> {
                String valStr = sign.map(Object::toString).orElse("") + d;
                return parseLong(valStr);
              })
          .mapWithIndex((val, begin, end) -> new CelExpr.LongValue(begin, val));

  private static final Parser<CelExpr.UintValue> positiveUint =
      literally(
              anyOf(
                      string("0x").then(HEX_DIGITS).map(s -> parseUnsignedLong(s, 16)),
                      digits().map(CelParser::parseUnsignedLong))
                  .followedBy(caseInsensitive("u")))
          .mapWithIndex((val, begin, end) -> new CelExpr.UintValue(begin, val));

  private static final Parser<String> EXPONENT =
      sequence(caseInsensitive("e"), anyOf("+", "-").optional(), digits()).source();

  private static final Parser<CelExpr.DoubleValue> signedDouble =
      sequence(
              one('-').optional(),
              anyOf(
                      sequence(digits(), one('.'), digits(), EXPONENT.optional()),
                      sequence(digits(), EXPONENT),
                      sequence(one('.'), digits(), EXPONENT.optional()))
                  .source(),
              (sign, d) -> {
                String valStr = sign.map(Object::toString).orElse("") + d;
                return parseDouble(valStr);
              })
          .mapWithIndex((val, begin, end) -> new CelExpr.DoubleValue(begin, val));

  private static final Parser<CelExpr> CONSTANT_LITERAL =
      anyOf(
          signedDouble,
          positiveUint,
          hexInt,
          decInt,
          STRING_LITERAL.mapWithIndex((val, begin, end) -> new CelExpr.StringValue(begin, val)),
          BYTES_LITERAL.mapWithIndex((val, begin, end) -> new CelExpr.BytesValue(begin, val)),
          word("true").mapWithIndex((val, begin, end) -> new CelExpr.BoolValue(begin, true)),
          word("false").mapWithIndex((val, begin, end) -> new CelExpr.BoolValue(begin, false)),
          word("null").mapWithIndex((val, begin, end) -> new CelExpr.NullValue(begin)));

  private static final Parser<CelExpr> IDENT_EXPR =
      sequence(string(".").orElse(""), IDENTIFIER, String::concat)
          .mapWithIndex((name, begin, end) -> new CelExpr.Ident(begin, name));

  private static final Parser<CelExpr> PARSER = makeParser();

  @SuppressWarnings("Immutable")
  private final Parser<CelExpr>.Lexical lexical;

  private CelParser(Parser<CelExpr>.Lexical lexical) {
    this.lexical = lexical;
  }

  public CelParser() {
    this(PARSER.skipping(WHITESPACES));
  }

  /** Returns an equivalent {@link CelParser} that also supports comments. */
  public CelParser withComments() {
    return new CelParser(PARSER.skipping(WHITESPACES_OR_COMMENTS));
  }

  /** Parses the given CEL expression. */
  public CelExpr parse(String input) {
    return lexical.parse(input);
  }

  /**
   * Parses the CEL expression and converts it to a {@link ParsedExpr} proto.
   *
   * <p>The returned {@link ParsedExpr#getSourceInfo} will have {@link
   * com.google.api.expr.v1alpha1.SourceInfo#getPositionsMap() positions}, {@link
   * com.google.api.expr.v1alpha1.SourceInfo#getLineOffsetsList() line_offsets} and {@link
   * com.google.api.expr.v1alpha1.SourceInfo#getMacroCallsMap() macro_calls} populated. The caller
   * can also populate the other fields like {@link
   * com.google.api.expr.v1alpha1.SourceInfo#getLocation location} and {@link
   * com.google.api.expr.v1alpha1.SourceInfo#getSyntaxVersion() syntax_version} if such information
   * is available.
   */
  public ParsedExpr parseToProto(String input) {
    return CelProtoConverter.toParsedExpr(parse(input), input);
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
            .mapWithIndex((elements, begin, end) -> new CelExpr.ListLiteral(begin, elements));
    Parser<CelExpr> mapExpr =
        sequence(
                OPTIONALITY,
                expr,
                string(":").mapWithIndex((colon, begin, end) -> begin),
                expr,
                (opt, key, colon, val) -> new CelExpr.Entry<>(colon, key, val, opt))
            .zeroOrMoreDelimitedBy(",")
            .optionallyFollowedBy(",")
            .between("{", "}")
            .mapWithIndex((entries, begin, end) -> new CelExpr.MapLiteral(begin, entries));
    Parser<CelExpr.Entry<CelExpr.Ident>> messageFieldInit =
        sequence(
            OPTIONALITY,
            ANY_IDENTIFIER.mapWithIndex((name, begin, end) -> new CelExpr.Ident(begin, name)),
            string(":").mapWithIndex((colon, begin, end) -> begin),
            expr,
            (opt, nameIdent, colon, val) -> new CelExpr.Entry<>(colon, nameIdent, val, opt));
    Parser<CelExpr> callOrStructOrIdent =
        IDENT_EXPR
            .withPostfixes(
                one('.')
                    .then(ANY_IDENTIFIER)
                    .mapWithIndex((f, begin, end) -> e -> new CelExpr.Select(begin, e, f)))
            .optionallyFollowedBy(
                anyOf(
                    suffixWithIndex(
                        messageFieldInit
                            .zeroOrMoreDelimitedBy(",")
                            .optionallyFollowedBy(",")
                            .between("{", "}"),
                        CelParser::structExpr),
                    suffixWithIndex(
                        expr.zeroOrMoreDelimitedBy(",").between("(", ")"), CelParser::call)),
                Suffix::apply);

    // Primary expression
    Parser<CelExpr> primary =
        anyOf(CONSTANT_LITERAL, callOrStructOrIdent, listExpr, mapExpr, parenthesized);
    Parser<CelExpr.Ident> memberMethod =
        ANY_IDENTIFIER.mapWithIndex((name, begin, end) -> new CelExpr.Ident(begin, name));

    Parser<CelExpr> memberExpr =
        withPostfixIndex(
            primary,
            anyOf(
                    one('.')
                        .then(
                            anyOf(
                                sequence(
                                    memberMethod,
                                    expr.zeroOrMoreDelimitedBy(",").between("(", ")"),
                                    (name, methodArgs) ->
                                        target -> macroOrCall(target, name, methodArgs)),
                                one('?').then(ANY_IDENTIFIER).map(CelParser::optionalSelect),
                                ANY_IDENTIFIER.map(CelParser::select))),
                    sequence(one('?').thenReturn(true).orElse(false), expr, CelParser::index)
                        .between("[", "]")));
    Parser<CelExpr> unaryExpr =
        anyOf(
            memberExpr.withPrefixes(unary('!', CelExpr.Not::new)),
            memberExpr.withPrefixes(unary('-', CelExpr.Negative::new)));
    Parser<CelExpr> binary =
        new OperatorTable<CelExpr>()
            .leftAssociative(binary("*", CelExpr::multiply), 6)
            .leftAssociative(binary("/", CelExpr::divide), 6)
            .leftAssociative(binary("%", CelExpr::modulo), 6)
            .leftAssociative(binary("+", CelExpr::add), 5)
            .leftAssociative(binary("-", CelExpr::subtract), 5)
            .leftAssociative(binary("<=", CelExpr::atMost), 4)
            .leftAssociative(binary("<", CelExpr::lessThan), 4)
            .leftAssociative(binary(">=", CelExpr::atLeast), 4)
            .leftAssociative(binary(">", CelExpr::greaterThan), 4)
            .leftAssociative(binary("==", CelExpr::equalTo), 4)
            .leftAssociative(binary("!=", CelExpr::notEqualTo), 4)
            .leftAssociative(binary(word("in"), CelExpr::in), 4)
            .build(unaryExpr);
    binary = associative(binary, "&&", CelExpr::and);
    binary = associative(binary, "||", CelExpr::or);
    regular.definedAs(binary);
    return expr.definedAs(
        new OperatorTable<CelExpr>()
            .rightAssociative(
                anyOf(parenthesized, regular)
                    .between("?", ":")
                    .mapWithIndex(
                        (ifTrue, begin, end) ->
                            (cond, ifFalse) -> new CelExpr.Ternary(begin, cond, ifTrue, ifFalse)),
                1)
            .build(anyOf(regular, parenthesized)));
  }

  private static Parser<CelExpr> associative(
      Parser<CelExpr> operand, String operator, BinaryOperator<CelExpr> factory) {
    return sequence(
        operand,
        sequence(binary(operator, factory), operand, Rhs::new).zeroOrMore(),
        (left, rights) -> balanced(left, rights, 0, rights.size()));
  }

  private static CelExpr balanced(CelExpr left, List<Rhs> rights, int from, int len) {
    if (len == 0) {
      return left;
    }
    if (len == 1) {
      Rhs rhs = rights.get(from);
      return rhs.op.apply(left, rhs.value);
    }
    int leftLen = (len + 2) / 2 - 1;
    int splitIndex = from + leftLen;
    Rhs split = rights.get(splitIndex);
    return split.op.apply(
        balanced(left, rights, from, leftLen),
        balanced(split.value, rights, splitIndex + 1, len - 1 - leftLen));
  }

  private static final record Rhs(BinaryOperator<CelExpr> op, CelExpr value) {}

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
          charEscape(),
          octEscape(),
          hexEscape(),
          one('u').then(fromHex(4)),
          one('U').then(fromHex(8)));
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

  private static UnaryOperator<CelExpr> select(String field) {
    return receiver -> receiver.select(field);
  }

  private static UnaryOperator<CelExpr> optionalSelect(String field) {
    return receiver -> receiver.optionalSelect(field);
  }

  private static UnaryOperator<CelExpr> index(boolean optional, CelExpr index) {
    return optional
        ? receiver -> receiver.optionalIndex(index)
        : receiver -> receiver.index(index);
  }

  private static CelExpr call(CelExpr target, List<CelExpr> args) {
    return switch (target) {
      case CelExpr.Ident ident -> macroOrCall(ident, args);
      case CelExpr.Select select ->
          macroOrCall(
              select.operand(), new CelExpr.Ident(select.sourceIndex(), select.field()), args);
      default -> throw new AssertionError("Invalid call receiver: " + target);
    };
  }

  private static CelExpr macroOrCall(CelExpr.Ident method, List<CelExpr> args) {
    return switch (method.name()) {
      case "has" -> {
        checkSyntax(args.size() == 1, "has() expects 1 arg, %s provided", args.size());
        CelExpr.Select select =
            expect(CelExpr.Select.class, args.get(0), "has() expects 1 select argument");
        yield new CelExpr.Macro.Has(select);
      }
      default -> new CelExpr.FunctionCall(method, args);
    };
  }

  private static CelExpr macroOrCall(CelExpr target, CelExpr.Ident method, List<CelExpr> args) {
    return switch (method.name()) {
      case "all" ->
          toMacro(target, method.name(), args, (t, v, c) -> new CelExpr.Macro.All(t, v, c));
      case "exists" ->
          toMacro(target, method.name(), args, (t, v, c) -> new CelExpr.Macro.Exists(t, v, c));
      case "exists_one" ->
          toMacro(target, method.name(), args, (t, v, c) -> new CelExpr.Macro.ExistsOne(t, v, c));
      case "filter" ->
          toMacro(target, method.name(), args, (t, v, c) -> new CelExpr.Macro.Filter(t, v, c));
      case "map" ->
          switch (args.size()) {
            case 2 ->
                toMacro(target, method.name(), args, (t, v, c) -> new CelExpr.Macro.Map(t, v, c));
            case 3 ->
                toMacro(
                    target,
                    method.name(),
                    args,
                    (t, v, c1, c2) -> new CelExpr.Macro.FilterMap(t, v, c1, c2));
            default ->
                throw Parser.fail("map() macro expects 2 or 3 args, " + args.size() + " provided");
          };
      default -> new CelExpr.MemberCall(target, method, args);
    };
  }

  private static <T extends CelExpr.Macro> T toMacro(
      CelExpr target,
      String method,
      List<CelExpr> args,
      TriFunction<CelExpr, CelExpr.Ident, CelExpr, T> construct) {
    checkSyntax(args.size() == 2, "%s() expects 2 args, %s provided", method, args.size());
    CelExpr.Ident placeholder =
        expect(
            CelExpr.Ident.class,
            args.get(0),
            "identifier expected for the 1st arg of %s()",
            method);
    return construct.apply(target, placeholder, args.get(1));
  }

  private static <T extends CelExpr.Macro> T toMacro(
      CelExpr target,
      String method,
      List<CelExpr> args,
      Function4<CelExpr, CelExpr.Ident, CelExpr, CelExpr, T> construct) {
    checkSyntax(args.size() == 3, "%s() expects 3 args, %s provided", method, args.size());
    CelExpr.Ident placeholder =
        expect(
            CelExpr.Ident.class,
            args.get(0),
            "identifier expected for the 1st arg of %s()",
            method);
    return construct.apply(target, placeholder, args.get(1), args.get(2));
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
      case CelExpr.Select select -> toTypeName(select.operand()) + "." + select.field();
      default -> throw new AssertionError("Invalid struct receiver: " + expr);
    };
  }

  private static Parser<BinaryOperator<CelExpr>> binary(
      Parser<?> op, BinaryOperator<CelExpr> factory) {
    return op.mapWithIndex(
        (s, begin, end) -> (l, r) -> factory.apply(l, r).withSourceIndex(begin));
  }

  private static Parser<BinaryOperator<CelExpr>> binary(
      String op, BinaryOperator<CelExpr> factory) {
    return binary(string(op), factory);
  }

  private static Parser<UnaryOperator<CelExpr>> unary(
      char opChar, BiFunction<Integer, CelExpr, CelExpr> factory) {
    return one(opChar).mapWithIndex((s, begin, end) -> e -> factory.apply(begin, e));
  }

  private static Parser<CelExpr> withPostfixIndex(
      Parser<CelExpr> parser, Parser<UnaryOperator<CelExpr>> op) {
    return parser.withPostfixes(
        op.mapWithIndex((f, begin, end) -> v -> f.apply(v).withSourceIndex(begin)));
  }

  private static <T, S> Parser<Function<T, CelExpr>> suffixWithIndex(
      Parser<S> suffix, BiFunction<? super T, ? super S, ? extends CelExpr> combiner) {
    java.util.Objects.requireNonNull(combiner);
    return suffix.mapWithIndex((s, begin, end) -> p -> combiner.apply(p, s).withSourceIndex(begin));
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
