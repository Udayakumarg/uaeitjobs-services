-- Convert free-form profile fields from JSONB to TEXT.
-- All existing rows have the default value '[]' (empty array); convert those to ''.
-- Any non-empty JSONB value is preserved as its text representation.
ALTER TABLE job_seeker_profiles
  ALTER COLUMN experience TYPE text
    USING CASE WHEN experience::text = '[]' THEN '' ELSE experience::text END,
  ALTER COLUMN education TYPE text
    USING CASE WHEN education::text = '[]' THEN '' ELSE education::text END;

ALTER TABLE job_seeker_profiles
  ALTER COLUMN experience SET DEFAULT '',
  ALTER COLUMN education  SET DEFAULT '';
