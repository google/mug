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
package com.google.mu.benchmarks;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.google.caliper.Benchmark;
import com.google.common.testing.SerializableTester;
import com.google.common.util.concurrent.Futures;

/**
 * Benchmarks for checking and throwing exceptions.
 */
public class ExceptionBenchmark {

  @Benchmark
    // Benchmark for Futures#getChecked.
  void futuresGetChecked(int n) {
    IOException exception = new IOException();
    CompletableFuture<?> future = new CompletableFuture<>();
    future.completeExceptionally(exception);
    for (int i = 0; i < n; i++) {
      try {
        Futures.getChecked(future, IOException.class);
        throw new AssertionError();
      } catch (IOException rethrow) {
        throw new AssertionError(rethrow); // rethrow the exception with a more descriptive message
      } catch (Exception e) {
        throw new AssertionError(e); // handle other exceptions in some other way
      }
    }
  }

  @Benchmark
  void serializeException(int n) {
    // Benchmark for serializing an exception.
    IOException exception = new IOException();
    for (int i = 0; i < n; i++) {
      SerializableTester.reserialize(exception);
    }
  }

  @Benchmark
  void serializeString(int n) {
    // Benchmark for serializing a string.
    String string = "abc";
    for (int i = 0; i < n; i++) {
      SerializableTester.reserialize(string);
    }
    // Benchmark for wrapping an exception.
  }
}
