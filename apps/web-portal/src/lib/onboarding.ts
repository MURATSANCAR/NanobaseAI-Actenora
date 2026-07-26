const STORAGE_KEY = "actenora-onboarding-v1";

export type OnboardingStepId = "signed_in" | "teams_connected" | "templates_ready" | "first_meeting";

export type OnboardingStep = {
  id: OnboardingStepId;
  done: boolean;
};

export type OnboardingState = {
  steps: OnboardingStep[];
  dismissed: boolean;
};

const DEFAULT_STEPS: OnboardingStepId[] = [
  "signed_in",
  "teams_connected",
  "templates_ready",
  "first_meeting",
];

export function defaultOnboardingState(): OnboardingState {
  return {
    steps: DEFAULT_STEPS.map((id) => ({ id, done: id === "signed_in" })),
    dismissed: false,
  };
}

export function loadOnboardingState(): OnboardingState {
  if (typeof localStorage === "undefined") return defaultOnboardingState();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaultOnboardingState();
    const parsed = JSON.parse(raw) as OnboardingState;
    if (!parsed?.steps?.length) return defaultOnboardingState();
    return parsed;
  } catch {
    return defaultOnboardingState();
  }
}

export function saveOnboardingState(state: OnboardingState): void {
  if (typeof localStorage === "undefined") return;
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function onboardingProgress(state: OnboardingState): { done: number; total: number } {
  const done = state.steps.filter((s) => s.done).length;
  return { done, total: state.steps.length };
}

export function isOnboardingComplete(state: OnboardingState): boolean {
  return state.steps.every((s) => s.done);
}

export function markOnboardingStep(state: OnboardingState, id: OnboardingStepId): OnboardingState {
  return {
    ...state,
    steps: state.steps.map((s) => (s.id === id ? { ...s, done: true } : s)),
  };
}
