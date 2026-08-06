/** Typed contracts mirroring packages/api-contracts/openapi/platform-api.yaml (FAZ 24). */

export type PortalRole = "VIEWER" | "MEMBER" | "APPROVER" | "ADMIN" | "OPERATIONS";

export type Permission =
  | "meetings:read"
  | "meetings:edit"
  | "notes:private:read_own"
  | "notes:private:read_any"
  | "approvals:decide"
  | "models:view"
  | "models:admin"
  | "models:routing_detail"
  | "operations:view"
  | "audit:view"
  | "templates:edit"
  | "teams:settings";

export type MeetingOccurrenceStatus =
  | "DRAFT"
  | "SCHEDULED"
  | "IN_PROGRESS"
  | "ENDED"
  | "CANCELLED"
  | "PROCESSING"
  | "READY"
  | "FAILED";

export type ArtifactStatus =
  | "DRAFT"
  | "PENDING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "COMPLETED"
  | "OPEN"
  | "IN_PROGRESS"
  | "CANCELLED"
  | "AT_RISK";

export interface CompositionAware {
  compositionStub?: boolean;
}

export type MarkerKind = "DECISION" | "ACTION" | "RISK" | "QUESTION" | "IMPORTANT";

export interface PortalUser {
  id: string;
  displayName: string;
  email: string;
  role: PortalRole;
  tenantId: string;
  permissions: Permission[];
}

export interface MeetingSummary {
  id: string;
  title: string;
  status: MeetingOccurrenceStatus;
  scheduledStartAt: string;
  participantCount: number;
}

export interface CursorPage<T> extends CompositionAware {
  items: T[];
  nextCursor: string | null;
}

export interface EvidenceRef {
  segmentId: string;
  startMs: number;
  endMs: number;
  quote: string;
}

export interface MeetingNote {
  id: string;
  visibility: "SHARED" | "PRIVATE";
  body: string;
  updatedAt: string;
  authorId: string;
  approvalStatus?: string | null;
  draft?: boolean;
  version?: number;
}

export interface DecisionItem {
  id: string;
  meetingId: string;
  title: string;
  status: ArtifactStatus;
  evidence: EvidenceRef[];
  createdAt: string;
  rationale?: string | null;
  decisionStatus?: string | null;
}

export interface ActionItem {
  id: string;
  meetingId: string;
  title: string;
  status: ArtifactStatus;
  ownerDisplayName: string;
  dueAt: string | null;
  evidence: EvidenceRef[];
  ownerType?: string | null;
  priority?: string | null;
  relativeDate?: string | null;
}

export interface RiskItem {
  id: string;
  title: string;
  severity: "LOW" | "MEDIUM" | "HIGH";
  evidence: EvidenceRef[];
  likelihood?: string | null;
  mitigation?: string | null;
}

export interface CommitmentItem {
  id: string;
  meetingId: string;
  statement: string;
  ownerDisplayName: string;
  dueAt: string | null;
  status: ArtifactStatus;
  evidence: EvidenceRef[];
}

export interface ApprovalRecord {
  id: string;
  artifactType: "DECISION" | "ACTION" | "COMMITMENT" | "NOTE" | "DELIVERY" | "MEETING_NOTE_VERSION";
  artifactId: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  decidedBy: string | null;
  decidedAt: string | null;
  comment: string | null;
}

export interface Participant {
  id: string;
  displayName: string;
  email: string;
  participantType: string;
  attendanceStatus: string;
  external: boolean;
}

export interface MeetingVersion {
  version: number;
  label: string;
  createdAt: string;
}

export interface MeetingDetailResponse {
  meeting: MeetingSummary;
  participants: Participant[];
  seriesTitle: string | null;
  businessContext: string | null;
  versions: MeetingVersion[];
  approvalHistory: ApprovalRecord[];
  notes: MeetingNote[];
  decisions: DecisionItem[];
  actions: ActionItem[];
  risks: RiskItem[];
  commitments: CommitmentItem[];
  qualityFlags: string[];
  partial: boolean;
}

