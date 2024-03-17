package com.google.mu.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Documents that the annotated class needs Guava dependency and is part of the mug-guava artifact.
 *
 * @since 8.0
 */
@Target(ElementType.TYPE)
@Documented
public @interface RequiresGuava {}
