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
import java.util.Set;

import com.google.errorprone.annotations.CheckReturnValue;

@CheckReturnValue
final class JavaBeanMapper<T> extends ResultMapper<T> {
  private final Class<T> beanClass;
  private final Constructor<T> defaultConstructor;
  private final Map<String, Populator> setters;

  private JavaBeanMapper(
      Class<T> beanClass, Constructor<T> defaultConstructor, Map<String, Populator> setters) {
    this.beanClass = beanClass;
    this.defaultConstructor = defaultConstructor;
    this.setters = setters;
  }

  @SuppressWarnings("unchecked")  // Class<T>.getDeclaredConstructors() must return Constructor<T>
  static <T> Optional<ResultMapper<T>> ofBeanClass(Class<T> beanClass) {
    try {
      Map<String, Populator> setters = biStream(stream(Introspector.getBeanInfo(beanClass).getPropertyDescriptors()))
          .filterValues(property -> property.getWriteMethod() != null)
          .mapKeys(property -> {
            SqlName sqlName = property.getWriteMethod().getAnnotation(SqlName.class);
            return canonicalize(sqlName == null ? property.getName() : sqlName.value());
          })
          .mapValues(Populator::of)
          .toMap();
      if (setters.isEmpty()) { // no setter? not a Java Bean
        return Optional.empty();
      }
      return stream(beanClass.getDeclaredConstructors())
          .filter(ctor -> ctor.getParameterCount() == 0 && !Modifier.isPrivate(ctor.getModifiers()))
          .peek(ctor -> ctor.setAccessible(true))
          .findAny()
          .map(ctor -> new JavaBeanMapper<T>(beanClass, (Constructor<T>) ctor, setters));
    } catch (IntrospectionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override T from(ResultSet row) throws SQLException {
    Set<String> columnNames = getCanonicalColumnNames(row.getMetaData());
    try {
      T bean = defaultConstructor.newInstance();
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
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  private interface Populator {
    void populate(Object bean, ResultSet row, String columnName)
        throws SQLException, InvocationTargetException, IllegalAccessException;

    static Populator of(PropertyDescriptor property) {
      Method setter = requireNonNull(property.getWriteMethod());
      setter.setAccessible(true);
      Class<?> type = property.getPropertyType();
      return (bean, row, columnName) -> {
        Object value = row.getObject(columnName, wrapperType(type));
        // for primitive, if the value is null, don't call setter.
        if (value != null || !type.isPrimitive()) {
          setter.invoke(bean, value);
        }
      };
    }
  }
}
