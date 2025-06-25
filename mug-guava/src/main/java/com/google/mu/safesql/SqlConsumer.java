package com.google.mu.safesql;

import java.sql.SQLException;

/**
 * An action that could throw {@link SqlException}.
 *
 * @since 8.8
 */
@FunctionalInterface
public interface SqlConsumer<T> {
  void accept(T value) throws SQLException;
}
