-- ============================================================
-- V7: Intelligence layer — dedup, normalisation, scoring,
-- technology extraction, keyword-driven ingestion.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------- Keyword strategy ----------
CREATE TABLE keyword_search_strategy (
    id                BIGSERIAL PRIMARY KEY,
    keyword           VARCHAR(255) NOT NULL UNIQUE,
    tier              SMALLINT NOT NULL CHECK (tier BETWEEN 1 AND 4),
    category          VARCHAR(60),
    weight            DECIMAL(4,2) NOT NULL DEFAULT 1.00,
    active            BOOLEAN NOT NULL DEFAULT true,
    last_run_at       TIMESTAMPTZ,
    total_runs        BIGINT NOT NULL DEFAULT 0,
    total_returned    BIGINT NOT NULL DEFAULT 0,
    total_inserted    BIGINT NOT NULL DEFAULT 0,
    total_duplicates  BIGINT NOT NULL DEFAULT 0,
    last_error        TEXT
);
CREATE INDEX idx_kw_active_tier ON keyword_search_strategy(active, tier, last_run_at NULLS FIRST);

-- ---------- Ingest run log ----------
CREATE TABLE ingest_run_log (
    id               BIGSERIAL PRIMARY KEY,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    source           VARCHAR(40)  NOT NULL,
    keyword          VARCHAR(255),
    fetched          INT NOT NULL DEFAULT 0,
    rejected_hard    INT NOT NULL DEFAULT 0,
    rejected_score   INT NOT NULL DEFAULT 0,
    duplicates_l1    INT NOT NULL DEFAULT 0,
    duplicates_l2    INT NOT NULL DEFAULT 0,
    duplicates_l3    INT NOT NULL DEFAULT 0,
    inserted         INT NOT NULL DEFAULT 0,
    error            TEXT
);
CREATE INDEX idx_ingest_log_started ON ingest_run_log(started_at DESC);
CREATE INDEX idx_ingest_log_source  ON ingest_run_log(source, started_at DESC);

-- ---------- Jobs: dedup + normalisation + filtering columns ----------
ALTER TABLE jobs
    ADD COLUMN external_job_id           VARCHAR(120),
    ADD COLUMN external_source           VARCHAR(40),
    ADD COLUMN dedup_hash                CHAR(64),
    ADD COLUMN last_seen_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN duplicate_source_count    INT NOT NULL DEFAULT 1,
    ADD COLUMN publisher                 VARCHAR(120),
    ADD COLUMN raw_title                 VARCHAR(300),
    ADD COLUMN normalized_title          VARCHAR(300),
    ADD COLUMN normalized_company_name   VARCHAR(255),
    ADD COLUMN city                      VARCHAR(120),
    ADD COLUMN country                   CHAR(2) DEFAULT 'AE',
    ADD COLUMN work_mode                 VARCHAR(20),
    ADD COLUMN seniority                 VARCHAR(20),
    ADD COLUMN relevance_score           INT NOT NULL DEFAULT 100,
    ADD COLUMN relevance_reasons         JSONB,
    ADD COLUMN technologies              JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN visa_sponsorship          BOOLEAN,
    ADD COLUMN easy_apply                BOOLEAN,
    ADD COLUMN relocation_support        BOOLEAN,
    ADD COLUMN has_java                  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_python                BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_javascript            BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_typescript            BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_csharp                BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_react                 BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_angular               BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_node                  BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_spring_boot           BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_selenium              BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_playwright            BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_aws                   BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_azure                 BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_kubernetes            BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_docker                BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN has_postgresql            BOOLEAN NOT NULL DEFAULT false;

-- Backfill existing rows so the new not-null columns are sane
UPDATE jobs SET
    raw_title        = title,
    normalized_title = title,
    normalized_company_name = company_name,
    city = COALESCE(NULLIF(location_uae, ''), 'United Arab Emirates')
WHERE normalized_title IS NULL;

-- ---------- Indexes ----------

-- L1: external ID uniqueness
CREATE UNIQUE INDEX uq_jobs_external_id_source
    ON jobs(external_source, external_job_id)
    WHERE external_job_id IS NOT NULL;

-- L2: hash lookup
CREATE INDEX idx_jobs_dedup_hash ON jobs(dedup_hash) WHERE dedup_hash IS NOT NULL;

-- L3: trigram for fuzzy
CREATE INDEX idx_jobs_normalized_title_trgm
    ON jobs USING gin (normalized_title gin_trgm_ops);

