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
├── eureka-server       (6009) — Netflix Eureka service registry and discovery
├── api-gateway         (6002) — Spring Cloud Gateway, JWT validation, Eureka-based routing
├── auth-service        (6004) — Login, registration, JWT issue, user management
├── interview-service   (6006) — Interview CRUD, engineer, JD, plan, rubric generation
├── ai-service          (6003) — Claude AI questions, two-pass assessment, rubric, manipulation detection
├── observer-service    (6007) — WebSocket STOMP events, email notifications
├── review-service      (6008) — Category scores, sign-off, benchmarking
└── compliance-service  (6005) — Audit log, token tracking, retention policies
```

### Frontend
```
AiInterviewBot/   (6001) — Next.js 15, App Router, server actions
```

### Service Discovery
- All services register with **Eureka Server** on startup
- Services discover each other dynamically via Eureka registry
- **Feign clients** provide declarative, type-safe inter-service communication
- Built-in client-side load balancing via Spring Cloud LoadBalancer
- No hardcoded service URLs — fully dynamic service discovery

---

## Service Details

### eureka-server (6009)
- **Netflix Eureka** service registry and discovery server
- Central registry for all microservices
- Health monitoring and service instance tracking
- Dashboard UI at `http://localhost:6009`
- Self-preservation mode disabled for development
- All services register on startup and send heartbeats every 10 seconds

### api-gateway (6002)
- Spring Cloud Gateway with global JWT filter
- **Eureka-based dynamic routing** using `lb://SERVICE-NAME` URIs
- **Dual path support**: Accepts both `/path/**` and `/api/path/**` (strips `/api` prefix)
- Extracts `X-User-Id`, `X-User-Role`, `X-User-Email` from JWT and forwards to downstream services
- Public paths: `/auth/login`, `/auth/logout`, `/auth/register`, `/actuator`
- CORS configured for `http://localhost:6001`
- Client-side load balancing for service instances

### auth-service (6004)
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

### interview-service (6006)
- Interview CRUD with mode-specific slot plans (5-10 questions based on interview mode)
- Engineer upsert by email — links candidate registration to interview
- **Interview modes** — SCREENING (5q, 15min), L1 (7q, 20min), L2 (8q, 25min), L3 (10q, 30min), L4 (10q, 30min)
- **Duration customization** — override mode defaults with custom duration in interview creation
- **Token limit enforcement** — checks daily usage before allowing new interviews
- **Real-time analytics** — interview status counts, success rates, mode distribution
- **Candidate performance analytics** — performance by verdict/mode, top candidates, skill gap analysis, average scores by dimension
- **Rubric generation** — calls ai-service at creation time to generate JD-driven evaluation categories + candidate profile
- `GET /interviews/mine` — candidate sees only their own interviews (filtered by `X-User-Email`)
- `GET /interviews/summary` — manager list with candidate name, email, JD title, verdict, interview mode
- `GET /analytics/realtime` — real-time dashboard statistics by role
- `GET /analytics/modes` — interview mode distribution analytics
- `PATCH /interviews/{id}/complete` — update status, transcript, verdict
- `POST /interviews/{id}/abandon` — candidate exits early or time expires, notifies bench manager via observer-service
- `resumeSummary` is required — used for candidate profile extraction and question calibration
- **Feign clients**: AiServiceClient, ObserverServiceClient, ReviewServiceClient, ComplianceServiceClient, AuthServiceClient
- Registers with Eureka as `INTERVIEW-SERVICE`

