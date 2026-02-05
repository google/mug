package com.google.mu.annotations;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Methods and constructors annotated as so will require the call site
 * argument expressions match (include) the tokenized and normalized formal parameter name.
 *
 * <p>For example: {@code new Dimension(size.getWidth(), size.getHeight())} will match
 * {@code record Dimension(int width, int height)}, but will fail if it's defined as
 * {@code record Dimension(int height, int width)}.
 *
 * <p>If the argument expression is indeed as expected despite not matching the parameter name,
 * you can always use an explicit comment, for example: <pre>{@code
 * new Dimension(&#47;* width *&#47; list.get(0), &#47;* height *&#47; list.get(1));
 * }</pre>
 *
 * <p>Note that method references used as functional interfaces are not checked for parameter
 * name matching between the method declaration and the functional interface's method names.
 *
 * @since 10.0
 */
@Documented
@Retention(CLASS)
@Target({ METHOD, CONSTRUCTOR, TYPE })
public @interface ParametersMustMatchByName {}
