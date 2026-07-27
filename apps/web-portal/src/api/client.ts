import type { ApiClient, PortalUser } from "./types";
import { resolveAuthHeaders } from "../auth/authHeaders";
import { resolvePortalAuthMode, type PortalAuthMode } from "../auth/portalAuthMode";

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
 * Operator identity headers when backend auth mode is headers.
 * Values must be a real Entra user from env — no built-in defaults.
 */
export function identityAuthHeaders(env?: Partial<ImportMetaEnv>): Record<string, string> {
  const meta = env ?? ((typeof import.meta !== "undefined" ? import.meta.env : undefined) as
    | ImportMetaEnv
    | undefined);
  const oid = meta?.VITE_IDENTITY_ENTRA_OID;
  const tid = meta?.VITE_IDENTITY_ENTRA_TID;
  const email = meta?.VITE_IDENTITY_EMAIL;
  const name = meta?.VITE_IDENTITY_DISPLAY_NAME;
  const globalAdmin = meta?.VITE_IDENTITY_GLOBAL_ADMIN;
  if (!oid || !tid || !email || !name) {
    return {};
  }
  return {
    "X-Actenora-Entra-Oid": oid,
    "X-Actenora-Entra-Tid": tid,
    "X-Actenora-Email": email,
    "X-Actenora-Display-Name": name,
    "X-Actenora-Global-Admin": globalAdmin ?? "false",
  };
}

function attachCompositionStub<T>(data: T, res: Response): T {
  if (
    data &&
    typeof data === "object" &&
    res.headers.get("X-Actenora-Composition") === "stub"
  ) {
    return { ...(data as Record<string, unknown>), compositionStub: true } as T;
  }
  return data;
}

