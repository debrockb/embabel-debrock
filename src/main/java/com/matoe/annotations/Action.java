package com.matoe.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documentation-only marker mirroring Embabel's {@code @Action} contract
 * without requiring the Embabel starter on the classpath. The Embabel GOAP
 * planner chains actions by their <b>parameter types</b> (preconditions)
 * and <b>return types</b> (effects). These semantics are preserved in the
 * {@link com.matoe.service.TravelService} virtual-thread fallback path,
 * which dispatches the same action methods in the same parallel shape.
 *
 * <p>See the note in {@code build.gradle.kts} explaining why the Embabel
 * dependencies are currently disabled.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {
    String description() default "";
    String[] pre() default {};
    String[] post() default {};
    double cost() default 1.0;
    boolean readOnly() default false;
}
