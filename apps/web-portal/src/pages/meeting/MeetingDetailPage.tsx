import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { EvidenceRef } from "@/api/types";
import { PageShell } from "@/components/qa/PageShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { useI18n } from "@/i18n";
import { MeetingCenterPanel } from "./MeetingCenterPanel";
import { MeetingLeftPanel } from "./MeetingLeftPanel";
import { TranscriptPanel } from "./TranscriptPanel";

export function MeetingDetailPage() {
  const { meetingId = "" } = useParams();
  const api = useApi();
  const { t } = useI18n();
  const [highlight, setHighlight] = useState<EvidenceRef | null>(null);

  const detailQ = useQuery({
    queryKey: queryKeys.meetingDetail(meetingId),
    queryFn: () => api.getMeetingDetail(meetingId),
    enabled: Boolean(meetingId),
  });

  const transcriptParams = useMemo(() => ({}), []);
  const transcriptQ = useQuery({
    queryKey: queryKeys.transcript(meetingId, transcriptParams),
    queryFn: () => api.getMeetingTranscript(meetingId, transcriptParams),
    enabled: Boolean(meetingId),
  });

  const status =
    detailQ.isLoading
      ? "loading"
      : detailQ.isError
        ? "error"
        : !detailQ.data
          ? "empty"
          : detailQ.data.partial
            ? "partial"
            : "ready";

  return (
    <PageShell
      titleKey="meeting.detailTitle"
      subtitleKey="meeting.detailDescription"
      maxWidth="max-w-[100rem]"
      heroTrailing={
        <Link to="/meetings" className="btn-secondary px-3 py-1.5 text-xs">
          <ArrowLeft className="h-4 w-4" />
          {t("meeting.backToList")}
        </Link>
      }
    >
      <AsyncState
        status={status}
        error={detailQ.error}
        partialMessage={t("meeting.processingPartial")}
      >
        {detailQ.data ? (
          <div className="grid min-h-[70vh] gap-3 xl:grid-cols-[18rem_minmax(0,1fr)_minmax(0,1.1fr)]">
            <MeetingLeftPanel detail={detailQ.data} />
            <MeetingCenterPanel detail={detailQ.data} onEvidence={setHighlight} />
            {transcriptQ.isLoading ? (
              <div className="card-static flex items-center justify-center p-8" role="status">
                {t("meeting.transcriptLoading")}
              </div>
            ) : transcriptQ.isError ? (
              <div className="card-static border-red-200/80 bg-red-50/40 p-6 text-red-700" role="alert">
                {t("meeting.transcriptUnavailable")}
              </div>
            ) : transcriptQ.data ? (
              <TranscriptPanel
                segments={transcriptQ.data.segments}
                speakers={transcriptQ.data.speakers}
                qualityFlags={detailQ.data.qualityFlags}
                highlightEvidence={highlight}
                onClearHighlight={() => setHighlight(null)}
              />
            ) : null}
          </div>
        ) : null}
      </AsyncState>
    </PageShell>
  );
}
