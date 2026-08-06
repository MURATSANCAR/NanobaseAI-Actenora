import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { PageShell } from "@/components/qa/PageShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { StatusBadge } from "@/components/qa/StatusBadge";
import { TemplateEditorShell, toEditorSchema } from "@/components/template/TemplateEditorShell";
import { TemplateVersionCompareView } from "@/components/template/TemplateVersionCompareView";
import type { TemplateVersionRow } from "@/components/template/TemplateVersionSidebar";
import { useApi } from "@/api/ApiProvider";
import { portalMutationsEnabled, queryKeys, resolvePortalAuthMode } from "@/api/client";
import { useApiMode } from "@/api/ApiProvider";
import type { TemplateVersionDetail } from "@/api/types";
import { useAuth } from "@/auth/AuthProvider";
import {
  buildStandardDesignSchema,
  createDesignComponent,
  validateDesignSchema,
} from "@/lib/templateStandards";
import { designSchemaToJson } from "@/lib/templateNoteBody";
import type { DesignSchema, TemplateComponentType, TemplateStatusMessage } from "@/types/template";
import { useI18n } from "@/i18n";
import { ArrowLeft } from "lucide-react";

function defaultComparePair(
  versions: TemplateVersionDetail[],
  activeVersionNumber: number | null,
): { left: number; right: number } {
  const sorted = [...versions].sort((a, b) => a.versionNumber - b.versionNumber);
  const right =
    activeVersionNumber ??
    sorted[sorted.length - 1]?.versionNumber ??
    0;
  const leftCandidate = sorted
    .filter((v) => v.versionNumber < right)
    .at(-1)?.versionNumber;
  const left =
    leftCandidate ??
    sorted.find((v) => v.versionNumber !== right)?.versionNumber ??
    right;
  return { left, right };
}

function versionRows(versions: TemplateVersionDetail[]): TemplateVersionRow[] {
  return versions.map((v) => ({
    version: v.versionNumber,
    status: v.status,
    changelog: v.changelog,
    updatedAt: v.updatedAt,
  }));
}

function schemaFromVersion(version: TemplateVersionDetail | undefined): DesignSchema {
  if (version?.designSchema?.components?.length) {
    return {
      schemaVersion: 1,
      pageSize: "A4",
      components: version.designSchema.components.map((c) => ({
        id: c.id,
        type: c.type as TemplateComponentType,
        order: c.order,
        props: c.props,
      })),
    };
  }
  return buildStandardDesignSchema();
}

