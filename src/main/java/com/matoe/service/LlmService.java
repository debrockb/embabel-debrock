package com.matoe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Routes LLM calls by model-string prefix:
 *   "anthropic/*"  → Anthropic Messages API
 *   "lmstudio/*"   → LM Studio  (OpenAI-compatible, local)
 *   "ollama/*"     → Ollama      (OpenAI-compatible, local)
 *   "openrouter/*" → OpenRouter  (OpenAI-compatible, cloud proxy)
 *
 * Never hardcodes a model — every call passes the model string from config or request.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*(.+?)\\s*```", Pattern.DOTALL);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    @Value("${anthropic.api.model:claude-3-5-sonnet-20241022}")
    private String defaultAnthropicModel;

    @Value("${anthropic.api.base-url:https://api.anthropic.com}")
    private String anthropicBaseUrl;

    @Value("${anthropic.api.version:2023-06-01}")
    private String anthropicVersion;

    @Value("${lmstudio.base-url:http://localhost:1234/v1}")
    private String lmStudioUrl;

    @Value("${ollama.base-url:http://localhost:11434/v1}")
    private String ollamaUrl;

    @Value("${openrouter.api-key:}")
    private String openRouterApiKey;

    @Value("${openrouter.base-url:https://openrouter.ai/api/v1}")
    private String openRouterBaseUrl;

    public LlmService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Call the appropriate LLM based on the model-string prefix.
     *
     * @param modelString e.g. "anthropic/claude-3-5-sonnet", "lmstudio/llama-3-8b",
     *                    "ollama/mistral", "openrouter/openai/gpt-4o"
     */
    public String call(String modelString, String systemPrompt, String userPrompt) {
        if (modelString == null || modelString.isBlank()) {
            modelString = "anthropic/" + defaultAnthropicModel;
        }

        if (modelString.startsWith("anthropic/")) {
            return callAnthropic(resolveAnthropicModel(modelString), systemPrompt, userPrompt);
        } else if (modelString.startsWith("lmstudio/")) {
            return callOpenAiCompatible(
                stripPrefix(modelString), lmStudioUrl, null,
                systemPrompt, userPrompt, "LM Studio"
            );
        } else if (modelString.startsWith("ollama/")) {
            return callOpenAiCompatible(
                stripPrefix(modelString), ollamaUrl, null,
                systemPrompt, userPrompt, "Ollama"
            );
        } else if (modelString.startsWith("openrouter/")) {
            // "openrouter/openai/gpt-4o" → model = "openai/gpt-4o"
            String model = modelString.substring("openrouter/".length());
            return callOpenAiCompatible(
                model, openRouterBaseUrl, openRouterApiKey,
                systemPrompt, userPrompt, "OpenRouter"
            );
        }

        log.warn("Unrecognised model prefix '{}', falling back to Anthropic default", modelString);
        return callAnthropic(defaultAnthropicModel, systemPrompt, userPrompt);
    }

    // ── provider implementations ──────────────────────────────────────────────

    private String callAnthropic(String model, String systemPrompt, String userPrompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", 4096);
            body.put("system", systemPrompt != null ? systemPrompt : "");
            body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));

            log.debug("Calling Anthropic with model={}", model);

            Map<?, ?> response = webClient.post()
                .uri(anthropicBaseUrl + "/v1/messages")
                .header("x-api-key", anthropicApiKey)
                .header("anthropic-version", anthropicVersion)
                .header("content-type", "application/json")
                .bodyValue(body)
                .retrieve()
                .onStatus(
                    status -> !status.is2xxSuccessful(),
                    r -> r.bodyToMono(String.class)
                          .flatMap(err -> Mono.error(new RuntimeException("Anthropic error: " + err)))
                )
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(120));

            if (response == null) throw new RuntimeException("Null response from Anthropic");
            List<?> content = (List<?>) response.get("content");
            if (content == null || content.isEmpty()) throw new RuntimeException("Empty content from Anthropic");
            String text = (String) ((Map<?, ?>) content.get(0)).get("text");
            if (text == null) throw new RuntimeException("No text in Anthropic content block");

            log.debug("Anthropic responded ({} chars)", text.length());
            return text;

        } catch (Exception e) {
            log.error("Anthropic call failed: {}", e.getMessage());
            throw new RuntimeException("Anthropic LLM call failed: " + e.getMessage(), e);
        }
    }

    private String callOpenAiCompatible(
            String model, String baseUrl, String apiKey,
            String systemPrompt, String userPrompt, String label) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                Map.of("role", "user",   "content", userPrompt != null ? userPrompt : "")
            ));
            body.put("max_tokens", 4096);
            body.put("temperature", 0.7);

            log.debug("Calling {} at {} with model={}", label, baseUrl, model);

            var req = webClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("content-type", "application/json");

            if (apiKey != null && !apiKey.isBlank()) {
                req = req.header("authorization", "Bearer " + apiKey);
            }

            Map<?, ?> response = req
                .bodyValue(body)
                .retrieve()
                .onStatus(
                    status -> !status.is2xxSuccessful(),
                    r -> r.bodyToMono(String.class)
                          .flatMap(err -> Mono.error(new RuntimeException(label + " error: " + err)))
                )
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(120));

            if (response == null) throw new RuntimeException("Null response from " + label);
            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) throw new RuntimeException("Empty choices from " + label);
            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            if (message == null) throw new RuntimeException("Null message in " + label + " choice");
            String text = (String) message.get("content");
            if (text == null) throw new RuntimeException("No content in " + label + " message");

            log.debug("{} responded ({} chars)", label, text.length());
            return text;

        } catch (Exception e) {
            log.error("{} call failed: {}", label, e.getMessage());
            throw new RuntimeException(label + " LLM call failed: " + e.getMessage(), e);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Resolves shorthand like "anthropic/claude-opus-4-6" → full Anthropic model ID. */
    String resolveAnthropicModel(String modelString) {
        String lower = modelString.toLowerCase();
        if (lower.contains("opus-4-6") || lower.contains("opus4"))   return "claude-opus-4-6";
        if (lower.contains("sonnet-4-6") || lower.contains("sonnet4")) return "claude-sonnet-4-6";
        if (lower.contains("haiku-4-5") || lower.contains("haiku4")) return "claude-haiku-4-5-20251001";
        if (lower.contains("3-5-sonnet")) return "claude-3-5-sonnet-20241022";
        if (lower.contains("3-opus"))     return "claude-3-opus-20240229";
        if (lower.contains("3-haiku"))    return "claude-3-haiku-20240307";
        return defaultAnthropicModel;
    }

    /** Strips "provider/" prefix from a model string. */
    String stripPrefix(String modelString) {
        int slash = modelString.indexOf('/');
        return slash >= 0 ? modelString.substring(slash + 1) : modelString;
    }

    /** Strips markdown code fences from a raw LLM response before JSON parsing. */
    public String extractJson(String raw) {
        if (raw == null) return null;
        Matcher m = JSON_FENCE.matcher(raw);
        return m.find() ? m.group(1).trim() : raw.trim();
    }

    /** Parse JSON string into a typed object via Jackson. */
    public <T> T parseJson(String json, Class<T> targetClass) throws Exception {
        return objectMapper.readValue(json, targetClass);
    }
}
