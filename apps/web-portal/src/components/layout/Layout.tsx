import { useState } from "react";
import { Outlet } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Menu, Search } from "lucide-react";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { useAuth } from "@/auth/AuthProvider";
import { AiAmbientShow } from "@/components/qa/AiAmbientShow";
import { Sidebar } from "@/components/layout/Sidebar";
import { NotificationBell } from "@/components/notifications/NotificationBell";
import {
  GlobalSearchDialog,
  useGlobalSearchShortcut,
} from "@/components/search/GlobalSearchDialog";
import { useI18n } from "@/i18n";

export function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const { t } = useI18n();
  const api = useApi();
  const auth = useAuth();
  useGlobalSearchShortcut(() => setSearchOpen(true));

  const dashboardQ = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: () => api.getDashboard(),
    enabled: Boolean(auth.user),
    refetchInterval: 30_000,
  });
  const pendingApprovals =
    auth.canApprove && auth.nav("approvals") ? (dashboardQ.data?.pendingApprovals ?? 0) : 0;

  return (
    <div className="relative h-[100dvh] min-h-0 overflow-hidden bg-gradient-to-br from-violet-100 via-white to-sky-100 text-slate-800">
      <AiAmbientShow />

      <div className="relative z-10 flex h-full min-h-0 p-1.5 pb-[max(0.375rem,env(safe-area-inset-bottom))] sm:p-2.5 lg:p-3">
        <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

        <div className="ai-module-shell relative flex min-h-0 min-w-0 flex-1 flex-col rounded-2xl border border-white/80 bg-gradient-to-br from-teal-50/30 via-white/40 to-violet-50/30 shadow-2xl backdrop-blur-xl sm:rounded-3xl">
          <button
            type="button"
            className="absolute left-3 top-3 z-30 rounded-2xl border border-white/70 bg-white/80 p-2 text-slate-600 shadow-sm backdrop-blur transition hover:bg-white lg:hidden"
            onClick={() => setSidebarOpen(true)}
            aria-label={
              pendingApprovals > 0
                ? t("nav.approvalsPending", { count: pendingApprovals })
                : t("nav.openMenu")
            }
          >
            <Menu className="h-5 w-5" />
            {pendingApprovals > 0 ? (
              <span className="nav-approval-badge absolute -right-1.5 -top-1.5" aria-hidden>
                <span className="nav-approval-badge-ping" />
                <span className="nav-approval-badge-count">
                  {pendingApprovals > 99 ? "99+" : pendingApprovals}
                </span>
              </span>
            ) : null}
          </button>

          <div className="absolute right-3 top-3 z-30 flex items-center gap-2">
            <NotificationBell />
            <button
              type="button"
              className="hidden items-center gap-2 rounded-2xl border border-white/70 bg-white/80 px-3 py-2 text-sm text-slate-600 shadow-sm backdrop-blur transition hover:bg-white sm:flex"
              onClick={() => setSearchOpen(true)}
              aria-label={t("search.open")}
            >
              <Search className="h-4 w-4" />
              <span>{t("search.shortcut")}</span>
            </button>
          </div>

          <main className="relative flex min-h-0 flex-1 flex-col overflow-x-hidden overflow-y-auto p-2 pt-12 sm:p-4 sm:pt-4 lg:p-5 lg:pt-5">
            <div className="flex min-h-0 min-w-0 flex-1 flex-col">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
      <GlobalSearchDialog open={searchOpen} onClose={() => setSearchOpen(false)} />
    </div>
  );
}
