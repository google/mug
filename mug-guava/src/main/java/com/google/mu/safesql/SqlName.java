package com.google.mu.safesql;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a constructor parameter's name corresponding to the name in SQL query.
 *
 * <p>If you can enable the javac {@code -parameters} flag, you do not need this annotation.
 *
 * <p>Otherwise, you can annotate your result type's constructor parameters so that they can be
 * used as result type of {@link SafeSql#query(java.sql.Connection, Class)} and friends.
 *
 * @since 8.7
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
public @interface SqlName {
  /** The name to fetch the corresponding value from a {@link java.sql.ResultSet}. */
  String value();
}
