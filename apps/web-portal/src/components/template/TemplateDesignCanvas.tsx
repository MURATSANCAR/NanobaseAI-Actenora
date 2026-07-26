import { Trash2 } from "lucide-react";
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
        "group flex items-center gap-2 rounded-lg border px-2 py-1.5 transition",
        selected
          ? "border-violet-400 bg-violet-100/60 ring-1 ring-violet-300/70"
          : "border-white/70 bg-white/50 hover:border-violet-200",
      ].join(" ")}
    >
      <button
        type="button"
        className="min-w-0 flex-1 truncate text-left text-sm"
        onClick={onSelect}
        disabled={readOnly}
      >
        <span className="font-medium text-slate-800">
          {t("templates.canvas.orderShort", { order: component.order })}
          {" · "}
          {tb("templateComponentType", component.type)}
        </span>
      </button>
      {!readOnly && onRemove ? (
        <button
          type="button"
          className="rounded p-1 text-slate-400 hover:bg-red-50 hover:text-red-600"
          aria-label={t("templates.canvas.remove")}
          onClick={onRemove}
        >
          <Trash2 className="h-3.5 w-3.5" />
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
  compact = false,
  versionLabel = "A4 · v1",
}: {
  components: DesignComponent[];
  selectedId?: string | null;
  onSelect?: (id: string) => void;
  onRemove?: (id: string) => void;
  readOnly?: boolean;
  emptyMessage: string;
  compact?: boolean;
  versionLabel?: string;
}) {
  const { t, tb } = useI18n();
  const sorted = [...components].sort((a, b) => a.order - b.order);

  if (compact) {
    return (
      <div className="min-w-[10rem] flex-1 sm:max-w-[16rem]">
        <div className="mb-1 flex items-center justify-between gap-2">
          <label className="label-text" htmlFor="template-canvas-select">
            {t("templates.canvas.title")}
          </label>
          <span className="text-[10px] font-semibold text-violet-600">{versionLabel}</span>
        </div>
        <div className="flex gap-1.5">
          <select
            id="template-canvas-select"
            className="input-field min-w-0 flex-1 py-1.5 text-sm"
            value={selectedId ?? ""}
            disabled={!sorted.length}
            onChange={(e) => {
              const id = e.target.value;
              if (id) onSelect?.(id);
            }}
          >
            {!sorted.length ? (
              <option value="">{emptyMessage}</option>
            ) : (
              <>
                <option value="" disabled hidden>
                  {t("templates.canvas.choose")}
                </option>
                {sorted.map((component) => (
                  <option key={component.id} value={component.id}>
                    {t("templates.canvas.orderShort", { order: component.order })}
                    {" · "}
                    {tb("templateComponentType", component.type)}
                  </option>
                ))}
              </>
            )}
          </select>
          <button
            type="button"
            className="btn-secondary shrink-0 px-2.5 py-1.5"
            disabled={readOnly || !selectedId || !onRemove}
            aria-label={t("templates.canvas.remove")}
            title={t("templates.canvas.remove")}
            onClick={() => selectedId && onRemove?.(selectedId)}
          >
            <Trash2 className="h-4 w-4" aria-hidden />
          </button>
        </div>
        {sorted.length ? (
          <p className="mt-1 text-[10px] text-slate-500">
            {t("templates.canvas.count", { count: sorted.length })}
          </p>
        ) : null}
      </div>
    );
  }

  return (
    <div className="card-static flex min-h-[12rem] flex-col gap-2 p-3">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-white/60 pb-2">
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("templates.canvas.title")}
        </h3>
        <span className="rounded-full bg-violet-50 px-2 py-0.5 text-[10px] font-semibold text-violet-700">
          {versionLabel}
        </span>
      </header>
      {sorted.length ? (
        <div className="max-h-48 space-y-1 overflow-y-auto">
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
        <p className="py-6 text-center text-sm text-slate-500">{emptyMessage}</p>
      )}
    </div>
  );
}
