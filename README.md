# Bench Readiness — Spring Boot Microservices

AI-powered technical interview platform with bench manager sign-off, candidate dashboard, and evaluation engine.

---

## Interview Modes

The platform supports 5 interview modes with different question counts, difficulty levels, and time limits:

| Mode | Purpose | Questions | Duration | Difficulty | READY Threshold |
|------|---------|-----------|----------|------------|----------------|
| SCREENING | Initial filter — is the candidate worth interviewing? | 5 | 15 min | Easy | avg >= 3 |
| L1 | First technical round — fundamentals depth | 7 | 20 min | Easy-Medium | avg >= 3.5 |
| L2 | Second technical round — applied knowledge | 8 | 25 min | Medium | avg >= 4 |
| L3 | Senior/deep technical round | 10 | 30 min | Medium-Hard | avg >= 4 |
| L4 | Staff/principal level — leadership + depth | 10 | 30 min | Hard | avg >= 4.5 |

### Mode-Specific Question Themes

**SCREENING** focuses on basic concepts and role fit:
- Basic intro — what they've built, primary stack
- Core concept check — one fundamental topic
- Simple problem solving
- Communication — explain something technical simply
- Role fit — why this role, what they want to learn

**L1** covers fundamentals and team collaboration:
- Technical opener — recent project walkthrough
- Core skills — fundamentals depth
- Problem solving — basic algorithm or logic
- Implementation details — how they build things
- Testing & quality — their approach to correctness
- Learning & growth — how they stay current
- Team collaboration — working with others

**L2** emphasizes applied knowledge and real-world scenarios:
- System overview — architecture they've worked on
- Trade-offs & decisions — competing concerns
- Real-world scenarios — production challenges
- Data & consistency — how they handle state
- Performance & scale — optimization experience
- Debugging & troubleshooting — incident response
- Design patterns — when and why to use them
- Integration challenges — working with external systems

**L3** tests senior-level architecture and leadership:
- Architecture design — system they've architected
- Distributed systems — consistency, availability, partition tolerance
- Failure handling — cascading failures, circuit breakers
- Performance at scale — bottlenecks and optimization
- Data architecture — storage, caching, replication
- Monitoring & observability — how they instrument systems
- Security considerations — threat modeling, defense
- Technical leadership — influencing technical decisions
- System evolution — refactoring large systems
- Complex problem solving — ambiguous technical challenges

**L4** evaluates staff/principal level impact and strategy:
- System design at scale — design a distributed system
- Architecture trade-offs — CAP theorem, consistency models
- Failure handling — chaos engineering, resilience patterns
- Cross-team impact — how they influenced architecture decisions
- Ambiguity handling — vague requirement to concrete plan
- Technical strategy — long-term technical vision
- Organizational scaling — technical decisions across teams
- Innovation & research — exploring new technologies
- Mentorship & growth — developing other engineers
- Business impact — connecting technical decisions to outcomes

---

## Architecture

```
bench-readiness/
├── api-gateway         (8080) — Spring Cloud Gateway, JWT validation, routing
├── auth-service        (8081) — Login, registration, JWT issue, user management
├── interview-service   (8082) — Interview CRUD, engineer, JD, plan, rubric generation
├── ai-service          (8083) — Claude AI questions, two-pass assessment, rubric, manipulation detection
├── observer-service    (8084) — WebSocket STOMP events, email notifications
├── review-service      (8085) — Category scores, sign-off, benchmarking
└── compliance-service  (8086) — Audit log, retention policies
```

### Frontend
```
AiInterviewBot/   (3000) — Next.js 15, App Router, server actions
```

---

## Service Details

### api-gateway (8080)
- Spring Cloud Gateway with global JWT filter
- Extracts `X-User-Id`, `X-User-Role`, `X-User-Email` from JWT and forwards to downstream services
- Public paths: `/auth/login`, `/auth/logout`, `/auth/register`, `/actuator`
- CORS configured for `http://localhost:3000`

