-- Hiring company directory: a curated + user-submitted list of UAE organisations
-- that hire for IT / technology roles. Public read-only listing with search +
-- filters, authenticated submission of new entries, admin moderation queue.
--
-- This is the data store behind /companies (public), /companies/submit (auth)
-- and /admin/companies (admin moderation).

CREATE TABLE hiring_companies (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    slug                  VARCHAR(280) NOT NULL UNIQUE,
    category              VARCHAR(100),
    city                  VARCHAR(100),
    careers_url           TEXT NOT NULL,
    website_url           TEXT,
    description           TEXT,
    tech_focus            TEXT,
    hiring_status         VARCHAR(40) NOT NULL DEFAULT 'OCCASIONAL'
        CHECK (hiring_status IN ('ACTIVE_HIRING', 'FREQUENT_HIRING', 'OCCASIONAL')),
    featured              BOOLEAN NOT NULL DEFAULT false,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- url_verified = a human (admin) or trusted source (scraper) has confirmed
    -- the careers_url actually goes to a careers page. Unverified rows still
    -- show on the public site but get a small "verifying" badge in the UI.
    url_verified          BOOLEAN NOT NULL DEFAULT false,
    submitted_by_user_id  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    rejection_reason      TEXT,
    approved_at           TIMESTAMPTZ,
    approved_by_user_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Public list page: default sort is featured-first, then alphabetical.
CREATE INDEX idx_hiring_companies_status_featured
    ON hiring_companies (status, featured DESC, name);

-- City + category filters on the public list page.
CREATE INDEX idx_hiring_companies_status_city
    ON hiring_companies (status, city);
CREATE INDEX idx_hiring_companies_status_category
    ON hiring_companies (status, category);

-- Trigram fuzzy search on company name (powers the search box).
-- pg_trgm was already enabled by V7 (intelligence layer); CREATE IF NOT EXISTS
-- is idempotent and harmless here.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_hiring_companies_name_trgm
    ON hiring_companies USING gin (name gin_trgm_ops);

-- Admin moderation queue: newest pending first.
CREATE INDEX idx_hiring_companies_pending
    ON hiring_companies (created_at DESC)
    WHERE status = 'PENDING';

-- Per-user submission rate-limit lookup.
CREATE INDEX idx_hiring_companies_submitter
    ON hiring_companies (submitted_by_user_id, created_at);
