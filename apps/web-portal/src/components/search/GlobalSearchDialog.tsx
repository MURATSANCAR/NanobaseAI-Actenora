import { useQuery } from "@tanstack/react-query";
import clsx from "clsx";
import { Loader2, Search, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { useI18n } from "@/i18n";
import {
  mergeSearchResults,
  searchGlobalKnowledge,
  searchMeetings,
  type SearchResult,
} from "@/lib/globalSearch";

type GlobalSearchDialogProps = {
  open: boolean;
  onClose: () => void;
};

export function GlobalSearchDialog({ open, onClose }: GlobalSearchDialogProps) {
  const api = useApi();
  const navigate = useNavigate();
  const { t, tb } = useI18n();
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);

  useEffect(() => {
    if (!open) return;
    setQuery("");
    setDebounced("");
    setActiveIndex(0);
    const timer = window.setTimeout(() => inputRef.current?.focus(), 0);
    return () => window.clearTimeout(timer);
  }, [open]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [query]);

  const enabled = open && debounced.length >= 2;

  const meetingsQ = useQuery({
    queryKey: queryKeys.meetings({ q: debounced, limit: 12 }),
    queryFn: () => api.listMeetings({ q: debounced, limit: 12 }),
    enabled,
  });
  const knowledgeQ = useQuery({
    queryKey: queryKeys.globalSearch({ q: debounced, limit: 20 }),
    queryFn: () => api.globalSearch({ q: debounced, limit: 20 }),
    enabled,
  });

  const results = useMemo(() => {
    if (!enabled) return [];
    return mergeSearchResults([
      searchMeetings(meetingsQ.data?.items ?? [], debounced),
      searchGlobalKnowledge(knowledgeQ.data?.items ?? []),
    ]);
  }, [
    enabled,
    debounced,
    meetingsQ.data?.items,
    knowledgeQ.data?.items,
  ]);

  useEffect(() => {
    setActiveIndex(0);
  }, [debounced, results.length]);

  const loading =
    enabled &&
    (meetingsQ.isFetching || knowledgeQ.isFetching);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (!results.length) return;
      if (event.key === "ArrowDown") {
        event.preventDefault();
        setActiveIndex((i) => Math.min(i + 1, results.length - 1));
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        setActiveIndex((i) => Math.max(i - 1, 0));
      }
      if (event.key === "Enter") {
        event.preventDefault();
        const hit = results[activeIndex];
        if (hit) {
          navigate(hit.href);
          onClose();
        }
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, results, activeIndex, navigate, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center bg-slate-900/45 p-4 pt-[12vh] backdrop-blur-[2px]"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="w-full max-w-2xl overflow-hidden rounded-2xl border border-slate-200/90 bg-white shadow-2xl shadow-slate-900/20"
        role="dialog"
        aria-modal="true"
        aria-label={t("search.title")}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 border-b border-slate-100 px-4 py-3">
          <Search className="h-5 w-5 shrink-0 text-violet-600" aria-hidden />
          <input
            ref={inputRef}
            className="min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-slate-400"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("search.placeholder")}
            aria-label={t("search.placeholder")}
          />
          {loading ? <Loader2 className="h-4 w-4 animate-spin text-violet-600" aria-hidden /> : null}
          <button type="button" className="rounded-lg p-1.5 text-slate-500 hover:bg-slate-100" onClick={onClose}>
            <X className="h-4 w-4" />
            <span className="sr-only">{t("search.close")}</span>
          </button>
        </div>

        <div className="max-h-[50vh] overflow-y-auto p-2">
          {debounced.length < 2 ? (
            <p className="px-3 py-6 text-center text-sm text-slate-500">{t("search.minChars")}</p>
          ) : loading && !results.length ? (
            <p className="px-3 py-6 text-center text-sm text-slate-500">{t("async.loading")}</p>
          ) : !results.length ? (
            <p className="px-3 py-6 text-center text-sm text-slate-500">{t("search.noResults")}</p>
          ) : (
            <ul className="space-y-1">
              {results.map((result, index) => (
                <SearchResultRow
                  key={`${result.kind}-${result.id}`}
                  result={result}
                  active={index === activeIndex}
                  kindLabel={t(`search.kind.${result.kind}`)}
                  subtitle={formatSubtitle(result, tb)}
                  onNavigate={() => {
                    navigate(result.href);
                    onClose();
                  }}
                  onHover={() => setActiveIndex(index)}
                />
              ))}
            </ul>
          )}
        </div>

        <div className="border-t border-slate-100 px-4 py-2 text-xs text-slate-500">
          {t("search.hint")}
        </div>
      </div>
    </div>
  );
}

function formatSubtitle(
  result: SearchResult,
  tb: (category: string, value: string) => string,
): string | undefined {
  if (!result.subtitle) return undefined;
  if (result.kind === "meeting") return tb("meetingStatus", result.subtitle);
  if (result.kind === "decision") return tb("artifactStatus", result.subtitle);
  return result.subtitle;
}

function SearchResultRow({
  result,
  active,
  kindLabel,
  subtitle,
  onNavigate,
  onHover,
}: {
  result: SearchResult;
  active: boolean;
  kindLabel: string;
  subtitle?: string;
  onNavigate: () => void;
  onHover: () => void;
}) {
  return (
    <li>
      <Link
        to={result.href}
        onClick={(e) => {
          e.preventDefault();
          onNavigate();
        }}
        onMouseEnter={onHover}
        className={clsx(
          "flex items-start gap-3 rounded-xl px-3 py-2.5 transition",
          active ? "bg-violet-100/80" : "hover:bg-violet-50/60",
        )}
      >
        <span className="mt-0.5 shrink-0 rounded-full bg-violet-100 px-2 py-0.5 text-[10px] font-bold uppercase text-violet-700">
          {kindLabel}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium text-slate-900">{result.title}</span>
          {subtitle ? <span className="block truncate text-xs text-slate-500">{subtitle}</span> : null}
        </span>
      </Link>
    </li>
  );
}

export function useGlobalSearchShortcut(onOpen: () => void): void {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        onOpen();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onOpen]);
}
