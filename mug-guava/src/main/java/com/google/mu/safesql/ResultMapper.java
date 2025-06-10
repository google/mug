package com.google.mu.safesql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.mu.util.Substring.after;
import static com.google.mu.util.Substring.prefix;
import static com.google.mu.util.stream.BiStream.groupingBy;
import static com.google.mu.util.stream.GuavaCollectors.flatteningToImmutableListMultimap;
import static com.google.mu.util.stream.GuavaCollectors.toListOf;
import static com.google.mu.util.stream.MoreCollectors.partitioningBy;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.counting;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Ascii;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Primitives;
import com.google.mu.util.stream.BiStream;

/**
 * Utility to map from a {@code ResultSet} to a pojo by invoking constructor from
 * the class {@code T}. Columns are matched by name (from column name to the
 * Java parameter names (if javac enables -parameters), or names annotated using
 * {@link SqlName @SqlName}.
 */
abstract class ResultMapper<T> {
  private static final ImmutableSet<Class<?>> SQL_PRIMITIVE_TYPES =
      ImmutableSet.of(
          ZonedDateTime.class, OffsetDateTime.class, Instant.class,
          LocalDate.class, LocalTime.class, String.class);

  static <T> ResultMapper<T> toResultOf(Class<T> resultType) {
    checkArgument(
        !Primitives.allPrimitiveTypes().contains(resultType), "resultType cannot be primitive: %s",
        resultType);
    checkArgument(resultType != Void.class, "resultType cannot be Void");
    if (SQL_PRIMITIVE_TYPES.contains(resultType) || Primitives.isWrapperType(resultType)) {
      return new ResultMapper<T>() {
        @Override T from(ResultSet row) throws SQLException {
          return row.getObject(1, resultType);
        }
      };
    }
    return new UsingConstructor<T>(resultType);
  }

  abstract T from(ResultSet row) throws SQLException;

  static final class UsingConstructor<T> extends ResultMapper<T> {
    private final Class<T> type;
    private final ImmutableListMultimap<Integer, Creator<T>> creators;

    @SuppressWarnings("unchecked")  // T.getDeclaredConstructors() should return Constructor<T>'s
    UsingConstructor(Class<T> type) {
      this.type = type;
      this.creators = stream(type.getDeclaredConstructors())
          .map(ctor -> (Constructor<T>) ctor)
          .filter(ctor -> !Modifier.isPrivate(ctor.getModifiers()))
          .collect(BiStream.groupingBy(ctor -> ctor.getParameterCount(), toListOf(Creator::new)))
          .collect(flatteningToImmutableListMultimap(List::stream));
      checkArgument(creators.size() > 0, "No accessible constructor from %s", type);
      checkAmbiguities();
    }

    @Override T from(ResultSet row) throws SQLException {
      ImmutableSet<String> canonicalColumnNames = getCanonicalColumnNames(row.getMetaData());
      List<Creator<T>> candidates = creators.get(canonicalColumnNames.size()).stream()
          .collect(partitioningBy(Creator::isExplicitlyAnnotated, toImmutableList()))
          .andThen(
              (annotated, unannotated) -> annotated.size() > 0 ? annotated : unannotated);
      return resolve(candidates, canonicalColumnNames).create(row);
    }

    private Creator<T> resolve(List<Creator<T>> candidates, Set<String> columnNames) {
      ImmutableList<Creator<T>> matched =
          candidates.stream()
              .filter(candidate -> candidate.getCanonicalColumnNames().equals(columnNames))
              .collect(toImmutableList());
      checkArgument(
          matched.size() > 0, "No matching constructor found in %s for columns: %s",
          type, columnNames);
      checkArgument(
          matched.size() == 1, "Ambiguous constructors found in %s for columns: %s",
          type, columnNames);
      return matched.get(0);
    }

    private void checkAmbiguities() {
      Map<ImmutableSet<String>, Creator<?>> byCanonicalColumNames = new HashMap<>();
      for (Creator<?> creator : creators.values()) {
        ImmutableSet<String> names = creator.getCanonicalColumnNames();
        Creator<?> dup = byCanonicalColumNames.putIfAbsent(names, creator);
        checkArgument(
            dup == null,
            "Ambiguous constructors in %s matching the same set of column names: %s", type, names);
      }
    }
  }

  private static final class Creator<T> {
    private final Constructor<T> constructor;
    private final ImmutableList<Class<?>> paramTypes;
    private final ImmutableList<String> sqlColumnNames;


    Creator(Constructor<T> constructor) {
      this.constructor = constructor;
      this.paramTypes = ImmutableList.copyOf(constructor.getParameterTypes());
      this.sqlColumnNames = stream(constructor.getParameters())
          .collect(groupingBy(param -> getNameForSql(param), counting()))
          .peek((name, cnt) -> checkArgument(cnt == 1, "Duplicate parameter name for sql: %s", name))
          .keys()
          .collect(toImmutableList());
      constructor.setAccessible(true);
    }

    T create(ResultSet row) throws SQLException {
      Object[] args = new Object[paramTypes.size()];
      for (int i = 0; i < paramTypes.size(); i++) {
        args[i] = row.getObject(sqlColumnNames.get(i), Primitives.wrap(paramTypes.get(i)));
      }
      try {
        return constructor.newInstance(args);
      } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
        throw new VerifyException(e);
      }
    }

    boolean isExplicitlyAnnotated() {
      return stream(constructor.getParameters()).anyMatch(param -> param.isAnnotationPresent(SqlName.class));
    }

    ImmutableSet<String> getCanonicalColumnNames() {
      return sqlColumnNames.stream().map(ResultMapper::canonicalize).collect(toImmutableSet());
    }

    @Override public String toString() {
      return constructor.toString();
    }
  }


  /** Returns the label-to-typename mapping of all columns, in encounter order. */
  private static ImmutableSet<String> getCanonicalColumnNames(ResultSetMetaData metadata)
      throws SQLException {
    LinkedHashSet<String> mappings = new LinkedHashSet<>();
    int columnCount = metadata.getColumnCount();
    for (int i = 1; i <= columnCount; i++) {
      String label = canonicalize(metadata.getColumnLabel(i));
      checkArgument(mappings.add(label), "Duplicate column %s at index %s", label, i);
    }
    return ImmutableSet.copyOf(mappings);
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
                .filter(suffix -> Ints.tryParse(suffix) != null)
                .isPresent();
    checkArgument(
        !noRealName,
        "Parameter name not found. "
            + "Either enable javac flag -parameters, or annotate the parameter with @SqlName:\n %s",
        param);
    return paramName;
  }

  private static String canonicalize(String name) {
    return Ascii.toUpperCase(name);
  }
}
