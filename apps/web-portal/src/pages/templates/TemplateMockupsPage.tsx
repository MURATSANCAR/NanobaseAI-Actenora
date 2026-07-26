import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { PageShell } from "@/components/qa/PageShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { TemplateComponentPalette } from "@/components/template/TemplateComponentPalette";
import { TemplateDesignCanvas } from "@/components/template/TemplateDesignCanvas";
import { TemplateNoteSectionEditor } from "@/components/template/TemplateNoteSectionEditor";
import { TemplatePreviewPanel } from "@/components/template/TemplatePreviewPanel";
import { TemplateValidationBanner } from "@/components/template/TemplateValidationBanner";
import { TemplateVersionSidebar } from "@/components/template/TemplateVersionSidebar";
import {
  buildEmptyDesignSchema,
  buildStandardDesignSchema,
  createDesignComponent,
  validateDesignSchema,
} from "@/lib/templateStandards";
import {
  NANOBASE_WEBSITE_LABEL,
  NANOBASE_WEBSITE_URL,
} from "@/components/template/TemplateBrandBanner";
import type { DesignSchema, TemplateComponentType } from "@/types/template";
import { useI18n } from "@/i18n";
import { ArrowLeft, Globe, Sparkles } from "lucide-react";

type MockScenario =
  | "studio"
  | "empty"
  | "validation"
  | "loading"
  | "error"
  | "meeting-note";

const MOCK_VERSIONS = [
  {
    version: 2,
    status: "DRAFT" as const,
    changelog: "Draft revision",
    updatedAt: "2026-07-26T07:00:00.000Z",
  },
  {
    version: 1,
    status: "PUBLISHED" as const,
    changelog: "Initial publish",
    updatedAt: "2026-07-20T14:30:00.000Z",
  },
];

