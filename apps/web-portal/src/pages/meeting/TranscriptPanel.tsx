import { useVirtualizer } from "@tanstack/react-virtual";
import { MessageSquare } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { EvidenceRef, MarkerKind, TranscriptSegment } from "@/api/types";
import { useI18n } from "@/i18n";
import { segmentEvidenceRef } from "@/lib/evidence";
import { evidenceScrollOffset, filterSegments, findEvidenceIndex } from "@/lib/filters";

const ROW_HEIGHT = 88;
const MARKERS: MarkerKind[] = ["DECISION", "ACTION", "RISK", "QUESTION", "IMPORTANT"];

const SPEAKER_PALETTE = [
  { bubble: "bg-violet-100 text-violet-950", avatar: "bg-violet-500", ring: "ring-violet-300" },
  { bubble: "bg-sky-100 text-sky-950", avatar: "bg-sky-500", ring: "ring-sky-300" },
  { bubble: "bg-emerald-100 text-emerald-950", avatar: "bg-emerald-500", ring: "ring-emerald-300" },
  { bubble: "bg-amber-100 text-amber-950", avatar: "bg-amber-500", ring: "ring-amber-300" },
  { bubble: "bg-rose-100 text-rose-950", avatar: "bg-rose-500", ring: "ring-rose-300" },
  { bubble: "bg-indigo-100 text-indigo-950", avatar: "bg-indigo-500", ring: "ring-indigo-300" },
];

const MARKER_TONE: Record<MarkerKind, string> = {
  DECISION: "bg-emerald-100 text-emerald-800 ring-emerald-200",
  ACTION: "bg-amber-100 text-amber-800 ring-amber-200",
  RISK: "bg-rose-100 text-rose-800 ring-rose-200",
  QUESTION: "bg-sky-100 text-sky-800 ring-sky-200",
  IMPORTANT: "bg-violet-100 text-violet-800 ring-violet-200",
};

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
  const { t, tb } = useI18n();
  const [speaker, setSpeaker] = useState("");
  const [q, setQ] = useState("");
  const [marker, setMarker] = useState<MarkerKind | "">("");
  const parentRef = useRef<HTMLDivElement>(null);

  const speakerColors = useMemo(() => {
    const map = new Map<string, (typeof SPEAKER_PALETTE)[number]>();
    speakers.forEach((s, i) => map.set(s, SPEAKER_PALETTE[i % SPEAKER_PALETTE.length]!));
    return map;
  }, [speakers]);

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
    <section className="card-static flex min-h-[28rem] flex-col gap-4 p-4 sm:p-5" aria-label={t("meeting.transcript")}>
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-white/60 pb-3">
        <div className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-sky-500 text-white shadow-md shadow-violet-200">
            <MessageSquare className="h-4 w-4" aria-hidden />
          </span>
          <div>
            <h2 className="text-sm font-bold text-slate-900">{t("meeting.transcriptLive")}</h2>
            <p className="text-xs text-slate-500">{t("meeting.transcriptLiveHint")}</p>
          </div>
        </div>
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
                {tb("markerKind", m)}
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

      <div
        ref={parentRef}
        className="min-h-[20rem] flex-1 overflow-auto rounded-2xl border border-white/70 bg-gradient-to-b from-white/70 to-violet-50/20 p-3"
        role="list"
      >
        <div style={{ height: `${virtualizer.getTotalSize()}px`, width: "100%", position: "relative" }}>
          {virtualizer.getVirtualItems().map((row) => {
            const seg = filtered[row.index]!;
            const active =
              highlightEvidence?.segmentId === seg.id || selectedSegmentId === seg.id;
            const playing = playbackSegmentId === seg.id;
            const palette = speakerColors.get(seg.speaker) ?? SPEAKER_PALETTE[0]!;
            const initials = speakerInitials(seg.speaker);

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
                  "absolute left-0 top-0 w-full cursor-pointer px-1 py-1.5 transition",
                  active ? "z-10" : "",
                ].join(" ")}
                style={{
                  height: `${row.size}px`,
                  transform: `translateY(${row.start}px)`,
                }}
              >
                <div className="flex gap-2.5">
                  <span
                    className={[
                      "mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[10px] font-bold text-white shadow-sm",
                      palette.avatar,
                    ].join(" ")}
                    aria-hidden
                  >
                    {initials}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex flex-wrap items-center gap-2">
                      <strong className="text-xs text-slate-800">{seg.speaker}</strong>
                      <span className="font-mono text-[10px] text-slate-400">{formatMs(seg.startMs)}</span>
                      {(seg.markers ?? []).map((m) => (
                        <span
                          key={m}
                          className={[
                            "rounded-full px-2 py-0.5 text-[9px] font-bold uppercase tracking-wide ring-1",
                            MARKER_TONE[m],
                          ].join(" ")}
                        >
                          {tb("markerKind", m)}
                        </span>
                      ))}
                    </div>
                    <div
                      className={[
                        "rounded-2xl rounded-tl-md px-3 py-2 text-sm leading-relaxed shadow-sm",
                        palette.bubble,
                        active ? `ring-2 ${palette.ring}` : "",
                        playing ? "ring-2 ring-teal-400 shadow-teal-100" : "",
                      ].join(" ")}
                    >
                      {seg.text}
                    </div>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function speakerInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return "?";
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return `${parts[0]![0] ?? ""}${parts[1]![0] ?? ""}`.toUpperCase();
}

function formatMs(ms: number): string {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${String(r).padStart(2, "0")}`;
}
