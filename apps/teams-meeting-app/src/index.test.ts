import assert from "node:assert/strict";
import test from "node:test";
import { assertDefined } from "@actenora/test-support";
import { createAppServer } from "./index.ts";

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
