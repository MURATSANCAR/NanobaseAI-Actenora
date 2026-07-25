package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generation knobs passed to the local runtime. Unknown keys are preserved for adapter forwarding
 * but must never be logged when they could embed prompt fragments.
 */
public final class GenerationParameters {

    private final Double temperature;
    private final Integer maxTokens;
    private final Double topP;
    private final Integer topK;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Boolean stream;
    private final Map<String, Object> extras;

    private GenerationParameters(Builder builder) {
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.presencePenalty = builder.presencePenalty;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.stream = builder.stream;
        this.extras = Collections.unmodifiableMap(new LinkedHashMap<>(builder.extras));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static GenerationParameters empty() {
        return builder().build();
    }

    public Double temperature() {
        return temperature;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    public Double topP() {
        return topP;
    }

    public Integer topK() {
        return topK;
    }

    public Double presencePenalty() {
        return presencePenalty;
    }

    public Double frequencyPenalty() {
        return frequencyPenalty;
    }

    public Boolean stream() {
        return stream;
    }

    public Map<String, Object> extras() {
        return extras;
    }

    public Map<String, Object> toOpenAiBodyFields() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }
        if (topP != null) {
            body.put("top_p", topP);
        }
        if (topK != null) {
            body.put("top_k", topK);
        }
        if (presencePenalty != null) {
            body.put("presence_penalty", presencePenalty);
        }
        if (frequencyPenalty != null) {
            body.put("frequency_penalty", frequencyPenalty);
        }
        if (stream != null) {
            body.put("stream", stream);
        }
        body.putAll(extras);
        return body;
    }

    public static final class Builder {
        private Double temperature;
        private Integer maxTokens;
        private Double topP;
        private Integer topK;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Boolean stream;
        private final Map<String, Object> extras = new LinkedHashMap<>();

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder extra(String key, Object value) {
            extras.put(Objects.requireNonNull(key, "key"), value);
            return this;
        }

        public GenerationParameters build() {
            return new GenerationParameters(this);
        }
    }
}
