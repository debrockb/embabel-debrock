package com.matoe.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documentation-only marker mirroring Embabel's {@code @Agent} contract
 * without requiring the Embabel starter on the classpath. See the TODO in
 * {@code build.gradle.kts} explaining why the Embabel dependencies are
 * currently disabled (Spring Boot 3.2.4 ASM incompatibility with Embabel
 * 0.3.5's compiled jars).
 *
 * <p>When the Embabel starter is re-enabled, swap these imports back to
 * {@code com.embabel.agent.api.annotation.Agent} without further changes —
 * the attribute names here match Embabel's exactly.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Agent {
    String name();
    String description() default "";
}
