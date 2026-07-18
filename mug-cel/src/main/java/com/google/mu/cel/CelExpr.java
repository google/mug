package com.google.mu.cel;

import java.util.List;
import java.util.Map;

import com.google.api.expr.v1alpha1.Expr;
import com.google.mu.util.stream.Joiner;

/**
 * Abstract representation of a parsed Common Expression Language (CEL) expression.
 *
 * @since 10.7
 */
public sealed interface CelExpr {
  /** Returns the 0-based source character index where this expression starts. */
  int sourceIndex();

  /** Returns a copy of this expression with the given source index. */
  CelExpr withSourceIndex(int index);

  /**
   * Parses and returns a {@link CelExpr} representing the {@code cel} string.
   *
   * <p>By default comments are not supported. Use {@link CelParser#withComments} if you need
   * comments.
   *
   * @throws Parser.ParseException if {@code cel} is invalid
   * @throws NullPointerException if {@code cel} is null
   */
  static CelExpr of(String cel) {
    return new CelParser().parse(cel);
  }

  /** Converts this expression to the official CEL Expr Protobuf representation. */
  default Expr toProto() {
    return CelProtoConverter.toProto(this);
  }

  /** {@code CelExpr.value(true)} is equivalent to {@code CelExpr.of("true")}. */
  static BoolValue value(boolean value) {
    return new BoolValue(value);
  }

  /** {@code CelExpr.value(123)} is equivalent to {@code CelExpr.of("123")}. */
  static LongValue value(long value) {
    return new LongValue(value);
  }

  /** {@code CelExpr.value(12.3)} is equivalent to {@code CelExpr.of("12.3")}. */
  static DoubleValue value(double value) {
    return new DoubleValue(value);
  }

  /** {@code CelExpr.value("foo")} is equivalent to {@code CelExpr.of("'foo'")}. */
  static StringValue string(String value) {
    return new StringValue(value);
  }

  /**
   * {@code CelExpr.bytes((byte) 1, (byte) 2)} is equivalent to {@code CelExpr.of("b'\\x01\\x02'")}.
   */
  static BytesValue bytes(byte... value) {
    return new BytesValue(value);
  }

  /** {@code CelExpr.unsigned(123)} is equivalent to {@code CelExpr.of("123u")}. */
  static UintValue unsigned(long value) {
    return new UintValue(value);
  }

  /**
   * {@code CelExpr.struct("MyMsg", Map.of("field", new Element(value(1), true)))} is equivalent to
   * {@code CelExpr.of("MyMsg{?field: 1}")}.
   */
  static StructLiteral struct(String messageName, Map<String, Element> fields) {
    return new StructLiteral(
        messageName,
        fields.entrySet().stream()
            .map(
                e ->
                    new Entry<>(
                        new Ident(e.getKey()), e.getValue().value(), e.getValue().optional()))
            .toList());
  }

  /** {@code CelExpr.negative(value(5))} is equivalent to {@code CelExpr.of("-(5)")}. */
  static Unary negative(CelExpr expr) {
    return new Unary(Unary.Op.NEGATIVE, expr);
  }

  /** {@code CelExpr.not(value(true))} is equivalent to {@code CelExpr.of("!true")}. */
  static Unary not(CelExpr expr) {
    return new Unary(Unary.Op.NOT, expr);
  }

  /** {@code expr.select("field")} is equivalent to {@code CelExpr.of("expr.field")}. */
  default Select select(String field) {
    return new Select(this, field);
  }

  /** {@code expr.index(value(0))} is equivalent to {@code CelExpr.of("expr[0]")}. */
  default Index index(CelExpr index) {
    return new Index(this, index);
  }

  /** {@code expr.optionalSelect("field")} is equivalent to {@code CelExpr.of("expr.?field")}. */
  default OptionalSelect optionalSelect(String field) {
    return new OptionalSelect(this, field);
  }

  /** {@code expr.optionalIndex(value(0))} is equivalent to {@code CelExpr.of("expr[?0]")}. */
  default OptionalIndex optionalIndex(CelExpr index) {
    return new OptionalIndex(this, index);
  }

  /** {@code a.add(b)} is equivalent to {@code CelExpr.of("a + b")}. */
  default Binary add(CelExpr that) {
    return new Binary(this, Binary.Op.ADD, that);
  }

  /** {@code a.subtract(b)} is equivalent to {@code CelExpr.of("a - b")}. */
  default Binary subtract(CelExpr that) {
    return new Binary(this, Binary.Op.SUB, that);
  }

