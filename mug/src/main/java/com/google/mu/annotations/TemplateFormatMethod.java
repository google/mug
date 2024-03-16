package com.google.mu.annotations;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A method annotated with {@code @TemplateFormatMethod} expects a string parameter
 * that's annotated with {@code @TemplateString}.
 *
 * <p>For example in: <pre>{@code
 * @TemplateFormatMethod
 * BillingException reportBillingError(@TemplateString String template, Object... args) {...}
 * }</pre>
 *
 * The method can be called like {@code reportBillingError("missing {field}", fieldName)}.
 *
 * <p>Similar to but different from ErrorProne's {@code @FormatMethod}, {@code
 * @TemplateFormatMethod} methods use named placeholders instead of the printf style "%s".
 * Such methods work better when the template strings are constants shared among multiple classes.
 *
 * <p>To minimize confusion, the template parameter must be annotated with {@code TemplateString}.
 * It's not optional.
 *
 * @since 7.2
 */
@Documented
@Retention(CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface TemplateFormatMethod {}
