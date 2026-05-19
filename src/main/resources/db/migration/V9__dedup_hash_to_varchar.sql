-- Same Hibernate quirk as V8: CHAR(N) → bpchar → Hibernate refuses
-- to validate against a String field. Switch dedup_hash to VARCHAR(64).
ALTER TABLE jobs ALTER COLUMN dedup_hash TYPE VARCHAR(64);
