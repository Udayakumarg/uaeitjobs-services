-- V7 declared jobs.country as CHAR(2) which Postgres exposes as bpchar
-- (JDBC Types.CHAR). Hibernate's String field maps to VARCHAR by default,
-- causing schema-validation to fail. Switch to VARCHAR(2) — same storage,
-- avoids the type-mismatch and survives further Hibernate quirks.
ALTER TABLE jobs ALTER COLUMN country TYPE VARCHAR(2);
