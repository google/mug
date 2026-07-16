package com.google.common.labs.cel;

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
import com.google.errorprone.annotations.Immutable;
import com.google.mu.util.CharPredicate;

/**
 * Parser for Common Expression Language (CEL) syntax, producing {@code CelExpr} AST.
 *
 * @since 10.7
 */
@Immutable
public final class CelParser {
  private static final Set<String> KEYWORDS = Set.of(
      "as", "break", "const", "continue", "else", "false", "for", "function", "if",
      "import", "in", "let", "loop", "package", "namespace", "null", "return", "true",
      "var", "void", "while");
  private static final CharPredicate WHITESPACES = CharPredicate.anyOf(" \t\r\n\f").precomputeForAscii();
  private static final Parser<?> WHITESPACES_OR_COMMENTS =
      anyOf(consecutive("[ \t\r\n\f]"), sequence(string("//"), zeroOrMore("[^\n]")));

  private static final Parser<Boolean>.OrEmpty OPTIONALITY =
      one('?').thenReturn(true).orElse(false);

  static final Parser<String> IDENTIFIER =
      literally(sequence(one("[a-zA-Z_]"), zeroOrMore("[a-zA-Z0-9_]")))
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
                      new BytesLexer().parser(),
                      RAW_STRING_LITERAL.map(s -> s.getBytes(UTF_8)))));

  private static final Parser<String> HEX_DIGITS = consecutive("[0-9a-fA-F]");

  private static final Parser<Long> HEX_INT =
      sequence(
          string("-").orElse(""),
          literally(caseInsensitive("0x").then(HEX_DIGITS)),
          (sign, d) -> Long.parseLong(sign + d, 16));

  private static final Parser<Long> DEC_INT =
      sequence(string("-").orElse(""), digits(), (sign, d) -> Long.parseLong(sign + d));

  private static final Parser<Long> NUM_UINT =
      literally(
          anyOf(
                  string("0x").then(HEX_DIGITS).map(s -> Long.parseUnsignedLong(s, 16)),
                  digits().map(Long::parseUnsignedLong))
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
              .map(Double::parseDouble),
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

  private static final Parser<CelExpr> PARSER = makeParser();

  /** Parses the given CEL expression. */
  public CelExpr parse(String input) {
    return PARSER.parseSkipping(WHITESPACES, input);
  }

  /** Parses the given CEL expression, supporting comments. */
  public CelExpr parseWithComments(String input) {
    return PARSER.parseSkipping(WHITESPACES_OR_COMMENTS, input);
  }

  private static Parser<CelExpr> makeParser() {
    Parser.Rule<CelExpr> regular = new Parser.Rule<>();
    Parser.Rule<CelExpr> expr = new Parser.Rule<>();
    Parser<CelExpr> parenthesized = expr.between("(", ")");

    Parser<CelExpr> listExpr =
        sequence(OPTIONALITY, expr, (opt, val) -> new CelExpr.ListLiteral.Element(val, opt))
            .zeroOrMoreDelimitedBy(",")
            .optionallyFollowedBy(",")
            .between("[", "]")
            .map(CelExpr.ListLiteral::new);
    Parser<CelExpr> mapExpr =
        sequence(OPTIONALITY, expr.followedBy(":"), expr, (opt, key, val) -> new CelExpr.MapLiteral.Entry(key, val, opt))
            .zeroOrMoreDelimitedBy(",")
            .optionallyFollowedBy(",")
            .between("{", "}")
            .map(CelExpr.MapLiteral::new);
    Parser<CelExpr.StructLiteral.Field> messageFieldInit =
        sequence(OPTIONALITY, ANY_IDENTIFIER.followedBy(":"), expr, (opt, name, val) -> new CelExpr.StructLiteral.Field(name, val, opt));
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
    Parser<MemberCallOp> memberCallOp =
        sequence(ANY_IDENTIFIER, args, MemberCallOp::new)
            .suchThat(MemberCallOp::isValidMacroCall, "valid macro call");
    Parser<UnaryOperator<CelExpr>> indexOp =
        sequence(
            one('?').thenReturn(true).orElse(false),
            expr,
            CelParser::indexCall);
    Parser<CelExpr> memberExpr =
        primary.withPostfixes(
            anyOf(
                one('.')
                    .then(
                        anyOf(
                            memberCallOp,
                            one('?').then(ANY_IDENTIFIER).map(CelParser::optionalSelect),
                            ANY_IDENTIFIER.map(CelParser::select))),
                indexOp.between("[", "]")));
    Parser<CelExpr> unaryExpr =
        anyOf(
            memberExpr.withPrefixes(
                one('!').thenReturn(e -> new CelExpr.Unary(CelExpr.Unary.Op.NOT, e))),
            memberExpr.withPrefixes(
                one('-').thenReturn(e -> new CelExpr.Unary(CelExpr.Unary.Op.MINUS, e))));
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
                    .map(trueExpr -> (cond, falseExpr) -> new CelExpr.Ternary(cond, trueExpr, falseExpr)),
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

  private static final Set<String> MACRO_METHODS =
      Set.of("all", "exists", "exists_one", "map", "filter");

  private static UnaryOperator<CelExpr> optionalSelect(String field) {
    return receiver -> new CelExpr.Call(
        Optional.of(receiver), "optionalSelect", List.of(new CelExpr.StringValue(field)));
  }

  private static UnaryOperator<CelExpr> select(String field) {
    return receiver -> new CelExpr.Select(receiver, field);
  }

  private static UnaryOperator<CelExpr> memberCall(String method, List<CelExpr> args) {
    return receiver -> new CelExpr.Call(Optional.of(receiver), method, args);
  }

  private static UnaryOperator<CelExpr> indexCall(boolean optional, CelExpr index) {
    return optional
        ? receiver -> new CelExpr.Call(Optional.of(receiver), "optionalIndex", List.of(index))
        : receiver -> new CelExpr.Index(receiver, index);
  }

  private static CelExpr call(CelExpr receiver, List<CelExpr> args) {
    return switch (receiver) {
      case CelExpr.Ident ident -> new CelExpr.Call(Optional.empty(), ident.name(), args);
      case CelExpr.Select select -> new CelExpr.Call(Optional.of(select.operand()), select.field(), args);
      default -> throw new AssertionError("Invalid call receiver: " + receiver);
    };
  }

  private static CelExpr structExpr(CelExpr receiver, List<CelExpr.StructLiteral.Field> fields) {
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

  private record MemberCallOp(String method, List<CelExpr> args) implements UnaryOperator<CelExpr> {
    boolean isValidMacroCall() {
      if (MACRO_METHODS.contains(method)) {
        int minArgs = 2;
        int maxArgs = method.equals("map") ? 3 : 2;
        return args.size() >= minArgs && args.size() <= maxArgs && args.get(0) instanceof CelExpr.Ident;
      }
      return true;
    }

    @Override public CelExpr apply(CelExpr receiver) {
      return memberCall(method, args).apply(receiver);
    }
  }
}
