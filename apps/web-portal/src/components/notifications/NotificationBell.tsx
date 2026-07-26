import { useQuery } from "@tanstack/react-query";
import clsx from "clsx";
import { Bell, CheckSquare, Clock, Handshake } from "lucide-react";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";

export function NotificationBell() {
  const api = useApi();
  const auth = useAuth();
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const q = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: () => api.getDashboard(),
    enabled: Boolean(auth.user),
  });

  const items = useMemo(() => {
    if (!q.data) return [];
    const next: Array<{ id: string; label: string; href: string; count: number; icon: typeof Clock }> = [];
    if (q.data.pendingApprovals > 0 && auth.canApprove) {
      next.push({
        id: "approvals",
        label: t("dashboard.pendingApprovals"),
        href: "/approvals",
        count: q.data.pendingApprovals,
        icon: Clock,
      });
    }
    if (q.data.openActions > 0) {
      next.push({
        id: "actions",
        label: t("dashboard.openActions"),
        href: "/actions",
        count: q.data.openActions,
        icon: CheckSquare,
      });
    }
    if (q.data.overdueCommitments > 0) {
      next.push({
        id: "commitments",
        label: t("dashboard.openCommitments"),
        href: "/commitments",
        count: q.data.overdueCommitments,
        icon: Handshake,
      });
    }
    return next;
  }, [q.data, auth.canApprove, t]);

  const total = items.reduce((sum, item) => sum + item.count, 0);

  return (
    <div className="relative">
      <button
        type="button"
        className="relative rounded-2xl border border-white/70 bg-white/80 p-2 text-slate-600 shadow-sm backdrop-blur transition hover:bg-white"
        aria-label={t("notifications.open")}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <Bell className="h-5 w-5" />
        {total > 0 ? (
          <span className="absolute -right-1 -top-1 flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-amber-500 px-1 text-[10px] font-bold text-white">
            {total > 99 ? "99+" : total}
          </span>
        ) : null}
      </button>

      {open ? (
        <>
          <button
            type="button"
            className="fixed inset-0 z-40 cursor-default"
            aria-label={t("notifications.close")}
            onClick={() => setOpen(false)}
          />
          <div className="absolute right-0 z-50 mt-2 w-[min(100vw-2rem,18rem)] rounded-2xl border border-white/70 bg-white/95 p-2 shadow-xl backdrop-blur-xl">
            <p className="px-2 py-1 text-xs font-bold uppercase tracking-wide text-slate-500">
              {t("notifications.title")}
            </p>
            {items.length ? (
              <ul className="space-y-1">
                {items.map((item) => {
                  const Icon = item.icon;
                  return (
                    <li key={item.id}>
                      <Link
                        to={item.href}
                        className="flex items-center gap-3 rounded-xl px-2 py-2 text-sm transition hover:bg-violet-50"
                        onClick={() => setOpen(false)}
                      >
                        <Icon className="h-4 w-4 text-violet-600" aria-hidden />
                        <span className="min-w-0 flex-1 truncate text-slate-800">{item.label}</span>
                        <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-bold text-violet-700">
                          {item.count}
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className={clsx("px-2 py-4 text-sm text-slate-500")}>{t("notifications.empty")}</p>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}
