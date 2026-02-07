package com.google.mu.examples;

import com.google.mu.annotations.ParametersMustMatchByName;

public class ParametersByNameDemo {

  @SuppressWarnings("ParametersMustMatchByNameCheck")
  void parametersOutOfOrder(String userId, String userName) {
    withTwoStringParameters(userName, userId);
  }

  void literalRequiresNamesWithDuplicateTypes() {
    withTwoStringParameters(/* id */ "foo", /* name */ "bar");
  }

  void literalOkayWithDistinctTypes() {
    withIntAndStringParameters(123, "user1");
    withClassAndStringParameters(String.class, "user1");
  }

  void literalBooleanNeedsNameDespiteDistinctTypes() {
    withBooleanAndStringParameters(/* isActive */ false, "user1");
  }

  void literalNullNeedsNameDespiteDistinctTypes() {
    withIntAndStringParameters(123, /* name */ null);
  }

  @ParametersMustMatchByName
  void withTwoStringParameters(String id, String name) {}

  @ParametersMustMatchByName
  void withIntAndStringParameters(long id, String name) {}

  @ParametersMustMatchByName
  void withBooleanAndStringParameters(boolean isActive, String name) {}

  @ParametersMustMatchByName
  void withClassAndStringParameters(Class<?> type, String name) {}
}
