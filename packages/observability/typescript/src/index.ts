export type LogLevel = "DEBUG" | "INFO" | "WARN" | "ERROR";

export interface LogFields {
  [key: string]: string | number | boolean | undefined;
}

export const PIPELINE_STAGES = [
  "MeetingDiscovered",
  "MeetingEnded",
  "TranscriptFetched",
  "TranscriptNormalized",
  "AiJobRouted",
  "InferenceCompleted",
  "ValidationCompleted",
  "NoteDrafted",
  "Approved",
  "Rendered",
  "Delivered",
] as const;

export type PipelineStageName = (typeof PIPELINE_STAGES)[number];

export const METRIC_NAMES = {
  meetingCount: "actenora.meeting.count",
  transcriptPendingAge: "actenora.transcript.pending_age_seconds",
  aiQueueDepth: "actenora.ai.queue_depth",
  routeDecision: "actenora.ai.route_decision",
  queueWait: "actenora.queue.wait_seconds",
  inferenceDuration: "actenora.inference.duration_ms",
  tokens: "actenora.inference.tokens",
  invalidJson: "actenora.validation.invalid_json",
  evidenceFailure: "actenora.validation.evidence_failure",
  approvalDuration: "actenora.approval.duration_ms",
  renderDuration: "actenora.render.duration_ms",
  mailFailures: "actenora.delivery.mail_failures",
  dlq: "actenora.messaging.dlq_depth",
  tenantThroughput: "actenora.tenant.throughput",
  deploymentHealth: "actenora.deployment.health",
} as const;

const BLOCKED_KEY =
  /^(password|passwd|secret|token|access[_-]?token|refresh[_-]?token|api[_-]?key|authorization|auth|bearer|client[_-]?secret|transcript|transcript[_-]?text|raw[_-]?transcript|prompt|completion|response[_-]?body|email|ssn|credit[_-]?card)$/i;

const BEARER = /(bearer\s+)[a-z0-9._\-]+/gi;
const API_KEY = /(api[_-]?key\s*[:=]\s*)[^\s"']+/gi;
const JWT = /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g;
const EMAIL = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g;

export const REDACTED = "[REDACTED]";

export function isBlockedKey(key: string): boolean {
  const normalized = key.replace(/[-_]/g, "");
  if (BLOCKED_KEY.test(key)) {
    return true;
  }
  const lower = normalized.toLowerCase();
  return (
    lower.includes("transcript") ||
    lower.includes("password") ||
    lower.includes("secret") ||
    lower.includes("apikey") ||
    lower.endsWith("token")
  );
}

export function redactValue(key: string, value: unknown): unknown {
  if (value == null) {
    return value;
  }
  if (isBlockedKey(key)) {
    return REDACTED;
  }
  if (typeof value === "string") {
    return value
      .replace(BEARER, `$1${REDACTED}`)
      .replace(API_KEY, `$1${REDACTED}`)
      .replace(JWT, REDACTED)
      .replace(EMAIL, REDACTED);
  }
  return value;
}

export function redactFields(fields: LogFields): LogFields {
  const out: LogFields = {};
  for (const [key, value] of Object.entries(fields)) {
    out[key] = redactValue(key, value) as LogFields[string];
  }
  return out;
}

export function formatLog(
  service: string,
  level: LogLevel,
  message: string,
  fields: LogFields = {},
): string {
  const payload = {
    ts: new Date().toISOString(),
    service,
    level,
    message,
    ...redactFields(fields),
  };
  return JSON.stringify(payload);
}
