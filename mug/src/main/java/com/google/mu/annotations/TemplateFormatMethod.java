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

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a method that uses a template string and fills the "{placeholder}"s in the template
 * with arguments. Such methods expect a string parameter annotated with {@code @TemplateString}.
 *
 * <p>For example with:
 * <pre>
 * {@literal @}TemplateFormatMethod
 * BillingException reportBillingError(@TemplateString String template, Object... args) {
 *   ...
 * }</pre>
 *
 * The method can be called like {@code reportBillingError("id: {id}", id)}.
 *
 * <p>Similar to but different from ErrorProne's {@code @FormatMethod},
 * {@code @TemplateFormatMethod} methods use named placeholders instead of the printf style "%s".
 * Such methods work better when the template strings are constants shared among multiple classes.
 *
 * <p>To minimize confusion, the template parameter must be annotated with {@code @TemplateString}.
 * It's not optional.
 *
 * @since 8.0
 */
@Documented
@Retention(CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface TemplateFormatMethod {}
