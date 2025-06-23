package com.google.mu.safesql;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to facilitate mapping from a result set row to a pojo through the
 * {@link SafeSql#query(java.sql.Connection, Class)} method and friends.
 *
 * <p>For records, you can use it to annotate a constructor parameter's name to match the
 * corresponding result set column name from a SQL query, although, if you can enable the javac
 * {@code -parameters} flag, you likely do not need this annotation.
 *
 * <p>For Java beans, you can annotate a setter method when the property name doesn't otherwise
 * match the result set column name.
 *
 * @since 8.7
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
public @interface SqlName {
  /**
   * The name to fetch the corresponding value from a {@link java.sql.ResultSet}.
   *
   * <p>Case (camelCase, snake_case etc.) doesn't matter.
   */
  String value();
}
