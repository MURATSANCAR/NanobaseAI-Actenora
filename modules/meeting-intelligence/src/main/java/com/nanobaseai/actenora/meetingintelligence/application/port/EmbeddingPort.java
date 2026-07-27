package com.nanobaseai.actenora.meetingintelligence.application.port;

/**
 * Produces dense embeddings for approved knowledge text.
 * Implementations must never embed raw transcripts.
 */
public interface EmbeddingPort {

    int dimensions();

    float[] embed(String text);

    static EmbeddingPort noop(int dimensions) {
        return new EmbeddingPort() {
            @Override
            public int dimensions() {
                return dimensions;
            }

            @Override
            public float[] embed(String text) {
                return new float[dimensions];
            }
        };
    }
}
