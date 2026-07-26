import type { LucideIcon } from "lucide-react";
import {
  HelpCircle,
  ListChecks,
  Scale,
  ShieldAlert,
  Sparkles,
  Zap,
} from "lucide-react";
import type { TemplateComponentType } from "@/types/template";

export type MinutesSectionTheme = {
  icon: LucideIcon;
  /** Card shell */
  shell: string;
  /** Icon tile */
  iconTile: string;
  /** Section index chip */
  indexChip: string;
  /** Heading */
  heading: string;
  /** List item accent */
  item: string;
  /** Item index bubble */
  itemIndex: string;
};

const FALLBACK: MinutesSectionTheme = {
  icon: Sparkles,
  shell: "border-slate-200/80 bg-gradient-to-br from-slate-50 to-white",
  iconTile: "bg-slate-500 text-white shadow-slate-200",
  indexChip: "bg-slate-100 text-slate-600",
  heading: "text-slate-700",
  item: "border-slate-100 bg-white/90",
  itemIndex: "bg-slate-200 text-slate-700",
};

export const MINUTES_SECTION_THEME: Partial<Record<TemplateComponentType, MinutesSectionTheme>> = {
  EXECUTIVE_SUMMARY: {
    icon: Sparkles,
    shell: "border-violet-200/80 bg-gradient-to-br from-violet-50 via-white to-indigo-50/50",
    iconTile: "bg-gradient-to-br from-violet-500 to-indigo-500 text-white shadow-violet-200",
    indexChip: "bg-violet-100 text-violet-700",
    heading: "text-violet-800",
    item: "border-violet-100 bg-white/90",
    itemIndex: "bg-violet-500 text-white",
  },
  DECISIONS: {
    icon: Scale,
    shell: "border-emerald-200/80 bg-gradient-to-br from-emerald-50 via-white to-teal-50/40",
    iconTile: "bg-gradient-to-br from-emerald-500 to-teal-500 text-white shadow-emerald-200",
    indexChip: "bg-emerald-100 text-emerald-700",
    heading: "text-emerald-800",
    item: "border-emerald-100 bg-white/90",
    itemIndex: "bg-emerald-500 text-white",
  },
  ACTIONS: {
    icon: ListChecks,
    shell: "border-amber-200/80 bg-gradient-to-br from-amber-50 via-white to-orange-50/40",
    iconTile: "bg-gradient-to-br from-amber-500 to-orange-500 text-white shadow-amber-200",
    indexChip: "bg-amber-100 text-amber-800",
    heading: "text-amber-800",
    item: "border-amber-100 bg-white/90",
    itemIndex: "bg-amber-500 text-white",
  },
  RISKS: {
    icon: ShieldAlert,
    shell: "border-rose-200/80 bg-gradient-to-br from-rose-50 via-white to-red-50/40",
    iconTile: "bg-gradient-to-br from-rose-500 to-red-500 text-white shadow-rose-200",
    indexChip: "bg-rose-100 text-rose-700",
    heading: "text-rose-800",
    item: "border-rose-100 bg-white/90",
    itemIndex: "bg-rose-500 text-white",
  },
  COMMITMENTS: {
    icon: Zap,
    shell: "border-cyan-200/80 bg-gradient-to-br from-cyan-50 via-white to-sky-50/40",
    iconTile: "bg-gradient-to-br from-cyan-500 to-sky-500 text-white shadow-cyan-200",
    indexChip: "bg-cyan-100 text-cyan-800",
    heading: "text-cyan-800",
    item: "border-cyan-100 bg-white/90",
    itemIndex: "bg-cyan-500 text-white",
  },
  OPEN_QUESTIONS: {
    icon: HelpCircle,
    shell: "border-sky-200/80 bg-gradient-to-br from-sky-50 via-white to-blue-50/40",
    iconTile: "bg-gradient-to-br from-sky-500 to-blue-500 text-white shadow-sky-200",
    indexChip: "bg-sky-100 text-sky-800",
    heading: "text-sky-800",
    item: "border-sky-100 bg-white/90",
    itemIndex: "bg-sky-500 text-white",
  },
};

export function minutesSectionTheme(type: TemplateComponentType): MinutesSectionTheme {
  return MINUTES_SECTION_THEME[type] ?? FALLBACK;
}