### ai-service (6003)
- **Claude API** (`claude-haiku-4-5` for questions/rubric, `claude-sonnet-4-5` for assessment)
- **Token limits** — 4000 tokens for assessment (prevents JSON truncation), 1000 for rubric, 300 for questions
- **Rubric generation** (`POST /ai/generate-rubric`) — extracts 4–6 JD-specific categories + candidate profile (YOE, level, difficulty) at interview creation. Uses dedicated `rubric-max-tokens` to prevent JSON truncation.
- **Question generation** (`POST /ai/next-question`) — mode-specific themed questions with difficulty calibration (easy to hard based on interview mode)
- **Skip/Next detection** — recognizes "next question", "skip", "pass", "move on" to allow candidates to skip questions they're not prepared for
- **Claude optimizations**:
  - First question caching (24h TTL) — saves ~800 tokens per interview
  - Vague answer detection — skips Claude for answers <15 words or containing "I don't know", "maybe", etc.
  - Slot-based model switching — haiku for slots 1-5, sonnet for slots 6-10
  - Compressed system prompts — reduced from ~200 to ~60 tokens
  - Transcript deduplication — removes similar consecutive answers
- **Mode-specific slot themes** — different question focus areas for SCREENING, L1, L2, L3, L4 modes
- **Manipulation detection** — 8 regex patterns detect prompt injection / score manipulation. Warn on first detection, terminate at 5th attempt
- **Two-pass assessment** (`POST /ai/assess`):
  - Pass 1 (sonnet) — evidence extraction per category from transcript
  - Pass 2 (sonnet) — scoring with evidence, resume consistency, behavioral signals, confidence per category, interview quality, 7-day roadmap
  - Mode-specific verdict thresholds (SCREENING: 3.0, L1: 3.5, L2: 4.0, L3: 4.0, L4: 4.5 for READY)
  - Skips Claude for transcripts <50 words or <3 candidate turns
  - If `rubricJson` not provided — generates rubric on-the-fly from JD before scoring
  - Automatic assessment response storage in compliance-service after successful completion
  - Recovery mechanism for truncated JSON responses with shorter prompts
- **Token tracking** — uses Feign client to call compliance-service for token tracking
- **Markdown fence stripping** — Claude responses wrapped in ` ```json ``` ` are automatically cleaned before parsing
- **Fallback** — heuristic scoring when Claude unavailable
- Spring Retry — 3 attempts with exponential backoff
- **Feign client**: ComplianceServiceClient
- Registers with Eureka as `AI-SERVICE`

### observer-service (6007)
- WebSocket STOMP — bench manager can observe live interview at `/topic/observer/{interviewId}`
- `POST /observer/inject` — inject follow-up question into live interview
- `POST /observer/flag` — flag a candidate answer
- **Email notifications** via Gmail SMTP:
  - Interview invite sent to candidate on creation
  - Bench manager alerted when candidate abandons or time expires
- Auth-service lookup for manager email on abandon notification
- **Feign clients**: AuthServiceClient, InterviewServiceClient
- Registers with Eureka as `OBSERVER-SERVICE`

### review-service (6008)
- `GET /scores/{interviewId}` — category scores with rationale, evidence, gap, confidence
- `POST /scores` — save/replace scores (called by frontend after assessment)
- `GET /reviews/{interviewId}` — sign-off status `{ signedOff, finalVerdict, note, signedOffAt }`
- `POST /reviews/{interviewId}/sign-off` — BENCH_MANAGER only, upsertable (can update existing sign-off)
- On sign-off → calls interview-service to update status to `SIGNED_OFF` and store `finalVerdict`
- **Feign client**: InterviewServiceClient
- Registers with Eureka as `REVIEW-SERVICE`

