package com.google.mu.cel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.google.api.expr.v1alpha1.Expr;
import com.google.mu.util.stream.Joiner;

/**
 * Abstract representation of a parsed Common Expression Language (CEL) expression.
 *
 * @since 10.7
 */
public sealed interface CelExpr {
  /**
   * Parses and returns a {@link CelExpr} representing the {@code cel} string.
   *
   * <p>By default comments are not supported. Use {@link CelParser#parseWithComments} if
   * you need comments.
   *
   * @throws Parser.ParseException if {@code cel} is invalid
   * @throws NullPointerException if {@code cel} is null
   */
  static CelExpr of(String cel) {
    return new CelParser().parse(cel);
  }

  /** Converts this expression to the official CEL Expr Protobuf representation. */
  default Expr toProto() {
    return CelProtoConverter.toProto(this, new AtomicLong(1));
  }

  static BoolValue value(boolean value) {
    return new BoolValue(value);
  }

  static LongValue value(long value) {
    return new LongValue(value);
  }

  static DoubleValue value(double value) {
    return new DoubleValue(value);
  }

  static StringValue string(String value) {
    return new StringValue(value);
  }

  static BytesValue bytes(byte... value) {
    return new BytesValue(value);
  }

  static UintValue unsigned(long value) {
    return new UintValue(value);
  }

  static StructLiteral struct(String messageName, Map<String, Element> fields) {
    return new StructLiteral(
        messageName,
        fields.entrySet().stream()
            .map(e -> new Entry<>(
                new Ident(e.getKey()), e.getValue().value(), e.getValue().optional()))
            .toList());
  }

  static Unary negative(CelExpr expr) {
    return new Unary(Unary.Op.NEGATIVE, expr);
  }

  static Unary not(CelExpr expr) {
    return new Unary(Unary.Op.NOT, expr);
  }

  static Call callFunction(String function, List<CelExpr> args) {
    return new Call(Optional.empty(), function, args);
  }

  default Select select(String field) {
    return new Select(this, field);
  }

  default Index index(CelExpr index) {
    return new Index(this, index);
  }

  default Binary add(CelExpr that) {
    return new Binary(this, Binary.Op.ADD, that);
  }

  default Binary subtract(CelExpr that) {
    return new Binary(this, Binary.Op.SUB, that);
  }

  default Binary multiply(CelExpr that) {
    return new Binary(this, Binary.Op.MULT, that);
  }

  default Binary divide(CelExpr that) {
    return new Binary(this, Binary.Op.DIV, that);
  }

  default Binary modulo(CelExpr that) {
    return new Binary(this, Binary.Op.MOD, that);
  }

  default Binary equalTo(CelExpr that) {
    return new Binary(this, Binary.Op.EQ, that);
  }

  default Binary notEqualTo(CelExpr that) {
    return new Binary(this, Binary.Op.NE, that);
  }

  default Binary lessThan(CelExpr that) {
    return new Binary(this, Binary.Op.LT, that);
  }

  default Binary atMost(CelExpr ceiling) {
    return new Binary(this, Binary.Op.LE, ceiling);
  }

  default Binary greaterThan(CelExpr that) {
    return new Binary(this, Binary.Op.GT, that);
  }

  default Binary atLeast(CelExpr floor) {
    return new Binary(this, Binary.Op.GE, floor);
  }

  default Binary in(CelExpr that) {
    return new Binary(this, Binary.Op.IN, that);
  }

  default Binary and(CelExpr that) {
    return new Binary(this, Binary.Op.AND, that);
  }

  default Binary or(CelExpr that) {
    return new Binary(this, Binary.Op.OR, that);
  }

  default Ternary ifElse(CelExpr ifTrue, CelExpr ifFalse) {
    return new Ternary(this, ifTrue, ifFalse);
  }

  default Call call(String member, List<CelExpr> args) {
    return new Call(Optional.of(this), member, args);
  }

