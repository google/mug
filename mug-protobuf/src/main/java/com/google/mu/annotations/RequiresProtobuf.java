package com.google.mu.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that the annotated class needs Protobuf dependency and is part of the mug-protobuf artifact.
 *
 * @since 8.0
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Documented
public @interface RequiresProtobuf {}
