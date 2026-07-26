import { Mic } from "lucide-react";
import { AiAmbientShow } from "@/components/qa/AiAmbientShow";
import { useMsalAuth } from "@/auth/MsalAuthProvider";
import { useI18n } from "@/i18n";

export function LoginPage() {
  const { login } = useMsalAuth();
  const { t } = useI18n();

  return (
    <div className="relative flex min-h-[100dvh] items-center justify-center bg-gradient-to-br from-violet-100 via-white to-sky-100 p-4">
      <AiAmbientShow />
      <div className="card-static relative z-10 w-full max-w-md space-y-6 p-8 text-center animate-fade-in">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-teal-500 to-violet-600 text-white shadow-lg">
          <Mic className="h-7 w-7" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-violet-900">{t("brand.name")}</h1>
          <p className="mt-2 text-sm text-slate-600">{t("auth.signInDescription")}</p>
        </div>
        <button type="button" className="btn-primary w-full" onClick={() => void login()}>
          {t("auth.signInMicrosoft")}
        </button>
      </div>
    </div>
  );
}
