-- V13: Add company domain and logo URL to jobs.
--
-- company_domain  → root domain extracted from apply_url (e.g. "microsoft.com").
--                   Used to construct Clearbit logo URLs and for company-level grouping.
-- company_logo_url → resolved URL served to the frontend (currently Clearbit).
--                    May be NULL when no apply URL was present or the domain is a
--                    generic ATS platform (lever.co, greenhouse.io, etc.).
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS company_domain    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS company_logo_url  VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_jobs_company_domain ON jobs (company_domain);
