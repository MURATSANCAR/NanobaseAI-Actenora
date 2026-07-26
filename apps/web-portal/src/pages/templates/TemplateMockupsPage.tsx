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
import type { DesignSchema, TemplateComponentType } from "@/types/template";
import { useI18n } from "@/i18n";
import { ArrowLeft } from "lucide-react";

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
        <AsyncState status="loading" />
      ) : null}

      {scenario === "error" ? (
        <AsyncState status="error" error={new Error(t("templates.mockups.errorDetail"))} />
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
    </PageShell>
  );
}
