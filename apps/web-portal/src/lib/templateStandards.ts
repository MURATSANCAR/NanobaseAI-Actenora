import type {
  DesignComponent,
  DesignSchema,
  TemplateComponentType,
  TemplateValidationIssue,
} from "@/types/template";

/** All allowed Template Studio building blocks (backend TemplateComponentType). */
export const TEMPLATE_COMPONENT_TYPES: TemplateComponentType[] = [
  "LOGO",
  "HEADER",
  "METADATA",
  "PARTICIPANT_TABLE",
  "EXECUTIVE_SUMMARY",
  "AGENDA",
  "DECISIONS",
  "ACTIONS",
  "RISKS",
  "OPEN_QUESTIONS",
  "COMMITMENTS",
  "SIGNATURE",
  "FOOTER",
  "CONFIDENTIALITY",
  "PAGE_NUMBER",
];

/** Corporate meeting note standard — ordered layout for A4 delivery. */
export const STANDARD_MEETING_NOTE_LAYOUT: TemplateComponentType[] = [
  "LOGO",
  "HEADER",
  "METADATA",
  "PARTICIPANT_TABLE",
  "EXECUTIVE_SUMMARY",
  "AGENDA",
  "DECISIONS",
  "ACTIONS",
  "RISKS",
  "OPEN_QUESTIONS",
  "COMMITMENTS",
  "SIGNATURE",
  "CONFIDENTIALITY",
  "FOOTER",
  "PAGE_NUMBER",
];

export function componentTypeToBackendKey(type: TemplateComponentType): string {
  return type;
}

export function createDesignComponent(
  type: TemplateComponentType,
  order: number,
  props: Record<string, string> = {},
): DesignComponent {
  return {
    id: crypto.randomUUID(),
    type,
    order,
    props,
  };
}

export function buildStandardDesignSchema(): DesignSchema {
  return {
    schemaVersion: 1,
    pageSize: "A4",
    components: STANDARD_MEETING_NOTE_LAYOUT.map((type, index) =>
      createDesignComponent(type, index + 1),
    ),
  };
}

export function buildEmptyDesignSchema(): DesignSchema {
  return {
    schemaVersion: 1,
    pageSize: "A4",
    components: [],
  };
}

const EVENT_HANDLER = /^on[a-z0-9_]+$/i;
const SCRIPTISH = /(<script|javascript:|expression\s*\(|eval\s*\(|Function\s*\()/i;

/** Client-side validation aligned with backend DesignSchemaValidator. */
export function validateDesignSchema(schema: DesignSchema): TemplateValidationIssue[] {
  const issues: TemplateValidationIssue[] = [];

  if (schema.components.length === 0) {
    issues.push({ code: "EMPTY_DESIGN", messageKey: "templates.validation.emptyDesign" });
    return issues;
  }

  const ids = new Set<string>();
  const orders = new Set<number>();

  for (const component of schema.components) {
    if (ids.has(component.id)) {
      issues.push({ code: "DUPLICATE_ID", messageKey: "templates.validation.duplicateId" });
    }
    ids.add(component.id);

    if (orders.has(component.order)) {
      issues.push({ code: "DUPLICATE_ORDER", messageKey: "templates.validation.duplicateOrder" });
    }
    orders.add(component.order);

    for (const [key, value] of Object.entries(component.props)) {
      const normalized = key.trim().toLowerCase();
      if (
        EVENT_HANDLER.test(normalized) ||
        normalized === "script" ||
        normalized.includes("javascript") ||
        (value && SCRIPTISH.test(value))
      ) {
        issues.push({ code: "FORBIDDEN_PROP", messageKey: "templates.validation.forbiddenProp" });
      }
    }
  }

  return issues;
}

/**
 * Component types an author fills in by hand. Everything else (logo, footer, page numbers)
 * is rendered from meeting data or branding, so it never becomes an editor field.
 */
export const MEETING_NOTE_EDITABLE_SECTIONS: TemplateComponentType[] = [
  "EXECUTIVE_SUMMARY",
  "AGENDA",
  "DECISIONS",
  "ACTIONS",
  "RISKS",
  "OPEN_QUESTIONS",
  "COMMITMENTS",
];

const EDITABLE_SECTION_SET = new Set<TemplateComponentType>(MEETING_NOTE_EDITABLE_SECTIONS);

/**
 * Editor fields for a note, taken from the design its template version defines.
 * A note pinned to an older version keeps that version's sections and ordering even
 * after the template is redesigned. Falls back to the standard set when the version
 * carries no design (drafts saved before a layout was chosen).
 */
export function editableSectionsFromDesign(
  schema: { components: Array<{ type: string; order: number }> } | null | undefined,
): TemplateComponentType[] {
  if (!schema || schema.components.length === 0) {
    return MEETING_NOTE_EDITABLE_SECTIONS;
  }
  const seen = new Set<TemplateComponentType>();
  const sections = [...schema.components]
    .sort((a, b) => a.order - b.order)
    .map((component) => component.type as TemplateComponentType)
    .filter((type) => {
      if (!EDITABLE_SECTION_SET.has(type) || seen.has(type)) return false;
      seen.add(type);
      return true;
    });
  return sections.length ? sections : MEETING_NOTE_EDITABLE_SECTIONS;
}
