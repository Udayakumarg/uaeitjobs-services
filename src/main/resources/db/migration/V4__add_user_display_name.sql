-- Display name for user notifications and HR emails. Null remains allowed; services fall back to email prefix.
ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name VARCHAR(160);

UPDATE users
SET display_name = substring(email from 1 for position('@' in email) - 1)
WHERE display_name IS NULL
  AND position('@' in email) > 1;
