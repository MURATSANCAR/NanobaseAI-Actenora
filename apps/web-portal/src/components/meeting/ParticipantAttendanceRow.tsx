import { CheckCircle2, UserX, HelpCircle } from "lucide-react";
import type { Participant } from "@/api/types";
import { useI18n } from "@/i18n";

const ATTENDED = new Set(["JOINED", "LEFT"]);
const EXPLICIT_ABSENT = new Set(["ABSENT", "DECLINED"]);

export type AttendanceBucket = "attended" | "absent" | "pending";

/**
 * Maps backend attendanceStatus only — never invents "absent" from meeting end.
 * INVITED/ACCEPTED/TENTATIVE stay pending until Teams attendance sync writes JOINED/ABSENT.
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

export function ParticipantAttendanceRow({
  participant,
  meetingStatus,
}: {
  participant: Participant;
  meetingStatus: string;
}) {
  const { t, tb } = useI18n();
  const p = participant;
  const name = p.displayName?.trim() || p.email || "—";
  const showEmail = Boolean(p.email) && p.email.trim().toLowerCase() !== name.toLowerCase();
  const bucket = classifyAttendance(p.attendanceStatus, meetingStatus, p.participantType);

  const attendanceLabel =
    bucket === "attended"
      ? t("meeting.attended")
      : bucket === "absent"
        ? t("meeting.didNotAttend")
        : tb("attendanceStatus", (p.attendanceStatus || "UNKNOWN").toUpperCase());

  const shell =
    bucket === "attended"
      ? "border-emerald-200/90 bg-gradient-to-r from-emerald-50/95 to-white"
      : bucket === "absent"
        ? "border-rose-200/90 bg-gradient-to-r from-rose-50/90 to-white"
        : "border-slate-200/90 bg-gradient-to-r from-slate-50/90 to-white";

  const badge =
    bucket === "attended"
      ? "bg-emerald-500 text-white shadow-emerald-200"
      : bucket === "absent"
        ? "bg-rose-500 text-white shadow-rose-200"
        : "bg-slate-400 text-white shadow-slate-200";

  const BadgeIcon =
    bucket === "attended" ? CheckCircle2 : bucket === "absent" ? UserX : HelpCircle;

  return (
    <li className={["flex items-start gap-3 rounded-xl border px-3 py-2.5 shadow-sm", shell].join(" ")}>
      <span
        className={["mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl shadow-md", badge].join(
          " ",
        )}
        aria-hidden
      >
        <BadgeIcon className="h-4 w-4" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="font-semibold text-slate-900">{name}</div>
        {showEmail ? <div className="truncate text-xs text-slate-500">{p.email}</div> : null}
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          <span
            className={[
              "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wide",
              bucket === "attended"
                ? "bg-emerald-100 text-emerald-900"
                : bucket === "absent"
                  ? "bg-rose-100 text-rose-900"
                  : "bg-slate-100 text-slate-700",
            ].join(" ")}
          >
            {bucket === "attended" ? (
              <span className="module-hero-live-dot" aria-hidden />
            ) : null}
            {attendanceLabel}
          </span>
          <span className="rounded-full bg-violet-100/90 px-2.5 py-0.5 text-[11px] font-semibold text-violet-900">
            {tb("participantType", p.participantType)}
          </span>
          {p.external ? (
            <span className="rounded-full bg-sky-100/90 px-2.5 py-0.5 text-[11px] font-semibold text-sky-900">
              {t("meeting.external")}
            </span>
          ) : null}
        </div>
      </div>
    </li>
  );
}
