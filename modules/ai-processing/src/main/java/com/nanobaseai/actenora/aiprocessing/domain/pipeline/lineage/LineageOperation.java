package com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage;

/** Item-level lineage operation. */
public enum LineageOperation {
    CREATE,
    KEEP,
    UPDATE,
    MERGE,
    DROP,
    REJECT,
    FLAG,
    MAP
}
