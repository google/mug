package com.google.mu.collect;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.NoSuchElementException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.testing.ClassSanityTester;
import com.google.common.testing.EqualsTester;
import com.google.mu.collect.InsertionPoint;

@RunWith(JUnit4.class)
public class InsertionPointTest {
  @Test public void at_exact() {
    assertThat(InsertionPoint.at(1).exact()).hasValue(1);
  }

  @Test public void at_floor() {
    assertThat(InsertionPoint.at(1).floor()).isEqualTo(1);
  }

  @Test public void at_ceiling() {
    assertThat(InsertionPoint.at(1).ceiling()).isEqualTo(1);
  }

  @Test public void at_notAboveAll() {
    assertThat(InsertionPoint.at(1).isAboveAll()).isFalse();
  }

  @Test public void at_notBelowAll() {
    assertThat(InsertionPoint.at(1).isBelowAll()).isFalse();
  }

  @Test public void at_toString() {
    assertThat(InsertionPoint.at(1).toString()).isEqualTo("1");
  }

  @Test public void before_eact() {
    assertThat(InsertionPoint.before(1).exact()).isEmpty();
  }

  @Test public void before_floor() {
    assertThat(InsertionPoint.before(1).floor()).isEqualTo(0);
  }

  @Test public void before_ceiling() {
    assertThat(InsertionPoint.before(1).ceiling()).isEqualTo(1);
  }

  @Test public void before_notAboveAll() {
    assertThat(InsertionPoint.before(1).isAboveAll()).isFalse();
  }

  @Test public void before_notBelowAll() {
    assertThat(InsertionPoint.before(1).isBelowAll()).isFalse();
  }

  @Test public void before_toString() {
    assertThat(InsertionPoint.before(1).toString()).isEqualTo("(0..1)");
  }

  @Test public void after_eact() {
    assertThat(InsertionPoint.after(1).exact()).isEmpty();
  }

  @Test public void after_floor() {
    assertThat(InsertionPoint.after(1).floor()).isEqualTo(1);
  }

  @Test public void after_ceiling() {
    assertThat(InsertionPoint.after(1).ceiling()).isEqualTo(2);
  }

  @Test public void after_notAboveAll() {
    assertThat(InsertionPoint.after(1).isAboveAll()).isFalse();
  }

  @Test public void after_notBelowAll() {
    assertThat(InsertionPoint.after(1).isBelowAll()).isFalse();
  }

  @Test public void after_toString() {
    assertThat(InsertionPoint.after(1).toString()).isEqualTo("(1..2)");
  }

  @Test public void beforeMinValue_eact() {
    assertThat(InsertionPoint.before(Integer.MIN_VALUE).exact()).isEmpty();
  }

  @Test public void beforeMinValue_floor() {
    assertThrows(NoSuchElementException.class, () -> InsertionPoint.before(Integer.MIN_VALUE).floor());
  }

  @Test public void beforeMinValue_ceiling() {
    assertThat(InsertionPoint.before(Integer.MIN_VALUE).ceiling()).isEqualTo(Integer.MIN_VALUE);
  }

  @Test public void beforeMinValue_notAboveAll() {
    assertThat(InsertionPoint.before(Integer.MIN_VALUE).isAboveAll()).isFalse();
  }

  @Test public void beforeMinValue_belowAll() {
    assertThat(InsertionPoint.before(Integer.MIN_VALUE).isBelowAll()).isTrue();
  }

  @Test public void beforeMinValue_toString() {
    assertThat(InsertionPoint.before(Integer.MIN_VALUE).toString())
        .isEqualTo(Range.lessThan(Integer.MIN_VALUE).toString());
  }

  @Test public void afterMaxValue_eact() {
    assertThat(InsertionPoint.after(Integer.MAX_VALUE).exact()).isEmpty();
  }