### auth-service (8081)
- **Staff login** — real credentials (email + password), role determined by stored account
- **Candidate registration** — `POST /auth/register` with name, email, password
- **Candidate login** — email as username, password validated against stored value
- **Staff creation** — `POST /auth/staff` (BENCH_MANAGER only) — creates INTERVIEWER, HR, COMPLIANCE, or BENCH_MANAGER accounts
- **Staff listing** — `GET /auth/staff` (BENCH_MANAGER only)
- **Staff deletion** — `DELETE /auth/staff/{id}` (BENCH_MANAGER only)
- **Roles** — `CANDIDATE`, `INTERVIEWER`, `HR`, `COMPLIANCE`, `BENCH_MANAGER`
- **Candidate search** — `GET /auth/candidates?search=` for manager interview setup
- **User lookup** — `GET /auth/users/{id}` for internal service-to-service calls
- Default admin: `admin@benchreadiness.com` / `Admin@123` (seeded on first startup)
- JWT signed with HS384, configurable expiry

### interview-service (8082)
- Interview CRUD with mode-specific slot plans (5-10 questions based on interview mode)
- Engineer upsert by email — links candidate registration to interview
- **Interview modes** — SCREENING (5q, 15min), L1 (7q, 20min), L2 (8q, 25min), L3 (10q, 30min), L4 (10q, 30min)
- **Rubric generation** — calls ai-service at creation time to generate JD-driven evaluation categories + candidate profile
- `GET /interviews/mine` — candidate sees only their own interviews (filtered by `X-User-Email`)
- `GET /interviews/summary` — manager list with candidate name, email, JD title, verdict, interview mode
- `PATCH /interviews/{id}/complete` — update status, transcript, verdict
- `POST /interviews/{id}/abandon` — candidate exits early or time expires, notifies bench manager via observer-service
- `resumeSummary` is required — used for candidate profile extraction and question calibration

### ai-service (8083)
- **Claude API** (`claude-haiku-4-5` for questions/rubric, `claude-sonnet-4-5` for assessment)
- **Rubric generation** (`POST /ai/generate-rubric`) — extracts 4–6 JD-specific categories + candidate profile (YOE, level, difficulty) at interview creation. Uses dedicated `rubric-max-tokens` to prevent JSON truncation.
- **Question generation** (`POST /ai/next-question`) — mode-specific themed questions with difficulty calibration (easy to hard based on interview mode)
- **Claude optimizations**:
  - First question caching (24h TTL) — saves ~800 tokens per interview
  - Vague answer detection — skips Claude for answers <15 words or containing "I don't know", "maybe", etc.
  - Slot-based model switching — haiku for slots 1-5, sonnet for slots 6-10
  - Compressed system prompts — reduced from ~200 to ~60 tokens
  - Transcript deduplication — removes similar consecutive answers
- **Mode-specific slot themes** — different question focus areas for SCREENING, L1, L2, L3, L4 modes
- **Manipulation detection** — 8 regex patterns detect prompt injection / score manipulation. Warn on first detection, terminate at 5th attempt
- **Two-pass assessment** (`POST /ai/assess`):
  - Pass 1 (haiku) — evidence extraction per category from transcript
  - Pass 2 (sonnet) — scoring with evidence, resume consistency, behavioral signals, confidence per category, interview quality, 7-day roadmap
  - Mode-specific verdict thresholds (SCREENING: 3.0, L1: 3.5, L2: 4.0, L3: 4.0, L4: 4.5 for READY)
  - Skips Claude for transcripts <50 words or <3 candidate turns
  - If `rubricJson` not provided — generates rubric on-the-fly from JD before scoring
- **Markdown fence stripping** — Claude responses wrapped in ` ```json ``` ` are automatically cleaned before parsing
- **Fallback** — heuristic scoring when Claude unavailable
- Spring Retry — 3 attempts with exponential backoff

### observer-service (8084)
- WebSocket STOMP — bench manager can observe live interview at `/topic/observer/{interviewId}`
- `POST /observer/inject` — inject follow-up question into live interview
- `POST /observer/flag` — flag a candidate answer
- **Email notifications** via Gmail SMTP:
  - Interview invite sent to candidate on creation
  - Bench manager alerted when candidate abandons or time expires
- Auth-service lookup for manager email on abandon notification

