package com.google.common.labs.parse;


import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PrefixPruneTreeTest {

  @Test
  public void emptyTree() {
    PrefixPruneTree<String> tree = new PrefixPruneTree.Builder<String>().build();
    assertThat(tree.pruneByPrefix(CharInput.from(""), 0)).isEmpty();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0)).isEmpty();
  }

  @Test
  public void treeWithOnlyEmptyPrefix() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>().addPrefix("", "default").build();
    assertThat(tree.pruneByPrefix(CharInput.from(""), 0)).containsExactly("default");
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0)).containsExactly("default");
    assertThat(tree.pruneByPrefix(CharInput.from("abc"), 0)).containsExactly("default");
  }

  @Test
  public void multipleCandidatesForSamePrefix() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("", "default")
            .addPrefix("a", "candidate1")
            .addPrefix("a", "candidate2")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0))
        .containsExactly("default", "candidate1", "candidate2");
    assertThat(tree.pruneByPrefix(CharInput.from("b"), 0)).containsExactly("default");
  }

  @Test
  public void disjunctPrefixes() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("a", "candidateA")
            .addPrefix("b", "candidateB")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0)).containsExactly("candidateA");
    assertThat(tree.pruneByPrefix(CharInput.from("b"), 0)).containsExactly("candidateB");
    assertThat(tree.pruneByPrefix(CharInput.from("c"), 0)).isEmpty();
  }

  @Test
  public void overlappingPrefixes() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("", "default")
            .addPrefix("a", "candidateA")
            .addPrefix("an", "candidateAn")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0))
        .containsExactly("default", "candidateA")
        .inOrder();
    assertThat(tree.pruneByPrefix(CharInput.from("an"), 0))
        .containsExactly("default", "candidateA", "candidateAn")
        .inOrder();
    assertThat(tree.pruneByPrefix(CharInput.from("b"), 0)).containsExactly("default");
  }

  @Test
  public void overlappingPrefixes_longer() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("there", "candidateThere")
            .addPrefix("the", "candidateThe")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("the"), 0)).containsExactly("candidateThe");
    assertThat(tree.pruneByPrefix(CharInput.from("there"), 0))
        .containsExactly("candidateThere", "candidateThe")
        .inOrder();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0)).isEmpty();
  }

  @Test
  public void nonAsciiInPrefix_ignored() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>().addPrefix("a" + (char) 128, "candidate").build();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0)).containsExactly("candidate");
    assertThat(tree.pruneByPrefix(CharInput.from("a" + (char) 128), 0))
        .containsExactly("candidate");
  }

  @Test
  public void firstCharIsNonAsciiInPrefix_sameAsEmptyPrefix() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>().addPrefix((char) 128 + "a", "candidate").build();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 0)).containsExactly("candidate");
    assertThat(tree.pruneByPrefix(CharInput.from(""), 0)).containsExactly("candidate");
  }

  @Test
  public void pruneByPrefix_emptyInput() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("", "default")
            .addPrefix("a", "candidateA")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from(""), 0)).containsExactly("default");
  }

  @Test
  public void pruneByPrefix_indexAtEnd() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("", "default")
            .addPrefix("a", "candidateA")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("a"), 1)).containsExactly("default");
  }

  @Test
  public void pruneByPrefix_respectsIndex() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("", "default")
            .addPrefix("a", "candidateA")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("ba"), 1))
        .containsExactly("default", "candidateA")
        .inOrder();
  }

  @Test
  public void singlePrefixPath() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>().addPrefix("abc", "val").build();
    assertThat(tree.pruneByPrefix(CharInput.from("abc"), 0)).containsExactly("val");
    assertThat(tree.pruneByPrefix(CharInput.from("ab"), 0)).isEmpty();
    assertThat(tree.pruneByPrefix(CharInput.from("abcd"), 0)).containsExactly("val");
  }

  @Test
  public void twoSubPaths() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("ab1", "val1")
            .addPrefix("ac1", "val2")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("ab1"), 0)).containsExactly("val1");
    assertThat(tree.pruneByPrefix(CharInput.from("ac1"), 0)).containsExactly("val2");
    assertThat(tree.pruneByPrefix(CharInput.from("ad1"), 0)).isEmpty();
  }

  @Test
  public void threeSubPaths() {
    PrefixPruneTree<String> tree =
        new PrefixPruneTree.Builder<String>()
            .addPrefix("ab1", "val1")
            .addPrefix("ac1", "val2")
            .addPrefix("ad1", "val3")
            .build();
    assertThat(tree.pruneByPrefix(CharInput.from("ab1"), 0)).containsExactly("val1");
    assertThat(tree.pruneByPrefix(CharInput.from("ac1"), 0)).containsExactly("val2");
    assertThat(tree.pruneByPrefix(CharInput.from("ad1"), 0)).containsExactly("val3");
    assertThat(tree.pruneByPrefix(CharInput.from("ae1"), 0)).isEmpty();
  }

  @Test
  public void manySubPaths() {
    PrefixPruneTree.Builder<String> builder = new PrefixPruneTree.Builder<>();
    for (int i = 0; i < 50; i++) {
      builder.addPrefix("a" + (char) (' ' + i) + "1", "val" + i);
    }
    PrefixPruneTree<String> tree = builder.build();
    for (int i = 0; i < 50; i++) {
      assertThat(tree.pruneByPrefix(CharInput.from("a" + (char) (' ' + i) + "1"), 0))
          .containsExactly("val" + i);
    }
    assertThat(tree.pruneByPrefix(CharInput.from("aZ1"), 0)).isEmpty();
  }
}
