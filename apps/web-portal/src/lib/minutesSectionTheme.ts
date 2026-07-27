import type { LucideIcon } from "lucide-react";
import {
  CalendarRange,
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
  /** Left accent rail */
  rail: string;
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
  /** Rainbow legend swatch */
  swatch: string;
};

const FALLBACK: MinutesSectionTheme = {
  icon: Sparkles,
  shell: "border-slate-200/70 bg-gradient-to-br from-slate-50/90 via-white to-slate-50/40",
  rail: "from-slate-400 to-slate-500",
  iconTile: "bg-slate-500 text-white shadow-slate-200/80",
  indexChip: "bg-slate-100 text-slate-600",
  heading: "text-slate-700",
  item: "border-slate-100/90 bg-white/90",
  itemIndex: "bg-slate-200 text-slate-700",
  swatch: "bg-slate-400",
};

export const MINUTES_SECTION_THEME: Partial<Record<TemplateComponentType, MinutesSectionTheme>> = {
  EXECUTIVE_SUMMARY: {
    icon: Sparkles,
    shell: "border-violet-200/70 bg-gradient-to-br from-violet-50/90 via-white to-fuchsia-50/40",
    rail: "from-violet-500 via-fuchsia-500 to-indigo-500",
    iconTile: "bg-gradient-to-br from-violet-500 to-fuchsia-500 text-white shadow-violet-300/50",
    indexChip: "bg-violet-100 text-violet-800",
    heading: "text-violet-900",
    item: "border-violet-100/90 bg-white/90",
    itemIndex: "bg-violet-500 text-white",
    swatch: "bg-gradient-to-br from-violet-500 to-fuchsia-500",
  },
  AGENDA: {
    icon: CalendarRange,
    shell: "border-indigo-200/70 bg-gradient-to-br from-indigo-50/90 via-white to-blue-50/40",
    rail: "from-indigo-500 via-blue-500 to-sky-500",
    iconTile: "bg-gradient-to-br from-indigo-500 to-blue-500 text-white shadow-indigo-300/50",
    indexChip: "bg-indigo-100 text-indigo-800",
    heading: "text-indigo-900",
    item: "border-indigo-100/90 bg-white/90",
    itemIndex: "bg-indigo-500 text-white",
    swatch: "bg-gradient-to-br from-indigo-500 to-blue-500",
  },
  DECISIONS: {
    icon: Scale,
    shell: "border-emerald-200/70 bg-gradient-to-br from-emerald-50/90 via-white to-teal-50/40",
    rail: "from-emerald-500 via-teal-500 to-green-500",
    iconTile: "bg-gradient-to-br from-emerald-500 to-teal-500 text-white shadow-emerald-300/50",
    indexChip: "bg-emerald-100 text-emerald-800",
    heading: "text-emerald-900",
    item: "border-emerald-100/90 bg-white/90",
    itemIndex: "bg-emerald-500 text-white",
    swatch: "bg-gradient-to-br from-emerald-500 to-teal-500",
  },
  ACTIONS: {
    icon: ListChecks,
    shell: "border-amber-200/70 bg-gradient-to-br from-amber-50/90 via-white to-orange-50/40",
    rail: "from-amber-500 via-orange-500 to-yellow-500",
    iconTile: "bg-gradient-to-br from-amber-500 to-orange-500 text-white shadow-amber-300/50",
    indexChip: "bg-amber-100 text-amber-900",
    heading: "text-amber-950",
    item: "border-amber-100/90 bg-white/90",
    itemIndex: "bg-amber-500 text-white",
    swatch: "bg-gradient-to-br from-amber-500 to-orange-500",
  },
  RISKS: {
    icon: ShieldAlert,
    shell: "border-rose-200/70 bg-gradient-to-br from-rose-50/90 via-white to-red-50/40",
    rail: "from-rose-500 via-red-500 to-pink-500",
    iconTile: "bg-gradient-to-br from-rose-500 to-red-500 text-white shadow-rose-300/50",
    indexChip: "bg-rose-100 text-rose-800",
    heading: "text-rose-900",
    item: "border-rose-100/90 bg-white/90",
    itemIndex: "bg-rose-500 text-white",
    swatch: "bg-gradient-to-br from-rose-500 to-red-500",
  },
  COMMITMENTS: {
    icon: Zap,
    shell: "border-cyan-200/70 bg-gradient-to-br from-cyan-50/90 via-white to-teal-50/40",
    rail: "from-cyan-500 via-sky-500 to-teal-500",
    iconTile: "bg-gradient-to-br from-cyan-500 to-sky-500 text-white shadow-cyan-300/50",
    indexChip: "bg-cyan-100 text-cyan-900",
    heading: "text-cyan-950",
    item: "border-cyan-100/90 bg-white/90",
    itemIndex: "bg-cyan-500 text-white",
    swatch: "bg-gradient-to-br from-cyan-500 to-sky-500",
  },
  OPEN_QUESTIONS: {
    icon: HelpCircle,
    shell: "border-sky-200/70 bg-gradient-to-br from-sky-50/90 via-white to-blue-50/40",
    rail: "from-sky-500 via-blue-500 to-indigo-500",
    iconTile: "bg-gradient-to-br from-sky-500 to-blue-500 text-white shadow-sky-300/50",
    indexChip: "bg-sky-100 text-sky-900",
    heading: "text-sky-950",
    item: "border-sky-100/90 bg-white/90",
    itemIndex: "bg-sky-500 text-white",
    swatch: "bg-gradient-to-br from-sky-500 to-blue-500",
  },
};

export function minutesSectionTheme(type: TemplateComponentType): MinutesSectionTheme {
  return MINUTES_SECTION_THEME[type] ?? FALLBACK;
}
