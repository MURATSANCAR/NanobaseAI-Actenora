package ai.nanobase.actenora.policy.domain;

/**
 * Rules for meetings that include external (non-tenant) participants.
 */
public record ExternalParticipantPolicy(
        boolean externalParticipantsAllowed,
        boolean redactExternalContent,
        boolean requireOrganizerApproval
) {
    public static ExternalParticipantPolicy systemDefaults() {
        return new ExternalParticipantPolicy(true, true, true);
    }
}
