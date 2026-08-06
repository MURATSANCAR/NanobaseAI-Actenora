import { Download, FileText, Loader2 } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  A4_PAGE_HEIGHT_PX,
  A4_PAGE_WIDTH_PX,
} from "@/components/template/TemplateDocumentContent";
import { TemplateBrandFooter, TemplateBrandHeader } from "@/components/template/TemplateBrandBanner";
import { useI18n } from "@/i18n";
import {
  parseActionMeta,
  parseMinutesBody,
  parseSectionContent,
  type MinutesSection,
  type MinutesSectionKind,
} from "@/lib/minutesDocument";
import { parseTemplateNoteBody } from "@/lib/templateNoteBody";
import type { TemplateComponentType } from "@/types/template";

const PREVIEW_SECTION_ORDER: Array<{ type: TemplateComponentType; kind: MinutesSectionKind }> = [
  { type: "EXECUTIVE_SUMMARY", kind: "paragraph" },
  { type: "AGENDA", kind: "list" },
  { type: "DECISIONS", kind: "list" },
  { type: "ACTIONS", kind: "list" },
  { type: "RISKS", kind: "list" },
  { type: "COMMITMENTS", kind: "list" },
  { type: "OPEN_QUESTIONS", kind: "list" },
];

const STAGE_PAD_X = 32;
const MAX_SCALE = 1.05;

function resolvePreviewDocument(
  body: string,
  meetingTitle: string,
): { title: string; sections: MinutesSection[] } | null {
  const trimmed = body.trim();
  if (!trimmed) return null;

  const template = parseTemplateNoteBody(trimmed);
  if (template) {
    const sections = PREVIEW_SECTION_ORDER.map(({ type, kind }) => ({
      type,
      kind,
      value: template.sections[type] ?? "",
    })).filter((section) => section.value.trim().length > 0);
    if (sections.length === 0) {
      return { title: meetingTitle.trim() || "", sections: [] };
    }
    return { title: meetingTitle.trim() || "", sections };
  }

  const minutes = parseMinutesBody(trimmed, meetingTitle);
  if (minutes) {
    return {
      title: minutes.title,
      sections: minutes.sections.filter((s) => s.value.trim().length > 0),
    };
  }

  return {
    title: meetingTitle.trim() || "",
    sections: [
      {
        type: "EXECUTIVE_SUMMARY",
        kind: "paragraph",
        value: trimmed,
      },
    ],
  };
}

