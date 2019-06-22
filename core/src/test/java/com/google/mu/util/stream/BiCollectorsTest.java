package com.google.mu.util.stream;

import static com.google.mu.util.stream.BiCollectors.toMap;
import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.summingInt;

import java.util.Map;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BiCollectorsTest {

  @Test public void testToMap_valuesCollected() {
    ImmutableList<Town> towns =
        ImmutableList.of(new Town("WA", 100), new Town("WA", 50), new Town("IL", 200));
    assertThat(
            BiStream.from(towns, Town::getState, town -> town)
                .collect(toMap(summingInt(Town::getPopulation))))
        .containsExactly("WA", 150, "IL", 200)
        .inOrder();
  }

  @Test public void testToMap_keyEncounterOrderRetainedThroughValueCollector() {
    ImmutableList<Town> towns =
        ImmutableList.of(
            new Town("WA", 1),
            new Town("FL", 2),
            new Town("WA", 3),
            new Town("IL", 4),
            new Town("AZ", 5),
            new Town("OH", 6),
            new Town("IN", 7),
            new Town("CA", 8),
            new Town("CA", 9));
    assertThat(
            BiStream.from(towns, Town::getState, town -> town)
                .collect(toMap(summingInt(Town::getPopulation))))
        .containsExactly("WA", 4, "FL", 2, "IL", 4, "AZ", 5, "OH", 6, "IN", 7, "CA", 17)
        .inOrder();
  }

  @Test public void testToMap_empty() {
    ImmutableList<Town> towns = ImmutableList.of();
    assertThat(
            BiStream.from(towns, Town::getState, town -> town)
                .collect(toMap(summingInt(Town::getPopulation))))
        .isEmpty();
  }

  @Test public void testToImmutableMap_covariance() {
    Map<Object, String> map = BiStream.of(1, "one").collect(toMap());
    assertThat(map).containsExactly(1, "one");
  }

  private static final class Town {
    private final String state;
    private final int population;

    Town(String state, int population) {
      this.state = state;
      this.population = population;
    }

    int getPopulation() {
      return population;
    }

    String getState() {
      return state;
    }
  }
}
