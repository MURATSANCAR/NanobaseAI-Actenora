import { SlidersHorizontal } from "lucide-react";
import type { DesignComponent } from "@/types/template";
import { applyPropEdit, propFieldsFor } from "@/lib/templateComponentProps";
import { useI18n } from "@/i18n";

export function TemplatePropertyInspector({
  component,
  readOnly,
  onChange,
}: {
  component: DesignComponent | null;
  readOnly?: boolean;
  onChange: (id: string, props: Record<string, string>) => void;
}) {
  const { t, tb } = useI18n();

  return (
    <div className="card-static space-y-3 p-3">
      <header className="flex items-center gap-2 border-b border-white/60 pb-2">
        <SlidersHorizontal className="h-4 w-4 text-violet-600" aria-hidden />
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("templates.inspector.title")}
        </h3>
        {component ? (
          <span className="ml-auto rounded-full bg-violet-50 px-2 py-0.5 text-[10px] font-semibold text-violet-700">
            {tb("templateComponentType", component.type)}
          </span>
        ) : null}
      </header>

      {!component ? (
        <p className="py-3 text-center text-sm text-slate-500">{t("templates.inspector.selectHint")}</p>
      ) : (
        <InspectorFields component={component} readOnly={readOnly} onChange={onChange} />
      )}
    </div>
  );
}

function InspectorFields({
  component,
  readOnly,
  onChange,
}: {
  component: DesignComponent;
  readOnly?: boolean;
  onChange: (id: string, props: Record<string, string>) => void;
}) {
  const { t } = useI18n();
  const fields = propFieldsFor(component.type);

  if (!fields.length) {
    return <p className="py-2 text-sm text-slate-500">{t("templates.inspector.noProps")}</p>;
  }

  const update = (key: string, value: string) => {
    onChange(component.id, applyPropEdit(component.props, key, value));
  };

  return (
    <div className="space-y-3">
      {fields.map((field) => {
        const value = component.props[field.key] ?? "";
        const id = `prop-${component.id}-${field.key}`;
        return (
          <label key={field.key} className="block">
            <span className="label-text" id={`${id}-label`}>
              {t(field.labelKey)}
            </span>
            {field.kind === "textarea" ? (
              <textarea
                id={id}
                className="input-field min-h-16 text-sm"
                value={value}
                disabled={readOnly}
                onChange={(event) => update(field.key, event.target.value)}
              />
            ) : (
              <input
                id={id}
                className="input-field text-sm"
                value={value}
                disabled={readOnly}
                onChange={(event) => update(field.key, event.target.value)}
              />
            )}
          </label>
        );
      })}
    </div>
  );
}
