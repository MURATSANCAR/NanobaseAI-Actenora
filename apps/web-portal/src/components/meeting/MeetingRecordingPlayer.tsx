import { Volume2 } from "lucide-react";
import { useEffect, useRef } from "react";
import type { MeetingRecording } from "@/api/types";
import { useI18n } from "@/i18n";
import { formatPlaybackClock } from "@/lib/recordingSync";

type MeetingRecordingPlayerProps = {
  recording: MeetingRecording | null | undefined;
  playbackMs: number;
  seekRequestMs: number | null;
  onTimeUpdate: (ms: number) => void;
  onSeekApplied: () => void;
};

export function MeetingRecordingPlayer({
  recording,
  playbackMs,
  seekRequestMs,
  onTimeUpdate,
  onSeekApplied,
}: MeetingRecordingPlayerProps) {
  const { t } = useI18n();
  const mediaRef = useRef<HTMLMediaElement>(null);
  const isVideo = recording?.contentType?.startsWith("video/") ?? false;

  useEffect(() => {
    if (seekRequestMs == null || !mediaRef.current) return;
    mediaRef.current.currentTime = seekRequestMs / 1000;
    onSeekApplied();
  }, [seekRequestMs, onSeekApplied]);

  if (!recording?.url) {
    return (
      <div className="card-static flex items-start gap-3 border-dashed border-violet-200/80 bg-violet-50/30 p-4 text-sm text-slate-600">
        <Volume2 className="mt-0.5 h-5 w-5 shrink-0 text-violet-500" aria-hidden />
        <div>
          <p className="font-semibold text-slate-800">{t("recording.unavailableTitle")}</p>
          <p className="mt-1 text-xs text-slate-500">{t("recording.unavailableHint")}</p>
        </div>
      </div>
    );
  }

  const durationMs = recording.durationMs ?? 0;

  return (
    <div className="card-static space-y-3 p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-xs font-bold uppercase tracking-wide text-violet-700">{t("recording.title")}</h3>
        <span className="font-mono text-xs text-slate-500">
          {formatPlaybackClock(playbackMs)}
          {durationMs > 0 ? ` / ${formatPlaybackClock(durationMs)}` : ""}
        </span>
      </div>

      {isVideo ? (
        <video
          ref={mediaRef as React.RefObject<HTMLVideoElement>}
          className="w-full rounded-xl bg-black/90"
          controls
          src={recording.url}
          onTimeUpdate={(e) => onTimeUpdate(Math.round(e.currentTarget.currentTime * 1000))}
        />
      ) : (
        <audio
          ref={mediaRef as React.RefObject<HTMLAudioElement>}
          className="w-full"
          controls
          src={recording.url}
          onTimeUpdate={(e) => onTimeUpdate(Math.round(e.currentTarget.currentTime * 1000))}
        />
      )}
    </div>
  );
}
