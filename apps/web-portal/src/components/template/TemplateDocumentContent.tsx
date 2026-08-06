import type { ReactNode } from "react";
import type { DesignComponent, TemplateComponentType } from "@/types/template";
import { TemplateBrandFooter, TemplateBrandHeader } from "@/components/template/TemplateBrandBanner";
import { useI18n } from "@/i18n";

const BRAND_HANDLED: TemplateComponentType[] = ["LOGO", "FOOTER", "PAGE_NUMBER"];

const SECTION_ACCENT: Partial<Record<TemplateComponentType, string>> = {
  EXECUTIVE_SUMMARY: "border-l-violet-500",
  AGENDA: "border-l-indigo-500",
  DECISIONS: "border-l-emerald-500",
  ACTIONS: "border-l-amber-500",
  RISKS: "border-l-rose-500",
  OPEN_QUESTIONS: "border-l-sky-500",
  COMMITMENTS: "border-l-fuchsia-500",
};

const HEADING_ACCENT: Partial<Record<TemplateComponentType, string>> = {
  EXECUTIVE_SUMMARY: "text-violet-700",
  AGENDA: "text-indigo-700",
  DECISIONS: "text-emerald-700",
  ACTIONS: "text-amber-700",
  RISKS: "text-rose-700",
  OPEN_QUESTIONS: "text-sky-700",
  COMMITMENTS: "text-fuchsia-700",
};

/** Fixed A4 canvas size (px @96dpi) — scaled by parent stage, never clipped. */
export const A4_PAGE_WIDTH_PX = 794;
export const A4_PAGE_HEIGHT_PX = 1123;

