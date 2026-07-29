import type { TemplateComponentType } from "@/types/template";
import { sanitizeProductCopy } from "@/lib/brandSanitize";

export type MinutesSectionKind = "paragraph" | "list";

/** Template sections plus presentation-only minutes blocks (not Template Studio components). */
export type MinutesSectionType =
  | TemplateComponentType
  | "PROPOSALS"
  | "ISSUES"
  | "NEXT_CHECKPOINT";

export interface MinutesSection {
  type: MinutesSectionType;
  kind: MinutesSectionKind;
  /** Raw section body (paragraph text or newline-joined list items). */
  value: string;
}

export interface MinutesDocument {
  title: string;
  statusLabel: string;
  sections: MinutesSection[];
}

const SECTION_SPECS: Array<{
  type: MinutesSectionType;
  kind: MinutesSectionKind;
  headings: string[];
}> = [
  {
    type: "EXECUTIVE_SUMMARY",
    kind: "paragraph",
    headings: ["YÖNETİCİ ÖZETİ", "YONETICI OZETI", "EXECUTIVE SUMMARY"],
  },
  {
    type: "AGENDA",
    kind: "list",
    headings: ["GÜNDEM", "GUNDEM", "AGENDA"],
  },
  {
    type: "DECISIONS",
    kind: "list",
    headings: ["ALINAN KARARLAR", "KARARLAR", "DECISIONS"],
  },
  {
    type: "ACTIONS",
    kind: "list",
    headings: ["AKSİYON MADDELERİ", "AKSIYON MADDELERI", "ACTIONS", "ACTION ITEMS"],
  },
  {
    type: "RISKS",
    kind: "list",
    headings: ["RİSKLER", "RISKLER", "RISKS"],
  },
  {
    type: "COMMITMENTS",
    kind: "list",
    headings: ["TAAHHÜTLER", "TAAHHUTLER", "COMMITMENTS"],
  },
  {
    type: "OPEN_QUESTIONS",
    kind: "list",
    headings: ["AÇIK SORULAR", "ACIK SORULAR", "OPEN QUESTIONS"],
  },
  {
    type: "ISSUES",
    kind: "list",
    headings: ["SORUNLAR", "ISSUES"],
  },
  {
    type: "PROPOSALS",
    kind: "list",
    headings: [
      "ÖNERİLER",
      "ONERILER",
      "ÖNERİLER — HENÜZ KARAR DEĞİL",
      "ONERILER HENUZ KARAR DEGIL",
      "PROPOSALS",
    ],
  },
  {
    type: "NEXT_CHECKPOINT",
    kind: "list",
    headings: [
      "BİR SONRAKİ KONTROL",
      "BIR SONRAKI KONTROL",
      "ÖNEMLİ BULGULAR",
      "ONEMLI BULGULAR",
      "NEXT STEPS",
      "IMPORTANT FACTS",
    ],
  },
];

const HEADING_RE =
  /^\s*(\d+)\.\s+(.+?)\s*$/u;

const CORPORATE_ID_RE = /^(K|A|R)-(\d{2})\s*[—–-]\s*(.+)$/u;

function normalizeHeading(raw: string): string {
  return raw
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toUpperCase()
    .replace(/\s+/gu, " ")
    .trim();
}

/** Splits `K-01 — body` presentation ids from list lines. */
export function parseCorporateItemId(item: string): {
  id?: string;
  text: string;
} {
  const m = CORPORATE_ID_RE.exec(item.trim());
  if (!m) return { text: item.trim() };
  return {
    id: `${m[1]}-${m[2]}`,
    text: (m[3] ?? "").trim(),
  };
}

/** Splits action lines like `Task (Sorumlu: Ada, Son tarih: —)` into display parts. */
export function parseActionMeta(item: string): {
  id?: string;
  text: string;
  owner?: string;
  due?: string;
} {
  const { id, text: withoutId } = parseCorporateItemId(item);
  const m =
    /^(.+?)\s*\((?:Sorumlu|Owner):\s*([^,)]+)\s*,\s*(?:Son tarih|Due):\s*([^)]+)\)\s*$/iu.exec(
      withoutId.trim(),
    );
  if (!m) return { id, text: withoutId.trim() };
  return {
    id,
    text: (m[1] ?? "").trim(),
    owner: (m[2] ?? "").trim(),
    due: (m[3] ?? "").trim(),
  };
}

