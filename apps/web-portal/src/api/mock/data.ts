import { permissionsForRole } from "../../auth/permissions";
import type {
  ActionItem,
  AiJob,
  ApprovalRecord,
  AuditEvent,
  CommitmentItem,
  DecisionItem,
  MeetingDetailResponse,
  MeetingNote,
  MeetingSummary,
  ModelHealthResponse,
  OperationsOverview,
  PortalRole,
  PortalUser,
  TemplateSummary,
  TeamsSettings,
  TranscriptSegment,
} from "../types";

const TENANT = "11111111-1111-1111-1111-111111111111";
const USER_IDS = {
  admin: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  approver: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  member: "cccccccc-cccc-cccc-cccc-cccccccccccc",
  viewer: "dddddddd-dddd-dddd-dddd-dddddddddddd",
  ops: "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
} as const;

const MEETING_A = "m1111111-1111-1111-1111-111111111111";
const MEETING_B = "m2222222-2222-2222-2222-222222222222";
const SEG_1 = "s1111111-1111-1111-1111-111111111111";
const SEG_2 = "s2222222-2222-2222-2222-222222222222";
const SEG_3 = "s3333333-3333-3333-3333-333333333333";
const DECISION_1 = "d1111111-1111-1111-1111-111111111111";
const ACTION_1 = "a1111111-1111-1111-1111-111111111111";
const ACTION_2 = "a2222222-2222-2222-2222-222222222222";
const COMMIT_1 = "c1111111-1111-1111-1111-111111111111";
const APPROVAL_1 = "p1111111-1111-1111-1111-111111111111";
const NOTE_SHARED = "n1111111-1111-1111-1111-111111111111";
const NOTE_PRIVATE = "n2222222-2222-2222-2222-222222222222";
const NOTE_OTHER_PRIVATE = "n3333333-3333-3333-3333-333333333333";

export function createUser(role: PortalRole): PortalUser {
  const map: Record<PortalRole, { id: string; displayName: string; email: string }> = {
    ADMIN: { id: USER_IDS.admin, displayName: "Ada Admin", email: "ada@actenora.local" },
    APPROVER: {
      id: USER_IDS.approver,
      displayName: "Omar Approver",
      email: "omar@actenora.local",
    },
    MEMBER: { id: USER_IDS.member, displayName: "Mia Member", email: "mia@actenora.local" },
    VIEWER: { id: USER_IDS.viewer, displayName: "Vera Viewer", email: "vera@actenora.local" },
    OPERATIONS: { id: USER_IDS.ops, displayName: "Otto Ops", email: "otto@actenora.local" },
  };
  const base = map[role];
  return {
    ...base,
    role,
    tenantId: TENANT,
    permissions: permissionsForRole(role),
  };
}

export const meetings: MeetingSummary[] = [
  {
    id: MEETING_A,
    title: "Q3 roadmap sync",
    status: "READY",
    scheduledStartAt: "2026-07-20T09:00:00Z",
    participantCount: 5,
  },
  {
    id: MEETING_B,
    title: "Customer escalation review",
    status: "PROCESSING",
    scheduledStartAt: "2026-07-22T14:00:00Z",
    participantCount: 3,
  },
  {
    id: "m3333333-3333-3333-3333-333333333333",
    title: "Weekly standup",
    status: "ENDED",
    scheduledStartAt: "2026-07-24T08:30:00Z",
    participantCount: 8,
  },
];

export const transcriptSegments: TranscriptSegment[] = Array.from({ length: 120 }, (_, i) => {
  const speakers = ["Ada Admin", "Omar Approver", "Mia Member"];
  const speaker = speakers[i % speakers.length]!;
  const base: TranscriptSegment = {
    id: i === 0 ? SEG_1 : i === 1 ? SEG_2 : i === 2 ? SEG_3 : `s${String(i).padStart(8, "0")}-0000-0000-0000-000000000000`,
    speaker,
    text: `Segment ${i + 1}: discussing deliverable ownership and timeline risks for the Actenora portal.`,
    startMs: i * 4000,
    endMs: i * 4000 + 3800,
    markers: [],
  };
  if (i === 0) {
    base.text = "We will ship evidence-linked decisions before any external delivery.";
    base.markers = ["DECISION", "IMPORTANT"];
  }
  if (i === 1) {
    base.text = "Omar will own the Action Center filter polish by Friday.";
    base.markers = ["ACTION"];
  }
  if (i === 2) {
    base.text = "There is a risk if model routing details leak to non-admins.";
    base.markers = ["RISK"];
  }
  return base;
});

