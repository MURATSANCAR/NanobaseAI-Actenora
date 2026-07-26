import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import clsx from "clsx";

type EmptyStateProps = {
  emoji?: string;
  title: string;
  description: string;
  ctaLabel?: string;
  ctaTo?: string;
  onCtaClick?: () => void;
  children?: ReactNode;
  className?: string;
};

export function EmptyState({
  emoji = "📭",
  title,
  description,
  ctaLabel,
  ctaTo,
  onCtaClick,
  children,
  className,
}: EmptyStateProps) {
  return (
    <div className={clsx("empty-state-card", className)}>
      <div className="empty-state-emoji" aria-hidden>
        {emoji}
      </div>
      <h3 className="empty-state-title">{title}</h3>
      <p className="empty-state-desc">{description}</p>
      {children}
      {ctaLabel && ctaTo ? (
        <Link to={ctaTo} className="btn-primary mt-4 inline-flex items-center gap-2">
          {ctaLabel}
          <ArrowRight className="h-4 w-4" />
        </Link>
      ) : null}
      {ctaLabel && onCtaClick && !ctaTo ? (
        <button
          type="button"
          className="btn-primary mt-4 inline-flex items-center gap-2"
          onClick={onCtaClick}
        >
          {ctaLabel}
          <ArrowRight className="h-4 w-4" />
        </button>
      ) : null}
    </div>
  );
}
