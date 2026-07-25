import assert from "node:assert/strict";
import test from "node:test";
import {
  REDACTED,
  formatLog,
  PIPELINE_STAGES,
  METRIC_NAMES,
} from "./index.ts";

test("formatLog includes service and level", () => {
  const line = formatLog("web-portal", "INFO", "ready", { port: 3000 });
  const parsed = JSON.parse(line);
  assert.equal(parsed.service, "web-portal");
  assert.equal(parsed.level, "INFO");
  assert.equal(parsed.port, 3000);
});

test("formatLog redacts secrets and transcript", () => {
  const snippet = "Confidential board transcript verbatim";
  const line = formatLog("ops", "INFO", "event", {
    apiKey: "sk-secret",
    transcript: snippet,
    correlationId: "c-1",
  });
  assert.equal(line.includes("sk-secret"), false);
  assert.equal(line.includes(snippet), false);
  assert.equal(line.includes(REDACTED), true);
  const parsed = JSON.parse(line);
  assert.equal(parsed.correlationId, "c-1");
});

test("pipeline stages match FAZ 25 order", () => {
  assert.equal(PIPELINE_STAGES[0], "MeetingDiscovered");
  assert.equal(PIPELINE_STAGES.at(-1), "Delivered");
  assert.equal(PIPELINE_STAGES.length, 11);
});

test("metric names are stable", () => {
  assert.equal(METRIC_NAMES.dlq, "actenora.messaging.dlq_depth");
  assert.equal(METRIC_NAMES.meetingCount, "actenora.meeting.count");
});
