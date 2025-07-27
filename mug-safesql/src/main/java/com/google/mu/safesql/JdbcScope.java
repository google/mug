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

import java.sql.SQLException;
import java.util.stream.Stream;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.MustBeClosed;

/**
 * A scope of JDBC resources to be closed using try-with-resources,
 * or {@link #deferTo deferred} to a lazy stream.
 */
@CheckReturnValue
final class JdbcScope implements AutoCloseable {
  interface JdbcCloseable {
    void close() throws SQLException;
  }

  private JdbcCloseable all = () -> {};

  @MustBeClosed JdbcScope() {}

  void onClose(JdbcCloseable closeable) {
    JdbcCloseable upper = all;
    all = () -> {
      try {
        closeable.close();
      } catch (SQLException e) {
        throw closeForException(upper, e);
      } catch (RuntimeException e) {
        throw closeForException(upper, e);
      } catch (Error e) {
        throw closeForException(upper, e);
      }
      upper.close();
    };
  }

  @MustBeClosed <T> Stream<T> deferTo(Stream<T> stream) {
    JdbcCloseable detached = all;
    Stream<T> attached = stream.onClose(() -> close(detached));
    all = () -> {};
    return attached;
  }

  @Override public void close() {
     close(all);
  }

  private static void close(JdbcCloseable closebale) {
    try {
      closebale.close();
    } catch (SQLException e) {
      throw new UncheckedSqlException(e);
    }
  }

  private static <E extends Throwable> E closeForException(
      JdbcCloseable closeable, E exception) {
    try {
      closeable.close();
    } catch (Throwable e) {
      exception.addSuppressed(e);
    }
    return exception;
  }
}
