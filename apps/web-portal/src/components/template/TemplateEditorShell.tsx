import { TemplateDesignToolbar } from "@/components/template/TemplateDesignToolbar";
import { TemplateDocumentPreviewStage } from "@/components/template/TemplateDocumentPreviewStage";
import { TemplatePropertyInspector } from "@/components/template/TemplatePropertyInspector";
import {
  type TemplateVersionRow,
} from "@/components/template/TemplateVersionSidebar";
import type {
  DesignComponent,
  DesignSchema,
  TemplateStatusMessage,
  TemplateValidationIssue,
} from "@/types/template";
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
  onMoveComponent,
  onUpdateComponentProps,
  onSaveDraft,
  onPublish,
  onCreateDraft,
  creatingDraft,
  onCompare,
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
  statusMessage?: TemplateStatusMessage | null;
  onSelectVersion: (version: number) => void;
  onAddComponent: (type: TemplateComponentType) => void;
  onSelectComponent: (id: string) => void;
  onRemoveComponent: (id: string) => void;
  onMoveComponent?: (id: string, direction: "up" | "down") => void;
  onUpdateComponentProps?: (id: string, props: Record<string, string>) => void;
  onSaveDraft: () => void;
  onPublish: () => void;
  onCreateDraft?: () => void;
  creatingDraft?: boolean;
  onCompare?: () => void;
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
    <div className="flex flex-col gap-4">
      <TemplateDesignToolbar
        templateName={templateName}
        locale={locale}
        versions={versions}
        activeVersion={activeVersionNumber}
        components={components}
        selectedId={selectedId}
        validationIssues={validationIssues}
        readOnly={readOnly}
        saving={saving}
        publishing={publishing}
        statusMessage={statusMessage}
        onSelectVersion={onSelectVersion}
        onAddComponent={onAddComponent}
        onSelectComponent={onSelectComponent}
        onRemoveComponent={onRemoveComponent}
        onMoveComponent={onMoveComponent}
        onSaveDraft={onSaveDraft}
        onPublish={onPublish}
        onCreateDraft={onCreateDraft}
        creatingDraft={creatingDraft}
        onCompare={onCompare}
      />
      {onUpdateComponentProps ? (
        <div className="grid gap-4 lg:grid-cols-[minmax(15rem,19rem)_1fr]">
          <TemplatePropertyInspector
            component={components.find((c) => c.id === selectedId) ?? null}
            readOnly={readOnly}
            onChange={onUpdateComponentProps}
          />
          <TemplateDocumentPreviewStage components={components} />
        </div>
      ) : (
        <TemplateDocumentPreviewStage components={components} />
      )}
    </div>
  );
}

export function toEditorSchema(schema: DesignSchema | null | undefined): DesignSchema {
  if (schema?.components?.length) return schema;
  return { schemaVersion: 1, pageSize: "A4", components: [] };
}
