import type { DesignComponent } from "@/types/template";
import { TemplateDocumentContent } from "@/components/template/TemplateDocumentContent";
import { useI18n } from "@/i18n";

/** Compact inline A4 preview card (legacy / small embeds). */
export function TemplatePreviewPanel({ components }: { components: DesignComponent[] }) {
  const { t } = useI18n();

  return (
    <div className="card-static p-4">
      <header className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-white/60 pb-3">
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("templates.preview.title")}
        </h3>
        <span className="rounded-full bg-gradient-to-r from-violet-100 to-sky-100 px-2.5 py-1 text-[10px] font-semibold text-violet-700">
          {t("templates.preview.hint")}
        </span>
      </header>
      <div
        className="mx-auto aspect-[210/297] w-full max-w-md overflow-y-auto rounded-lg border border-slate-200 bg-white p-4 shadow-lg ring-1 ring-slate-100"
        aria-label={t("templates.preview.title")}
      >
        <TemplateDocumentContent components={components} density="compact" />
      </div>
    </div>
  );
}
