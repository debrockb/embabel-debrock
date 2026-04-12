package com.matoe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests LlmService routing logic, model resolution, JSON extraction, and prefix stripping.
 * Uses package-private access to test helper methods directly (no reflection needed).
 */
class LlmServiceTest {

    private LlmService llmService;

    @BeforeEach
    void setUp() {
        // Construct LlmService with a real WebClient builder and ObjectMapper.
        // We only test the helper methods — no actual HTTP calls are made.
        llmService = new LlmService(WebClient.builder(), new ObjectMapper());
    }

    // ── resolveAnthropicModel ────────────────────────────────────────────────

    @Test
    void resolveAnthropicModel_opusLatest() {
        String result = llmService.resolveAnthropicModel("anthropic/claude-opus-4-6");
        assertEquals("claude-opus-4-6", result);
    }

    @Test
    void resolveAnthropicModel_sonnetLatest() {
        String result = llmService.resolveAnthropicModel("anthropic/claude-sonnet-4-6");
        assertEquals("claude-sonnet-4-6", result);
    }

    @Test
    void resolveAnthropicModel_35sonnet() {
        String result = llmService.resolveAnthropicModel("anthropic/claude-3-5-sonnet");
        assertEquals("claude-3-5-sonnet-20241022", result);
    }

    @Test
    void resolveAnthropicModel_haiku45() {
        String result = llmService.resolveAnthropicModel("anthropic/claude-haiku-4-5");
        assertEquals("claude-haiku-4-5-20251001", result);
    }

    @Test
    void resolveAnthropicModel_3opus() {
        String result = llmService.resolveAnthropicModel("anthropic/claude-3-opus");
        assertEquals("claude-3-opus-20240229", result);
    }

    @Test
    void resolveAnthropicModel_3haiku() {
        String result = llmService.resolveAnthropicModel("anthropic/claude-3-haiku");
        assertEquals("claude-3-haiku-20240307", result);
    }

    @Test
    void resolveAnthropicModel_caseInsensitive() {
        String result = llmService.resolveAnthropicModel("ANTHROPIC/CLAUDE-OPUS-4-6");
        assertEquals("claude-opus-4-6", result);
    }

    // ── extractJson ──────────────────────────────────────────────────────────

    @Test
    void extractJson_withMarkdownCodeFence() {
        String input = "```json\n[{\"name\":\"test\"}]\n```";
        String result = llmService.extractJson(input);
        assertEquals("[{\"name\":\"test\"}]", result);
    }

    @Test
    void extractJson_withCodeFenceNoLanguageTag() {
        String input = "```\n{\"key\":\"value\"}\n```";
        String result = llmService.extractJson(input);
        assertEquals("{\"key\":\"value\"}", result);
    }

    @Test
    void extractJson_rawJsonNoFences() {
        String input = "[{\"name\":\"test\"}]";
        String result = llmService.extractJson(input);
        assertEquals("[{\"name\":\"test\"}]", result);
    }

    @Test
    void extractJson_rawJsonWithWhitespace() {
        String input = "  {\"key\": \"value\"}  ";
        String result = llmService.extractJson(input);
        assertEquals("{\"key\": \"value\"}", result);
    }

    @Test
    void extractJson_nullInput() {
        String result = llmService.extractJson(null);
        assertNull(result);
    }

    @Test
    void extractJson_withSurroundingText() {
        String input = "Here is the result:\n```json\n{\"result\": true}\n```\nDone.";
        String result = llmService.extractJson(input);
        assertEquals("{\"result\": true}", result);
    }

    // ── stripPrefix ──────────────────────────────────────────────────────────

    @Test
    void stripPrefix_lmstudio() {
        String result = llmService.stripPrefix("lmstudio/llama-3-8b");
        assertEquals("llama-3-8b", result);
    }

    @Test
    void stripPrefix_ollama() {
        String result = llmService.stripPrefix("ollama/mistral");
        assertEquals("mistral", result);
    }

    @Test
    void stripPrefix_anthropic() {
        String result = llmService.stripPrefix("anthropic/claude-3-5-sonnet");
        assertEquals("claude-3-5-sonnet", result);
    }

    @Test
    void stripPrefix_noSlash() {
        String result = llmService.stripPrefix("llama-3-8b");
        assertEquals("llama-3-8b", result);
    }

    @Test
    void stripPrefix_openRouter_multiSlash() {
        // "openrouter/openai/gpt-4o" — stripPrefix returns "openai/gpt-4o"
        String result = llmService.stripPrefix("openrouter/openai/gpt-4o");
        assertEquals("openai/gpt-4o", result);
    }
}
