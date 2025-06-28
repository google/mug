package com.google.mu.safesql;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.primitives.Primitives;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameter.TestParameterValuesProvider;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@RunWith(TestParameterInjector.class)
public class ResultMapperTest {

  @Test public void testWrapperType(
      @TestParameter(valuesProvider = SampleClassesProvider.class) Class<?> type) {
    assertThat(ResultMapper.wrapperType(type)).isEqualTo(Primitives.wrap(type));
  }

  private static final class SampleClassesProvider implements TestParameterValuesProvider {
    @Override
    public List<?> provideValues() {
      return Stream.concat(
              Primitives.allPrimitiveTypes().stream(),
              Primitives.allWrapperTypes().stream())
          .collect(toList());
    }
  }

}
