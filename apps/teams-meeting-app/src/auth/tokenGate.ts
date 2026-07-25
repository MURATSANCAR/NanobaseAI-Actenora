import type { UntrustedTeamsContext } from "../domain/types.js";

/**
 * Teams client context is untrusted. It may be shown in the UI, but every
 * mutating/API call must carry a backend-validated bearer token.
 */
export function assertBackendToken(authorizationHeader: string | undefined | null): string {
  if (!authorizationHeader || !authorizationHeader.trim()) {
    throw new Error("INVALID_MEETING_APP_TOKEN: Teams context alone is not trusted");
  }
  const token = authorizationHeader.startsWith("Bearer ")
    ? authorizationHeader.slice("Bearer ".length).trim()
    : authorizationHeader.trim();
  if (!token) {
    throw new Error("INVALID_MEETING_APP_TOKEN: empty bearer token");
  }
  return token;
}

export function buildAuthHeaders(
  backendToken: string,
  teamsContext: UntrustedTeamsContext = {},
): Record<string, string> {
  const token = assertBackendToken(backendToken.startsWith("Bearer ") ? backendToken : `Bearer ${backendToken}`);
  const headers: Record<string, string> = {
    Authorization: `Bearer ${token}`,
    "content-type": "application/json",
  };
  if (teamsContext.teamsMeetingId) headers["X-Teams-Meeting-Id"] = teamsContext.teamsMeetingId;
  if (teamsContext.chatId) headers["X-Teams-Chat-Id"] = teamsContext.chatId;
  if (teamsContext.claimedTenantId) headers["X-Teams-Claimed-Tenant-Id"] = teamsContext.claimedTenantId;
  if (teamsContext.claimedUserId) headers["X-Teams-Claimed-User-Id"] = teamsContext.claimedUserId;
  return headers;
}
