import type { MessageKey } from "@/i18n";
import type { TemplateComponentType } from "@/types/template";

export type PropInputKind = "text" | "textarea";

export interface ComponentPropField {
  /** Prop key persisted into the design schema and read by the backend renderer. */
  key: string;
  labelKey: MessageKey;
  kind: PropInputKind;
}

/**
 * Editable props per component type. Keys mirror exactly what the backend PDF
 * renderer consumes (HtmlPdfDocumentRenderer): `title` overrides a section
 * heading, LOGO uses `src`/`alt`, SIGNATURE adds `label`, FOOTER/CONFIDENTIALITY
 * use `text`, PAGE_NUMBER uses `format`. Anything not listed here is rendered
 * purely from meeting data or branding and has no author-editable prop.
 */
const TITLE_FIELD: ComponentPropField = {
  key: "title",
  labelKey: "templates.props.title",
  kind: "text",
};

export const COMPONENT_PROP_FIELDS: Partial<Record<TemplateComponentType, ComponentPropField[]>> = {
  LOGO: [
    { key: "src", labelKey: "templates.props.src", kind: "text" },
    { key: "alt", labelKey: "templates.props.alt", kind: "text" },
  ],
  HEADER: [TITLE_FIELD],
  METADATA: [TITLE_FIELD],
  PARTICIPANT_TABLE: [TITLE_FIELD],
  EXECUTIVE_SUMMARY: [TITLE_FIELD],
  AGENDA: [TITLE_FIELD],
  DECISIONS: [TITLE_FIELD],
  ACTIONS: [TITLE_FIELD],
  RISKS: [TITLE_FIELD],
  OPEN_QUESTIONS: [TITLE_FIELD],
  COMMITMENTS: [TITLE_FIELD],
  SIGNATURE: [TITLE_FIELD, { key: "label", labelKey: "templates.props.label", kind: "textarea" }],
  FOOTER: [{ key: "text", labelKey: "templates.props.text", kind: "textarea" }],
  CONFIDENTIALITY: [{ key: "text", labelKey: "templates.props.text", kind: "textarea" }],
  PAGE_NUMBER: [{ key: "format", labelKey: "templates.props.format", kind: "text" }],
};

export function propFieldsFor(type: TemplateComponentType): ComponentPropField[] {
  return COMPONENT_PROP_FIELDS[type] ?? [];
}

/**
 * Applies an edited prop value immutably. An empty value removes the key so the
 * renderer's built-in default applies again rather than rendering a blank.
 */
export function applyPropEdit(
  props: Record<string, string>,
  key: string,
  value: string,
): Record<string, string> {
  const next = { ...props };
  if (value.trim() === "") {
    delete next[key];
  } else {
    next[key] = value;
  }
  return next;
}
