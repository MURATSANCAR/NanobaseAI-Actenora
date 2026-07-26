import { CheckCircle2, Circle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import clsx from "clsx";
import { useApi } from "@/api/ApiProvider";
import { queryKeys } from "@/api/client";
import { PageShell } from "@/components/qa/PageShell";
import { useAuth } from "@/auth/AuthProvider";
import { useI18n } from "@/i18n";
import {
  isOnboardingComplete,
  loadOnboardingState,
  markOnboardingStep,
  onboardingProgress,
  saveOnboardingState,
  type OnboardingStepId,
} from "@/lib/onboarding";

const STEP_LINKS: Record<OnboardingStepId, string> = {
  signed_in: "/",
  teams_connected: "/teams",
  templates_ready: "/templates",
  first_meeting: "/meetings",
};

export function OnboardingPage() {
  const auth = useAuth();
  const api = useApi();
  const { t } = useI18n();
  const [state, setState] = useState(loadOnboardingState);

  const teamsQ = useQuery({
    queryKey: queryKeys.teams,
    queryFn: () => api.getTeamsSettings(),
    enabled: auth.nav("teams"),
  });
  const templatesQ = useQuery({
    queryKey: queryKeys.templates,
    queryFn: () => api.listTemplates(),
    enabled: auth.nav("templates"),
  });
  const meetingsQ = useQuery({
    queryKey: queryKeys.meetings({ limit: 1 }),
    queryFn: () => api.listMeetings({ limit: 1 }),
  });

  useEffect(() => {
    setState((prev) => {
      let next = prev;
      if (auth.user) next = markOnboardingStep(next, "signed_in");
      if (teamsQ.data?.tenantConnected) next = markOnboardingStep(next, "teams_connected");
      if ((templatesQ.data?.items.length ?? 0) > 0) next = markOnboardingStep(next, "templates_ready");
      if ((meetingsQ.data?.items.length ?? 0) > 0) next = markOnboardingStep(next, "first_meeting");
      if (JSON.stringify(next) !== JSON.stringify(prev)) {
        saveOnboardingState(next);
        return next;
      }
      return prev;
    });
  }, [auth.user, teamsQ.data, templatesQ.data, meetingsQ.data]);

  const progress = onboardingProgress(state);
  const complete = isOnboardingComplete(state);

  const toggleManual = (id: OnboardingStepId) => {
    const next = markOnboardingStep(state, id);
    setState(next);
    saveOnboardingState(next);
  };

  return (
    <PageShell titleKey="onboarding.title" subtitleKey="onboarding.description" maxWidth="max-w-3xl">
      <div className="card-static space-y-4 p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="text-sm text-slate-600">
            {t("onboarding.progress", { done: progress.done, total: progress.total })}
          </p>
          {complete ? (
            <span className="rounded-full bg-teal-100 px-3 py-1 text-xs font-semibold text-teal-800">
              {t("onboarding.completeBadge")}
            </span>
          ) : null}
        </div>

        <ul className="space-y-3">
          {state.steps.map((step) => {
            const done = step.done;
            return (
              <li
                key={step.id}
                className={clsx(
                  "flex flex-wrap items-start justify-between gap-3 rounded-2xl border px-4 py-3",
                  done ? "border-teal-200/80 bg-teal-50/50" : "border-white/70 bg-white/50",
                )}
              >
                <div className="flex min-w-0 flex-1 items-start gap-3">
                  {done ? (
                    <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-teal-600" aria-hidden />
                  ) : (
                    <Circle className="mt-0.5 h-5 w-5 shrink-0 text-slate-400" aria-hidden />
                  )}
                  <div>
                    <p className="font-semibold text-slate-900">{t(`onboarding.steps.${step.id}.title`)}</p>
                    <p className="mt-1 text-sm text-slate-600">{t(`onboarding.steps.${step.id}.body`)}</p>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Link to={STEP_LINKS[step.id]} className="btn-secondary px-3 py-1.5 text-xs">
                    {t("onboarding.openStep")}
                  </Link>
                  {!done ? (
                    <button
                      type="button"
                      className="btn-primary px-3 py-1.5 text-xs"
                      onClick={() => toggleManual(step.id)}
                    >
                      {t("onboarding.markDone")}
                    </button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </PageShell>
  );
}

export function OnboardingBanner() {
  const { t } = useI18n();
  const [state, setState] = useState(loadOnboardingState);

  if (state.dismissed || isOnboardingComplete(state)) return null;
  const progress = onboardingProgress(state);

  return (
    <div className="card-static flex flex-wrap items-center justify-between gap-3 border-violet-200/70 bg-violet-50/50 p-4">
      <div>
        <p className="font-semibold text-violet-950">{t("onboarding.bannerTitle")}</p>
        <p className="text-sm text-violet-900/80">
          {t("onboarding.progress", { done: progress.done, total: progress.total })}
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Link to="/onboarding" className="btn-primary px-3 py-1.5 text-xs">
          {t("onboarding.openChecklist")}
        </Link>
        <button
          type="button"
          className="btn-secondary px-3 py-1.5 text-xs"
          onClick={() => {
            const next = { ...state, dismissed: true };
            setState(next);
            saveOnboardingState(next);
          }}
        >
          {t("onboarding.dismiss")}
        </button>
      </div>
    </div>
  );
}
