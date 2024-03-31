package com.google.mu.errorprone.testdata;

import com.google.mu.util.StringFormat;

public class ParseWithFewerLambdaArgs {

  void iShouldFailToCompile() {
    System.out.println(new StringFormat("{foo}/{bar}").parse("a/b", foo -> foo));
  }
}
