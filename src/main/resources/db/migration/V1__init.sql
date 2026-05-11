CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(32) NOT NULL CHECK (user_type IN ('job_seeker', 'hr', 'admin')),
    phone VARCHAR(40),
    country VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_verified BOOLEAN NOT NULL DEFAULT false,
    last_login TIMESTAMPTZ
);

CREATE TABLE job_seeker_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    cv_url TEXT,
    headline VARCHAR(255),
    summary TEXT,
    years_experience INT,
    visa_status VARCHAR(80),
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    experience JSONB NOT NULL DEFAULT '[]'::jsonb,
    education JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE TABLE hr_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    company_logo_url TEXT,
    website TEXT,
    industry VARCHAR(120),
    subscription_tier VARCHAR(40) NOT NULL DEFAULT 'free'
);

CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(280) NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    requirements TEXT,
    salary_min INT,
    salary_max INT,
    salary_currency VARCHAR(3) NOT NULL DEFAULT 'AED',
    job_type VARCHAR(60),
    experience_level VARCHAR(80),
    location_uae VARCHAR(120),
    skills JSONB NOT NULL DEFAULT '[]'::jsonb,
    linkedin_url TEXT,
    source VARCHAR(40) NOT NULL DEFAULT 'manual',
    posted_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    is_featured BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    view_count BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cover_letter TEXT,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(40) NOT NULL DEFAULT 'applied' CHECK (status IN ('applied','reviewed','shortlisted','rejected','hired')),
    UNIQUE(job_id, user_id)
);

CREATE TABLE saved_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, job_id)
);

CREATE TABLE linkedin_imports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    linkedin_job_url TEXT NOT NULL,
    imported_job_id BIGINT REFERENCES jobs(id) ON DELETE SET NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'pending',
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

CREATE TABLE hr_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tier VARCHAR(40) NOT NULL,
    featured_jobs_limit INT NOT NULL DEFAULT 0,
    featured_jobs_used INT NOT NULL DEFAULT 0,
    monthly_job_limit INT NOT NULL DEFAULT 1,
    jobs_posted INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
