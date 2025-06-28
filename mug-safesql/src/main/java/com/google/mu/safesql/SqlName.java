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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to facilitate mapping from a result set row to a pojo through the
 * {@link SafeSql#query(java.sql.Connection, Class)} method and friends.
 *
 * <p>For records, you can use it to annotate a constructor parameter's name to match the
 * corresponding result set column name from a SQL query, although, if you can enable the javac
 * {@code -parameters} flag, you likely do not need this annotation.
 *
 * <p>For Java beans, you can annotate a setter method when the property name doesn't otherwise
 * match the result set column name.
 *
 * @since 8.7
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, PARAMETER})
public @interface SqlName {
  /**
   * The name to fetch the corresponding value from a {@link java.sql.ResultSet}.
   *
   * <p>Case (camelCase, snake_case etc.) doesn't matter.
   */
  String value();
}