/** User-facing review reasons only — successful consistency drops are not review. */
export function reviewBannerReasons(qualityFlags: string[]): string[] {
  const reasons: string[] = [];
  const seen = new Set<string>();
  for (const raw of qualityFlags) {
    const f = raw.trim().toUpperCase();
    if (!f || seen.has(f)) continue;
    if (f.includes("UNRESOLVED_DECISION_PROPOSAL_CONFLICT")) {
      seen.add(f);
      reasons.push("UNRESOLVED_DECISION_PROPOSAL_CONFLICT");
      continue;
    }
    if (f === "REQUIRES_MANUAL_REVIEW" || f === "CONSISTENCY_AUDIT_NEEDS_REVIEW") {
      seen.add(f);
      reasons.push(f);
      continue;
    }
    if (
      f === "NEEDS_REVIEW" ||
      f === "LOW_CONFIDENCE" ||
      f === "SYNTHESIS_FALLBACK" ||
      f === "AUDIT_FALLBACK"
    ) {
      seen.add(f);
      reasons.push(f);
    }
  }
  return reasons;
}

export function hasSubsumedProposalDrop(qualityFlags: string[]): boolean {
  return qualityFlags.some((f) =>
    f.toUpperCase().includes("DECISION_SUBSUMED_PROPOSAL_DROPPED"),
  );
}

function matchSectionHeading(raw: string): (typeof SECTION_SPECS)[number] | null {
  const m = HEADING_RE.exec(raw);
  if (!m) return null;
  const heading = normalizeHeading(m[2] ?? "");
  return (
    SECTION_SPECS.find((spec) =>
      spec.headings.some((h) => {
        const nh = normalizeHeading(h);
        return heading === nh || heading.startsWith(nh);
      }),
    ) ?? null
  );
}

/**
 * Expands dense one-line summaries (`Gündem: a; b. 3 karar kaydedildi.`) into
 * numbered lines so sequence numbers stay with their item text.
 */
export function enhanceParagraphReadability(raw: string): string {
  const text = raw.replace(/\r\n/g, "\n").trim();
  if (!text) return text;
  if (/^(?:Gündem|Agenda)\s*:\s*\n\s*\d+\.\s+/iu.test(text)) {
    return text;
  }

  const prefix = /^(Gündem|Agenda)\s*:\s*/iu.exec(text);
  if (!prefix) return text;

  const label = prefix[1] ?? "Gündem";
  const rest = text.slice(prefix[0].length).trim();
  const countRe =
    /(\d+\s+(?:karar kaydedildi|decision\(s\) recorded|aksiyon maddesi|action item\(s\)|risk(?:\(s\))?)\.?)/giu;
  const counts: string[] = [];
  let firstCountAt = -1;
  let match: RegExpExecArray | null;
  while ((match = countRe.exec(rest)) !== null) {
    if (firstCountAt < 0) firstCountAt = match.index;
    counts.push(`${match[0].trim().replace(/\.$/u, "")}.`);
  }

  let agendaPart = (firstCountAt >= 0 ? rest.slice(0, firstCountAt) : rest).trim().replace(/\.\s*$/u, "");
  const items = agendaPart
    .split(/;|\n/u)
    .map((s) => s.replace(/^\d+\.\s+/u, "").trim())
    .filter(Boolean);

  if (items.length === 0 && counts.length === 0) return text;
  if (items.length <= 1 && counts.length === 0 && !agendaPart.includes(";")) return text;

  const lines: string[] = [`${label}:`];
  items.forEach((item, i) => lines.push(`${i + 1}. ${item}`));
  if (counts.length > 0) {
    if (items.length > 0) lines.push("");
    lines.push(...counts);
  }
  return lines.join("\n");
}

/** Splits numbered list lines (`1. item`) from free text; treats `—` / `-` as empty. */
export function parseSectionContent(
  raw: string,
  kind: MinutesSectionKind,
): { empty: boolean; paragraph: string; items: string[] } {
  const trimmed = raw.trim();
  if (!trimmed || trimmed === "—" || trimmed === "-" || trimmed === "–") {
    return { empty: true, paragraph: "", items: [] };
  }
  if (kind === "paragraph") {
    return { empty: false, paragraph: enhanceParagraphReadability(trimmed), items: [] };
  }
  const lines = trimmed.split(/\n+/).map((l) => l.trim()).filter(Boolean);
  const items: string[] = [];
  for (const line of lines) {
    if (/^Not:\s+/iu.test(line)) continue;
    const numbered = /^\d+\.\s+(.+)$/u.exec(line);
    items.push((numbered?.[1] ?? line).trim());
  }
  return { empty: items.length === 0, paragraph: "", items };
}

