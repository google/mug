package com.google.mu.safesql;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

/**
 * Used to configure trusted table name to be used in safe SQL.
 *
 * <p>Frameworks can use this annotation to configure table names on classes, methods,
 * enum constants etc. at compile time.
 *
 * @since 8.2
 */
@Retention(RUNTIME)
public @interface TableName {
  String value();
}
