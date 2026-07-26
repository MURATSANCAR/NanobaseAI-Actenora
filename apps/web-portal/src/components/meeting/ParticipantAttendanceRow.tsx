import type { Participant } from "@/api/types";
import { useI18n } from "@/i18n";

const ATTENDED = new Set(["JOINED", "LEFT"]);
const ABSENT = new Set(["ABSENT", "DECLINED"]);

export function ParticipantAttendanceRow({
  participant,
  meetingStatus: _meetingStatus,
}: {
  participant: Participant;
  meetingStatus: string;
}) {
  const { t, tb } = useI18n();
  const p = participant;
  const name = p.displayName?.trim() || p.email || "—";
  const showEmail = Boolean(p.email) && p.email.trim().toLowerCase() !== name.toLowerCase();
  const normalized = (p.attendanceStatus || "UNKNOWN").toUpperCase();
  const attended = ATTENDED.has(normalized);
  const knownAbsent = ABSENT.has(normalized);
  const attendanceLabel = attended
    ? t("meeting.attended")
    : knownAbsent
      ? t("meeting.didNotAttend")
      : tb("attendanceStatus", normalized);

  return (
    <li className="flex items-start justify-between gap-2 rounded-lg bg-white/50 px-2.5 py-1.5">
      <div className="min-w-0">
        <div className="font-medium text-slate-800">{name}</div>
        {showEmail ? <div className="truncate text-xs text-slate-500">{p.email}</div> : null}
      </div>
      <div className="shrink-0 text-right text-xs text-slate-500">
        <div>
          {tb("participantType", p.participantType)}
          {p.external ? ` · ${t("meeting.external")}` : ""}
        </div>
        <div
          className={
            attended
              ? "font-semibold text-emerald-700"
              : knownAbsent
                ? "font-semibold text-amber-700"
                : "text-slate-500"
          }
        >
          {attendanceLabel}
        </div>
      </div>
    </li>
  );
}
