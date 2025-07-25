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
package com.google.mu.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Template methods with this annotation reject raw String template parameters to prevent programmer
 * intention ambiguity, because the programmer may intend to use a string as either an identifier
 * (table/column name) or as a string value.
 *
 * <p>Instead, the template should backtick-quote (or double-quote) the placeholder to explicitly
 * indicate an identifier, for example:
 *
 * <pre>{@code
 * ParameterizedQuery.of("SELECT * FROM `{table}`", table);
 * }</pre>
 *
 * <p>Or single-quote the placeholder to indicate that it's a string value, as in:
 *
 * <pre>{@code
 * ParameterizedQuery.of("SELECT * FROM Users WHERE name = '{user_name}'", user.name());
 * }</pre>
 *
 * <p>The single quotes will be elided when the placeholder value is sent through the
 * underlying parameterization API.
 *
 * @since 9.0
 */
@Documented
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface TemplateStringArgsMustBeQuoted {}
