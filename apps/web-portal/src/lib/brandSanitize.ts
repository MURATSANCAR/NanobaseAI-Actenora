import { PRODUCT_BRAND } from "@/config/brand";

/**
 * Strips third-party / infra technology names from user-facing copy.
 * When a product name is required, use NanobaseAI EasyMeeting.
 */
const VENDOR_RE =
  /\b(llm|qwen(?:[\d._-]*)?|ollama|vllm|llama(?:\.?cpp)?|openai|anthropic|claude|chatgpt|gpt-?\d*|mistral|huggingface|transformers|cuda|pytorch|tensorflow)\b/gi;

const PHRASE_RE =
  /openai-compatible|open\s*ai|yapay\s*zeka\s*modeli|language\s*model/gi;

export function sanitizeProductCopy(text: string | null | undefined): string {
  if (text == null || !text.trim()) return text ?? "";
  let cleaned = text.replace(PHRASE_RE, PRODUCT_BRAND);
  cleaned = cleaned.replace(VENDOR_RE, PRODUCT_BRAND);
  // Collapse accidental duplicates: "NanobaseAI EasyMeeting EasyMeeting"
  cleaned = cleaned.replace(
    new RegExp(`(?:${escapeRegExp(PRODUCT_BRAND)}\\s*){2,}`, "gi"),
    PRODUCT_BRAND,
  );
  // Legacy "Taslak (LLM)" / "Draft (LLM)" already covered by VENDOR_RE.
  return cleaned.replace(/\s{2,}/g, " ").trim();
}

export function formatMeetingVersionLabel(version: number, label: string): string {
  const safe = sanitizeProductCopy(label);
  if (!safe) return `v${version}`;
  if (/^v\d+/i.test(safe)) return safe;
  return `v${version} — ${safe}`;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
