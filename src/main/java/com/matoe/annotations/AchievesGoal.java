package com.matoe.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documentation-only marker mirroring Embabel's {@code @AchievesGoal}
 * contract. Marks the terminal action that produces the agent's goal
 * output. See {@link Action} for the rationale behind the local stubs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AchievesGoal {
    String description() default "";
}
