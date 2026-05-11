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
- HR: profile, post/update/delete jobs, applicants, application status, LinkedIn import placeholder, subscriptions
- Admin: stats, users, job approval toggle, user delete

## Notes

- LinkedIn import validates LinkedIn job URLs and creates a review-ready placeholder job. Real scraping/API use should be added behind `LinkedInScraperService` once an approved data source is chosen.
- `EmailService` logs verification links by default. Swap it for SendGrid or Resend in production.
- Integration test skeleton is included but disabled until a PostgreSQL test database is configured, so production Flyway JSONB and full-text indexes are tested honestly.
