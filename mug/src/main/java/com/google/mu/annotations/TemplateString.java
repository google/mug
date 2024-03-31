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
 * Used to annotate the template string parameter of a {@code @TemplateFormatMethod} method.
 *
 * <p>IMPORTANT: the template string uses named placeholders like "{foo}", <em>not "%s"</em>.
 * Using "%s" in the place of a "{placeholder}" will cause compilation errors.
 *
 * @since 8.0
 */
@Documented
@Target({ElementType.PARAMETER})
public @interface TemplateString {}
