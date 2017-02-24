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
package com.google.mu.util;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import com.google.common.testing.NullPointerTester;

public class IterateTest {

  @Test public void testNulls() {
    new NullPointerTester().testAllPublicStaticMethods(Iterate.class);
  }

  @Test public void testThrough() {
    List<String> to = new ArrayList<>();
    Iterate.through(Stream.of(1, 2).map(Object::toString), to::add);
    assertThat(to).containsExactly("1", "2");
  }

  // TODO: Test close() being called. (but Mockito is having trouble to spy streams)
}
