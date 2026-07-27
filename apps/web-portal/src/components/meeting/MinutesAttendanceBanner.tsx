import { CheckCircle2, UserX, Users } from "lucide-react";
import type { Participant } from "@/api/types";
import { classifyAttendance } from "@/components/meeting/ParticipantAttendanceRow";
import { useI18n } from "@/i18n";

/** Attendance strip for the meeting minutes document header. */
export function MinutesAttendanceBanner({
  participants,
  meetingStatus,
}: {
  participants: Participant[];
  meetingStatus: string;
}) {
  const { t } = useI18n();
  if (!participants.length) return null;

  const attended = participants.filter(
    (p) => classifyAttendance(p.attendanceStatus, meetingStatus, p.participantType) === "attended",
  );
  const absent = participants.filter(
    (p) => classifyAttendance(p.attendanceStatus, meetingStatus, p.participantType) === "absent",
  );
  const pending = participants.filter(
    (p) => classifyAttendance(p.attendanceStatus, meetingStatus, p.participantType) === "pending",
  );

  return (
    <section
      className="meeting-note-attendance"
      aria-label={t("meeting.participants")}
    >
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-sky-500 text-white shadow-md">
          <Users className="h-4 w-4" aria-hidden />
        </span>
        <h3 className="font-display text-sm font-semibold text-slate-900">
          {t("meeting.participants")}
        </h3>
        <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-[11px] font-bold text-emerald-900">
          {t("meeting.attendanceSummary", {
            attended: attended.length,
            absent: absent.length,
          })}
        </span>
        {pending.length ? (
          <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-[11px] font-bold text-slate-700">
            {t("meeting.pendingGroup", { count: pending.length })}
          </span>
        ) : null}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <AttendanceColumn
          tone="attended"
          title={t("meeting.attendedGroup", { count: attended.length })}
          empty={t("meeting.attendedEmpty")}
          people={attended}
        />
        <AttendanceColumn
          tone="absent"
          title={t("meeting.absentGroup", { count: absent.length })}
          empty={t("meeting.absentEmpty")}
          people={absent}
        />
      </div>

      {pending.length ? (
        <div className="mt-3">
          <AttendanceColumn
            tone="pending"
            title={t("meeting.pendingGroup", { count: pending.length })}
            empty={t("meeting.pendingEmpty")}
            people={pending}
          />
        </div>
      ) : null}
    </section>
  );
}

function AttendanceColumn({
  tone,
  title,
  empty,
  people,
}: {
  tone: "attended" | "absent" | "pending";
  title: string;
  empty: string;
  people: Participant[];
}) {
  const { t, tb } = useI18n();

  return (
    <div
      className={[
        "rounded-2xl border p-3",
        tone === "attended"
          ? "border-emerald-200/80 bg-gradient-to-br from-emerald-50/90 via-white to-teal-50/40"
          : tone === "absent"
            ? "border-rose-200/80 bg-gradient-to-br from-rose-50/90 via-white to-red-50/40"
            : "border-slate-200/80 bg-gradient-to-br from-slate-50/90 via-white to-slate-50/40",
      ].join(" ")}
    >
      <p
        className={[
          "mb-2 flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-[0.12em]",
          tone === "attended"
            ? "text-emerald-800"
            : tone === "absent"
              ? "text-rose-800"
              : "text-slate-600",
        ].join(" ")}
      >
        {tone === "attended" ? (
          <CheckCircle2 className="h-3.5 w-3.5" aria-hidden />
        ) : tone === "absent" ? (
          <UserX className="h-3.5 w-3.5" aria-hidden />
        ) : null}
        {title}
      </p>
      {people.length ? (
        <ul className="space-y-1.5">
          {people.map((p) => {
            const name = p.displayName?.trim() || p.email || "—";
            return (
              <li
                key={p.id}
                className="flex flex-wrap items-center gap-1.5 rounded-xl border border-white/80 bg-white/85 px-2.5 py-1.5 text-sm shadow-sm"
              >
                <span
                  className={[
                    "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide",
                    tone === "attended"
                      ? "bg-emerald-500 text-white"
                      : tone === "absent"
                        ? "bg-rose-500 text-white"
                        : "bg-slate-400 text-white",
                  ].join(" ")}
                >
                  {tone === "attended" ? (
                    <span className="module-hero-live-dot !bg-white" aria-hidden />
                  ) : null}
                  {tone === "attended"
                    ? t("meeting.attended")
                    : tone === "absent"
                      ? t("meeting.didNotAttend")
                      : tb("attendanceStatus", (p.attendanceStatus || "UNKNOWN").toUpperCase())}
                </span>
                <span className="min-w-0 flex-1 font-medium text-slate-900">{name}</span>
                <span className="rounded-full bg-violet-100/90 px-2 py-0.5 text-[10px] font-semibold text-violet-900">
                  {tb("participantType", p.participantType)}
                </span>
              </li>
            );
          })}
        </ul>
      ) : (
        <p className="rounded-xl border border-dashed border-slate-200/80 bg-white/50 px-2.5 py-2 text-xs text-slate-500">
          {empty}
        </p>
      )}
    </div>
  );
}