export function TemplateDetailPage() {
  const { templateId = "" } = useParams();
  const api = useApi();
  const apiMode = useApiMode();
  const auth = useAuth();
  const qc = useQueryClient();
  const { t } = useI18n();
  const mutationsEnabled = portalMutationsEnabled(apiMode, resolvePortalAuthMode()) && auth.can("templates:edit");

  const q = useQuery({
    queryKey: queryKeys.templateDetail(templateId),
    queryFn: () => api.getTemplate(templateId),
    enabled: Boolean(templateId),
  });

  const [activeVersionNumber, setActiveVersionNumber] = useState<number | null>(null);
  const [schema, setSchema] = useState<DesignSchema>(() => buildStandardDesignSchema());
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<TemplateStatusMessage | null>(null);
  const [compareMode, setCompareMode] = useState(false);
  const [compareLeft, setCompareLeft] = useState<number | null>(null);
  const [compareRight, setCompareRight] = useState<number | null>(null);

  const detail = q.data;
  const versions = detail?.versions ?? [];
  const activeVersion = versions.find((v) => v.versionNumber === activeVersionNumber);

  useEffect(() => {
    if (!detail?.versions.length || activeVersionNumber !== null) return;
    const draft = detail.versions.find((v) => v.status === "DRAFT");
    const initial = draft ?? detail.versions[0];
    setActiveVersionNumber(initial.versionNumber);
    setSchema(schemaFromVersion(initial));
  }, [detail, activeVersionNumber]);

  function selectVersion(version: number) {
    setActiveVersionNumber(version);
    const selected = detail?.versions.find((v) => v.versionNumber === version);
    setSchema(schemaFromVersion(selected));
  }

  function openCompare() {
    if (versions.length < 2) return;
    const pair = defaultComparePair(versions, activeVersionNumber);
    setCompareLeft(pair.left);
    setCompareRight(pair.right);
    setCompareMode(true);
  }

  const validationIssues = useMemo(() => validateDesignSchema(schema), [schema]);

  const createDraftMutation = useMutation({
    mutationFn: () => api.createTemplateDraft(templateId, { changelog: t("templates.editor.newDraft") }),
    onSuccess: async (version) => {
      setStatusMessage(null);
      await qc.invalidateQueries({ queryKey: queryKeys.templateDetail(templateId) });
      setActiveVersionNumber(version.versionNumber);
      setSchema(buildStandardDesignSchema());
    },
    onError: () => setStatusMessage({ text: t("admin.savePendingBackend"), tone: "error" }),
  });

  const saveMutation = useMutation({
    mutationFn: () => {
      if (!activeVersion) throw new Error(t("templates.editor.noActiveVersion"));
      return api.saveTemplateDesign(templateId, activeVersion.id, {
        designSchemaJson: designSchemaToJson(schema),
      });
    },
    onSuccess: async () => {
      setStatusMessage({ text: t("templates.editor.saved"), tone: "success" });
      await qc.invalidateQueries({ queryKey: queryKeys.templateDetail(templateId) });
      await qc.invalidateQueries({ queryKey: queryKeys.templates });
    },
    onError: () => setStatusMessage({ text: t("admin.savePendingBackend"), tone: "error" }),
  });

  const publishMutation = useMutation({
    mutationFn: () => {
      if (!activeVersion) throw new Error(t("templates.editor.noActiveVersion"));
      return api.publishTemplateVersion(templateId, activeVersion.id);
    },
    onSuccess: async () => {
      setStatusMessage({ text: t("templates.editor.published"), tone: "success" });
      await qc.invalidateQueries({ queryKey: queryKeys.templateDetail(templateId) });
      await qc.invalidateQueries({ queryKey: queryKeys.templates });
    },
    onError: () => setStatusMessage({ text: t("admin.savePendingBackend"), tone: "error" }),
  });

  const defaultMutation = useMutation({
    mutationFn: () => api.setDefaultTemplate(templateId),
    onSuccess: async () => {
      setStatusMessage({ text: t("templates.default.updated"), tone: "success" });
      await qc.invalidateQueries({ queryKey: queryKeys.templateDetail(templateId) });
      await qc.invalidateQueries({ queryKey: queryKeys.templates });
    },
    onError: () => setStatusMessage({ text: t("templates.default.requiresPublished"), tone: "error" }),
  });

  function addComponent(type: TemplateComponentType) {
    const nextOrder = schema.components.length
      ? Math.max(...schema.components.map((c) => c.order)) + 1
      : 1;
    setSchema({
      ...schema,
      components: [...schema.components, createDesignComponent(type, nextOrder)],
    });
  }

  function removeComponent(id: string) {
    setSchema({ ...schema, components: schema.components.filter((c) => c.id !== id) });
    if (selectedId === id) setSelectedId(null);
  }

  function updateComponentProps(id: string, props: Record<string, string>) {
    setSchema({
      ...schema,
      components: schema.components.map((c) => (c.id === id ? { ...c, props } : c)),
    });
  }

  function moveComponent(id: string, direction: "up" | "down") {
    const sorted = [...schema.components].sort((a, b) => a.order - b.order);
    const index = sorted.findIndex((c) => c.id === id);
    if (index < 0) return;
    const swapWith = direction === "up" ? index - 1 : index + 1;
    if (swapWith < 0 || swapWith >= sorted.length) return;
    const current = sorted[index];
    const neighbour = sorted[swapWith];
    setSchema({
      ...schema,
      components: schema.components.map((c) => {
        if (c.id === current.id) return { ...c, order: neighbour.order };
        if (c.id === neighbour.id) return { ...c, order: current.order };
        return c;
      }),
    });
  }

  const status = q.isLoading ? "loading" : q.isError ? "error" : !detail ? "empty" : "ready";

  return (
    <PageShell titleKey="templates.editor.title" subtitleKey="templates.editor.description" maxWidth="max-w-[90rem]">
      <div className="mb-4">
        <Link to="/templates" className="btn-secondary inline-flex items-center gap-2 text-sm">
          <ArrowLeft className="h-4 w-4" aria-hidden />
          {t("templates.mockups.back")}
        </Link>
      </div>
      {detail ? (
        <div className="card-static mb-4 flex flex-wrap items-center justify-between gap-3 p-4">
          <div className="flex items-center gap-2">
            {detail.isDefault ? (
              <StatusBadge label={t("templates.default.badge")} status="APPROVED" />
            ) : null}
            <p className="text-sm text-slate-600">{t("templates.default.explainer")}</p>
          </div>
          {!detail.isDefault && mutationsEnabled ? (
            <button
              type="button"
              className="btn-secondary text-sm"
              disabled={!detail.publishedVersionId || defaultMutation.isPending}
              onClick={() => defaultMutation.mutate()}
            >
              {t("templates.default.action")}
            </button>
          ) : null}
        </div>
      ) : null}
      <AsyncState status={status} error={q.error}>
        {detail ? (
          compareMode && compareLeft !== null && compareRight !== null ? (
            <TemplateVersionCompareView
              versions={detail.versions}
              leftVersionNumber={compareLeft}
              rightVersionNumber={compareRight}
              onChangeLeft={setCompareLeft}
              onChangeRight={setCompareRight}
              onClose={() => setCompareMode(false)}
            />
          ) : (
            <TemplateEditorShell
              templateName={detail.name}
              locale={detail.locale}
              versions={versionRows(detail.versions)}
              activeVersionNumber={activeVersionNumber ?? 0}
              components={toEditorSchema(schema).components}
              selectedId={selectedId}
              validationIssues={validationIssues}
              canEdit={mutationsEnabled}
              saving={saveMutation.isPending}
              publishing={publishMutation.isPending}
              statusMessage={statusMessage}
              onSelectVersion={selectVersion}
              onAddComponent={addComponent}
              onSelectComponent={setSelectedId}
              onRemoveComponent={removeComponent}
              onMoveComponent={moveComponent}
              onUpdateComponentProps={updateComponentProps}
              onSaveDraft={() => saveMutation.mutate()}
              onPublish={() => publishMutation.mutate()}
              onCreateDraft={mutationsEnabled ? () => createDraftMutation.mutate() : undefined}
              creatingDraft={createDraftMutation.isPending}
              onCompare={openCompare}
            />
          )
        ) : null}
      </AsyncState>
    </PageShell>
  );
}
