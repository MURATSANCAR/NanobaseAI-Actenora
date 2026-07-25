import assert from "node:assert/strict";
import test from "node:test";
import { assertDefined } from "@actenora/test-support";
import { App } from "./App.tsx";

test("App export is defined", () => {
  assert.equal(typeof assertDefined(App), "function");
});
