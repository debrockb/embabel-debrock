package com.matoe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: verify Spring wiring resolves without a running LLM.
 *
 * <p>Embabel's {@code ConfigurableModelProvider} eagerly discovers LLM
 * models at startup. In CI there is no running Ollama or LM Studio,
 * so the available-models list is empty and bean creation fails.
 *
 * <p>This is handled by two mechanisms:
 * <ol>
 *   <li>{@link com.matoe.config.EmbabelLazyInitConfig} — a
 *       {@code BeanFactoryPostProcessor} that marks all Embabel beans as
 *       lazy-init, so they are not created until first accessed.</li>
 *   <li>{@code spring.main.lazy-initialization=true} — belt-and-suspenders
 *       to catch any Embabel bean not covered by the post-processor.</li>
 * </ol>
 *
 * <p>{@code TravelService} resolves Embabel lazily on the first trip
 * request (not at construction) and falls back to virtual-thread dispatch
 * if the platform is unavailable.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
class MATOEApplicationTest {

    @Test
    void contextLoads() {
        // Verify the Spring context starts without errors
    }
}