### compliance-service (6005)
- **Token tracking** — daily usage monitoring per interview with cost estimation
- **Assessment response storage** — stores complete Claude assessment results per interview
- **Per-interview token summaries** — aggregated token usage and cost breakdown by operation type
- **Daily token limits** — configurable usage limits with hard blocks when exceeded
- **Usage analytics** — daily/weekly token consumption reports
- `POST /tokens/track` — record token usage per AI operation (called via gateway)
- `GET /tokens/check-limit` — verify if user can create new interviews
- `GET /tokens/analytics/daily` — daily usage statistics and cost breakdown
- `POST /tokens/limits` — update daily token limits (admin only)
- `POST /tokens/assessment-response` — store assessment JSON response with token count
- `GET /tokens/assessment-response/{interviewId}` — retrieve stored assessment for interview
- `GET /tokens/interview-summary/{interviewId}` — get per-interview token usage summary
- `POST /tokens/finalize-interview` — aggregate and finalize token usage for completed interview
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
| `compliance_svc` | compliance-service | `audit_logs`, `token_usage`, `assessment_responses`, `interview_token_summary` |

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
# Service Discovery
EUREKA_SERVER_URL=http://localhost:6009/eureka/

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
CLAUDE_ASSESSMENT_MAX_TOKENS=4000
CLAUDE_RUBRIC_MAX_TOKENS=1000
CLAUDE_QUESTION_MAX_TOKENS=300

# Email (Gmail SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Frontend URL
INTERVIEW_BASE_URL=http://localhost:6001/interview
FRONTEND_URL=http://localhost:6001

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:6001
```

### 3. Build All Services

```bash
cd bench-readiness
mvn clean install
```

### 4. Run Services

Services must start in dependency order:

```bash
# Terminal 1 - Service Registry (MUST START FIRST)
cd eureka-server && mvn spring-boot:run

# Terminal 2 - No dependencies
cd compliance-service && mvn spring-boot:run

# Terminal 3 - No dependencies
cd auth-service && mvn spring-boot:run

# Terminal 4 - Depends on auth-service
cd interview-service && mvn spring-boot:run

# Terminal 5 - Depends on compliance-service
cd ai-service && mvn spring-boot:run

# Terminal 6 - Depends on auth-service
cd observer-service && mvn spring-boot:run

# Terminal 7 - Depends on interview-service
cd review-service && mvn spring-boot:run

# Terminal 8 - Depends on all services
cd api-gateway && mvn spring-boot:run
```

Or use the provided script (handles dependencies automatically):
```bash
start-all.bat
```

**Eureka Dashboard**: http://localhost:6009

### 5. Run Frontend

```bash
cd C:\Users\Asus\FrontEndPractice\AiInterviewBot
npm install
npm run dev
```

Frontend runs at `http://localhost:6001`.

---

## API Reference

All requests go through the gateway at `http://localhost:6002`.

### Auth

```bash
# Staff login
curl -X POST http://localhost:6002/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@benchreadiness.com","password":"Admin@123"}'
# Response: {"ok":true,"token":"eyJ...","role":"BENCH_MANAGER","name":"Admin"}

# Candidate registration
curl -X POST http://localhost:6002/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","email":"john@example.com","password":"secret123"}'

# Candidate login
curl -X POST http://localhost:6002/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john@example.com","password":"secret123","role":"CANDIDATE"}'

# Create staff account (BENCH_MANAGER only)
curl -X POST http://localhost:6002/auth/staff \
  -H "Authorization: Bearer <manager-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Smith","email":"jane@company.com","password":"Pass@123","role":"INTERVIEWER"}'

# List all staff (BENCH_MANAGER only)
curl http://localhost:6002/auth/staff \
  -H "Authorization: Bearer <manager-token>"

# Delete staff account (BENCH_MANAGER only)
curl -X DELETE http://localhost:6002/auth/staff/<id> \
  -H "Authorization: Bearer <manager-token>"

# Search registered candidates (manager use)
curl http://localhost:6002/auth/candidates?search=john \
  -H "Authorization: Bearer <token>"

# Get current user
curl http://localhost:6002/auth/me \
  -H "Authorization: Bearer <token>"
```

### Interviews