/** Visible A4 PDF-style preview of meeting minutes with draft watermark when unapproved. */
export function MeetingMinutesPdfPreview({
  meetingTitle,
  body,
  approved,
  downloadUrl,
  pdfPending,
  loadPdf,
}: {
  meetingTitle: string;
  body: string;
  approved: boolean;
  downloadUrl: string | null;
  pdfPending: boolean;
  /** Auth-aware loader for portal-proxied PDFs (MinIO is not browser-reachable). */
  loadPdf?: () => Promise<Blob>;
}) {
  const { t } = useI18n();
  const stageRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0.72);
  const [resolvedUrl, setResolvedUrl] = useState<string | null>(null);
  const [pdfLoading, setPdfLoading] = useState(false);
  const previewDoc = useMemo(
    () => resolvePreviewDocument(body, meetingTitle),
    [body, meetingTitle],
  );

  useEffect(() => {
    const stage = stageRef.current;
    if (!stage) return;

    const update = () => {
      const w = stage.clientWidth - STAGE_PAD_X;
      if (w <= 0) return;
      setScale(Math.max(0.4, Math.min(w / A4_PAGE_WIDTH_PX, MAX_SCALE)));
    };

    update();
    const ro = new ResizeObserver(update);
    ro.observe(stage);
    window.addEventListener("resize", update);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", update);
    };
  }, [previewDoc?.sections.length]);

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;

    if (!downloadUrl) {
      setResolvedUrl(null);
      setPdfLoading(false);
      return () => {
        cancelled = true;
      };
    }

    const isPortalProxy =
      downloadUrl.startsWith("/api/v1/portal/") ||
      downloadUrl.includes("/api/v1/portal/meetings/");

    if (!isPortalProxy || !loadPdf) {
      setResolvedUrl(downloadUrl);
      setPdfLoading(false);
      return () => {
        cancelled = true;
      };
    }

    setPdfLoading(true);
    void loadPdf()
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setResolvedUrl(objectUrl);
        setPdfLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        setResolvedUrl(null);
        setPdfLoading(false);
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [downloadUrl, loadPdf]);

  const scaledW = A4_PAGE_WIDTH_PX * scale;
  const scaledH = A4_PAGE_HEIGHT_PX * scale;
  const showDraftOverlay = !approved;
  const title = previewDoc?.title.trim() || t("meeting.minutesUntitled");
  const downloadBusy = pdfPending || pdfLoading;
  return (
    <section
      className="overflow-hidden rounded-2xl border border-slate-300/80 bg-slate-500/25 shadow-inner"
      aria-label={t("meeting.pdfPreview.title")}
    >
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-300/60 bg-slate-900/90 px-4 py-2.5 text-white backdrop-blur">
        <div className="min-w-0">
          <h3 className="flex items-center gap-2 text-sm font-bold tracking-tight">
            <FileText className="h-4 w-4 shrink-0 text-sky-300" aria-hidden />
            {t("meeting.pdfPreview.title")}
          </h3>
          <p className="mt-0.5 text-[11px] text-white/60">{t("meeting.pdfPreview.hint")}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full bg-white/10 px-3 py-1 text-[10px] font-semibold uppercase tracking-wider text-white/80 ring-1 ring-white/20">
            A4 · {Math.round(scale * 100)}%
          </span>
          {resolvedUrl ? (
            <a
              href={resolvedUrl}
              target="_blank"
              rel="noreferrer"
              download="meeting-minutes.pdf"
              className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500 px-3.5 py-1.5 text-[11px] font-bold text-white shadow-md shadow-emerald-900/30 transition hover:bg-emerald-400"
            >
              <Download className="h-3.5 w-3.5" aria-hidden />
              {t("meeting.pdfDownload")}
            </a>
          ) : downloadBusy ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-400/20 px-3 py-1.5 text-[11px] font-semibold text-amber-100 ring-1 ring-amber-300/40">
              <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden />
              {t("meeting.pdfPending")}
            </span>
          ) : (
            <span className="rounded-full bg-white/10 px-3 py-1.5 text-[11px] font-semibold text-white/70 ring-1 ring-white/15">
              {t("meeting.pdfPreview.visualOnly")}
            </span>
          )}
        </div>
      </header>

      <div
        ref={stageRef}
        className="relative flex min-h-[34rem] items-start justify-center overflow-auto p-4 sm:min-h-[40rem] sm:p-5"
      >
        <div className="shrink-0" style={{ width: scaledW, height: scaledH }}>
          <div
            className="relative origin-top-left overflow-hidden rounded-sm bg-white shadow-2xl ring-1 ring-slate-900/15"
            style={{
              width: A4_PAGE_WIDTH_PX,
              height: A4_PAGE_HEIGHT_PX,
              transform: `scale(${scale})`,
            }}
          >
            {showDraftOverlay ? (
              <>
                <div
                  className="pointer-events-none absolute inset-x-0 top-0 z-30 flex items-center justify-center gap-2 bg-amber-500 px-4 py-2.5 text-center shadow-md"
                  role="status"
                >
                  <span className="rounded bg-white/25 px-2 py-0.5 text-[11px] font-black uppercase tracking-[0.18em] text-amber-950">
                    {t("meeting.noteDraftBadge")}
                  </span>
                  <span className="text-sm font-bold text-amber-950">
                    {t("meeting.pdfPreview.awaitingManagerApproval")}
                  </span>
                </div>
                <div
                  className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center overflow-hidden"
                  aria-hidden
                >
                  <div className="absolute inset-0 bg-[repeating-linear-gradient(-32deg,transparent,transparent_48px,rgba(225,29,72,0.04)_48px,rgba(225,29,72,0.04)_96px)]" />
                  <span className="select-none rotate-[-32deg] border-4 border-rose-500/40 px-8 py-3 text-[6.5rem] font-black uppercase leading-none tracking-[0.22em] text-rose-600/35 shadow-sm">
                    {t("meeting.noteDraftBadge")}
                  </span>
                </div>
              </>
            ) : null}

            {resolvedUrl ? (
              <object
                data={`${resolvedUrl}#toolbar=0&navpanes=0`}
                type="application/pdf"
                className={[
                  "relative z-10 h-full w-full bg-slate-100",
                  showDraftOverlay ? "pt-12" : "",
                ].join(" ")}
                aria-label={t("meeting.pdfPreview.title")}
              >
                <MinutesHtmlPreviewPage
                  title={title}
                  previewDoc={previewDoc}
                  showDraftPad={showDraftOverlay}
                />
              </object>
            ) : (
              <MinutesHtmlPreviewPage
                title={title}
                previewDoc={previewDoc}
                showDraftPad={showDraftOverlay}
              />
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function MinutesHtmlPreviewPage({
  title,
  previewDoc,
  showDraftPad,
}: {
  title: string;
  previewDoc: { title: string; sections: MinutesSection[] } | null;
  showDraftPad: boolean;
}) {
  const { t, tb } = useI18n();

  return (
    <div
      className={[
        // Keep footer pinned to the A4 page bottom; only the body scrolls.
        "relative z-10 flex h-full flex-col gap-3 overflow-hidden p-8 text-slate-700",
        showDraftPad ? "pt-14" : "",
      ].join(" ")}
    >
      <div className="shrink-0">
        <TemplateBrandHeader />
      </div>
      <div className="shrink-0 border-b border-slate-200 pb-3">
        <p className="text-[10px] font-bold uppercase tracking-[0.2em] text-slate-400">
          {t("meeting.minutesDocumentTitle")}
        </p>
        <h4 className="mt-1 font-display text-xl font-bold tracking-tight text-slate-900">
          {title}
        </h4>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto">
        {!previewDoc || previewDoc.sections.length === 0 ? (
          <p className="my-auto text-center text-sm text-slate-400">
            {t("meeting.pdfPreview.empty")}
          </p>
        ) : (
          previewDoc.sections.map((section) => (
            <PreviewSection
              key={section.type}
              section={section}
              label={tb("templateComponentType", section.type)}
            />
          ))
        )}
      </div>

      <div className="shrink-0 pt-1">
        <TemplateBrandFooter pageLabel={t("templates.preview.placeholder.page")} />
      </div>
    </div>
  );
}

function PreviewSection({
  section,
  label,
}: {
  section: MinutesSection;
  label: string;
}) {
  const { t } = useI18n();
  const parsed = parseSectionContent(section.value, section.kind);

  if (parsed.empty) return null;

  return (
    <section className="rounded-r-md border-l-2 border-violet-400 bg-slate-50/70 px-3 py-2">
      <h5 className="mb-1.5 text-[11px] font-bold uppercase tracking-wider text-violet-700">
        {label}
      </h5>
      {section.kind === "paragraph" ? (
        <p className="whitespace-pre-wrap text-xs leading-relaxed text-slate-800">
          {parsed.paragraph}
        </p>
      ) : (
        <ol className="space-y-1.5">
          {parsed.items.map((item, index) => {
            const meta = section.type === "ACTIONS" ? parseActionMeta(item) : { text: item };
            return (
              <li key={`${section.type}-${index}`} className="flex gap-2 text-xs leading-relaxed text-slate-800">
                <span className="mt-0.5 flex h-5 min-w-5 shrink-0 items-center justify-center rounded-full bg-violet-100 text-[10px] font-bold text-violet-800">
                  {index + 1}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="break-words">{meta.text}</p>
                  {"owner" in meta && (meta.owner || meta.due) ? (
                    <p className="mt-0.5 text-[10px] text-slate-500">
                      {[
                        meta.owner ? `${t("meeting.minutesOwner")}: ${meta.owner}` : null,
                        meta.due ? `${t("meeting.minutesDue")}: ${meta.due}` : null,
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                    </p>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
