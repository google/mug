package com.google.mu.errorprone.regex;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.labs.regex.RegexPattern;
import com.google.mu.util.graph.Walker;
import java.util.List;
import java.util.stream.Stream;

/** Common AST utilities for traversing and inspecting {@link RegexPattern} trees. */
public final class RegexPatternUtils {
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
      case RegexPattern.Lookaround lookaround -> Stream.of(lookaround.target());
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
      if (isUnboundedQuantified(ei)
          && !isReluctantBoundedBy(
              (RegexPattern.Quantified) ei, elements.subList(i + 1, elements.size()))) {
        for (int j = i + 1; j < elements.size(); j++) {
          RegexPattern ej = unwrapGroup(elements.get(j));
          if (ej instanceof RegexPattern.Anchor) {
            break;
          }
          if (isUnboundedQuantified(ej)) {
            if (CharRanges.intersects(charRangesOf(ei), charRangesOf(ej))) {
              if (isTerminalUnconstrainedWildcard(
                  ei, ej, elements.subList(j + 1, elements.size()))) {
                continue;
              }
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

  static boolean isTerminalUnconstrainedWildcard(
      RegexPattern ei, RegexPattern ej, List<RegexPattern> subsequent) {
    if (unwrapGroup(ej) instanceof RegexPattern.Quantified qj && isUnboundedQuantified(qj)) {
      ImmutableRangeSet<Integer> charsJ = charRangesOf(qj.element());
      if (charsJ.equals(CharRanges.ANY) || charsJ.equals(CharRanges.ANY_CHAR)) {
        ImmutableRangeSet<Integer> charsI = charRangesOf(ei);
        boolean eiIsAny = charsI.equals(CharRanges.ANY) || charsI.equals(CharRanges.ANY_CHAR);
        if (!eiIsAny) {
          return subsequent.stream().noneMatch(RegexPatternUtils::hasAnchorOrConstraint);
        }
      }
    }
    return false;
  }

  static boolean hasAnchorOrConstraint(RegexPattern pattern) {
    return Walker.inTree(RegexPatternUtils::childrenOf)
        .preOrderFrom(pattern)
        .anyMatch(node -> node instanceof RegexPattern.Anchor || node.metadata().minSize() > 0);
  }

  static boolean isReluctantBoundedBy(RegexPattern.Quantified q, List<RegexPattern> subsequent) {
    return q.quantifier().isReluctant()
        && subsequent.stream()
            .map(RegexPatternUtils::unwrapGroup)
            .filter(next -> next.metadata().minSize() > 0)
            .map(RegexPatternUtils::firstCharRangesOf)
            .filter(nextChars -> !nextChars.isEmpty())
            .anyMatch(
                nextChars -> !CharRanges.intersects(firstCharRangesOf(q.element()), nextChars));
  }

  static ImmutableRangeSet<Integer> firstCharRangesOf(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Sequence seq -> {
        ImmutableRangeSet<Integer> res = CharRanges.EMPTY;
        for (RegexPattern elem : seq.elements()) {
          res = CharRanges.union(res, firstCharRangesOf(elem));
          if (elem.metadata().minSize() > 0) {
            break;
          }
        }
        yield res;
      }
      case RegexPattern.Alternation alt -> alt.alternatives().stream()
          .map(RegexPatternUtils::firstCharRangesOf)
          .reduce(CharRanges.EMPTY, CharRanges::union);
      case RegexPattern.Quantified q -> firstCharRangesOf(q.element());
      case RegexPattern.Group group -> firstCharRangesOf(group.content());
      case RegexPattern.CharacterSet cs -> CharRanges.from(cs);
      case RegexPattern.PredefinedCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.PosixCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.Literal lit ->
          lit.value().isEmpty() ? CharRanges.EMPTY : CharRanges.of(lit.value().charAt(0));
      default -> CharRanges.EMPTY;
    };
  }

  static boolean isUnboundedQuantified(RegexPattern pattern) {
    return unwrapGroup(pattern) instanceof RegexPattern.Quantified q
        && !q.quantifier().isPossessive()
        && switch (q.quantifier()) {
          case RegexPattern.AtLeast atLeast -> true;
          case RegexPattern.Limited limited -> limited.max() > 5;
          default -> false;
        };
  }

  static ImmutableRangeSet<Integer> charRangesOf(RegexPattern pattern) {
    return switch (pattern) {
      case RegexPattern.Sequence seq -> seq.elements().stream()
          .map(RegexPatternUtils::charRangesOf)
          .reduce(CharRanges.EMPTY, CharRanges::union);
      case RegexPattern.Alternation alt -> alt.alternatives().stream()
          .map(RegexPatternUtils::charRangesOf)
          .reduce(CharRanges.EMPTY, CharRanges::union);
      case RegexPattern.Quantified q -> charRangesOf(q.element());
      case RegexPattern.Group group -> charRangesOf(group.content());
      case RegexPattern.CharacterSet cs -> CharRanges.from(cs);
      case RegexPattern.PredefinedCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.PosixCharClass pcc -> CharRanges.from(pcc);
      case RegexPattern.Literal lit ->
          lit.value().chars().mapToObj(CharRanges::of).reduce(CharRanges.EMPTY, CharRanges::union);
      default -> CharRanges.EMPTY;
    };
  }

  public static List<RegexPattern.Group> capturingGroupsIn(RegexPattern root) {
    return Walker.inTree(RegexPatternUtils::childrenOf)
        .preOrderFrom(root)
        .filter(
            p -> p instanceof RegexPattern.Group.Capturing || p instanceof RegexPattern.Group.Named)
        .map(RegexPattern.Group.class::cast)
        .toList();
  }

  static boolean referencesGroup(
      RegexPattern.Backreference backref,
      RegexPattern.Group group,
      List<RegexPattern.Group> allGroups) {
    return switch (backref) {
      case RegexPattern.Backreference.Numbered num -> {
        int index = num.groupNumber() - 1;
        yield index >= 0 && index < allGroups.size() && allGroups.get(index).equals(group);
      }
      case RegexPattern.Backreference.Named named ->
          group instanceof RegexPattern.Group.Named namedGroup
              && namedGroup.name().equals(named.groupName());
    };
  }

  private RegexPatternUtils() {}
}
