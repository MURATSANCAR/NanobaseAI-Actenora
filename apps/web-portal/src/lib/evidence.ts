import type {
  ActionItem,
  CommitmentItem,
  DecisionItem,
  EvidenceRef,
  MeetingDetailResponse,
  RiskItem,
} from "@/api/types";

export type LinkedArtifact =
  | { kind: "decision"; item: DecisionItem }
  | { kind: "action"; item: ActionItem }
  | { kind: "risk"; item: RiskItem }
  | { kind: "commitment"; item: CommitmentItem };

export function normalizeSegmentId(segmentId: string): string {
  return segmentId.trim().toLowerCase();
}

export function evidenceMatchesSegment(evidence: EvidenceRef, segmentId: string): boolean {
  return normalizeSegmentId(evidence.segmentId) === normalizeSegmentId(segmentId);
}

export function findArtifactsForSegment(
  detail: MeetingDetailResponse,
  segmentId: string,
): LinkedArtifact[] {
  const linked: LinkedArtifact[] = [];

  for (const item of detail.decisions) {
    if (item.evidence.some((e) => evidenceMatchesSegment(e, segmentId))) {
      linked.push({ kind: "decision", item });
    }
  }
  for (const item of detail.actions) {
    if (item.evidence.some((e) => evidenceMatchesSegment(e, segmentId))) {
      linked.push({ kind: "action", item });
    }
  }
  for (const item of detail.risks) {
    if (item.evidence.some((e) => evidenceMatchesSegment(e, segmentId))) {
      linked.push({ kind: "risk", item });
    }
  }
  for (const item of detail.commitments) {
    if (item.evidence.some((e) => evidenceMatchesSegment(e, segmentId))) {
      linked.push({ kind: "commitment", item });
    }
  }

  return linked;
}

export function formatEvidenceRange(startMs: number, endMs: number): string {
  return `${formatMs(startMs)}–${formatMs(endMs)}`;
}

function formatMs(ms: number): string {
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${m}:${String(r).padStart(2, "0")}`;
}

export function segmentEvidenceRef(
  segmentId: string,
  startMs: number,
  endMs: number,
  quote: string,
): EvidenceRef {
  return { segmentId, startMs, endMs, quote };
}