async function httpJson<T>(baseUrl: string, path: string, init?: RequestInit): Promise<T> {
  const authHeaders = await resolveAuthHeaders();
  const res = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...authHeaders,
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
  const data = (await res.json()) as T;
  return attachCompositionStub(data, res);
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
    listNotifications: (params) => httpJson(baseUrl, `/api/v1/portal/notifications${q(params)}`),
    markNotificationRead: (id) =>
      httpJson(baseUrl, `/api/v1/portal/notifications/${id}/read`, { method: "POST" }),
    markAllNotificationsRead: () =>
      httpJson(baseUrl, "/api/v1/portal/notifications/read-all", { method: "POST" }),
    listMeetings: (params) => httpJson(baseUrl, `/api/v1/portal/meetings${q(params)}`),
    getMeetingDetail: (id) => httpJson(baseUrl, `/api/v1/portal/meetings/${id}`),
    getMeetingDelivery: (id) => httpJson(baseUrl, `/api/v1/portal/meetings/${id}/delivery`),
    getNoteRenders: (meetingId, noteId) =>
      httpJson(baseUrl, `/api/v1/portal/meetings/${meetingId}/notes/${noteId}/renders`),
    getMeetingTranscript: (id, params) =>
      httpJson(baseUrl, `/api/v1/portal/meetings/${id}/transcript${q(params)}`),
    updateMeetingNote: (meetingId, noteId, body) =>
      httpJson(baseUrl, `/api/v1/portal/meetings/${meetingId}/notes/${noteId}`, {
        method: "PUT",
        body: JSON.stringify({ body }),
      }),
    submitNoteForApproval: (meetingId, noteId, expectedVersion) =>
      httpJson(baseUrl, `/api/v1/portal/meetings/${meetingId}/notes/${noteId}/submit-for-approval`, {
        method: "POST",
        body: JSON.stringify({ expectedVersion: expectedVersion ?? 0 }),
      }),
    decideApproval: (approvalId, decision, comment) =>
      httpJson(baseUrl, `/api/v1/portal/approvals/${approvalId}/decide`, {
        method: "POST",
        body: JSON.stringify({ decision, comment }),
      }),
    listPendingApprovals: () => httpJson(baseUrl, "/api/v1/portal/approvals/pending"),
    listDecisions: (params) => httpJson(baseUrl, `/api/v1/portal/decisions${q(params)}`),
    listActions: (params) => httpJson(baseUrl, `/api/v1/portal/actions${q(params)}`),
    completeAction: (actionId) =>
      httpJson(baseUrl, `/api/v1/portal/actions/${actionId}/complete`, { method: "POST" }),
    listCommitments: (params) => httpJson(baseUrl, `/api/v1/portal/commitments${q(params)}`),
    listTemplates: () => httpJson(baseUrl, "/api/v1/portal/templates"),
    createTemplate: (body) =>
      httpJson(baseUrl, "/api/v1/portal/templates", {
        method: "POST",
        body: JSON.stringify(body),
      }),
    getTemplate: (templateId) => httpJson(baseUrl, `/api/v1/portal/templates/${templateId}`),
    createTemplateDraft: (templateId, body) =>
      httpJson(baseUrl, `/api/v1/portal/templates/${templateId}/versions`, {
        method: "POST",
        body: JSON.stringify(body),
      }),
    saveTemplateDesign: (templateId, versionId, body) =>
      httpJson(baseUrl, `/api/v1/portal/templates/${templateId}/versions/${versionId}/design`, {
        method: "PUT",
        body: JSON.stringify(body),
      }),
    publishTemplateVersion: (templateId, versionId) =>
      httpJson(baseUrl, `/api/v1/portal/templates/${templateId}/versions/${versionId}/publish`, {
        method: "POST",
      }),
    setDefaultTemplate: (templateId) =>
      httpJson(baseUrl, `/api/v1/portal/templates/${templateId}/default`, {
        method: "PUT",
      }),
    getNoteTemplateLock: async (meetingId, noteId) => {
      try {
        return await httpJson(baseUrl, `/api/v1/portal/meetings/${meetingId}/notes/${noteId}/template-lock`);
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null;
        throw err;
      }
    },
    lockNoteTemplate: (meetingId, noteId, templateVersionId) =>
      httpJson(baseUrl, `/api/v1/portal/meetings/${meetingId}/notes/${noteId}/template-lock`, {
        method: "PUT",
        body: JSON.stringify({ templateVersionId }),
      }),
    getTeamsSettings: () => httpJson(baseUrl, "/api/v1/portal/teams/settings"),
    updateTeamsSettings: (body) =>
      httpJson(baseUrl, "/api/v1/portal/teams/settings", {
        method: "PUT",
        body: JSON.stringify(body),
      }),
    getNanobaseAiConnection: () => httpJson(baseUrl, "/api/v1/portal/intelligence/connection"),
    updateNanobaseAiConnection: (body) =>
      httpJson(baseUrl, "/api/v1/portal/intelligence/connection", {
        method: "PUT",
        body: JSON.stringify(body),
      }),
    testNanobaseAiConnection: () =>
      httpJson(baseUrl, "/api/v1/portal/intelligence/connection/test", {
        method: "POST",
        body: "{}",
      }),
    getModelHealth: () => httpJson(baseUrl, "/api/v1/portal/model-control/health"),
    listAiJobs: (params) => httpJson(baseUrl, `/api/v1/portal/ai-jobs${q(params)}`),
    getOperationsOverview: () => httpJson(baseUrl, "/api/v1/portal/operations/overview"),
    listAuditEvents: (params) => httpJson(baseUrl, `/api/v1/portal/audit/events${q(params)}`),
  };
}

/** Portal talks only to the real platform BFF — no in-memory demo API. */
export type ApiMode = "http";
export { type PortalAuthMode, resolvePortalAuthMode } from "../auth/portalAuthMode";

export function portalMutationsEnabled(
  mode: ApiMode,
  portalAuthMode: PortalAuthMode = resolvePortalAuthMode(),
): boolean {
  return mode === "http" && (portalAuthMode === "headers" || portalAuthMode === "msal");
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
  notifications: ["notifications"] as const,
  approvalsPending: ["approvals-pending"] as const,
  meetings: (params: object) => ["meetings", params] as const,
  meetingDetail: (id: string) => ["meeting", id] as const,
  meetingDelivery: (id: string) => ["meeting-delivery", id] as const,
  noteRenders: (meetingId: string, noteId: string) => ["note-renders", meetingId, noteId] as const,
  transcript: (id: string, params: object) => ["transcript", id, params] as const,
  decisions: (params: object) => ["decisions", params] as const,
  actions: (params: object) => ["actions", params] as const,
  commitments: (params: object) => ["commitments", params] as const,
  templates: ["templates"] as const,
  templateDetail: (id: string) => ["template", id] as const,
  noteTemplateLock: (meetingId: string, noteId: string) =>
    ["note-template-lock", meetingId, noteId] as const,
  teams: ["teams-settings"] as const,
  intelligence: ["nanobaseai-connection"] as const,
  models: ["models"] as const,
  jobs: (params: object) => ["ai-jobs", params] as const,
  operations: ["operations"] as const,
  audit: (params: object) => ["audit", params] as const,
};
