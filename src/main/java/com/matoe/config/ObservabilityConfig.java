package com.matoe.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@code @Observed} annotation processing via Micrometer's AOP aspect.
 * Combined with the Micrometer → OpenTelemetry bridge and the Zipkin exporter,
 * this produces distributed traces for every annotated method (LLM calls,
 * browser dispatches, agent actions, trip orchestration).
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }
}
