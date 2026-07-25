-- FAZ 18: delivery orders bound to approval + approved note version.
CREATE TABLE IF NOT EXISTS delivery.orders (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    approval_id       UUID NOT NULL,
    note_version_id   UUID NOT NULL,
    channel           VARCHAR(64) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_delivery_orders_approval
    ON delivery.orders (tenant_id, approval_id);

COMMENT ON TABLE delivery.orders IS 'READY only when ApprovalGranted is bound (ADR-010)';