### review-service (8085)
- `GET /scores/{interviewId}` — category scores with rationale, evidence, gap, confidence
- `POST /scores` — save/replace scores (called by frontend after assessment)
- `GET /reviews/{interviewId}` — sign-off status `{ signedOff, finalVerdict, note, signedOffAt }`
- `POST /reviews/{interviewId}/sign-off` — BENCH_MANAGER only, upsertable (can update existing sign-off)
- On sign-off → calls interview-service to update status to `SIGNED_OFF` and store `finalVerdict`
- Apache HttpClient5 for PATCH support

### compliance-service (8086)
- Audit log for all significant events
- Retention policy management
- Read-only access for COMPLIANCE role

---

## Database

Single PostgreSQL instance, schema-per-service isolation.

| Schema | Service | Key Tables |
|---|---|---|
| `auth_svc` | auth-service | `users` |
| `interview_svc` | interview-service | `engineers`, `job_descriptions`, `interview_plans`, `interviews` |
| `observer_svc` | observer-service | `observer_events` |
| `review_svc` | review-service | `scores`, `sign_offs` |
| `compliance_svc` | compliance-service | `audit_logs` |

### Interview Status Flow
```
SCHEDULED → IN_PROGRESS → COMPLETED → REVIEW_PENDING → SIGNED_OFF
                       ↘ (abandon) → COMPLETED (verdict: WITHDRAWN)
```

### Readiness Verdicts
`READY` | `NEEDS_1_WEEK_PREP` | `NEEDS_RESKILLING` | `MISMATCH_WITH_JD` | `WITHDRAWN`

---

## Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 12+ on `localhost:3308`
- Node.js 18+ (frontend)
- Claude API key (`sk-ant-...`)
- Gmail account with App Password (for email notifications)

---

## Setup

### 1. Database

```sql
CREATE DATABASE bench_readiness;

\c bench_readiness

CREATE SCHEMA auth_svc;
CREATE SCHEMA interview_svc;
CREATE SCHEMA observer_svc;
CREATE SCHEMA review_svc;
CREATE SCHEMA compliance_svc;
```

Flyway runs migrations automatically on startup.

### 2. Environment Variables

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:3308/bench_readiness
DB_USER=postgres
DB_PASSWORD=<your-password>

# Auth
JWT_SECRET=dev-jwt-secret-change-in-production-min-32-chars

# AI (Claude)
CLAUDE_API_KEY=sk-ant-...
CLAUDE_MODEL=claude-haiku-4-5
CLAUDE_ASSESSMENT_MODEL=claude-sonnet-4-5

# Email (Gmail SMTP)
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password   # Gmail App Password, not real password

# Service URLs (defaults shown — only set if ports differ)
OBSERVER_SERVICE_URL=http://localhost:8084
AI_SERVICE_URL=http://localhost:8083
AUTH_SERVICE_URL=http://localhost:8081
INTERVIEW_SERVICE_URL=http://localhost:8082
INTERVIEW_BASE_URL=http://localhost:3000/interview
```

### 3. Build All Services

```bash
cd bench-readiness
mvn clean install
```

### 4. Run Services

```bash
# Terminal 1
cd api-gateway && mvn spring-boot:run

# Terminal 2
cd auth-service && mvn spring-boot:run

# Terminal 3
cd interview-service && mvn spring-boot:run

# Terminal 4
cd ai-service && mvn spring-boot:run

# Terminal 5
cd observer-service && mvn spring-boot:run

# Terminal 6
cd review-service && mvn spring-boot:run

# Terminal 7
cd compliance-service && mvn spring-boot:run
```

Or use the provided script:
```bash
start-all.bat
```

### 5. Run Frontend

```bash
cd C:\Users\Asus\FrontEndPractice\AiInterviewBot
npm install
npm run dev
```

Frontend runs at `http://localhost:3000`.

---

## API Reference

All requests go through the gateway at `http://localhost:8080`.

### Auth

```bash
# Staff login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@benchreadiness.com","password":"Admin@123"}'
# Response: {"ok":true,"token":"eyJ...","role":"BENCH_MANAGER","name":"Admin"}

# Candidate registration
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","email":"john@example.com","password":"secret123"}'

# Candidate login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john@example.com","password":"secret123","role":"CANDIDATE"}'

# Create staff account (BENCH_MANAGER only)
curl -X POST http://localhost:8080/auth/staff \
  -H "Authorization: Bearer <manager-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Smith","email":"jane@company.com","password":"Pass@123","role":"INTERVIEWER"}'

# List all staff (BENCH_MANAGER only)
curl http://localhost:8080/auth/staff \
  -H "Authorization: Bearer <manager-token>"

# Delete staff account (BENCH_MANAGER only)
curl -X DELETE http://localhost:8080/auth/staff/<id> \
  -H "Authorization: Bearer <manager-token>"

# Search registered candidates (manager use)
curl http://localhost:8080/auth/candidates?search=john \
  -H "Authorization: Bearer <token>"

# Get current user
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer <token>"
```

