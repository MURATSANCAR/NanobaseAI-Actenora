import type { ReactNode } from "react";
import { ModuleAiHero } from "./ModuleAiHero";
import type { MessageKey } from "@/i18n";

type PageShellProps = {
  titleKey: MessageKey;
  subtitleKey: MessageKey;
  maxWidth?: string;
  children?: ReactNode;
  header?: ReactNode;
  heroTrailing?: ReactNode;
  heroSize?: "dashboard" | "page";
};

export function PageShell({
  titleKey,
  subtitleKey,
  maxWidth = "max-w-6xl",
  header,
  heroTrailing,
  heroSize = "page",
  children,
}: PageShellProps) {
  const defaultHeader = (
    <ModuleAiHero
      size={heroSize}
      titleKey={titleKey}
      subtitleKey={subtitleKey}
      trailing={heroTrailing}
    />
  );

  return (
    <div
      className={[
        "mobile-page ai-page-shell mx-auto flex w-full min-w-0 flex-1 flex-col animate-fade-in gap-2 sm:gap-3 min-h-0",
        maxWidth,
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <div className="shrink-0 space-y-2">{header ?? defaultHeader}</div>
      <div className="ai-page-content relative z-10 flex min-h-0 min-w-0 flex-1 flex-col gap-2 sm:gap-3 stagger-children">
        {children}
      </div>
    </div>
  );
}