/** Rendered A4 document body used by compact card and fullscreen stage. */
export function TemplateDocumentContent({
  components,
  density = "normal",
}: {
  components: DesignComponent[];
  density?: "compact" | "normal";
}) {
  const { t, tb } = useI18n();
  const bodyComponents = [...components]
    .filter((c) => !BRAND_HANDLED.includes(c.type))
    .sort((a, b) => a.order - b.order);

  // Keep the canvas and the preview in agreement: the branded header/footer and
  // the page number are only chrome for the LOGO/FOOTER/PAGE_NUMBER components,
  // so they must appear only when those components are actually in the schema.
  const hasLogo = components.some((c) => c.type === "LOGO");
  const hasFooter = components.some((c) => c.type === "FOOTER");
  const hasPageNumber = components.some((c) => c.type === "PAGE_NUMBER");

  const textSize = density === "compact" ? "text-[11px] leading-relaxed" : "text-xs leading-relaxed";

  return (
    <div className={`flex h-full min-h-full flex-col gap-3 text-slate-700 ${textSize}`}>
      {hasLogo ? <TemplateBrandHeader compact={density === "compact"} /> : null}
      <div className="flex min-h-0 flex-1 flex-col gap-2.5 overflow-y-auto">
        {bodyComponents.length ? (
          bodyComponents.map((component) => (
            <PreviewBlock key={component.id} component={component} density={density} />
          ))
        ) : (
          <p className="my-auto text-center text-slate-400">{t("templates.preview.empty")}</p>
        )}
      </div>
      {hasFooter || hasPageNumber ? (
        <TemplateBrandFooter
          pageLabel={hasPageNumber ? t("templates.preview.placeholder.page") : undefined}
        />
      ) : null}
    </div>
  );

  function SectionShell({
    type,
    children,
    density: d,
    titleOverride,
  }: {
    type: TemplateComponentType;
    children: ReactNode;
    density: "compact" | "normal";
    titleOverride?: string;
  }) {
    const accent = SECTION_ACCENT[type] ?? "border-l-slate-300";
    const heading = HEADING_ACCENT[type] ?? "text-slate-600";
    const label = titleOverride?.trim() ? titleOverride : tb("templateComponentType", type);
    return (
      <section className={`rounded-r-md border-l-2 bg-slate-50/60 px-3 py-2 ${accent}`}>
        <h4
          className={[
            "mb-1 font-bold uppercase tracking-wider",
            d === "compact" ? "text-[10px]" : "text-[11px]",
            heading,
          ].join(" ")}
        >
          {label}
        </h4>
        {children}
      </section>
    );
  }

  function PreviewBlock({
    component,
    density: d,
  }: {
    component: DesignComponent;
    density: "compact" | "normal";
  }) {
    const label = tb("templateComponentType", component.type);
    const titleProp = component.props.title?.trim();

    switch (component.type) {
      case "HEADER":
        return (
          <div className="border-b-2 border-violet-100 pb-2">
            <p className={d === "compact" ? "text-lg font-bold text-slate-900" : "text-xl font-bold text-slate-900"}>
              {titleProp || t("templates.preview.placeholder.title")}
            </p>
            <p className="text-slate-500">{t("templates.preview.placeholder.subtitle")}</p>
            <p className="mt-1 font-mono text-[9px] uppercase tracking-wider text-violet-400">
              {t("templates.preview.placeholder.reference")}
            </p>
          </div>
        );
      case "METADATA":
        return (
          <dl className="grid grid-cols-2 gap-x-3 gap-y-1 rounded-md bg-slate-50/80 px-3 py-2 text-slate-600">
            <MetaRow term={t("templates.preview.placeholder.date")} />
            <MetaRow term={t("templates.preview.placeholder.time")} />
            <MetaRow term={t("templates.preview.placeholder.location")} />
            <MetaRow term={t("templates.preview.placeholder.meetingType")} />
          </dl>
        );
      case "PARTICIPANT_TABLE":
        return (
          <SectionShell type="PARTICIPANT_TABLE" density={d} titleOverride={titleProp}>
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-slate-200 text-slate-500">
                  <th className="py-1 font-semibold">{t("templates.preview.placeholder.name")}</th>
                  <th className="py-1 font-semibold">{t("templates.preview.placeholder.role")}</th>
                  <th className="py-1 font-semibold">{t("templates.preview.placeholder.attendance")}</th>
                </tr>
              </thead>
              <tbody>
                {[0, 1].map((i) => (
                  <tr key={i} className="border-b border-slate-100">
                    <td className="py-1 text-slate-400">{t("templates.preview.placeholder.row")}</td>
                    <td className="py-1 text-slate-400">{t("templates.preview.placeholder.row")}</td>
                    <td className="py-1">
                      <span className="rounded-full bg-emerald-50 px-1.5 py-0.5 text-[8px] font-semibold text-emerald-600">
                        {t("templates.preview.placeholder.present")}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </SectionShell>
        );
      case "AGENDA":
        return (
          <SectionShell type="AGENDA" density={d} titleOverride={titleProp}>
            <ol className="space-y-1 text-slate-400">
              {[0, 1].map((i) => (
                <li key={i} className="flex items-start gap-2">
                  <span className="shrink-0 tabular-nums">{i + 1}.</span>
                  <span className="min-w-0 break-words">{t("templates.preview.placeholder.item")}</span>
                </li>
              ))}
            </ol>
          </SectionShell>
        );
      case "ACTIONS":
        return (
          <SectionShell type="ACTIONS" density={d} titleOverride={titleProp}>
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-amber-100 text-amber-700/80">
                  <th className="py-1 font-semibold">{t("templates.preview.placeholder.task")}</th>
                  <th className="py-1 font-semibold">{t("templates.preview.placeholder.owner")}</th>
                  <th className="py-1 font-semibold">{t("templates.preview.placeholder.due")}</th>
                </tr>
              </thead>
              <tbody>
                <tr className="border-b border-amber-50">
                  <td className="py-1 text-slate-400">{t("templates.preview.placeholder.row")}</td>
                  <td className="py-1 text-slate-400">{t("templates.preview.placeholder.row")}</td>
                  <td className="py-1 text-slate-400">{t("templates.preview.placeholder.row")}</td>
                </tr>
              </tbody>
            </table>
          </SectionShell>
        );
      case "DECISIONS":
      case "RISKS":
      case "OPEN_QUESTIONS":
      case "COMMITMENTS":
      case "EXECUTIVE_SUMMARY":
        return (
          <SectionShell type={component.type} density={d} titleOverride={titleProp}>
            <p className="text-slate-400">{t("templates.preview.placeholder.section")}</p>
          </SectionShell>
        );
      case "CONFIDENTIALITY":
        return (
          <p className="rounded-md border border-amber-200 bg-amber-50 px-2 py-1 text-[9px] font-semibold uppercase tracking-wider text-amber-800">
            {label} · {component.props.text?.trim() || t("templates.preview.placeholder.classification")}
          </p>
        );
      case "SIGNATURE": {
        const signatureLabel = component.props.label?.trim();
        return (
          <div className="mt-1 space-y-1 pt-2">
            <div className="grid grid-cols-2 gap-4">
              {[0, 1].map((i) => (
                <div key={i}>
                  <div className="h-7 border-b border-slate-300" />
                  <p className="mt-1 text-[9px] uppercase tracking-wider text-slate-400">
                    {t("templates.preview.placeholder.signatory")}
                  </p>
                </div>
              ))}
            </div>
            {signatureLabel ? <p className="text-slate-500">{signatureLabel}</p> : null}
          </div>
        );
      }
      default:
        return (
          <SectionShell type={component.type} density={d} titleOverride={titleProp}>
            <p className="text-slate-400">{t("templates.preview.placeholder.section")}</p>
          </SectionShell>
        );
    }
  }

  function MetaRow({ term }: { term: string }) {
    return (
      <>
        <dt className="font-semibold text-slate-500">{term}</dt>
        <dd className="text-slate-400">{t("templates.preview.placeholder.value")}</dd>
      </>
    );
  }
}
