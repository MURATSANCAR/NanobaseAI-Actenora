import { useMutation } from "@tanstack/react-query";
import { MessageCircleQuestion, Search, Sparkles, Trash2 } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { useApi } from "@/api/ApiProvider";
import type { EvidenceRef, MeetingQuestionResponse } from "@/api/types";
import { useI18n } from "@/i18n";

type AnswerEntry = {
  question: string;
  response: MeetingQuestionResponse;
};

const QA_STORAGE_PREFIX = "actenora-meeting-qa-v1:";

function qaStorageKey(meetingId: string): string {
  return `${QA_STORAGE_PREFIX}${meetingId}`;
}

function readStoredAnswers(key: string): AnswerEntry[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as AnswerEntry[]) : [];
  } catch {
    return [];
  }
}

function writeStoredAnswers(key: string, answers: AnswerEntry[]): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, JSON.stringify(answers));
  } catch {
    /* storage unavailable (private mode / quota) — persistence is best-effort */
  }
}

export function MeetingQuestionPanel({
  meetingId,
  enabled,
  onEvidence,
}: {
  meetingId: string;
  enabled: boolean;
  onEvidence: (evidence: EvidenceRef) => void;
}) {
  const api = useApi();
  const { t } = useI18n();
  const storageKey = qaStorageKey(meetingId);
  const [question, setQuestion] = useState("");
  const [answers, setAnswers] = useState<AnswerEntry[]>(() => readStoredAnswers(storageKey));

  // Reload persisted history when switching between meetings without a remount.
  useEffect(() => {
    setAnswers(readStoredAnswers(storageKey));
  }, [storageKey]);

  const mutation = useMutation({
    mutationFn: (input: string) => api.askMeeting(meetingId, input),
    onSuccess: (response, input) => {
      setAnswers((current) => {
        const next = [{ question: input, response }, ...current];
        writeStoredAnswers(storageKey, next);
        return next;
      });
      setQuestion("");
    },
  });

  const clearHistory = () => {
    setAnswers([]);
    writeStoredAnswers(storageKey, []);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const value = question.trim();
    if (!value || mutation.isPending) return;
    mutation.mutate(value);
  };

  return (
    <section className="card-static space-y-4 p-4 sm:p-5" aria-labelledby="meeting-question-title">
      <div className="flex items-start gap-3">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-sky-500 text-white">
          <MessageCircleQuestion className="h-4 w-4" />
        </span>
        <div>
          <h2 id="meeting-question-title" className="font-semibold text-slate-900">
            {t("meetingQuestion.title")}
          </h2>
          <p className="text-xs text-slate-500">{t("meetingQuestion.hint")}</p>
        </div>
      </div>

      <form className="flex flex-col gap-2 sm:flex-row" onSubmit={submit}>
        <label className="min-w-0 flex-1">
          <span className="sr-only">{t("meetingQuestion.placeholder")}</span>
          <input
            className="input-field"
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder={t("meetingQuestion.placeholder")}
            disabled={!enabled || mutation.isPending}
          />
        </label>
        <button
          type="submit"
          className="btn-primary shrink-0"
          disabled={!enabled || !question.trim() || mutation.isPending}
        >
          {mutation.isPending ? <Sparkles className="h-4 w-4 animate-pulse" /> : <Search className="h-4 w-4" />}
          {mutation.isPending ? t("meetingQuestion.answering") : t("meetingQuestion.ask")}
        </button>
      </form>

      {!enabled ? <p className="text-sm text-slate-500">{t("meetingQuestion.noTranscript")}</p> : null}
      {mutation.isError ? (
        <p className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700" role="alert">
          {mutation.error.message}
        </p>
      ) : null}

      {answers.length ? (
        <div className="flex justify-end">
          <button
            type="button"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-500 transition hover:text-slate-700"
            onClick={clearHistory}
          >
            <Trash2 className="h-3.5 w-3.5" aria-hidden />
            {t("meeting.qaClear")}
          </button>
        </div>
      ) : null}

      {answers.length ? (
        <ol className="space-y-3">
          {answers.map((entry, index) => (
            <li key={`${index}-${entry.question}`} className="rounded-xl border border-slate-200 bg-white/75 p-4">
              <p className="text-xs font-semibold text-violet-700">{entry.question}</p>
              {entry.response.status === "ANSWERED" && entry.response.answer ? (
                <>
                  <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
                    {entry.response.answer}
                  </p>
                  <div className="mt-3 space-y-2">
                    <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
                      {t("meetingQuestion.evidence")}
                    </p>
                    {entry.response.citations.map((citation) => (
                      <button
                        key={citation.segmentId}
                        type="button"
                        className="block w-full rounded-lg border border-violet-100 bg-violet-50/50 p-3 text-left transition hover:border-violet-300"
                        onClick={() =>
                          onEvidence({
                            segmentId: citation.segmentId,
                            startMs: citation.startMs,
                            endMs: citation.endMs,
                            quote: citation.quote,
                          })
                        }
                      >
                        <span className="block text-xs font-semibold text-violet-800">
                          {citation.speaker} · {formatMs(citation.startMs)}
                        </span>
                        <span className="mt-1 block line-clamp-2 text-xs text-slate-600">
                          {citation.quote}
                        </span>
                        <span className="mt-2 block text-xs font-semibold text-violet-700">
                          {t("meetingQuestion.goToEvidence")}
                        </span>
                      </button>
                    ))}
                  </div>
                </>
              ) : (
                <p className="mt-2 text-sm text-slate-600">{t("meetingQuestion.insufficient")}</p>
              )}
            </li>
          ))}
        </ol>
      ) : null}
    </section>
  );
}

function formatMs(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}
