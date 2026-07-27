-- Separate DRAFT_ORGANIZER vs FINAL_EXTERNAL for the same note version + recipient.
ALTER TABLE delivery.requests
    ADD COLUMN IF NOT EXISTS intent VARCHAR(64) NOT NULL DEFAULT 'FINAL_EXTERNAL';

UPDATE delivery.requests
SET intent = 'DRAFT_ORGANIZER'
WHERE COALESCE(policy_snapshot->>'requireApproval', 'true') = 'false';

ALTER TABLE delivery.requests
    DROP CONSTRAINT IF EXISTS uq_delivery_note_recipient;

ALTER TABLE delivery.requests
    ADD CONSTRAINT uq_delivery_note_recipient_intent
        UNIQUE (tenant_id, note_version_id, recipient_email, intent);

CREATE TABLE IF NOT EXISTS delivery.pdf_attachments (
    tenant_id       UUID NOT NULL,
    note_version_id UUID NOT NULL,
    document_id     UUID NOT NULL,
    storage_key     VARCHAR(1024) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, note_version_id)
);