  @Test public void afterMaxValue_floor() {
    assertThat(InsertionPoint.after(Integer.MAX_VALUE).floor()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test public void afterMaxValue_ceiling() {
    assertThrows(NoSuchElementException.class, () -> InsertionPoint.after(Integer.MAX_VALUE).ceiling());
  }

  @Test public void afterMaxValue_aboveAll() {
    assertThat(InsertionPoint.after(Integer.MAX_VALUE).isAboveAll()).isTrue();
  }

  @Test public void afterMaxValue_notBelowAll() {
    assertThat(InsertionPoint.after(Integer.MAX_VALUE).isBelowAll()).isFalse();
  }

  @Test public void afterMaxValue_toString() {
    assertThat(InsertionPoint.after(Integer.MAX_VALUE).toString())
        .isEqualTo(Range.greaterThan(Integer.MAX_VALUE).toString());
  }

  @Test public void afterDoubleMax() {
    InsertionPoint<Double> insertionPoint = InsertionPoint.after(Double.MAX_VALUE);
    assertThat(insertionPoint.floor()).isEqualTo(Double.MAX_VALUE);
    assertThat(insertionPoint.isBelowAll()).isFalse();
  }

  @Test public void beforeDoubleNegativeMax() {
    InsertionPoint<Double> insertionPoint = InsertionPoint.before(-Double.MAX_VALUE);
    assertThat(insertionPoint.ceiling()).isEqualTo(-Double.MAX_VALUE);
    assertThat(insertionPoint.isAboveAll()).isFalse();
  }

  @Test public void betweenDoublePostiveAndNegativeZero() {
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(0.0, -0.0));
  }

  @Test public void negativeZeroIsEqualToPositiveZero() {
    assertThat(InsertionPoint.before(-0.0).compareTo(InsertionPoint.before(0.0))).isEqualTo(0);
    assertThat(InsertionPoint.before(-0.0)).isEqualTo(InsertionPoint.before(0.0));
  }

  @Test public void lowerDoubleGreaterThanHigherDouble() {
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(Double.MIN_VALUE, 0.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> InsertionPoint.between(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY));
  }

  @Test public void betweenEqualValue() {
    assertThrows(
        IllegalArgumentException.class,
        () -> InsertionPoint.between(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
    assertThrows(
        IllegalArgumentException.class,
        () -> InsertionPoint.between(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(0.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(-0.0, 0.0));
    assertThrows(
        IllegalArgumentException.class,
        () -> InsertionPoint.between(Double.MAX_VALUE, Double.MAX_VALUE));
    assertThrows(
        IllegalArgumentException.class,
        () -> InsertionPoint.between(Double.MIN_VALUE, Double.MIN_VALUE));
  }

  @Test public void betweenFiniteDoubles() {
    InsertionPoint<Double> insertionPoint = InsertionPoint.between(-1.0, 1.0);
    assertThat(insertionPoint.floor()).isEqualTo(-1.0);
    assertThat(insertionPoint.ceiling()).isEqualTo(1.0);
    assertThat(insertionPoint.isBelowAll()).isFalse();
    assertThat(insertionPoint.isAboveAll()).isFalse();
  }

  @Test public void betweenInfinity() {
    InsertionPoint<Double> insertionPoint = InsertionPoint.between(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    assertThat(insertionPoint.floor()).isEqualTo(Double.NEGATIVE_INFINITY);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(insertionPoint.isBelowAll()).isFalse();
    assertThat(insertionPoint.isAboveAll()).isFalse();
  }

  @Test public void beforePositiveInfinity() {
    InsertionPoint<Double> insertionPoint = InsertionPoint.before(Double.POSITIVE_INFINITY);
    assertThat(insertionPoint.floor()).isEqualTo(Double.MAX_VALUE);
    assertThat(insertionPoint.ceiling()).isEqualTo(Double.POSITIVE_INFINITY);
    assertThat(insertionPoint.isBelowAll()).isFalse();
    assertThat(insertionPoint.isAboveAll()).isFalse();
  }

  @Test public void beforeNegativeInfinity() {
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.before(Double.NEGATIVE_INFINITY));
  }

  @Test public void afterPositiveInfinity() {
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.after(Double.POSITIVE_INFINITY));
  }

  @Test public void afterNegativeInfinity() {
    InsertionPoint<Double> insertionPoint = InsertionPoint.after(Double.NEGATIVE_INFINITY);
    assertThat(insertionPoint.floor()).isEqualTo(Double.NEGATIVE_INFINITY);
    assertThat(insertionPoint.ceiling()).isEqualTo(-Double.MAX_VALUE);
    assertThat(insertionPoint.isBelowAll()).isFalse();
    assertThat(insertionPoint.isAboveAll()).isFalse();
  }

  @Test public void nanNotSupported() {
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.before(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.after(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(Double.NaN, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(0.0, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(Double.NaN, 0.0));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(Double.POSITIVE_INFINITY, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(Double.NEGATIVE_INFINITY, Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> InsertionPoint.between(Double.NaN, Double.POSITIVE_INFINITY));
  }

  @Test public void testEquals() {
    new EqualsTester()
        .addEqualityGroup(InsertionPoint.at(1))
        .addEqualityGroup(InsertionPoint.at(2))
        .addEqualityGroup(InsertionPoint.at(Integer.MIN_VALUE))
        .addEqualityGroup(InsertionPoint.at(Integer.MAX_VALUE))
        .addEqualityGroup(InsertionPoint.after(Integer.MIN_VALUE), InsertionPoint.before(Integer.MIN_VALUE + 1))
        .addEqualityGroup(InsertionPoint.before(Integer.MAX_VALUE), InsertionPoint.after(Integer.MAX_VALUE - 1))
        .addEqualityGroup(InsertionPoint.after(Integer.MAX_VALUE))
        .addEqualityGroup(InsertionPoint.before(Integer.MIN_VALUE))
        .addEqualityGroup(InsertionPoint.before(1), InsertionPoint.after(0))
        .addEqualityGroup(InsertionPoint.before(-1), InsertionPoint.after(-2))
        .addEqualityGroup(InsertionPoint.before(0.0), InsertionPoint.between(-Double.MIN_VALUE, 0.0))
        .addEqualityGroup(InsertionPoint.before(Double.MIN_VALUE), InsertionPoint.after(0.0))
        .addEqualityGroup(InsertionPoint.before(-Double.MAX_VALUE), InsertionPoint.after(Double.NEGATIVE_INFINITY))
        .addEqualityGroup(InsertionPoint.after(Double.MAX_VALUE), InsertionPoint.before(Double.POSITIVE_INFINITY))
        .addEqualityGroup(InsertionPoint.between(-Double.MIN_VALUE, Double.MIN_VALUE))
        .addEqualityGroup(InsertionPoint.between(-Double.MAX_VALUE, Double.MAX_VALUE))
        .addEqualityGroup(InsertionPoint.between(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
        .testEquals();
  }

  @Test public void testNulls() throws Exception {
    ClassSanityTester tester = new ClassSanityTester()
        .setDefault(DiscreteDomain.class, DiscreteDomain.integers())
        .setDefault(Comparable.class, 123);
    tester.testNulls(InsertionPoint.class);
  }
}
