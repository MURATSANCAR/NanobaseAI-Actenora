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
    return { empty: false, paragraph: trimmed, items: [] };
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
    if (!trimmed) continue;

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
    return {
      type: spec.type,
      kind: spec.kind,
      value: linesForSection.join("\n"),
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
    AGENDA: "",
    DECISIONS: "2. ALINAN KARARLAR",
    ACTIONS: "3. AKSİYON MADDELERİ",
    RISKS: "4. RİSKLER",
    COMMITMENTS: "5. TAAHHÜTLER",
    OPEN_QUESTIONS: "6. AÇIK SORULAR",
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
