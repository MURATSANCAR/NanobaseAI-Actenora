import type { TemplateComponentType } from "@/types/template";
import { sanitizeProductCopy } from "@/lib/brandSanitize";

export type MinutesSectionKind = "paragraph" | "list";

export interface MinutesSection {
  type: TemplateComponentType;
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
  type: TemplateComponentType;
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
];

const HEADING_RE =
  /^\s*(\d+)\.\s+(.+?)\s*$/u;

function normalizeHeading(raw: string): string {
  return raw
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toUpperCase()
    .trim();
}

/** Splits action lines like `Task (Sorumlu: Ada, Son tarih: —)` into display parts. */
export function parseActionMeta(item: string): {
  text: string;
  owner?: string;
  due?: string;
} {
  const m =
    /^(.+?)\s*\((?:Sorumlu|Owner):\s*([^,)]+)\s*,\s*(?:Son tarih|Due):\s*([^)]+)\)\s*$/iu.exec(
      item.trim(),
    );
  if (!m) return { text: item.trim() };
  return {
    text: (m[1] ?? "").trim(),
    owner: (m[2] ?? "").trim(),
    due: (m[3] ?? "").trim(),
  };
}

function matchSectionHeading(raw: string): (typeof SECTION_SPECS)[number] | null {
  const m = HEADING_RE.exec(raw);
  if (!m) return null;
  const heading = normalizeHeading(m[2] ?? "");
  return (
    SECTION_SPECS.find((spec) =>
      spec.headings.some((h) => normalizeHeading(h) === heading),
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
  const buckets = new Map<TemplateComponentType, string[]>();
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

  const headings: Record<TemplateComponentType, string> = {
    LOGO: "",
    HEADER: "",
    METADATA: "",
    PARTICIPANT_TABLE: "",
    EXECUTIVE_SUMMARY: "1. YÖNETİCİ ÖZETİ",
    AGENDA: "2. GÜNDEM",
    DECISIONS: "3. ALINAN KARARLAR",
    ACTIONS: "4. AKSİYON MADDELERİ",
    RISKS: "5. RİSKLER",
    COMMITMENTS: "6. TAAHHÜTLER",
    OPEN_QUESTIONS: "7. AÇIK SORULAR",
    SIGNATURE: "",
    FOOTER: "",
    CONFIDENTIALITY: "",
    PAGE_NUMBER: "",
  };

  for (const section of doc.sections) {
    const heading = headings[section.type];
    if (!heading) continue;
    lines.push(heading);
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
