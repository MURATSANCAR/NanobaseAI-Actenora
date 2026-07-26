import type { LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";
import clsx from "clsx";

type MetricCardProps = {
  label: string;
  value: number | string;
  icon: LucideIcon;
  tone?: string;
  delay?: number;
  to?: string;
};

export function MetricCard({
  label,
  value,
  icon: Icon,
  tone = "text-violet-600",
  delay = 0,
  to,
}: MetricCardProps) {
  const card = (
    <div
      className={clsx(
        "card p-4 animate-fade-in sm:p-5",
        to && "transition hover:border-violet-300 hover:shadow-md",
      )}
      style={{ animationDelay: `${delay}ms` }}
    >
      <div className="mb-3 flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</span>
        <div className={clsx("rounded-xl bg-violet-50 p-2", tone)}>
          <Icon className="h-4 w-4" />
        </div>
      </div>
      <div className="metric-value">{value}</div>
    </div>
  );

  if (to) {
    return (
      <Link
        to={to}
        className="block rounded-2xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-500"
      >
        {card}
      </Link>
    );
  }

  return card;
}
