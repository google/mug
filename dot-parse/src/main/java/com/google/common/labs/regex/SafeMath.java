package com.google.common.labs.regex;

final class SafeMath {
  static int saturatedAdd(int a, int b) {
    long sum = (long) a + b;
    return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
  }

  static int saturatedMultiply(int a, int b) {
    long product = (long) a * b;
    return product > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) product;
  }

  private SafeMath() {}
}
