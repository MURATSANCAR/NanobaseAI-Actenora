import { AlertCircle, CheckCircle2 } from "lucide-react";

export type FeedbackTone = "success" | "error";

export interface FeedbackMessage {
  text: string;
  tone: FeedbackTone;
}

/** Inline success/error notice with a distinct colour and icon per tone. */
export function InlineFeedback({ message }: { message: FeedbackMessage | null | undefined }) {
  if (!message) return null;
  const success = message.tone === "success";
  return (
    <p
      role="status"
      className={[
        "flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm",
        success
          ? "border-emerald-200 bg-emerald-50 text-emerald-800"
          : "border-red-200 bg-red-50 text-red-700",
      ].join(" ")}
    >
      {success ? (
        <CheckCircle2 className="h-4 w-4 shrink-0" aria-hidden />
      ) : (
        <AlertCircle className="h-4 w-4 shrink-0" aria-hidden />
      )}
      {message.text}
    </p>
  );
}
