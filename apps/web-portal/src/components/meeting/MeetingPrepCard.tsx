import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  CheckSquare,
  ClipboardList,
  Handshake,
  HelpCircle,
  Scale,
} from "lucide-react";
import { Link } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { MeetingPrepResponse } from "@/api/types";
import { useI18n } from "@/i18n";

export function MeetingPrepCard({
  meetingId,
  compact = false,
}: {
  meetingId: string;
  compact?: boolean;
}) {
  const api = useApi();
  const { t } = useI18n();
  const query = useQuery({
    queryKey: queryKeys.meetingPrep(meetingId),
    queryFn: () => api.getMeetingPrep(meetingId),
    enabled: Boolean(meetingId),
  });

  if (query.isLoading) {
    return (
      <section className="card-static p-4 text-sm text-slate-500" aria-label={t("meetingPrep.title")}>
        {t("meetingPrep.loading")}
      </section>
    );
  }
  if (query.isError || !query.data) {
    return null;
  }

  const data = query.data;
  const hasContext =
    data.previousDecisions.length +
      data.openActions.length +
      data.openRisks.length +
      data.openQuestions.length +
      data.overdueCommitments.length +
      data.contradictions.length +
      data.suggestedAgenda.length >
    0;
  if (!hasContext) return null;

  return (
    <section className="card-static overflow-hidden" aria-labelledby={`meeting-prep-${meetingId}`}>
      <div className="border-b border-slate-100 bg-gradient-to-r from-violet-50/80 to-sky-50/70 px-4 py-3 sm:px-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-violet-700">
              {t("meetingPrep.eyebrow")}
            </p>
            <h2 id={`meeting-prep-${meetingId}`} className="mt-1 font-semibold text-slate-900">
              {t("meetingPrep.title")}
            </h2>
          </div>
          {data.previousMeetingId ? (
            <Link
              to={`/meetings/${data.previousMeetingId}`}
              className="text-xs font-semibold text-violet-700 hover:text-violet-900"
            >
              {t("meetingPrep.openPrevious")}
            </Link>
          ) : null}
        </div>
      </div>

      <div className={`grid gap-3 p-4 sm:p-5 ${compact ? "md:grid-cols-2" : "lg:grid-cols-2"}`}>
        <PrepSection
          icon={<Scale className="h-4 w-4" />}
          title={t("meetingPrep.previousDecisions")}
          items={data.previousDecisions.map((item) => item.text)}
          empty={t("meetingPrep.empty")}
        />
        <PrepSection
          icon={<CheckSquare className="h-4 w-4" />}
          title={t("meetingPrep.openActions")}
          items={data.openActions.map((item) => item.text)}
          empty={t("meetingPrep.empty")}
        />
        <PrepSection
          icon={<Handshake className="h-4 w-4" />}
          title={t("meetingPrep.overdueCommitments")}
          items={data.overdueCommitments.map((item) => item.text)}
          empty={t("meetingPrep.empty")}
          tone="warn"
        />
        <PrepSection
          icon={<AlertTriangle className="h-4 w-4" />}
          title={t("meetingPrep.openRisks")}
          items={data.openRisks.map((item) => item.text)}
          empty={t("meetingPrep.empty")}
          tone="warn"
        />
        <PrepSection
          icon={<AlertTriangle className="h-4 w-4" />}
          title={t("meetingPrep.contradictions")}
          items={data.contradictions.map((item) => item.reason)}
          empty={t("meetingPrep.empty")}
          tone="warn"
        />
        <PrepSection
          icon={<HelpCircle className="h-4 w-4" />}
          title={t("meetingPrep.openQuestions")}
          items={data.openQuestions.map((item) => item.text)}
          empty={t("meetingPrep.empty")}
        />
        <PrepSection
          icon={<ClipboardList className="h-4 w-4" />}
          title={t("meetingPrep.suggestedAgenda")}
          items={data.suggestedAgenda.map((item) => item.text)}
          empty={t("meetingPrep.empty")}
          ordered
        />
      </div>
    </section>
  );
}

function PrepSection({
  icon,
  title,
  items,
  empty,
  tone = "default",
  ordered = false,
}: {
  icon: React.ReactNode;
  title: string;
  items: string[];
  empty: string;
  tone?: "default" | "warn";
  ordered?: boolean;
}) {
  const Tag = ordered ? "ol" : "ul";
  return (
    <div
      className={[
        "rounded-xl border p-3",
        tone === "warn" ? "border-amber-200 bg-amber-50/50" : "border-slate-200 bg-white/70",
      ].join(" ")}
    >
      <h3 className="flex items-center gap-2 text-xs font-bold uppercase tracking-wide text-slate-600">
        {icon}
        {title}
      </h3>
      {items.length ? (
        <Tag className={`mt-2 space-y-1.5 text-sm text-slate-800 ${ordered ? "list-decimal pl-5" : ""}`}>
          {items.slice(0, 6).map((item, index) => (
            <li key={`${index}-${item}`} className={ordered ? "" : "flex gap-2"}>
              {!ordered ? <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-violet-500" /> : null}
              <span>{item}</span>
            </li>
          ))}
        </Tag>
      ) : (
        <p className="mt-2 text-xs text-slate-400">{empty}</p>
      )}
    </div>
  );
}
