package com.matoe.annotations;

import java.lang.annotation.*;

/**
 * Marks a method as an agent action.
 * Actions are discrete tasks executed as part of the agent orchestration.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {
    /**
     * Name of the action (e.g., "search_hotels").
     */
    String name() default "";

    /**
     * Preconditions that must be met before this action executes.
     * Used for documentation; actual execution flow is via CompletableFuture in TravelService.
     */
    String[] preconditions() default {};

    /**
     * Effects that this action produces.
     * Used for documentation; actual execution flow is via CompletableFuture in TravelService.
     */
    String[] effects() default {};
}
