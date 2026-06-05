-- Bootstrap the hiring_companies directory from companies that have already
-- posted real jobs into our jobs table. Every distinct company_name in jobs
-- is, by construction, a UAE organisation hiring for IT — that's the scraper's
-- whole filter. So this is our highest-signal seed source.
--
-- For each unique company we derive:
--   slug          — kebab-cased company name
--   category      — display category mapped from most-common job_category
--   city          — display city mapped from most-common emirate
--   careers_url   — root of the newest job's apply_url, IF that apply_url
--                   points to a real company domain (not an aggregator like
--                   LinkedIn / Indeed / Bayt). Otherwise the inferred
--                   company_domain root.
--   website_url   — inferred company_domain root.
--   hiring_status — bucketed from job count.
--
-- Status = APPROVED, url_verified = true (derived from real postings).
-- ON CONFLICT (slug) DO NOTHING keeps the migration idempotent and lets the
-- next migration (V19, augmented well-known employers) merge in safely.

WITH company_stats AS (
    SELECT
        company_name,
        -- newest apply_url that's non-null
        (array_agg(apply_url ORDER BY created_at DESC NULLS LAST))
            FILTER (WHERE apply_url IS NOT NULL AND apply_url <> '')
            AS apply_urls,
        -- newest company_domain that's non-null
        (array_agg(company_domain ORDER BY created_at DESC NULLS LAST))
            FILTER (WHERE company_domain IS NOT NULL AND company_domain <> '')
            AS domains,
        -- most-common emirate / category for this company
        mode() WITHIN GROUP (ORDER BY emirate)     AS top_emirate,
        mode() WITHIN GROUP (ORDER BY job_category) AS top_category,
        COUNT(*) AS job_count
    FROM jobs
    WHERE company_name IS NOT NULL
      AND TRIM(company_name) <> ''
      AND LOWER(company_name) <> 'unknown'
      AND LOWER(company_name) <> 'confidential'
    GROUP BY company_name
)
INSERT INTO hiring_companies (
    name, slug, category, city, careers_url, website_url,
    hiring_status, status, url_verified, created_at, updated_at
)
SELECT
    company_name,
    -- slug: lowercased, alphanumerics + single dashes, no leading/trailing dash
    regexp_replace(
        regexp_replace(LOWER(company_name), '[^a-z0-9]+', '-', 'g'),
        '(^-+|-+$)', '', 'g'
    ) AS slug,
    CASE LOWER(COALESCE(top_category, ''))
        WHEN 'backend'   THEN 'Software & Product'
        WHEN 'frontend'  THEN 'Software & Product'
        WHEN 'fullstack' THEN 'Software & Product'
        WHEN 'mobile'    THEN 'Software & Product'
        WHEN 'qa'        THEN 'Software & Product'
        WHEN 'devops'    THEN 'Cloud & DevOps'
        WHEN 'cloud'     THEN 'Cloud & DevOps'
        WHEN 'data'      THEN 'Data & AI'
        WHEN 'ai'        THEN 'Data & AI'
        WHEN 'security'  THEN 'Cybersecurity'
        WHEN 'erp'       THEN 'Enterprise IT'
        WHEN 'support'   THEN 'Enterprise IT'
        ELSE NULL
    END AS category,
    CASE LOWER(COALESCE(top_emirate, ''))
        WHEN 'dubai'          THEN 'Dubai'
        WHEN 'abu_dhabi'      THEN 'Abu Dhabi'
        WHEN 'abu dhabi'      THEN 'Abu Dhabi'
        WHEN 'sharjah'        THEN 'Sharjah'
        WHEN 'ajman'          THEN 'Ajman'
        WHEN 'ras_al_khaimah' THEN 'Ras Al Khaimah'
        WHEN 'fujairah'       THEN 'Fujairah'
        WHEN 'umm_al_quwain'  THEN 'Umm Al Quwain'
        WHEN 'al_ain'         THEN 'Al Ain'
        ELSE 'UAE-wide'
    END AS city,
    COALESCE(
        CASE
            WHEN apply_urls[1] IS NULL THEN NULL
            WHEN apply_urls[1] ~* '(linkedin\.com|indeed\.com|naukrigulf\.com|bayt\.com|gulftalent\.com|glassdoor\.com|monster\.|adzuna\.)'
                THEN NULL
            ELSE substring(apply_urls[1] from '^(https?://[^/]+)')
        END,
        CASE WHEN domains[1] IS NOT NULL THEN 'https://' || domains[1] END
    ) AS careers_url,
    CASE WHEN domains[1] IS NOT NULL THEN 'https://' || domains[1] END AS website_url,
    CASE
        WHEN job_count >= 10 THEN 'ACTIVE_HIRING'
        WHEN job_count >= 3  THEN 'FREQUENT_HIRING'
        ELSE 'OCCASIONAL'
    END AS hiring_status,
    'APPROVED' AS status,
    true       AS url_verified,
    now(), now()
FROM company_stats
WHERE
    (
        (apply_urls[1] IS NOT NULL
            AND apply_urls[1] !~* '(linkedin\.com|indeed\.com|naukrigulf\.com|bayt\.com|gulftalent\.com|glassdoor\.com|monster\.|adzuna\.)')
        OR domains[1] IS NOT NULL
    )
ON CONFLICT (slug) DO NOTHING;