### Interviews

```bash
# Create interview (resumeSummary required, interviewMode defaults to SCREENING)
curl -X POST http://localhost:8080/interviews \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "engineerEmail": "john@example.com",
    "engineerName": "John Doe",
    "jdTitle": "Senior Backend Engineer",
    "jdText": "5+ years Java/Spring, Kafka, Kubernetes, PostgreSQL...",
    "focusAreas": "Kafka, system design, API design",
    "resumeSummary": "8 years Java/Spring, led Kafka migration, built payment platform",
    "interviewMode": "L3"
  }'
# On creation: rubric + candidate profile auto-generated by ai-service
# Invite email sent to engineerEmail automatically

# Get candidate's own interviews
curl http://localhost:8080/interviews/mine \
  -H "Authorization: Bearer <candidate-token>"

# Get all interviews (manager)
curl http://localhost:8080/interviews/summary \
  -H "Authorization: Bearer <manager-token>"

# Complete interview
curl -X PATCH http://localhost:8080/interviews/<id>/complete \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"status":"COMPLETED","transcriptJson":"...","proposedVerdict":"NEEDS_1_WEEK_PREP"}'

# Abandon interview (candidate not prepared or time expired)
curl -X POST http://localhost:8080/interviews/<id>/abandon \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"transcriptJson":"...","reason":"not_prepared"}'
# reason: "not_prepared" | "time_expired" | "ai_manipulation"
# Bench manager notified by email automatically
```

### AI

```bash
# Generate rubric (called automatically at interview creation)
curl -X POST http://localhost:8080/ai/generate-rubric \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "jdTitle": "Senior Backend Engineer",
    "jdText": "...",
    "resumeSummary": "...",
    "focusAreas": "..."
  }'

# Get next question (with interviewMode for proper slot themes and difficulty)
curl -X POST http://localhost:8080/ai/next-question \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "slot": 1,
    "lastAnswer": "",
    "jdTitle": "Senior Backend Engineer",
    "jdText": "...",
    "utterances": [],
    "manipulationCount": 0,
    "rubricJson": "...",
    "candidateProfileJson": "...",
    "interviewMode": "L3"
  }'
# Response: {"question":"...","manipulationDetected":false,"terminateInterview":false}

# Assess interview (two-pass with interviewMode for verdict thresholds)
curl -X POST http://localhost:8080/ai/assess \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "jdTitle": "Senior Backend Engineer",
    "jdText": "...",
    "resumeSummary": "...",
    "transcriptJson": "{\"utterances\":[...]}",
    "rubricJson": "...",
    "candidateProfileJson": "...",
    "interviewMode": "L3"
  }'
```

### Review

```bash
# Get scores for an interview
curl http://localhost:8080/scores/<interviewId> \
  -H "Authorization: Bearer <token>"

# Save scores (after assessment)
curl -X POST http://localhost:8080/scores \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "interviewId": "<id>",
    "scores": [
      {"dimension":"coreJava","value":4,"rationale":"...","evidence":"...","gap":"...","confidence":"high"}
    ]
  }'

# Get sign-off
curl http://localhost:8080/reviews/<interviewId> \
  -H "Authorization: Bearer <token>"
# Response: {"signedOff":true,"finalVerdict":"READY","note":"...","signedOffAt":"..."}

# Sign off (BENCH_MANAGER only, upsertable)
curl -X POST http://localhost:8080/reviews/<interviewId>/sign-off \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>","verdict":"READY","note":"Strong candidate"}'
```

### Observer

