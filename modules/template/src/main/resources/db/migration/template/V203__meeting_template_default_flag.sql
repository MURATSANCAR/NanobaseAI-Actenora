-- Tenant default meeting note template: new notes bind to its latest published version.
ALTER TABLE template.meeting_template
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one default template per tenant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_template_default_per_tenant
    ON template.meeting_template (tenant_id)
    WHERE is_default;
