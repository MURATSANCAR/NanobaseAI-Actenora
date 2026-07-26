import type { MeetingDetailResponse } from "@/api/types";

function downloadBlob(filename: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function exportMeetingDetailJson(detail: MeetingDetailResponse): void {
  const safeTitle = detail.meeting.title.replace(/[^\w.-]+/g, "_").slice(0, 60);
  const blob = new Blob([JSON.stringify(detail, null, 2)], { type: "application/json" });
  downloadBlob(`${safeTitle || "meeting"}-${detail.meeting.id}.json`, blob);
}

function csvEscape(value: string): string {
  if (/[",\n]/.test(value)) return `"${value.replace(/"/g, '""')}"`;
  return value;
}

export function exportMeetingSummaryCsv(detail: MeetingDetailResponse): void {
  const rows: string[][] = [
    ["type", "title", "status", "owner", "dueAt", "meetingId"],
    ...detail.decisions.map((d) => ["decision", d.title, d.status, "", "", d.meetingId]),
    ...detail.actions.map((a) => [
      "action",
      a.title,
      a.status,
      a.ownerDisplayName,
      a.dueAt ?? "",
      a.meetingId,
    ]),
    ...detail.commitments.map((c) => [
      "commitment",
      c.statement,
      c.status,
      c.ownerDisplayName,
      c.dueAt ?? "",
      c.meetingId,
    ]),
    ...detail.risks.map((r) => ["risk", r.title, r.severity, "", "", detail.meeting.id]),
  ];
  const csv = rows.map((row) => row.map((cell) => csvEscape(String(cell))).join(",")).join("\n");
  const safeTitle = detail.meeting.title.replace(/[^\w.-]+/g, "_").slice(0, 60);
  downloadBlob(`${safeTitle || "meeting"}-artifacts.csv`, new Blob([csv], { type: "text/csv" }));
}

export function exportTableCsv(
  filename: string,
  headers: string[],
  rows: string[][],
): void {
  const csv = [headers, ...rows]
    .map((row) => row.map((cell) => csvEscape(cell)).join(","))
    .join("\n");
  downloadBlob(filename, new Blob([csv], { type: "text/csv" }));
}
