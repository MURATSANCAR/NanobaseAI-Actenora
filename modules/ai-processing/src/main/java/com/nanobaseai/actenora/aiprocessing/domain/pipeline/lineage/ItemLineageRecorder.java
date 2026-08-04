package com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-local lineage sink. Default disabled (no-op). Failures must never break extraction.
 */
public final class ItemLineageRecorder {

    private static final ThreadLocal<ItemLineageRecorder> CURRENT = new ThreadLocal<>();

    private final boolean enabled;
    private final List<ItemLineageRecord> records = new ArrayList<>();

    private ItemLineageRecorder(boolean enabled) {
        this.enabled = enabled;
    }

    public static ItemLineageRecorder disabled() {
        return new ItemLineageRecorder(false);
    }

    public static ItemLineageRecorder enabled() {
        return new ItemLineageRecorder(true);
    }

    public static void install(ItemLineageRecorder recorder) {
        CURRENT.set(Objects.requireNonNull(recorder, "recorder"));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static ItemLineageRecorder current() {
        ItemLineageRecorder r = CURRENT.get();
        return r == null ? disabled() : r;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void record(ItemLineageRecord record) {
        if (!enabled || record == null) {
            return;
        }
        try {
            if (record.operation() == LineageOperation.DROP && record.reasonCode() == null) {
                return;
            }
            records.add(record);
        } catch (RuntimeException ignored) {
            // Observability must never fail the pipeline.
        }
    }

    public List<ItemLineageRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public int size() {
        return records.size();
    }

    public static final String ARTIFACT_TYPE = "item-lineage";

    /** JSON array of safe maps — suitable for jsonb processing_artifact.payload_json. */
    public List<Map<String, Object>> toSafeMaps() {
        List<Map<String, Object>> out = new ArrayList<>(records.size());
        for (ItemLineageRecord r : records) {
            try {
                out.add(r.toSafeMap());
            } catch (RuntimeException ignored) {
                // skip corrupt row
            }
        }
        return out;
    }
}
