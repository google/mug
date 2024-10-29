package com.google.mu.safesql;

import java.sql.SQLException;

/** A simple unchecked wrapper of {@link SQLException}. */
public class UncheckedSqlException extends RuntimeException {
  private final SQLException sqlException;

  public UncheckedSqlException(SQLException sqlException) {
    super(sqlException);
    this.sqlException = sqlException;
  }

  public SQLException asChecked() {
    return sqlException;
  }
}
