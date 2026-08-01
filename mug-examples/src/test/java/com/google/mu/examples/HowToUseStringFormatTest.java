package com.google.mu.examples;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.mu.util.Substring.first;
import static com.google.mu.util.Substring.last;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.mu.safesql.SafeSql;
import com.google.mu.util.StringFormat;
import com.google.mu.util.Substring;

@RunWith(JUnit4.class)
public class HowToUseStringFormatTest {

  @Test public void parseExample() {
    StringFormat timeFormat = new StringFormat("{hour}:{minute}:{second}.{millis}");
    String input = "12:10:01.234";
    assertThat(timeFormat.parse(input, (hour, minute, second, millis) -> minute))
        .hasValue("10");
  }

  @Test public void parseGreedyExample() {
    StringFormat filenameFormat = new StringFormat("{path}/{name}.{ext}");
    String input = "/usr/tom/my.file.txt";
    assertThat(filenameFormat.parseGreedy(input, (path, name, ext) -> name))
        .hasValue("my.file");
  }

  @Test public void scanExample() {
    StringFormat boldFormat = new StringFormat("<b>{bolded}</b>");
    String input = "Please come back to the <b>front desk</b> at <b>12:00</b>.";
    assertThat(boldFormat.scan(input, bolded -> bolded))
        .containsExactly("front desk", "12:00");
  }

  @Test public void safeSqlExample() {
    assertThat(SafeSql.of("WHERE id = '{id}'", "foo").debugString())
        .isEqualTo("WHERE id = ? /* foo */");
  }

  @Test public void parse2dArray() {
    String x = "{ {F, 40 , 40 , 2000},{L, 60 , 60 , 1000},{F, 40 , 40 , 2000}}";
    String nested = Substring.between(first('{'), last('}')).from(x).get();
    List<List<String>> result =  Substring.between('{', '}')
        .repeatedly()
        .from(nested)
        .map(first(',').repeatedly()::splitThenTrim)
        .map(elements -> elements.map(Substring.Match::toString).collect(toList()))
        .collect(toList());
    assertThat(result)
       .containsExactly(
           asList("F", "40", "40", "2000"),
           asList("L", "60", "60", "1000"),
           asList("F", "40", "40", "2000"))
       .inOrder();
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

  @SuppressWarnings("StringFormatArgsCheck")
  SafeSql mismatchingPlaceholderInSafeSqlTemplate(String name) {
    return SafeSql.template("WHERE id = '{id}'").with(name);
  }

  @SuppressWarnings("StringUnformatArgsCheck")
  List<String> failsScanningTwoPlaceholdersToList() {
    return new StringFormat("{key}:{value}").scanAndCollectFrom("k:v", toList());
  }

  @SuppressWarnings("StringUnformatArgsCheck")
  Map<String, String> failsScanningSinglePlaceholderToMap() {
    return new StringFormat("({v})").scanAndCollectFrom("(1), (2)", Collectors::toMap);
  }
}
