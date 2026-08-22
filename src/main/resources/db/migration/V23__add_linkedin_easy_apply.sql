-- LinkedIn's own apply-flow classification: true when LinkedIn lets the
-- candidate apply without leaving LinkedIn ("Easy Apply"), false when
-- LinkedIn redirects to the employer's own site. Null everywhere else
-- (non-LinkedIn jobs, and LinkedIn jobs ingested without a detail fetch).
ALTER TABLE jobs ADD COLUMN linkedin_easy_apply BOOLEAN;
