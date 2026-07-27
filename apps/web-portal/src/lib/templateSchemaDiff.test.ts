import assert from "node:assert/strict";
import test from "node:test";
import type { DesignComponent } from "../types/template.ts";
import { diffDesignSchemas, meaningfulDiffEntries } from "./templateSchemaDiff.ts";

function comp(
  id: string,
  type: DesignComponent["type"],
  order: number,
  props: Record<string, string> = {},
): DesignComponent {
  return { id, type, order, props };
}

test("diffDesignSchemas detects added and removed components", () => {
  const left = [comp("a", "HEADER", 1), comp("b", "AGENDA", 2)];
  const right = [comp("a", "HEADER", 1), comp("c", "DECISIONS", 2)];
  const diff = meaningfulDiffEntries(diffDesignSchemas(left, right));
  assert.ok(diff.some((e) => e.id === "b" && e.kind === "removed"));
  assert.ok(diff.some((e) => e.id === "c" && e.kind === "added"));
});

test("diffDesignSchemas detects moved and changed components", () => {
  const left = [comp("a", "HEADER", 1), comp("b", "AGENDA", 2, { title: "Old" })];
  const right = [comp("b", "AGENDA", 1, { title: "New" }), comp("a", "HEADER", 2)];
  const diff = meaningfulDiffEntries(diffDesignSchemas(left, right));
  const moved = diff.find((e) => e.id === "a");
  const changed = diff.find((e) => e.id === "b");
  assert.equal(moved?.kind, "moved");
  assert.equal(moved?.fromOrder, 1);
  assert.equal(moved?.order, 2);
  assert.equal(changed?.kind, "changed");
  assert.equal(changed?.order, 1);
});

test("diffDesignSchemas marks identical schemas as unchanged only", () => {
  const schema = [comp("a", "HEADER", 1)];
  const diff = diffDesignSchemas(schema, schema);
  assert.equal(meaningfulDiffEntries(diff).length, 0);
  assert.equal(diff[0]?.kind, "unchanged");
});
