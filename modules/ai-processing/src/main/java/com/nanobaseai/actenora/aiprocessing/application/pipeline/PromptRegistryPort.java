package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;

import java.util.List;
import java.util.Optional;

/**
 * Fetches immutable published prompts. Implementations must not mutate templates in place —
 * publishing always creates a new version (PROMPT-VERSIONING).
 */
public interface PromptRegistryPort {

    /** Latest published version for a logical prompt id. */
    PublishedPrompt requirePublished(String promptId);

    /** Exact published version by {@code promptVersionId}. */
    PublishedPrompt requireByVersionId(String promptVersionId);

    Optional<PublishedPrompt> findByVersionId(String promptVersionId);

    /**
     * Publishes a new immutable version. Prior versions remain resolvable by version id.
     * The returned prompt is the new latest for its {@code promptId}.
     */
    PublishedPrompt publish(String promptId, String template, String outputSchemaId, String modelCapabilityRequired);

    List<PublishedPrompt> listVersions(String promptId);
}
