-- existsByApplyUrl runs on every job create and every HR import-preview
-- call with no backing index — full sequential scan each time.
CREATE INDEX idx_jobs_apply_url ON jobs(apply_url);
