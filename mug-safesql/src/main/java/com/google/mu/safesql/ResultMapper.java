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
package com.google.mu.safesql;

import static com.google.mu.safesql.SafeSqlUtils.checkArgument;
import static com.google.mu.safesql.SafeSqlUtils.toImmutableNavigableMap;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.stream.BiStream.groupingBy;
import static com.google.mu.util.stream.MoreCollectors.partitioningBy;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.mu.util.CaseBreaker;
import com.google.mu.util.stream.BiStream;

/**
 * Utility to map from a {@code ResultSet} to a pojo by invoking constructor from
 * the class {@code T}. Columns are matched by name (from column name to the
 * Java parameter names (if javac enables -parameters), or names annotated using
 * {@link SqlName @SqlName}.
 */
@CheckReturnValue
abstract class ResultMapper<T> {
  static <T> ResultMapper<T> toResultOf(Class<T> resultType) {
    checkArgument(
        !resultType.isPrimitive(),
        "resultType cannot be primitive: %s", resultType);
    checkArgument(resultType != Void.class, "resultType cannot be Void");
    if (resultType.getName().startsWith("java.")
        || resultType.getName().startsWith("javax.")
        || resultType.isArray()) {
      return new ResultMapper<T>() {
        @Override T from(ResultSet row) throws SQLException {
          return row.getObject(1, resultType);
        }
      };
    }
    return JavaBeanMapper.ofBeanClass(resultType)
        .orElseGet(() -> new UsingConstructor<T>(resultType));
  }

  abstract T from(ResultSet row) throws SQLException;

  static final class UsingConstructor<T> extends ResultMapper<T> {
    private final Class<T> type;
    private final NavigableMap<Integer, List<Creator<T>>> creators;

    @SuppressWarnings("unchecked")  // T.getDeclaredConstructors() should return Constructor<T>'s
    UsingConstructor(Class<T> type) {
      this.type = type;
      this.creators = stream(type.getDeclaredConstructors())
          .map(ctor -> (Constructor<T>) ctor)
          .filter(ctor -> !Modifier.isPrivate(ctor.getModifiers()))
          .collect(BiStream.groupingBy(ctor -> ctor.getParameterCount(), mapping(Creator::new, toList())))
          .collect(toImmutableNavigableMap());
      checkArgument(creators.size() > 0, "No accessible constructor from %s", type);
      checkAmbiguities();
    }

    @Override T from(ResultSet row) throws SQLException {
      Set<String> canonicalColumnNames = getCanonicalColumnNames(row.getMetaData());
      Map.Entry<Integer, List<Creator<T>>> band =
          creators.floorEntry(canonicalColumnNames.size());
      checkArgument(
          band != null,
          "No constructor found from %s suitable for mapping columns: %s", type, canonicalColumnNames);
      List<Creator<T>> candidates = band.getValue().stream()
          .collect(partitioningBy(Creator::isExplicitlyAnnotated, toList()))
          .andThen(
              (annotated, unannotated) -> annotated.size() > 0 ? annotated : unannotated);
      return resolve(candidates, canonicalColumnNames).create(row);
    }

    private Creator<T> resolve(List<Creator<T>> candidates, Set<String> columnNames) {
      List<Creator<T>> matched =
          candidates.stream()
              .filter(candidate -> columnNames.containsAll(candidate.getCanonicalColumnNames()))
              .collect(toList());
      checkArgument(
          matched.size() > 0, "No matching constructor found in %s for columns: %s",
          type, columnNames);
      checkArgument(
          matched.size() == 1, "Ambiguous constructors found in %s for columns: %s",
          type, columnNames);
      return matched.get(0);
    }

    private void checkAmbiguities() {
      for (List<Creator<T>> candidates : creators.values()) {
        Map<Set<String>, Creator<?>> byCanonicalColumNames = new HashMap<>();
        for (Creator<?> creator : candidates) {
          Set<String> names = creator.getCanonicalColumnNames();
          Creator<?> dup = byCanonicalColumNames.putIfAbsent(names, creator);
          checkArgument(
              dup == null,
              "Ambiguous constructors in %s matching the same set of column names: %s", type, names);
        }
      }
    }
  }

