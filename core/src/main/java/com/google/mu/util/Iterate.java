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

import java.util.stream.Stream;

import com.google.mu.function.CheckedConsumer;
import com.google.mu.util.stream.MoreStreams;

@Deprecated
public final class Iterate {

  /** @deprecated Moved to {@link MoreStreams#iterateOnce}. */
  @Deprecated
  public static <T> Iterable<T> once(Stream<T> stream) {
    return MoreStreams.iterateOnce(stream);
  }

  /** @deprecated Moved to {@link MoreStreams#iterateThrough}. */
  @Deprecated
  public static <T, E extends Throwable> void through(
      Stream<? extends T> stream, CheckedConsumer<? super T, E> consumer) throws E {
    MoreStreams.iterateThrough(stream, consumer);
  }

  private Iterate() {}
}
