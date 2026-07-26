import {
  AlertCircle,
  CalendarDays,
  CheckSquare,
  FileText,
  Footprints,
  Hash,
  HelpCircle,
  Image,
  LayoutTemplate,
  ListChecks,
  Shield,
  Signature,
  Table,
  Users,
} from "lucide-react";
import type { TemplateComponentType } from "@/types/template";
import { TEMPLATE_COMPONENT_TYPES } from "@/lib/templateStandards";
import { useI18n } from "@/i18n";

const iconByType: Record<TemplateComponentType, typeof FileText> = {
  LOGO: Image,
  HEADER: LayoutTemplate,
  METADATA: CalendarDays,
  PARTICIPANT_TABLE: Users,
  EXECUTIVE_SUMMARY: FileText,
  AGENDA: ListChecks,
  DECISIONS: CheckSquare,
  ACTIONS: CheckSquare,
  RISKS: AlertCircle,
  OPEN_QUESTIONS: HelpCircle,
  COMMITMENTS: Footprints,
  SIGNATURE: Signature,
  FOOTER: FileText,
  CONFIDENTIALITY: Shield,
  PAGE_NUMBER: Hash,
};

export function TemplateComponentPalette({
  onAdd,
  disabled,
}: {
  onAdd?: (type: TemplateComponentType) => void;
  disabled?: boolean;
}) {
  const { t, tb } = useI18n();

  return (
    <div className="card-static space-y-3 p-4">
      <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
        {t("templates.palette.title")}
      </h3>
      <p className="text-xs text-slate-500">{t("templates.palette.hint")}</p>
      <ul className="grid gap-2 sm:grid-cols-2">
        {TEMPLATE_COMPONENT_TYPES.map((type) => {
          const Icon = iconByType[type];
          return (
            <li key={type}>
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded-xl border border-white/70 bg-white/50 px-3 py-2 text-left text-sm transition hover:border-violet-200 hover:bg-violet-50/40 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={disabled || !onAdd}
                onClick={() => onAdd?.(type)}
              >
                <Icon className="h-4 w-4 shrink-0 text-violet-600" aria-hidden />
                <span className="font-medium text-slate-800">{tb("templateComponentType", type)}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
