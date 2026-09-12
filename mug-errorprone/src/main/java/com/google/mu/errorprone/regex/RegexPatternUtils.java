package com.google.mu.errorprone.regex;

import com.google.common.collect.ImmutableRangeSet;
import com.google.common.labs.regex.RegexPattern;
import com.google.mu.util.graph.Walker;
import com.google.mu.util.stream.BiStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Common AST utilities for traversing and inspecting {@link RegexPattern} trees. */
public final class RegexPatternUtils {
  /**
   * The modifier flags in effect at a point in the AST. A {@code (?i:...)} group scopes them to its
   * content; a standalone {@code (?i)} directive applies to the elements after it in its sequence,
   * up to the enclosing group.
   */
  record Flags(boolean caseInsensitive, boolean unicodeCase) {
    static final Flags NONE = new Flags(false, false);

    Flags updated(
        List<RegexPattern.ModifierFlag> enabled, List<RegexPattern.ModifierFlag> disabled) {
      return new Flags(
          updated(caseInsensitive, RegexPattern.ModifierFlag.CASE_INSENSITIVE, enabled, disabled),
          updated(unicodeCase, RegexPattern.ModifierFlag.UNICODE_CASE, enabled, disabled));
    }

    /** Returns {@code ranges} closed under the case folding these flags imply. */
    ImmutableRangeSet<Integer> fold(ImmutableRangeSet<Integer> ranges) {
      return caseInsensitive ? CharRanges.caseFolded(ranges, unicodeCase) : ranges;
    }

