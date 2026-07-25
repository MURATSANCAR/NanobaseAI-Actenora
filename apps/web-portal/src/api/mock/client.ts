import { applyApprovalDecision } from "../../lib/approval";
import { applyMeetingListFilter, filterSegments, paginateCursor } from "../../lib/filters";
import type { ApiClient, PortalRole, TranscriptSegment } from "../types";
import * as store from "./data";

function delay<T>(value: T, ms = 40): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

export interface MockApiClient extends ApiClient {
  setRole(next: PortalRole): void;
}

export function createMockApiClient(role: PortalRole = "ADMIN"): MockApiClient {
  let currentRole = role;

  return {
    setRole(next) {
      currentRole = next;
    },

    async getCurrentUser() {
      return delay(store.createUser(currentRole));
    },

    async getDashboard() {
      return delay({
        pendingApprovals: store.approvals.filter((a) => a.status === "PENDING").length,
        openActions: store.actions.filter((a) => a.status === "OPEN").length,
        overdueCommitments: store.commitments.filter(
          (c) => c.status === "AT_RISK" || c.status === "OPEN",
        ).length,
        runningJobs: store.aiJobs.filter((j) => j.status === "RUNNING").length,
        recentMeetings: store.meetings.slice(0, 5),
      });
    },

    async listMeetings(params = {}) {
      const filtered = applyMeetingListFilter(store.meetings, {
        q: params.q,
        status: params.status,
      });
      return delay(paginateCursor(filtered, params.cursor, params.limit ?? 25));
    },

    async getMeetingDetail(meetingId) {
      return delay(store.meetingDetail(meetingId));
    },

    async getMeetingTranscript(meetingId, params = {}) {
      void meetingId;
      const segments = filterSegments(store.transcriptSegments, params) as TranscriptSegment[];
      const speakers = [...new Set(store.transcriptSegments.map((s) => s.speaker))];
      return delay({ segments, speakers });
    },

    async updateMeetingNote(meetingId, noteId, body) {
      void meetingId;
      const note = store.notes.find((n) => n.id === noteId);
      if (!note) throw new Error("Note not found");
      note.body = body;
      note.updatedAt = new Date().toISOString();
      return delay({ ...note });
    },

    async decideApproval(approvalId, decision, comment) {
      const idx = store.approvals.findIndex((a) => a.id === approvalId);
      if (idx < 0) throw new Error("Approval not found");
      const user = store.createUser(currentRole);
      const updated = applyApprovalDecision(
        store.approvals[idx]!,
        decision,
        user.displayName,
        new Date().toISOString(),
        comment,
      );
      store.approvals[idx] = updated;
      const artifact = store.decisions.find((d) => d.id === updated.artifactId);
      if (artifact) {
        artifact.status = decision === "APPROVE" ? "APPROVED" : "REJECTED";
      }
      return delay({ ...updated });
    },

    async listDecisions(params = {}) {
      const items = params.status
        ? store.decisions.filter((d) => d.status === params.status)
        : store.decisions;
      return delay(paginateCursor(items, params.cursor, params.limit ?? 25));
    },

    async listActions(params = {}) {
      const items = params.status
        ? store.actions.filter((a) => a.status === params.status)
        : store.actions;
      return delay(paginateCursor(items, params.cursor, params.limit ?? 25));
    },

    async completeAction(actionId) {
      const action = store.actions.find((a) => a.id === actionId);
      if (!action) throw new Error("Action not found");
      action.status = "COMPLETED";
      return delay({ ...action });
    },

    async listCommitments(params = {}) {
      const items = params.status
        ? store.commitments.filter((c) => c.status === params.status)
        : store.commitments;
      return delay(paginateCursor(items, params.cursor, params.limit ?? 25));
    },

    async listTemplates() {
      return delay({ items: [...store.templates] });
    },

    async getTeamsSettings() {
      return delay({ ...store.teamsSettings });
    },

    async getModelHealth() {
      return delay(structuredClone(store.modelHealth));
    },

    async listAiJobs(params = {}) {
      return delay(paginateCursor(store.aiJobs, params.cursor, params.limit ?? 25));
    },

    async getOperationsOverview() {
      return delay(structuredClone(store.operations));
    },

    async listAuditEvents(params = {}) {
      return delay(paginateCursor(store.auditEvents, params.cursor, params.limit ?? 25));
    },
  };
}