```bash
# Inject question into live interview
curl -X POST http://localhost:8080/observer/inject \
  -H "Authorization: Bearer <manager-token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>","question":"Can you elaborate on Kafka consumer groups?","mode":"INJECT"}'

# Flag a candidate answer
curl -X POST http://localhost:8080/observer/flag \
  -H "Authorization: Bearer <manager-token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>","note":"Candidate seemed to read from notes"}'

# Get events for an interview
curl http://localhost:8080/observer/events/<interviewId> \
  -H "Authorization: Bearer <token>"
```

---

## Assessment Output

The two-pass assessment returns:

```json
{
  "categoryScores": [
    {
      "dimension": "spring",
      "value": 3,
      "strengths": "[\"Built REST APIs\", \"Knows @Transactional basics\"]",
      "weaknesses": "[\"Cannot explain propagation\", \"No Spring Security knowledge\"]",
      "evidence": "Used @Transactional but couldn't explain REQUIRES_NEW",
      "gap": "Transaction propagation, Spring Security",
      "confidence": "medium"
    }
  ],
  "proposedVerdict": "NEEDS_1_WEEK_PREP",
  "summary": "Solid Java backend engineer with gaps in advanced Spring",
  "resumeConsistency": {
    "claimed": ["Kafka migration", "8 years Java"],
    "demonstrated": ["Java basics", "Spring Boot"],
    "notDemonstrated": ["Kafka migration"],
    "consistencyScore": 3,
    "flags": ["Claimed Kafka expertise but couldn't explain consumer group rebalancing"]
  },
  "behavioralSignals": {
    "ownershipLevel": "high",
    "learningAgility": "medium",
    "communicationStructure": "low",
    "confidenceCalibration": "high",
    "summary": "Strong ownership, honest about gaps, but answers lack structure"
  },
  "interviewQuality": {
    "coverageScore": 4,
    "categoriesCovered": ["coreJava", "spring", "microservices"],
    "categoriesMissed": ["advancedJava"],
    "note": "All major categories covered"
  },
  "candidateFeedback": {
    "overallSummary": "You have solid Java and Spring fundamentals...",
    "prosAndCons": [
      {
        "category": "Spring & Spring Boot",
        "pros": ["Built REST APIs confidently", "Good understanding of @Transactional basics"],
        "cons": ["Couldn't explain transaction propagation levels", "Spring Security internals not covered"]
      }
    ],
    "resumeConsistencyForCandidate": [
      { "claim": "Kafka migration experience", "demonstrated": false, "note": "Not discussed in interview" },
      { "claim": "8 years Java", "demonstrated": true, "note": "Demonstrated through multiple Java answers" }
    ],
    "roadmap": [
      {
        "day": "Day 1",
        "category": "Spring & Spring Boot",
        "gap": "Transaction propagation",
        "focus": "Spring @Transactional — REQUIRED, REQUIRES_NEW, NESTED propagation",
        "whyItMatters": "Asked in every Spring interview — you used it but couldn't explain it",
        "resource": "Spring Transaction Management docs",
        "resourceUrl": "https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction",
        "exercise": "Build two services, test REQUIRES_NEW rollback behavior",
        "estimatedHours": 2
      }
    ],
    "estimatedReadinessTimeline": "Ready in 5-7 days with focused prep"
  },
  "source": "claude-two-pass"
}
```

---

## Manipulation Detection

The AI question engine detects and handles prompt injection attempts:

| Attempt count | Action |
|---|---|
| 1st detection | Warning message returned as next question |
| 2nd–4th | Warning repeated |
| 5th+ | Interview terminated, status set to `COMPLETED` with verdict `WITHDRAWN`, bench manager notified |

Detected patterns include: score manipulation requests, topic restriction commands, prompt injection, identity override attempts.

---

## Interview Timer

- Mode-specific countdown: SCREENING (15min), L1 (20min), L2 (25min), L3 (30min), L4 (30min)
- Timer starts when first BOT question is received
- Timer displayed in interview UI, turns red under 5 minutes
- On expiry: AI sends closing message → interview auto-submitted → bench manager notified with reason `time_expired`

---

## Email Notifications

| Event | Recipient | Content |
|---|---|---|
| Interview created | Candidate | Interview link + login instructions |
| Candidate abandons | Bench Manager | Interview ID + review link + reason |
| Time expired | Bench Manager | Interview ID + review link + reason |
| Daily digest (7 PM) | ADMIN + BENCH_MANAGER | Today's interviews table with status and verdict |