  /** Null literal. */
  record NullValue() implements CelExpr {
    @Override public String toString() {
      return "null";
    }
  }

  /** Boolean literal. */
  record BoolValue(boolean value) implements CelExpr {
    @Override public String toString() {
      return String.valueOf(value);
    }
  }

  /** Signed 64-bit integer literal. */
  record LongValue(long value) implements CelExpr {
    @Override public String toString() {
      return String.valueOf(value);
    }
  }

  /** Unsigned 64-bit integer literal. */
  record UintValue(long value) implements CelExpr {
    @Override public String toString() {
      return Long.toUnsignedString(value) + "u";
    }
  }

  /** Double-precision floating point literal. */
  record DoubleValue(double value) implements CelExpr {
    @Override public String toString() {
      return String.valueOf(value);
    }
  }

  /** UTF-8 string literal. */
  record StringValue(String value) implements CelExpr {
    @Override public String toString() {
      return escapeString(value);
    }
  }

  /** Byte sequence literal. */
  record BytesValue(byte[] value) implements CelExpr {
    @Override public String toString() {
      return escapeBytes(value);
    }
    @Override public boolean equals(Object obj) {
      return obj instanceof BytesValue other && java.util.Arrays.equals(value, other.value);
    }
    @Override public int hashCode() {
      return java.util.Arrays.hashCode(value);
    }
  }

  /** Variable or symbol lookup (e.g. {@code request}). */
  record Ident(String name) implements CelExpr {
    @Override public String toString() {
      return CelParser.IDENTIFIER.matches(name) ? name : "`" + name + "`";
    }
  }

  /** Field selection (e.g. {@code operand.field}). */
  record Select(CelExpr operand, String field) implements CelExpr {
    @Override public String toString() {
      return "(" + operand + ")." + new Ident(field);
    }
  }

  /** Subscript/indexing (e.g. {@code operand[index]}). */
  record Index(CelExpr operand, CelExpr index) implements CelExpr {
    @Override public String toString() {
      return "(" + operand + ")[" + index + "]";
    }
  }

  /** Unary prefix operations (e.g. {@code -x}, {@code !x}). */
  record Unary(Op operator, CelExpr operand) implements CelExpr {
    public enum Op {
      NEGATIVE("-"), NOT("!");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      @Override public String toString() {
        return symbol;
      }
    }

    @Override public String toString() {
      return operator.toString() + "(" + operand + ")";
    }
  }

  /** Binary operations (e.g. {@code x + y}, {@code x == y}, {@code x && y}). */
  record Binary(CelExpr left, Op operator, CelExpr right) implements CelExpr {
    public enum Op {
      ADD("+"), SUB("-"), MULT("*"), DIV("/"), MOD("%"),
      EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="), IN("in"),
      AND("&&"), OR("||");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      @Override public String toString() {
        return symbol;
      }
    }

    @Override public String toString() {
      return "(" + left + ") " + operator + " (" + right + ")";
    }
  }

  /** Ternary conditional (e.g. {@code condition ? trueExpr : falseExpr}). */
  record Ternary(CelExpr condition, CelExpr ifTrue, CelExpr ifFalse) implements CelExpr {
    @Override public String toString() {
      return "(" + condition + ") ? (" + ifTrue + ") : (" + ifFalse + ")";
    }
  }

  /** Global function calls {@code f(args)} or member calls {@code target.f(args)}. */
  record Call(Optional<CelExpr> target, String function, List<CelExpr> args) implements CelExpr {
    @Override public String toString() {
      return target.map(t -> "(" + t + ").").orElse("")
          + function
          + args.stream().collect(Joiner.on(", ").between('(', ')'));
    }
  }

  /** List creation (e.g. {@code [1, ?optional_var]}). */
  record ListLiteral(List<Element> elements) implements CelExpr {
    @Override public String toString() {
      return elements.stream().collect(Joiner.on(", ").between('[', ']'));
    }
  }

