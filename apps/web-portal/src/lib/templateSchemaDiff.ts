import type { DesignComponent } from "@/types/template";

export type SchemaDiffKind = "added" | "removed" | "moved" | "changed" | "unchanged";
export type MeaningfulSchemaDiffKind = Exclude<SchemaDiffKind, "unchanged">;

export interface SchemaDiffEntry {
  id: string;
  kind: SchemaDiffKind;
  type: string;
  /** Present when kind is moved — previous order in the left schema. */
  fromOrder?: number;
  /** Order in the right schema (or left when removed). */
  order: number;
}

export type MeaningfulSchemaDiffEntry = SchemaDiffEntry & { kind: MeaningfulSchemaDiffKind };

function propsEqual(a: Record<string, string>, b: Record<string, string>): boolean {
  const aKeys = Object.keys(a);
  const bKeys = Object.keys(b);
  if (aKeys.length !== bKeys.length) return false;
  return aKeys.every((key) => a[key] === b[key]);
}

/**
 * Compares two design schemas by component id.
 * Left = baseline, right = compared version.
 */
export function diffDesignSchemas(
  left: DesignComponent[],
  right: DesignComponent[],
): SchemaDiffEntry[] {
  const leftById = new Map(left.map((c) => [c.id, c]));
  const rightById = new Map(right.map((c) => [c.id, c]));
  const entries: SchemaDiffEntry[] = [];

  for (const c of left) {
    if (!rightById.has(c.id)) {
      entries.push({ id: c.id, kind: "removed", type: c.type, order: c.order });
    }
  }

  for (const c of right) {
    const prev = leftById.get(c.id);
    if (!prev) {
      entries.push({ id: c.id, kind: "added", type: c.type, order: c.order });
      continue;
    }
    const typeOrPropsChanged = prev.type !== c.type || !propsEqual(prev.props, c.props);
    const orderChanged = prev.order !== c.order;
    if (typeOrPropsChanged) {
      entries.push({
        id: c.id,
        kind: "changed",
        type: c.type,
        order: c.order,
        fromOrder: orderChanged ? prev.order : undefined,
      });
    } else if (orderChanged) {
      entries.push({
        id: c.id,
        kind: "moved",
        type: c.type,
        order: c.order,
        fromOrder: prev.order,
      });
    } else {
      entries.push({ id: c.id, kind: "unchanged", type: c.type, order: c.order });
    }
  }

  return entries.sort((a, b) => a.order - b.order || a.id.localeCompare(b.id));
}

/** Entries that represent an actual difference (excludes unchanged). */
export function meaningfulDiffEntries(entries: SchemaDiffEntry[]): MeaningfulSchemaDiffEntry[] {
  return entries.filter((e): e is MeaningfulSchemaDiffEntry => e.kind !== "unchanged");
}
