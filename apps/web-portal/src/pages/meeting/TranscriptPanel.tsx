import { useVirtualizer } from "@tanstack/react-virtual";
import { useEffect, useMemo, useRef, useState } from "react";
import type { EvidenceRef, MarkerKind, TranscriptSegment } from "../../api/types";
import { evidenceScrollOffset, filterSegments, findEvidenceIndex } from "../../lib/filters";

const ROW_HEIGHT = 72;

export function TranscriptPanel({
  segments,
  speakers,
  qualityFlags,
  highlightEvidence,
  onClearHighlight,
}: {
  segments: TranscriptSegment[];
  speakers: string[];
  qualityFlags: string[];
  highlightEvidence: EvidenceRef | null;
  onClearHighlight: () => void;
}) {
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
    <section className="panel transcript-panel" aria-label="Transcript">
      <header className="panel-head">
        <h2>Transcript</h2>
        {highlightEvidence ? (
          <button type="button" className="btn ghost" onClick={onClearHighlight}>
            Clear highlight
          </button>
        ) : null}
      </header>
      <div className="filter-bar compact" role="search">
        <label>
          Search
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Find in transcript"
            aria-label="Search transcript"
          />
        </label>
        <label>
          Speaker
          <select
            value={speaker}
            onChange={(e) => setSpeaker(e.target.value)}
            aria-label="Filter by speaker"
          >
            <option value="">All speakers</option>
            {speakers.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <label>
          Marker
          <select
            value={marker}
            onChange={(e) => setMarker(e.target.value as MarkerKind | "")}
            aria-label="Filter by marker"
          >
            <option value="">All markers</option>
            <option value="DECISION">Decision</option>
            <option value="ACTION">Action</option>
            <option value="RISK">Risk</option>
            <option value="QUESTION">Question</option>
            <option value="IMPORTANT">Important</option>
          </select>
        </label>
      </div>
      {qualityFlags.length ? (
        <ul className="flag-list" aria-label="Quality flags">
          {qualityFlags.map((f) => (
            <li key={f}>{f}</li>
          ))}
        </ul>
      ) : null}
      <div
        ref={parentRef}
        className="transcript-viewport"
        role="list"
        aria-label="Virtualized transcript"
      >
        <div
          style={{ height: `${virtualizer.getTotalSize()}px`, width: "100%", position: "relative" }}
        >
          {virtualizer.getVirtualItems().map((row) => {
            const seg = filtered[row.index]!;
            const active = highlightEvidence?.segmentId === seg.id;
            return (
              <article
                key={seg.id}
                role="listitem"
                className={active ? "transcript-row active" : "transcript-row"}
                data-segment-id={seg.id}
                style={{
                  position: "absolute",
                  top: 0,
                  left: 0,
                  width: "100%",
                  height: `${row.size}px`,
                  transform: `translateY(${row.start}px)`,
                }}
              >
                <div className="transcript-meta">
                  <strong>{seg.speaker}</strong>
                  <span className="muted">{formatMs(seg.startMs)}</span>
                  {(seg.markers ?? []).map((m) => (
                    <span key={m} className="marker">
                      {m}
                    </span>
                  ))}
                </div>
                <p>{seg.text}</p>
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
