import type { ApprovalRecord } from "../api/types";

export type ApprovalDecision = "APPROVE" | "REJECT";

/**
 * Approvals are NOT optimistic-safe: they gate external delivery.
 * Apply only after a successful server response.
 */
export function applyApprovalDecision(
  record: ApprovalRecord,
  decision: ApprovalDecision,
  actorDisplayName: string,
  at: string,
  comment?: string,
): ApprovalRecord {
  if (record.status !== "PENDING") {
    throw new Error(`Approval ${record.id} is not pending`);
  }
  return {
    ...record,
    status: decision === "APPROVE" ? "APPROVED" : "REJECTED",
    decidedBy: actorDisplayName,
    decidedAt: at,
    comment: comment ?? null,
  };
}

/** Safe optimistic ops: local UI toggles that can roll back without side effects. */
export const OPTIMISTIC_SAFE_OPS = ["completeAction", "updateMeetingNote"] as const;
export type OptimisticSafeOp = (typeof OPTIMISTIC_SAFE_OPS)[number];

export function isOptimisticSafe(op: string): op is OptimisticSafeOp {
  return (OPTIMISTIC_SAFE_OPS as readonly string[]).includes(op);
}
