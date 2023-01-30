package com.google.mu.util;

import static com.google.mu.util.InternalCollectors.toImmutableList;
import static com.google.mu.util.stream.BiCollectors.toMap;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.reducing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.mu.util.stream.BiStream;
import com.google.mu.util.stream.MoreStreams;

/**
 * A string template uses curly braced placholder variable names such as
 * {@code "{foo}"}.
 *
 * <p>Identical placeholder names in the template are treated similar to back references
 * in regex. That is {@code "/bns/{cell}/borg/{cell}/{user}"} will match {@code "/bns/ir/borg/ir/myname"}
 * but won't match {@code "/bns/ir/borg/xy/myname"} because the "{cell}" variable is expected to match
 * the same substring.
 */
public final class StringTemplate extends StringFormat {
  private static final Substring.RepeatingPattern PLACEHOLDERS =
      Substring.spanningInOrder("{", "}").repeatedly();
  private final List<String> placeholderNames;
  private final List<List<Integer>> placeholdersWithSameName;

  public StringTemplate(String template) {
    super(template, PLACEHOLDERS);
    this.placeholderNames = placeholders().stream()
        .map(StringTemplate::getPlaceholderName)
        .collect(toImmutableList());
    this.placeholdersWithSameName = getPlaceholderIndexesWithSameName(placeholderNames);
  }

  public Optional<Map<String, String>> parseToMap(String input) {
    return parse(input)
        .map(values -> BiStream.zip(placeholderNames, values)
            .collect((toMap(collectingAndThen(reducing((a, b) -> a), v -> v.get().toString())))));
  }

  @Override
  public Optional<List<Substring.Match>> parse(String input) {
    return super.parse(input).filter(this::consistentPerPlaceholderNames);
  }

  @Override
  public Stream<List<Substring.Match>> scan(String input) {
    return super.scan(input).filter(this::consistentPerPlaceholderNames);
  }

  private boolean consistentPerPlaceholderNames(List<Substring.Match> candidate) {
    for (List<Integer> indexes : placeholdersWithSameName) {
      CharSequence canonical = candidate.get(indexes.get(0));
      if (indexes.stream().skip(1).anyMatch(i -> !equalChars(candidate.get(i), canonical))) {
        return false;
      }
    }
    return true;
  }

  private static boolean equalChars(CharSequence a, CharSequence b) {
    if (a.length() != b.length()) {
      return false;
    }
    for (int i = 0; i < a.length(); i++) {
      if (a.charAt(i) != b.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private static List<List<Integer>> getPlaceholderIndexesWithSameName(List<String> placeholderNames) {
    Map<String, List<Integer>> indexClusters = new LinkedHashMap<>();
    BiStream.zip(placeholderNames.stream(), MoreStreams.indexesFrom(0))
        .forEachOrdered(((name, index) -> indexClusters.computeIfAbsent(name, k -> new ArrayList<>()).add(index)));
    return indexClusters.values().stream()
        .filter(indexes -> indexes.size() > 1)
        .collect(toImmutableList());
  }

  private static String getPlaceholderName(Substring.Match placeholder) {
    String name = placeholder.skip(1, 1).toString();
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Empty placeholder {} not supported.");
    }
    for (int i = 0; i < name.length(); i++) {
      if (!CharPredicate.WORD.test(name.charAt(i))) {
        throw new IllegalArgumentException("Only alphanumeric or underscore (_) allowed in placeholder names: " + name);
      }
    }
    return name;
  }
}
