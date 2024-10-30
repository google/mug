package com.google.mu.safesql;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

/**
 * Used to configure trusted table name to be used in safe SQL.
 *
 * @since 8.2
 */
@Retention(RUNTIME)
public @interface TableName {
  String value();
}
