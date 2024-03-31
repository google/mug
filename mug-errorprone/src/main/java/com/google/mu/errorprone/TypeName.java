package com.google.mu.errorprone;

import java.util.Objects;

import com.google.errorprone.VisitorState;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Type;

/** Represents a (lazily memoized) type name with convenient methods to operate on it. */
final class TypeName {
  private final String name;
  private final Supplier<Type> memoized;

  TypeName(String name) {
    this.name = Objects.requireNonNull(name);
    this.memoized = VisitorState.memoize(state -> state.getTypeFromString(name));
  }

  static TypeName of(Class<?> cls) {
    return new TypeName(cls.getTypeName());
  }

  boolean isSameType(Type type, VisitorState state) {
    return ASTHelpers.isSameType(memoized.get(state), type, state);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TypeName) {
      return name.equals(((TypeName) obj).name);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return name;
  }
}