function seedDecisions(): DecisionItem[] {
  return [
    {
      id: DECISION_1,
      meetingId: MEETING_A,
      title: "Ship evidence-linked decisions before delivery",
      status: "PENDING_APPROVAL",
      evidence: [
        {
          segmentId: SEG_1,
          startMs: 0,
          endMs: 3800,
          quote: "We will ship evidence-linked decisions before any external delivery.",
        },
      ],
      createdAt: "2026-07-20T10:05:00Z",
    },
  ];
}

function seedActions(): ActionItem[] {
  return [
    {
      id: ACTION_1,
      meetingId: MEETING_A,
      title: "Polish Action Center filters",
      status: "OPEN",
      ownerDisplayName: "Omar Approver",
      dueAt: "2026-07-25T17:00:00Z",
      evidence: [
        {
          segmentId: SEG_2,
          startMs: 4000,
          endMs: 7800,
          quote: "Omar will own the Action Center filter polish by Friday.",
        },
      ],
    },
    {
      id: ACTION_2,
      meetingId: MEETING_B,
      title: "Confirm escalation owner",
      status: "PENDING_APPROVAL",
      ownerDisplayName: "Mia Member",
      dueAt: null,
      evidence: [],
    },
  ];
}

function seedCommitments(): CommitmentItem[] {
  return [
    {
      id: COMMIT_1,
      meetingId: MEETING_A,
      statement: "Keep model routing detail admin-only in the portal",
      ownerDisplayName: "Ada Admin",
      dueAt: "2026-07-30T00:00:00Z",
      status: "OPEN",
      evidence: [
        {
          segmentId: SEG_3,
          startMs: 8000,
          endMs: 11800,
          quote: "There is a risk if model routing details leak to non-admins.",
        },
      ],
    },
  ];
}

function seedApprovals(): ApprovalRecord[] {
  return [
    {
      id: APPROVAL_1,
      artifactType: "DECISION",
      artifactId: DECISION_1,
      status: "PENDING",
      decidedBy: null,
      decidedAt: null,
      comment: null,
    },
  ];
}

function seedNotes(): MeetingNote[] {
  return [
    {
      id: NOTE_SHARED,
      visibility: "SHARED",
      body: "Shared summary: focus on evidence scroll and RBAC.",
      updatedAt: "2026-07-20T10:10:00Z",
      authorId: USER_IDS.member,
    },
    {
      id: NOTE_PRIVATE,
      visibility: "PRIVATE",
      body: "Private note from Mia — only Mia / admin.",
      updatedAt: "2026-07-20T10:12:00Z",
      authorId: USER_IDS.member,
    },
    {
      id: NOTE_OTHER_PRIVATE,
      visibility: "PRIVATE",
      body: "Private note from Omar — hidden from Mia.",
      updatedAt: "2026-07-20T10:13:00Z",
      authorId: USER_IDS.approver,
    },
  ];
}

export let decisions: DecisionItem[] = seedDecisions();
export let actions: ActionItem[] = seedActions();
export let commitments: CommitmentItem[] = seedCommitments();
export let approvals: ApprovalRecord[] = seedApprovals();
export let notes: MeetingNote[] = seedNotes();

export function resetMockStore(): void {
  decisions = seedDecisions();
  actions = seedActions();
  commitments = seedCommitments();
  approvals = seedApprovals();
  notes = seedNotes();
}

export const templates: TemplateSummary[] = [
  {
    id: "t1111111-1111-1111-1111-111111111111",
    name: "Executive decision memo",
    locale: "en-US",
    version: 3,
    status: "PUBLISHED",
  },
  {
    id: "t2222222-2222-2222-2222-222222222222",
    name: "Action digest",
    locale: "tr-TR",
    version: 1,
    status: "DRAFT",
  },
];

export const teamsSettings: TeamsSettings = {
  tenantConnected: true,
  graphAppId: "graph-app-demo",
  webhookStatus: "HEALTHY",
  autoJoinEnabled: false,
};

export const modelHealth: ModelHealthResponse = {
  models: [
    { modelKey: "qwen27-final", displayName: "Qwen 2.7 Final", enabled: true, status: "READY" },
    { modelKey: "fast-extract", displayName: "Fast Extract", enabled: true, status: "READY" },
  ],
  deployments: [
    {
      deploymentKey: "qwen-node-a",
      modelKey: "qwen27-final",
      nodeName: "gpu-a1",
      healthy: true,
    },
  ],
  routing: {
    strategy: "role-primary-with-fallback",
    roles: [
      { role: "FAST_EXTRACTION", primaryModel: "fast-extract", fallbackModel: "qwen27-final" },
      { role: "QWEN27_FINAL", primaryModel: "qwen27-final", fallbackModel: "fast-extract" },
    ],
  },
};

