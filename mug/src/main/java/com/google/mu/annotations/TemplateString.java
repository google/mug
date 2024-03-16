package com.google.mu.annotations;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to annotate the template string parameter of a {@code @TemplateFormatMethod} method.
 *
 * <p>IMPORTANT: the template string uses named placeholders like "{foo}", <em>not "%s"</em>.
 * Using "%s" in the place of a "{placeholder}" will cause compilation errors.
 *
 * @since 7.2
 */
@Documented
@Retention(CLASS)
@Target({ElementType.PARAMETER})
public @interface TemplateString {}
