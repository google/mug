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
package com.google.mu.safesql;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.sql.SQLException;
import java.util.stream.Stream;

import com.google.common.base.VerifyException;
import com.google.common.io.Closer;
import com.google.errorprone.annotations.MustBeClosed;

/** Helps manage JDBC resources that need to be closed now or lazily. */
final class JdbcCloser implements AutoCloseable {
  interface JdbcCloseable {
    void run() throws SQLException;
  }

  private final Closer closer = Closer.create();
  private boolean owned = true;

  void register(JdbcCloseable closeable) {
    closer.register(() -> {
      try {
        closeable.run();
      } catch (SQLException e) {
        throw new UncheckedSqlException(e);
      }
    });
  }

  @MustBeClosed
  <T> Stream<T> attachTo(Stream<T> stream) {
    checkState(owned);
    owned = false;
    return stream.onClose(this::closeNow);
  }

  @Override public void close() {
    if (owned) {
      closeNow();
    }
  }

  private void closeNow() {
    try {
      closer.close();
    } catch (IOException e) {
      throw new VerifyException(e);
    }
  }
}
