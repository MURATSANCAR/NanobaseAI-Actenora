import type { MeetingDetailResponse, MeetingOccurrenceStatus } from "@/api/types";

const PROCESSING_STATUSES: MeetingOccurrenceStatus[] = ["PROCESSING", "SCHEDULED", "IN_PROGRESS"];

export function meetingNeedsProcessingPoll(detail: MeetingDetailResponse | undefined): boolean {
  if (!detail) return false;
  if (detail.partial) return true;
  return PROCESSING_STATUSES.includes(detail.meeting.status);
}

export const MEETING_PROCESSING_POLL_MS = 5_000;
