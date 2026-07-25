/** Pure helpers for transcript evidence navigation and filters (FAZ 24). */

export interface SegmentLike {
  id: string;
  speaker: string;
  text: string;
  startMs: number;
  endMs: number;
  markers?: string[];
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

export function findEvidenceIndex(
  segments: readonly SegmentLike[],
  segmentId: string,
): number {
  return segments.findIndex((s) => s.id === segmentId);
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