export function TemplateMockupsPage() {
  const { t } = useI18n();
  const [scenario, setScenario] = useState<MockScenario>("studio");
  const [schema, setSchema] = useState<DesignSchema>(() => buildStandardDesignSchema());
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [activeVersion, setActiveVersion] = useState(2);
  const [noteSections, setNoteSections] = useState<Partial<Record<TemplateComponentType, string>>>({});
  const [noteError, setNoteError] = useState<string | null>(null);

  const validationIssues = useMemo(() => validateDesignSchema(schema), [schema]);

  const scenarios: { id: MockScenario; label: string }[] = [
    { id: "studio", label: t("templates.mockups.scenario.studio") },
    { id: "empty", label: t("templates.mockups.scenario.empty") },
    { id: "validation", label: t("templates.mockups.scenario.validation") },
    { id: "loading", label: t("templates.mockups.scenario.loading") },
    { id: "error", label: t("templates.mockups.scenario.error") },
    { id: "meeting-note", label: t("templates.mockups.scenario.meetingNote") },
  ];

  function applyScenario(next: MockScenario) {
    setScenario(next);
    setNoteError(null);
    if (next === "empty") {
      setSchema(buildEmptyDesignSchema());
      setSelectedId(null);
      return;
    }
    if (next === "validation") {
      const broken = buildStandardDesignSchema();
      broken.components[3] = { ...broken.components[3], order: broken.components[2].order };
      setSchema(broken);
      return;
    }
    if (next === "studio") {
      setSchema(buildStandardDesignSchema());
      return;
    }
  }

  function addComponent(type: TemplateComponentType) {
    const nextOrder = schema.components.length
      ? Math.max(...schema.components.map((c) => c.order)) + 1
      : 1;
    const next = {
      ...schema,
      components: [...schema.components, createDesignComponent(type, nextOrder)],
    };
    setSchema(next);
  }

  function removeComponent(id: string) {
    setSchema({
      ...schema,
      components: schema.components.filter((c) => c.id !== id),
    });
    if (selectedId === id) setSelectedId(null);
  }

  function handleSaveNote() {
    const emptyRequired = !noteSections.EXECUTIVE_SUMMARY?.trim();
    setNoteError(emptyRequired ? t("templates.note.validationRequired") : null);
  }

  return (
    <PageShell
      titleKey="templates.mockups.title"
      subtitleKey="templates.mockups.description"
      maxWidth="max-w-[90rem]"
    >
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Link to="/templates" className="btn-secondary inline-flex items-center gap-2 text-sm">
          <ArrowLeft className="h-4 w-4" aria-hidden />
          {t("templates.mockups.back")}
        </Link>
        <span className="rounded-full bg-amber-50 px-2.5 py-1 text-[11px] font-semibold text-amber-800">
          {t("templates.mockups.badge")}
        </span>
      </div>

      <div className="relative mb-4 overflow-hidden rounded-2xl bg-gradient-to-r from-violet-600 via-indigo-500 to-sky-500 px-5 py-6 text-white shadow-glow sm:px-7 sm:py-7">
        <div className="pointer-events-none absolute -right-8 -top-10 h-40 w-40 rounded-full bg-white/10 blur-2xl" aria-hidden />
        <div className="pointer-events-none absolute -bottom-16 left-16 h-40 w-40 rounded-full bg-sky-300/20 blur-2xl" aria-hidden />
        <div className="relative flex flex-wrap items-center gap-4">
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/20 ring-1 ring-white/40 backdrop-blur" aria-hidden>
            <Sparkles className="h-7 w-7" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-2xl font-bold tracking-tight">NanobaseAI</h2>
              <span className="rounded-full bg-white/20 px-2.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider ring-1 ring-white/30">
                {t("templates.brand.aiBadge")}
              </span>
            </div>
            <p className="mt-1 text-sm text-white/90">{t("templates.brand.slogan")}</p>
          </div>
          <a
            href={NANOBASE_WEBSITE_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-2 rounded-xl bg-white/15 px-3 py-2 text-sm font-medium text-white ring-1 ring-white/30 transition hover:bg-white/25"
          >
            <Globe className="h-4 w-4" aria-hidden />
            {NANOBASE_WEBSITE_LABEL}
          </a>
        </div>
      </div>

      <nav className="card-static mb-4 flex flex-wrap gap-2 p-2" aria-label={t("templates.mockups.scenariosNav")}>
        {scenarios.map((s) => (
          <button
            key={s.id}
            type="button"
            className={[
              "rounded-xl px-3 py-2 text-sm font-medium transition",
              scenario === s.id
                ? "bg-violet-600 text-white shadow-sm"
                : "text-slate-600 hover:bg-violet-50 hover:text-violet-800",
            ].join(" ")}
            onClick={() => applyScenario(s.id)}
          >
            {s.label}
          </button>
        ))}
      </nav>

      {scenario === "loading" ? (
        <AsyncState status="loading">{null}</AsyncState>
      ) : null}

      {scenario === "error" ? (
        <AsyncState status="error" error={new Error(t("templates.mockups.errorDetail"))}>
          {null}
        </AsyncState>
      ) : null}

      {scenario === "meeting-note" ? (
        <section className="card-static max-w-2xl p-4 sm:p-5">
          <TemplateNoteSectionEditor
            templateName={t("templates.mockups.sampleTemplateName")}
            templateVersion={1}
            locked
            canEdit
            sectionValues={noteSections}
            onSectionChange={(type, value) =>
              setNoteSections((prev) => ({ ...prev, [type]: value }))
            }
            onSave={handleSaveNote}
            validationError={noteError}
          />
        </section>
      ) : null}

      {scenario === "studio" || scenario === "empty" || scenario === "validation" ? (
        <div className="grid gap-4 xl:grid-cols-[16rem_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.1fr)]">
          <TemplateVersionSidebar
            templateName={t("templates.mockups.sampleTemplateName")}
            locale={t("templates.mockups.sampleLocale")}
            versions={MOCK_VERSIONS}
            activeVersion={activeVersion}
            onSelect={setActiveVersion}
          />
          <TemplateComponentPalette onAdd={addComponent} disabled={scenario === "validation"} />
          <div className="space-y-3">
            {scenario === "validation" || validationIssues.length ? (
              <TemplateValidationBanner issues={validationIssues} />
            ) : null}
            <TemplateDesignCanvas
              components={schema.components}
              selectedId={selectedId}
              onSelect={setSelectedId}
              onRemove={removeComponent}
              readOnly={scenario === "validation"}
              emptyMessage={t("templates.canvas.empty")}
            />
            <div className="flex flex-wrap gap-2">
              <button type="button" className="btn-primary" disabled={validationIssues.length > 0}>
                {t("templates.actions.saveDraft")}
              </button>
              <button
                type="button"
                className="btn-secondary"
                disabled={validationIssues.length > 0 || !schema.components.length}
              >
                {t("templates.actions.publish")}
              </button>
            </div>
          </div>
          <TemplatePreviewPanel components={schema.components} />
        </div>
      ) : null}

      <footer className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-slate-900 px-5 py-4 text-white">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-sky-500" aria-hidden>
            <Sparkles className="h-4 w-4" />
          </span>
          <div>
            <p className="text-sm font-semibold">NanobaseAI</p>
            <p className="text-[11px] text-white/60">{t("templates.brand.footerNote")}</p>
          </div>
        </div>
        <a
          href={NANOBASE_WEBSITE_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-2 text-sm font-medium text-sky-300 hover:text-sky-200"
        >
          <Globe className="h-4 w-4" aria-hidden />
          {NANOBASE_WEBSITE_LABEL}
        </a>
      </footer>
    </PageShell>
  );
}
