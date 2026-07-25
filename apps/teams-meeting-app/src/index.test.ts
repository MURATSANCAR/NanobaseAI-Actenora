import assert from "node:assert/strict";
import test from "node:test";
import { assertDefined } from "@actenora/test-support";
import { assertBackendToken, buildAuthHeaders } from "./auth/tokenGate.js";
import { DuplicateClickGuard } from "./idempotency/clickGuard.js";
import { OfflineRetryQueue } from "./offline/retryQueue.js";
import { buildSurfaceViewModel, renderSurfaceHtml } from "./surfaces/surfaces.js";
import { createAppServer } from "./index.js";

test("health endpoint responds UP", async () => {
  const server = createAppServer();
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const port = assertDefined(address.port);
  const response = await fetch(`http://127.0.0.1:${port}/health`);
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.status, "UP");
  await new Promise<void>((resolve, reject) => server.close((err) => (err ? reject(err) : resolve())));
});

test("surfaces are served for details tab, side panel, and chat tab", async () => {
  const server = createAppServer();
  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const port = assertDefined(address.port);

  for (const path of ["/surfaces/details-tab", "/surfaces/side-panel", "/surfaces/chat-tab"]) {
    const response = await fetch(`http://127.0.0.1:${port}${path}?meetingId=m-1`);
    const html = await response.text();
    assert.equal(response.status, 200);
    assert.match(html, /data-meeting-id="m-1"/);
  }

  await new Promise<void>((resolve, reject) => server.close((err) => (err ? reject(err) : resolve())));
});

test("teams context alone is rejected without backend token", () => {
  assert.throws(() => assertBackendToken(undefined), /INVALID_MEETING_APP_TOKEN/);
  assert.throws(() => assertBackendToken(""), /INVALID_MEETING_APP_TOKEN/);
});

test("duplicate click reuses the same idempotency key", () => {
  const guard = new DuplicateClickGuard();
  const first = guard.begin("marker:decision");
  const second = guard.begin("marker:decision");
  assert.equal(first, second);
  guard.release("marker:decision");
  const third = guard.begin("marker:decision");
  assert.notEqual(first, third);
});

test("offline retry queues and flushes marker creation", async () => {
  const sent: string[] = [];
  const queue = new OfflineRetryQueue(async (op) => {
    if (op.kind === "create-marker") {
      sent.push(op.idempotencyKey);
    }
  });

  queue.setOnline(false);
  const queued = await queue.enqueue({
    kind: "create-marker",
    meetingId: "m1",
    type: "DECISION",
    body: "Ship",
    idempotencyKey: "idem-1",
  });
  assert.equal(queued, "queued");
  assert.equal(queue.pending().length, 1);

  queue.setOnline(true);
  const flushed = await queue.flush();
  assert.equal(flushed, 1);
  assert.deepEqual(sent, ["idem-1"]);
  assert.equal(queue.pending().length, 0);
});

test("offline retry retries after failed dispatch", async () => {
  let failOnce = true;
  const queue = new OfflineRetryQueue(async () => {
    if (failOnce) {
      failOnce = false;
      throw new Error("network");
    }
  });

  const result = await queue.enqueue({
    kind: "update-agenda",
    meetingId: "m1",
    items: ["A"],
    expectedVersion: 0,
    idempotencyKey: "agenda-1",
  });
  assert.equal(result, "queued");
  assert.equal(await queue.flush(), 1);
});

test("auth headers never omit bearer token", () => {
  const headers = buildAuthHeaders("tok-123", {
    teamsMeetingId: "tm",
    claimedTenantId: "tenant",
    claimedUserId: "user",
  });
  assert.equal(headers.Authorization, "Bearer tok-123");
  assert.equal(headers["X-Teams-Meeting-Id"], "tm");
});

test("surface view models include required collaboration sections", () => {
  const details = buildSurfaceViewModel("details-tab", "m1");
  assert.ok(details.sections.includes("agenda"));
  assert.ok(details.sections.includes("private-note"));
  assert.ok(details.sections.includes("markers"));

  const html = renderSurfaceHtml(details);
  assert.match(html, /Meeting Details/);
  assert.match(html, /data-section="agenda"/);
});
