import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import {
  AlertTriangle,
  Bell,
  CheckCheck,
  CheckSquare,
  ClipboardCheck,
  FileText,
  Handshake,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import type { NotificationType, PortalNotificationItem } from "@/api/types";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";

const TYPE_ICON: Record<NotificationType, LucideIcon> = {
  APPROVAL_REQUESTED: ClipboardCheck,
  DRAFT_MINUTES_READY: FileText,
  AI_JOB_FAILED: AlertTriangle,
  ACTION_OVERDUE: CheckSquare,
  COMMITMENT_OVERDUE: Handshake,
};

function formatRelative(iso: string | null, locale: string): string {
  if (!iso) return "";
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return "";
  const deltaSec = Math.round((then - Date.now()) / 1000);
  const rtf = new Intl.RelativeTimeFormat(locale.startsWith("tr") ? "tr" : "en", { numeric: "auto" });
  const abs = Math.abs(deltaSec);
  if (abs < 60) return rtf.format(deltaSec, "second");
  const deltaMin = Math.round(deltaSec / 60);
  if (Math.abs(deltaMin) < 60) return rtf.format(deltaMin, "minute");
  const deltaHr = Math.round(deltaMin / 60);
  if (Math.abs(deltaHr) < 24) return rtf.format(deltaHr, "hour");
  const deltaDay = Math.round(deltaHr / 24);
  return rtf.format(deltaDay, "day");
}

export function NotificationBell() {
  const api = useApi();
  const auth = useAuth();
  const { t, tb, locale } = useI18n();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  const q = useQuery({
    queryKey: queryKeys.notifications,
    queryFn: () => api.listNotifications({ limit: 30 }),
    enabled: Boolean(auth.user),
    refetchInterval: 45_000,
  });

  const markRead = useMutation({
    mutationFn: (id: string) => api.markNotificationRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  });

  const markAll = useMutation({
    mutationFn: () => api.markAllNotificationsRead(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
  });

  const items = q.data?.items ?? [];
  const unread = q.data?.unreadCount ?? 0;

  const onOpenItem = (item: PortalNotificationItem) => {
    setOpen(false);
    if (!item.readAt) {
      markRead.mutate(item.id);
    }
  };

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
        {unread > 0 ? (
          <span className="absolute -right-1 -top-1 flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-amber-500 px-1 text-[10px] font-bold text-white">
            {unread > 99 ? "99+" : unread}
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
          <div className="absolute right-0 z-50 mt-2 w-[min(100vw-2rem,22rem)] rounded-2xl border border-white/70 bg-white/95 p-2 shadow-xl backdrop-blur-xl">
            <div className="flex items-center justify-between gap-2 px-2 py-1">
              <p className="text-xs font-bold uppercase tracking-wide text-slate-500">
                {t("notifications.title")}
              </p>
              {unread > 0 ? (
                <button
                  type="button"
                  className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-[11px] font-semibold text-violet-700 transition hover:bg-violet-50"
                  onClick={() => markAll.mutate()}
                  disabled={markAll.isPending}
                >
                  <CheckCheck className="h-3.5 w-3.5" aria-hidden />
                  {t("notifications.markAllRead")}
                </button>
              ) : null}
            </div>

            {items.length ? (
              <ul className="max-h-[min(70vh,24rem)] space-y-1 overflow-y-auto">
                {items.map((item) => {
                  const Icon = TYPE_ICON[item.type as NotificationType] ?? Bell;
                  const unreadRow = !item.readAt;
                  return (
                    <li key={item.id}>
                      <Link
                        to={item.href || "/"}
                        className={clsx(
                          "flex items-start gap-3 rounded-xl px-2 py-2 text-sm transition hover:bg-violet-50",
                          unreadRow && "bg-violet-50/40",
                        )}
                        onClick={() => onOpenItem(item)}
                      >
                        <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-violet-100 text-violet-700">
                          <Icon className="h-4 w-4" aria-hidden />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex items-center gap-2">
                            <span className="truncate font-semibold text-slate-800">
                              {tb("notificationType", item.type) || item.title}
                            </span>
                            {unreadRow ? (
                              <span className="h-2 w-2 shrink-0 rounded-full bg-amber-500" aria-hidden />
                            ) : null}
                          </span>
                          {item.body ? (
                            <span className="mt-0.5 line-clamp-2 block text-xs text-slate-600">{item.body}</span>
                          ) : null}
                          <span className="mt-1 block text-[11px] text-slate-400">
                            {formatRelative(item.createdAt, locale)}
                          </span>
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="px-2 py-4 text-sm text-slate-500">{t("notifications.empty")}</p>
            )}
          </div>
        </>
      ) : null}
    </div>
  );
}
