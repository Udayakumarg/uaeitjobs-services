-- V12: guarantee unlimited TEXT for every long-form column.
--
-- Earlier migrations declared these as TEXT, but a couple of envs ended
-- up with VARCHAR(255) because the entity didn't pin columnDefinition.
-- This migration is idempotent — TYPE TEXT is a no-op when already TEXT.
ALTER TABLE jobs ALTER COLUMN description       TYPE TEXT;
ALTER TABLE jobs ALTER COLUMN requirements      TYPE TEXT;
ALTER TABLE jobs ALTER COLUMN description_html  TYPE TEXT;
