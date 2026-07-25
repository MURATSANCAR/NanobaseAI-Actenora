import assert from "node:assert/strict";
import test from "node:test";
import { assertDefined } from "@actenora/test-support";
import { App } from "./App.tsx";
import {
  canAccessPrivateNote,
  canManageModels,
  canViewModelRouting,
  navVisible,
  permissionsForRole,
} from "./auth/permissions.ts";
import { applyApprovalDecision, isOptimisticSafe } from "./lib/approval.ts";
import {
  applyMeetingListFilter,
  evidenceScrollOffset,
  filterSegments,
  findEvidenceIndex,
} from "./lib/filters.ts";
import { createMockApiClient } from "./api/mock/client.ts";
import { IDS, resetMockStore } from "./api/mock/data.ts";

test("App export is defined", () => {
  assert.equal(typeof assertDefined(App), "function");
});

test("role visibility gates nav and model routing", () => {
  const viewer = permissionsForRole("VIEWER");
  const member = permissionsForRole("MEMBER");
  const admin = permissionsForRole("ADMIN");
  const ops = permissionsForRole("OPERATIONS");

  assert.equal(navVisible(viewer, "operations"), false);
  assert.equal(navVisible(viewer, "audit"), false);
  assert.equal(navVisible(viewer, "teams"), false);
  assert.equal(navVisible(member, "templates"), true);
  assert.equal(navVisible(admin, "operations"), true);
  assert.equal(navVisible(ops, "audit"), true);

  assert.equal(canViewModelRouting(viewer), false);
  assert.equal(canViewModelRouting(member), false);
  assert.equal(canViewModelRouting(admin), true);
  assert.equal(canManageModels(ops), true);
  assert.equal(canManageModels(member), false);
});

test("private note access is role and author scoped", () => {
  const memberPerms = permissionsForRole("MEMBER");
  const adminPerms = permissionsForRole("ADMIN");
  const viewerPerms = permissionsForRole("VIEWER");
  const mia = IDS.USER_IDS.member;
  const omar = IDS.USER_IDS.approver;

  assert.equal(canAccessPrivateNote(memberPerms, mia, mia), true);
  assert.equal(canAccessPrivateNote(memberPerms, omar, mia), false);
  assert.equal(canAccessPrivateNote(adminPerms, omar, mia), true);
  assert.equal(canAccessPrivateNote(viewerPerms, mia, mia), false);
});

test("meeting and transcript filters", () => {
  const meetings = [
    { title: "Q3 roadmap sync", status: "READY" },
    { title: "Customer escalation review", status: "PROCESSING" },
  ];
  assert.equal(applyMeetingListFilter(meetings, { q: "roadmap" }).length, 1);
  assert.equal(applyMeetingListFilter(meetings, { status: "PROCESSING" }).length, 1);

  const segments = [
    { id: "1", speaker: "Ada", text: "decision language", startMs: 0, endMs: 1, markers: ["DECISION"] },
    { id: "2", speaker: "Omar", text: "action language", startMs: 2, endMs: 3, markers: ["ACTION"] },
  ];
  assert.equal(filterSegments(segments, { speaker: "Omar" }).length, 1);
  assert.equal(filterSegments(segments, { marker: "DECISION" }).length, 1);
  assert.equal(filterSegments(segments, { q: "action" }).length, 1);
});

test("evidence navigation index and scroll offset", () => {
  const segments = [
    { id: "a", speaker: "A", text: "one", startMs: 0, endMs: 1 },
    { id: "b", speaker: "B", text: "two", startMs: 2, endMs: 3 },
    { id: "c", speaker: "C", text: "three", startMs: 4, endMs: 5 },
  ];
  assert.equal(findEvidenceIndex(segments, "b"), 1);
  assert.equal(findEvidenceIndex(segments, "missing"), -1);
  assert.equal(evidenceScrollOffset(0, 72, 300), 0);
  assert.ok(evidenceScrollOffset(10, 72, 300) > 0);
});

test("approval is applied only when pending; optimistic only for safe ops", async () => {
  resetMockStore();
  const pending = {
    id: IDS.APPROVAL_1,
    artifactType: "DECISION" as const,
    artifactId: IDS.DECISION_1,
    status: "PENDING" as const,
    decidedBy: null,
    decidedAt: null,
    comment: null,
  };
  const approved = applyApprovalDecision(pending, "APPROVE", "Omar Approver", "2026-07-25T00:00:00Z");
  assert.equal(approved.status, "APPROVED");
  assert.equal(approved.decidedBy, "Omar Approver");
  assert.throws(() => applyApprovalDecision(approved, "REJECT", "x", "t"));

  assert.equal(isOptimisticSafe("completeAction"), true);
  assert.equal(isOptimisticSafe("updateMeetingNote"), true);
  assert.equal(isOptimisticSafe("decideApproval"), false);

  const api = createMockApiClient("APPROVER");
  const result = await api.decideApproval(IDS.APPROVAL_1, "APPROVE", "looks good");
  assert.equal(result.status, "APPROVED");
  const decisions = await api.listDecisions();
  assert.equal(decisions.items[0]?.status, "APPROVED");
});

test("model admin permissions expose routing only for admin/ops users", async () => {
  resetMockStore();
  const memberApi = createMockApiClient("MEMBER");
  const member = await memberApi.getCurrentUser();
  assert.equal(canViewModelRouting(member.permissions), false);
  assert.equal(canManageModels(member.permissions), false);

  const adminApi = createMockApiClient("ADMIN");
  const admin = await adminApi.getCurrentUser();
  assert.equal(canViewModelRouting(admin.permissions), true);
  assert.equal(canManageModels(admin.permissions), true);

  const health = await adminApi.getModelHealth();
  assert.ok(health.routing.roles.length > 0);
});

test("mock meeting detail includes three-panel payload and private notes", async () => {
  resetMockStore();
  const api = createMockApiClient("MEMBER");
  const detail = await api.getMeetingDetail(IDS.MEETING_A);
  assert.ok(detail.participants.length);
  assert.ok(detail.decisions.length);
  assert.ok(detail.notes.some((n) => n.visibility === "PRIVATE"));
  const transcript = await api.getMeetingTranscript(IDS.MEETING_A);
  const decisionsOnly = filterSegments(transcript.segments, { marker: "DECISION" });
  assert.equal(findEvidenceIndex(decisionsOnly, IDS.SEG_1) >= 0, true);
});
