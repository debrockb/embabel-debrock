package com.matoe.annotations;

import java.lang.annotation.*;

/**
 * Marks a method as achieving a previously declared goal.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AchievesGoal {
    /**
     * The goal that this method achieves (e.g., "Plan an unforgettable trip").
     */
    String goal() default "";
}
