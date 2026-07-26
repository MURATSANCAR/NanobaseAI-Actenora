import { Sparkles, Globe } from "lucide-react";
import { useI18n } from "@/i18n";
import { PRODUCT_WEBSITE_LABEL, PRODUCT_WEBSITE_URL } from "@/config/brand";

export { PRODUCT_WEBSITE_URL, PRODUCT_WEBSITE_LABEL };

/**
 * Branded document header: EasyMeeting mark + wordmark + AI-generated slogan.
 */
export function TemplateBrandHeader({ compact = false }: { compact?: boolean }) {
  const { t } = useI18n();

  return (
    <div
      className={[
        "relative overflow-hidden rounded-lg bg-gradient-to-r from-violet-600 via-indigo-500 to-sky-500 text-white",
        compact ? "px-3 py-2" : "px-4 py-3",
      ].join(" ")}
    >
      <div className="pointer-events-none absolute -right-6 -top-8 h-20 w-20 rounded-full bg-white/15 blur-xl" aria-hidden />
      <div className="pointer-events-none absolute -bottom-10 left-10 h-16 w-16 rounded-full bg-sky-200/20 blur-xl" aria-hidden />
      <div className="relative flex items-center gap-3">
        <span
          className={[
            "flex shrink-0 items-center justify-center rounded-xl bg-white/20 ring-1 ring-white/40 backdrop-blur",
            compact ? "h-8 w-8" : "h-10 w-10",
          ].join(" ")}
          aria-hidden
        >
          <Sparkles className={compact ? "h-4 w-4" : "h-5 w-5"} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className={compact ? "text-sm font-bold tracking-tight" : "text-base font-bold tracking-tight"}>
              {t("brand.name")}
            </span>
            <span className="rounded-full bg-white/20 px-2 py-0.5 text-[9px] font-semibold uppercase tracking-wider ring-1 ring-white/30">
              {t("templates.brand.aiBadge")}
            </span>
          </div>
          <p className={compact ? "text-[10px] text-white/85" : "text-[11px] text-white/90"}>
            {t("templates.brand.slogan")}
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * Branded document footer: EasyMeeting mark + website link + AI note.
 */
export function TemplateBrandFooter({ pageLabel }: { pageLabel?: string }) {
  const { t } = useI18n();

  return (
    <div className="mt-auto flex items-center justify-between gap-2 rounded-lg bg-slate-900 px-3 py-2 text-white">
      <div className="flex items-center gap-2">
        <span className="flex h-5 w-5 items-center justify-center rounded-md bg-gradient-to-br from-violet-500 to-sky-500" aria-hidden>
          <Sparkles className="h-3 w-3" />
        </span>
        <span className="text-[10px] font-semibold">{t("brand.name")}</span>
        <span className="text-[9px] text-white/60">{t("templates.brand.footerNote")}</span>
      </div>
      <a
        href={PRODUCT_WEBSITE_URL}
        target="_blank"
        rel="noopener noreferrer"
        className="flex items-center gap-1 text-[10px] font-medium text-sky-300 hover:text-sky-200"
      >
        <Globe className="h-3 w-3" aria-hidden />
        {PRODUCT_WEBSITE_LABEL}
      </a>
      {pageLabel ? <span className="text-[9px] text-white/50">{pageLabel}</span> : null}
    </div>
  );
}
