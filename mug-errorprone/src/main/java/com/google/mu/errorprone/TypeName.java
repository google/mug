/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.errorprone;

import java.util.Objects;

import com.google.errorprone.VisitorState;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.tools.javac.code.Type;

/** Represents a (lazily memoized) type name with convenient methods to operate on it. */
@SuppressWarnings("restriction")
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

  boolean isSupertypeOf(Type type, VisitorState state) {
    return ASTHelpers.isSubtype(type, memoized.get(state), state);
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
