import { useMemo } from "react";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { TemplateDocumentPreviewStage } from "@/components/template/TemplateDocumentPreviewStage";
import type { TemplateVersionDetail } from "@/api/types";
import type { DesignComponent, TemplateComponentType } from "@/types/template";
import { buildStandardDesignSchema } from "@/lib/templateStandards";
import { diffDesignSchemas, meaningfulDiffEntries } from "@/lib/templateSchemaDiff";
import { useI18n } from "@/i18n";
import type { MessageKey } from "@/i18n";

function schemaComponents(version: TemplateVersionDetail | undefined): DesignComponent[] {
  if (version?.designSchema?.components?.length) {
    return version.designSchema.components.map((c) => ({
      id: c.id,
      type: c.type as TemplateComponentType,
      order: c.order,
      props: c.props,
    }));
  }
  return buildStandardDesignSchema().components;
}

const DIFF_KIND_KEY: Record<"added" | "removed" | "moved" | "changed", MessageKey> = {
  added: "templates.compare.diff.added",
  removed: "templates.compare.diff.removed",
  moved: "templates.compare.diff.moved",
  changed: "templates.compare.diff.changed",
};

const DIFF_KIND_STYLE: Record<"added" | "removed" | "moved" | "changed", string> = {
  added: "border-emerald-200 bg-emerald-50 text-emerald-900",
  removed: "border-rose-200 bg-rose-50 text-rose-900",
  moved: "border-sky-200 bg-sky-50 text-sky-900",
  changed: "border-amber-200 bg-amber-50 text-amber-900",
};

export function TemplateVersionCompareView({
  versions,
  leftVersionNumber,
  rightVersionNumber,
  onChangeLeft,
  onChangeRight,
  onClose,
}: {
  versions: TemplateVersionDetail[];
  leftVersionNumber: number;
  rightVersionNumber: number;
  onChangeLeft: (version: number) => void;
  onChangeRight: (version: number) => void;
  onClose: () => void;
}) {
  const { t, tb } = useI18n();

  const left = versions.find((v) => v.versionNumber === leftVersionNumber);
  const right = versions.find((v) => v.versionNumber === rightVersionNumber);
  const leftComponents = useMemo(() => schemaComponents(left), [left]);
  const rightComponents = useMemo(() => schemaComponents(right), [right]);

  const changes = useMemo(
    () => meaningfulDiffEntries(diffDesignSchemas(leftComponents, rightComponents)),
    [leftComponents, rightComponents],
  );

  return (
    <div className="space-y-4">
      <div className="card-static flex flex-wrap items-center justify-between gap-3 p-3">
        <p className="text-sm font-medium text-slate-700">{t("templates.compare.diff.title")}</p>
        <button type="button" className="btn-secondary px-3 py-1.5 text-sm" onClick={onClose}>
          {t("templates.compare.close")}
        </button>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ComparePanel
          label={t("templates.compare.left")}
          versions={versions}
          selected={leftVersionNumber}
          onSelect={onChangeLeft}
          version={left}
          components={leftComponents}
        />
        <ComparePanel
          label={t("templates.compare.right")}
          versions={versions}
          selected={rightVersionNumber}
          onSelect={onChangeRight}
          version={right}
          components={rightComponents}
        />
      </div>

      <section className="card-static p-4" aria-label={t("templates.compare.diff.title")}>
        <h3 className="mb-3 text-xs font-bold uppercase tracking-wide text-violet-700">
          {t("templates.compare.diff.title")}
        </h3>
        {changes.length === 0 ? (
          <p className="text-sm text-slate-600">{t("templates.compare.diff.empty")}</p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {changes.map((entry) => {
              const typeLabel = tb("templateComponentType", entry.type);
              const label =
                entry.kind === "moved"
                  ? t(DIFF_KIND_KEY.moved, {
                      type: typeLabel,
                      from: entry.fromOrder ?? "—",
                      to: entry.order,
                    })
                  : t(DIFF_KIND_KEY[entry.kind], { type: typeLabel });
              return (
                <li
                  key={`${entry.kind}-${entry.id}`}
                  className={[
                    "rounded-lg border px-2.5 py-1.5 text-xs font-medium",
                    DIFF_KIND_STYLE[entry.kind],
                  ].join(" ")}
                >
                  {label}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

function ComparePanel({
  label,
  versions,
  selected,
  onSelect,
  version,
  components,
}: {
  label: string;
  versions: TemplateVersionDetail[];
  selected: number;
  onSelect: (version: number) => void;
  version: TemplateVersionDetail | undefined;
  components: DesignComponent[];
}) {
  const { t, tb } = useI18n();
  const selectId = `compare-version-${label.replace(/\s+/g, "-").toLowerCase()}`;

  return (
    <div className="flex min-w-0 flex-col gap-3">
      <div className="card-static space-y-2 p-3">
        <label className="label-text" htmlFor={selectId}>
          {label}
        </label>
        <div className="flex flex-wrap items-center gap-2">
          <select
            id={selectId}
            className="input-field min-w-[8rem] flex-1 py-1.5 text-sm"
            value={selected}
            onChange={(e) => onSelect(Number(e.target.value))}
          >
            {versions.map((v) => (
              <option key={v.id} value={v.versionNumber}>
                {t("templates.versions.label", { version: v.versionNumber })}
                {" · "}
                {tb("artifactStatus", v.status)}
              </option>
            ))}
          </select>
          {version ? (
            <StatusBadge label={tb("artifactStatus", version.status)} status={version.status} />
          ) : null}
        </div>
        {version?.changelog ? (
          <p className="text-xs text-slate-600">{version.changelog}</p>
        ) : null}
        {version?.updatedAt ? (
          <p className="text-[10px] text-slate-400">
            {new Date(version.updatedAt).toLocaleString()}
          </p>
        ) : null}
      </div>
      <TemplateDocumentPreviewStage
        components={components}
        compact
        title={t("templates.versions.label", { version: selected })}
      />
    </div>
  );
}