  /** Map creation (e.g. {@code {"key": value, ? "opt_key": value}}). */
  record MapLiteral(List<Entry<CelExpr>> entries) implements CelExpr {
    @Override public String toString() {
      return entries.stream().collect(Joiner.on(", ").between('{', '}'));
    }
  }

  /** Struct/message creation (e.g. {@code Type{field: value, ?opt_field: value}}). */
  record StructLiteral(String messageName, List<Entry<Ident>> fields) implements CelExpr {
    @Override public String toString() {
      return messageName + fields.stream().collect(Joiner.on(", ").between('{', '}'));
    }
  }

  /** An entry/field representing a key-value or field-value mapping in map or struct literals. */
  record Entry<K>(K key, CelExpr value, boolean optional) {
    public static Entry<Ident> of(String key, CelExpr value) {
      return new Entry<>(new Ident(key), value, false);
    }

    public static Entry<Ident> optional(String key, CelExpr value) {
      return new Entry<>(new Ident(key), value, true);
    }

    public static Entry<CelExpr> of(CelExpr key, CelExpr value) {
      return new Entry<>(key, value, false);
    }

    public static Entry<CelExpr> optional(CelExpr key, CelExpr value) {
      return new Entry<>(key, value, true);
    }

    @Override public String toString() {
      return optionalMarker(optional) + key + ": " + value;
    }
  }

  /** Abstract representation of a CEL macro expression. */
  sealed interface Macro extends CelExpr {
    /** The {@code has(member)} macro. */
    record Has(Select member) implements Macro {
      @Override public String toString() {
        return "has(" + member + ")";
      }
    }

    /** The {@code target.all(varName, condition)} macro. */
    record All(CelExpr target, String varName, CelExpr condition) implements Macro {
      @Override public String toString() {
        return target + ".all(" + varName + ", " + condition + ")";
      }
    }

    /** The {@code target.exists(varName, condition)} macro. */
    record Exists(CelExpr target, String varName, CelExpr condition) implements Macro {
      @Override public String toString() {
        return target + ".exists(" + varName + ", " + condition + ")";
      }
    }

    /** The {@code target.exists_one(varName, condition)} macro. */
    record ExistsOne(CelExpr target, String varName, CelExpr condition) implements Macro {
      @Override public String toString() {
        return target + ".exists_one(" + varName + ", " + condition + ")";
      }
    }

    /** The {@code target.filter(varName, expr)} macro. */
    record Filter(CelExpr target, String varName, CelExpr expr) implements Macro {
      @Override public String toString() {
        return target + ".filter(" + varName + ", " + expr + ")";
      }
    }

    /** The {@code target.map(varName, expr)} macro. */
    record Map(CelExpr target, String varName, CelExpr expr) implements Macro {
      @Override public String toString() {
        return target + ".map(" + varName + ", " + expr + ")";
      }
    }

    /** The {@code target.map(varName, filter, transform)} macro. */
    record FilterMap(CelExpr target, String varName, CelExpr filter, CelExpr transform)
        implements Macro {
      @Override public String toString() {
        return target + ".map(" + varName + ", " + filter + ", " + transform + ")";
      }
    }
  }

  record Element(CelExpr value, boolean optional) {
    @Override public String toString() {
      return optionalMarker(optional) + value;
    }
  }

  private static String escapeString(String s) {
    return s.codePoints()
        .mapToObj(c -> switch (c) {
          case '\\' -> "\\\\";
          case '"' -> "\\\"";
          case '\n' -> "\\n";
          case '\r' -> "\\r";
          case '\t' -> "\\t";
          default -> Character.toString(c);
        })
        .collect(Joiner.on("").between('"', '"'));
  }

  private static String escapeBytes(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    builder.append("b\"");
    for (byte b : bytes) {
      builder.append(String.format("\\x%02x", b));
    }
    builder.append('"');
    return builder.toString();
  }

  private static String optionalMarker(boolean optional) {
    return optional ? "?" : "";
  }
}
