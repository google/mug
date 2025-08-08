package com.google.mu.safesql;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Functional interface to apply settings onto a JDBC {@link Statement}.
 *
 * <p>For example: {@code stmt -> stmt.setMaxRows(100)}.
 *
 * @since 9.2
 */
@FunctionalInterface
public interface StatementSettings {
  /** Applies the settings on the statement. */
  void apply(Statement statment) throws SQLException;
}
