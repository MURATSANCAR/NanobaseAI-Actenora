import { TemplateComponentPalette } from "@/components/template/TemplateComponentPalette";
import { TemplateDesignCanvas } from "@/components/template/TemplateDesignCanvas";
import { TemplateValidationBanner } from "@/components/template/TemplateValidationBanner";
import {
  TemplateVersionSidebar,
  type TemplateVersionRow,
} from "@/components/template/TemplateVersionSidebar";
import type { DesignComponent, TemplateValidationIssue } from "@/types/template";
import type { TemplateComponentType } from "@/types/template";
import { useI18n } from "@/i18n";

export function TemplateDesignToolbar({
  templateName,
  locale,
  versions,
  activeVersion,
  components,
  selectedId,
  validationIssues,
  readOnly,
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
  onCompare,
  showActions = true,
  actionsReadOnly = false,
}: {
  templateName: string;
  locale: string;
  versions: TemplateVersionRow[];
  activeVersion: number;
  components: DesignComponent[];
  selectedId: string | null;
  validationIssues: TemplateValidationIssue[];
  readOnly?: boolean;
  saving?: boolean;
  publishing?: boolean;
  statusMessage?: string | null;
  onSelectVersion: (version: number) => void;
  onAddComponent: (type: TemplateComponentType) => void;
  onSelectComponent: (id: string) => void;
  onRemoveComponent: (id: string) => void;
  onSaveDraft?: () => void;
  onPublish?: () => void;
  onCreateDraft?: () => void;
  creatingDraft?: boolean;
  onCompare?: () => void;
  showActions?: boolean;
  actionsReadOnly?: boolean;
}) {
  const { t } = useI18n();
  const versionLabel = `A4 · v${activeVersion}`;
  const canCompare = versions.length >= 2;

  return (
    <div className="card-static space-y-2 p-3">
      <div className="flex flex-wrap items-end gap-x-3 gap-y-2">
        <TemplateVersionSidebar
          compact
          templateName={templateName}
          locale={locale}
          versions={versions}
          activeVersion={activeVersion}
          onSelect={onSelectVersion}
        />
        <TemplateComponentPalette compact onAdd={onAddComponent} disabled={readOnly} />
        <TemplateDesignCanvas
          compact
          components={components}
          selectedId={selectedId}
          onSelect={onSelectComponent}
          onRemove={onRemoveComponent}
          readOnly={readOnly}
          emptyMessage={t("templates.canvas.empty")}
          versionLabel={versionLabel}
        />
        {showActions && onSaveDraft && onPublish ? (
          <div className="flex flex-wrap gap-2 sm:ml-auto">
            {onCompare ? (
              <button
                type="button"
                className="btn-secondary px-3 py-1.5 text-sm"
                disabled={!canCompare}
                title={!canCompare ? t("templates.compare.needTwoVersions") : undefined}
                onClick={onCompare}
              >
                {t("templates.compare.action")}
              </button>
            ) : null}
            <button
              type="button"
              className="btn-primary px-3 py-1.5 text-sm"
              disabled={readOnly || actionsReadOnly || saving || validationIssues.length > 0}
              onClick={onSaveDraft}
            >
              {t("templates.actions.saveDraft")}
            </button>
            <button
              type="button"
              className="btn-secondary px-3 py-1.5 text-sm"
              disabled={readOnly || actionsReadOnly || publishing || validationIssues.length > 0 || !components.length}
              onClick={onPublish}
            >
              {t("templates.actions.publish")}
            </button>
            {onCreateDraft ? (
              <button
                type="button"
                className="btn-secondary px-3 py-1.5 text-sm"
                disabled={creatingDraft}
                onClick={onCreateDraft}
              >
                {t("templates.editor.newDraft")}
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
      {validationIssues.length ? <TemplateValidationBanner issues={validationIssues} /> : null}
      {statusMessage ? <p className="text-sm text-amber-800">{statusMessage}</p> : null}
    </div>
  );
}
