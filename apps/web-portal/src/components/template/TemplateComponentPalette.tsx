import { Plus } from "lucide-react";
import { useState } from "react";
import type { TemplateComponentType } from "@/types/template";
import { TEMPLATE_COMPONENT_TYPES } from "@/lib/templateStandards";
import { useI18n } from "@/i18n";

export function TemplateComponentPalette({
  onAdd,
  disabled,
  compact = false,
}: {
  onAdd?: (type: TemplateComponentType) => void;
  disabled?: boolean;
  compact?: boolean;
}) {
  const { t, tb } = useI18n();
  const [pending, setPending] = useState<TemplateComponentType | "">("");

  function handleAdd() {
    if (!pending || !onAdd) return;
    onAdd(pending);
    setPending("");
  }

  if (!compact) {
    return (
      <div className="card-static space-y-3 p-4">
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("templates.palette.title")}
        </h3>
        <p className="text-xs text-slate-500">{t("templates.palette.hint")}</p>
        <PaletteControls
          pending={pending}
          disabled={disabled}
          onPendingChange={setPending}
          onAdd={handleAdd}
          labelType={tb}
          chooseLabel={t("templates.palette.choose")}
          addLabel={t("templates.palette.add")}
        />
      </div>
    );
  }

  return (
    <div className="min-w-[10rem] flex-1 sm:max-w-[14rem]">
      <label className="label-text" htmlFor="template-palette-add">
        {t("templates.palette.title")}
      </label>
      <PaletteControls
        id="template-palette-add"
        pending={pending}
        disabled={disabled}
        onPendingChange={setPending}
        onAdd={handleAdd}
        labelType={tb}
        chooseLabel={t("templates.palette.choose")}
        addLabel={t("templates.palette.add")}
      />
    </div>
  );
}

function PaletteControls({
  id = "template-palette-select",
  pending,
  disabled,
  onPendingChange,
  onAdd,
  labelType,
  chooseLabel,
  addLabel,
}: {
  id?: string;
  pending: TemplateComponentType | "";
  disabled?: boolean;
  onPendingChange: (value: TemplateComponentType | "") => void;
  onAdd: () => void;
  labelType: (category: "templateComponentType", value: string) => string;
  chooseLabel: string;
  addLabel: string;
}) {
  return (
    <div className="flex gap-1.5">
      <select
        id={id}
        className="input-field min-w-0 flex-1 py-1.5 text-sm"
        value={pending}
        disabled={disabled || !onAdd}
        onChange={(e) => onPendingChange(e.target.value as TemplateComponentType | "")}
      >
        <option value="">{chooseLabel}</option>
        {TEMPLATE_COMPONENT_TYPES.map((type) => (
          <option key={type} value={type}>
            {labelType("templateComponentType", type)}
          </option>
        ))}
      </select>
      <button
        type="button"
        className="btn-primary shrink-0 px-2.5 py-1.5"
        disabled={disabled || !pending || !onAdd}
        aria-label={addLabel}
        title={addLabel}
        onClick={onAdd}
      >
        <Plus className="h-4 w-4" aria-hidden />
      </button>
    </div>
  );
}
