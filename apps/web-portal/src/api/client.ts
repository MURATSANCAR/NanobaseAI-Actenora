import type { ApiClient } from "./types";
import { createMockApiClient } from "./mock/client";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

async function httpJson<T>(baseUrl: string, path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });
  if (!res.ok) {
    let code = "HTTP_ERROR";
    let message = res.statusText;
    try {
      const body = (await res.json()) as { code?: string; message?: string; title?: string };
      code = body.code ?? body.title ?? code;
      message = body.message ?? message;
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, code, message);
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
    getCurrentUser: () => httpJson(baseUrl, "/api/v1/me"),
    getDashboard: () => httpJson(baseUrl, "/api/v1/dashboard"),
    listMeetings: (params) => httpJson(baseUrl, `/api/v1/meetings${q(params)}`),
    getMeetingDetail: (id) => httpJson(baseUrl, `/api/v1/meetings/${id}`),
    getMeetingTranscript: (id, params) =>
      httpJson(baseUrl, `/api/v1/meetings/${id}/transcript${q(params)}`),
    updateMeetingNote: (meetingId, noteId, body) =>
      httpJson(baseUrl, `/api/v1/meetings/${meetingId}/notes/${noteId}`, {
        method: "PUT",
        body: JSON.stringify({ body }),
      }),
    decideApproval: (approvalId, decision, comment) =>
      httpJson(baseUrl, `/api/v1/approvals/${approvalId}/decide`, {
        method: "POST",
        body: JSON.stringify({ decision, comment }),
      }),
    listDecisions: (params) => httpJson(baseUrl, `/api/v1/decisions${q(params)}`),
    listActions: (params) => httpJson(baseUrl, `/api/v1/actions${q(params)}`),
    completeAction: (actionId) =>
      httpJson(baseUrl, `/api/v1/actions/${actionId}/complete`, { method: "POST" }),
    listCommitments: (params) => httpJson(baseUrl, `/api/v1/commitments${q(params)}`),
    listTemplates: () => httpJson(baseUrl, "/api/v1/templates"),
    getTeamsSettings: () => httpJson(baseUrl, "/api/v1/teams/settings"),
    getModelHealth: () => httpJson(baseUrl, "/api/v1/model-control/health"),
    listAiJobs: (params) => httpJson(baseUrl, `/api/v1/ai-jobs${q(params)}`),
    getOperationsOverview: () => httpJson(baseUrl, "/api/v1/operations/overview"),
    listAuditEvents: (params) => httpJson(baseUrl, `/api/v1/audit/events${q(params)}`),
  };
}

export type ApiMode = "mock" | "http";

export function createApiClient(opts?: {
  mode?: ApiMode;
  baseUrl?: string;
}): ApiClient {
  const mode =
    opts?.mode ??
    ((import.meta.env.VITE_API_MODE as ApiMode | undefined) ?? "mock");
  if (mode === "http") {
    const baseUrl =
      opts?.baseUrl ?? (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";
    return createHttpApiClient(baseUrl);
  }
  return createMockApiClient("ADMIN");
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
