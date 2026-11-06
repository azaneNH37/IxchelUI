package com.azane.ixchel.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type a host mod may build on. Java makes every type used across packages {@code public},
 * so being public says nothing; only marked types are part of the library's contract with hosts, and
 * only they go into the published Javadoc. A marked type's public members are its API; types its
 * signatures mention without the mark are visible but not promised.
 */
@HostApi
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface HostApi {
}
