import { Fragment, useMemo, type ReactNode } from "react";
import { splitByPersonNames } from "@/lib/personNames";

/** Renders text with known person names wrapped in {@code <strong>}. */
export function HighlightPersonNames({
  text,
  names,
  strongClassName = "font-bold text-slate-950",
}: {
  text: string;
  names: string[];
  strongClassName?: string;
}): ReactNode {
  const segments = useMemo(() => splitByPersonNames(text, names), [text, names]);
  if (!text) return null;
  if (names.length === 0) return text;

  return (
    <>
      {segments.map((segment, index) =>
        segment.isName ? (
          <strong key={`n-${index}`} className={strongClassName}>
            {segment.text}
          </strong>
        ) : (
          <Fragment key={`t-${index}`}>{segment.text}</Fragment>
        ),
      )}
    </>
  );
}
