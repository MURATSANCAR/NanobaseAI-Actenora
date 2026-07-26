import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { useMsalAuth } from "@/auth/MsalAuthProvider";
import { LoginPage } from "@/pages/LoginPage";
import { useI18n } from "@/i18n";

export function AuthGate({ children }: { children: ReactNode }) {
  const { enabled, ready, account, configError } = useMsalAuth();
  const { t } = useI18n();

  if (!enabled) return <>{children}</>;

  if (!ready) {
    return (
      <div className="flex min-h-[100dvh] items-center justify-center bg-gradient-to-br from-slate-100 via-white to-sky-50">
        <div className="card-static flex items-center gap-3 px-6 py-4" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-slate-700" />
          <span className="text-sm font-medium text-slate-700">{t("auth.initializing")}</span>
        </div>
      </div>
    );
  }

  if (configError) {
    return (
      <div className="flex min-h-[100dvh] items-center justify-center bg-gradient-to-br from-slate-100 via-white to-sky-50 px-4">
        <div className="card-static max-w-lg space-y-3 p-6" role="alert">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">NanobaseAI</p>
          <h1 className="text-lg font-semibold text-slate-900">{t("auth.misconfiguredTitle")}</h1>
          <p className="text-sm text-slate-600">{t("auth.misconfiguredBody")}</p>
          <p className="rounded-md bg-slate-50 p-3 font-mono text-xs text-slate-700">{configError}</p>
        </div>
      </div>
    );
  }

  if (!account) {
    return <LoginPage />;
  }

  return <>{children}</>;
}
