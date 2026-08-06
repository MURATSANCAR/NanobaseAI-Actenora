export type AttendanceBucket = "attended" | "absent" | "pending";

const ATTENDED = new Set(["JOINED", "LEFT"]);
const EXPLICIT_ABSENT = new Set(["ABSENT"]);

/**
 * Maps backend attendanceStatus. Only JOINED/LEFT count as attended and only
 * ABSENT as did-not-attend. DECLINED is only a calendar RSVP; a person can decline
 * and still join, so every unresolved RSVP stays pending until Teams attendance sync
 * writes an explicit outcome (do not invent absences).
 */
export function classifyAttendance(
  attendanceStatus: string | null | undefined,
  _meetingStatus?: string,
  _participantType?: string | null,
): AttendanceBucket {
  const status = (attendanceStatus || "UNKNOWN").toUpperCase();
  if (ATTENDED.has(status)) return "attended";
  if (EXPLICIT_ABSENT.has(status)) return "absent";
  return "pending";
}
