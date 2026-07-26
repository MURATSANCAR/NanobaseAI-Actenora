import type { ActionItem, CommitmentItem, DecisionItem, MeetingSummary } from "@/api/types";

export type SearchResultKind = "meeting" | "decision" | "action" | "commitment";

export type SearchResult = {
  kind: SearchResultKind;
  id: string;
  title: string;
  subtitle?: string;
  href: string;
};

function matchesQuery(text: string, q: string): boolean {
  return text.toLowerCase().includes(q.trim().toLowerCase());
}

export function searchMeetings(meetings: MeetingSummary[], q: string, limit = 8): SearchResult[] {
  if (!q.trim()) return [];
  return meetings
    .filter((m) => matchesQuery(m.title, q))
    .slice(0, limit)
    .map((m) => ({
      kind: "meeting" as const,
      id: m.id,
      title: m.title,
      subtitle: m.status,
      href: `/meetings/${m.id}`,
    }));
}

export function searchDecisions(decisions: DecisionItem[], q: string, limit = 8): SearchResult[] {
  if (!q.trim()) return [];
  return decisions
    .filter((d) => matchesQuery(d.title, q))
    .slice(0, limit)
    .map((d) => ({
      kind: "decision" as const,
      id: d.id,
      title: d.title,
      subtitle: d.status,
      href: `/meetings/${d.meetingId}`,
    }));
}

export function searchActions(actions: ActionItem[], q: string, limit = 8): SearchResult[] {
  if (!q.trim()) return [];
  return actions
    .filter((a) => matchesQuery(a.title, q) || matchesQuery(a.ownerDisplayName, q))
    .slice(0, limit)
    .map((a) => ({
      kind: "action" as const,
      id: a.id,
      title: a.title,
      subtitle: a.ownerDisplayName,
      href: `/meetings/${a.meetingId}`,
    }));
}

export function searchCommitments(
  commitments: CommitmentItem[],
  q: string,
  limit = 8,
): SearchResult[] {
  if (!q.trim()) return [];
  return commitments
    .filter((c) => matchesQuery(c.statement, q) || matchesQuery(c.ownerDisplayName, q))
    .slice(0, limit)
    .map((c) => ({
      kind: "commitment" as const,
      id: c.id,
      title: c.statement,
      subtitle: c.ownerDisplayName,
      href: `/meetings/${c.meetingId}`,
    }));
}

export function mergeSearchResults(groups: SearchResult[][]): SearchResult[] {
  return groups.flat().slice(0, 20);
}
