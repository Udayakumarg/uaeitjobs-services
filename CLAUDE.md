# UAEITJobs Backend — Claude Context

Spring Boot backend for UAEITJobs. Live at https://www.uaeitjobs.com (port 8081 in Docker).

## Read at session start
Full memory is in the **frontend repo** at `C:\Users\inbox\uaeitjobs\uaeitjobs-fe\.claude\memory\`:
1. `00_bootstrap/SESSION_BOOTSTRAP.md` — VPS access, commit style, key commands
2. `01_core/CURRENT_STATE.md` — all implemented endpoints and behaviour
3. `01_core/NON_NEGOTIABLES.md` — hard rules
4. `02_backend/BACKEND_OVERVIEW.md` — backend subsystem map
5. `01_core/DECISIONS.md` — why things are built the way they are

## Backend-specific quick facts

### Always compile before committing
```bash
mvn clean compile -q   # no output = success
```

### Key packages
```
com.uaeitjobs.controller   — HTTP endpoints (AdminController, HRController, JobController, AuthController)
com.uaeitjobs.service      — business logic
com.uaeitjobs.service.ingest — ingest pipeline, sources
com.uaeitjobs.service.ingest.pipeline — normalizers, dedup, scoring, tech extraction
com.uaeitjobs.dto          — request/response DTOs
com.uaeitjobs.entity       — JPA entities
com.uaeitjobs.repository   — Spring Data repositories
com.uaeitjobs.config       — security, JWT, rate limiting
```

### Critical behaviours
- Login attempt logging uses `@Transactional(REQUIRES_NEW)` — must not change
- Rate limit: 10 req/min, message: "Rate limit exceeded. Please try again shortly."
- `JobDTOTest.EXPECTED_FIELD_COUNT = 31` — update when adding fields to JobResponse
- `jobLocation` in JSON-LD handled as array AND object in `UrlJobScraperService`
- `inferUaeCity()` normalises hyphens: `text.toLowerCase().replace('-', ' ')`

### VPS / deploy
- SSH: `ssh -i ~/.ssh/new-vps-key root@82.25.110.205`
- Redeploy: `cd /opt/apps/uaeitjobs && docker compose pull uaeitjobs-backend && docker stop uaeitjobs-backend && docker rm uaeitjobs-backend && docker compose up -d --no-deps uaeitjobs-backend`

### ⚠ Action required
JSearch RapidAPI key was exposed in conversation history — rotate at RapidAPI dashboard.
