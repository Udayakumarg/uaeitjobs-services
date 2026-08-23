# UAEITJobs Backend — Claude Context (auto-loaded)

Spring Boot backend · Live at https://www.uaeitjobs.com · Port 8081 in Docker

## Identity
- VPS: `root@82.25.110.205` key `~/.ssh/new-vps-key`
- DB: `uaeitjobs_db` on `qten-db` container
- Frontend + full memory: `C:\Users\inbox\uaeitjobs\uaeitjobs-fe\.claude\memory\`

## Hard rules — always apply

**Credentials**: Never ask user to paste passwords in chat. Never enter them via tools.

**Before every commit**: `mvn clean compile -q` — no output = success.

**Never `docker compose down`** — stops the DB. Redeploy sequence:
```bash
cd /opt/apps/uaeitjobs && docker compose pull uaeitjobs-backend && docker stop uaeitjobs-backend && docker rm uaeitjobs-backend && docker compose up -d --no-deps uaeitjobs-backend
```

**Git**: No force-push to main. End commits with:
`Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>`

**Responses**: Terse. No trailing summaries.

## Critical state (June 2026)
- `@Transactional(REQUIRES_NEW)` on login attempt logging — do not change propagation
- Rate limit: 10 req/min · error: "Rate limit exceeded. Please try again shortly."
- Anonymous users see full `applyUrl`/`linkedinUrl` on every job — the old sign-up-gated masking (`withMaskedApply()`, `JobDTOTest`) was removed 2026-08 (product decision: users benefit over forced signups). Do not reintroduce it.
- `jobLocation` in JSON-LD: handled as array AND object in `UrlJobScraperService`
- `inferUaeCity()` normalises hyphens: `text.toLowerCase().replace('-', ' ')`
- `LinkedInJobData` has `location` field — `HRController.importPreview()` sets `.locationUae(ld.getLocation())`
- ⚠ JSearch RapidAPI key needs rotation (was exposed in conversation history)

## Package map
```
com.uaeitjobs.controller          — AdminController, HRController, JobController, AuthController
com.uaeitjobs.service             — AdminService, JobService, HRService, EmailService
com.uaeitjobs.service.ingest      — JobIngestService, JobIngestPipeline, JSearchSource, *Source
com.uaeitjobs.service.ingest.pipeline — Normalizers, DedupResolver, RelevanceScorer, TechnologyExtractor
com.uaeitjobs.dto                 — AdminDTO, JobDTO, AuthDTO, ExternalIngestRequest, UrlImportDTO
com.uaeitjobs.entity              — Job, User, LoginAttempt, IngestRunLog, KeywordSearchStrategy
com.uaeitjobs.config              — SecurityConfig, RateLimitingInterceptor, JwtTokenProvider
```

## For complex tasks — read these
1. `C:\Users\inbox\uaeitjobs\uaeitjobs-fe\.claude\memory\01_core\CURRENT_STATE.md`
2. `C:\Users\inbox\uaeitjobs\uaeitjobs-fe\.claude\memory\02_backend\BACKEND_OVERVIEW.md`
3. `C:\Users\inbox\uaeitjobs\uaeitjobs-fe\.claude\memory\01_core\DECISIONS.md`