---

## Roles & Permissions

| Role | Who they are | Can do |
|---|---|---|
| `CANDIDATE` | Job candidate | Take interview, view own dashboard, view own feedback |
| `BENCH_MANAGER` | Hiring manager | Everything — create interviews, sign off, observe live, inject questions, manage staff |
| `INTERVIEWER` | Technical reviewer | View interviews, view scores, view transcripts, observe live, inject questions |
| `HR` | HR/Talent team | View interview results and verdicts only |
| `COMPLIANCE` | Audit/legal team | Read-only audit logs only |
| `ADMIN` | Platform monitor | View everything read-only, receive daily digest email |

---

## Local LLM Support

The AI client can be switched to a local Ollama instance by:

1. Changing `app.claude.*` → `app.ollama.*` in `ai-service/application.yml`
2. Rewriting the `chat()` method in `OpenAiClient.java` to use Ollama's `/api/chat` endpoint
3. No other service changes required

See `MIGRATION.md` for the full local LLM migration guide including model recommendations and impact analysis per feature.

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Schema-per-service | Clean isolation, easy to split to separate DBs later |
| Flyway migrations | Versioned, repeatable, no manual SQL |
| JWT forwarded as headers | Services don't need to validate JWT — gateway does it |
| Fire-and-forget notifications | Email/observer failures never roll back interview creation |
| Two-pass assessment | Evidence extraction (cheap) + scoring (accurate) = better results at lower cost |
| Rubric at creation time | JD-specific categories generated once, reused for questions and assessment |
| Resume required | Enables candidate profiling, question difficulty calibration, resume consistency check |
| Upsertable sign-off | Bench manager can correct a verdict without creating a new record |

---

## Project Structure

```
bench-readiness/
├── api-gateway/
│   └── JwtAuthFilter.java          — JWT validation + header injection
├── auth-service/
│   ├── AuthController.java         — login, register, candidates, user lookup
│   ├── JwtService.java             — token generation
│   └── db/migration/               — V1 init, V2 CANDIDATE role, V3 password
├── interview-service/
│   ├── InterviewController.java    — CRUD, mine, summary, complete, abandon
│   ├── InterviewService.java       — business logic, mode-specific slot plans, rubric call, notifications
│   ├── InterviewMode.java          — enum for SCREENING, L1, L2, L3, L4 modes
│   └── db/migration/               — V1 init, V2 email/name, V3 WITHDRAWN, V4 rubric, V5 interview_mode
├── ai-service/
│   ├── AiController.java           — next-question, assess, generate-rubric
│   ├── QuestionService.java        — mode-specific slot themes, manipulation detection, difficulty calibration, caching, vague answer detection
│   ├── QuestionCacheService.java   — first question caching with 24h TTL
│   ├── AssessmentService.java      — two-pass, evidence, scoring, mode-specific verdict thresholds, roadmap, transcript deduplication
│   ├── RubricService.java          — JD category + candidate profile extraction
│   ├── OpenAiClient.java           — Claude API client with slot-based model switching
│   └── config/CacheCleanupScheduler.java — hourly cache cleanup
├── observer-service/
│   ├── ObserverController.java     — inject, flag, notify endpoints
│   └── EmailService.java           — invite + abandon notifications
├── review-service/
│   ├── ReviewController.java       — scores, sign-off
│   ├── ReviewService.java          — upsert sign-off, update interview status
│   └── db/migration/               — V1 init, V2 WITHDRAWN, V3 gap/confidence
└── compliance-service/
    └── AuditLogController.java     — audit log CRUD
```

---

## Frontend Structure

```
AiInterviewBot/src/app/
├── login/                  — Staff + candidate login tabs
├── register/               — Candidate self-registration
├── candidate/
│   ├── dashboard/          — Upcoming + past interviews
│   └── feedback/[id]/      — Category scores, roadmap, manager review
├── interview/[id]/         — Live interview, timer, abandon button
├── admin/
│   ├── setup/              — Create interview with candidate search
│   ├── review/             — Interview list with status/verdict badges
│   └── interviews/[id]/review/ — Full review: scores, consistency, signals, sign-off
└── observer/               — Live observer view with WebSocket
```
