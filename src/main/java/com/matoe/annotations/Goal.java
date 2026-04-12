package com.matoe.annotations;

import java.lang.annotation.*;

/**
 * Marks a method as declaring a goal.
 * Goals are desired end states that agents work to achieve.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Goal {
    /**
     * Description of the goal (e.g., "Plan an unforgettable trip").
     */
    String description() default "";
}
