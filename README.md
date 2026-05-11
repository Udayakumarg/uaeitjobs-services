# UAEITJOBS Backend

Spring Boot 3.5.14 backend for `uaeitjobs.com`, using Java 17, PostgreSQL, Spring Data JPA, Spring Security JWT auth, Flyway migrations, MapStruct, Lombok, and Swagger/OpenAPI.

## Run Locally

1. Create PostgreSQL database:

```bash
createdb uaeitjobs
```

2. Configure environment variables, or copy `.env.example`.

3. Start the app:

```bash
mvn spring-boot:run
```

Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

## Build

```bash
mvn clean package
```

## Test

PowerShell needs the Spring profile flag quoted:

```bash
mvn test "-Dspring.profiles.active=test"
```

The `test` profile uses an in-memory H2 database in PostgreSQL compatibility mode so integration tests run without Docker or a local PostgreSQL service. Production and normal local runs still use PostgreSQL with Flyway migrations.

Testing caveat: H2 does not perfectly emulate PostgreSQL JSONB and full-text search behavior. The enabled suite is safe for controller wiring, auth, service-level parsing, email fallback behavior, and rate limiting. It is not sufficient for JSONB queries, full-text search, or Flyway migration validation. Planned action: migrate integration tests to Testcontainers with real PostgreSQL; until then, run full database checks against PostgreSQL with `docker compose -f docker-compose.test.yml up -d` before changing JSONB queries or migrations.

For a local PostgreSQL test database:

```bash
docker compose -f docker-compose.test.yml up -d
DB_URL=jdbc:postgresql://localhost:5433/uaeitjobs_test DB_USERNAME=postgres DB_PASSWORD=test mvn test -Dspring.profiles.active=test
```

## Docker

```bash
docker build -t uaeitjobs-be .
docker run --env-file .env -p 8080:8080 uaeitjobs-be
```

## Seed Users

All seeded users use password:

```text
Password123!
```

Seed accounts:

- `seeker1@uaeitjobs.com`
- `seeker2@uaeitjobs.com`
- `hr1@uaeitjobs.com`
- `hr2@uaeitjobs.com`
- `admin@uaeitjobs.com`

## Implemented API Areas

- Public jobs: list, detail with view count, search, filters, skills, locations, stats
- Auth: register, login, refresh, logout, email verification token flow
- Job seeker: profile, CV upload, skills, applications, saved jobs
- HR: profile, post/update/delete jobs, applicants, application status, LinkedIn import, subscriptions
- Admin: stats, users, job approval toggle, user delete

## Notes

- LinkedIn import fetches public LinkedIn job pages with Jsoup and extracts title, company, description, requirements, salary hints, skills, job type, and experience level. LinkedIn may still block scraping for some pages; those failures are stored on the import record.
- `EmailService` sends through SendGrid when `SENDGRID_API_KEY` is configured. Without a key, local/dev runs log a no-op instead of failing registration.
- Auth login, register, and refresh are limited to 5 requests per minute per client IP with Bucket4j.
- Integration tests are enabled for auth, public jobs, and rate limiting under the `test` profile.
