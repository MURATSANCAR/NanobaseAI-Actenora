import { TemplateComponentPalette } from "@/components/template/TemplateComponentPalette";
import { TemplateDesignCanvas } from "@/components/template/TemplateDesignCanvas";
import { TemplatePreviewPanel } from "@/components/template/TemplatePreviewPanel";
import { TemplateValidationBanner } from "@/components/template/TemplateValidationBanner";
import {
  TemplateVersionSidebar,
  type TemplateVersionRow,
} from "@/components/template/TemplateVersionSidebar";
import type { DesignComponent, DesignSchema, TemplateValidationIssue } from "@/types/template";
import type { TemplateComponentType } from "@/types/template";
import { useI18n } from "@/i18n";

export function TemplateEditorShell({
  templateName,
  locale,
  versions,
  activeVersionNumber,
  components,
  selectedId,
  validationIssues,
  canEdit,
  saving,
  publishing,
  statusMessage,
  onSelectVersion,
  onAddComponent,
  onSelectComponent,
  onRemoveComponent,
  onSaveDraft,
  onPublish,
  onCreateDraft,
  creatingDraft,
}: {
  templateName: string;
  locale: string;
  versions: TemplateVersionRow[];
  activeVersionNumber: number;
  components: DesignComponent[];
  selectedId: string | null;
  validationIssues: TemplateValidationIssue[];
  canEdit: boolean;
  saving: boolean;
  publishing: boolean;
  statusMessage?: string | null;
  onSelectVersion: (version: number) => void;
  onAddComponent: (type: TemplateComponentType) => void;
  onSelectComponent: (id: string) => void;
  onRemoveComponent: (id: string) => void;
  onSaveDraft: () => void;
  onPublish: () => void;
  onCreateDraft?: () => void;
  creatingDraft?: boolean;
}) {
  const { t } = useI18n();
  const activeVersion = versions.find((v) => v.version === activeVersionNumber);
  const readOnly = !canEdit || activeVersion?.status !== "DRAFT";

  if (!versions.length) {
    return (
      <div className="card-static space-y-4 p-6 text-center">
        <p className="text-sm text-slate-600">{t("templates.editor.noVersions")}</p>
        {onCreateDraft ? (
          <button type="button" className="btn-primary" disabled={creatingDraft} onClick={onCreateDraft}>
            {t("templates.editor.createFirstDraft")}
          </button>
        ) : null}
      </div>
    );
  }

  return (
    <div className="grid gap-4 xl:grid-cols-[16rem_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.1fr)]">
      <TemplateVersionSidebar
        templateName={templateName}
        locale={locale}
        versions={versions}
        activeVersion={activeVersionNumber}
        onSelect={onSelectVersion}
      />
      <TemplateComponentPalette onAdd={onAddComponent} disabled={readOnly} />
      <div className="space-y-3">
        {validationIssues.length ? <TemplateValidationBanner issues={validationIssues} /> : null}
        <TemplateDesignCanvas
          components={components}
          selectedId={selectedId}
          onSelect={onSelectComponent}
          onRemove={onRemoveComponent}
          readOnly={readOnly}
          emptyMessage={t("templates.canvas.empty")}
        />
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-primary"
            disabled={readOnly || saving || validationIssues.length > 0}
            onClick={onSaveDraft}
          >
            {t("templates.actions.saveDraft")}
          </button>
          <button
            type="button"
            className="btn-secondary"
            disabled={readOnly || publishing || validationIssues.length > 0 || !components.length}
            onClick={onPublish}
          >
            {t("templates.actions.publish")}
          </button>
          {onCreateDraft ? (
            <button type="button" className="btn-secondary" disabled={creatingDraft} onClick={onCreateDraft}>
              {t("templates.editor.newDraft")}
            </button>
          ) : null}
        </div>
        {statusMessage ? <p className="text-sm text-amber-800">{statusMessage}</p> : null}
      </div>
      <TemplatePreviewPanel components={components} />
    </div>
  );
}

export function toEditorSchema(schema: DesignSchema | null | undefined): DesignSchema {
  if (schema?.components?.length) return schema;
  return { schemaVersion: 1, pageSize: "A4", components: [] };
}
