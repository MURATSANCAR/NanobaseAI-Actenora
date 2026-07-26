import type { TranscriptSegment } from "@/api/types";

export function findSegmentAtTime(
  segments: readonly TranscriptSegment[],
  playbackMs: number,
): TranscriptSegment | null {
  if (!segments.length || playbackMs < 0) return null;
  return (
    segments.find((s) => playbackMs >= s.startMs && playbackMs <= s.endMs) ??
    segments.find((s) => s.startMs <= playbackMs) ??
    null
  );
}

export function formatPlaybackClock(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}
