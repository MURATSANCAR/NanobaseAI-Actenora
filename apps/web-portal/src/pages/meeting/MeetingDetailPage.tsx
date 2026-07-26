import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Download } from "lucide-react";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { EvidenceRef } from "@/api/types";
import { MeetingHeaderBar } from "@/components/meeting/MeetingHeaderBar";
import { MeetingProgressPipeline } from "@/components/meeting/MeetingProgressPipeline";
import { PageShell } from "@/components/qa/PageShell";
import { AsyncState } from "@/components/ui/AsyncState";
import { useI18n } from "@/i18n";
import { exportMeetingDetailJson, exportMeetingSummaryCsv } from "@/lib/export";
import { findArtifactsForSegment } from "@/lib/evidence";
import {
  MEETING_PROCESSING_POLL_MS,
  meetingNeedsProcessingPoll,
} from "@/lib/meetingProcessing";
import { MeetingCenterPanel } from "./MeetingCenterPanel";
import { TranscriptPanel } from "./TranscriptPanel";

export function MeetingDetailPage() {
  const { meetingId = "" } = useParams();
  const api = useApi();
  const { t } = useI18n();
  const [highlight, setHighlight] = useState<EvidenceRef | null>(null);
  const [selectedSegmentId, setSelectedSegmentId] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | undefined>();

  const detailQ = useQuery({
    queryKey: queryKeys.meetingDetail(meetingId),
    queryFn: () => api.getMeetingDetail(meetingId),
    enabled: Boolean(meetingId),
    refetchInterval: (query) =>
      meetingNeedsProcessingPoll(query.state.data) ? MEETING_PROCESSING_POLL_MS : false,
  });

  useEffect(() => {
    if (detailQ.dataUpdatedAt && meetingNeedsProcessingPoll(detailQ.data)) {
      setLastUpdated(new Date(detailQ.dataUpdatedAt));
    }
  }, [detailQ.dataUpdatedAt, detailQ.data]);

  useEffect(() => {
    if (!selectedSegmentId || !detailQ.data) return;
    const linked = findArtifactsForSegment(detailQ.data, selectedSegmentId);
    const first = linked[0];
    if (!first) return;
    const id = `artifact-${first.kind}-${first.item.id}`;
    document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [selectedSegmentId, detailQ.data]);

  const transcriptParams = useMemo(() => ({}), []);
  const transcriptQ = useQuery({
    queryKey: queryKeys.transcript(meetingId, transcriptParams),
    queryFn: () => api.getMeetingTranscript(meetingId, transcriptParams),
    enabled: Boolean(meetingId),
  });

  const hasTranscript = Boolean(transcriptQ.data?.segments.length);

  const handleEvidence = (ref: EvidenceRef) => {
    setHighlight(ref);
    setSelectedSegmentId(ref.segmentId);
    // Bring the conversation panel into view — jump used to only scroll inside an off-screen list.
    requestAnimationFrame(() => {
      document
        .getElementById("meeting-conversation")
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  };

  const handleSegmentSelect = (ref: EvidenceRef) => {
    setSelectedSegmentId(ref.segmentId);
    setHighlight(ref);
  };

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
      maxWidth="max-w-5xl"
      heroTrailing={
        <div className="flex flex-wrap items-center gap-2">
          {detailQ.data ? (
            <>
              <button
                type="button"
                className="btn-secondary px-3 py-1.5 text-xs"
                onClick={() => exportMeetingSummaryCsv(detailQ.data!)}
              >
                <Download className="h-4 w-4" />
                {t("export.csv")}
              </button>
              <button
                type="button"
                className="btn-secondary px-3 py-1.5 text-xs"
                onClick={() => exportMeetingDetailJson(detailQ.data!)}
              >
                <Download className="h-4 w-4" />
                {t("export.json")}
              </button>
            </>
          ) : null}
          <Link to="/meetings" className="btn-secondary px-3 py-1.5 text-xs">
            <ArrowLeft className="h-4 w-4" />
            {t("meeting.backToList")}
          </Link>
        </div>
      }
    >
      <AsyncState
        status={status}
        error={detailQ.error}
        partialMessage={t("meeting.processingPartial")}
      >
        {detailQ.data ? (
          <div className="space-y-5 pb-6">
            <MeetingHeaderBar detail={detailQ.data} />

            <MeetingProgressPipeline
              detail={detailQ.data}
              hasTranscript={hasTranscript}
              lastUpdated={lastUpdated}
            />

            <MeetingCenterPanel
              detail={detailQ.data}
              onEvidence={handleEvidence}
              selectedSegmentId={selectedSegmentId}
              hasTranscript={hasTranscript}
            />

            {transcriptQ.isLoading ? (
              <div className="card-static flex items-center justify-center p-10" role="status">
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
                selectedSegmentId={selectedSegmentId}
                onClearHighlight={() => {
                  setHighlight(null);
                  setSelectedSegmentId(null);
                }}
                onSegmentSelect={handleSegmentSelect}
              />
            ) : null}
          </div>
        ) : null}
      </AsyncState>
    </PageShell>
  );
}
