import { describe, expect, it } from "vitest";
import type { DesignComponent } from "@/types/template";
import { diffDesignSchemas, meaningfulDiffEntries } from "./templateSchemaDiff";

function comp(
  id: string,
  type: DesignComponent["type"],
  order: number,
  props: Record<string, string> = {},
): DesignComponent {
  return { id, type, order, props };
}

describe("diffDesignSchemas", () => {
  it("detects added and removed components", () => {
    const left = [comp("a", "HEADER", 1), comp("b", "AGENDA", 2)];
    const right = [comp("a", "HEADER", 1), comp("c", "DECISIONS", 2)];
    const diff = meaningfulDiffEntries(diffDesignSchemas(left, right));
    expect(diff).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: "b", kind: "removed" }),
        expect.objectContaining({ id: "c", kind: "added" }),
      ]),
    );
  });

  it("detects moved and changed components", () => {
    const left = [comp("a", "HEADER", 1), comp("b", "AGENDA", 2, { title: "Old" })];
    const right = [comp("b", "AGENDA", 1, { title: "New" }), comp("a", "HEADER", 2)];
    const diff = meaningfulDiffEntries(diffDesignSchemas(left, right));
    expect(diff.find((e) => e.id === "a")).toMatchObject({ kind: "moved", fromOrder: 1, order: 2 });
    expect(diff.find((e) => e.id === "b")).toMatchObject({ kind: "changed", order: 1 });
  });

  it("marks identical schemas as unchanged only", () => {
    const schema = [comp("a", "HEADER", 1)];
    const diff = diffDesignSchemas(schema, schema);
    expect(meaningfulDiffEntries(diff)).toHaveLength(0);
    expect(diff[0]?.kind).toBe("unchanged");
  });
});
