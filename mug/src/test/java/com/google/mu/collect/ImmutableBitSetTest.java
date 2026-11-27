/*****************************************************************************
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
package com.google.mu.collect;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import com.google.common.testing.NullPointerTester;
import java.util.BitSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.Spliterator;
import java.util.function.IntPredicate;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ImmutableBitSetTest {

  @Test public void builder_setNegativeIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(-1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_setIntegerMinValue_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_setSameIndexTwice_cardinalityIsOne() {
    ImmutableBitSet set = ImmutableBitSet.builder().set(5).set(5).build();
    assertThat(set.cardinality()).isEqualTo(1);
  }

  @Test public void builder_setAfterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder().set(1);
    ImmutableBitSet first = builder.build();
    builder.set(2);
    assertThat(indicesBelow(200, first::get)).containsExactly(1);
  }

  @Test public void builder_buildAgain_includesBitsFromBothRounds() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder().set(1);
    builder.build();
    ImmutableBitSet second = builder.set(2).build();
    assertThat(indicesBelow(200, second::get)).containsExactly(1, 2).inOrder();
  }

  @Test public void builder_setAfterBuild_bitAbove63_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder().set(127);
    ImmutableBitSet first = builder.build();
    builder.set(5);
    assertThat(indicesBelow(200, first::get)).containsExactly(127);
  }

  @Test public void builder_buildAgain_bitAbove63_includesBitsFromBothRounds() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder().set(127);
    builder.build();
    ImmutableBitSet second = builder.set(5).build();
    assertThat(indicesBelow(200, second::get)).containsExactly(5, 127).inOrder();
  }

  @Test public void builder_onlyBitIntegerMaxValue_isSet() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    assertThat(bitSetOf(Integer.MAX_VALUE).stream()).containsExactly(Integer.MAX_VALUE);
  }

  @Test public void builder_setIntegerMaxValue_preservesAllBits() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    assertThat(bitSetOf(0, 100, Integer.MAX_VALUE).stream())
        .containsExactly(0, 100, Integer.MAX_VALUE)
        .inOrder();
  }

  @Test public void builder_setValueTrue_setsBit() {
    assertThat(ImmutableBitSet.builder().set(5, true).build().stream()).containsExactly(5);
  }

  @Test public void builder_setValueFalse_clearsSetBit() {
    assertThat(builderOf(1, 5, 100).set(5, false).build().stream())
        .containsExactly(1, 100)
        .inOrder();
  }

  @Test public void builder_setValueFalse_onUnsetBit_noEffect() {
    assertThat(builderOf(1).set(5, false).build().stream()).containsExactly(1);
  }

  @Test public void builder_setValueFalse_clearsLastBit_returnsEmpty() {
    assertThat(builderOf(5).set(5, false).build()).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void builder_setValueTrue_negativeIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(-1, true));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_setValueFalse_negativeIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(-1, false));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_setValueTrue_integerMinValue_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(Integer.MIN_VALUE, true));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_setValueFalse_integerMinValue_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(Integer.MIN_VALUE, false));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_setRange_emptyRange_returnsEmpty() {
    assertThat(ImmutableBitSet.builder().set(5, 5).build()).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void builder_setRange_emptyRangeAtIntegerMaxValue_returnsEmpty() {
    assertThat(ImmutableBitSet.builder().set(Integer.MAX_VALUE, Integer.MAX_VALUE).build())
        .isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void builder_setRange_singleBit() {
    assertThat(ImmutableBitSet.builder().set(5, 6).build().stream()).containsExactly(5);
  }

  @Test public void builder_setRange_withinSingleWord() {
    assertThat(ImmutableBitSet.builder().set(1, 5).build().stream())
        .containsExactly(1, 2, 3, 4)
        .inOrder();
  }

  @Test public void builder_setRange_fullSingleWord_allBitsBelow64() {
    assertThat(ImmutableBitSet.builder().set(0, 64).build().stream())
        .containsExactlyElementsIn(IntStream.range(0, 64).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void builder_setRange_acrossWordBoundary() {
    assertThat(ImmutableBitSet.builder().set(62, 66).build().stream())
        .containsExactly(62, 63, 64, 65)
        .inOrder();
  }

  @Test public void builder_setRange_acrossMultipleWords() {
    assertThat(ImmutableBitSet.builder().set(1, 200).build().stream())
        .containsExactlyElementsIn(IntStream.range(1, 200).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void builder_setRange_negativeFromIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(-1, 5));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_setRange_negativeToIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(0, -1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_setRange_fromGreaterThanTo_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(5, 4));
    assertThat(thrown).hasMessageThat().contains("5");
  }

  @Test public void builder_setRange_integerMinValueFromIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(Integer.MIN_VALUE, 0));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_setRange_integerMinValueToIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.set(0, Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_setRange_afterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder().set(1);
    ImmutableBitSet first = builder.build();
    builder.set(2, 100);
    assertThat(first.stream()).containsExactly(1);
  }

  @Test public void builder_clearNegativeIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.clear(-1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_clearIntegerMinValue_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.clear(Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_clearSetBit_removesBit() {
    ImmutableBitSet set = builderOf(1, 3, 100).clear(3).build();
    assertThat(set.stream()).containsExactly(1, 100).inOrder();
  }

  @Test public void builder_clearUnsetBit_noEffect() {
    ImmutableBitSet set = builderOf(1, 3).clear(2).build();
    assertThat(set.stream()).containsExactly(1, 3).inOrder();
  }

  @Test public void builder_clearBitBeyondHighest_noEffect() {
    ImmutableBitSet set = builderOf(1).clear(Integer.MAX_VALUE).build();
    assertThat(set.stream()).containsExactly(1);
  }

  @Test public void builder_clearHighBit_retainsLowBit() {
    assertThat(builderOf(1, 200).clear(200).build().stream()).containsExactly(1);
  }

  @Test public void builder_clearLastBit_returnsEmpty() {
    assertThat(builderOf(5).clear(5).build()).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void builder_clearAfterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3);
    ImmutableBitSet first = builder.build();
    builder.clear(1);
    assertThat(first.stream()).containsExactly(1, 3).inOrder();
  }

  @Test public void builder_clearRange_emptyRange_noEffect() {
    assertThat(builderOf(1, 5).clear(5, 5).build().stream()).containsExactly(1, 5).inOrder();
  }

  @Test public void builder_clearRange_emptyRangeAtIntegerMaxValue_noEffect() {
    assertThat(builderOf(1).clear(Integer.MAX_VALUE, Integer.MAX_VALUE).build().stream())
        .containsExactly(1);
  }

  @Test public void builder_clearRange_singleBit() {
    assertThat(builderOf(1, 5, 100).clear(5, 6).build().stream()).containsExactly(1, 100).inOrder();
  }

  @Test public void builder_clearRange_withinSingleWord() {
    assertThat(ImmutableBitSet.builder().set(0, 10).clear(2, 7).build().stream())
        .containsExactly(0, 1, 7, 8, 9)
        .inOrder();
  }

  @Test public void builder_clearRange_acrossWordBoundary() {
    assertThat(ImmutableBitSet.builder().set(60, 68).clear(62, 66).build().stream())
        .containsExactly(60, 61, 66, 67)
        .inOrder();
  }

  @Test public void builder_clearRange_acrossMultipleWords() {
    assertThat(ImmutableBitSet.builder().set(0, 200).clear(1, 199).build().stream())
        .containsExactly(0, 199)
        .inOrder();
  }

  @Test public void builder_clearRange_highBits_retainsLowBits() {
    assertThat(ImmutableBitSet.builder().set(0, 200).clear(64, 200).build())
        .isEqualTo(ImmutableBitSet.valueOf(new long[] {-1L}));
  }

  @Test public void builder_clearRange_toIntegerMaxValue_clearsHighBits() {
    assertThat(builderOf(1, 200).clear(64, Integer.MAX_VALUE).build().stream()).containsExactly(1);
  }

  @Test public void builder_clearRange_allBits_returnsEmpty() {
    assertThat(ImmutableBitSet.builder().set(0, 200).clear(0, 200).build())
        .isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void builder_clearRange_beyondHighestBit_noEffect() {
    assertThat(builderOf(1).clear(100, Integer.MAX_VALUE).build().stream()).containsExactly(1);
  }

  @Test public void builder_clearRange_negativeFromIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.clear(-1, 5));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_clearRange_negativeToIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.clear(0, -1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builder_clearRange_fromGreaterThanTo_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.clear(5, 4));
    assertThat(thrown).hasMessageThat().contains("5");
  }

  @Test public void builder_clearRange_integerMinValueFromIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.clear(Integer.MIN_VALUE, 0));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_clearRange_integerMinValueToIndex_throws() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> builder.clear(0, Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builder_clearRange_afterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder().set(0, 100);
    ImmutableBitSet first = builder.build();
    builder.clear(0, 50);
    assertThat(first.stream())
        .containsExactlyElementsIn(IntStream.range(0, 100).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void builder_isEmpty_newBuilder_isTrue() {
    assertThat(ImmutableBitSet.builder().isEmpty()).isTrue();
  }

  @Test public void builder_isEmpty_withExpectedSize_isTrue() {
    assertThat(ImmutableBitSet.builderWithExpectedSize(1024).isEmpty()).isTrue();
  }

  @Test public void builder_isEmpty_afterSet_isFalse() {
    assertThat(ImmutableBitSet.builder().set(0).isEmpty()).isFalse();
  }

  @Test public void builder_isEmpty_afterSetHighBit_isFalse() {
    assertThat(ImmutableBitSet.builder().set(100).isEmpty()).isFalse();
  }

  @Test public void builder_isEmpty_afterClearLastBit_isTrue() {
    assertThat(builderOf(5).clear(5).isEmpty()).isTrue();
  }

  @Test public void builder_isEmpty_afterAndNotAllBits_isTrue() {
    assertThat(builderOf(1, 100).andNot(bitSetOf(1, 100)).isEmpty()).isTrue();
  }

  @Test public void builder_isEmpty_fromEmptyToBuilder_isTrue() {
    assertThat(ImmutableBitSet.EMPTY.toBuilder().isEmpty()).isTrue();
  }

  @Test public void builder_isEmpty_fromNonEmptyToBuilder_isFalse() {
    assertThat(bitSetOf(1, 100).toBuilder().isEmpty()).isFalse();
  }

  @Test public void builderWithExpectedSize_negative_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class, () -> ImmutableBitSet.builderWithExpectedSize(-1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void builderWithExpectedSize_integerMinValue_throws() {
    IllegalArgumentException thrown = assertThrows(
        IllegalArgumentException.class,
        () -> ImmutableBitSet.builderWithExpectedSize(Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void builderWithExpectedSize_zero_buildsEmptySet() {
    assertThat(ImmutableBitSet.builderWithExpectedSize(0).build().stream()).isEmpty();
  }

  @Test public void builderWithExpectedSize_bitBeyondExpectedSize_isSet() {
    ImmutableBitSet set = ImmutableBitSet.builderWithExpectedSize(64).set(1000).build();
    assertThat(indicesBelow(1100, set::get)).containsExactly(1000);
  }

  @Test public void builderWithExpectedSize_1024_onlyBit3_buildsBit3() {
    assertThat(ImmutableBitSet.builderWithExpectedSize(1024).set(3).build().stream())
        .containsExactly(3);
  }

  @Test public void builderWithExpectedSize_1024_onlyBit100_buildsBit100() {
    assertThat(ImmutableBitSet.builderWithExpectedSize(1024).set(100).build().stream())
        .containsExactly(100);
  }

  @Test public void builderAnd_multiWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.and(bitSetOf(3, 4, 100, 150));
    assertThat(builder.build().stream()).containsExactly(3, 100).inOrder();
  }

  @Test public void builderAnd_singleWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.and(bitSetOf(1, 2, 3));
    assertThat(builder.build().stream()).containsExactly(1, 3).inOrder();
  }

  @Test public void builderAnd_emptySet_clearsAllBits() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.and(bitSetOf());
    assertThat(builder.build().stream()).isEmpty();
  }

  @Test public void builderAnd_nullImmutableBitSet_throws() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    assertThrows(NullPointerException.class, () -> builder.and((ImmutableBitSet) null));
  }

  @Test public void builderAnd_afterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    ImmutableBitSet first = builder.build();
    builder.and(bitSetOf(3));
    assertThat(first.stream()).containsExactly(1, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderAnd_argumentUnchanged() {
    ImmutableBitSet that = bitSetOf(3, 4, 100, 150);
    builderOf(1, 3, 64, 100, 200).and(that);
    assertThat(that.stream()).containsExactly(3, 4, 100, 150).inOrder();
  }

  @Test public void builderOr_multiWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.or(bitSetOf(3, 4, 100, 150));
    assertThat(builder.build().stream()).containsExactly(1, 3, 4, 64, 100, 150, 200).inOrder();
  }

  @Test public void builderOr_singleWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.or(bitSetOf(1, 2, 3));
    assertThat(builder.build().stream()).containsExactly(1, 2, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderOr_emptySet_bitsUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.or(bitSetOf());
    assertThat(builder.build().stream()).containsExactly(1, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderOr_nullImmutableBitSet_throws() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    assertThrows(NullPointerException.class, () -> builder.or((ImmutableBitSet) null));
  }

  @Test public void builderOr_afterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    ImmutableBitSet first = builder.build();
    builder.or(bitSetOf(5, 300));
    assertThat(first.stream()).containsExactly(1, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderOr_argumentUnchanged() {
    ImmutableBitSet that = bitSetOf(3, 4, 100, 150);
    builderOf(1, 3, 64, 100, 200).or(that);
    assertThat(that.stream()).containsExactly(3, 4, 100, 150).inOrder();
  }

  @Test public void builderXor_multiWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.xor(bitSetOf(3, 4, 100, 150));
    assertThat(builder.build().stream()).containsExactly(1, 4, 64, 150, 200).inOrder();
  }

  @Test public void builderXor_singleWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.xor(bitSetOf(1, 2, 3));
    assertThat(builder.build().stream()).containsExactly(2, 64, 100, 200).inOrder();
  }

  @Test public void builderXor_emptySet_bitsUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.xor(bitSetOf());
    assertThat(builder.build().stream()).containsExactly(1, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderXor_nullImmutableBitSet_throws() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    assertThrows(NullPointerException.class, () -> builder.xor((ImmutableBitSet) null));
  }

  @Test public void builderXor_clearsHighBits_retainsLowBits() {
    assertThat(builderOf(1, 200).xor(bitSetOf(200)).build().stream()).containsExactly(1);
  }

  @Test public void builderXor_afterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    ImmutableBitSet first = builder.build();
    builder.xor(bitSetOf(1, 64));
    assertThat(first.stream()).containsExactly(1, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderXor_argumentUnchanged() {
    ImmutableBitSet that = bitSetOf(3, 4, 100, 150);
    builderOf(1, 3, 64, 100, 200).xor(that);
    assertThat(that.stream()).containsExactly(3, 4, 100, 150).inOrder();
  }

  @Test public void builderAndNot_multiWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.andNot(bitSetOf(3, 4, 100, 150));
    assertThat(builder.build().stream()).containsExactly(1, 64, 200).inOrder();
  }

  @Test public void builderAndNot_singleWordSet() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.andNot(bitSetOf(1, 2, 3));
    assertThat(builder.build().stream()).containsExactly(64, 100, 200).inOrder();
  }

  @Test public void builderAndNot_emptySet_bitsUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    builder.andNot(bitSetOf());
    assertThat(builder.build().stream()).containsExactly(1, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderAndNot_nullImmutableBitSet_throws() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    assertThrows(NullPointerException.class, () -> builder.andNot((ImmutableBitSet) null));
  }

  @Test public void builderAndNot_clearsHighBits_retainsLowBits() {
    assertThat(builderOf(1, 200).andNot(bitSetOf(200)).build().stream()).containsExactly(1);
  }

  @Test public void builderAndNot_afterBuild_builtSetUnchanged() {
    ImmutableBitSet.Builder builder = builderOf(1, 3, 64, 100, 200);
    ImmutableBitSet first = builder.build();
    builder.andNot(bitSetOf(1, 64));
    assertThat(first.stream()).containsExactly(1, 3, 64, 100, 200).inOrder();
  }

  @Test public void builderAndNot_argumentUnchanged() {
    ImmutableBitSet that = bitSetOf(3, 4, 100, 150);
    builderOf(1, 3, 64, 100, 200).andNot(that);
    assertThat(that.stream()).containsExactly(3, 4, 100, 150).inOrder();
  }

  @Test public void toBuilder_emptySet_buildsEmptySet() {
    assertThat(bitSetOf().toBuilder().build().stream()).isEmpty();
  }

  @Test public void toBuilder_singleWordSet_buildsSameBits() {
    assertThat(bitSetOf(1, 3).toBuilder().build().stream()).containsExactly(1, 3).inOrder();
  }

  @Test public void toBuilder_multiWordSet_buildsSameBits() {
    assertThat(bitSetOf(1, 100, 200).toBuilder().build().stream())
        .containsExactly(1, 100, 200)
        .inOrder();
  }

  @Test public void toBuilder_valueOfWords_buildsSameBits() {
    assertThat(ImmutableBitSet.valueOf(new long[] {1L, 1L}).toBuilder().build().stream())
        .containsExactly(0, 64)
        .inOrder();
  }

  @Test public void toBuilder_setBit_originalUnchanged() {
    ImmutableBitSet set = bitSetOf(1, 100);
    set.toBuilder().set(200);
    assertThat(set.stream()).containsExactly(1, 100).inOrder();
  }

  @Test public void toBuilder_clearBit_originalUnchanged() {
    ImmutableBitSet set = bitSetOf(1, 100);
    set.toBuilder().clear(100);
    assertThat(set.stream()).containsExactly(1, 100).inOrder();
  }

  @Test public void toBuilder_clearAllBits_originalUnchanged() {
    ImmutableBitSet set = bitSetOf(1, 100);
    set.toBuilder().andNot(set);
    assertThat(set.stream()).containsExactly(1, 100).inOrder();
  }

  @Test public void toBuilder_valueOfWords_clearAllBits_originalUnchanged() {
    ImmutableBitSet set = ImmutableBitSet.valueOf(new long[] {1L, 1L});
    set.toBuilder().andNot(set);
    assertThat(set.stream()).containsExactly(0, 64).inOrder();
  }

  @Test public void toBuilder_bitInTopWord_buildsSameBits() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    ImmutableBitSet set = bitSetOf(Integer.MAX_VALUE - 63);
    assertThat(set.toBuilder().build().stream()).containsExactly(Integer.MAX_VALUE - 63);
  }

  @Test public void toBuilder_bitIntegerMaxValue_buildsSameBits() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    ImmutableBitSet set = bitSetOf(Integer.MAX_VALUE);
    assertThat(set.toBuilder().build().stream()).containsExactly(Integer.MAX_VALUE);
  }

  @Test public void empty_isEmpty() {
    assertThat(ImmutableBitSet.EMPTY.isEmpty()).isTrue();
  }

  @Test public void empty_stream_isEmpty() {
    assertThat(ImmutableBitSet.EMPTY.stream()).isEmpty();
  }

  @Test public void empty_cardinality_isZero() {
    assertThat(ImmutableBitSet.EMPTY.cardinality()).isEqualTo(0);
  }

  @Test public void empty_length_isZero() {
    assertThat(ImmutableBitSet.EMPTY.length()).isEqualTo(0);
  }

  @Test public void empty_getIndex0_returnsFalse() {
    assertThat(ImmutableBitSet.EMPTY.get(0)).isFalse();
  }

  @Test public void empty_getIntegerMaxValue_returnsFalse() {
    assertThat(ImmutableBitSet.EMPTY.get(Integer.MAX_VALUE)).isFalse();
  }

  @Test public void empty_getNegativeIndex_throws() {
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> ImmutableBitSet.EMPTY.get(-1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void empty_toLongArray_isEmpty() {
    assertThat(ImmutableBitSet.EMPTY.toLongArray()).isEmpty();
  }

  @Test public void empty_toByteArray_isEmpty() {
    assertThat(ImmutableBitSet.EMPTY.toByteArray()).isEmpty();
  }

  @Test public void empty_toString() {
    assertThat(ImmutableBitSet.EMPTY.toString()).isEqualTo("{}");
  }

  @Test public void empty_intersectsNonEmptySet_isFalse() {
    assertThat(ImmutableBitSet.EMPTY.intersects(bitSetOf(1, 100))).isFalse();
  }

  @Test public void empty_intersectsNull_throws() {
    ImmutableBitSet that = null;
    assertThrows(NullPointerException.class, () -> ImmutableBitSet.EMPTY.intersects(that));
  }

  @Test public void empty_toBuilder_setBit_buildsThatBit() {
    assertThat(ImmutableBitSet.EMPTY.toBuilder().set(5).build().stream()).containsExactly(5);
  }

  @Test public void empty_toBuilder_setBit_emptyUnchanged() {
    ImmutableBitSet.EMPTY.toBuilder().set(5);
    assertThat(ImmutableBitSet.EMPTY.stream()).isEmpty();
  }

  @Test public void valueOf_emptyArray_returnsEmpty() {
    assertThat(ImmutableBitSet.valueOf(new long[0])).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void valueOf_singleZeroWord_returnsEmpty() {
    assertThat(ImmutableBitSet.valueOf(new long[] {0L})).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void valueOf_allZeroWords_returnsEmpty() {
    assertThat(ImmutableBitSet.valueOf(new long[] {0L, 0L}))
        .isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void build_noBits_returnsEmpty() {
    assertThat(ImmutableBitSet.builder().build()).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void build_allBitsCleared_returnsEmpty() {
    assertThat(builderOf(1, 100).andNot(bitSetOf(1, 100)).build())
        .isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void valueOf_emptyArray_isEmpty() {
    assertThat(ImmutableBitSet.valueOf(new long[0]).stream()).isEmpty();
  }

  @Test public void valueOf_zero_isEmpty() {
    assertThat(ImmutableBitSet.valueOf(new long[] {0L}).stream()).isEmpty();
  }

  @Test public void valueOf_nullLongArray_throws() {
    assertThrows(NullPointerException.class, () -> ImmutableBitSet.valueOf((long[]) null));
  }

  @Test public void valueOf_bit0() {
    assertThat(ImmutableBitSet.valueOf(new long[] {1L}).stream()).containsExactly(0);
  }

  @Test public void valueOf_bit63() {
    assertThat(ImmutableBitSet.valueOf(new long[] {Long.MIN_VALUE}).stream()).containsExactly(63);
  }

  @Test public void valueOf_minusOne_allBitsBelow64() {
    assertThat(ImmutableBitSet.valueOf(new long[] {-1L}).stream())
        .containsExactlyElementsIn(IntStream.range(0, 64).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void valueOf_matchesBitSetValueOf_randomWord() {
    long[] words = {new Random(42).nextLong()};
    assertThat(ImmutableBitSet.valueOf(words).stream())
        .containsExactlyElementsIn(BitSet.valueOf(words).stream().boxed().collect(toList()))
        .inOrder();
  }

  @Test public void valueOf_oneWord() {
    assertThat(ImmutableBitSet.valueOf(new long[] {0b101L}).stream())
        .containsExactly(0, 2)
        .inOrder();
  }

  @Test public void valueOf_twoWords() {
    assertThat(ImmutableBitSet.valueOf(new long[] {1L, 1L}).stream())
        .containsExactly(0, 64)
        .inOrder();
  }

  @Test public void valueOf_fourWords() {
    assertThat(ImmutableBitSet.valueOf(new long[] {1L, 0L, 0L, Long.MIN_VALUE}).stream())
        .containsExactly(0, 255)
        .inOrder();
  }

  @Test public void valueOf_leadingZeroWords() {
    assertThat(ImmutableBitSet.valueOf(new long[] {0L, 0L, 1L}).stream()).containsExactly(128);
  }

  @Test public void valueOf_trailingZeroWords() {
    assertThat(ImmutableBitSet.valueOf(new long[] {1L, 0L, 0L}).stream()).containsExactly(0);
  }

  @Test public void valueOf_arrayModifiedAfterwards_setUnchanged() {
    long[] words = {1L, 1L};
    ImmutableBitSet set = ImmutableBitSet.valueOf(words);
    words[0] = 0L;
    assertThat(set.stream()).containsExactly(0, 64).inOrder();
  }

  @Test public void valueOf_matchesBitSetValueOf_randomWords() {
    long[] words = new Random(42).longs(4).toArray();
    assertThat(ImmutableBitSet.valueOf(words).stream())
        .containsExactlyElementsIn(BitSet.valueOf(words).stream().boxed().collect(toList()))
        .inOrder();
  }

  @Test public void valueOf_trailingZeroWordPastIntegerMaxValue_succeeds() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    long[] words = new long[Integer.MAX_VALUE / 64 + 2];
    words[0] = 1L;
    assertThat(ImmutableBitSet.valueOf(words).stream()).containsExactly(0);
  }

  @Test public void valueOf_bitIntegerMaxValue_isSet() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    long[] words = new long[Integer.MAX_VALUE / 64 + 1];
    words[Integer.MAX_VALUE / 64] = Long.MIN_VALUE;
    assertThat(ImmutableBitSet.valueOf(words).stream()).containsExactly(Integer.MAX_VALUE);
  }

  @Test public void valueOfBytes_emptyArray_returnsEmpty() {
    assertThat(ImmutableBitSet.valueOf(new byte[0])).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void valueOfBytes_singleZeroByte_returnsEmpty() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {0})).isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void valueOfBytes_allZeroBytes_returnsEmpty() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0}))
        .isSameInstanceAs(ImmutableBitSet.EMPTY);
  }

  @Test public void valueOfBytes_nullArray_throws() {
    assertThrows(NullPointerException.class, () -> ImmutableBitSet.valueOf((byte[]) null));
  }

  @Test public void valueOfBytes_bit0() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {1}).stream()).containsExactly(0);
  }

  @Test public void valueOfBytes_bit7_signedNegativeByte() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {(byte) 0x80}).stream()).containsExactly(7);
  }

  @Test public void valueOfBytes_bit8() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {0, 1}).stream()).containsExactly(8);
  }

  @Test public void valueOfBytes_bit63() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {0, 0, 0, 0, 0, 0, 0, (byte) 0x80}).stream())
        .containsExactly(63);
  }

  @Test public void valueOfBytes_eightMinusOneBytes_allBitsBelow64() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {-1, -1, -1, -1, -1, -1, -1, -1}).stream())
        .containsExactlyElementsIn(IntStream.range(0, 64).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void valueOfBytes_nineBytes_bit64() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 1}).stream())
        .containsExactly(64);
  }

  @Test public void valueOfBytes_multipleBytes() {
    assertThat(
            ImmutableBitSet.valueOf(new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 1, (byte) 0x80}).stream())
        .containsExactly(0, 64, 79)
        .inOrder();
  }

  @Test public void valueOfBytes_trailingZeroBytes_beyondEightBytes() {
    assertThat(ImmutableBitSet.valueOf(new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 0, 0}).stream())
        .containsExactly(0);
  }

  @Test public void valueOfBytes_arrayModifiedAfterwards_singleWordUnchanged() {
    byte[] bytes = {1, 2};
    ImmutableBitSet set = ImmutableBitSet.valueOf(bytes);
    bytes[0] = 0;
    assertThat(set.stream()).containsExactly(0, 9).inOrder();
  }

  @Test public void valueOfBytes_arrayModifiedAfterwards_multiWordUnchanged() {
    byte[] bytes = {1, 0, 0, 0, 0, 0, 0, 0, 1};
    ImmutableBitSet set = ImmutableBitSet.valueOf(bytes);
    bytes[0] = 0;
    assertThat(set.stream()).containsExactly(0, 64).inOrder();
  }

  @Test public void valueOfBytes_matchesBitSetValueOf_randomSingleWordBytes() {
    byte[] bytes = new byte[7];
    new Random(42).nextBytes(bytes);
    assertThat(ImmutableBitSet.valueOf(bytes).stream())
        .containsExactlyElementsIn(BitSet.valueOf(bytes).stream().boxed().collect(toList()))
        .inOrder();
  }

  @Test public void valueOfBytes_matchesBitSetValueOf_randomMultiWordBytes() {
    byte[] bytes = new byte[25];
    new Random(42).nextBytes(bytes);
    assertThat(ImmutableBitSet.valueOf(bytes).stream())
        .containsExactlyElementsIn(BitSet.valueOf(bytes).stream().boxed().collect(toList()))
        .inOrder();
  }

  @Test public void valueOfBytes_trailingZeroBytePastIntegerMaxValue_succeeds() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    byte[] bytes = new byte[Integer.MAX_VALUE / 8 + 2];
    bytes[0] = 1;
    assertThat(ImmutableBitSet.valueOf(bytes).stream()).containsExactly(0);
  }

  @Test public void valueOfBytes_bitIntegerMaxValue_isSet() {
    assumeTrue(Runtime.getRuntime().maxMemory() >= 1024L * 1024 * 1024);
    byte[] bytes = new byte[Integer.MAX_VALUE / 8 + 1];
    bytes[Integer.MAX_VALUE / 8] = (byte) 0x80;
    assertThat(ImmutableBitSet.valueOf(bytes).stream()).containsExactly(Integer.MAX_VALUE);
  }

  @Test public void toImmutableBitSet_collectsIndices() {
    ImmutableBitSet set = Stream.of(3, 100, 1).collect(ImmutableBitSet.toImmutableBitSet());
    assertThat(set.stream()).containsExactly(1, 3, 100).inOrder();
  }

  @Test public void toImmutableBitSet_emptyStream_isEmpty() {
    assertThat(Stream.<Integer>empty().collect(ImmutableBitSet.toImmutableBitSet()).stream())
        .isEmpty();
  }

  @Test public void toImmutableBitSet_duplicateIndices_setOnce() {
    ImmutableBitSet set = Stream.of(5, 5, 5).collect(ImmutableBitSet.toImmutableBitSet());
    assertThat(set.stream()).containsExactly(5);
  }

  @Test public void toImmutableBitSet_streamRoundTrip_equalsOriginal() {
    ImmutableBitSet set = bitSetOf(1, 64, 200);
    assertThat(set.stream().boxed().collect(ImmutableBitSet.toImmutableBitSet())).isEqualTo(set);
  }

  @Test public void toImmutableBitSet_negativeIndex_throws() {
    Stream<Integer> indices = Stream.of(1, -1);
    IndexOutOfBoundsException thrown = assertThrows(
        IndexOutOfBoundsException.class,
        () -> indices.collect(ImmutableBitSet.toImmutableBitSet()));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void toImmutableBitSet_nullIndex_throws() {
    Stream<Integer> indices = Stream.of(1, null);
    assertThrows(
        NullPointerException.class, () -> indices.collect(ImmutableBitSet.toImmutableBitSet()));
  }

  @Test public void toImmutableBitSet_combinesPartialResults() {
    ImmutableBitSet set = collectInTwoParts(
        ImmutableBitSet.toImmutableBitSet(), ImmutableList.of(1, 100), ImmutableList.of(3, 200));
    assertThat(set.stream()).containsExactly(1, 3, 100, 200).inOrder();
  }

  @Test public void toImmutableBitSet_combinesWithEmptyPartialResult() {
    ImmutableBitSet set = collectInTwoParts(
        ImmutableBitSet.toImmutableBitSet(), ImmutableList.of(), ImmutableList.of(3, 200));
    assertThat(set.stream()).containsExactly(3, 200).inOrder();
  }

  @Test public void or_collectsUnion() {
    ImmutableBitSet union =
        Stream.of(bitSetOf(1, 3), bitSetOf(3, 100), bitSetOf(200)).collect(ImmutableBitSet.or());
    assertThat(union.stream()).containsExactly(1, 3, 100, 200).inOrder();
  }

  @Test public void or_emptyStream_isEmpty() {
    assertThat(Stream.<ImmutableBitSet>empty().collect(ImmutableBitSet.or()).stream()).isEmpty();
  }

  @Test public void or_combinesPartialResults() {
    ImmutableBitSet union = collectInTwoParts(
        ImmutableBitSet.or(),
        ImmutableList.of(bitSetOf(1, 3), bitSetOf(100)),
        ImmutableList.of(bitSetOf(3, 200)));
    assertThat(union.stream()).containsExactly(1, 3, 100, 200).inOrder();
  }

  @Test public void or_combinesWithEmptyPartialResult() {
    ImmutableBitSet union = collectInTwoParts(
        ImmutableBitSet.or(), ImmutableList.of(), ImmutableList.of(bitSetOf(3, 200)));
    assertThat(union.stream()).containsExactly(3, 200).inOrder();
  }

  @Test public void or_nullElement_throws() {
    Stream<ImmutableBitSet> sets = Stream.of(bitSetOf(1), null);
    assertThrows(NullPointerException.class, () -> sets.collect(ImmutableBitSet.or()));
  }

  @Test public void xor_collectsBitsSetAnOddNumberOfTimes() {
    ImmutableBitSet result = Stream.of(bitSetOf(1, 3, 64), bitSetOf(3, 100), bitSetOf(1, 100, 200))
        .collect(ImmutableBitSet.xor());
    assertThat(result.stream()).containsExactly(64, 200).inOrder();
  }

  @Test public void xor_bitSetThreeTimes_isSet() {
    ImmutableBitSet result =
        Stream.of(bitSetOf(100), bitSetOf(100), bitSetOf(100)).collect(ImmutableBitSet.xor());
    assertThat(result.stream()).containsExactly(100);
  }

  @Test public void xor_emptyStream_isEmpty() {
    assertThat(Stream.<ImmutableBitSet>empty().collect(ImmutableBitSet.xor()).stream()).isEmpty();
  }

  @Test public void xor_clearsHighBits_retainsLowBits() {
    ImmutableBitSet result =
        Stream.of(bitSetOf(1, 200), bitSetOf(200)).collect(ImmutableBitSet.xor());
    assertThat(result.stream()).containsExactly(1);
  }

  @Test public void xor_combinesPartialResults() {
    ImmutableBitSet result = collectInTwoParts(
        ImmutableBitSet.xor(),
        ImmutableList.of(bitSetOf(1, 3), bitSetOf(3, 100)),
        ImmutableList.of(bitSetOf(1, 200)));
    assertThat(result.stream()).containsExactly(100, 200).inOrder();
  }

  @Test public void xor_combinesWithEmptyPartialResult() {
    ImmutableBitSet result = collectInTwoParts(
        ImmutableBitSet.xor(), ImmutableList.of(bitSetOf(3, 200)), ImmutableList.of());
    assertThat(result.stream()).containsExactly(3, 200).inOrder();
  }

  @Test public void xor_nullElement_throws() {
    Stream<ImmutableBitSet> sets = Stream.of(bitSetOf(1), null);
    assertThrows(NullPointerException.class, () -> sets.collect(ImmutableBitSet.xor()));
  }

  @Test public void get_negativeIndex_emptySet_throws() {
    ImmutableBitSet set = bitSetOf();
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> set.get(-1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void get_negativeIndex_onlyBit3_throws() {
    ImmutableBitSet set = bitSetOf(3);
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> set.get(-1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void get_negativeIndex_onlyBit100_throws() {
    ImmutableBitSet set = bitSetOf(100);
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> set.get(-1));
    assertThat(thrown).hasMessageThat().contains("-1");
  }

  @Test public void get_integerMinValue_singleWord_throws() {
    ImmutableBitSet set = bitSetOf(3);
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> set.get(Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void get_integerMinValue_multiWord_throws() {
    ImmutableBitSet set = bitSetOf(100);
    IndexOutOfBoundsException thrown =
        assertThrows(IndexOutOfBoundsException.class, () -> set.get(Integer.MIN_VALUE));
    assertThat(thrown).hasMessageThat().contains(String.valueOf(Integer.MIN_VALUE));
  }

  @Test public void get_emptySet_noBitsSet() {
    assertThat(indicesBelow(200, bitSetOf()::get)).isEmpty();
  }

  @Test public void get_onlyBit0() {
    assertThat(indicesBelow(200, bitSetOf(0)::get)).containsExactly(0);
  }

  @Test public void get_onlyBit63() {
    assertThat(indicesBelow(200, bitSetOf(63)::get)).containsExactly(63);
  }

  @Test public void get_onlyBit64() {
    assertThat(indicesBelow(200, bitSetOf(64)::get)).containsExactly(64);
  }

  @Test public void get_onlyBit127() {
    assertThat(indicesBelow(200, bitSetOf(127)::get)).containsExactly(127);
  }

  @Test public void get_onlyBit1000() {
    assertThat(indicesBelow(1100, bitSetOf(1000)::get)).containsExactly(1000);
  }

  @Test public void get_allBitsBelow64() {
    ImmutableBitSet set = bitSetOf(IntStream.range(0, 64).toArray());
    assertThat(indicesBelow(200, set::get))
        .containsExactlyElementsIn(IntStream.range(0, 64).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void get_bitsAcrossWordBoundaries() {
    ImmutableBitSet set = bitSetOf(0, 63, 64, 127, 128, 1000);
    assertThat(indicesBelow(1100, set::get)).containsExactly(0, 63, 64, 127, 128, 1000).inOrder();
  }

  @Test public void get_index64_onlyBit0_returnsFalse() {
    assertThat(bitSetOf(0).get(64)).isFalse();
  }

  @Test public void get_index128_onlyBit64_returnsFalse() {
    assertThat(bitSetOf(64).get(128)).isFalse();
  }

  @Test public void get_integerMaxValue_onlyBit3_returnsFalse() {
    assertThat(bitSetOf(3).get(Integer.MAX_VALUE)).isFalse();
  }

  @Test public void get_integerMaxValue_onlyBit100_returnsFalse() {
    assertThat(bitSetOf(100).get(Integer.MAX_VALUE)).isFalse();
  }

  @Test public void get_matchesJavaUtilBitSet_randomBitsBelow64() {
    int[] indices = randomIndices(40, 64);
    BitSet expected = javaUtilBitSetOf(indices);
    assertThat(indicesBelow(200, bitSetOf(indices)::get))
        .containsExactlyElementsIn(indicesBelow(200, expected::get))
        .inOrder();
  }

  @Test public void get_matchesJavaUtilBitSet_randomBitsBelow5000() {
    int[] indices = randomIndices(500, 5000);
    BitSet expected = javaUtilBitSetOf(indices);
    assertThat(indicesBelow(5100, bitSetOf(indices)::get))
        .containsExactlyElementsIn(indicesBelow(5100, expected::get))
        .inOrder();
  }

  @Test public void cardinality_emptySet_isZero() {
    assertThat(bitSetOf().cardinality()).isEqualTo(0);
  }

  @Test public void cardinality_onlyBit1000_isOne() {
    assertThat(bitSetOf(1000).cardinality()).isEqualTo(1);
  }

  @Test public void cardinality_allBitsBelow64_is64() {
    assertThat(bitSetOf(IntStream.range(0, 64).toArray()).cardinality()).isEqualTo(64);
  }

  @Test public void cardinality_bitsAcrossWordBoundaries_isSix() {
    assertThat(bitSetOf(0, 63, 64, 127, 128, 1000).cardinality()).isEqualTo(6);
  }

  @Test public void cardinality_multiWord_repeatedCallsReturnSameValue() {
    ImmutableBitSet set = bitSetOf(0, 63, 64, 127, 128, 1000);
    assertThat(set.cardinality()).isEqualTo(6);
    assertThat(set.cardinality()).isEqualTo(6);
  }

  @Test public void cardinality_matchesJavaUtilBitSet_randomBitsBelow64() {
    int[] indices = randomIndices(40, 64);
    assertThat(bitSetOf(indices).cardinality()).isEqualTo(javaUtilBitSetOf(indices).cardinality());
  }

  @Test public void cardinality_matchesJavaUtilBitSet_randomBitsBelow5000() {
    int[] indices = randomIndices(500, 5000);
    assertThat(bitSetOf(indices).cardinality()).isEqualTo(javaUtilBitSetOf(indices).cardinality());
  }

  @Test public void stream_emptySet_isEmpty() {
    assertThat(bitSetOf().stream()).isEmpty();
  }

  @Test public void stream_onlyBit0() {
    assertThat(bitSetOf(0).stream()).containsExactly(0);
  }

  @Test public void stream_onlyBit63() {
    assertThat(bitSetOf(63).stream()).containsExactly(63);
  }

  @Test public void stream_onlyBit64() {
    assertThat(bitSetOf(64).stream()).containsExactly(64);
  }

  @Test public void stream_onlyBit1000() {
    assertThat(bitSetOf(1000).stream()).containsExactly(1000);
  }

  @Test public void stream_allBitsBelow64() {
    assertThat(bitSetOf(IntStream.range(0, 64).toArray()).stream())
        .containsExactlyElementsIn(IntStream.range(0, 64).boxed().collect(toList()))
        .inOrder();
  }

  @Test public void stream_bitsAcrossWordBoundaries() {
    assertThat(bitSetOf(0, 63, 64, 127, 128, 1000).stream())
        .containsExactly(0, 63, 64, 127, 128, 1000)
        .inOrder();
  }

  @Test public void stream_bitsSetInDescendingOrder_streamedInAscendingOrder() {
    ImmutableBitSet set = ImmutableBitSet.builder().set(1000).set(64).set(0).build();
    assertThat(set.stream()).containsExactly(0, 64, 1000).inOrder();
  }

  @Test public void stream_secondCall_returnsAllBits() {
    ImmutableBitSet set = bitSetOf(3, 100);
    int[] unused = set.stream().toArray();
    assertThat(set.stream()).containsExactly(3, 100).inOrder();
  }

  @Test public void stream_matchesJavaUtilBitSet_randomBitsBelow64() {
    int[] indices = randomIndices(40, 64);
    assertThat(bitSetOf(indices).stream())
        .containsExactlyElementsIn(javaUtilBitSetOf(indices).stream().boxed().collect(toList()))
        .inOrder();
  }

  @Test public void stream_matchesJavaUtilBitSet_randomBitsBelow5000() {
    int[] indices = randomIndices(500, 5000);
    assertThat(bitSetOf(indices).stream())
        .containsExactlyElementsIn(javaUtilBitSetOf(indices).stream().boxed().collect(toList()))
        .inOrder();
  }

  @Test public void stream_singleWordSet_toArray() {
    assertThat(bitSetOf(1, 3).stream().toArray()).asList().containsExactly(1, 3).inOrder();
  }

  @Test public void stream_singleWordSet_count() {
    assertThat(bitSetOf(1, 3).stream().count()).isEqualTo(2L);
  }

  @Test public void stream_singleWordSet_isSequential() {
    assertThat(bitSetOf(1, 3).stream().isParallel()).isFalse();
  }

  @Test public void stream_singleWordSet_isDistinct() {
    assertThat(bitSetOf(1, 3).stream().spliterator().hasCharacteristics(Spliterator.DISTINCT))
        .isTrue();
  }

  @Test public void stream_emptySet_iteratorNextInt_throws() {
    PrimitiveIterator.OfInt iterator = ImmutableBitSet.EMPTY.stream().iterator();
    assertThrows(NoSuchElementException.class, iterator::nextInt);
  }

  @Test public void stream_singleWordSet_iteratorPastEnd_throws() {
    PrimitiveIterator.OfInt iterator = bitSetOf(1).stream().iterator();
    assertThat(iterator.nextInt()).isEqualTo(1);
    assertThrows(NoSuchElementException.class, iterator::nextInt);
  }

  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(
            ImmutableBitSet.EMPTY,
            bitSetOf(),
            ImmutableBitSet.valueOf(new long[] {0L}),
            ImmutableBitSet.valueOf(new long[0]),
            ImmutableBitSet.builderWithExpectedSize(1024).build())
        .addEqualityGroup(
            bitSetOf(1, 3),
            ImmutableBitSet.valueOf(new long[] {0b1010L}),
            ImmutableBitSet.valueOf(new long[] {0b1010L, 0L}))
        .addEqualityGroup(bitSetOf(1, 4))
        .addEqualityGroup(bitSetOf(63), ImmutableBitSet.valueOf(new long[] {Long.MIN_VALUE}))
        .addEqualityGroup(
            bitSetOf(0, 64),
            ImmutableBitSet.valueOf(new long[] {1L, 1L}),
            ImmutableBitSet.builderWithExpectedSize(1024).set(0).set(64).build())
        .addEqualityGroup(bitSetOf(0, 65))
        .addEqualityGroup(javaUtilBitSetOf(1, 3))
        .testEquals();
  }

  @Test public void hashCode_singleWordSetsWithDifferentBits_differ() {
    assertThat(bitSetOf(1).hashCode()).isNotEqualTo(bitSetOf(2).hashCode());
  }

  @Test public void hashCode_multiWordSetsWithDifferentBits_differ() {
    assertThat(bitSetOf(1, 100).hashCode()).isNotEqualTo(bitSetOf(2, 100).hashCode());
  }

  @Test public void hashCode_multiWord_repeatedCallsReturnSameValue() {
    ImmutableBitSet set = bitSetOf(1, 100, 200);
    int first = set.hashCode();
    assertThat(set.hashCode()).isEqualTo(first);
  }

  @Test public void hashCode_multiWordZeroHash_repeatedCallsReturnZero() {
    ImmutableBitSet set = ImmutableBitSet.valueOf(new long[] {1234L ^ 2L, 1L});
    assertThat(set.hashCode()).isEqualTo(0);
    assertThat(set.hashCode()).isEqualTo(0);
  }

  @Test public void testNulls_staticMethods() {
    new NullPointerTester().testAllPublicStaticMethods(ImmutableBitSet.class);
  }

  @Test public void testNulls_singleWordSetMethods() {
    new NullPointerTester().testAllPublicInstanceMethods(bitSetOf(1, 3));
  }

  @Test public void testNulls_multiWordSetMethods() {
    new NullPointerTester().testAllPublicInstanceMethods(bitSetOf(1, 100));
  }

  @Test public void testNulls_builderMethods() {
    new NullPointerTester().testAllPublicInstanceMethods(ImmutableBitSet.builder());
  }

  @Test public void toString_emptySet() {
    assertThat(bitSetOf().toString()).isEqualTo("{}");
  }

  @Test public void toString_singleWordSet() {
    assertThat(bitSetOf(1, 3).toString()).isEqualTo("{1, 3}");
  }

  @Test public void toString_multiWordSet() {
    assertThat(bitSetOf(1, 64, 200).toString()).isEqualTo("{1, 64, 200}");
  }

  @Test public void isEmpty_emptySet_isTrue() {
    assertThat(bitSetOf().isEmpty()).isTrue();
  }

  @Test public void isEmpty_singleWordSet_isFalse() {
    assertThat(bitSetOf(3).isEmpty()).isFalse();
  }

  @Test public void isEmpty_multiWordSet_isFalse() {
    assertThat(bitSetOf(100).isEmpty()).isFalse();
  }

  @Test public void isEmpty_allBitsCleared_isTrue() {
    assertThat(builderOf(1, 100).andNot(bitSetOf(1, 100)).build().isEmpty()).isTrue();
  }

  @Test public void length_emptySet_isZero() {
    assertThat(bitSetOf().length()).isEqualTo(0);
  }

  @Test public void length_onlyBit0_isOne() {
    assertThat(bitSetOf(0).length()).isEqualTo(1);
  }

  @Test public void length_onlyBit63_is64() {
    assertThat(bitSetOf(63).length()).isEqualTo(64);
  }

  @Test public void length_onlyBit64_is65() {
    assertThat(bitSetOf(64).length()).isEqualTo(65);
  }

  @Test public void length_onlyBit1000_is1001() {
    assertThat(bitSetOf(1000).length()).isEqualTo(1001);
  }

  @Test public void intersects_singleWordSets_commonBit_isTrue() {
    assertThat(bitSetOf(1, 3).intersects(bitSetOf(3, 5))).isTrue();
  }

  @Test public void intersects_singleWordSets_noCommonBit_isFalse() {
    assertThat(bitSetOf(1, 3).intersects(bitSetOf(2, 4))).isFalse();
  }

  @Test public void intersects_multiWordSets_commonBit_isTrue() {
    assertThat(bitSetOf(1, 100).intersects(bitSetOf(100, 200))).isTrue();
  }

  @Test public void intersects_multiWordSets_noCommonBit_isFalse() {
    assertThat(bitSetOf(1, 100).intersects(bitSetOf(2, 200))).isFalse();
  }

  @Test public void intersects_singleWordWithMultiWord_commonBit_isTrue() {
    assertThat(bitSetOf(3).intersects(bitSetOf(3, 100))).isTrue();
  }

  @Test public void intersects_singleWordWithMultiWord_noCommonBit_isFalse() {
    assertThat(bitSetOf(3).intersects(bitSetOf(4, 100))).isFalse();
  }

  @Test public void intersects_multiWordWithSingleWord_commonBit_isTrue() {
    assertThat(bitSetOf(3, 100).intersects(bitSetOf(3))).isTrue();
  }

  @Test public void intersects_multiWordWithSingleWord_noCommonBit_isFalse() {
    assertThat(bitSetOf(4, 100).intersects(bitSetOf(3))).isFalse();
  }

  @Test public void intersects_singleWordWithMultiWord_onlyHigherBitInCommon_isTrue() {
    assertThat(bitSetOf(0, 3).intersects(bitSetOf(3, 100))).isTrue();
  }

  @Test public void intersects_singleWordWithMultiWord_bit0NotInCommon_isFalse() {
    assertThat(bitSetOf(0, 3).intersects(bitSetOf(4, 100))).isFalse();
  }

  @Test public void intersects_singleWordBit63WithMultiWord_commonBit_isTrue() {
    assertThat(bitSetOf(63).intersects(bitSetOf(63, 100))).isTrue();
  }

  @Test public void intersects_singleWordBit63WithMultiWord_noCommonBit_isFalse() {
    assertThat(bitSetOf(63).intersects(bitSetOf(62, 100))).isFalse();
  }

  @Test public void intersects_multiWordWithSingleWordBit63_commonBit_isTrue() {
    assertThat(bitSetOf(63, 100).intersects(bitSetOf(63))).isTrue();
  }

  @Test public void intersects_multiWordWithSingleWordBit63_noCommonBit_isFalse() {
    assertThat(bitSetOf(62, 100).intersects(bitSetOf(63))).isFalse();
  }

  @Test public void intersects_emptySet_isFalse() {
    assertThat(bitSetOf(1, 100).intersects(bitSetOf())).isFalse();
  }

  @Test public void intersects_singleWordSet_null_throws() {
    ImmutableBitSet set = bitSetOf(1);
    ImmutableBitSet that = null;
    assertThrows(NullPointerException.class, () -> set.intersects(that));
  }

  @Test public void intersects_multiWordSet_null_throws() {
    ImmutableBitSet set = bitSetOf(1, 100);
    ImmutableBitSet that = null;
    assertThrows(NullPointerException.class, () -> set.intersects(that));
  }

  @Test public void toLongArray_emptySet_isEmpty() {
    assertThat(bitSetOf().toLongArray()).isEmpty();
  }

  @Test public void toLongArray_singleWordSet() {
    assertThat(bitSetOf(1, 3).toLongArray()).isEqualTo(new long[] {0b1010L});
  }

  @Test public void toLongArray_multiWordSet() {
    assertThat(bitSetOf(0, 64, 255).toLongArray())
        .isEqualTo(new long[] {1L, 1L, 0L, Long.MIN_VALUE});
  }

  @Test public void toLongArray_matchesJavaUtilBitSet_randomBitsBelow5000() {
    int[] indices = randomIndices(500, 5000);
    assertThat(bitSetOf(indices).toLongArray()).isEqualTo(javaUtilBitSetOf(indices).toLongArray());
  }

  @Test public void toLongArray_arrayModified_setUnchanged() {
    ImmutableBitSet set = bitSetOf(0, 64);
    long[] words = set.toLongArray();
    words[0] = 0L;
    assertThat(set.stream()).containsExactly(0, 64).inOrder();
  }

  @Test public void toLongArray_valueOfRoundTrip_equalsOriginal() {
    ImmutableBitSet set = bitSetOf(1, 64, 200);
    assertThat(ImmutableBitSet.valueOf(set.toLongArray())).isEqualTo(set);
  }

  @Test public void toByteArray_emptySet_isEmpty() {
    assertThat(bitSetOf().toByteArray()).isEmpty();
  }

  @Test public void toByteArray_singleByte_bit0() {
    assertThat(bitSetOf(0).toByteArray()).isEqualTo(new byte[] {1});
  }

  @Test public void toByteArray_singleByte_bit7() {
    assertThat(bitSetOf(7).toByteArray()).isEqualTo(new byte[] {(byte) 0x80});
  }

  @Test public void toByteArray_twoBytes_bit8() {
    assertThat(bitSetOf(8).toByteArray()).isEqualTo(new byte[] {0, 1});
  }

  @Test public void toByteArray_eightBytes_bit63() {
    assertThat(bitSetOf(63).toByteArray()).isEqualTo(new byte[] {0, 0, 0, 0, 0, 0, 0, (byte) 0x80});
  }

  @Test public void toByteArray_multiWordSet() {
    assertThat(bitSetOf(0, 64, 79).toByteArray())
        .isEqualTo(new byte[] {1, 0, 0, 0, 0, 0, 0, 0, 1, (byte) 0x80});
  }

  @Test public void toByteArray_matchesJavaUtilBitSet_randomBitsBelow64() {
    int[] indices = randomIndices(40, 64);
    assertThat(bitSetOf(indices).toByteArray()).isEqualTo(javaUtilBitSetOf(indices).toByteArray());
  }

  @Test public void toByteArray_matchesJavaUtilBitSet_randomBitsBelow5000() {
    int[] indices = randomIndices(500, 5000);
    assertThat(bitSetOf(indices).toByteArray()).isEqualTo(javaUtilBitSetOf(indices).toByteArray());
  }

  @Test public void toByteArray_arrayModified_setUnchanged() {
    ImmutableBitSet set = bitSetOf(0, 64);
    byte[] bytes = set.toByteArray();
    bytes[0] = 0;
    assertThat(set.stream()).containsExactly(0, 64).inOrder();
  }

  @Test public void toByteArray_valueOfRoundTrip_singleWord_equalsOriginal() {
    ImmutableBitSet set = bitSetOf(0, 7, 15, 63);
    assertThat(ImmutableBitSet.valueOf(set.toByteArray())).isEqualTo(set);
  }

  @Test public void toByteArray_valueOfRoundTrip_multiWord_equalsOriginal() {
    ImmutableBitSet set = bitSetOf(1, 64, 200);
    assertThat(ImmutableBitSet.valueOf(set.toByteArray())).isEqualTo(set);
  }

  private static ImmutableBitSet bitSetOf(int... indices) {
    return builderOf(indices).build();
  }

  private static ImmutableBitSet.Builder builderOf(int... indices) {
    ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
    for (int index : indices) {
      builder.set(index);
    }
    return builder;
  }

  private static BitSet javaUtilBitSetOf(int... indices) {
    BitSet bitSet = new BitSet();
    for (int index : indices) {
      bitSet.set(index);
    }
    return bitSet;
  }

  private static <T, A> ImmutableBitSet collectInTwoParts(
      Collector<T, A, ImmutableBitSet> collector, List<T> left, List<T> right) {
    A leftResult = collector.supplier().get();
    left.forEach(element -> collector.accumulator().accept(leftResult, element));
    A rightResult = collector.supplier().get();
    right.forEach(element -> collector.accumulator().accept(rightResult, element));
    return collector.finisher().apply(collector.combiner().apply(leftResult, rightResult));
  }

  private static int[] randomIndices(int count, int bound) {
    return new Random(42).ints(count, 0, bound).toArray();
  }

  private static List<Integer> indicesBelow(int limit, IntPredicate predicate) {
    return IntStream.range(0, limit).filter(predicate).boxed().collect(toList());
  }
}
