package com.google.mu.safesql;

import java.sql.SQLException;

/**
 * Similar to {@link java.util.function.Consumer} but can throw {@link SQLException}.
 *
 * @since 9.0
 */
@FunctionalInterface
public interface SqlConsumer<T> {
  void accept(T value) throws SQLException;
}
