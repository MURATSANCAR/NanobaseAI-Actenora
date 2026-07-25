import assert from "node:assert/strict";
import test from "node:test";
import { assertDefined } from "./index.ts";

test("assertDefined returns value", () => {
  assert.equal(assertDefined("ok"), "ok");
});

test("assertDefined throws on null", () => {
  assert.throws(() => assertDefined(null, "x"), /x must be defined/);
});
