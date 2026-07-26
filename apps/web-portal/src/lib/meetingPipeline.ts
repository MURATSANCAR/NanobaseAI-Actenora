import type { MeetingDetailResponse, MeetingOccurrenceStatus } from "@/api/types";

export type MeetingPipelineStageId =
  | "RECORDING"
  | "TRANSCRIPT"
  | "AI_ANALYSIS"
  | "NOTES"
  | "REVIEW";

export type MeetingPipelineStageState = "pending" | "active" | "done" | "failed";

export interface MeetingPipelineStage {
  id: MeetingPipelineStageId;
  state: MeetingPipelineStageState;
}

const POST_MEETING: MeetingOccurrenceStatus[] = ["ENDED", "PROCESSING", "READY", "FAILED"];

function noteHasContent(detail: MeetingDetailResponse): boolean {
  return detail.notes.some((n) => {
    const body = n.body.trim();
    if (!body) return false;
    if (body.startsWith("{")) {
      try {
        const parsed = JSON.parse(body) as { sections?: Record<string, string> };
        return Object.values(parsed.sections ?? {}).some((v) => v?.trim());
      } catch {
        return true;
      }
    }
    return true;
  });
}

function hasArtifacts(detail: MeetingDetailResponse): boolean {
  return (
    detail.decisions.length > 0 ||
    detail.actions.length > 0 ||
    detail.risks.length > 0 ||
    detail.commitments.length > 0
  );
}

/** Derives post-meeting pipeline stages from meeting detail and transcript availability. */
export function deriveMeetingPipelineStages(
  detail: MeetingDetailResponse,
  hasTranscript: boolean,
): MeetingPipelineStage[] {
  const status = detail.meeting.status;
  const failed = status === "FAILED";

  const recordingDone =
    Boolean(detail.recording?.url) || POST_MEETING.includes(status);
  const transcriptDone = hasTranscript;
  const analysisDone = hasArtifacts(detail) || noteHasContent(detail);
  const notesDone = noteHasContent(detail);
  const reviewDone = status === "READY" && !detail.partial;

  const processing =
    detail.partial || status === "PROCESSING" || (status === "ENDED" && !reviewDone);

  function stage(
    id: MeetingPipelineStageId,
    done: boolean,
    prerequisite: boolean,
  ): MeetingPipelineStage {
    if (failed && id === "REVIEW") return { id, state: "failed" };
    if (done) return { id, state: "done" };
    if (processing && prerequisite) return { id, state: "active" };
    return { id, state: "pending" };
  }

  return [
    stage("RECORDING", recordingDone, POST_MEETING.includes(status) || status === "IN_PROGRESS"),
    stage("TRANSCRIPT", transcriptDone, recordingDone),
    stage("AI_ANALYSIS", analysisDone, transcriptDone),
    stage("NOTES", notesDone, analysisDone || transcriptDone),
    stage("REVIEW", reviewDone, notesDone),
  ];
}

export function pipelineIsActive(stages: MeetingPipelineStage[]): boolean {
  return stages.some((s) => s.state === "active") || stages.some((s) => s.state === "pending" && s.id !== "REVIEW");
}
