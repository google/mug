package com.google.mu.safesql;

import java.sql.SQLException;

/** For lambdas that can throw {@link SQLException}. */
@FunctionalInterface
public interface SqlSupplier<T> {
  T get() throws SQLException;
}