export function isMinutesPlainBody(body: string): boolean {
  const head = body.trim().slice(0, 80).toUpperCase();
  return head.includes("TOPLANTI TUTANA") || head.includes("MEETING MINUTES");
}

export function parseMinutesBody(body: string, fallbackTitle = ""): MinutesDocument | null {
  if (!isMinutesPlainBody(body)) return null;
  const lines = body.replace(/\r\n/g, "\n").split("\n");
  let title = fallbackTitle.trim();
  let statusLabel = "";
  const buckets = new Map<MinutesSectionType, string[]>();
  let current: (typeof SECTION_SPECS)[number] | null = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) {
      // Keep blank lines inside paragraph sections so Enter creates real breaks.
      if (current?.kind === "paragraph") {
        buckets.get(current.type)!.push("");
      }
      continue;
    }

    const upper = trimmed.toUpperCase();
    if (upper.startsWith("TOPLANTI TUTANA") || upper.startsWith("MEETING MINUTES")) {
      continue;
    }

    const titleMatch =
      /^Toplant[ıi] Başlığı:\s*(.+)$/iu.exec(trimmed) ??
      /^Meeting Title:\s*(.+)$/iu.exec(trimmed);
    if (titleMatch) {
      title = (titleMatch[1] ?? "").trim();
      continue;
    }

    const statusMatch =
      /^Durum:\s*(.+)$/iu.exec(trimmed) ?? /^Status:\s*(.+)$/iu.exec(trimmed);
    if (statusMatch) {
      statusLabel = sanitizeProductCopy((statusMatch[1] ?? "").trim());
      continue;
    }

    const section = matchSectionHeading(trimmed);
    if (section) {
      current = section;
      if (!buckets.has(section.type)) buckets.set(section.type, []);
      continue;
    }

    if (current) {
      buckets.get(current.type)!.push(trimmed);
    }
  }

  const sections: MinutesSection[] = SECTION_SPECS.map((spec) => {
    const linesForSection = buckets.get(spec.type) ?? [];
    const value =
      spec.kind === "paragraph"
        ? linesForSection.join("\n").replace(/^\n+|\n+$/g, "")
        : linesForSection.join("\n");
    return {
      type: spec.type,
      kind: spec.kind,
      value,
    };
  });

  return { title, statusLabel, sections };
}

export function serializeMinutesBody(doc: MinutesDocument): string {
  const lines: string[] = ["TOPLANTI TUTANAĞI"];
  if (doc.title.trim()) {
    lines.push(`Toplantı Başlığı: ${doc.title.trim()}`);
  }
  if (doc.statusLabel.trim()) {
    lines.push(`Durum: ${doc.statusLabel.trim()}`);
  }
  lines.push("");

  const headings: Partial<Record<MinutesSectionType, string>> = {
    EXECUTIVE_SUMMARY: "1. YÖNETİCİ ÖZETİ",
    AGENDA: "2. GÜNDEM",
    DECISIONS: "3. ALINAN KARARLAR",
    ACTIONS: "4. AKSİYON MADDELERİ",
    RISKS: "5. RİSKLER",
    COMMITMENTS: "6. TAAHHÜTLER",
    OPEN_QUESTIONS: "7. AÇIK SORULAR",
    ISSUES: "8. SORUNLAR",
    PROPOSALS: "9. ÖNERİLER — HENÜZ KARAR DEĞİL",
    NEXT_CHECKPOINT: "10. BİR SONRAKİ KONTROL",
  };

  let index = 1;
  for (const section of doc.sections) {
    const heading = headings[section.type];
    if (!heading) continue;
    const labeled = heading.replace(/^\d+\./u, `${index}.`);
    index += 1;
    lines.push(labeled);
    const parsed = parseSectionContent(section.value, section.kind);
    if (parsed.empty) {
      lines.push("—");
    } else if (section.kind === "paragraph") {
      lines.push(parsed.paragraph);
    } else {
      parsed.items.forEach((item, i) => lines.push(`${i + 1}. ${item}`));
    }
    lines.push("");
  }

  return lines.join("\n").trim();
}

export function sectionsFromTemplateValues(
  values: Partial<Record<TemplateComponentType, string>>,
  order: TemplateComponentType[],
): MinutesSection[] {
  return order.map((type) => {
    const spec = SECTION_SPECS.find((s) => s.type === type);
    return {
      type,
      kind: spec?.kind ?? "paragraph",
      value: values[type] ?? "",
    };
  });
}
