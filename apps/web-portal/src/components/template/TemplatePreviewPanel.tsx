import type { DesignComponent } from "@/types/template";
import { useI18n } from "@/i18n";

/** A4 document preview — structural placeholders only, no demo meeting data. */
export function TemplatePreviewPanel({ components }: { components: DesignComponent[] }) {
  const { t, tb } = useI18n();
  const sorted = [...components].sort((a, b) => a.order - b.order);

  return (
    <div className="card-static p-4">
      <header className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-white/60 pb-3">
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("templates.preview.title")}
        </h3>
        <span className="text-[11px] font-medium text-slate-500">{t("templates.preview.hint")}</span>
      </header>
      <div
        className="mx-auto aspect-[210/297] w-full max-w-md overflow-hidden rounded-lg border border-slate-200 bg-white shadow-inner"
        aria-label={t("templates.preview.title")}
      >
        <div className="flex h-full flex-col gap-3 overflow-y-auto p-5 text-[11px] leading-relaxed text-slate-700">
          {sorted.length ? (
            sorted.map((component) => (
              <PreviewBlock key={component.id} component={component} />
            ))
          ) : (
            <p className="text-center text-slate-400">{t("templates.preview.empty")}</p>
          )}
        </div>
      </div>
    </div>
  );

  function PreviewBlock({ component }: { component: DesignComponent }) {
    const label = tb("templateComponentType", component.type);

    switch (component.type) {
      case "LOGO":
        return (
          <div className="flex h-10 items-center justify-center rounded border border-dashed border-slate-200 bg-slate-50 text-slate-400">
            {label}
          </div>
        );
      case "HEADER":
        return (
          <div className="border-b border-slate-200 pb-2">
            <p className="text-base font-bold text-slate-900">{t("templates.preview.placeholder.title")}</p>
            <p className="text-slate-500">{t("templates.preview.placeholder.subtitle")}</p>
          </div>
        );
      case "METADATA":
        return (
          <dl className="grid grid-cols-2 gap-1 text-slate-600">
            <dt className="font-semibold">{t("templates.preview.placeholder.date")}</dt>
            <dd>{t("templates.preview.placeholder.value")}</dd>
            <dt className="font-semibold">{t("templates.preview.placeholder.duration")}</dt>
            <dd>{t("templates.preview.placeholder.value")}</dd>
          </dl>
        );
      case "PARTICIPANT_TABLE":
        return (
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="py-1 font-semibold">{t("templates.preview.placeholder.name")}</th>
                <th className="py-1 font-semibold">{t("templates.preview.placeholder.role")}</th>
              </tr>
            </thead>
            <tbody>
              <tr className="border-b border-slate-100">
                <td className="py-1 text-slate-400">{t("templates.preview.placeholder.row")}</td>
                <td className="py-1 text-slate-400">{t("templates.preview.placeholder.row")}</td>
              </tr>
            </tbody>
          </table>
        );
      case "PAGE_NUMBER":
        return (
          <p className="mt-auto text-center text-[10px] text-slate-400">
            {t("templates.preview.placeholder.page")}
          </p>
        );
      case "CONFIDENTIALITY":
        return (
          <p className="rounded bg-amber-50 px-2 py-1 text-[10px] font-semibold text-amber-800">
            {label}
          </p>
        );
      case "SIGNATURE":
        return (
          <div className="mt-2 border-t border-slate-200 pt-3">
            <p className="font-semibold text-slate-800">{label}</p>
            <div className="mt-4 h-8 border-b border-slate-300" />
          </div>
        );
      case "FOOTER":
        return (
          <p className="border-t border-slate-100 pt-2 text-[10px] text-slate-400">{label}</p>
        );
      default:
        return (
          <section>
            <h4 className="mb-1 text-xs font-bold uppercase tracking-wide text-violet-700">{label}</h4>
            <p className="text-slate-400">{t("templates.preview.placeholder.section")}</p>
          </section>
        );
    }
  }
}
