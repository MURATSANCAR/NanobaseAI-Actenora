import type { ApiClient, PortalUser } from "./types";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

/**
 * Dev-only mock IdP headers when backend auth mode is mock.
 * Values must come from environment — no built-in demo identity defaults.
 */
export function mockAuthHeaders(env?: Partial<ImportMetaEnv>): Record<string, string> {
  const meta = env ?? ((typeof import.meta !== "undefined" ? import.meta.env : undefined) as
    | ImportMetaEnv
    | undefined);
  const oid = meta?.VITE_MOCK_ENTRA_OID;
  const tid = meta?.VITE_MOCK_ENTRA_TID;
  const email = meta?.VITE_MOCK_EMAIL;
  const name = meta?.VITE_MOCK_DISPLAY_NAME;
  const globalAdmin = meta?.VITE_MOCK_GLOBAL_ADMIN;
  if (!oid || !tid || !email || !name) {
    return {};
  }
  return {
    "X-Mock-Entra-Oid": oid,
    "X-Mock-Entra-Tid": tid,
    "X-Mock-Email": email,
    "X-Mock-Display-Name": name,
    "X-Mock-Global-Admin": globalAdmin ?? "false",
  };
}

async function httpJson<T>(baseUrl: string, path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...mockAuthHeaders(),
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    let code = "HTTP_ERROR";
    let message = res.statusText;
    try {
      const body = (await res.json()) as { code?: string; message?: string; title?: string; detail?: string };
      code = body.code ?? body.title ?? code;
      message = body.detail ?? body.message ?? message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, code, message);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

function createHttpApiClient(baseUrl: string): ApiClient {
  const q = (params?: object) => {
    if (!params) return "";
    const sp = new URLSearchParams();
    for (const [k, v] of Object.entries(params as Record<string, unknown>)) {
      if (v !== undefined && v !== "") sp.set(k, String(v));
    }
    const s = sp.toString();
    return s ? `?${s}` : "";
  };

  return {
    getCurrentUser: () => httpJson<PortalUser>(baseUrl, "/api/v1/portal/me"),
    getDashboard: () => httpJson(baseUrl, "/api/v1/portal/dashboard"),
    listMeetings: (params) => httpJson(baseUrl, `/api/v1/portal/meetings${q(params)}`),
    getMeetingDetail: (id) => httpJson(baseUrl, `/api/v1/portal/meetings/${id}`),
    getMeetingTranscript: (id, params) =>
      httpJson(baseUrl, `/api/v1/portal/meetings/${id}/transcript${q(params)}`),
    updateMeetingNote: (meetingId, noteId, body) =>
      httpJson(baseUrl, `/api/v1/portal/meetings/${meetingId}/notes/${noteId}`, {
        method: "PUT",
        body: JSON.stringify({ body }),
      }),
    decideApproval: (approvalId, decision, comment) =>
      httpJson(baseUrl, `/api/v1/portal/approvals/${approvalId}/decide`, {
        method: "POST",
        body: JSON.stringify({ decision, comment }),
      }),
    listDecisions: (params) => httpJson(baseUrl, `/api/v1/portal/decisions${q(params)}`),
    listActions: (params) => httpJson(baseUrl, `/api/v1/portal/actions${q(params)}`),
    completeAction: (actionId) =>
      httpJson(baseUrl, `/api/v1/portal/actions/${actionId}/complete`, { method: "POST" }),
    listCommitments: (params) => httpJson(baseUrl, `/api/v1/portal/commitments${q(params)}`),
    listTemplates: () => httpJson(baseUrl, "/api/v1/portal/templates"),
    getTeamsSettings: () => httpJson(baseUrl, "/api/v1/portal/teams/settings"),
    getModelHealth: () => httpJson(baseUrl, "/api/v1/portal/model-control/health"),
    listAiJobs: (params) => httpJson(baseUrl, `/api/v1/portal/ai-jobs${q(params)}`),
    getOperationsOverview: () => httpJson(baseUrl, "/api/v1/portal/operations/overview"),
    listAuditEvents: (params) => httpJson(baseUrl, `/api/v1/portal/audit/events${q(params)}`),
  };
}

/** Portal talks only to the real platform BFF — no in-memory demo API. */
export type ApiMode = "http";
export type PortalAuthMode = "mock" | "msal";

export function portalMutationsEnabled(
  mode: ApiMode,
  portalAuthMode: PortalAuthMode = resolvePortalAuthMode(),
): boolean {
  return mode === "http" && (portalAuthMode === "mock" || portalAuthMode === "msal");
}

export function resolvePortalAuthMode(env?: Partial<ImportMetaEnv>): PortalAuthMode {
  const meta = env ?? ((typeof import.meta !== "undefined" ? import.meta.env : undefined) as
    | ImportMetaEnv
    | undefined);
  const raw = meta?.VITE_PORTAL_AUTH_MODE ?? "mock";
  return raw === "msal" ? "msal" : "mock";
}

export function createApiClient(opts?: {
  mode?: ApiMode;
  baseUrl?: string;
}): ApiClient {
  const meta = (typeof import.meta !== "undefined" ? import.meta.env : undefined) as
    | ImportMetaEnv
    | undefined;
  const mode = opts?.mode ?? ((meta?.VITE_API_MODE as ApiMode | undefined) ?? "http");
  if (mode !== "http") {
    throw new Error(
      `Unsupported VITE_API_MODE=${String(mode)}. Portal requires http against platform-backend (no mock datasets).`,
    );
  }
  const baseUrl = opts?.baseUrl ?? meta?.VITE_API_BASE_URL ?? "";
  if (!baseUrl) {
    throw new Error("VITE_API_BASE_URL is required — portal has no in-memory demo API.");
  }
  return createHttpApiClient(baseUrl.replace(/\/$/, ""));
}

export const queryKeys = {
  me: ["me"] as const,
  dashboard: ["dashboard"] as const,
  meetings: (params: object) => ["meetings", params] as const,
  meetingDetail: (id: string) => ["meeting", id] as const,
  transcript: (id: string, params: object) => ["transcript", id, params] as const,
  decisions: (params: object) => ["decisions", params] as const,
  actions: (params: object) => ["actions", params] as const,
  commitments: (params: object) => ["commitments", params] as const,
  templates: ["templates"] as const,
  teams: ["teams-settings"] as const,
  models: ["models"] as const,
  jobs: (params: object) => ["ai-jobs", params] as const,
  operations: ["operations"] as const,
  audit: (params: object) => ["audit", params] as const,
};
