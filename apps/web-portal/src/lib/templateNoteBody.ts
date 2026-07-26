import type { TemplateComponentType } from "@/types/template";
import { MEETING_NOTE_EDITABLE_SECTIONS } from "@/lib/templateStandards";

export const TEMPLATE_NOTE_FORMAT = "actenora-template-note" as const;

export interface TemplateNoteBody {
  format: typeof TEMPLATE_NOTE_FORMAT;
  version: 1;
  templateId: string;
  templateVersionId: string;
  templateName: string;
  templateVersionNumber: number;
  sections: Partial<Record<TemplateComponentType, string>>;
}

export function isTemplateNoteBody(body: string): boolean {
  return parseTemplateNoteBody(body) !== null;
}

export function parseTemplateNoteBody(body: string): TemplateNoteBody | null {
  const trimmed = body.trim();
  if (!trimmed.startsWith("{")) return null;
  try {
    const parsed = JSON.parse(trimmed) as Partial<TemplateNoteBody>;
    if (parsed.format !== TEMPLATE_NOTE_FORMAT || parsed.version !== 1) return null;
    if (!parsed.templateId || !parsed.templateVersionId || !parsed.templateName) return null;
    return {
      format: TEMPLATE_NOTE_FORMAT,
      version: 1,
      templateId: parsed.templateId,
      templateVersionId: parsed.templateVersionId,
      templateName: parsed.templateName,
      templateVersionNumber: parsed.templateVersionNumber ?? 1,
      sections: parsed.sections ?? {},
    };
  } catch {
    return null;
  }
}

export function createTemplateNoteBody(input: {
  templateId: string;
  templateVersionId: string;
  templateName: string;
  templateVersionNumber: number;
  sections?: Partial<Record<TemplateComponentType, string>>;
}): TemplateNoteBody {
  return {
    format: TEMPLATE_NOTE_FORMAT,
    version: 1,
    templateId: input.templateId,
    templateVersionId: input.templateVersionId,
    templateName: input.templateName,
    templateVersionNumber: input.templateVersionNumber,
    sections: input.sections ?? {},
  };
}

export function serializeTemplateNoteBody(body: TemplateNoteBody): string {
  return JSON.stringify(body);
}

export function plainTextFromTemplateNote(body: TemplateNoteBody): string {
  return MEETING_NOTE_EDITABLE_SECTIONS.map((type) => body.sections[type]?.trim())
    .filter(Boolean)
    .join("\n\n");
}

export function designSchemaToJson(schema: {
  schemaVersion: number;
  pageSize: string;
  components: Array<{ id: string; type: string; order: number; props: Record<string, string> }>;
}): string {
  return JSON.stringify(schema);
}
