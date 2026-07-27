/**
 * Collects and highlights real person display names inside meeting note text.
 */

const SKIP_NAMES = new Set(["unknown", "unassigned", "n/a", "na", "—", "-", "–"]);

export type NameSegment = { text: string; isName: boolean };

/** Deduplicates display names; longest first so "Murat Sancar" wins over "Murat". */
export function collectPersonNames(
  sources: Array<string | null | undefined> | Iterable<string | null | undefined>,
): string[] {
  const byLower = new Map<string, string>();
  for (const raw of sources) {
    const name = normalizePersonName(raw);
    if (!name) continue;
    const key = name.toLocaleLowerCase("tr-TR");
    const existing = byLower.get(key);
    if (!existing || name.length > existing.length) {
      byLower.set(key, name);
    }
  }
  return [...byLower.values()].sort((a, b) => b.length - a.length || a.localeCompare(b, "tr"));
}

export function normalizePersonName(raw: string | null | undefined): string | null {
  if (raw == null) return null;
  const name = raw.trim().replace(/\s+/gu, " ");
  if (name.length < 2) return null;
  if (SKIP_NAMES.has(name.toLocaleLowerCase("tr-TR"))) return null;
  if (name.includes("@")) return null;
  if (/^\d+$/u.test(name)) return null;
  return name;
}

export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, "\\$&");
}

/**
 * Splits text into plain / name segments. Matching is case-insensitive and
 * respects Unicode letter boundaries so names are not glued to adjacent words.
 */
export function splitByPersonNames(text: string, names: string[]): NameSegment[] {
  if (!text) return [];
  if (names.length === 0) return [{ text, isName: false }];

  const unique = collectPersonNames(names);
  if (unique.length === 0) return [{ text, isName: false }];

  const pattern = unique.map(escapeRegExp).join("|");
  const re = new RegExp(`(?<![\\p{L}\\p{N}_])(${pattern})(?![\\p{L}\\p{N}_])`, "giu");

  const segments: NameSegment[] = [];
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = re.exec(text)) !== null) {
    const start = match.index;
    const matched = match[1] ?? match[0];
    if (start > last) {
      segments.push({ text: text.slice(last, start), isName: false });
    }
    segments.push({ text: matched, isName: true });
    last = start + matched.length;
    // Avoid zero-length loops if the engine allows empty matches.
    if (matched.length === 0) re.lastIndex += 1;
  }
  if (last < text.length) {
    segments.push({ text: text.slice(last), isName: false });
  }
  return segments.length ? segments : [{ text, isName: false }];
}
