-- V15: store the original posting date reported by the external source.
-- For ingested jobs this is populated from the API (Adzuna `created`,
-- JSearch `job_posted_at_datetime_utc`).  For HR-posted and LinkedIn-
-- imported jobs it defaults to NOW() (the moment of publication).
-- NULL for any legacy rows that pre-date this migration.
ALTER TABLE jobs
    ADD COLUMN posted_at timestamptz;
