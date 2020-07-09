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
package com.google.mu.util.concurrent;

import java.time.Duration;

import com.google.mu.util.concurrent.Retryer.Delay;

/**
 * Looks like Mockito is having trouble to spy Delay.of(), which returns an anonymous class
 * that happens to be final.
 */
class SpyableDelay<E> extends Delay<E> {
  private final Duration duration;

  SpyableDelay(Duration duration) {
    this.duration = duration;
  }

  @Override public Duration duration() {
    return duration;
  }
}