import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApi } from "../../api/ApiProvider";
import { queryKeys } from "../../api/client";
import type { EvidenceRef } from "../../api/types";
import { AsyncState, PageHeader } from "../../components/ui/AsyncState";
import { useI18n } from "../../i18n";
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
    <section className="page meeting-detail-page">
      <PageHeader
        title={t("meeting.detailTitle")}
        description={t("meeting.detailDescription")}
        actions={
          <Link className="btn ghost" to="/meetings">
            {t("meeting.backToList")}
          </Link>
        }
      />
      <AsyncState
        status={status}
        error={detailQ.error}
        partialMessage={t("meeting.processingPartial")}
      >
        {detailQ.data ? (
          <div className="three-panel" role="region" aria-label={t("meeting.intelligence")}>
            <MeetingLeftPanel detail={detailQ.data} />
            <MeetingCenterPanel detail={detailQ.data} onEvidence={setHighlight} />
            {transcriptQ.isLoading ? (
              <div className="panel" role="status">
                {t("meeting.transcriptLoading")}
              </div>
            ) : transcriptQ.isError ? (
              <div className="panel async-error" role="alert">
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
    </section>
  );
}
