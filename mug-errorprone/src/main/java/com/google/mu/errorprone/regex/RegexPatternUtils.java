package com.google.mu.errorprone.regex;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.labs.regex.RegexPattern;
import java.util.List;
import java.util.stream.Stream;

/** Common AST utilities for traversing and inspecting {@link RegexPattern} trees. */
final class RegexPatternUtils {
  static RegexPattern unwrapGroup(RegexPattern pattern) {
    while (pattern instanceof RegexPattern.Group group) {
      pattern = group.content();
    }
    return pattern;
  }

  static Stream<RegexPattern> childrenOf(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Sequence seq -> seq.elements().stream();
      case RegexPattern.Alternation alt -> alt.alternatives().stream();
      case RegexPattern.Group group -> Stream.of(group.content());
      case RegexPattern.Quantified q -> Stream.of(q.element());
      default -> Stream.empty();
    };
  }

  record OverlappingQuantifierPair(
      int firstIndex,
      int secondIndex,
      RegexPattern.Quantified first,
      RegexPattern.Quantified second) {}

  static Stream<OverlappingQuantifierPair> findOverlappingQuantifiers(RegexPattern.Sequence seq) {
    List<RegexPattern> elements = seq.elements();
    for (int i = 0; i < elements.size(); i++) {
      RegexPattern ei = unwrapGroup(elements.get(i));
      if (isUnboundedQuantified(ei)) {
        for (int j = i + 1; j < elements.size(); j++) {
          RegexPattern ej = unwrapGroup(elements.get(j));
          if (isUnboundedQuantified(ej)) {
            if (CharRanges.intersects(charRangesOf(ei), charRangesOf(ej))) {
              return Stream.of(
                  new OverlappingQuantifierPair(
                      i, j, (RegexPattern.Quantified) ei, (RegexPattern.Quantified) ej));
            }
          }
          if (ej.metadata().minSize() > 0) {
            break;
          }
        }
      }
    }
    return Stream.empty();
  }

  private static boolean isUnboundedQuantified(RegexPattern pattern) {
    pattern = unwrapGroup(pattern);
    return pattern instanceof RegexPattern.Quantified q && !q.quantifier().isPossessive()
        && switch (q.quantifier()) {
          case RegexPattern.AtLeast atLeast -> true;
          case RegexPattern.Limited limited -> limited.max() > 5;
          default -> false;
        };
  }

  private static ImmutableRangeSet<Integer> charRangesOf(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Quantified q -> charRangesOf(q.element());
      case RegexPattern.Group group -> charRangesOf(group.content());
      case RegexPattern.CharacterSet cs -> CharRanges.from(cs);
      case RegexPattern.PredefinedCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.PosixCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.Literal lit -> CharRanges.of(lit.value().charAt(0));
      default -> CharRanges.ANY;
    };
  }

  private RegexPatternUtils() {}
}
