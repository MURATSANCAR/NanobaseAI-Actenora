import type { MessageKey } from "@/i18n";

/** Mirrors backend TemplateComponentType wire names. */
export type TemplateComponentType =
  | "LOGO"
  | "HEADER"
  | "METADATA"
  | "PARTICIPANT_TABLE"
  | "EXECUTIVE_SUMMARY"
  | "AGENDA"
  | "DECISIONS"
  | "ACTIONS"
  | "RISKS"
  | "OPEN_QUESTIONS"
  | "COMMITMENTS"
  | "SIGNATURE"
  | "FOOTER"
  | "CONFIDENTIALITY"
  | "PAGE_NUMBER";

export interface DesignComponent {
  id: string;
  type: TemplateComponentType;
  order: number;
  props: Record<string, string>;
}

export interface DesignSchema {
  schemaVersion: 1;
  pageSize: "A4";
  components: DesignComponent[];
}

export type TemplateVersionStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export type TemplateStatusTone = "success" | "error";

export interface TemplateStatusMessage {
  text: string;
  tone: TemplateStatusTone;
}

export interface TemplateValidationIssue {
  code: "EMPTY_DESIGN" | "DUPLICATE_ORDER" | "DUPLICATE_ID" | "FORBIDDEN_PROP";
  messageKey: MessageKey;
}
