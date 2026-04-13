package com.matoe;

import com.embabel.agent.core.AgentPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Smoke test: verify the Spring context starts and all bean wiring resolves.
 *
 * {@code AgentPlatform} is mocked because its real bean creation chain
 * ({@code DefaultAgentPlatform → ChatClientLlmOperations → ModelProvider →
 * AnthropicModelsConfig}) requires live LLM provider API keys. In CI and
 * local tests, those keys are not available; the {@code IllegalStateException}
 * from {@code AnthropicModelsConfig} cascades and fails the entire context.
 * TravelService already handles a null/mocked AgentPlatform gracefully via
 * the virtual-thread fallback path.
 */
@SpringBootTest
class MATOEApplicationTest {

    @MockBean
    AgentPlatform agentPlatform;

    @Test
    void contextLoads() {
        // Verify the Spring context starts without errors
    }
}
