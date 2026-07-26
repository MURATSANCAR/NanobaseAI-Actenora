import { useVirtualizer } from "@tanstack/react-virtual";
import { useEffect, useMemo, useRef, useState } from "react";
import type { EvidenceRef, MarkerKind, TranscriptSegment } from "@/api/types";
import { useI18n } from "@/i18n";
import { segmentEvidenceRef } from "@/lib/evidence";
import { evidenceScrollOffset, filterSegments, findEvidenceIndex } from "@/lib/filters";

const ROW_HEIGHT = 72;
const MARKERS: MarkerKind[] = ["DECISION", "ACTION", "RISK", "QUESTION", "IMPORTANT"];

export function TranscriptPanel({
  segments,
  speakers,
  qualityFlags,
  highlightEvidence,
  selectedSegmentId,
  playbackSegmentId,
  onClearHighlight,
  onSegmentSelect,
}: {
  segments: TranscriptSegment[];
  speakers: string[];
  qualityFlags: string[];
  highlightEvidence: EvidenceRef | null;
  selectedSegmentId: string | null;
  playbackSegmentId?: string | null;
  onClearHighlight: () => void;
  onSegmentSelect: (ref: EvidenceRef) => void;
}) {
  const { t } = useI18n();
  const [speaker, setSpeaker] = useState("");
  const [q, setQ] = useState("");
  const [marker, setMarker] = useState<MarkerKind | "">("");
  const parentRef = useRef<HTMLDivElement>(null);

  const filtered = useMemo(
    () => filterSegments(segments, { speaker: speaker || undefined, q, marker: marker || undefined }),
    [segments, speaker, q, marker],
  );

  const virtualizer = useVirtualizer({
    count: filtered.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 8,
  });

  useEffect(() => {
    if (!highlightEvidence) return;
    const idx = findEvidenceIndex(filtered, highlightEvidence.segmentId);
    if (idx < 0 || !parentRef.current) return;
    const top = evidenceScrollOffset(idx, ROW_HEIGHT, parentRef.current.clientHeight);
    parentRef.current.scrollTo({ top, behavior: "smooth" });
  }, [highlightEvidence, filtered]);

  return (
    <section className="card-static flex max-h-[calc(100dvh-12rem)] min-h-[22rem] flex-col gap-3 p-4 sm:p-5" aria-label={t("meeting.transcript")}>
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-white/60 pb-3">
        <h2 className="text-sm font-bold uppercase tracking-wide text-slate-500">{t("meeting.transcript")}</h2>
        {highlightEvidence ? (
          <button type="button" className="btn-secondary px-3 py-1.5 text-xs" onClick={onClearHighlight}>
            {t("meeting.clearHighlight")}
          </button>
        ) : null}
      </header>

      <div className="mobile-toolbar" role="search">
        <label className="min-w-[10rem] flex-1">
          <span className="label-text">{t("filter.search")}</span>
          <input
            className="input-field"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t("filter.transcriptPlaceholder")}
          />
        </label>
        <label className="min-w-[10rem]">
          <span className="label-text">{t("filter.speaker")}</span>
          <select className="input-field" value={speaker} onChange={(e) => setSpeaker(e.target.value)}>
            <option value="">{t("filter.allSpeakers")}</option>
            {speakers.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label className="min-w-[10rem]">
          <span className="label-text">{t("filter.marker")}</span>
          <select
            className="input-field"
            value={marker}
            onChange={(e) => setMarker(e.target.value as MarkerKind | "")}
          >
            <option value="">{t("filter.allMarkers")}</option>
            {MARKERS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </label>
      </div>

      {qualityFlags.length ? (
        <ul className="space-y-1 text-xs">
          {qualityFlags.map((f) => (
            <li key={f} className="rounded-lg border-l-4 border-violet-500 bg-violet-50/50 px-3 py-1.5 text-violet-900">
              {f}
            </li>
          ))}
        </ul>
      ) : null}

      <div ref={parentRef} className="min-h-0 flex-1 overflow-auto rounded-xl border border-white/70 bg-white/60" role="list">
        <div style={{ height: `${virtualizer.getTotalSize()}px`, width: "100%", position: "relative" }}>
          {virtualizer.getVirtualItems().map((row) => {
            const seg = filtered[row.index]!;
            const active =
              highlightEvidence?.segmentId === seg.id || selectedSegmentId === seg.id;
            const playing = playbackSegmentId === seg.id;
            return (
              <article
                key={seg.id}
                role="listitem"
                tabIndex={0}
                onClick={() =>
                  onSegmentSelect(segmentEvidenceRef(seg.id, seg.startMs, seg.endMs, seg.text))
                }
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    onSegmentSelect(segmentEvidenceRef(seg.id, seg.startMs, seg.endMs, seg.text));
                  }
                }}
                className={[
                  "cursor-pointer border-b border-white/60 px-3 py-2 text-sm transition",
                  active
                    ? "bg-violet-100/70 ring-1 ring-inset ring-violet-300"
                    : playing
                      ? "bg-teal-50/80 ring-1 ring-inset ring-teal-200"
                      : "hover:bg-violet-50/40",
                ].join(" ")}
                style={{
                  position: "absolute",
                  top: 0,
                  left: 0,
                  width: "100%",
                  height: `${row.size}px`,
                  transform: `translateY(${row.start}px)`,
                }}
              >
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <strong className="text-slate-800">{seg.speaker}</strong>
                  <span className="font-mono text-slate-500">{formatMs(seg.startMs)}</span>
                  {(seg.markers ?? []).map((m) => (
                    <span key={m} className="rounded-full bg-violet-100 px-2 py-0.5 text-[10px] font-semibold text-violet-700">
                      {m}
                    </span>
                  ))}
                </div>
                <p className="mt-1 text-slate-700">{seg.text}</p>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function formatMs(ms: number): string {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${String(r).padStart(2, "0")}`;
}
