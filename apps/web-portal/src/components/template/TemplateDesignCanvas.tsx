import { GripVertical, Trash2 } from "lucide-react";
import type { DesignComponent } from "@/types/template";
import { useI18n } from "@/i18n";

export function TemplateCanvasBlock({
  component,
  selected,
  onSelect,
  onRemove,
  readOnly,
}: {
  component: DesignComponent;
  selected?: boolean;
  onSelect?: () => void;
  onRemove?: () => void;
  readOnly?: boolean;
}) {
  const { tb, t } = useI18n();

  return (
    <div
      className={[
        "group flex items-start gap-2 rounded-xl border p-3 transition",
        selected
          ? "border-violet-400 bg-violet-100/60 ring-2 ring-violet-300/70"
          : "border-white/70 bg-white/50 hover:border-violet-200",
      ].join(" ")}
    >
      {!readOnly ? (
        <span className="mt-0.5 cursor-grab text-slate-400" aria-hidden>
          <GripVertical className="h-4 w-4" />
        </span>
      ) : null}
      <button
        type="button"
        className="min-w-0 flex-1 text-left"
        onClick={onSelect}
        disabled={readOnly}
      >
        <span className="text-[10px] font-bold uppercase tracking-wide text-violet-600">
          {t("templates.canvas.order", { order: component.order })}
        </span>
        <strong className="mt-0.5 block text-sm text-slate-900">
          {tb("templateComponentType", component.type)}
        </strong>
        {Object.keys(component.props).length ? (
          <span className="mt-1 block font-mono text-[10px] text-slate-500">
            {Object.entries(component.props)
              .map(([k, v]) => `${k}=${v}`)
              .join(" · ")}
          </span>
        ) : (
          <span className="mt-1 block text-xs text-slate-500">{t("templates.canvas.noProps")}</span>
        )}
      </button>
      {!readOnly && onRemove ? (
        <button
          type="button"
          className="rounded-lg p-1.5 text-slate-400 opacity-0 transition hover:bg-red-50 hover:text-red-600 group-hover:opacity-100"
          aria-label={t("templates.canvas.remove")}
          onClick={onRemove}
        >
          <Trash2 className="h-4 w-4" />
        </button>
      ) : null}
    </div>
  );
}

export function TemplateDesignCanvas({
  components,
  selectedId,
  onSelect,
  onRemove,
  readOnly,
  emptyMessage,
}: {
  components: DesignComponent[];
  selectedId?: string | null;
  onSelect?: (id: string) => void;
  onRemove?: (id: string) => void;
  readOnly?: boolean;
  emptyMessage: string;
}) {
  const { t } = useI18n();
  const sorted = [...components].sort((a, b) => a.order - b.order);

  return (
    <div className="card-static flex min-h-[20rem] flex-col gap-3 p-4">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-white/60 pb-3">
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("templates.canvas.title")}
        </h3>
        <span className="rounded-full bg-violet-50 px-2.5 py-1 text-[11px] font-semibold text-violet-700">
          A4 · v1
        </span>
      </header>
      {sorted.length ? (
        <div className="space-y-2">
          {sorted.map((component) => (
            <TemplateCanvasBlock
              key={component.id}
              component={component}
              selected={selectedId === component.id}
              onSelect={() => onSelect?.(component.id)}
              onRemove={() => onRemove?.(component.id)}
              readOnly={readOnly}
            />
          ))}
        </div>
      ) : (
        <p className="flex flex-1 items-center justify-center text-sm text-slate-500">{emptyMessage}</p>
      )}
    </div>
  );
}
