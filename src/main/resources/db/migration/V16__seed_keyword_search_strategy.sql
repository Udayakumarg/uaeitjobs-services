-- Seed the keyword rotation table for JSearch (UAE IT jobs via RapidAPI).
-- Tier 1 = high-volume core roles, run every pass.
-- Tier 2 = important but slightly narrower, run less often.
-- Tier 3 = specialist / niche, run rarely.
-- Tier 4 = emerging / enrichment only.

INSERT INTO keyword_search_strategy (keyword, tier, category, weight) VALUES
  -- Tier 1 — core UAE IT roles (picked 2 per cron pass)
  ('software engineer UAE',           1, 'role',       1.50),
  ('software developer Dubai',        1, 'role',       1.50),
  ('full stack developer UAE',        1, 'role',       1.40),
  ('backend developer Dubai',         1, 'role',       1.30),
  ('frontend developer UAE',          1, 'role',       1.20),
  ('Java developer UAE',              1, 'technology', 1.20),
  ('Python developer Dubai',          1, 'technology', 1.20),
  ('DevOps engineer UAE',             1, 'role',       1.20),
  ('cloud engineer Dubai',            1, 'role',       1.10),
  ('data engineer UAE',               1, 'role',       1.10),

  -- Tier 2 — important roles, 1 per cron pass
  ('mobile developer UAE',            2, 'role',       1.00),
  ('React developer Dubai',           2, 'technology', 1.00),
  ('Node.js developer UAE',           2, 'technology', 1.00),
  ('QA engineer UAE',                 2, 'role',       1.00),
  ('solution architect Dubai',        2, 'role',       1.10),
  ('IT manager UAE',                  2, 'role',       0.90),
  ('cybersecurity engineer Dubai',    2, 'role',       1.00),
  ('senior software engineer Dubai',  2, 'experience', 1.10),
  ('junior developer UAE',            2, 'experience', 0.90),

  -- Tier 3 — specialist (0 per cron pass by default, enable via JSEARCH_TIER3)
  ('AWS engineer UAE',                3, 'technology', 1.00),
  ('Azure developer Dubai',           3, 'technology', 1.00),
  ('Kubernetes Dubai',                3, 'technology', 0.90),
  ('machine learning engineer UAE',   3, 'role',       1.10),
  ('data scientist Dubai',            3, 'role',       1.10),
  ('blockchain developer UAE',        3, 'technology', 0.80),
  ('UI UX designer Dubai',            3, 'role',       0.80),
  ('SAP consultant UAE',              3, 'technology', 0.90),
  ('Android developer UAE',           3, 'technology', 0.90),
  ('iOS developer Dubai',             3, 'technology', 0.90)

ON CONFLICT (keyword) DO NOTHING;
