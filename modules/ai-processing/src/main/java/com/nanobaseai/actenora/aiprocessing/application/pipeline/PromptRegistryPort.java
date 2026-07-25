package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.prompt.PublishedPrompt;

/**
 * Fetches immutable published prompts. Implementations must not mutate templates.
 */
public interface PromptRegistryPort {

    PublishedPrompt requirePublished(String promptId);
}
