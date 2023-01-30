package com.google.mu.util;

import static com.google.common.truth.Truth8.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableMap;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class StringTemplateTest {

  @Test public void parseToMap_noDuplicatePlaceholderNames() {
    assertThat(new StringTemplate("{key}: {value}").parseToMap("foo: bar"))
        .hasValue(ImmutableMap.of("key", "foo", "value", "bar"));
  }

  @Test public void parseToMap_duplicatePlaceholderNamesMatch() {
    assertThat(new StringTemplate("/bns/{cell}/borg/{cell}/bns/{user}").parseToMap("/bns/ir/borg/ir/bns/myname"))
        .hasValue(ImmutableMap.of("cell", "ir", "user", "myname"));
  }

  @Test public void parseToMap_duplicatePlaceholderNamesDontMatch() {
    assertThat(new StringTemplate("/bns/{cell}/borg/{cell}/bns/{user}").parseToMap("/bns/ir/borg/os/bns/myname"))
        .isEmpty();
  }

  @Test public void parse_duplicatePlaceholderNamesMatch() {
    assertThat(
            new StringTemplate("/bns/{cell}/borg/{cell}/bns/{user}")
                .parse("/bns/ir/borg/ir/bns/myname", (c1, c2, user) -> c1 + c2 + user))
        .hasValue("irirmyname");
  }

  @Test public void parse_duplicatePlaceholderNamesDontMatch() {
    assertThat(
            new StringTemplate("/bns/{cell}/borg/{cell}/bns/{user}")
                .parse("/bns/ir/borg/os/bns/myname", (c1, c2, user) -> c1 + c2 + user))
        .isEmpty();
  }

  @Test public void scan_duplicatePlaceholderNamesMatch() {
    assertThat(
            new StringTemplate("/bns/{cell}/borg/{cell}/bns/{user}/")
                .scan("/bns/ir/borg/ir/bns/prod/ /bns/os/borg/os/bns/test/", (c1, c2, user) -> c1 + c2 + user))
        .containsExactly("irirprod", "osostest")
        .inOrder();
  }

  @Test public void scan_duplicatePlaceholderNamesDontMatch() {
    assertThat(
            new StringTemplate("/bns/{cell}/borg/{cell}/bns/{user}/")
                .scan("/bns/ir/borg/ir2/bns/prod/ /bns/os/borg/os/bns/test/", (c1, c2, user) -> c1 + c2 + user))
        .containsExactly("osostest")
        .inOrder();
  }

  @Test public void emptyPlaceholderNotAllowed() {
    assertThrows(IllegalArgumentException.class, () -> new StringTemplate("bad placeholder: {}"));
  }

  @Test public void punctuationsNotAllowedInPlaceholderName() {
    assertThrows(IllegalArgumentException.class, () -> new StringTemplate("bad placeholder: {*}"));
    assertThrows(IllegalArgumentException.class, () -> new StringTemplate("bad placeholder: { a}"));
    assertThrows(IllegalArgumentException.class, () -> new StringTemplate("bad placeholder: {b }"));
  }
}
