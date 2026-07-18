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
    return new BoolValue(0, value);
  }

  /** {@code CelExpr.value(123)} is equivalent to {@code CelExpr.of("123")}. */
  static LongValue value(long value) {
    return new LongValue(0, value);
  }

  /** {@code CelExpr.value(12.3)} is equivalent to {@code CelExpr.of("12.3")}. */
  static DoubleValue value(double value) {
    return new DoubleValue(0, value);
  }

  /** {@code CelExpr.value("foo")} is equivalent to {@code CelExpr.of("'foo'")}. */
  static StringValue string(String value) {
    return new StringValue(0, value);
  }

  /**
   * {@code CelExpr.bytes((byte) 1, (byte) 2)} is equivalent to {@code CelExpr.of("b'\\x01\\x02'")}.
   */
  static BytesValue bytes(byte... value) {
    return new BytesValue(0, value);
  }

  /** {@code CelExpr.unsigned(123)} is equivalent to {@code CelExpr.of("123u")}. */
  static UintValue unsigned(long value) {
    return new UintValue(0, value);
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
  static Negative negative(CelExpr expr) {
    return new Negative(expr.sourceIndex(), expr);
  }

  /** {@code CelExpr.not(value(true))} is equivalent to {@code CelExpr.of("!true")}. */
  static Not not(CelExpr expr) {
    return new Not(expr.sourceIndex(), expr);
  }

  /** {@code expr.select("field")} is equivalent to {@code CelExpr.of("expr.field")}. */
  default Select select(String field) {
    return new Select(sourceIndex(), this, field);
  }

  /** {@code expr.index(value(0))} is equivalent to {@code CelExpr.of("expr[0]")}. */
  default Index index(CelExpr index) {
    return new Index(sourceIndex(), this, index);
  }

  /** {@code expr.optionalSelect("field")} is equivalent to {@code CelExpr.of("expr.?field")}. */
  default OptionalSelect optionalSelect(String field) {
    return new OptionalSelect(sourceIndex(), this, field);
  }

  /** {@code expr.optionalIndex(value(0))} is equivalent to {@code CelExpr.of("expr[?0]")}. */
  default OptionalIndex optionalIndex(CelExpr index) {
    return new OptionalIndex(sourceIndex(), this, index);
  }

  /** {@code a.add(b)} is equivalent to {@code CelExpr.of("a + b")}. */
  default Add add(CelExpr that) {
    return new Add(sourceIndex(), this, that);
  }

  /** {@code a.subtract(b)} is equivalent to {@code CelExpr.of("a - b")}. */
  default Subtract subtract(CelExpr that) {
    return new Subtract(sourceIndex(), this, that);
  }

  /** {@code a.multiply(b)} is equivalent to {@code CelExpr.of("a * b")}. */
  default Multiply multiply(CelExpr that) {
    return new Multiply(sourceIndex(), this, that);
  }

  /** {@code a.divide(b)} is equivalent to {@code CelExpr.of("a / b")}. */
  default Divide divide(CelExpr that) {
    return new Divide(sourceIndex(), this, that);
  }

  /** {@code a.modulo(b)} is equivalent to {@code CelExpr.of("a % b")}. */
  default Modulo modulo(CelExpr that) {
    return new Modulo(sourceIndex(), this, that);
  }

  /** {@code a.equalTo(b)} is equivalent to {@code CelExpr.of("a == b")}. */
  default EqualTo equalTo(CelExpr that) {
    return new EqualTo(sourceIndex(), this, that);
  }

  /** {@code a.notEqualTo(b)} is equivalent to {@code CelExpr.of("a != b")}. */
  default NotEqualTo notEqualTo(CelExpr that) {
    return new NotEqualTo(sourceIndex(), this, that);
  }

  /** {@code a.lessThan(b)} is equivalent to {@code CelExpr.of("a < b")}. */
  default LessThan lessThan(CelExpr that) {
    return new LessThan(sourceIndex(), this, that);
  }

  /** {@code a.atMost(b)} is equivalent to {@code CelExpr.of("a <= b")}. */
  default LessThanOrEqualTo atMost(CelExpr ceiling) {
    return new LessThanOrEqualTo(sourceIndex(), this, ceiling);
  }

  /** {@code a.greaterThan(b)} is equivalent to {@code CelExpr.of("a > b")}. */
  default GreaterThan greaterThan(CelExpr that) {
    return new GreaterThan(sourceIndex(), this, that);
  }

  /** {@code a.atLeast(b)} is equivalent to {@code CelExpr.of("a >= b")}. */
  default GreaterThanOrEqualTo atLeast(CelExpr floor) {
    return new GreaterThanOrEqualTo(sourceIndex(), this, floor);
  }

  /** {@code a.in(b)} is equivalent to {@code CelExpr.of("a in b")}. */
  default In in(CelExpr that) {
    return new In(sourceIndex(), this, that);
  }

  /** {@code a.and(b)} is equivalent to {@code CelExpr.of("a && b")}. */
  default And and(CelExpr that) {
    return new And(sourceIndex(), this, that);
  }

  /** {@code a.or(b)} is equivalent to {@code CelExpr.of("a || b")}. */
  default Or or(CelExpr that) {
    return new Or(sourceIndex(), this, that);
  }

  /** {@code active.ifElse(1, 0)} is equivalent to {@code CelExpr.of("active ? 1 : 0")}. */
  default Ternary ifElse(CelExpr ifTrue, CelExpr ifFalse) {
    return new Ternary(sourceIndex(), this, ifTrue, ifFalse);
  }

  /**
   * {@code target.call(member, args)} is equivalent to {@code CelExpr.of("target.member(args)")}.
   */
  default MemberCall call(Ident member, List<CelExpr> args) {
    return new MemberCall(sourceIndex(), this, member, args);
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
  record BoolValue(int sourceIndex, boolean value) implements CelExpr {
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

  /** Addition: x + y. */
  record Add(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public Add withSourceIndex(int index) {
      return new Add(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") + (" + right + ")";
    }
  }

  /** Subtraction: x - y. */
  record Subtract(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public Subtract withSourceIndex(int index) {
      return new Subtract(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") - (" + right + ")";
    }
  }

  /** Multiplication: x * y. */
  record Multiply(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public Multiply withSourceIndex(int index) {
      return new Multiply(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") * (" + right + ")";
    }
  }

  /** Division: x / y. */
  record Divide(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public Divide withSourceIndex(int index) {
      return new Divide(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") / (" + right + ")";
    }
  }

  /** Modulo/remainder: x % y. */
  record Modulo(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public Modulo withSourceIndex(int index) {
      return new Modulo(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") % (" + right + ")";
    }
  }

  /** Less than relational comparison: x < y. */
  record LessThan(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public LessThan withSourceIndex(int index) {
      return new LessThan(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") < (" + right + ")";
    }
  }

  /** Less than or equal to relational comparison: x <= y. */
  record LessThanOrEqualTo(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public LessThanOrEqualTo withSourceIndex(int index) {
      return new LessThanOrEqualTo(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") <= (" + right + ")";
    }
  }

  /** Greater than relational comparison: x > y. */
  record GreaterThan(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public GreaterThan withSourceIndex(int index) {
      return new GreaterThan(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") > (" + right + ")";
    }
  }

  /** Greater than or equal to relational comparison: x >= y. */
  record GreaterThanOrEqualTo(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public GreaterThanOrEqualTo withSourceIndex(int index) {
      return new GreaterThanOrEqualTo(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") >= (" + right + ")";
    }
  }

  /** Equality comparison: x == y. */
  record EqualTo(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public EqualTo withSourceIndex(int index) {
      return new EqualTo(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") == (" + right + ")";
    }
  }

  /** Inequality comparison: x != y. */
  record NotEqualTo(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public NotEqualTo withSourceIndex(int index) {
      return new NotEqualTo(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") != (" + right + ")";
    }
  }

  /** Containment comparison: x in y. */
  record In(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public In withSourceIndex(int index) {
      return new In(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") in (" + right + ")";
    }
  }

  /** Logical AND comparison: x && y. */
  record And(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public And withSourceIndex(int index) {
      return new And(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") && (" + right + ")";
    }
  }

  /** Logical OR comparison: x || y. */
  record Or(int sourceIndex, CelExpr left, CelExpr right) implements CelExpr {
    @Override
    public Or withSourceIndex(int index) {
      return new Or(index, left, right);
    }

    @Override
    public String toString() {
      return "(" + left + ") || (" + right + ")";
    }
  }

  /** Unary sign negation: -x. */
  record Negative(int sourceIndex, CelExpr operand) implements CelExpr {
    @Override
    public Negative withSourceIndex(int index) {
      return new Negative(index, operand);
    }

    @Override
    public String toString() {
      return "-(" + operand + ")";
    }
  }

  /** Logical negation: !x. */
  record Not(int sourceIndex, CelExpr operand) implements CelExpr {
    @Override
    public Not withSourceIndex(int index) {
      return new Not(index, operand);
    }

    @Override
    public String toString() {
      return "!(" + operand + ")";
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
  record FunctionCall(int sourceIndex, Ident function, List<CelExpr> args) implements CelExpr {
    public FunctionCall(Ident function, List<CelExpr> args) {
      this(0, function, args);
    }

    @Override
    public FunctionCall withSourceIndex(int index) {
      return new FunctionCall(index, function, args);
    }

    @Override
    public String toString() {
      return function.name() + args.stream().collect(Joiner.on(", ").between('(', ')'));
    }
  }

  /** Member calls {@code target.f(args)}. */
  record MemberCall(int sourceIndex, CelExpr target, Ident member, List<CelExpr> args)
      implements CelExpr {
    public MemberCall(CelExpr target, Ident member, List<CelExpr> args) {
      this(0, target, member, args);
    }

    @Override
    public MemberCall withSourceIndex(int index) {
      return new MemberCall(index, target, member, args);
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
  record Entry<K>(int sourceIndex, K key, CelExpr value, boolean optional) {
    public Entry(K key, CelExpr value, boolean optional) {
      this(0, key, value, optional);
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
      return new Entry<>(key.sourceIndex(), key, value, false);
    }

    /**
     * {@code Entry.optional(string("key"), value(1))} is equivalent to {@code CelExpr.of("? 'key':
     * 1")}.
     */
    public static Entry<CelExpr> optional(CelExpr key, CelExpr value) {
      return new Entry<>(key.sourceIndex(), key, value, true);
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

    record All(int sourceIndex, CelExpr target, Ident var, CelExpr condition) implements Macro {
      public All(CelExpr target, Ident var, CelExpr condition) {
        this(target.sourceIndex(), target, var, condition);
      }

      public All(CelExpr target, String varName, CelExpr condition) {
        this(target.sourceIndex(), target, new Ident(target.sourceIndex(), varName), condition);
      }

      public All(int sourceIndex, CelExpr target, String varName, CelExpr condition) {
        this(sourceIndex, target, new Ident(sourceIndex, varName), condition);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public All withSourceIndex(int index) {
        return new All(index, target, var, condition);
      }

      @Override
      public String toString() {
        return target + ".all(" + var.name() + ", " + condition + ")";
      }
    }

    record Exists(int sourceIndex, CelExpr target, Ident var, CelExpr condition) implements Macro {
      public Exists(CelExpr target, Ident var, CelExpr condition) {
        this(target.sourceIndex(), target, var, condition);
      }

      public Exists(CelExpr target, String varName, CelExpr condition) {
        this(target.sourceIndex(), target, new Ident(target.sourceIndex(), varName), condition);
      }

      public Exists(int sourceIndex, CelExpr target, String varName, CelExpr condition) {
        this(sourceIndex, target, new Ident(sourceIndex, varName), condition);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public Exists withSourceIndex(int index) {
        return new Exists(index, target, var, condition);
      }

      @Override
      public String toString() {
        return target + ".exists(" + var.name() + ", " + condition + ")";
      }
    }

    record ExistsOne(int sourceIndex, CelExpr target, Ident var, CelExpr condition)
        implements Macro {
      public ExistsOne(CelExpr target, Ident var, CelExpr condition) {
        this(target.sourceIndex(), target, var, condition);
      }

      public ExistsOne(CelExpr target, String varName, CelExpr condition) {
        this(target.sourceIndex(), target, new Ident(target.sourceIndex(), varName), condition);
      }

      public ExistsOne(int sourceIndex, CelExpr target, String varName, CelExpr condition) {
        this(sourceIndex, target, new Ident(sourceIndex, varName), condition);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public ExistsOne withSourceIndex(int index) {
        return new ExistsOne(index, target, var, condition);
      }

      @Override
      public String toString() {
        return target + ".exists_one(" + var.name() + ", " + condition + ")";
      }
    }

    record Filter(int sourceIndex, CelExpr target, Ident var, CelExpr expr) implements Macro {
      public Filter(CelExpr target, Ident var, CelExpr expr) {
        this(target.sourceIndex(), target, var, expr);
      }

      public Filter(CelExpr target, String varName, CelExpr expr) {
        this(target.sourceIndex(), target, new Ident(target.sourceIndex(), varName), expr);
      }

      public Filter(int sourceIndex, CelExpr target, String varName, CelExpr expr) {
        this(sourceIndex, target, new Ident(sourceIndex, varName), expr);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public Filter withSourceIndex(int index) {
        return new Filter(index, target, var, expr);
      }

      @Override
      public String toString() {
        return target + ".filter(" + var.name() + ", " + expr + ")";
      }
    }

    record Map(int sourceIndex, CelExpr target, Ident var, CelExpr expr) implements Macro {
      public Map(CelExpr target, Ident var, CelExpr expr) {
        this(target.sourceIndex(), target, var, expr);
      }

      public Map(CelExpr target, String varName, CelExpr expr) {
        this(target.sourceIndex(), target, new Ident(target.sourceIndex(), varName), expr);
      }

      public Map(int sourceIndex, CelExpr target, String varName, CelExpr expr) {
        this(sourceIndex, target, new Ident(sourceIndex, varName), expr);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public Map withSourceIndex(int index) {
        return new Map(index, target, var, expr);
      }

      @Override
      public String toString() {
        return target + ".map(" + var.name() + ", " + expr + ")";
      }
    }

    record FilterMap(int sourceIndex, CelExpr target, Ident var, CelExpr filter, CelExpr transform)
        implements Macro {
      public FilterMap(CelExpr target, Ident var, CelExpr filter, CelExpr transform) {
        this(target.sourceIndex(), target, var, filter, transform);
      }

      public FilterMap(CelExpr target, String varName, CelExpr filter, CelExpr transform) {
        this(
            target.sourceIndex(),
            target,
            new Ident(target.sourceIndex(), varName),
            filter,
            transform);
      }

      public FilterMap(
          int sourceIndex, CelExpr target, String varName, CelExpr filter, CelExpr transform) {
        this(sourceIndex, target, new Ident(sourceIndex, varName), filter, transform);
      }

      public String varName() {
        return var.name();
      }

      @Override
      public FilterMap withSourceIndex(int index) {
        return new FilterMap(index, target, var, filter, transform);
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
}
