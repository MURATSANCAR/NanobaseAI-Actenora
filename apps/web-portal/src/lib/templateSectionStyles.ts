import type { TemplateComponentType } from "@/types/template";

export const SECTION_ACCENT: Partial<Record<TemplateComponentType, string>> = {
  EXECUTIVE_SUMMARY: "border-l-violet-500 bg-violet-50/40",
  AGENDA: "border-l-indigo-500 bg-indigo-50/40",
  DECISIONS: "border-l-emerald-500 bg-emerald-50/40",
  ACTIONS: "border-l-amber-500 bg-amber-50/40",
  RISKS: "border-l-rose-500 bg-rose-50/40",
  OPEN_QUESTIONS: "border-l-sky-500 bg-sky-50/40",
  COMMITMENTS: "border-l-fuchsia-500 bg-fuchsia-50/40",
};

export const HEADING_ACCENT: Partial<Record<TemplateComponentType, string>> = {
  EXECUTIVE_SUMMARY: "text-violet-700",
  AGENDA: "text-indigo-700",
  DECISIONS: "text-emerald-700",
  ACTIONS: "text-amber-700",
  RISKS: "text-rose-700",
  OPEN_QUESTIONS: "text-sky-700",
  COMMITMENTS: "text-fuchsia-700",
};

export function sectionShellClass(type: TemplateComponentType): string {
  const accent = SECTION_ACCENT[type] ?? "border-l-slate-300 bg-slate-50/40";
  return `rounded-r-xl border-l-[3px] px-4 py-3 ${accent}`;
}

export function sectionHeadingClass(type: TemplateComponentType): string {
  const heading = HEADING_ACCENT[type] ?? "text-slate-600";
  return `mb-2 text-[11px] font-bold uppercase tracking-wider ${heading}`;
}
