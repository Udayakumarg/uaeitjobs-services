INSERT INTO users (email, password_hash, user_type, phone, country, is_verified) VALUES
('seeker1@uaeitjobs.com', '$2a$10$t0CRaQuMIhSNhPw8/KOk3u5BjtKyhI6aQh72WtX2A1sqpHUPbWyLq', 'job_seeker', '+971500000001', 'UAE', true),
('seeker2@uaeitjobs.com', '$2a$10$t0CRaQuMIhSNhPw8/KOk3u5BjtKyhI6aQh72WtX2A1sqpHUPbWyLq', 'job_seeker', '+971500000002', 'UAE', true),
('hr1@uaeitjobs.com', '$2a$10$t0CRaQuMIhSNhPw8/KOk3u5BjtKyhI6aQh72WtX2A1sqpHUPbWyLq', 'hr', '+971500000003', 'UAE', true),
('hr2@uaeitjobs.com', '$2a$10$t0CRaQuMIhSNhPw8/KOk3u5BjtKyhI6aQh72WtX2A1sqpHUPbWyLq', 'hr', '+971500000004', 'UAE', true),
('admin@uaeitjobs.com', '$2a$10$t0CRaQuMIhSNhPw8/KOk3u5BjtKyhI6aQh72WtX2A1sqpHUPbWyLq', 'admin', '+971500000005', 'UAE', true);

INSERT INTO job_seeker_profiles (user_id, headline, summary, years_experience, visa_status, skills) VALUES
(1, 'Java backend engineer', 'Spring Boot and PostgreSQL specialist.', 5, 'resident', '["Java","Spring Boot","PostgreSQL"]'::jsonb),
(2, 'Frontend engineer', 'React and design systems developer.', 3, 'visit', '["React","TypeScript","CSS"]'::jsonb);

INSERT INTO hr_profiles (user_id, company_name, industry, subscription_tier) VALUES
(3, 'Emirates Cloud Labs', 'Cloud', 'starter'),
(4, 'Dubai Fintech Systems', 'Fintech', 'professional');

INSERT INTO hr_subscriptions (user_id, tier, featured_jobs_limit, monthly_job_limit, expires_at, is_active) VALUES
(3, 'starter', 2, 10, now() + interval '30 days', true),
(4, 'professional', 10, 50, now() + interval '30 days', true),
(5, 'admin', 999, 999, now() + interval '365 days', true);

INSERT INTO jobs (slug, title, company_name, description, requirements, salary_min, salary_max, job_type, experience_level, location_uae, skills, posted_by_id, is_featured)
SELECT
    lower(regexp_replace(title || '-' || n, '[^a-zA-Z0-9]+', '-', 'g')),
    title,
    company,
    'Build and operate high-quality technology products for UAE customers.',
    'Strong communication, ownership, and relevant production experience.',
    12000 + (n * 500),
    18000 + (n * 700),
    type,
    level,
    location,
    skills::jsonb,
    CASE WHEN n % 2 = 0 THEN 3 ELSE 4 END,
    n IN (1, 2, 3)
FROM (
    VALUES
    (1,'Senior Java Developer','Emirates Cloud Labs','full_time','senior','Dubai','["Java","Spring Boot","AWS"]'),
    (2,'React Frontend Engineer','Dubai Fintech Systems','full_time','mid','Dubai','["React","TypeScript","CSS"]'),
    (3,'DevOps Engineer','Emirates Cloud Labs','contract','senior','Abu Dhabi','["Kubernetes","Docker","AWS"]'),
    (4,'Data Engineer','Dubai Fintech Systems','full_time','mid','Sharjah','["Python","SQL","Airflow"]'),
    (5,'QA Automation Engineer','Emirates Cloud Labs','full_time','mid','Dubai','["Selenium","Java","API Testing"]'),
    (6,'Product Security Engineer','Dubai Fintech Systems','full_time','senior','Dubai','["Security","OWASP","Cloud"]'),
    (7,'Mobile Flutter Developer','Emirates Cloud Labs','full_time','mid','Ajman','["Flutter","Dart","Firebase"]'),
    (8,'Business Analyst IT','Dubai Fintech Systems','full_time','junior','Dubai','["Agile","SQL","Documentation"]'),
    (9,'Solutions Architect','Emirates Cloud Labs','full_time','lead','Abu Dhabi','["Architecture","AWS","Java"]'),
    (10,'PHP Laravel Developer','Dubai Fintech Systems','full_time','mid','Dubai','["PHP","Laravel","MySQL"]'),
    (11,'Network Engineer','Emirates Cloud Labs','contract','mid','Ras Al Khaimah','["Cisco","Routing","Firewalls"]'),
    (12,'UI UX Designer','Dubai Fintech Systems','full_time','mid','Dubai','["Figma","Design Systems","Research"]'),
    (13,'Machine Learning Engineer','Emirates Cloud Labs','full_time','senior','Abu Dhabi','["Python","ML","MLOps"]'),
    (14,'Salesforce Developer','Dubai Fintech Systems','full_time','mid','Dubai','["Salesforce","Apex","CRM"]'),
    (15,'IT Support Specialist','Emirates Cloud Labs','full_time','junior','Fujairah','["Windows","Networking","Helpdesk"]'),
    (16,'Scrum Master','Dubai Fintech Systems','full_time','senior','Dubai','["Scrum","Jira","Agile"]'),
    (17,'Database Administrator','Emirates Cloud Labs','full_time','senior','Sharjah','["PostgreSQL","Backup","Performance"]'),
    (18,'Cloud Support Engineer','Dubai Fintech Systems','full_time','junior','Dubai','["AWS","Linux","Support"]'),
    (19,'Technical Project Manager','Emirates Cloud Labs','full_time','lead','Abu Dhabi','["Project Management","Agile","Delivery"]'),
    (20,'Cybersecurity Analyst','Dubai Fintech Systems','full_time','mid','Dubai','["SIEM","Incident Response","Security"]')
) AS seed(n, title, company, type, level, location, skills);

INSERT INTO applications (job_id, user_id, cover_letter, status)
SELECT id, CASE WHEN id % 2 = 0 THEN 1 ELSE 2 END, 'I am interested in this role.', 
CASE (id % 5) WHEN 0 THEN 'hired' WHEN 1 THEN 'applied' WHEN 2 THEN 'reviewed' WHEN 3 THEN 'shortlisted' ELSE 'rejected' END
FROM jobs WHERE id <= 10;

INSERT INTO saved_jobs (user_id, job_id) VALUES
(1,1),(1,2),(1,3),(2,4),(2,5),(2,6);