    private static boolean updated(
        boolean current,
        RegexPattern.ModifierFlag flag,
        List<RegexPattern.ModifierFlag> enabled,
        List<RegexPattern.ModifierFlag> disabled) {
      return !disabled.contains(flag) && (current || enabled.contains(flag));
    }
  }

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
    return findOverlappingQuantifiers(seq, Flags.NONE);
  }

  static Stream<OverlappingQuantifierPair> findOverlappingQuantifiers(
      RegexPattern.Sequence seq, Flags outer) {
    List<RegexPattern> elements = seq.elements();
    List<Flags> flagsAt = flagsAt(elements, outer);
    for (int i = 0; i < elements.size(); i++) {
      RegexPattern ei = unwrapGroup(elements.get(i));
      if (isUnboundedQuantified(ei)
          && !isReluctantBoundedBy(
              (RegexPattern.Quantified) ei, elements.subList(i + 1, elements.size()),
              flagsAt.get(i))) {
        for (int j = i + 1; j < elements.size(); j++) {
          RegexPattern ej = unwrapGroup(elements.get(j));
          if (ej instanceof RegexPattern.Anchor) {
            break;
          }
          if (isUnboundedQuantified(ej)) {
            if (CharRanges.intersects(
                charRangesOf(elements.get(i), flagsAt.get(i)),
                charRangesOf(elements.get(j), flagsAt.get(j)))) {
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

  /**
   * Returns the flags in effect at each element of a sequence. A standalone {@code (?i)} directive
   * applies to the elements after it, so the flags only change from one index to the next.
   */
  private static List<Flags> flagsAt(List<RegexPattern> elements, Flags outer) {
    List<Flags> result = new ArrayList<>(elements.size());
    Flags current = outer;
    for (RegexPattern element : elements) {
      if (element instanceof RegexPattern.ModifierDirective directive) {
        current =
            current.updated(directive.enabledModifierFlags(), directive.disabledModifierFlags());
      }
      result.add(current);
    }
    return result;
  }

  /** Every sequence under {@code root}, paired with the modifier flags in effect at it. */
  static BiStream<RegexPattern.Sequence, Flags> sequencesIn(RegexPattern root) {
    BiStream.Builder<RegexPattern.Sequence, Flags> builder = BiStream.builder();
    collectSequences(root, Flags.NONE, builder);
    return builder.build();
  }

  private static void collectSequences(
      RegexPattern node, Flags flags, BiStream.Builder<RegexPattern.Sequence, Flags> builder) {
    switch (node) {
      case RegexPattern.Sequence seq -> {
        builder.add(seq, flags);
        List<Flags> flagsAt = flagsAt(seq.elements(), flags);
        for (int i = 0; i < seq.elements().size(); i++) {
          collectSequences(seq.elements().get(i), flagsAt.get(i), builder);
        }
      }
      case RegexPattern.Group.NonCapturing g -> collectSequences(
          g.content(), flags.updated(g.enabledModifierFlags(), g.disabledModifierFlags()), builder);
      default -> childrenOf(node).forEach(child -> collectSequences(child, flags, builder));
    }
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
    return isReluctantBoundedBy(q, subsequent, Flags.NONE);
  }

  static boolean isReluctantBoundedBy(
      RegexPattern.Quantified q, List<RegexPattern> subsequent, Flags flags) {
    return q.quantifier().isReluctant()
        && subsequent.stream()
            .map(RegexPatternUtils::unwrapGroup)
            .filter(next -> next.metadata().minSize() > 0)
            .map(next -> firstCharRangesOf(next, flags))
            .filter(nextChars -> !nextChars.isEmpty())
            .anyMatch(nextChars ->
                !CharRanges.intersects(firstCharRangesOf(q.element(), flags), nextChars));
  }

  static ImmutableRangeSet<Integer> firstCharRangesOf(RegexPattern pattern) {
    return firstCharRangesOf(pattern, Flags.NONE);
  }

  static ImmutableRangeSet<Integer> firstCharRangesOf(RegexPattern pattern, Flags flags) {
    return switch (pattern) {
      case RegexPattern.Sequence seq -> {
        ImmutableRangeSet<Integer> res = CharRanges.EMPTY;
        List<Flags> flagsAt = flagsAt(seq.elements(), flags);
        for (int i = 0; i < seq.elements().size(); i++) {
          RegexPattern elem = seq.elements().get(i);
          res = CharRanges.union(res, firstCharRangesOf(elem, flagsAt.get(i)));
          if (elem.metadata().minSize() > 0) {
            break;
          }
        }
        yield res;
      }
      case RegexPattern.Alternation alt -> alt.alternatives().stream()
          .map(alternative -> firstCharRangesOf(alternative, flags))
          .reduce(CharRanges.EMPTY, CharRanges::union);
      case RegexPattern.Quantified q -> firstCharRangesOf(q.element(), flags);
      case RegexPattern.Group.NonCapturing g -> firstCharRangesOf(
          g.content(), flags.updated(g.enabledModifierFlags(), g.disabledModifierFlags()));
      case RegexPattern.Group group -> firstCharRangesOf(group.content(), flags);
      case RegexPattern.CharSetElement cse -> flags.fold(CharRanges.from(cse));
      case RegexPattern.Literal lit ->
          lit.value().isEmpty()
              ? CharRanges.EMPTY
              : flags.fold(CharRanges.of(lit.value().codePointAt(0)));
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
    return charRangesOf(pattern, Flags.NONE);
  }

  static ImmutableRangeSet<Integer> charRangesOf(RegexPattern pattern, Flags flags) {
    return switch (pattern) {
      case RegexPattern.Sequence seq -> {
        ImmutableRangeSet<Integer> res = CharRanges.EMPTY;
        List<Flags> flagsAt = flagsAt(seq.elements(), flags);
        for (int i = 0; i < seq.elements().size(); i++) {
          res = CharRanges.union(res, charRangesOf(seq.elements().get(i), flagsAt.get(i)));
        }
        yield res;
      }
      case RegexPattern.Alternation alt -> alt.alternatives().stream()
          .map(alternative -> charRangesOf(alternative, flags))
          .reduce(CharRanges.EMPTY, CharRanges::union);
      case RegexPattern.Quantified q -> charRangesOf(q.element(), flags);
      case RegexPattern.Group.NonCapturing g -> charRangesOf(
          g.content(), flags.updated(g.enabledModifierFlags(), g.disabledModifierFlags()));
      case RegexPattern.Group group -> charRangesOf(group.content(), flags);
      case RegexPattern.CharSetElement cse -> flags.fold(CharRanges.from(cse));
      case RegexPattern.Literal lit -> flags.fold(
          lit.value()
              .codePoints()
              .mapToObj(CharRanges::of)
              .reduce(CharRanges.EMPTY, CharRanges::union));
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
