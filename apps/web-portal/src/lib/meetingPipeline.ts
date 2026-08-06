import type { MeetingDetailResponse, MeetingOccurrenceStatus } from "@/api/types";

export type MeetingPipelineStageId =
  | "CONVERSATION"
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

export function hasPendingNoteApprovals(detail: MeetingDetailResponse): boolean {
  return detail.approvalHistory.some((a) => a.status === "PENDING");
}

export function draftNotesNeedingSubmit(detail: MeetingDetailResponse) {
  return detail.notes.filter(
    (n) => n.draft || n.approvalStatus === "DRAFT" || n.approvalStatus === "CHANGES_REQUESTED",
  );
}

/** Derives post-meeting pipeline stages from meeting detail and conversation availability. */
export function deriveMeetingPipelineStages(
  detail: MeetingDetailResponse,
  hasConversation: boolean,
): MeetingPipelineStage[] {
  const status = detail.meeting.status;
  const failed = status === "FAILED";

  const conversationDone = hasConversation;
  const analysisDone = hasArtifacts(detail) || noteHasContent(detail);
  const notesDone = noteHasContent(detail);
  const pendingApprovals = hasPendingNoteApprovals(detail);
  const drafts = draftNotesNeedingSubmit(detail);
  const reviewDone = notesDone && drafts.length === 0 && !pendingApprovals;
  const reviewWaiting = notesDone && (drafts.length > 0 || pendingApprovals);

  const postMeetingStarted =
    POST_MEETING.includes(status) || status === "IN_PROGRESS" || hasConversation;

  // Only spin while the backend is genuinely working. A meeting that has ENDED
  // but produced nothing is stalled, not in-flight — surfacing it as "pending"
  // (a plain step, no spinner) avoids a perpetual fake spinner.
  const processing =
    detail.partial || status === "PROCESSING" || status === "IN_PROGRESS";

  const steps: Array<{ id: MeetingPipelineStageId; done: boolean; prerequisite: boolean }> = [
    { id: "CONVERSATION", done: conversationDone, prerequisite: postMeetingStarted },
    { id: "AI_ANALYSIS", done: analysisDone, prerequisite: conversationDone },
    { id: "NOTES", done: notesDone, prerequisite: analysisDone || conversationDone },
    { id: "REVIEW", done: reviewDone, prerequisite: notesDone },
  ];

  // On failure, mark the earliest incomplete stage as failed (that's where the
  // pipeline actually broke) and leave later stages pending — rather than always
  // blaming REVIEW.
  const firstIncomplete = failed
    ? steps.find((s) => !s.done)?.id ?? "REVIEW"
    : null;

  return steps.map(({ id, done, prerequisite }): MeetingPipelineStage => {
    if (done) return { id, state: "done" };
    if (failed) return { id, state: id === firstIncomplete ? "failed" : "pending" };
    if (id === "REVIEW" && reviewWaiting) return { id, state: "active" };
    if (processing && prerequisite) return { id, state: "active" };
    return { id, state: "pending" };
  });
}
