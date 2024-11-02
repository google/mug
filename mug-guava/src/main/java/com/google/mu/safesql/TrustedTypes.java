package com.google.mu.safesql;

import static com.google.common.base.MoreObjects.firstNonNull;

final class TrustedTypes {

  static final String TRUSTED_SQL_TYPE_NAME = firstNonNull(
      System.getProperty("com.google.mu.safesql.SafeQuery.trusted_sql_type"),
      "com.google.storage.googlesql.safesql.TrustedSqlString");

  static boolean isTrusted(Object value) {
    return value instanceof SafeQuery || value.getClass().getName().equals(TRUSTED_SQL_TYPE_NAME);
  }

  private TrustedTypes() {}
}
