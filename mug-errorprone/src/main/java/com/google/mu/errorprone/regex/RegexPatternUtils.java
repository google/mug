package com.google.mu.errorprone.regex;

import com.google.common.labs.regex.RegexPattern;
import com.google.mu.util.graph.Walker;
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

  static boolean isWildcard(RegexPattern pattern) {
    if (pattern instanceof RegexPattern.Quantified q) {
      RegexPattern inner = unwrapGroup(q.element());
      return inner instanceof RegexPattern.PredefinedCharClass pcc
          && pcc == RegexPattern.PredefinedCharClass.ANY_CHAR;
    }
    return false;
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
            if (charRangesOf(ei).intersects(charRangesOf(ej))) {
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
          case RegexPattern.AtLeast atLeast -> atLeast.min() >= 0;
          case RegexPattern.Limited limited -> limited.max() > 5;
          default -> false;
        };
  }

  private static CharRanges charRangesOf(RegexPattern pattern) {
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

  static boolean containsNode(RegexPattern root, RegexPattern target) {
    return root.equals(target)
        || Walker.inTree(RegexPatternUtils::childrenOf)
            .preOrderFrom(root)
            .anyMatch(node -> node.equals(target));
  }

  private RegexPatternUtils() {}
}