```bash
# Create interview (resumeSummary required, interviewMode defaults to SCREENING, customDurationMinutes optional)
curl -X POST http://localhost:6002/interviews \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "engineerEmail": "john@example.com",
    "engineerName": "John Doe",
    "jdTitle": "Senior Backend Engineer",
    "jdText": "5+ years Java/Spring, Kafka, Kubernetes, PostgreSQL...",
    "focusAreas": "Kafka, system design, API design",
    "resumeSummary": "8 years Java/Spring, led Kafka migration, built payment platform",
    "interviewMode": "L3",
    "customDurationMinutes": 45
  }'
# On creation: rubric + candidate profile auto-generated by ai-service
# Invite email sent to engineerEmail automatically

# Get candidate's own interviews
curl http://localhost:6002/interviews/mine \
  -H "Authorization: Bearer <candidate-token>"

# Get all interviews (manager)
curl http://localhost:6002/interviews/summary \
  -H "Authorization: Bearer <manager-token>"

# Complete interview
curl -X PATCH http://localhost:6002/interviews/<id>/complete \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"status":"COMPLETED","transcriptJson":"...","proposedVerdict":"NEEDS_1_WEEK_PREP"}'

# Abandon interview (candidate not prepared or time expired)
curl -X POST http://localhost:6002/interviews/<id>/abandon \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"transcriptJson":"...","reason":"not_prepared"}'
# reason: "not_prepared" | "time_expired" | "ai_manipulation"
# Bench manager notified by email automatically
```

### AI

```bash
# Generate rubric (called automatically at interview creation)
curl -X POST http://localhost:6002/ai/generate-rubric \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "jdTitle": "Senior Backend Engineer",
    "jdText": "...",
    "resumeSummary": "...",
    "focusAreas": "..."
  }'

# Get next question (with interviewMode for proper slot themes and difficulty)
curl -X POST http://localhost:6002/ai/next-question \
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
curl -X POST http://localhost:6002/ai/assess \
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
curl http://localhost:6002/scores/<interviewId> \
  -H "Authorization: Bearer <token>"

# Save scores (after assessment)
curl -X POST http://localhost:6002/scores \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "interviewId": "<id>",
    "scores": [
      {"dimension":"coreJava","value":4,"rationale":"...","evidence":"...","gap":"...","confidence":"high"}
    ]
  }'

# Get sign-off
curl http://localhost:6002/reviews/<interviewId> \
  -H "Authorization: Bearer <token>"
# Response: {"signedOff":true,"finalVerdict":"READY","note":"...","signedOffAt":"..."}

# Sign off (BENCH_MANAGER only, upsertable)
curl -X POST http://localhost:6002/reviews/<interviewId>/sign-off \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>","verdict":"READY","note":"Strong candidate"}'
```

### Observer

```bash
# Inject question into live interview
curl -X POST http://localhost:6002/observer/inject \
  -H "Authorization: Bearer <manager-token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>","question":"Can you elaborate on Kafka consumer groups?","mode":"INJECT"}'

# Flag a candidate answer
curl -X POST http://localhost:6002/observer/flag \
  -H "Authorization: Bearer <manager-token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>","note":"Candidate seemed to read from notes"}'

# Get events for an interview
curl http://localhost:6002/observer/events/<interviewId> \
  -H "Authorization: Bearer <token>"
```

### Analytics

```bash
# Get real-time interview analytics
curl http://localhost:6002/analytics/realtime \
  -H "Authorization: Bearer <token>"
# Response: {"statusCounts":{"scheduled":5,"inProgress":2,"completed":10,"signedOff":8},"timePeriods":{"today":3,"thisWeek":12},"successMetrics":{"readyCount":6,"successRate":75.0}}

# Get interview mode distribution
curl http://localhost:6002/analytics/modes \
  -H "Authorization: Bearer <token>"
# Response: {"modeDistribution":{"SCREENING":5,"L1":3,"L2":4,"L3":2},"totalInterviews":14}

# Get candidate performance analytics
curl http://localhost:6002/analytics/candidates \
  -H "Authorization: Bearer <token>"
# Response: {"performanceByVerdict":{"READY":6,"NEEDS_1_WEEK_PREP":4},"performanceByMode":{"L1":{"totalCandidates":5,"readyCandidates":3,"successRate":60.0}},"topCandidates":[{"candidateName":"John Doe","averageScore":4.2,"verdict":"READY"}],"commonWeaknesses":[{"skill":"spring","candidateCount":8,"percentage":40.0}],"averageScoresBySkill":{"java":3.8,"spring":3.2}}
```

