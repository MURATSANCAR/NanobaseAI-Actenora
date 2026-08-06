/**
 * Normalizes an owner/assignee display name for the UI.
 * The backend uses the literal sentinel `"unknown"` (and empty string) for an
 * unassigned owner; both must render as the localized "unassigned" label rather
 * than leaking the raw sentinel into the interface.
 */
export function formatOwner(
  ownerDisplayName: string | null | undefined,
  unassignedLabel: string,
): string {
  if (!ownerDisplayName || ownerDisplayName === "unknown") {
    return unassignedLabel;
  }
  return ownerDisplayName;
}

function normalizeOwner(value: string | null | undefined): string {
  return (value ?? "").trim().toLocaleLowerCase();
}

/**
 * Whether an artifact owner refers to the given user. Artifacts only carry an
 * owner *display name* (no stable id/email), so this is a best-effort, drift-
 * tolerant name match — case- and whitespace-insensitive. A server-side owner id
 * would make this exact; until then this is the most robust match available.
 */
export function ownerMatchesUser(
  ownerDisplayName: string | null | undefined,
  userDisplayName: string | null | undefined,
): boolean {
  const owner = normalizeOwner(ownerDisplayName);
  const user = normalizeOwner(userDisplayName);
  if (!owner || !user || owner === "unknown") return false;
  return owner === user;
}
