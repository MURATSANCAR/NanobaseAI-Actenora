import { buildAuthHeaders } from "../auth/tokenGate.js";
import type {
  Agenda,
  Marker,
  MarkerType,
  MeetingWorkspace,
  PrivateNote,
  SharedNote,
  UntrustedTeamsContext,
} from "../domain/types.js";

export interface CollaborationApiClientOptions {
  baseUrl: string;
  backendToken: string;
  teamsContext?: UntrustedTeamsContext;
  fetchImpl?: typeof fetch;
}

export class CollaborationApiClient {
  private readonly baseUrl: string;
  private readonly backendToken: string;
  private readonly teamsContext: UntrustedTeamsContext;
  private readonly fetchImpl: typeof fetch;

  constructor(options: CollaborationApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.backendToken = options.backendToken;
    this.teamsContext = options.teamsContext ?? {};
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  async getWorkspace(meetingId: string): Promise<MeetingWorkspace> {
    return this.request<MeetingWorkspace>(`/api/v1/meetings/${meetingId}/collaboration`);
  }

  async createMarker(
    meetingId: string,
    type: MarkerType,
    body: string,
    idempotencyKey: string,
  ): Promise<Marker> {
    return this.request<Marker>(`/api/v1/meetings/${meetingId}/collaboration/markers`, {
      method: "POST",
      body: JSON.stringify({ type, body }),
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  async updateAgenda(
    meetingId: string,
    items: string[],
    expectedVersion: number | null,
    idempotencyKey: string,
  ): Promise<Agenda> {
    return this.request<Agenda>(`/api/v1/meetings/${meetingId}/collaboration/agenda`, {
      method: "PUT",
      body: JSON.stringify({ items, expectedVersion }),
      headers: { "Idempotency-Key": idempotencyKey },
    });
  }

  async upsertSharedNote(meetingId: string, body: string, expectedVersion: number | null): Promise<SharedNote> {
    return this.request<SharedNote>(`/api/v1/meetings/${meetingId}/collaboration/shared-note`, {
      method: "PUT",
      body: JSON.stringify({ body, expectedVersion }),
    });
  }

  async upsertPrivateNote(meetingId: string, body: string, expectedVersion: number | null): Promise<PrivateNote> {
    return this.request<PrivateNote>(`/api/v1/meetings/${meetingId}/collaboration/private-note`, {
      method: "PUT",
      body: JSON.stringify({ body, expectedVersion }),
    });
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = {
      ...buildAuthHeaders(this.backendToken, this.teamsContext),
      ...(init.headers as Record<string, string> | undefined),
    };
    const response = await this.fetchImpl(`${this.baseUrl}${path}`, { ...init, headers });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(`collaboration_api_${response.status}: ${text}`);
    }
    return (await response.json()) as T;
  }
}