export interface MeetingDeliveryRequest {
  id: string;
  noteVersionId: string;
  intent: string;
  status: string;
  recipientEmail: string;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface NoteRenderJob {
  id: string;
  format: string;
  status: string;
  renderedDocumentId: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  lastError: string | null;
}

export interface NoteRenderedDocument {
  id: string;
  format: string;
  sizeBytes: number;
  downloadUrl: string | null;
  expiresAt: string | null;
  createdAt: string | null;
}

export interface NoteRenderStatus {
  jobs: NoteRenderJob[];
  documents: NoteRenderedDocument[];
}

export interface TranscriptSegment {
  id: string;
  speaker: string;
  text: string;
  startMs: number;
  endMs: number;
  markers: MarkerKind[];
}

export interface TranscriptResponse {
  segments: TranscriptSegment[];
  speakers: string[];
}

export interface PendingApprovalGroup {
  meetingId: string;
  meetingTitle: string;
  items: ApprovalRecord[];
}

export interface PendingApprovalsResponse {
  groups: PendingApprovalGroup[];
}

export interface DashboardResponse {
  pendingApprovals: number;
  openActions: number;
  overdueCommitments: number;
  runningJobs: number;
  recentMeetings: MeetingSummary[];
}

export interface MeetingPrepDecision {
  id: string;
  meetingId: string;
  text: string;
  recordedAt: string;
}

export interface MeetingPrepCarryOver {
  id: string;
  kind: string;
  text: string;
  sourceMeetingId: string;
}

export interface MeetingPrepCommitment {
  id: string;
  meetingId: string;
  text: string;
  owner: string | null;
  dueDate: string | null;
  status: string;
  overdue: boolean;
  updatedAt: string;
}

export interface MeetingPrepContradiction {
  id: string;
  meetingId: string;
  leftDecisionId: string;
  rightDecisionId: string;
  reason: string;
  confidence: string;
  status: string;
}

export interface MeetingPrepAgendaItem {
  id: string;
  sourceType: string;
  text: string;
  sourceMeetingId: string;
}

export interface MeetingPrepResponse {
  briefId: string;
  targetMeetingId: string;
  previousMeetingId: string | null;
  meetingSeriesId: string | null;
  businessContextId: string | null;
  previousDecisions: MeetingPrepDecision[];
  openActions: MeetingPrepCarryOver[];
  openRisks: MeetingPrepCarryOver[];
  openQuestions: MeetingPrepCarryOver[];
  overdueCommitments: MeetingPrepCommitment[];
  contradictions: MeetingPrepContradiction[];
  suggestedAgenda: MeetingPrepAgendaItem[];
  generatedAt: string;
}

export interface WorkAction {
  id: string;
  meetingId: string;
  noteId: string;
  title: string;
  status: ArtifactStatus;
  owner: string | null;
  dueAt: string | null;
  priority: string | null;
  ownerType: string | null;
  version: number;
}

export interface MyWorkResponse {
  assignedActions: WorkAction[];
  dueSoonActions: WorkAction[];
  overdueActions: WorkAction[];
  pendingApprovals: Array<{
    id: string;
    subjectType: string;
    subjectId: string;
    status: string;
    version: number;
    updatedAt: string;
    expiresAt: string | null;
  }>;
  recentCommitments: MeetingPrepCommitment[];
  today: string;
  upcomingUntil: string;
}

export interface ActionRequestResponse {
  requestId: string;
  requestType: string;
}

export type GlobalSearchKind =
  | "DECISION"
  | "ACTION_ITEM"
  | "COMMITMENT"
  | "RISK"
  | "OPEN_QUESTION";

export interface GlobalSearchHit {
  id: string;
  meetingId: string;
  sourceItemId: string;
  kind: GlobalSearchKind;
  content: string;
  score: number;
  href: string;
}

export interface GlobalSearchResponse {
  query: string;
  items: GlobalSearchHit[];
}

export interface MeetingQuestionCitation {
  segmentId: string;
  speaker: string;
  quote: string;
  startMs: number;
  endMs: number;
}

export interface MeetingQuestionResponse {
  status: "ANSWERED" | "INSUFFICIENT_EVIDENCE";
  answer: string | null;
  citations: MeetingQuestionCitation[];
  modelVersion: string | null;
  inputTokens: number;
  outputTokens: number;
}

export interface OutlookDraftResponse {
  providerMessageId: string;
  webLink: string | null;
  reused: boolean;
  recipientCount: number;
}

export type NotificationType =
  | "APPROVAL_REQUESTED"
  | "DRAFT_MINUTES_READY"
  | "AI_JOB_FAILED"
  | "ACTION_OVERDUE"
  | "COMMITMENT_OVERDUE";

export interface PortalNotificationItem {
  id: string;
  type: NotificationType | string;
  title: string;
  body: string;
  href: string;
  createdAt: string | null;
  readAt: string | null;
}

export interface PortalNotificationFeed {
  items: PortalNotificationItem[];
  unreadCount: number;
}

export interface TemplateSummary {
  id: string;
  name: string;
  locale: string;
  version: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  isDefault: boolean;
}

export interface DesignComponentView {
  id: string;
  type: string;
  order: number;
  props: Record<string, string>;
}

export interface DesignSchemaView {
  schemaVersion: number;
  pageSize: string;
  components: DesignComponentView[];
}

export interface TemplateVersionDetail {
  id: string;
  versionNumber: number;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  changelog: string;
  updatedAt: string;
  designSchema: DesignSchemaView | null;
}

export interface TemplateDetail {
  id: string;
  name: string;
  locale: string;
  versions: TemplateVersionDetail[];
  publishedVersionId: string | null;
  isDefault: boolean;
}

/**
 * Effective template binding for a note. `locked` is false while the binding is only the
 * tenant default suggestion — the note is pinned on first save.
 */
export interface NoteTemplateLock {
  templateId: string;
  templateName: string;
  templateVersionId: string;
  templateVersionNumber: number;
  locked: boolean;
  designSchema: DesignSchemaView | null;
}

export interface TeamsSettings extends CompositionAware {
  tenantConnected: boolean;
  graphAppId: string;
  webhookStatus: string;
  autoJoinEnabled: boolean;
}

export interface NanobaseAiConnection {
  productName: string;
  mode: "nanobaseai" | "offline" | string;
  enabled: boolean;
  endpointHost: string;
  baseUrl: string;
  healthy: boolean;
  latencyMs: number;
  statusDetail: string;
  servedModelIds: string[];
  checkedAt: string;
}

export interface TemplateListResponse extends CompositionAware {
  items: TemplateSummary[];
}

export interface ModelHealthResponse extends CompositionAware {
  models: Array<{
    modelKey: string;
    displayName: string;
    enabled: boolean;
    status: string;
  }>;
  deployments: Array<{
    deploymentKey: string;
    modelKey: string;
    nodeName: string;
    healthy: boolean;
  }>;
  routing: {
    strategy: string;
    roles: Array<{
      role: string;
      primaryModel: string;
      fallbackModel: string;
    }>;
  };
}

export interface AiJob {
  id: string;
  meetingId: string;
  meetingTitle: string;
  status: string;
  stage: string;
  startedAt: string;
  finishedAt: string | null;
}

export interface OperationsOverview extends CompositionAware {
  queueDepth: number;
  failedJobs: number;
  circuitBreakers: Array<{ name: string; state: string }>;
  workers: Array<{ name: string; status: string }>;
}

export interface AuditEvent {
  id: string;
  action: string;
  actorName: string;
  resourceLabel: string;
  resourceType: string;
  resourceId: string;
  at: string;
}

export interface ListMeetingsParams {
  status?: MeetingOccurrenceStatus;
  cursor?: string;
  limit?: number;
  q?: string;
}

export interface ListArtifactsParams {
  status?: ArtifactStatus;
  cursor?: string;
  limit?: number;
}

export interface ApiClient {
  getCurrentUser(): Promise<PortalUser>;
  getDashboard(): Promise<DashboardResponse>;
  listNotifications(params?: { limit?: number }): Promise<PortalNotificationFeed>;
  markNotificationRead(id: string): Promise<void>;
  markAllNotificationsRead(): Promise<void>;
  listMeetings(params?: ListMeetingsParams): Promise<CursorPage<MeetingSummary>>;
  getMeetingDetail(meetingId: string): Promise<MeetingDetailResponse>;
  getMeetingPrep(meetingId: string): Promise<MeetingPrepResponse>;
  getMyWork(): Promise<MyWorkResponse>;
  getMeetingDelivery(meetingId: string): Promise<MeetingDeliveryRequest[]>;
  getNoteRenders(meetingId: string, noteId: string): Promise<NoteRenderStatus>;
  downloadNotePdf(meetingId: string, noteId: string): Promise<Blob>;
  getMeetingTranscript(
    meetingId: string,
    params?: { speaker?: string; q?: string },
  ): Promise<TranscriptResponse>;
  updateMeetingNote(meetingId: string, noteId: string, body: string): Promise<MeetingNote>;
  submitNoteForApproval(
    meetingId: string,
    noteId: string,
    expectedVersion?: number,
  ): Promise<ApprovalRecord>;
  decideApproval(
    approvalId: string,
    decision: "APPROVE" | "REJECT",
    comment?: string,
  ): Promise<ApprovalRecord>;
  listPendingApprovals(): Promise<PendingApprovalsResponse>;
  listDecisions(params?: ListArtifactsParams): Promise<CursorPage<DecisionItem>>;
  listActions(params?: ListArtifactsParams): Promise<CursorPage<ActionItem>>;
  completeAction(actionId: string): Promise<ActionItem>;
  disputeAction(
    actionId: string,
    body: { reason: string; proposedTitle?: string },
  ): Promise<ActionRequestResponse>;
  requestActionDueDateChange(
    actionId: string,
    body: { requestedDueDate: string; reason: string },
  ): Promise<ActionRequestResponse>;
  listCommitments(params?: ListArtifactsParams): Promise<CursorPage<CommitmentItem>>;
  globalSearch(params: { q: string; limit?: number }): Promise<GlobalSearchResponse>;
  askMeeting(meetingId: string, question: string): Promise<MeetingQuestionResponse>;
  createOutlookDraft(meetingId: string, noteId: string): Promise<OutlookDraftResponse>;
  listTemplates(): Promise<TemplateListResponse>;
  createTemplate(body: { name: string; locale?: string }): Promise<TemplateSummary>;
  getTemplate(templateId: string): Promise<TemplateDetail>;
  createTemplateDraft(
    templateId: string,
    body: { changelog?: string },
  ): Promise<TemplateVersionDetail>;
  saveTemplateDesign(
    templateId: string,
    versionId: string,
    body: { designSchemaJson: string; contentSchemaJson?: string },
  ): Promise<TemplateVersionDetail>;
  publishTemplateVersion(templateId: string, versionId: string): Promise<TemplateVersionDetail>;
  setDefaultTemplate(templateId: string): Promise<TemplateDetail>;
  getNoteTemplateLock(meetingId: string, noteId: string): Promise<NoteTemplateLock | null>;
  lockNoteTemplate(meetingId: string, noteId: string, templateVersionId: string): Promise<NoteTemplateLock>;
  getTeamsSettings(): Promise<TeamsSettings>;
  updateTeamsSettings(body: { autoJoinEnabled: boolean }): Promise<TeamsSettings>;
  getNanobaseAiConnection(): Promise<NanobaseAiConnection>;
  updateNanobaseAiConnection(body: {
    baseUrl?: string;
    enabled?: boolean;
    servedModelIds?: string[];
  }): Promise<NanobaseAiConnection>;
  testNanobaseAiConnection(): Promise<NanobaseAiConnection>;
  getModelHealth(): Promise<ModelHealthResponse>;
  listAiJobs(params?: { cursor?: string; limit?: number }): Promise<CursorPage<AiJob>>;
  getOperationsOverview(): Promise<OperationsOverview>;
  listAuditEvents(params?: { cursor?: string; limit?: number }): Promise<CursorPage<AuditEvent>>;
}
