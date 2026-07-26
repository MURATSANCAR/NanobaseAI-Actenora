import clsx from "clsx";
import { Mic } from "lucide-react";
import type { ReactNode } from "react";
import { useI18n, type MessageKey } from "@/i18n";

type ModuleAiHeroProps = {
  size?: "dashboard" | "page";
  titleKey: MessageKey;
  subtitleKey: MessageKey;
  trailing?: ReactNode;
};

export function ModuleAiHero({
  size = "page",
  titleKey,
  subtitleKey,
  trailing,
}: ModuleAiHeroProps) {
  const { t } = useI18n();
  const isDashboard = size === "dashboard";

  return (
    <header
      className={clsx(
        "module-hero-strip animate-fade-in module-hero-strip--actenora",
        isDashboard && "module-hero-strip--dashboard",
      )}
    >
      <div className="module-hero-strip-main">
        <div
          className="module-hero-strip-icon bg-gradient-to-br from-teal-500 via-emerald-600 to-violet-600"
          aria-hidden
        >
          <Mic className="h-4 w-4" />
        </div>
        <div className="module-hero-strip-text min-w-0 flex-1">
          <h1 className="module-hero-strip-title">{t(titleKey)}</h1>
          <p className="module-hero-strip-subtitle">{t(subtitleKey)}</p>
        </div>
        <div className="module-hero-strip-trailing">
          {trailing}
          {isDashboard ? (
            <span className="module-hero-strip-live" title={t("actenora.hero.live")}>
              <span className="module-hero-live-dot" aria-hidden />
              <span className="hidden sm:inline">{t("actenora.hero.live")}</span>
            </span>
          ) : null}
        </div>
      </div>
    </header>
  );
}
