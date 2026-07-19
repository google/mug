package com.google.mu.cel;

import com.google.mu.util.stream.Joiner;
import java.util.List;
import java.util.Map;

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

  /** {@code CelExpr.value(true)} is equivalent to {@code CelExpr.of("true")}. */
  static BoolValue value(boolean value) {
    return new BoolValue(value, 0);
  }

  /** {@code CelExpr.value(123)} is equivalent to {@code CelExpr.of("123")}. */
  static LongValue value(long value) {
    return new LongValue(value, 0);
  }

  /** {@code CelExpr.value(12.3)} is equivalent to {@code CelExpr.of("12.3")}. */
  static DoubleValue value(double value) {
    return new DoubleValue(value, 0);
  }

  /** {@code CelExpr.value("foo")} is equivalent to {@code CelExpr.of("'foo'")}. */
  static StringValue string(String value) {
    return new StringValue(value, 0);
  }

  /**
   * {@code CelExpr.bytes((byte) 1, (byte) 2)} is equivalent to {@code CelExpr.of("b'\\x01\\x02'")}.
   */
  static BytesValue bytes(byte... value) {
    return new BytesValue(value, 0);
  }

  /** {@code CelExpr.unsigned(123)} is equivalent to {@code CelExpr.of("123u")}. */
  static UintValue unsigned(long value) {
    return new UintValue(value, 0);
  }

  /**
   * {@code CelExpr.struct("MyMsg", Map.of("field", new Element(value(1), true)))} is equivalent to
   * {@code CelExpr.of("MyMsg{?field: 1}")}.
   */
  static Struct struct(String messageName, Map<String, Element> fields) {
    return new Struct(
        messageName,
        fields.entrySet().stream()
            .map(
                e ->
                    new Entry<>(
                        new Ident(e.getKey()), e.getValue().value(), e.getValue().optional()))
            .toList());
  }

  /** {@code CelExpr.negative(value(5))} is equivalent to {@code CelExpr.of("-(5)")}. */
  static Negative negative(CelExpr expr) {
    return new Negative(expr, expr.sourceIndex());
  }

  /** {@code CelExpr.not(value(true))} is equivalent to {@code CelExpr.of("!true")}. */
  static Not not(CelExpr expr) {
    return new Not(expr, expr.sourceIndex());
  }

  /** {@code expr.select("field")} is equivalent to {@code CelExpr.of("expr.field")}. */
  default Select select(Ident field) {
    return new Select(this, field, field.sourceIndex());
  }

  default Select select(String field) {
    return select(new Ident(field));
  }

  default OptionalSelect optionalSelect(String field) {
    return optionalSelect(new Ident(field));
  }

  /** {@code expr.index(value(0))} is equivalent to {@code CelExpr.of("expr[0]")}. */
  default Index index(CelExpr index) {
    return new Index(this, index, index.sourceIndex());
  }

  /** {@code expr.optionalSelect("field")} is equivalent to {@code CelExpr.of("expr.?field")}. */
  default OptionalSelect optionalSelect(Ident field) {
    return new OptionalSelect(this, field, field.sourceIndex());
  }

  /** {@code expr.optionalIndex(value(0))} is equivalent to {@code CelExpr.of("expr[?0]")}. */
  default OptionalIndex optionalIndex(CelExpr index) {
    return new OptionalIndex(this, index, sourceIndex());
  }

  /** {@code a.add(b)} is equivalent to {@code CelExpr.of("a + b")}. */
  default Add add(CelExpr that) {
    return new Add(this, that, sourceIndex());
  }

  /** {@code a.subtract(b)} is equivalent to {@code CelExpr.of("a - b")}. */
  default Subtract subtract(CelExpr that) {
    return new Subtract(this, that, sourceIndex());
  }

  /** {@code a.multiply(b)} is equivalent to {@code CelExpr.of("a * b")}. */
  default Multiply multiply(CelExpr that) {
    return new Multiply(this, that, sourceIndex());
  }

  /** {@code a.divide(b)} is equivalent to {@code CelExpr.of("a / b")}. */
  default Divide divide(CelExpr that) {
    return new Divide(this, that, sourceIndex());
  }

  /** {@code a.modulo(b)} is equivalent to {@code CelExpr.of("a % b")}. */
  default Modulo modulo(CelExpr that) {
    return new Modulo(this, that, sourceIndex());
  }

  /** {@code a.equalTo(b)} is equivalent to {@code CelExpr.of("a == b")}. */
  default EqualTo equalTo(CelExpr that) {
    return new EqualTo(this, that, sourceIndex());
  }

  /** {@code a.notEqualTo(b)} is equivalent to {@code CelExpr.of("a != b")}. */
  default NotEqualTo notEqualTo(CelExpr that) {
    return new NotEqualTo(this, that, sourceIndex());
  }

  /** {@code a.lessThan(b)} is equivalent to {@code CelExpr.of("a < b")}. */
  default LessThan lessThan(CelExpr that) {
    return new LessThan(this, that, sourceIndex());
  }

  /** {@code a.atMost(b)} is equivalent to {@code CelExpr.of("a <= b")}. */
  default LessThanOrEqualTo atMost(CelExpr ceiling) {
    return new LessThanOrEqualTo(this, ceiling, sourceIndex());
  }

  /** {@code a.greaterThan(b)} is equivalent to {@code CelExpr.of("a > b")}. */
  default GreaterThan greaterThan(CelExpr that) {
    return new GreaterThan(this, that, sourceIndex());
  }

  /** {@code a.atLeast(b)} is equivalent to {@code CelExpr.of("a >= b")}. */
  default GreaterThanOrEqualTo atLeast(CelExpr floor) {
    return new GreaterThanOrEqualTo(this, floor, sourceIndex());
  }

  /** {@code a.in(b)} is equivalent to {@code CelExpr.of("a in b")}. */
  default In in(CelExpr that) {
    return new In(this, that, sourceIndex());
  }

  /** {@code a.and(b)} is equivalent to {@code CelExpr.of("a && b")}. */
  default And and(CelExpr that) {
    return new And(this, that, sourceIndex());
  }

  /** {@code a.or(b)} is equivalent to {@code CelExpr.of("a || b")}. */
  default Or or(CelExpr that) {
    return new Or(this, that, sourceIndex());
  }

  /** {@code active.ifElse(1, 0)} is equivalent to {@code CelExpr.of("active ? 1 : 0")}. */
  default IfElse ifElse(CelExpr ifTrue, CelExpr ifFalse) {
    return new IfElse(this, ifTrue, ifFalse, sourceIndex());
  }

  /**
   * {@code target.call(member, args)} is equivalent to {@code CelExpr.of("target.member(args)")}.
   */
  default MemberCall call(Ident member, List<CelExpr> args) {
    return new MemberCall(this, member, args, sourceIndex());
  }

  /** Null literal. */
  record NullValue(int sourceIndex) implements CelExpr {
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
  record BoolValue(boolean value, int sourceIndex) implements CelExpr {
    @Override
    public BoolValue withSourceIndex(int index) {
      return new BoolValue(value, index);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  /** Signed 64-bit integer literal. */
  record LongValue(long value, int sourceIndex) implements CelExpr {
    @Override
    public LongValue withSourceIndex(int index) {
      return new LongValue(value, index);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  /** Unsigned 64-bit integer literal. */
  record UintValue(long value, int sourceIndex) implements CelExpr {
    @Override
    public UintValue withSourceIndex(int index) {
      return new UintValue(value, index);
    }

    @Override
    public String toString() {
      return Long.toUnsignedString(value) + "u";
    }
  }

  /** Double-precision floating point literal. */
  record DoubleValue(double value, int sourceIndex) implements CelExpr {
    @Override
    public DoubleValue withSourceIndex(int index) {
      return new DoubleValue(value, index);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  /** UTF-8 string literal. */
  record StringValue(String value, int sourceIndex) implements CelExpr {
    @Override
    public StringValue withSourceIndex(int index) {
      return new StringValue(value, index);
    }

    @Override
    public String toString() {
      return escapeString(value);
    }
  }

  /** Byte sequence literal. */
  @SuppressWarnings("ArrayRecordComponent")
  record BytesValue(byte[] value, int sourceIndex) implements CelExpr {
    @Override
    public BytesValue withSourceIndex(int index) {
      return new BytesValue(value, index);
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
  record Ident(String name, int sourceIndex) implements CelExpr {
    public Ident(String name) {
      this(name, 0);
    }

    public Ident(int sourceIndex, String name) {
      this(name, sourceIndex);
    }

    @Override
    public Ident withSourceIndex(int index) {
      return new Ident(name, index);
    }

    @Override
    public String toString() {
      return CelParser.PLAIN_IDENTIFIER.matches(name) ? name : "`" + name + "`";
    }
  }

  /** Field selection (e.g. {@code operand.field}). */
  record Select(CelExpr operand, Ident field, int sourceIndex) implements CelExpr {
    public Select(CelExpr operand, Ident field) {
      this(operand, field, field.sourceIndex());
    }

    @Override
    public Select withSourceIndex(int index) {
      return new Select(operand, field, index);
    }

    @Override
    public String toString() {
      return "(" + operand + ")." + field;
    }
  }

  /** Subscript/indexing (e.g. {@code operand[index]}). */
  record Index(CelExpr operand, CelExpr index, int sourceIndex) implements CelExpr {
    public Index(CelExpr operand, CelExpr index) {
      this(operand, index, operand.sourceIndex());
    }

    @Override
    public Index withSourceIndex(int index) {
      return new Index(operand, this.index, index);
    }

    @Override
    public String toString() {
      return "(" + operand + ")[" + index + "]";
    }
  }

  /** Optional field selection (e.g. {@code operand.?field}). */
  record OptionalSelect(CelExpr operand, Ident field, int sourceIndex) implements CelExpr {
    public OptionalSelect(CelExpr operand, Ident field) {
      this(operand, field, field.sourceIndex());
    }

    public OptionalSelect(int sourceIndex, CelExpr operand, Ident field) {
      this(operand, field, sourceIndex);
    }

    public OptionalSelect(int sourceIndex, CelExpr operand, String fieldName) {
      this(operand, new Ident(fieldName), sourceIndex);
    }

    @Override
    public OptionalSelect withSourceIndex(int index) {
      return new OptionalSelect(operand, field, index);
    }

    @Override
    public String toString() {
      return "(" + operand + ").?" + field;
    }
  }

  /** Optional subscript/indexing (e.g. {@code operand[?index]}). */
  record OptionalIndex(CelExpr operand, CelExpr index, int sourceIndex) implements CelExpr {
    public OptionalIndex(CelExpr operand, CelExpr index) {
      this(operand, index, index.sourceIndex());
    }

    public OptionalIndex(int sourceIndex, CelExpr operand, CelExpr index) {
      this(operand, index, sourceIndex);
    }

    @Override
    public OptionalIndex withSourceIndex(int index) {
      return new OptionalIndex(operand, this.index, index);
    }

    @Override
    public String toString() {
      return "(" + operand + ")[?" + index + "]";
    }
  }

  /**
   * An expression composed by a binary operator. The {@link sourceIndex} points to the binary
   * operator.
   */
  public sealed interface Binary extends CelExpr {
    CelExpr left();

    CelExpr right();
  }

  /** Addition: {@code x + y}. */
  record Add(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public Add withSourceIndex(int index) {
      return new Add(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") + (" + right + ")";
    }
  }

  /** Subtraction: {@code x - y}. */
  record Subtract(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public Subtract withSourceIndex(int index) {
      return new Subtract(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") - (" + right + ")";
    }
  }

  /** Multiplication: {@code x * y}. */
  record Multiply(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public Multiply withSourceIndex(int index) {
      return new Multiply(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") * (" + right + ")";
    }
  }

  /** Division: {@code x / y}. */
  record Divide(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public Divide withSourceIndex(int index) {
      return new Divide(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") / (" + right + ")";
    }
  }

  /** Modulo/remainder: {@code x % y}. */
  record Modulo(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public Modulo withSourceIndex(int index) {
      return new Modulo(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") % (" + right + ")";
    }
  }

  /** Less than relational comparison: {@code x < y}. */
  record LessThan(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public LessThan withSourceIndex(int index) {
      return new LessThan(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") < (" + right + ")";
    }
  }

  /** Less than or equal to relational comparison: {@code x <= y}. */
  record LessThanOrEqualTo(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public LessThanOrEqualTo withSourceIndex(int index) {
      return new LessThanOrEqualTo(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") <= (" + right + ")";
    }
  }

  /** Greater than relational comparison: {@code x > y}. */
  record GreaterThan(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public GreaterThan withSourceIndex(int index) {
      return new GreaterThan(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") > (" + right + ")";
    }
  }

  /** Greater than or equal to relational comparison: {@code x >= y}. */
  record GreaterThanOrEqualTo(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public GreaterThanOrEqualTo withSourceIndex(int index) {
      return new GreaterThanOrEqualTo(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") >= (" + right + ")";
    }
  }

  /** Equality comparison: {@code x == y}. */
  record EqualTo(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public EqualTo withSourceIndex(int index) {
      return new EqualTo(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") == (" + right + ")";
    }
  }

  /** Inequality comparison: {@code x != y}. */
  record NotEqualTo(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public NotEqualTo withSourceIndex(int index) {
      return new NotEqualTo(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") != (" + right + ")";
    }
  }

  /** Containment comparison: {@code x in y}. */
  record In(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public In withSourceIndex(int index) {
      return new In(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") in (" + right + ")";
    }
  }

  /** Logical AND comparison: {@code x && y}. */
  record And(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public And withSourceIndex(int index) {
      return new And(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") && (" + right + ")";
    }
  }

  /** Logical OR comparison: {@code x || y}. */
  record Or(CelExpr left, CelExpr right, int sourceIndex) implements Binary {
    @Override
    public Or withSourceIndex(int index) {
      return new Or(left, right, index);
    }

    @Override
    public String toString() {
      return "(" + left + ") || (" + right + ")";
    }
  }

  /** An expression composed by a unary operator. */
  public sealed interface Unary extends CelExpr {
    CelExpr operand();
  }

  /** Unary sign negation: {@code -x}. */
  record Negative(CelExpr operand, int sourceIndex) implements Unary {
    @Override
    public Negative withSourceIndex(int index) {
      return new Negative(operand, index);
    }

    @Override
    public String toString() {
      return "-(" + operand + ")";
    }
  }

  /** Logical negation: {@code !x}. */
  record Not(CelExpr operand, int sourceIndex) implements Unary {
    @Override
    public Not withSourceIndex(int index) {
      return new Not(operand, index);
    }

    @Override
    public String toString() {
      return "!(" + operand + ")";
    }
  }

  /** Ternary conditional (e.g. {@code condition ? trueExpr : falseExpr}). */
  record IfElse(CelExpr condition, CelExpr ifTrue, CelExpr ifFalse, int sourceIndex)
      implements CelExpr {
    public IfElse(CelExpr condition, CelExpr ifTrue, CelExpr ifFalse) {
      this(condition, ifTrue, ifFalse, condition.sourceIndex());
    }

    @Override
    public IfElse withSourceIndex(int index) {
      return new IfElse(condition, ifTrue, ifFalse, index);
    }

    @Override
    public String toString() {
      return "(" + condition + ") ? (" + ifTrue + ") : (" + ifFalse + ")";
    }
  }

  /** Global function calls {@code f(args)}. */
  record FunctionCall(Ident function, List<CelExpr> args, int sourceIndex) implements CelExpr {
    public FunctionCall(Ident function, List<CelExpr> args) {
      this(function, args, 0);
    }

    public FunctionCall(int sourceIndex, Ident function, List<CelExpr> args) {
      this(function, args, sourceIndex);
    }

    @Override
    public FunctionCall withSourceIndex(int index) {
      return new FunctionCall(function, args, index);
    }

    @Override
    public String toString() {
      return function.name() + args.stream().collect(Joiner.on(", ").between('(', ')'));
    }
  }

  /** Member calls {@code target.f(args)}. */
  record MemberCall(CelExpr target, Ident member, List<CelExpr> args, int sourceIndex)
      implements CelExpr {
    public MemberCall(CelExpr target, Ident member, List<CelExpr> args) {
      this(target, member, args, member.sourceIndex);
    }

    public MemberCall(int sourceIndex, CelExpr target, Ident member, List<CelExpr> args) {
      this(target, member, args, sourceIndex);
    }

    @Override
    public MemberCall withSourceIndex(int index) {
      return new MemberCall(target, member, args, index);
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
  record ListOf(List<Element> elements, int sourceIndex) implements CelExpr {
    @Override
    public ListOf withSourceIndex(int index) {
      return new ListOf(elements, index);
    }

    @Override
    public String toString() {
      return elements.stream().collect(Joiner.on(", ").between('[', ']'));
    }
  }

  /** Map creation (e.g. {@code {"key": value, ? "opt_key": value}}). */
  record MapOf(List<Entry<CelExpr>> entries, int sourceIndex) implements CelExpr {
    @Override
    public MapOf withSourceIndex(int index) {
      return new MapOf(entries, index);
    }

    @Override
    public String toString() {
      return entries.stream().collect(Joiner.on(", ").between('{', '}'));
    }
  }

  /** Struct/message creation (e.g. {@code Type{field: value, ?opt_field: value}}). */
  record Struct(String messageName, List<Entry<Ident>> fields, int sourceIndex) implements CelExpr {
    public Struct(String messageName, List<Entry<Ident>> fields) {
      this(messageName, fields, 0);
    }

    @Override
    public Struct withSourceIndex(int index) {
      return new Struct(messageName, fields, index);
    }

    @Override
    public String toString() {
      return messageName + fields.stream().collect(Joiner.on(", ").between('{', '}'));
    }
  }

  /** An entry/field representing a key-value or field-value mapping in map or struct literals. */
  record Entry<K>(K key, CelExpr value, boolean optional, int sourceIndex) {
    public Entry(K key, CelExpr value, boolean optional) {
      this(key, value, optional, 0);
    }

    public Entry(int sourceIndex, K key, CelExpr value, boolean optional) {
      this(key, value, optional, sourceIndex);
    }

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
      return new Entry<>(key, value, false, key.sourceIndex());
    }

    /**
     * {@code Entry.optional(string("key"), value(1))} is equivalent to {@code CelExpr.of("? 'key':
     * 1")}.
     */
    public static Entry<CelExpr> optional(CelExpr key, CelExpr value) {
      return new Entry<>(key, value, true, key.sourceIndex());
    }

    @Override
    public String toString() {
      return optionalMarker(optional) + key + ": " + value;
    }
  }

  /** Abstract representation of a CEL macro expression. */
  sealed interface Macro extends CelExpr {
    /** The {@code has(member)} macro. */
    record Has(Select member, int sourceIndex) implements Macro {
      public Has(Select member) {
        this(member, 0);
      }

      public Has(int sourceIndex, Select member) {
        this(member, sourceIndex);
      }

      @Override
      public Has withSourceIndex(int index) {
        return new Has(member, index);
      }

      @Override
      public String toString() {
        return "has(" + member + ")";
      }
    }

    record All(CelExpr target, Ident var, CelExpr condition, int sourceIndex) implements Macro {
      public All(CelExpr target, Ident var, CelExpr condition) {
        this(target, var, condition, target.sourceIndex());
      }

      public All(CelExpr target, String varName, CelExpr condition) {
        this(target, new Ident(varName, target.sourceIndex()), condition, target.sourceIndex());
      }

      public All(CelExpr target, String varName, CelExpr condition, int sourceIndex) {
        this(target, new Ident(varName, sourceIndex), condition, sourceIndex);
      }

      public All(int sourceIndex, CelExpr target, String varName, CelExpr condition) {
        this(target, varName, condition, sourceIndex);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public All withSourceIndex(int index) {
        return new All(target, var, condition, index);
      }

      @Override
      public String toString() {
        return target + ".all(" + var.name() + ", " + condition + ")";
      }
    }

    record Exists(CelExpr target, Ident var, CelExpr condition, int sourceIndex) implements Macro {
      public Exists(CelExpr target, Ident var, CelExpr condition) {
        this(target, var, condition, target.sourceIndex());
      }

      public Exists(CelExpr target, String varName, CelExpr condition) {
        this(target, new Ident(varName, target.sourceIndex()), condition, target.sourceIndex());
      }

      public Exists(CelExpr target, String varName, CelExpr condition, int sourceIndex) {
        this(target, new Ident(varName, sourceIndex), condition, sourceIndex);
      }

      public Exists(int sourceIndex, CelExpr target, String varName, CelExpr condition) {
        this(target, varName, condition, sourceIndex);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public Exists withSourceIndex(int index) {
        return new Exists(target, var, condition, index);
      }

      @Override
      public String toString() {
        return target + ".exists(" + var.name() + ", " + condition + ")";
      }
    }

    record ExistsOne(CelExpr target, Ident var, CelExpr condition, int sourceIndex)
        implements Macro {
      public ExistsOne(CelExpr target, Ident var, CelExpr condition) {
        this(target, var, condition, target.sourceIndex());
      }

      public ExistsOne(CelExpr target, String varName, CelExpr condition) {
        this(target, new Ident(varName, target.sourceIndex()), condition, target.sourceIndex());
      }

      public ExistsOne(CelExpr target, String varName, CelExpr condition, int sourceIndex) {
        this(target, new Ident(varName, sourceIndex), condition, sourceIndex);
      }

      public ExistsOne(int sourceIndex, CelExpr target, String varName, CelExpr condition) {
        this(target, varName, condition, sourceIndex);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public ExistsOne withSourceIndex(int index) {
        return new ExistsOne(target, var, condition, index);
      }

      @Override
      public String toString() {
        return target + ".exists_one(" + var.name() + ", " + condition + ")";
      }
    }

    record Filter(CelExpr target, Ident var, CelExpr expr, int sourceIndex) implements Macro {
      public Filter(CelExpr target, Ident var, CelExpr expr) {
        this(target, var, expr, target.sourceIndex());
      }

      public Filter(CelExpr target, String varName, CelExpr expr) {
        this(target, new Ident(varName, target.sourceIndex()), expr, target.sourceIndex());
      }

      public Filter(CelExpr target, String varName, CelExpr expr, int sourceIndex) {
        this(target, new Ident(varName, sourceIndex), expr, sourceIndex);
      }

      public Filter(int sourceIndex, CelExpr target, String varName, CelExpr expr) {
        this(target, varName, expr, sourceIndex);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public Filter withSourceIndex(int index) {
        return new Filter(target, var, expr, index);
      }

      @Override
      public String toString() {
        return target + ".filter(" + var.name() + ", " + expr + ")";
      }
    }

    record Map(CelExpr target, Ident var, CelExpr expr, int sourceIndex) implements Macro {
      public Map(CelExpr target, Ident var, CelExpr expr) {
        this(target, var, expr, target.sourceIndex());
      }

      public Map(CelExpr target, String varName, CelExpr expr) {
        this(target, new Ident(varName, target.sourceIndex()), expr, target.sourceIndex());
      }

      public Map(CelExpr target, String varName, CelExpr expr, int sourceIndex) {
        this(target, new Ident(varName, sourceIndex), expr, sourceIndex);
      }

      public Map(int sourceIndex, CelExpr target, String varName, CelExpr expr) {
        this(target, varName, expr, sourceIndex);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public Map withSourceIndex(int index) {
        return new Map(target, var, expr, index);
      }

      @Override
      public String toString() {
        return target + ".map(" + var.name() + ", " + expr + ")";
      }
    }

    record FilterMap(CelExpr target, Ident var, CelExpr filter, CelExpr transform, int sourceIndex)
        implements Macro {
      public FilterMap(CelExpr target, Ident var, CelExpr filter, CelExpr transform) {
        this(target, var, filter, transform, target.sourceIndex());
      }

      public FilterMap(CelExpr target, String varName, CelExpr filter, CelExpr transform) {
        this(
            target,
            new Ident(varName, target.sourceIndex()),
            filter,
            transform,
            target.sourceIndex());
      }

      public FilterMap(
          CelExpr target, String varName, CelExpr filter, CelExpr transform, int sourceIndex) {
        this(target, new Ident(varName, sourceIndex), filter, transform, sourceIndex);
      }

      public FilterMap(
          int sourceIndex, CelExpr target, String varName, CelExpr filter, CelExpr transform) {
        this(target, varName, filter, transform, sourceIndex);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public FilterMap withSourceIndex(int index) {
        return new FilterMap(target, var, filter, transform, index);
      }

      @Override
      public String toString() {
        return target + ".map(" + var.name() + ", " + filter + ", " + transform + ")";
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

  @Deprecated
  record ListLiteral(int sourceIndex, List<Element> elements) implements CelExpr {
    public ListLiteral(List<Element> elements) {
      this(0, elements);
    }

    @Override
    public ListLiteral withSourceIndex(int index) {
      return new ListLiteral(index, elements);
    }
  }

  @Deprecated
  record MapLiteral(int sourceIndex, List<Entry<CelExpr>> entries) implements CelExpr {
    public MapLiteral(List<Entry<CelExpr>> entries) {
      this(0, entries);
    }

    @Override
    public MapLiteral withSourceIndex(int index) {
      return new MapLiteral(index, entries);
    }
  }

  @Deprecated
  record StructLiteral(int sourceIndex, String messageName, List<Entry<Ident>> fields)
      implements CelExpr {
    public StructLiteral(String messageName, List<Entry<Ident>> fields) {
      this(0, messageName, fields);
    }

    @Override
    public StructLiteral withSourceIndex(int index) {
      return new StructLiteral(index, messageName, fields);
    }
  }
}
