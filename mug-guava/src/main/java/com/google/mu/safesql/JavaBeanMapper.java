package com.google.mu.safesql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.mu.util.stream.BiStream.biStream;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Primitives;

final class JavaBeanMapper<T> extends ResultMapper<T> {
  private final Class<T> beanClass;
  private final Constructor<T> defaultConstructor;
  private final ImmutableMap<String, Populator> setters;

  private JavaBeanMapper(
      Class<T> beanClass, Constructor<T> defaultConstructor, Map<String, Populator> setters) {
    this.beanClass = beanClass;
    this.defaultConstructor = defaultConstructor;
    this.setters = ImmutableMap.copyOf(setters);
  }

  @SuppressWarnings("unchecked")  // Class<T>.getDeclaredConstructors() must return Constructor<T>
  static <T> Optional<ResultMapper<T>> ofBeanClass(Class<T> beanClass) {
    return stream(beanClass.getDeclaredConstructors())
        .filter(ctor -> ctor.getParameterCount() == 0 && !Modifier.isPrivate(ctor.getModifiers()))
        .findAny()
        .map(ctor -> {
          ctor.setAccessible(true);
          try {
            return new JavaBeanMapper<T>(
                beanClass,
                (Constructor<T>) ctor,
                biStream(stream(Introspector.getBeanInfo(beanClass).getPropertyDescriptors()))
                    .filterValues(property -> property.getWriteMethod() != null)
                    .mapKeys(property -> {
                      SqlName sqlName = property.getWriteMethod().getAnnotation(SqlName.class);
                      return canonicalize(sqlName == null ? property.getName() : sqlName.value());
                    })
                    .mapValues(Populator::of)
                    .toMap());
          } catch (IntrospectionException e) {
            throw new VerifyException(e);
          }
        });
  }

  @Override T from(ResultSet row) throws SQLException {
    ImmutableSet<String> columnNames = getCanonicalColumnNames(row.getMetaData());
    T bean;
    try {
      bean = defaultConstructor.newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new VerifyException(e);
    }
    if (columnNames.size() >= setters.size()) { // full population
      for (Map.Entry<String, Populator> property : setters.entrySet()) {
        property.getValue().populate(bean, row, property.getKey());
      }
    } else { // sparsely populate from the columns
      for (String name : columnNames) {
        Populator populator = setters.get(name);
        checkArgument(
            populator != null,
            "No settable property is defined by %s for column %s", beanClass, name);
        populator.populate(bean, row, name);
      }
    }
    return bean;
  }

  private interface Populator {
    void populate(Object bean, ResultSet row, String columnName) throws SQLException;

    static Populator of(PropertyDescriptor property) {
      Method setter = requireNonNull(property.getWriteMethod());
      setter.setAccessible(true);
      Class<?> type = property.getPropertyType();
      return (bean, row, columnName) -> {
        Object value = row.getObject(columnName, Primitives.wrap(type));
        if (value == null && type.isPrimitive()) {
          // for primitive, if the value is null, don't call setter.
          return;
        }
        try {
          setter.invoke(bean, value);
        } catch (InvocationTargetException | IllegalAccessException e) {
          throw new VerifyException(e);
        }
      };
    }
  }
}
