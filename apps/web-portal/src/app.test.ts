import assert from "node:assert/strict";
import test from "node:test";
import { createApiClient, identityAuthHeaders, portalMutationsEnabled } from "./api/client.ts";
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
  findTurnIndexBySegmentId,
  groupConsecutiveSpeakerTurns,
} from "./lib/filters.ts";
import { meetingNeedsProcessingPoll } from "./lib/meetingProcessing.ts";
import { deriveMeetingPipelineStages } from "./lib/meetingPipeline.ts";
import {
  evidenceMatchesSegment,
  findArtifactsForSegment,
  formatEvidenceRange,
  isSeekableEvidence,
} from "./lib/evidence.ts";
import { dueDateTone, isOverdue } from "./lib/dueDates.ts";
import { mergeSearchResults, searchMeetings } from "./lib/globalSearch.ts";
import { markOnboardingStep, onboardingProgress, defaultOnboardingState } from "./lib/onboarding.ts";

test("App export is defined", () => {
  assert.equal(typeof assertDefined(App), "function");
});

test("identity auth headers are empty without env identity", () => {
  assert.deepEqual(identityAuthHeaders({}), {});
});

test("identity auth headers pass through env identity only", () => {
  const headers = identityAuthHeaders({
    VITE_IDENTITY_ENTRA_OID: "oid-1",
    VITE_IDENTITY_ENTRA_TID: "tid-1",
    VITE_IDENTITY_EMAIL: "user@example.com",
    VITE_IDENTITY_DISPLAY_NAME: "User",
    VITE_IDENTITY_GLOBAL_ADMIN: "true",
  } as Partial<ImportMetaEnv>);
  assert.equal(headers["X-Actenora-Entra-Oid"], "oid-1");
  assert.equal(headers["X-Actenora-Entra-Tid"], "tid-1");
  assert.equal(headers["X-Actenora-Global-Admin"], "true");
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
  assert.match(products[0]!.href, /portal\.nanobase\.ai\/easymeeting/);
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

test("meeting and conversation filters", () => {
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
  assert.equal(findEvidenceIndex(segments, "B"), 1);
  assert.equal(findEvidenceIndex(segments, " missing "), -1);
  assert.equal(findEvidenceIndex(segments, "missing"), -1);
  assert.equal(evidenceScrollOffset(0, 72, 300), 0);
  assert.ok(evidenceScrollOffset(10, 72, 300) > 0);
});

test("consecutive same-speaker fragments merge into paragraph turns", () => {
  const segments = [
    { id: "1", speaker: "Murat Sancar", text: "Selam.", startMs: 25000, endMs: 26000, markers: [] },
    { id: "2", speaker: "Murat Sancar", text: "Ben Murat.", startMs: 27000, endMs: 28000, markers: ["IMPORTANT"] },
    {
      id: "3",
      speaker: "Murat Sancar",
      text: "Bu hafta hiçbir katılımcı gelmedi.",
      startMs: 34000,
      endMs: 40000,
      markers: ["RISK"],
    },
    { id: "4", speaker: "Ayşe Yılmaz", text: "Anladım.", startMs: 41000, endMs: 42000, markers: [] },
    { id: "5", speaker: "Ayşe Yılmaz", text: "Hemen bakıyorum.", startMs: 43000, endMs: 45000, markers: ["ACTION"] },
    { id: "6", speaker: "Murat Sancar", text: "Teşekkürler.", startMs: 46000, endMs: 47000, markers: [] },
  ];
  const turns = groupConsecutiveSpeakerTurns(segments);
  assert.equal(turns.length, 3);
  assert.equal(
    turns[0]!.text,
    "Selam. Ben Murat. Bu hafta hiçbir katılımcı gelmedi.",
  );
  assert.deepEqual(turns[0]!.segmentIds, ["1", "2", "3"]);
  assert.equal(turns[0]!.startMs, 25000);
  assert.equal(turns[0]!.endMs, 40000);
  assert.deepEqual(turns[0]!.markers, ["IMPORTANT", "RISK"]);
  assert.equal(turns[1]!.speaker, "Ayşe Yılmaz");
  assert.equal(turns[1]!.text, "Anladım. Hemen bakıyorum.");
  assert.equal(turns[2]!.text, "Teşekkürler.");
  assert.equal(findTurnIndexBySegmentId(turns, "3"), 0);
  assert.equal(findTurnIndexBySegmentId(turns, "5"), 1);
  assert.equal(findTurnIndexBySegmentId(turns, "missing"), -1);
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
  assert.equal(portalMutationsEnabled("http", "headers"), true);
  assert.equal(portalMutationsEnabled("http", "msal"), true);
});

test("portal auth mode resolves from env", () => {
  assert.equal(resolvePortalAuthMode({ VITE_PORTAL_AUTH_MODE: "msal" }), "msal");
  assert.equal(resolvePortalAuthMode({}), "headers");
  assert.equal(resolvePortalAuthMode({ VITE_PORTAL_AUTH_MODE: "mock" }), "headers");
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

test("pipeline review is active for draft notes and done after approval", () => {
  const base = {
    meeting: { id: "m1", title: "T", status: "ENDED" as const, scheduledStartAt: "", participantCount: 0 },
    participants: [],
    seriesTitle: null,
    businessContext: null,
    versions: [],
    approvalHistory: [],
    notes: [
      {
        id: "n1",
        visibility: "SHARED" as const,
        body: "TOPLANTI TUTANAĞI\nÖzet",
        updatedAt: "",
        authorId: "u1",
        approvalStatus: "DRAFT",
        draft: true,
        version: 1,
      },
    ],
    decisions: [],
    actions: [],
    risks: [],
    commitments: [],
    qualityFlags: [],
    partial: false,
  };
  const draftStages = deriveMeetingPipelineStages(base, true);
  assert.equal(draftStages.find((s) => s.id === "REVIEW")?.state, "active");
  assert.equal(draftStages.find((s) => s.id === "NOTES")?.state, "done");

  const approved = {
    ...base,
    notes: [{ ...base.notes[0]!, draft: false, approvalStatus: "APPROVED" }],
  };
  const approvedStages = deriveMeetingPipelineStages(approved, true);
  assert.equal(approvedStages.find((s) => s.id === "REVIEW")?.state, "done");
});

test("approver role can decide approvals", () => {
  assert.equal(canDecideApprovals(permissionsForRole("APPROVER")), true);
  assert.equal(canDecideApprovals(permissionsForRole("VIEWER")), false);
});

test("evidence helpers link segments and format ranges", () => {
  assert.equal(evidenceMatchesSegment({ segmentId: "s1", startMs: 0, endMs: 1, quote: "hi" }, "s1"), true);
  assert.equal(formatEvidenceRange(65000, 72000), "1:05–1:12");
  assert.equal(isSeekableEvidence({ segmentId: "s1", startMs: 0, endMs: 1000, quote: "yes" }), true);
  assert.equal(isSeekableEvidence({ segmentId: "s1", startMs: 0, endMs: 0, quote: "" }), false);
  const detail = {
    meeting: { id: "m1", title: "T", status: "READY" as const, scheduledStartAt: "", participantCount: 0 },
    participants: [],
    seriesTitle: null,
    businessContext: null,
    versions: [],
    approvalHistory: [],
    notes: [],
    decisions: [
      {
        id: "d1",
        meetingId: "m1",
        title: "Decide",
        status: "APPROVED" as const,
        evidence: [{ segmentId: "s1", startMs: 0, endMs: 1000, quote: "yes" }],
        createdAt: "",
      },
    ],
    actions: [],
    risks: [],
    commitments: [],
    qualityFlags: [],
    partial: false,
  };
  assert.equal(findArtifactsForSegment(detail, "s1").length, 1);
  assert.equal(findArtifactsForSegment(detail, "missing").length, 0);
});

test("due date helpers detect overdue items", () => {
  const past = new Date(Date.now() - 86_400_000).toISOString();
  const future = new Date(Date.now() + 86_400_000).toISOString();
  assert.equal(isOverdue(past), true);
  assert.equal(isOverdue(future), false);
  assert.equal(dueDateTone(null), "muted");
  assert.equal(dueDateTone(past), "warn");
});

test("global search merges filtered results", () => {
  const hits = searchMeetings(
    [{ id: "1", title: "Quarterly sync", status: "READY", scheduledStartAt: "", participantCount: 2 }],
    "quarter",
  );
  assert.equal(hits.length, 1);
  assert.equal(mergeSearchResults([hits, hits]).length, 2);
});

test("minutes document parses Turkish plain-text tutanak", async () => {
  const { parseMinutesBody, serializeMinutesBody, parseSectionContent } = await import(
    "./lib/minutesDocument.ts"
  );
  const body = `TOPLANTI TUTANAĞI
Toplantı Başlığı: teams entegrasyon toplantısı
Durum: Taslak (LLM)

1. YÖNETİCİ ÖZETİ
Özet metni burada.

2. ALINAN KARARLAR
—

3. AKSİYON MADDELERİ
1. Murat Sancar tarafından analiz tamamlanması. (Sorumlu: Murat Sancar, Son tarih: —)

4. RİSKLER
1. Gecikme riski.

5. TAAHHÜTLER
1. Burak bitireceğini söyledi.

6. AÇIK SORULAR
1. Takvim ne zaman?
`;
  const doc = parseMinutesBody(body);
  assert.ok(doc);
  assert.equal(doc!.title, "teams entegrasyon toplantısı");
  assert.equal(doc!.statusLabel, "Taslak (NanobaseAI EasyMeeting)");
  const summary = doc!.sections.find((s) => s.type === "EXECUTIVE_SUMMARY");
  assert.equal(parseSectionContent(summary!.value, "paragraph").paragraph, "Özet metni burada.");
  const actions = doc!.sections.find((s) => s.type === "ACTIONS");
  assert.equal(parseSectionContent(actions!.value, "list").items.length, 1);
  const decisions = doc!.sections.find((s) => s.type === "DECISIONS");
  assert.equal(parseSectionContent(decisions!.value, "list").empty, true);
  const roundTrip = parseMinutesBody(serializeMinutesBody(doc!));
  assert.equal(roundTrip!.title, doc!.title);
  assert.equal(
    parseSectionContent(roundTrip!.sections.find((s) => s.type === "RISKS")!.value, "list").items[0],
    "Gecikme riski.",
  );
});

test("dense executive summary expands to numbered lines", async () => {
  const { enhanceParagraphReadability, parseSectionContent } = await import("./lib/minutesDocument.ts");
  const dense =
    "Gündem: Sprint planlama ve kapasite; Ürün gereksinimleri ve filtre davranışı; Toplantı yönetimi ve teknik ayarlar; Mevcut kararların bağlamı ve doğrulanması. 3 karar kaydedildi. 2 aksiyon maddesi. 2 risk.";
  const readable = enhanceParagraphReadability(dense);
  assert.ok(readable.includes("Gündem:\n1. Sprint planlama ve kapasite"));
  assert.ok(readable.includes("2. Ürün gereksinimleri ve filtre davranışı"));
  assert.ok(readable.includes("\n3 karar kaydedildi."));
  assert.ok(readable.includes("\n2 aksiyon maddesi."));
  assert.ok(readable.includes("\n2 risk."));
  assert.equal(parseSectionContent(dense, "paragraph").paragraph, readable);
});

test("finalization fallback is visible as a review reason", async () => {
  const { reviewBannerReasons } = await import("./lib/minutesDocument.ts");
  assert.deepEqual(reviewBannerReasons(["FINALIZATION_FALLBACK"]), ["FINALIZATION_FALLBACK"]);
});

test("person names split longest match for bold highlighting", async () => {
  const { collectPersonNames, splitByPersonNames } = await import("./lib/personNames.ts");
  const names = collectPersonNames(["Murat Sancar", "Burak", "unknown", "ada@example.com"]);
  assert.deepEqual(names, ["Murat Sancar", "Burak"]);
  const segments = splitByPersonNames(
    "Murat Sancar tarafından analiz tamamlanması. Burak bitireceğini söyledi.",
    names,
  );
  assert.deepEqual(
    segments.filter((s) => s.isName).map((s) => s.text),
    ["Murat Sancar", "Burak"],
  );
  assert.equal(segments[0]?.isName, true);
});

test("onboarding progress tracks completed steps", () => {
  const state = defaultOnboardingState();
  assert.equal(onboardingProgress(state).done, 1);
  const next = markOnboardingStep(state, "teams_connected");
  assert.equal(onboardingProgress(next).done, 2);
});
