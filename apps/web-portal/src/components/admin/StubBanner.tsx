import { AlertTriangle } from "lucide-react";
import { Link } from "react-router-dom";
import { useI18n } from "@/i18n";

export function StubBanner({ featureKey }: { featureKey: "templates" | "teams" | "models" | "jobs" | "audit" }) {
  const { t } = useI18n();

  return (
    <div className="card-static flex items-start gap-3 border-amber-200/80 bg-amber-50/60 p-4 text-sm text-amber-950">
      <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" aria-hidden />
      <div className="min-w-0">
        <p className="font-semibold">{t(`admin.stub.${featureKey}.title`)}</p>
        <p className="mt-1 text-xs text-amber-900/80">{t(`admin.stub.${featureKey}.body`)}</p>
        <Link to="/onboarding" className="btn-secondary mt-3 inline-flex px-3 py-1.5 text-xs">
          {t("onboarding.openChecklist")}
        </Link>
      </div>
    </div>
  );
}
