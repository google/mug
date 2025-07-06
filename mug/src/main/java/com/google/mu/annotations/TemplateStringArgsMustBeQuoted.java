package com.google.mu.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Template methods with this annotation reject raw String template parameters to prevent programmer
 * intention ambiguity, because the programmer may intend to use a string as either an identifier
 * (table/column name) or as a string expression.
 *
 * <p>Instead, the template should backtick-quote the placeholder to indicate that it's an
 * identifier, for example:
 *
 * <pre>{@code
 * SafeQuery.of("SELECT * FROM `{table}`", table);
 * }</pre>
 *
 * <p>Or single-quote the placeholder to indicate that it's a string expression, as in:
 *
 * <pre>{@code
 * SafeQuery.of("SELECT * FROM Users WHERE name = '{user_name}'", user.name());
 * }</pre>
 *
 * <p>The single quotes will be elided as needed, if for example the placeholder value is sent
 * through a parameterization API; For non-parameterized templates such as {@link
 * com.google.common.labs.text.SafeQuery}, the quoted string will be auto-escaped and
 * unicode-encoded.
 *
 * <p>Alternatively you can wrap it inside TrustedSqlString, which is then embedded directly into
 * the template.
 */
@Documented
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface TemplateStringArgsMustBeQuoted {}
