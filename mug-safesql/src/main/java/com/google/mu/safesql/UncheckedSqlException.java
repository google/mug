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

/** A simple unchecked wrapper of {@link SQLException}. */
public class UncheckedSqlException extends RuntimeException {
  private final SQLException sqlException;

  public UncheckedSqlException(SQLException sqlException) {
    super(sqlException);
    this.sqlException = sqlException;
  }

  public SQLException asChecked() {
    return sqlException;
  }
}
