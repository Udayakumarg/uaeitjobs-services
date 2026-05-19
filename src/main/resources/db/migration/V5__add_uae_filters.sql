-- UAE-specific filter columns on jobs
-- visa_type:   free_visa | own_visa | visit_visa_accepted | employment_visa
-- emirate:     dubai | abu_dhabi | sharjah | ajman | fujairah | ras_al_khaimah | umm_al_quwain
-- immediate_joiner: true if recruiter wants someone who can start now
-- remote_uae:      true if work-from-home within UAE is acceptable

ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS visa_type        VARCHAR(40),
    ADD COLUMN IF NOT EXISTS emirate          VARCHAR(40),
    ADD COLUMN IF NOT EXISTS immediate_joiner BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS remote_uae       BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_jobs_visa_type        ON jobs (visa_type)        WHERE visa_type IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jobs_emirate          ON jobs (emirate)          WHERE emirate IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jobs_immediate_joiner ON jobs (immediate_joiner) WHERE immediate_joiner = true;
CREATE INDEX IF NOT EXISTS idx_jobs_remote_uae       ON jobs (remote_uae)       WHERE remote_uae = true;
