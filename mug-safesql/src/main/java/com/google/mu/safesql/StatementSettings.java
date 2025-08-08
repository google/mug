package com.google.mu.safesql;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Encapsulates the settings on a JDBC statement.
 *
 * @since 9.2
 */
@FunctionalInterface
public interface StatementSettings {
  /** Applies the settings on the statement. */
  void apply(Statement statment) throws SQLException;
}
