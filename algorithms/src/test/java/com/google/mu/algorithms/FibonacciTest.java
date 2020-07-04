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
package com.google.mu.algorithms;

import static com.google.common.truth.Truth8.assertThat;

import org.junit.Test;

public class FibonacciTest {
  @Test public void fibonacci() {
    assertThat(Fibonacci.iterate().limit(7))
        .containsExactly(0L, 1L, 1L, 2L, 3L, 5L, 8L)
        .inOrder();
  }
}
