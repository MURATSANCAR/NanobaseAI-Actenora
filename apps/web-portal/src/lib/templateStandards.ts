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

/** Sections shown in meeting note editor when a template is locked. */
export const MEETING_NOTE_EDITABLE_SECTIONS: TemplateComponentType[] = [
  "EXECUTIVE_SUMMARY",
  "AGENDA",
  "DECISIONS",
  "ACTIONS",
  "RISKS",
  "OPEN_QUESTIONS",
  "COMMITMENTS",
];