export const aiJobs: AiJob[] = [
  {
    id: "j1111111-1111-1111-1111-111111111111",
    meetingId: MEETING_A,
    status: "SUCCEEDED",
    stage: "MERGE",
    startedAt: "2026-07-20T09:45:00Z",
    finishedAt: "2026-07-20T09:52:00Z",
  },
  {
    id: "j2222222-2222-2222-2222-222222222222",
    meetingId: MEETING_B,
    status: "RUNNING",
    stage: "EXTRACTION",
    startedAt: "2026-07-22T14:20:00Z",
    finishedAt: null,
  },
];

export const operations: OperationsOverview = {
  queueDepth: 4,
  failedJobs: 1,
  circuitBreakers: [
    { name: "transcript-fetch", state: "CLOSED" },
    { name: "model-route", state: "HALF_OPEN" },
  ],
  workers: [
    { name: "ai-orchestrator", status: "UP" },
    { name: "delivery-worker", status: "UP" },
  ],
};

export const auditEvents: AuditEvent[] = [
  {
    id: "e1111111-1111-1111-1111-111111111111",
    action: "MEETING_UPDATED",
    actor: "Mia Member",
    resourceType: "MeetingOccurrence",
    resourceId: MEETING_A,
    at: "2026-07-20T10:00:00Z",
  },
  {
    id: "e2222222-2222-2222-2222-222222222222",
    action: "MODEL_ENABLE",
    actor: "Ada Admin",
    resourceType: "ModelDefinition",
    resourceId: "qwen27-final",
    at: "2026-07-19T16:00:00Z",
  },
];

export function meetingDetail(meetingId: string): MeetingDetailResponse {
  const meeting = meetings.find((m) => m.id === meetingId) ?? meetings[0]!;
  return {
    meeting,
    participants: [
      {
        id: "part-1",
        displayName: "Ada Admin",
        email: "ada@actenora.local",
        participantType: "ORGANIZER",
        attendanceStatus: "ATTENDED",
        external: false,
      },
      {
        id: "part-2",
        displayName: "Omar Approver",
        email: "omar@actenora.local",
        participantType: "REQUIRED",
        attendanceStatus: "ATTENDED",
        external: false,
      },
      {
        id: "part-3",
        displayName: "Mia Member",
        email: "mia@actenora.local",
        participantType: "OPTIONAL",
        attendanceStatus: "ATTENDED",
        external: false,
      },
    ],
    seriesTitle: "Product sync series",
    businessContext: "Actenora portal GTM",
    versions: [
      { version: 1, label: "Initial AI draft", createdAt: "2026-07-20T09:52:00Z" },
      { version: 2, label: "Human edits", createdAt: "2026-07-20T10:15:00Z" },
    ],
    approvalHistory: approvals.filter((a) =>
      [...decisions, ...actions, ...commitments].some(
        (x) => x.id === a.artifactId && "meetingId" in x && x.meetingId === meeting.id,
      ),
    ),
    notes: notes.map((n) => ({ ...n })),
    decisions: decisions.filter((d) => d.meetingId === meeting.id).map((d) => ({ ...d })),
    actions: actions.filter((a) => a.meetingId === meeting.id).map((a) => ({ ...a })),
    risks: [
      {
        id: "r1111111-1111-1111-1111-111111111111",
        title: "Routing detail leakage",
        severity: "HIGH",
        evidence: [
          {
            segmentId: SEG_3,
            startMs: 8000,
            endMs: 11800,
            quote: "There is a risk if model routing details leak to non-admins.",
          },
        ],
      },
    ],
    commitments: commitments.filter((c) => c.meetingId === meeting.id).map((c) => ({ ...c })),
    qualityFlags: ["LOW_CONFIDENCE_SPEAKER_3", "PARTIAL_TRANSCRIPT_TAIL"],
    partial: meeting.status === "PROCESSING",
  };
}

export const IDS = {
  MEETING_A,
  MEETING_B,
  SEG_1,
  SEG_2,
  SEG_3,
  DECISION_1,
  ACTION_1,
  ACTION_2,
  COMMIT_1,
  APPROVAL_1,
  NOTE_SHARED,
  NOTE_PRIVATE,
  NOTE_OTHER_PRIVATE,
  USER_IDS,
};
