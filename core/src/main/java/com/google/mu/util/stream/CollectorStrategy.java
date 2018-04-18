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
package com.google.mu.util.stream;

import java.util.Collection;
import java.util.stream.Collector;

/**
 * APIs that need to parameterize the type of collections to use for internal element types
 * can accept a {@code CollectorStrategy} as parameter. For example:
 * {@code theApi(Collectors::toList)} or {@code theApi(ImmutableList::toImmutableList)}.
 *
 * <p>This allows users to control mutability and thread-safety etc.
 *
 * @since 1.4
 */
@Deprecated
@FunctionalInterface
public interface CollectorStrategy {
  <T> Collector<T, ?, ? extends Collection<? extends T>> collector();
}
