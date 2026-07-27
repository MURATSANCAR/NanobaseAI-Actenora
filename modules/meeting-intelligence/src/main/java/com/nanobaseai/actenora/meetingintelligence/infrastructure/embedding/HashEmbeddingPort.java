package com.nanobaseai.actenora.meetingintelligence.infrastructure.embedding;

import com.nanobaseai.actenora.meetingintelligence.application.port.EmbeddingPort;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Deterministic local embedding for offline/dev when no embedding model is configured.
 * Not suitable as a semantic substitute for a trained embedding model in production retrieval quality,
 * but keeps the pipeline wired and tests stable.
 */
public final class HashEmbeddingPort implements EmbeddingPort {

    private final int dimensions;

    public HashEmbeddingPort(int dimensions) {
        if (dimensions < 8) {
            throw new IllegalArgumentException("dimensions must be >= 8");
        }
        this.dimensions = dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float[] embed(String text) {
        Objects.requireNonNull(text, "text");
        float[] vector = new float[dimensions];
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < dimensions; i++) {
            CRC32 crc = new CRC32();
            crc.update(bytes);
            crc.update(i);
            crc.update(i >>> 8);
            long v = crc.getValue();
            vector[i] = ((v % 10_000) / 5_000.0f) - 1.0f;
        }
        normalize(vector);
        return vector;
    }

    private static void normalize(float[] vector) {
        double sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        if (sum <= 0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
