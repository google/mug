package com.google.mu.examples;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.mu.util.StringFormat;

@RunWith(JUnit4.class)
public class HowToUseStringFormatTest {

  @Test public void parseExample() {
    StringFormat timeFormat = new StringFormat("{hour}:{minute}:{second}.{millis}");
    String input = "12:10:01.234";
    assertThat(timeFormat.parse(input, (hour, minute, second, millis) -> minute))
        .isEqualTo("10");
  }

  @SuppressWarnings("StringUnformatArgsCheck")
  String failsBecauseTwoLambdaParametersAreExpected() {
	  return new StringFormat("{key}:{value}").parseOrThrow("k:v", key -> key);
  }
}
