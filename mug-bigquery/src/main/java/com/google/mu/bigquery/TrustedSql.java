package com.google.mu.bigquery;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.mapping;

import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.errorprone.annotations.CompileTimeConstant;

/**
 * A SQL snippet that's trusted to be safe against injection.
 *
 * @since 7.1
 */
public final class TrustedSql {
  private String sql;
  
  private TrustedSql(String sql) {
    this.sql = requireNonNull(sql);
  }
  
  public static TrustedSql of(@CompileTimeConstant String constant) {
    return new TrustedSql(constant);
  }

  /** Returns a joiner that joins TrustedSql elements using {@code delim}. */
  public static Collector<TrustedSql, ?, TrustedSql> joining(@CompileTimeConstant String delim) {
    return collectingAndThen(
        mapping(TrustedSql::toString, Collectors.joining(delim)),
        TrustedSql::new);
  }
  
  @Override public boolean equals(Object obj) {
    if (obj instanceof TrustedSql) {
      TrustedSql that = (TrustedSql) obj;
      return sql.equals(that.sql);
    }
    return false;
  }
  
  @Override public int hashCode() {
    return sql.hashCode();
  }
  
  @Override public String toString() {
    return sql;
  }
}