  private static final class Creator<T> {
    private final Constructor<T> constructor;
    private final List<Class<?>> paramTypes;
    private final List<String> sqlColumnNames;

    Creator(Constructor<T> constructor) {
      this.constructor = constructor;
      this.paramTypes = unmodifiableList(asList(constructor.getParameterTypes()));
      this.sqlColumnNames = stream(constructor.getParameters())
          .collect(groupingBy(param -> getNameForSql(param), counting()))
          .peek((name, cnt) -> checkArgument(cnt == 1, "Duplicate parameter name for sql: %s", name))
          .keys()
          .collect(toList());
      constructor.setAccessible(true);
    }

    T create(ResultSet row) throws SQLException {
      Object[] args = new Object[paramTypes.size()];
      for (int i = 0; i < paramTypes.size(); i++) {
        args[i] = row.getObject(sqlColumnNames.get(i), wrapperType(paramTypes.get(i)));
      }
      try {
        return constructor.newInstance(args);
      } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
        throw new RuntimeException(e);
      }
    }

    boolean isExplicitlyAnnotated() {
      return stream(constructor.getParameters()).anyMatch(param -> param.isAnnotationPresent(SqlName.class));
    }

    Set<String> getCanonicalColumnNames() {
      return sqlColumnNames.stream().map(ResultMapper::canonicalize).collect(toCollection(LinkedHashSet::new));
    }

    @Override public String toString() {
      return constructor.toString();
    }
  }

  /** Returns the label-to-typename mapping of all columns, in encounter order. */
  static Set<String> getCanonicalColumnNames(ResultSetMetaData metadata)
      throws SQLException {
    LinkedHashSet<String> mappings = new LinkedHashSet<>();
    int columnCount = metadata.getColumnCount();
    for (int i = 1; i <= columnCount; i++) {
      String label = canonicalize(metadata.getColumnLabel(i));
      checkArgument(mappings.add(label), "Duplicate column %s at index %s", label, i);
    }
    return Collections.unmodifiableSet(mappings);
  }

  private static String getNameForSql(Parameter param) {
    SqlName sqlName = param.getAnnotation(SqlName.class);
    if (sqlName != null) {
      checkArgument(
          !sqlName.value().isBlank(),
          "@SqlName defined in %s shouldn't be empty or blank: %s",
          param.getDeclaringExecutable().getDeclaringClass(), param);
      return sqlName.value().trim();
    }
    String paramName = param.getName();
    boolean noRealName =
        paramName.isEmpty()
            || after(prefix("arg"))
                .from(paramName)
                .filter(suffix -> {
                  try {
                    Integer.parseInt(suffix);
                    return true;
                  } catch (NumberFormatException e) {
                    return false;
                  }
                })
                .isPresent();
    checkArgument(
        !noRealName,
        "Parameter name not found. "
            + "Either enable javac flag -parameters, or annotate the parameter with @SqlName:\n %s",
        param);
    return paramName;
  }

  static String canonicalize(String name) {
    // Most dbms will use UPPER CASE. And if the java name is fooBar, it needs to be FOO_BAR.
    return new CaseBreaker()
        .breakCase(name)
        .map(seg -> seg.toUpperCase(Locale.ROOT))
        .collect(joining("_"));
  }

  // Since we don't want to use Guava
  static Class<?> wrapperType(Class<?> type) {
    if (type == boolean.class) {
      return Boolean.class;
    }
    if (type == double.class) {
      return Double.class;
    }
    if (type == float.class) {
      return Float.class;
    }
    if (type == long.class) {
      return Long.class;
    }
    if (type == int.class) {
      return Integer.class;
    }
    if (type == short.class) {
      return Short.class;
    }
    if (type == byte.class) {
      return Byte.class;
    }
    if (type == char.class) {
      return Character.class;
    }
    if (type == void.class) {
      return Void.class;
    }
    return type;
  }
}