-- Hot-path filter columns
CREATE INDEX idx_jobs_city            ON jobs(city)            WHERE is_active = true;
CREATE INDEX idx_jobs_country         ON jobs(country)         WHERE is_active = true;
CREATE INDEX idx_jobs_work_mode       ON jobs(work_mode)       WHERE is_active = true;
CREATE INDEX idx_jobs_seniority       ON jobs(seniority)       WHERE is_active = true;
CREATE INDEX idx_jobs_relevance_score ON jobs(relevance_score) WHERE is_active = true;
CREATE INDEX idx_jobs_last_seen_at    ON jobs(last_seen_at DESC) WHERE is_active = true;

-- Technologies JSONB
CREATE INDEX idx_jobs_technologies_gin
    ON jobs USING gin (technologies jsonb_path_ops);

-- Per-tech partial indexes (only "true" rows live in the index)
CREATE INDEX idx_jobs_has_java        ON jobs(id) WHERE has_java        = true;
CREATE INDEX idx_jobs_has_python      ON jobs(id) WHERE has_python      = true;
CREATE INDEX idx_jobs_has_react       ON jobs(id) WHERE has_react       = true;
CREATE INDEX idx_jobs_has_typescript  ON jobs(id) WHERE has_typescript  = true;
CREATE INDEX idx_jobs_has_aws         ON jobs(id) WHERE has_aws         = true;
CREATE INDEX idx_jobs_has_azure       ON jobs(id) WHERE has_azure       = true;
CREATE INDEX idx_jobs_has_kubernetes  ON jobs(id) WHERE has_kubernetes  = true;
CREATE INDEX idx_jobs_has_selenium    ON jobs(id) WHERE has_selenium    = true;
CREATE INDEX idx_jobs_has_playwright  ON jobs(id) WHERE has_playwright  = true;
CREATE INDEX idx_jobs_has_docker      ON jobs(id) WHERE has_docker      = true;
CREATE INDEX idx_jobs_has_postgresql  ON jobs(id) WHERE has_postgresql  = true;

-- ---------- Seed keywords ----------

INSERT INTO keyword_search_strategy(keyword, tier, category, weight) VALUES
-- Tier 1: high-yield role searches
('Software Engineer Dubai',     1, 'role',       1.00),
('QA Engineer UAE',             1, 'role',       1.00),
('Automation Tester UAE',       1, 'role',       1.00),
('DevOps Engineer UAE',         1, 'role',       1.00),
('Backend Developer Dubai',     1, 'role',       1.00),
('Frontend Developer Dubai',    1, 'role',       1.00),
('Full Stack Developer UAE',    1, 'role',       1.00),
('Cloud Engineer Dubai',        1, 'role',       1.00),
('Data Engineer UAE',           1, 'role',       1.00),
('Cybersecurity Engineer UAE',  1, 'role',       1.00),
('Mobile Developer Dubai',      1, 'role',       0.95),
('Machine Learning Engineer UAE',1,'role',       0.95),
-- Tier 2: technology-focused
('Java Developer UAE',          2, 'technology', 0.85),
('Python Developer UAE',        2, 'technology', 0.85),
('Selenium UAE',                2, 'technology', 0.85),
('Playwright UAE',              2, 'technology', 0.85),
('Spring Boot Dubai',           2, 'technology', 0.85),
('AWS Engineer UAE',            2, 'technology', 0.85),
('Azure Engineer UAE',          2, 'technology', 0.85),
('Kubernetes UAE',              2, 'technology', 0.85),
('React Developer UAE',         2, 'technology', 0.80),
('Angular Developer UAE',       2, 'technology', 0.75),
('Node.js Developer UAE',       2, 'technology', 0.80),
('TypeScript Developer UAE',    2, 'technology', 0.75),
-- Tier 3: seniority modifiers
('Senior Software Engineer Dubai', 3, 'experience', 0.65),
('Lead QA Engineer UAE',           3, 'experience', 0.55),
('Junior Developer UAE',           3, 'experience', 0.55),
('Senior DevOps Dubai',            3, 'experience', 0.60),
('Principal Engineer UAE',         3, 'experience', 0.50),
('Engineering Manager UAE',        3, 'experience', 0.50),
-- Tier 4: location-only enrichment
('IT jobs Dubai',                  4, 'location',   0.30),
('Tech jobs Abu Dhabi',            4, 'location',   0.30),
('Software jobs Sharjah',          4, 'location',   0.25),
('Remote software engineer UAE',   4, 'location',   0.40);
