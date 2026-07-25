export type MarkerType = "DECISION" | "ACTION" | "RISK" | "QUESTION" | "IMPORTANT";

export type TeamsSurface = "details-tab" | "side-panel" | "chat-tab";

export interface UntrustedTeamsContext {
  teamsMeetingId?: string;
  chatId?: string;
  claimedTenantId?: string;
  claimedUserId?: string;
}

export interface MeetingWorkspace {
  meetingOccurrenceId: string;
  agenda: Agenda | null;
  openTasks: OpenTask[];
  markers: Marker[];
  sharedNote: SharedNote | null;
  privateNote: PrivateNote | null;
}

export interface Marker {
  id: string;
  meetingOccurrenceId: string;
  type: MarkerType;
  body: string;
  offsetMs: number;
  createdByUserId: string;
  createdAt: string;
}

export interface SharedNote {
  id: string;
  meetingOccurrenceId: string;
  body: string;
  version: number;
}

export interface PrivateNote {
  id: string;
  meetingOccurrenceId: string;
  ownerUserId: string;
  body: string;
  aiUseAllowed: boolean;
  version: number;
}

export interface Agenda {
  id: string;
  meetingOccurrenceId: string;
  items: string[];
  version: number;
}

export interface OpenTask {
  id: string;
  meetingOccurrenceId: string;
  title: string;
  assigneeUserId: string | null;
  open: boolean;
}

export interface SurfaceViewModel {
  surface: TeamsSurface;
  title: string;
  meetingId: string;
  sections: string[];
}