### Token Management

```bash
# Check daily token limit status
curl http://localhost:6002/tokens/check-limit \
  -H "Authorization: Bearer <token>"
# Response: {"usage":15000,"limit":100000,"canProceed":true,"nearLimit":false,"remainingTokens":85000}

# Get daily token analytics
curl http://localhost:6002/tokens/analytics/daily \
  -H "Authorization: Bearer <token>"
# Response: {"totalTokens":15000,"totalCost":0.45,"operationBreakdown":{"question":45,"assessment":12,"rubric":8}}

# Update token limits (admin only)
curl -X POST http://localhost:6002/tokens/limits \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"dailyLimit":150000,"warningThreshold":120000}'

# Store assessment response (called automatically by ai-service)
curl -X POST http://localhost:6002/tokens/assessment-response \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>","assessmentJson":"{...}","tokensUsed":1250,"assessmentSource":"claude-two-pass"}'

# Get stored assessment response
curl http://localhost:6002/tokens/assessment-response/<interviewId> \
  -H "Authorization: Bearer <token>"
# Response: {"interviewId":"...","assessmentJson":"{...}","tokensUsed":1250,"assessmentSource":"claude-two-pass","createdAt":"..."}

# Get per-interview token summary
curl http://localhost:6002/tokens/interview-summary/<interviewId> \
  -H "Authorization: Bearer <token>"
# Response: {"interviewId":"...","totalTokens":2500,"totalCostUsd":0.75,"questionTokens":800,"assessmentTokens":1250,"rubricTokens":450}

# Finalize interview tokens (called automatically after assessment)
curl -X POST http://localhost:6002/tokens/finalize-interview \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"interviewId":"<id>"}'
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
- Custom duration override available during interview creation
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
| Eureka service discovery | Dynamic service discovery, no hardcoded URLs, built-in load balancing |
| Feign clients | Declarative, type-safe inter-service communication with retry and circuit breaker support |

---

## Project Structure

```
bench-readiness/
├── eureka-server/
│   ├── EurekaServerApplication.java  — @EnableEurekaServer
│   └── application.yml               — Eureka server configuration
├── api-gateway/
│   ├── JwtAuthFilter.java            — JWT validation + header injection
│   └── application.yml               — Eureka-based routing (lb://SERVICE-NAME)
├── auth-service/
│   ├── AuthController.java           — login, register, candidates, user lookup
│   ├── JwtService.java               — token generation
│   └── db/migration/                 — V1 init, V2 CANDIDATE role, V3 password
├── interview-service/
│   ├── InterviewController.java      — CRUD, mine, summary, complete, abandon
│   ├── InterviewService.java         — business logic, mode-specific slot plans, rubric call, notifications
│   ├── InterviewMode.java            — enum for SCREENING, L1, L2, L3, L4 modes
│   ├── client/                       — Feign clients (AiServiceClient, ObserverServiceClient, etc.)
│   └── db/migration/                 — V1 init, V2 email/name, V3 WITHDRAWN, V4 rubric, V5 interview_mode
├── ai-service/
│   ├── AiController.java             — next-question, assess, generate-rubric
│   ├── QuestionService.java          — mode-specific slot themes, manipulation detection, difficulty calibration, caching, vague answer detection
│   ├── QuestionCacheService.java     — first question caching with 24h TTL
│   ├── AssessmentService.java        — two-pass, evidence, scoring, mode-specific verdict thresholds, roadmap, transcript deduplication
│   ├── RubricService.java            — JD category + candidate profile extraction
│   ├── OpenAiClient.java             — Claude API client with slot-based model switching
│   ├── client/ComplianceServiceClient.java — Feign client for compliance service
│   └── config/CacheCleanupScheduler.java — hourly cache cleanup
├── observer-service/
│   ├── ObserverController.java       — inject, flag, notify endpoints
│   ├── EmailService.java             — invite + abandon notifications
│   └── client/                       — Feign clients (AuthServiceClient, InterviewServiceClient)
├── review-service/
│   ├── ReviewController.java         — scores, sign-off
│   ├── ReviewService.java            — upsert sign-off, update interview status
│   ├── client/InterviewServiceClient.java — Feign client for interview service
│   └── db/migration/                 — V1 init, V2 WITHDRAWN, V3 gap/confidence
└── compliance-service/
    ├── AuditLogController.java       — audit log CRUD
    └── TokenController.java          — token tracking, limits, analytics
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

---

## Production Deployment on Ubuntu Server

### Prerequisites

- Ubuntu 20.04+ server with sudo access
- Java 21 installed
- PostgreSQL 12+ installed and running
- Domain name (optional, for HTTPS)
- Minimum 4GB RAM, 2 CPU cores

### 1. Install Java 21

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk
java -version  # Verify installation
```

### 2. Setup PostgreSQL

```bash
sudo apt install -y postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Create database and user
sudo -u postgres psql
```

```sql
CREATE DATABASE bench_readiness;
CREATE USER benchuser WITH ENCRYPTED PASSWORD 'your-secure-password';
GRANT ALL PRIVILEGES ON DATABASE bench_readiness TO benchuser;
\c bench_readiness
CREATE SCHEMA auth_svc;
CREATE SCHEMA interview_svc;
CREATE SCHEMA observer_svc;
CREATE SCHEMA review_svc;
CREATE SCHEMA compliance_svc;
GRANT ALL ON SCHEMA auth_svc TO benchuser;
GRANT ALL ON SCHEMA interview_svc TO benchuser;
GRANT ALL ON SCHEMA observer_svc TO benchuser;
GRANT ALL ON SCHEMA review_svc TO benchuser;
GRANT ALL ON SCHEMA compliance_svc TO benchuser;
\q
```

### 3. Build JAR Files

On your development machine:

```bash
cd bench-readiness
mvn clean package -DskipTests

# JAR files will be in:
# api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar
# auth-service/target/auth-service-0.0.1-SNAPSHOT.jar
# interview-service/target/interview-service-0.0.1-SNAPSHOT.jar
# ai-service/target/ai-service-0.0.1-SNAPSHOT.jar
# observer-service/target/observer-service-0.0.1-SNAPSHOT.jar
# review-service/target/review-service-0.0.1-SNAPSHOT.jar
# compliance-service/target/compliance-service-0.0.1-SNAPSHOT.jar
```

### 4. Transfer JARs to Server

```bash
# Create application directory on server
ssh user@your-server "sudo mkdir -p /opt/bench-readiness/{api-gateway,auth-service,interview-service,ai-service,observer-service,review-service,compliance-service}"

# Transfer JAR files
scp api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/api-gateway/
scp auth-service/target/auth-service-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/auth-service/
scp interview-service/target/interview-service-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/interview-service/
scp ai-service/target/ai-service-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/ai-service/
scp observer-service/target/observer-service-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/observer-service/
scp review-service/target/review-service-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/review-service/
scp compliance-service/target/compliance-service-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/compliance-service/
```

### 5. Create Environment Configuration

On the server, create `/opt/bench-readiness/.env`:

```bash
sudo nano /opt/bench-readiness/.env
```

Add:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/bench_readiness
DB_USER=benchuser
DB_PASSWORD=your-secure-password
JWT_SECRET=production-jwt-secret-min-32-chars-change-this
CLAUDE_API_KEY=sk-ant-your-actual-key
CLAUDE_MODEL=claude-haiku-4-5
CLAUDE_ASSESSMENT_MODEL=claude-sonnet-4-5
CLAUDE_ASSESSMENT_MAX_TOKENS=4000
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password
OBSERVER_SERVICE_URL=http://localhost:8084
AI_SERVICE_URL=http://localhost:8083
AUTH_SERVICE_URL=http://localhost:8081
INTERVIEW_SERVICE_URL=http://localhost:8082
INTERVIEW_BASE_URL=https://your-domain.com/interview
COMPLIANCE_SERVICE_URL=http://localhost:8086
```

```bash
sudo chmod 600 /opt/bench-readiness/.env
```

### 6. Create Systemd Service Files

#### API Gateway Service

```bash
sudo nano /etc/systemd/system/bench-api-gateway.service
```

```ini
[Unit]
Description=Bench Readiness API Gateway
After=network.target postgresql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/bench-readiness/api-gateway
EnvironmentFile=/opt/bench-readiness/.env
ExecStart=/usr/bin/java -jar -Xmx512m api-gateway-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=bench-api-gateway

[Install]
WantedBy=multi-user.target
```

#### Auth Service

```bash
sudo nano /etc/systemd/system/bench-auth-service.service
```

```ini
[Unit]
Description=Bench Readiness Auth Service
After=network.target postgresql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/bench-readiness/auth-service
EnvironmentFile=/opt/bench-readiness/.env
ExecStart=/usr/bin/java -jar -Xmx512m auth-service-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=bench-auth-service

[Install]
WantedBy=multi-user.target
```

#### Interview Service

```bash
sudo nano /etc/systemd/system/bench-interview-service.service
```

```ini
[Unit]
Description=Bench Readiness Interview Service
After=network.target postgresql.service bench-auth-service.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/bench-readiness/interview-service
EnvironmentFile=/opt/bench-readiness/.env
ExecStart=/usr/bin/java -jar -Xmx512m interview-service-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=bench-interview-service

[Install]
WantedBy=multi-user.target
```

#### AI Service

```bash
sudo nano /etc/systemd/system/bench-ai-service.service
```

```ini
[Unit]
Description=Bench Readiness AI Service
After=network.target bench-compliance-service.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/bench-readiness/ai-service
EnvironmentFile=/opt/bench-readiness/.env
ExecStart=/usr/bin/java -jar -Xmx1024m ai-service-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=bench-ai-service

[Install]
WantedBy=multi-user.target
```

#### Observer Service

```bash
sudo nano /etc/systemd/system/bench-observer-service.service
```

```ini
[Unit]
Description=Bench Readiness Observer Service
After=network.target bench-auth-service.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/bench-readiness/observer-service
EnvironmentFile=/opt/bench-readiness/.env
ExecStart=/usr/bin/java -jar -Xmx512m observer-service-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=bench-observer-service

[Install]
WantedBy=multi-user.target
```

#### Review Service

```bash
sudo nano /etc/systemd/system/bench-review-service.service
```

```ini
[Unit]
Description=Bench Readiness Review Service
After=network.target postgresql.service bench-interview-service.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/bench-readiness/review-service
EnvironmentFile=/opt/bench-readiness/.env
ExecStart=/usr/bin/java -jar -Xmx512m review-service-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=bench-review-service

[Install]
WantedBy=multi-user.target
```

#### Compliance Service

```bash
sudo nano /etc/systemd/system/bench-compliance-service.service
```

```ini
[Unit]
Description=Bench Readiness Compliance Service
After=network.target postgresql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/bench-readiness/compliance-service
EnvironmentFile=/opt/bench-readiness/.env
ExecStart=/usr/bin/java -jar -Xmx512m compliance-service-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=bench-compliance-service

[Install]
WantedBy=multi-user.target
```

### 7. Enable and Start Services

```bash
# Reload systemd to recognize new services
sudo systemctl daemon-reload

# Enable services to start on boot
sudo systemctl enable bench-compliance-service
sudo systemctl enable bench-auth-service
sudo systemctl enable bench-interview-service
sudo systemctl enable bench-ai-service
sudo systemctl enable bench-observer-service
sudo systemctl enable bench-review-service
sudo systemctl enable bench-api-gateway

# Start services in order (dependencies first)
sudo systemctl start bench-compliance-service
sleep 5
sudo systemctl start bench-auth-service
sleep 5
sudo systemctl start bench-interview-service
sudo systemctl start bench-ai-service
sudo systemctl start bench-observer-service
sudo systemctl start bench-review-service
sleep 5
sudo systemctl start bench-api-gateway
```

### 8. Verify Services

```bash
# Check status of all services
sudo systemctl status bench-api-gateway
sudo systemctl status bench-auth-service
sudo systemctl status bench-interview-service
sudo systemctl status bench-ai-service
sudo systemctl status bench-observer-service
sudo systemctl status bench-review-service
sudo systemctl status bench-compliance-service

# View logs
sudo journalctl -u bench-api-gateway -f
sudo journalctl -u bench-auth-service -f

# Check if services are listening on ports
sudo netstat -tlnp | grep java
```

### 9. Setup Nginx Reverse Proxy (Optional)

```bash
sudo apt install -y nginx
sudo nano /etc/nginx/sites-available/bench-readiness
```

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 10M;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass http://localhost:8084;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/bench-readiness /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 10. Setup SSL with Let's Encrypt (Optional)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
sudo systemctl reload nginx
```

### 11. Management Commands

```bash
# Stop all services
sudo systemctl stop bench-api-gateway
sudo systemctl stop bench-review-service
sudo systemctl stop bench-observer-service
sudo systemctl stop bench-ai-service
sudo systemctl stop bench-interview-service
sudo systemctl stop bench-auth-service
sudo systemctl stop bench-compliance-service

# Restart a specific service
sudo systemctl restart bench-ai-service

# View logs for a service
sudo journalctl -u bench-ai-service -n 100 --no-pager

# Follow logs in real-time
sudo journalctl -u bench-api-gateway -f

# Check service status
sudo systemctl status bench-*
```

### 12. Update Deployment

When deploying new versions:

```bash
# On development machine, build new JARs
mvn clean package -DskipTests

# Transfer to server
scp service-name/target/service-name-0.0.1-SNAPSHOT.jar user@your-server:/opt/bench-readiness/service-name/

# On server, restart the service
sudo systemctl restart bench-service-name

# Verify
sudo systemctl status bench-service-name
sudo journalctl -u bench-service-name -n 50
```

### 13. Monitoring and Maintenance

```bash
# Check disk usage
df -h

# Check memory usage
free -h

# Check Java processes
ps aux | grep java

# Monitor system resources
top
htop  # Install with: sudo apt install htop

# Database backup
sudo -u postgres pg_dump bench_readiness > backup_$(date +%Y%m%d).sql

# View all service logs
sudo journalctl -u 'bench-*' --since today
```

### Troubleshooting

**Service won't start:**
```bash
# Check logs for errors
sudo journalctl -u bench-service-name -n 100

# Verify JAR file exists
ls -lh /opt/bench-readiness/service-name/

# Check environment variables
sudo systemctl show bench-service-name --property=Environment

# Test JAR manually
cd /opt/bench-readiness/service-name
source /opt/bench-readiness/.env
java -jar service-name-0.0.1-SNAPSHOT.jar
```

**Port already in use:**
```bash
# Find process using port
sudo lsof -i :8080

# Kill process
sudo kill -9 <PID>
```

**Database connection issues:**
```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Test connection
psql -h localhost -U benchuser -d bench_readiness

# Check PostgreSQL logs
sudo tail -f /var/log/postgresql/postgresql-*.log
```
