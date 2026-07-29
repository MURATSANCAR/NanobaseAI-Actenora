import { useVirtualizer } from "@tanstack/react-virtual";
import { MessageSquare } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import type { EvidenceRef, MarkerKind, TranscriptSegment } from "@/api/types";
import { useI18n } from "@/i18n";
import { segmentEvidenceRef } from "@/lib/evidence";
import {
  filterSegments,
  findEvidenceIndex,
  findTurnIndexBySegmentId,
  groupConsecutiveSpeakerTurns,
} from "@/lib/filters";

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

export function ConversationPanel({
  segments,
  speakers,
  qualityFlags,
  highlightEvidence,
  selectedSegmentId,
  onClearHighlight,
  onSegmentSelect,
}: {
  segments: TranscriptSegment[];
  speakers: string[];
  qualityFlags: string[];
  highlightEvidence: EvidenceRef | null;
  selectedSegmentId: string | null;
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

  const turns = useMemo(() => groupConsecutiveSpeakerTurns(segments), [segments]);

  const filtered = useMemo(
    () => filterSegments(turns, { speaker: speaker || undefined, q, marker: marker || undefined }),
    [turns, speaker, q, marker],
  );

  const visibleQualityFlags = useMemo(() => {
    const seen = new Set<string>();
    const out: string[] = [];
    for (const flag of qualityFlags) {
      const normalized = flag.trim().toUpperCase();
      if (!normalized || isInternalQualityFlag(normalized) || seen.has(normalized)) continue;
      seen.add(normalized);
      out.push(normalized);
    }
    return out;
  }, [qualityFlags]);

  const virtualizer = useVirtualizer({
    count: filtered.length,
    getScrollElement: () => parentRef.current,
    estimateSize: (index) => estimateTurnHeight(filtered[index]?.text ?? ""),
    overscan: 8,
    getItemKey: (index) => filtered[index]?.segmentIds.join("|") ?? index,
  });

  const highlightSegmentId = highlightEvidence?.segmentId ?? null;
  const scrolledHighlightRef = useRef<string | null>(null);

  useEffect(() => {
    if (!highlightSegmentId) {
      scrolledHighlightRef.current = null;
      return;
    }
    const inAll = findEvidenceIndex(segments, highlightSegmentId);
    if (inAll < 0) return;
    const idx = findTurnIndexBySegmentId(filtered, highlightSegmentId);
    if (idx < 0) {
      // Active filters hide the target segment — clear them so jump can land.
      setSpeaker("");
      setQ("");
      setMarker("");
      return;
    }
    if (scrolledHighlightRef.current === highlightSegmentId) return;
    scrolledHighlightRef.current = highlightSegmentId;
    // Defer until layout settles so we do not fight measureElement resize loops.
    // Use instant scroll — smooth + remasure fights and makes the thumb crawl.
    const frame = requestAnimationFrame(() => {
      virtualizer.scrollToIndex(idx, { align: "center", behavior: "auto" });
    });
    return () => cancelAnimationFrame(frame);
  }, [highlightSegmentId, filtered, segments, virtualizer]);

  return (
    <section
      id="meeting-conversation"
      className="card-static flex min-h-[28rem] flex-col gap-4 p-4 sm:p-5 scroll-mt-20"
      aria-label={t("meeting.conversation")}
    >
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-white/60 pb-3">
        <div className="flex items-center gap-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-sky-500 text-white shadow-md shadow-violet-200">
            <MessageSquare className="h-4 w-4" aria-hidden />
          </span>
          <div>
            <h2 className="text-sm font-bold text-slate-900">{t("meeting.conversationLive")}</h2>
            <p className="text-xs text-slate-500">{t("meeting.conversationLiveHint")}</p>
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
            placeholder={t("filter.conversationPlaceholder")}
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

      {visibleQualityFlags.length ? (
        <ul className="space-y-1 text-xs">
          {visibleQualityFlags.map((f) => (
            <li key={f} className="rounded-lg border-l-4 border-violet-500 bg-violet-50/50 px-3 py-1.5 text-violet-900">
              {tb("qualityFlag", f)}
            </li>
          ))}
        </ul>
      ) : null}

      <div
        ref={parentRef}
        className="conversation-scroll h-[min(32rem,calc(100dvh-16rem))] min-h-[20rem] shrink-0 overflow-x-hidden overflow-y-auto rounded-2xl border border-white/70 bg-gradient-to-b from-white/70 to-violet-50/20 p-3"
        role="list"
      >
        <div style={{ height: `${virtualizer.getTotalSize()}px`, width: "100%", position: "relative" }}>
          {virtualizer.getVirtualItems().map((row) => {
            const turn = filtered[row.index]!;
            const active =
              (highlightEvidence != null &&
                turnContainsSegment(turn.segmentIds, highlightEvidence.segmentId)) ||
              (selectedSegmentId != null && turnContainsSegment(turn.segmentIds, selectedSegmentId));
            const palette = speakerColors.get(turn.speaker) ?? SPEAKER_PALETTE[0]!;
            const initials = speakerInitials(turn.speaker);
            const markers = (turn.markers ?? []) as MarkerKind[];

            return (
              <article
                key={row.key}
                ref={virtualizer.measureElement}
                data-index={row.index}
                role="listitem"
                tabIndex={0}
                onClick={() =>
                  onSegmentSelect(
                    segmentEvidenceRef(turn.id, turn.startMs, turn.endMs, turn.text),
                  )
                }
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    onSegmentSelect(
                      segmentEvidenceRef(turn.id, turn.startMs, turn.endMs, turn.text),
                    );
                  }
                }}
                className={[
                  // Do not use Tailwind `transition` here — it animates `transform` and makes
                  // virtualized translateY updates look like a constantly moving scrollbar.
                  "absolute left-0 top-0 w-full cursor-pointer px-1 py-1.5",
                  active ? "z-10" : "",
                ].join(" ")}
                style={{
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
                      <strong className="text-xs text-slate-800">{turn.speaker}</strong>
                      <span className="font-mono text-[10px] text-slate-400">{formatMs(turn.startMs)}</span>
                      {markers
                        .filter((m): m is MarkerKind => MARKERS.includes(m as MarkerKind))
                        .map((m) => (
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
                        "rounded-2xl rounded-tl-md px-3 py-2.5 text-sm leading-relaxed shadow-sm",
                        palette.bubble,
                        active ? `ring-2 ${palette.ring}` : "",
                      ].join(" ")}
                    >
                      <p className="whitespace-pre-wrap break-words">{turn.text}</p>
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

function turnContainsSegment(segmentIds: readonly string[], segmentId: string): boolean {
  const needle = segmentId.trim().toLowerCase();
  return segmentIds.some((id) => id.trim().toLowerCase() === needle);
}

function estimateTurnHeight(text: string): number {
  // Header (avatar/name) + bubble padding + line wraps — keep close to measured height
  // so the scrollbar thumb does not jitter while measureElement corrects sizes.
  const lines = Math.max(1, Math.ceil(text.length / 64));
  return 88 + lines * 22;
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

/** Ops/version tokens and bare OTHER stay hidden; soft-degrade fallbacks are user-visible. */
function isInternalQualityFlag(flag: string): boolean {
  const normalized = flag.trim().toUpperCase();
  return (
    normalized === "OTHER" ||
    normalized.includes("LLM") ||
    normalized.startsWith("SV-") ||
    normalized.startsWith("PV-") ||
    normalized.startsWith("AUDITSTATUS=") ||
    normalized.startsWith("UNRESOLVEDCONFLICTCOUNT=") ||
    normalized.startsWith("GENERICACTIONCOUNT=") ||
    normalized.startsWith("UNSUPPORTEDITEMCOUNT=") ||
    normalized.startsWith("FALLBACKUSED=") ||
    normalized === "CONSISTENCY_AUDIT_PASSED" ||
    normalized === "DECISION_SUBSUMED_PROPOSAL_DROPPED" ||
    normalized === "AMBIGUOUS_ACTION_ENRICHMENT"
  );
}