  /** {@code a.multiply(b)} is equivalent to {@code CelExpr.of("a * b")}. */
  default Binary multiply(CelExpr that) {
    return new Binary(this, Binary.Op.MULT, that);
  }

  /** {@code a.divide(b)} is equivalent to {@code CelExpr.of("a / b")}. */
  default Binary divide(CelExpr that) {
    return new Binary(this, Binary.Op.DIV, that);
  }

  /** {@code a.modulo(b)} is equivalent to {@code CelExpr.of("a % b")}. */
  default Binary modulo(CelExpr that) {
    return new Binary(this, Binary.Op.MOD, that);
  }

  /** {@code a.equalTo(b)} is equivalent to {@code CelExpr.of("a == b")}. */
  default Binary equalTo(CelExpr that) {
    return new Binary(this, Binary.Op.EQ, that);
  }

  /** {@code a.notEqualTo(b)} is equivalent to {@code CelExpr.of("a != b")}. */
  default Binary notEqualTo(CelExpr that) {
    return new Binary(this, Binary.Op.NE, that);
  }

  /** {@code a.lessThan(b)} is equivalent to {@code CelExpr.of("a < b")}. */
  default Binary lessThan(CelExpr that) {
    return new Binary(this, Binary.Op.LT, that);
  }

  /** {@code a.atMost(b)} is equivalent to {@code CelExpr.of("a <= b")}. */
  default Binary atMost(CelExpr ceiling) {
    return new Binary(this, Binary.Op.LE, ceiling);
  }

  /** {@code a.greaterThan(b)} is equivalent to {@code CelExpr.of("a > b")}. */
  default Binary greaterThan(CelExpr that) {
    return new Binary(this, Binary.Op.GT, that);
  }

  /** {@code a.atLeast(b)} is equivalent to {@code CelExpr.of("a >= b")}. */
  default Binary atLeast(CelExpr floor) {
    return new Binary(this, Binary.Op.GE, floor);
  }

  /** {@code a.in(b)} is equivalent to {@code CelExpr.of("a in b")}. */
  default Binary in(CelExpr that) {
    return new Binary(this, Binary.Op.IN, that);
  }

  /** {@code a.and(b)} is equivalent to {@code CelExpr.of("a && b")}. */
  default Binary and(CelExpr that) {
    return new Binary(this, Binary.Op.AND, that);
  }

  /** {@code a.or(b)} is equivalent to {@code CelExpr.of("a || b")}. */
  default Binary or(CelExpr that) {
    return new Binary(this, Binary.Op.OR, that);
  }

  /** {@code active.ifElse(1, 0)} is equivalent to {@code CelExpr.of("active ? 1 : 0")}. */
  default Ternary ifElse(CelExpr ifTrue, CelExpr ifFalse) {
    return new Ternary(this, ifTrue, ifFalse);
  }

  /**
   * {@code target.call("member", args)} is equivalent to {@code CelExpr.of("target.member(args)")}.
   */
  default MemberCall call(String member, List<CelExpr> args) {
    return new MemberCall(this, new Ident(member), args);
  }

  /**
   * {@code target.call(member, args)} is equivalent to {@code CelExpr.of("target.member(args)")}.
   */
  default MemberCall call(Ident member, List<CelExpr> args) {
    return new MemberCall(this, member, args);
  }

  /** Null literal. */
  record NullValue(int sourceIndex) implements CelExpr {
    public NullValue() {
      this(0);
    }

    @Override
    public NullValue withSourceIndex(int index) {
      return new NullValue(index);
    }

    @Override
    public String toString() {
      return "null";
    }
  }

  /** Boolean literal. */
  record BoolValue(int sourceIndex, boolean value) implements CelExpr {
    public BoolValue(boolean value) {
      this(0, value);
    }

