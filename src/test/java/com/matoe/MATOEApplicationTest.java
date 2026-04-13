package com.matoe;

import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: verify our Spring wiring resolves.
 *
 * <p>{@code AgentPlatform} is mocked because Embabel's
 * {@code ConfigurableModelProvider} requires at least one LLM to be
 * discoverable at startup. In CI there is no running Ollama or LM Studio
 * instance, so the available-models list is empty and the
 * {@code modelProvider} bean fails with "Default LLM '…' not found in
 * available models: []". Lazy init prevents eager creation of Embabel's
 * internal beans; the mock satisfies any code that injects
 * {@code AgentPlatform}.
 *
 * <p>{@code TravelService} already handles a mocked/absent platform
 * gracefully via the virtual-thread fallback path.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.main.lazy-initialization=true")
class MATOEApplicationTest {

    @MockBean
    AgentPlatform agentPlatform;

    @Test
    void contextLoads() {
        // Verify the Spring context starts without errors
    }
}
