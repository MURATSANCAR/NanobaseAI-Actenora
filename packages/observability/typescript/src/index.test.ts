import assert from "node:assert/strict";
import test from "node:test";
import { formatLog } from "./index.ts";

test("formatLog includes service and level", () => {
  const line = formatLog("web-portal", "INFO", "ready", { port: 3000 });
  const parsed = JSON.parse(line);
  assert.equal(parsed.service, "web-portal");
  assert.equal(parsed.level, "INFO");
  assert.equal(parsed.port, 3000);
});