    @Override
    public BoolValue withSourceIndex(int index) {
      return new BoolValue(index, value);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  /** Signed 64-bit integer literal. */
  record LongValue(int sourceIndex, long value) implements CelExpr {
    public LongValue(long value) {
      this(0, value);
    }

    @Override
    public LongValue withSourceIndex(int index) {
      return new LongValue(index, value);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  /** Unsigned 64-bit integer literal. */
  record UintValue(int sourceIndex, long value) implements CelExpr {
    public UintValue(long value) {
      this(0, value);
    }

    @Override
    public UintValue withSourceIndex(int index) {
      return new UintValue(index, value);
    }

    @Override
    public String toString() {
      return Long.toUnsignedString(value) + "u";
    }
  }

  /** Double-precision floating point literal. */
  record DoubleValue(int sourceIndex, double value) implements CelExpr {
    public DoubleValue(double value) {
      this(0, value);
    }

    @Override
    public DoubleValue withSourceIndex(int index) {
      return new DoubleValue(index, value);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  /** UTF-8 string literal. */
  record StringValue(int sourceIndex, String value) implements CelExpr {
    public StringValue(String value) {
      this(0, value);
    }

    @Override
    public StringValue withSourceIndex(int index) {
      return new StringValue(index, value);
    }

    @Override
    public String toString() {
      return escapeString(value);
    }
  }

  /** Byte sequence literal. */
  record BytesValue(int sourceIndex, byte[] value) implements CelExpr {
    public BytesValue(byte[] value) {
      this(0, value);
    }

    @Override
    public BytesValue withSourceIndex(int index) {
      return new BytesValue(index, value);
    }

    @Override
    public String toString() {
      return escapeBytes(value);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof BytesValue other && java.util.Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.hashCode(value);
    }
  }

  /** Variable or symbol lookup (e.g. {@code request}). */
  record Ident(int sourceIndex, String name) implements CelExpr {
    public Ident(String name) {
      this(0, name);
    }

    @Override
    public Ident withSourceIndex(int index) {
      return new Ident(index, name);
    }

    @Override
    public String toString() {
      return CelParser.IDENTIFIER.matches(name) ? name : "`" + name + "`";
    }
  }

  /** Field selection (e.g. {@code operand.field}). */
  record Select(int sourceIndex, CelExpr operand, String field) implements CelExpr {
    public Select(CelExpr operand, String field) {
      this(operand.sourceIndex(), operand, field);
    }

    @Override
    public Select withSourceIndex(int index) {
      return new Select(index, operand, field);
    }

    @Override
    public String toString() {
      return "(" + operand + ")." + new Ident(field);
    }
  }

  /** Subscript/indexing (e.g. {@code operand[index]}). */
  record Index(int sourceIndex, CelExpr operand, CelExpr index) implements CelExpr {
    public Index(CelExpr operand, CelExpr index) {
      this(operand.sourceIndex(), operand, index);
    }

    @Override
    public Index withSourceIndex(int index) {
      return new Index(index, operand, this.index);
    }

    @Override
    public String toString() {
      return "(" + operand + ")[" + index + "]";
    }
  }

  /** Optional field selection (e.g. {@code operand.?field}). */
  record OptionalSelect(int sourceIndex, CelExpr operand, String field) implements CelExpr {
    public OptionalSelect(CelExpr operand, String field) {
      this(operand.sourceIndex(), operand, field);
    }

    @Override
    public OptionalSelect withSourceIndex(int index) {
      return new OptionalSelect(index, operand, field);
    }

    @Override
    public String toString() {
      return "(" + operand + ").?" + new Ident(field);
    }
  }

  /** Optional subscript/indexing (e.g. {@code operand[?index]}). */
  record OptionalIndex(int sourceIndex, CelExpr operand, CelExpr index) implements CelExpr {
    public OptionalIndex(CelExpr operand, CelExpr index) {
      this(operand.sourceIndex(), operand, index);
    }

    @Override
    public OptionalIndex withSourceIndex(int index) {
      return new OptionalIndex(index, operand, this.index);
    }

    @Override
    public String toString() {
      return "(" + operand + ")[?" + index + "]";
    }
  }

  /** Unary prefix operations (e.g. {@code -x}, {@code !x}). */
  record Unary(int sourceIndex, Op operator, CelExpr operand) implements CelExpr {
    public Unary(Op operator, CelExpr operand) {
      this(0, operator, operand);
    }

    @Override
    public Unary withSourceIndex(int index) {
      return new Unary(index, operator, operand);
    }

    public enum Op {
      NEGATIVE("-"),
      NOT("!");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      @Override
      public String toString() {
        return symbol;
      }
    }

    @Override
    public String toString() {
      return operator.toString() + "(" + operand + ")";
    }
  }

  /** Binary operations (e.g. {@code x + y}, {@code x == y}, {@code x && y}). */
  record Binary(int sourceIndex, CelExpr left, Op operator, CelExpr right) implements CelExpr {
    public Binary(CelExpr left, Op operator, CelExpr right) {
      this(left.sourceIndex(), left, operator, right);
    }

    @Override
    public Binary withSourceIndex(int index) {
      return new Binary(index, left, operator, right);
    }

    public enum Op {
      ADD("+"),
      SUB("-"),
      MULT("*"),
      DIV("/"),
      MOD("%"),
      EQ("=="),
      NE("!="),
      LT("<"),
      LE("<="),
      GT(">"),
      GE(">="),
      IN("in"),
      AND("&&"),
      OR("||");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      @Override
      public String toString() {
        return symbol;
      }
    }

    @Override
    public String toString() {
      return "(" + left + ") " + operator + " (" + right + ")";
    }
  }

  /** Ternary conditional (e.g. {@code condition ? trueExpr : falseExpr}). */
  record Ternary(int sourceIndex, CelExpr condition, CelExpr ifTrue, CelExpr ifFalse)
      implements CelExpr {
    public Ternary(CelExpr condition, CelExpr ifTrue, CelExpr ifFalse) {
      this(condition.sourceIndex(), condition, ifTrue, ifFalse);
    }

    @Override
    public Ternary withSourceIndex(int index) {
      return new Ternary(index, condition, ifTrue, ifFalse);
    }

    @Override
    public String toString() {
      return "(" + condition + ") ? (" + ifTrue + ") : (" + ifFalse + ")";
    }
  }

  /** Global function calls {@code f(args)}. */
  record FunctionCall(Ident function, List<CelExpr> args) implements CelExpr {

    @Override
    public int sourceIndex() {
      return function.sourceIndex();
    }

    @Override
    public FunctionCall withSourceIndex(int index) {
      return new FunctionCall(function.withSourceIndex(index), args);
    }

    @Override
    public String toString() {
      return function.name() + args.stream().collect(Joiner.on(", ").between('(', ')'));
    }
  }

  /** Member calls {@code target.f(args)}. */
  record MemberCall(CelExpr target, Ident member, List<CelExpr> args) implements CelExpr {
    @Override
    public int sourceIndex() {
      return target.sourceIndex();
    }

    @Override
    public MemberCall withSourceIndex(int index) {
      return new MemberCall(target.withSourceIndex(index), member, args);
    }

    @Override
    public String toString() {
      return "("
          + target
          + ")."
          + member.name()
          + args.stream().collect(Joiner.on(", ").between('(', ')'));
    }
  }

  /** List creation (e.g. {@code [1, ?optional_var]}). */
  record ListLiteral(int sourceIndex, List<Element> elements) implements CelExpr {
    public ListLiteral(List<Element> elements) {
      this(0, elements);
    }

    @Override
    public ListLiteral withSourceIndex(int index) {
      return new ListLiteral(index, elements);
    }

    @Override
    public String toString() {
      return elements.stream().collect(Joiner.on(", ").between('[', ']'));
    }
  }

  /** Map creation (e.g. {@code {"key": value, ? "opt_key": value}}). */
  record MapLiteral(int sourceIndex, List<Entry<CelExpr>> entries) implements CelExpr {
    public MapLiteral(List<Entry<CelExpr>> entries) {
      this(0, entries);
    }

    @Override
    public MapLiteral withSourceIndex(int index) {
      return new MapLiteral(index, entries);
    }

    @Override
    public String toString() {
      return entries.stream().collect(Joiner.on(", ").between('{', '}'));
    }
  }

  /** Struct/message creation (e.g. {@code Type{field: value, ?opt_field: value}}). */
  record StructLiteral(int sourceIndex, String messageName, List<Entry<Ident>> fields)
      implements CelExpr {
    public StructLiteral(String messageName, List<Entry<Ident>> fields) {
      this(0, messageName, fields);
    }

    @Override
    public StructLiteral withSourceIndex(int index) {
      return new StructLiteral(index, messageName, fields);
    }

    @Override
    public String toString() {
      return messageName + fields.stream().collect(Joiner.on(", ").between('{', '}'));
    }
  }

  /** An entry/field representing a key-value or field-value mapping in map or struct literals. */
  record Entry<K>(K key, CelExpr value, boolean optional) {
    /** {@code Entry.of("field", value(1))} is equivalent to {@code CelExpr.of("field: 1")}. */
    public static Entry<Ident> of(String key, CelExpr value) {
      return new Entry<>(new Ident(key), value, false);
    }

    /**
     * {@code Entry.optional("field", value(1))} is equivalent to {@code CelExpr.of("?field: 1")}.
     */
    public static Entry<Ident> optional(String key, CelExpr value) {
      return new Entry<>(new Ident(key), value, true);
    }

    /**
     * {@code Entry.of(string("key"), value(1))} is equivalent to {@code CelExpr.of("'key': 1")}.
     */
    public static Entry<CelExpr> of(CelExpr key, CelExpr value) {
      return new Entry<>(key, value, false);
    }

    /**
     * {@code Entry.optional(string("key"), value(1))} is equivalent to {@code CelExpr.of("? 'key':
     * 1")}.
     */
    public static Entry<CelExpr> optional(CelExpr key, CelExpr value) {
      return new Entry<>(key, value, true);
    }

    @Override
    public String toString() {
      return optionalMarker(optional) + key + ": " + value;
    }
  }

  /** Abstract representation of a CEL macro expression. */
  sealed interface Macro extends CelExpr {
    /** The {@code has(member)} macro. */
    record Has(int sourceIndex, Select member) implements Macro {
      public Has(Select member) {
        this(0, member);
      }

      @Override
      public Has withSourceIndex(int index) {
        return new Has(index, member);
      }

      @Override
      public String toString() {
        return "has(" + member + ")";
      }
    }

    /** The {@code target.all(varName, condition)} macro. */
    record All(int sourceIndex, CelExpr target, String varName, CelExpr condition)
        implements Macro {
      public All(CelExpr target, String varName, CelExpr condition) {
        this(target.sourceIndex(), target, varName, condition);
      }

      @Override
      public All withSourceIndex(int index) {
        return new All(index, target, varName, condition);
      }

      @Override
      public String toString() {
        return target + ".all(" + varName + ", " + condition + ")";
      }
    }

    /** The {@code target.exists(varName, condition)} macro. */
    record Exists(int sourceIndex, CelExpr target, String varName, CelExpr condition)
        implements Macro {
      public Exists(CelExpr target, String varName, CelExpr condition) {
        this(target.sourceIndex(), target, varName, condition);
      }

      @Override
      public Exists withSourceIndex(int index) {
        return new Exists(index, target, varName, condition);
      }

      @Override
      public String toString() {
        return target + ".exists(" + varName + ", " + condition + ")";
      }
    }

    /** The {@code target.exists_one(varName, condition)} macro. */
    record ExistsOne(int sourceIndex, CelExpr target, String varName, CelExpr condition)
        implements Macro {
      public ExistsOne(CelExpr target, String varName, CelExpr condition) {
        this(target.sourceIndex(), target, varName, condition);
      }

      @Override
      public ExistsOne withSourceIndex(int index) {
        return new ExistsOne(index, target, varName, condition);
      }

      @Override
      public String toString() {
        return target + ".exists_one(" + varName + ", " + condition + ")";
      }
    }

    /** The {@code target.filter(varName, expr)} macro. */
    record Filter(int sourceIndex, CelExpr target, String varName, CelExpr expr) implements Macro {
      public Filter(CelExpr target, String varName, CelExpr expr) {
        this(target.sourceIndex(), target, varName, expr);
      }

      @Override
      public Filter withSourceIndex(int index) {
        return new Filter(index, target, varName, expr);
      }

      @Override
      public String toString() {
        return target + ".filter(" + varName + ", " + expr + ")";
      }
    }

    /** The {@code target.map(varName, expr)} macro. */
    record Map(int sourceIndex, CelExpr target, String varName, CelExpr expr) implements Macro {
      public Map(CelExpr target, String varName, CelExpr expr) {
        this(target.sourceIndex(), target, varName, expr);
      }

      @Override
      public Map withSourceIndex(int index) {
        return new Map(index, target, varName, expr);
      }

      @Override
      public String toString() {
        return target + ".map(" + varName + ", " + expr + ")";
      }
    }

    /** The {@code target.map(varName, filter, transform)} macro. */
    record FilterMap(
        int sourceIndex, CelExpr target, String varName, CelExpr filter, CelExpr transform)
        implements Macro {
      public FilterMap(CelExpr target, String varName, CelExpr filter, CelExpr transform) {
        this(target.sourceIndex(), target, varName, filter, transform);
      }

      @Override
      public FilterMap withSourceIndex(int index) {
        return new FilterMap(index, target, varName, filter, transform);
      }

      @Override
      public String toString() {
        return target + ".map(" + varName + ", " + filter + ", " + transform + ")";
      }
    }
  }

  record Element(CelExpr value, boolean optional) {
    @Override
    public String toString() {
      return optionalMarker(optional) + value;
    }
  }

  private static String escapeString(String s) {
    return s.codePoints()
        .mapToObj(
            c ->
                switch (c) {
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
