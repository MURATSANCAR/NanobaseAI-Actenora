-- Expand action due timestamps while keeping due_date / relative_date for compatibility.
ALTER TABLE meetingintelligence.action_items
    ADD COLUMN IF NOT EXISTS due_at TIMESTAMPTZ;
