-- Per-seeker AI provider settings for AI-drafted cover letters. The API key
-- is stored encrypted (AES-256-GCM, see EncryptionService) — never in
-- plaintext, never returned to the client after save.
ALTER TABLE job_seeker_profiles ADD COLUMN ai_provider VARCHAR(20);
ALTER TABLE job_seeker_profiles ADD COLUMN ai_api_key_encrypted TEXT;
