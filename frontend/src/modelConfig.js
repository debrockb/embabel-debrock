/**
 * Editable LLM model list — persisted in localStorage, editable from Admin Dashboard.
 *
 * Each model entry:
 *   id          — unique string (UUID-ish)
 *   provider    — display group label (e.g. "Local (LM Studio)")
 *   modelId     — backend model string sent in TravelRequest (e.g. "lmstudio/qwen3.5:9b")
 *   displayName — human-readable label shown in dropdowns
 *   roles       — array of "orchestrator" | "extractor" (which dropdowns show this model)
 *   enabled     — toggle visibility in dropdowns without deleting
 */

const STORAGE_KEY = 'matoe-model-config';

let nextId = 100;
const uid = () => String(++nextId);

export const DEFAULT_MODELS = [
  // ── Local (LM Studio) ────────────────────────────────────────────────────
  { id: uid(), provider: 'Local (LM Studio)', modelId: 'lmstudio/qwen3.5:9b',          displayName: 'Qwen 3.5 9B (LM Studio)',          roles: ['orchestrator', 'extractor'], enabled: true },
  { id: uid(), provider: 'Local (LM Studio)', modelId: 'lmstudio/nemotron-3-nano:4b',   displayName: 'Nemotron 3 Nano 4B (LM Studio)',    roles: ['orchestrator', 'extractor'], enabled: true },
  { id: uid(), provider: 'Local (LM Studio)', modelId: 'lmstudio/nemotron-mini:4b',     displayName: 'Nemotron Mini 4B (LM Studio)',       roles: ['orchestrator', 'extractor'], enabled: true },
  { id: uid(), provider: 'Local (LM Studio)', modelId: 'lmstudio/qwen3.5:0.8b',        displayName: 'Qwen 3.5 0.8B (LM Studio)',         roles: ['extractor'],                 enabled: true },
  // ── Local (Ollama) ────────────────────────────────────────────────────────
  { id: uid(), provider: 'Local (Ollama)',    modelId: 'ollama/qwen3.5:9b',             displayName: 'Qwen 3.5 9B (Ollama)',              roles: ['orchestrator', 'extractor'], enabled: true },
  { id: uid(), provider: 'Local (Ollama)',    modelId: 'ollama/nemotron-3-nano:4b',      displayName: 'Nemotron 3 Nano 4B (Ollama)',       roles: ['orchestrator', 'extractor'], enabled: true },
  { id: uid(), provider: 'Local (Ollama)',    modelId: 'ollama/llama3',                  displayName: 'Llama 3 (Ollama)',                  roles: ['orchestrator', 'extractor'], enabled: false },
  { id: uid(), provider: 'Local (Ollama)',    modelId: 'ollama/mistral',                 displayName: 'Mistral (Ollama)',                  roles: ['orchestrator', 'extractor'], enabled: false },
  // ── Anthropic ─────────────────────────────────────────────────────────────
  { id: uid(), provider: 'Anthropic',         modelId: 'anthropic/claude-opus-4-6',      displayName: 'Claude Opus 4.6 (most capable)',    roles: ['orchestrator'],              enabled: true },
  { id: uid(), provider: 'Anthropic',         modelId: 'anthropic/claude-sonnet-4-6',    displayName: 'Claude Sonnet 4.6 (balanced)',      roles: ['orchestrator', 'extractor'], enabled: true },
  { id: uid(), provider: 'Anthropic',         modelId: 'anthropic/claude-haiku-4-5',     displayName: 'Claude Haiku 4.5 (fast)',           roles: ['orchestrator', 'extractor'], enabled: true },
  { id: uid(), provider: 'Anthropic',         modelId: 'anthropic/claude-3-5-sonnet-20241022', displayName: 'Claude 3.5 Sonnet',           roles: ['orchestrator', 'extractor'], enabled: true },
  // ── OpenRouter ────────────────────────────────────────────────────────────
  { id: uid(), provider: 'OpenRouter',        modelId: 'openrouter/openai/gpt-4o',      displayName: 'GPT-4o (via OpenRouter)',            roles: ['orchestrator'],              enabled: true },
  { id: uid(), provider: 'OpenRouter',        modelId: 'openrouter/google/gemini-pro-1.5', displayName: 'Gemini Pro 1.5 (via OpenRouter)', roles: ['orchestrator'],              enabled: true },
  { id: uid(), provider: 'OpenRouter',        modelId: 'openrouter/meta-llama/llama-3.3-70b-instruct', displayName: 'Llama 3.3 70B (via OpenRouter)', roles: ['orchestrator', 'extractor'], enabled: true },
];

/** Read the model list from localStorage, falling back to defaults. */
export function getModels() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch { /* corrupt storage — fall through */ }
  return DEFAULT_MODELS;
}

/** Persist the model list to localStorage. */
export function saveModels(models) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(models));
}

/** Reset to factory defaults. */
export function resetModels() {
  localStorage.removeItem(STORAGE_KEY);
  return DEFAULT_MODELS;
}

/** Get models filtered by role and enabled status, grouped by provider. */
export function getModelsForRole(role) {
  return getModels().filter((m) => m.enabled && m.roles.includes(role));
}

/** Group a flat model array by provider (preserves insertion order). */
export function groupByProvider(models) {
  const groups = {};
  for (const m of models) {
    if (!groups[m.provider]) groups[m.provider] = [];
    groups[m.provider].push(m);
  }
  return groups;
}

/** Generate a unique ID for new models. */
export function newModelId() {
  return 'custom-' + Date.now() + '-' + Math.random().toString(36).slice(2, 7);
}
