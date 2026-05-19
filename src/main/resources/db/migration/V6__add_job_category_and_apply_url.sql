-- Pivot to aggregator model: every job is a link to the source posting,
-- and a coarse job_category enables single-click filtering for testers,
-- frontend devs, devops folks, etc.

ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS job_category VARCHAR(40),
    ADD COLUMN IF NOT EXISTS apply_url    TEXT;

CREATE INDEX IF NOT EXISTS idx_jobs_job_category ON jobs (job_category) WHERE job_category IS NOT NULL;
