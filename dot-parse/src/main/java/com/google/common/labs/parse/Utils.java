/*****************************************************************************
 * Copyright (C) google.com                                                  *
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
package com.google.common.labs.parse;

import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.lang.Math.min;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

class Utils {
  static void checkArgument(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  static void checkState(boolean condition, String message, Object... args) {
    if (!condition) {
      throw new IllegalStateException(String.format(message, args));
    }
  }

  static int checkPositionIndex(int index, int size, String name) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException(
         String.format("%s (%s) must be in range of [0..%s]", name, index, size));
    }
    return index;
  }

  static Set<String> caseInsensitivePrefixes(String string, int maxLength) {
    checkArgument(maxLength >= 0, "maxLength (%s) must not be negative", maxLength);
    Set<String> prefixes = new LinkedHashSet<>();
    char[] buffer = new char[min(maxLength, string.length())];
    new Object() {
      void from(int index) {
        if (index == buffer.length) {
          prefixes.add(new String(buffer));
          return;
        }
        char chr = string.charAt(index);
        Stream.of(toLowerCase(chr), toUpperCase(chr))
            .distinct()
            .forEach(c -> {
              buffer[index] = c;
              from(index + 1);
            });
      }
    }.from(0);
    return prefixes;
  }
}
