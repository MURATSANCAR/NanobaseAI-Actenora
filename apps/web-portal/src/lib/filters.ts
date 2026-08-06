/** Pure helpers for meeting conversation evidence navigation and filters (FAZ 24). */

export interface SegmentLike {
  id: string;
  speaker: string;
  text: string;
  startMs: number;
  endMs: number;
  markers?: string[];
  speakerResolutionStatus?: string;
  speakerConfidence?: number;
  speakerReviewRequired?: boolean;
}

/** Consecutive same-speaker utterances merged into one readable turn/paragraph. */
export interface SpeakerTurn<T extends SegmentLike = SegmentLike> extends SegmentLike {
  segmentIds: string[];
  segments: T[];
}

export function filterSegments<T extends SegmentLike>(
  segments: readonly T[],
  opts: { speaker?: string; q?: string; marker?: string } = {},
): T[] {
  const q = opts.q?.trim().toLowerCase();
  return segments.filter((s) => {
    if (opts.speaker && s.speaker !== opts.speaker) return false;
    if (opts.marker && !(s.markers ?? []).includes(opts.marker)) return false;
    if (q && !s.text.toLowerCase().includes(q) && !s.speaker.toLowerCase().includes(q)) {
      return false;
    }
    return true;
  });
}

/**
 * Collapses consecutive ASR fragments from the same speaker into paragraph turns
 * so the conversation reads as continuous speech rather than one bubble per clause.
 */
export function groupConsecutiveSpeakerTurns<T extends SegmentLike>(
  segments: readonly T[],
): SpeakerTurn<T>[] {
  const turns: SpeakerTurn<T>[] = [];
  for (const seg of segments) {
    const prev = turns[turns.length - 1];
    if (prev && sameSpeaker(prev.speaker, seg.speaker)) {
      prev.segments.push(seg);
      prev.segmentIds.push(seg.id);
      prev.text = joinUtteranceText(prev.text, seg.text);
      prev.endMs = Math.max(prev.endMs, seg.endMs);
      prev.markers = mergeMarkers(prev.markers, seg.markers);
      prev.speakerConfidence = Math.min(
        prev.speakerConfidence ?? 1,
        seg.speakerConfidence ?? 1,
      );
      prev.speakerReviewRequired = Boolean(prev.speakerReviewRequired || seg.speakerReviewRequired);
      if (seg.speakerReviewRequired) prev.speakerResolutionStatus = seg.speakerResolutionStatus;
      continue;
    }
    turns.push({
      id: seg.id,
      segmentIds: [seg.id],
      speaker: seg.speaker,
      text: seg.text.trim(),
      startMs: seg.startMs,
      endMs: seg.endMs,
      markers: [...(seg.markers ?? [])],
      segments: [seg],
    });
  }
  return turns;
}

export function normalizeSegmentId(segmentId: string): string {
  return segmentId.trim().toLowerCase();
}

export function findEvidenceIndex(
  segments: readonly SegmentLike[],
  segmentId: string,
): number {
  const needle = normalizeSegmentId(segmentId);
  return segments.findIndex((s) => normalizeSegmentId(s.id) === needle);
}

/** Finds the turn that contains a raw transcript segment id (any fragment in the turn). */
export function findTurnIndexBySegmentId(
  turns: readonly Pick<SpeakerTurn, "segmentIds">[],
  segmentId: string,
): number {
  const needle = normalizeSegmentId(segmentId);
  return turns.findIndex((turn) =>
    turn.segmentIds.some((id) => normalizeSegmentId(id) === needle),
  );
}

function sameSpeaker(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

function joinUtteranceText(left: string, right: string): string {
  const a = left.trim();
  const b = right.trim();
  if (!a) return b;
  if (!b) return a;
  return `${a} ${b}`;
}

function mergeMarkers(
  left: readonly string[] | undefined,
  right: readonly string[] | undefined,
): string[] {
  const out: string[] = [];
  const seen = new Set<string>();
  for (const m of [...(left ?? []), ...(right ?? [])]) {
    if (seen.has(m)) continue;
    seen.add(m);
    out.push(m);
  }
  return out;
}

/**
 * Returns the scroll offset (px) to bring a segment into view given fixed row height.
 */
export function evidenceScrollOffset(
  index: number,
  rowHeight: number,
  viewportHeight: number,
): number {
  if (index < 0) return 0;
  const center = index * rowHeight - viewportHeight / 2 + rowHeight / 2;
  return Math.max(0, center);
}

export function applyMeetingListFilter<T extends { title: string; status: string }>(
  items: readonly T[],
  opts: { q?: string; status?: string } = {},
): T[] {
  const q = opts.q?.trim().toLowerCase();
  return items.filter((item) => {
    if (opts.status && item.status !== opts.status) return false;
    if (q && !item.title.toLowerCase().includes(q)) return false;
    return true;
  });
}

export function paginateCursor<T>(
  items: readonly T[],
  cursor: string | undefined,
  limit: number,
): { items: T[]; nextCursor: string | null } {
  const start = cursor ? Number.parseInt(cursor, 10) || 0 : 0;
  const slice = items.slice(start, start + limit);
  const next = start + limit < items.length ? String(start + limit) : null;
  return { items: [...slice], nextCursor: next };
}
