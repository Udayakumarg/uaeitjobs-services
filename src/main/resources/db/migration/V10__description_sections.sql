-- Structured description sections so the frontend can render headings
-- and bullet lists instead of one giant blob. The existing `description`
-- column stays as the plain-text fallback used by SEO/JSON-LD.
ALTER TABLE jobs
    ADD COLUMN description_sections JSONB NOT NULL DEFAULT '[]'::jsonb;
