/*****************************************************************************
 * Copyright (C) google.com                                                  *
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.common.labs.parse;

import static com.google.common.labs.parse.Utils.checkArgument;
import static com.google.mu.util.CharPredicate.ASCII;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.labs.regex.RegexPattern;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.IntStream;

/** Internal utilities to validate and extract properties from {@link RegexPattern} AST. */
final class Regexes {
  private static final Set<String> EMPTY_PREFIX = Set.of("");

  static RegexPattern validate(String regex) {
    RegexPattern pattern = RegexPattern.of(regex);
    Regexes.checkSupportedFeatures(pattern);
    checkArgument(!pattern.mayMatchEmpty(), "regex must not match empty string: %s", regex);
    return pattern;
  }

  private static void checkSupportedFeatures(RegexPattern pattern) {
    switch (pattern) {
      case RegexPattern.Anchor anchor ->
          throw new IllegalArgumentException("anchors are not allowed in regex parser: " + anchor);
      case RegexPattern.Lookaround lookaround ->
          throw new IllegalArgumentException(
              "lookarounds are not allowed in regex parser: " + lookaround);
      case RegexPattern.Backreference backreference ->
          throw new IllegalArgumentException(
              "backreferences are not allowed in regex parser: " + backreference);
      case RegexPattern.Sequence sequence ->
          sequence.elements().forEach(Regexes::checkSupportedFeatures);
      case RegexPattern.Alternation alternation ->
          alternation.alternatives().forEach(Regexes::checkSupportedFeatures);
      case RegexPattern.Group group -> checkSupportedFeatures(group.content());
      case RegexPattern.Quantified quantified -> checkSupportedFeatures(quantified.element());
      case RegexPattern.Literal literal -> {}
      case RegexPattern.PredefinedCharClass predefined -> {}
      case RegexPattern.CharacterSet characterSet -> {}
      case RegexPattern.CharacterProperty characterProperty -> {}
      case RegexPattern.CharacterProperty.Negated negated -> {}
    }
  }

  static Set<String> prefixesOf(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Literal literal ->
          literal.value().isEmpty() ? EMPTY_PREFIX : Set.of(literal.value());
      case RegexPattern.Sequence sequence -> {
        Set<String> result = new LinkedHashSet<>();
        for (RegexPattern element : sequence.elements()) {
          Set<String> prefixes = prefixesOf(element);
          if (prefixes.contains("")) { // "" means anything goes
            yield EMPTY_PREFIX;
          }
          result.addAll(prefixes);
          if (!element.mayMatchEmpty()) {
            // once we reach a never-empty pattern, chars after it can't be safely used as prefixes.
            break;
          }
        }
        yield Set.copyOf(result);
      }
      case RegexPattern.Alternation alternation -> alternation.alternatives().stream()
          .flatMap(alternative -> prefixesOf(alternative).stream())
          .collect(toPrefixSet());
      case RegexPattern.Group group -> prefixesOf(group.content());
      case RegexPattern.Quantified quantified -> prefixesOf(quantified.element());
      case RegexPattern.CharacterSet.AnyOf anyOf -> anyOf.elements().stream()
          .flatMap(element -> charsOf(element).stream())
          .collect(toPrefixSet());
      case RegexPattern.CharacterSet.NoneOf noneOf -> EMPTY_PREFIX;
      case RegexPattern.PredefinedCharClass predefined ->
          predefined == RegexPattern.PredefinedCharClass.DIGIT
              ? CharacterSet.DECIMAL.getAsciiPrefixes()
              : EMPTY_PREFIX;
      case RegexPattern.CharacterProperty characterProperty -> EMPTY_PREFIX;
      case RegexPattern.CharacterProperty.Negated negated -> EMPTY_PREFIX;
      case RegexPattern.Anchor anchor -> EMPTY_PREFIX;
      case RegexPattern.Lookaround lookaround -> EMPTY_PREFIX;
      case RegexPattern.Backreference backreference -> EMPTY_PREFIX;
    };
  }

  private static Set<String> charsOf(RegexPattern.CharSetElement element) {
    return switch (element) {
      case RegexPattern.LiteralChar literalChar -> Set.of(String.valueOf(literalChar.value()));
      case RegexPattern.CharRange range ->
          // Fall back to empty prefix if the range contains non-ASCII characters (which
          // PrefixPruneTree doesn't index anyway) or is too large to register in the lookup tree.
          ASCII.test(range.end()) && range.end() - range.start() < 30
              ? IntStream.rangeClosed(range.start(), range.end())
                  .mapToObj(c -> String.valueOf((char) c))
                  .collect(toUnmodifiableSet())
              : EMPTY_PREFIX;
      case RegexPattern.CharacterProperty characterProperty -> EMPTY_PREFIX;
      case RegexPattern.CharacterProperty.Negated negated -> EMPTY_PREFIX;
      case RegexPattern.PredefinedCharClass predefined -> EMPTY_PREFIX;
    };
  }

  private static Collector<String, ?, Set<String>> toPrefixSet() {
    return collectingAndThen(
        toUnmodifiableSet(), union -> union.contains("") ? EMPTY_PREFIX : union);
  }

  private Regexes() {}
}
