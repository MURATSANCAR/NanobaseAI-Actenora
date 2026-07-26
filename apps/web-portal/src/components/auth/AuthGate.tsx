import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { useMsalAuth } from "@/auth/MsalAuthProvider";
import { LoginPage } from "@/pages/LoginPage";
import { useI18n } from "@/i18n";

export function AuthGate({ children }: { children: ReactNode }) {
  const { enabled, ready, account } = useMsalAuth();
  const { t } = useI18n();

  if (!enabled) return <>{children}</>;

  if (!ready) {
    return (
      <div className="flex min-h-[100dvh] items-center justify-center bg-gradient-to-br from-violet-100 via-white to-sky-100">
        <div className="card-static flex items-center gap-3 px-6 py-4" role="status">
          <Loader2 className="h-5 w-5 animate-spin text-violet-600" />
          <span className="text-sm font-medium text-slate-700">{t("auth.initializing")}</span>
        </div>
      </div>
    );
  }

  if (!account) {
    return <LoginPage />;
  }

  return <>{children}</>;
}
