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

/**
 * The onboarding checklist is per-user: namespacing the storage key by user id
 * prevents a second user on the same browser from inheriting the first user's
 * completed/dismissed state.
 */
function storageKeyFor(userKey?: string | null): string {
  return userKey ? `${STORAGE_KEY}:${userKey}` : STORAGE_KEY;
}

export function loadOnboardingState(userKey?: string | null): OnboardingState {
  if (typeof localStorage === "undefined") return defaultOnboardingState();
  try {
    const raw = localStorage.getItem(storageKeyFor(userKey));
    if (!raw) return defaultOnboardingState();
    const parsed = JSON.parse(raw) as OnboardingState;
    if (!parsed?.steps?.length) return defaultOnboardingState();
    return parsed;
  } catch {
    return defaultOnboardingState();
  }
}

export function saveOnboardingState(state: OnboardingState, userKey?: string | null): void {
  if (typeof localStorage === "undefined") return;
  localStorage.setItem(storageKeyFor(userKey), JSON.stringify(state));
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
