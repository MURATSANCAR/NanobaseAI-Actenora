import assert from "node:assert/strict";
import test from "node:test";
import { createApiClient, mockAuthHeaders, portalMutationsEnabled } from "./api/client.ts";
import { familyProducts } from "./config/familyProducts.ts";
import { translateBackend } from "./i18n/translateBackend.ts";
import { assertDefined } from "@actenora/test-support";
import { App } from "./App.tsx";
import { resolvePortalAuthMode } from "./auth/portalAuthMode.ts";
import {
  canAccessPrivateNote,
  canDecideApprovals,
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
import { meetingNeedsProcessingPoll } from "./lib/meetingProcessing.ts";

test("App export is defined", () => {
  assert.equal(typeof assertDefined(App), "function");
});

test("mock auth headers are empty without env identity", () => {
  assert.deepEqual(mockAuthHeaders({}), {});
});

test("mock auth headers pass through env identity only", () => {
  const headers = mockAuthHeaders({
    VITE_MOCK_ENTRA_OID: "oid-1",
    VITE_MOCK_ENTRA_TID: "tid-1",
    VITE_MOCK_EMAIL: "user@example.com",
    VITE_MOCK_DISPLAY_NAME: "User",
    VITE_MOCK_GLOBAL_ADMIN: "true",
  } as Partial<ImportMetaEnv>);
  assert.equal(headers["X-Mock-Entra-Oid"], "oid-1");
  assert.equal(headers["X-Mock-Entra-Tid"], "tid-1");
  assert.equal(headers["X-Mock-Global-Admin"], "true");
});

test("createApiClient requires http base URL", () => {
  assert.throws(() => createApiClient({ mode: "http", baseUrl: "" }), /VITE_API_BASE_URL/);
  const api = createApiClient({ mode: "http", baseUrl: "http://localhost:8080" });
  assert.equal(typeof api.getCurrentUser, "function");
  assert.equal(typeof api.listMeetings, "function");
});

test("family product bar lists Actenora first, then QA and BI", () => {
  const products = familyProducts();
  assert.equal(products.length, 3);
  assert.deepEqual(
    products.map((p) => p.key),
    ["actenora", "qa", "bi"],
  );
  assert.match(products[0]!.href, /portal\.nanobase\.ai\/actenora/);
  assert.match(products[1]!.href, /^https:\/\//);
});

test("role visibility gates nav and model routing", () => {
  const viewer = permissionsForRole("VIEWER");
  const member = permissionsForRole("MEMBER");
  const admin = permissionsForRole("ADMIN");
  const ops = permissionsForRole("OPERATIONS");

  assert.equal(navVisible(viewer, "operations"), false);
  assert.equal(navVisible(viewer, "audit"), false);
  assert.equal(navVisible(viewer, "teams"), false);
  assert.equal(navVisible(viewer, "approvals"), false);
  assert.equal(navVisible(member, "templates"), true);
  assert.equal(navVisible(permissionsForRole("APPROVER"), "approvals"), true);
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
  const author = "cccccccc-cccc-cccc-cccc-cccccccccccc";
  const other = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

  assert.equal(canAccessPrivateNote(memberPerms, author, author), true);
  assert.equal(canAccessPrivateNote(memberPerms, other, author), false);
  assert.equal(canAccessPrivateNote(adminPerms, other, author), true);
  assert.equal(canAccessPrivateNote(viewerPerms, author, author), false);
});

test("meeting and transcript filters", () => {
  const meetings = [
    { title: "alpha sync", status: "READY" },
    { title: "beta review", status: "PROCESSING" },
  ];
  assert.equal(applyMeetingListFilter(meetings, { q: "alpha" }).length, 1);
  assert.equal(applyMeetingListFilter(meetings, { status: "PROCESSING" }).length, 1);

  const segments = [
    { id: "1", speaker: "A", text: "decision language", startMs: 0, endMs: 1, markers: ["DECISION"] },
    { id: "2", speaker: "B", text: "action language", startMs: 2, endMs: 3, markers: ["ACTION"] },
  ];
  assert.equal(filterSegments(segments, { speaker: "B" }).length, 1);
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

test("approval is applied only when pending; optimistic only for safe ops", () => {
  const pending = {
    id: "p1",
    artifactType: "DECISION" as const,
    artifactId: "d1",
    status: "PENDING" as const,
    decidedBy: null,
    decidedAt: null,
    comment: null,
  };
  const approved = applyApprovalDecision(pending, "APPROVE", "approver", "2026-07-25T00:00:00Z");
  assert.equal(approved.status, "APPROVED");
  assert.equal(approved.decidedBy, "approver");
  assert.throws(() => applyApprovalDecision(approved, "REJECT", "x", "t"));

  assert.equal(isOptimisticSafe("completeAction"), true);
  assert.equal(isOptimisticSafe("updateMeetingNote"), true);
  assert.equal(isOptimisticSafe("decideApproval"), false);
});

test("model admin permissions expose routing only for admin/ops users", () => {
  const member = permissionsForRole("MEMBER");
  const admin = permissionsForRole("ADMIN");
  assert.equal(canViewModelRouting(member), false);
  assert.equal(canManageModels(member), false);
  assert.equal(canViewModelRouting(admin), true);
  assert.equal(canManageModels(admin), true);
});

test("backend enum values translate via locale catalogs", () => {
  assert.equal(translateBackend("en", "meetingStatus", "READY"), "Ready");
  assert.equal(translateBackend("tr", "meetingStatus", "READY"), "Hazır");
  assert.equal(translateBackend("tr", "meetingStatus", "UNKNOWN"), "UNKNOWN");
});

test("portal mutations enabled only for HTTP with mock or msal auth", () => {
  assert.equal(portalMutationsEnabled("http", "mock"), true);
  assert.equal(portalMutationsEnabled("http", "msal"), true);
});

test("portal auth mode resolves from env", () => {
  assert.equal(resolvePortalAuthMode({ VITE_PORTAL_AUTH_MODE: "msal" }), "msal");
  assert.equal(resolvePortalAuthMode({}), "mock");
});

test("meeting processing poll when partial or in-flight status", () => {
  const base = {
    meeting: { id: "m1", title: "T", status: "READY" as const, scheduledStartAt: "", participantCount: 0 },
    participants: [],
    seriesTitle: null,
    businessContext: null,
    versions: [],
    approvalHistory: [],
    notes: [],
    decisions: [],
    actions: [],
    risks: [],
    commitments: [],
    qualityFlags: [],
    partial: false,
  };
  assert.equal(meetingNeedsProcessingPoll(undefined), false);
  assert.equal(meetingNeedsProcessingPoll({ ...base, partial: true }), true);
  assert.equal(
    meetingNeedsProcessingPoll({
      ...base,
      meeting: { ...base.meeting, status: "PROCESSING" },
    }),
    true,
  );
  assert.equal(meetingNeedsProcessingPoll(base), false);
});

test("approver role can decide approvals", () => {
  assert.equal(canDecideApprovals(permissionsForRole("APPROVER")), true);
  assert.equal(canDecideApprovals(permissionsForRole("VIEWER")), false);
});
