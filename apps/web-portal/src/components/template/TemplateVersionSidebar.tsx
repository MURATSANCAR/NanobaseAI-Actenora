import { StatusBadge } from "@/components/qa/StatusBadge";
import type { TemplateVersionStatus } from "@/types/template";
import { useI18n } from "@/i18n";

export interface TemplateVersionRow {
  version: number;
  status: TemplateVersionStatus;
  changelog: string;
  updatedAt: string;
}

export function TemplateVersionSidebar({
  templateName,
  locale,
  versions,
  activeVersion,
  onSelect,
  compact = false,
}: {
  templateName: string;
  locale: string;
  versions: TemplateVersionRow[];
  activeVersion: number;
  onSelect?: (version: number) => void;
  compact?: boolean;
}) {
  const { t, tb } = useI18n();

  if (compact) {
    return (
      <div className="min-w-[8rem] flex-1 sm:max-w-[11rem]">
        <label className="label-text" htmlFor="template-version-select">
          {templateName}
        </label>
        <select
          id="template-version-select"
          className="input-field w-full py-1.5 text-sm"
          value={activeVersion}
          onChange={(e) => onSelect?.(Number(e.target.value))}
        >
          {versions.map((v) => (
            <option key={v.version} value={v.version}>
              {t("templates.versions.label", { version: v.version })}
              {" · "}
              {tb("artifactStatus", v.status)}
            </option>
          ))}
        </select>
        <p className="mt-0.5 text-[10px] text-slate-500">{locale.toUpperCase()}</p>
      </div>
    );
  }

  return (
    <aside className="card-static space-y-3 p-4">
      <div>
        <h2 className="text-lg font-bold text-slate-900">{templateName}</h2>
        <p className="text-xs text-slate-500">{locale.toUpperCase()}</p>
      </div>
      <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">
        {t("templates.versions.title")}
      </h3>
      <ul className="space-y-2">
        {versions.map((v) => (
          <li key={v.version}>
            <button
              type="button"
              className={[
                "w-full rounded-xl border p-3 text-left transition",
                activeVersion === v.version
                  ? "border-violet-400 bg-violet-100/60 ring-2 ring-violet-300/70"
                  : "border-white/70 bg-white/50 hover:border-violet-200",
              ].join(" ")}
              onClick={() => onSelect?.(v.version)}
            >
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-semibold text-slate-900">
                  {t("templates.versions.label", { version: v.version })}
                </span>
                <StatusBadge label={tb("artifactStatus", v.status)} status={v.status} />
              </div>
              <p className="mt-1 text-xs text-slate-600">{v.changelog}</p>
              <p className="mt-1 text-[10px] text-slate-400">
                {new Date(v.updatedAt).toLocaleString()}
              </p>
            </button>
          </li>
        ))}
      </ul>
    </aside>
  );
}
