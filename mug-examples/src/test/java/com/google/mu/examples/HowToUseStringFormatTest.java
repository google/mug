package com.google.mu.examples;

import static com.google.common.truth.Truth8.assertThat;

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
        .hasValue("10");
  }

  @Test public void scanExample() {
    StringFormat boldFormat = new StringFormat("<b>{bolded}</b>");
    String input = "Please come back to the <b>front desk</b> at <b>12:00</b>.";
    assertThat(boldFormat.scan(input, bolded -> bolded))
        .containsExactly("front desk", "12:00");
  }

  @SuppressWarnings("StringUnformatArgsCheck")
  String failsBecauseTwoLambdaParametersAreExpected() {
	  return new StringFormat("{key}:{value}").parseOrThrow("k:v", key -> key);
  }

  @SuppressWarnings("StringUnformatArgsCheck")
  String failsBecauseLambdaParameterNamesAreOutOfOrder() {
    return new StringFormat("{key}:{value}").parseOrThrow("k:v", (value, key) -> key);
  }
 
  @SuppressWarnings("StringFormatPlaceholderNamesCheck")
  String failsDueToBadPlaceholderName() {
    return new StringFormat("{?}:{-}").parseOrThrow("k:v", (k, v) -> k);
  }
}
