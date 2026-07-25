import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useApi } from "../../api/ApiProvider";
import { queryKeys } from "../../api/client";
import type { EvidenceRef } from "../../api/types";
import { AsyncState, PageHeader } from "../../components/ui/AsyncState";
import { MeetingCenterPanel } from "./MeetingCenterPanel";
import { MeetingLeftPanel } from "./MeetingLeftPanel";
import { TranscriptPanel } from "./TranscriptPanel";

export function MeetingDetailPage() {
  const { meetingId = "" } = useParams();
  const api = useApi();
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
        title="Meeting detail"
        description="Metadata, intelligence, and evidence-linked transcript."
        actions={
          <Link className="btn ghost" to="/meetings">
            Back to list
          </Link>
        }
      />
      <AsyncState
        status={status}
        error={detailQ.error}
        partialMessage="Meeting is still processing — some panels may be incomplete."
      >
        {detailQ.data ? (
          <div className="three-panel" role="region" aria-label="Meeting workspace">
            <MeetingLeftPanel detail={detailQ.data} />
            <MeetingCenterPanel detail={detailQ.data} onEvidence={setHighlight} />
            {transcriptQ.isLoading ? (
              <div className="panel" role="status">
                Loading transcript…
              </div>
            ) : transcriptQ.isError ? (
              <div className="panel async-error" role="alert">
                Transcript unavailable
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
