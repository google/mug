package com.google.common.labs.cel;

import java.util.List;
import java.util.Optional;

import com.google.mu.util.stream.Joiner;

/** Abstract representation of a parsed Common Expression Language (CEL) expression. */
public sealed interface CelExpr {

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
      MINUS("-"), NOT("!");

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
    public record Element(CelExpr value, boolean optional) {
      @Override public String toString() {
        return optionalMarker(optional) + value;
      }
    }

    @Override public String toString() {
      return elements.stream().collect(Joiner.on(", ").between('[', ']'));
    }
  }

  /** Map creation (e.g. {@code {"key": value, ? "opt_key": value}}). */
  record MapLiteral(List<Entry> entries) implements CelExpr {
    public record Entry(CelExpr key, CelExpr value, boolean optional) {
      @Override public String toString() {
        return optionalMarker(optional) + key + ": " + value;
      }
    }

    @Override public String toString() {
      return entries.stream().collect(Joiner.on(", ").between('{', '}'));
    }
  }

  /** Struct/message creation (e.g. {@code Type{field: value, ?opt_field: value}}). */
  record StructLiteral(String messageName, List<Field> fields) implements CelExpr {
    public record Field(String name, CelExpr value, boolean optional) {
      @Override public String toString() {
        return optionalMarker(optional) + new Ident(name) + ": " + value;
      }
    }

    @Override public String toString() {
      return messageName + fields.stream().collect(Joiner.on(", ").between('{', '}'));
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
