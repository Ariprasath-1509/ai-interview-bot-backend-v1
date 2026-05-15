# Bench Readiness — Spring Boot Microservices

AI-powered technical interview platform with admin sign-off, candidate dashboard, and evaluation engine.

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
- Public paths: `/auth/login`, `/auth/logout`, `/auth/register`, `/auth/forgot-password`, `/auth/reset-password`, `/actuator`
- CORS configured for `http://localhost:6001`
- Client-side load balancing for service instances

### auth-service (6004)
- **Staff login** — real credentials (email + password), role determined by stored account
- **Candidate registration** — `POST /auth/register` with full profile: name, email, password, contactNumber, batch, source (B2B/BENCH/MARKET), skillSet (JAVA_SB/JFSR/REACT_JS), yoeActual, yoePortrayed, yop, officialEmail, personalEmail
- **Candidate login** — email as username (official or personal), password validated against stored value
- **Forgot password** — `POST /auth/forgot-password` sends 6-digit OTP to email (valid 10 minutes)
- **Reset password** — `POST /auth/reset-password` verifies OTP and updates password
- **Candidate profile update** — `PATCH /auth/candidates/{id}` (ADMIN only) — update rating (ASSET/MEDIUM/LIABILITY), candidateStatus (RFD/WFD/DOB/DEPLOYED), noOfInterviews
- **Deployment bulk import** — `POST /auth/candidates/deployment/bulk-import` (ADMIN only) — bulk import deployment data from Excel (matches by email)
- **Get deployed candidates** — `GET /auth/candidates/deployed` — list all candidates with DEPLOYED status
- **Update deployment** — `PATCH /auth/candidates/{id}/deployment` (ADMIN only) — update deployment fields (empId, clientName, deployedDate, mentor)
- **Clear deployment** — `DELETE /auth/candidates/{id}/deployment` (ADMIN only) — clear deployment fields and reset status
- **Candidate profile view** — `GET /auth/candidates/{id}` — full candidate profile with all fields
- **Staff creation** — `POST /auth/staff` (SUPER_ADMIN only) — creates RECRUITER or ADMIN accounts
- **Staff listing** — `GET /auth/staff` (SUPER_ADMIN only)
- **Staff deletion** — `DELETE /auth/staff/{id}` (SUPER_ADMIN only)
- **Roles** — `CANDIDATE`, `RECRUITER`, `ADMIN`, `SUPER_ADMIN`
- **Admin source segregation** — BENCH admin manages B2B+BENCH candidates, RECRUITMENT admin manages MARKET candidates
- **Candidate search** — `GET /auth/candidates?search=` filtered by admin source (returns full profile)
- **User lookup** — `GET /auth/users/{id}` for internal service-to-service calls
- Default super admin: `admin@benchreadiness.com` / `Admin@123` (seeded on first startup)
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
- **Client management** — client-centric approach with JD details and candidate requirements (BENCH/B2B vs MARKET)
- **AI candidate matching** — calls ai-service for Claude-powered intelligent matching with detailed analysis, strengths/concerns, and recommendations. Falls back to rule-based algorithm if AI unavailable.
- **Auto-fill interview creation** — automatically populate interview forms using candidate and client data with intelligent suggestions
- `GET /interviews/mine` — candidate sees only their own interviews (filtered by `X-User-Email`)
- `GET /interviews/summary` — manager list with candidate name, email, JD title, verdict, interview mode
- `GET /analytics/realtime` — real-time dashboard statistics by role
- `GET /analytics/modes` — interview mode distribution analytics
- `PATCH /interviews/{id}/complete` — update status, transcript, verdict
- `POST /interviews/{id}/abandon` — candidate exits early or time expires, notifies admin via observer-service
- `resumeSummary` is required — used for candidate profile extraction and question calibration
- **Feign clients**: AiServiceClient (includes AiMatchingClient), ObserverServiceClient, ReviewServiceClient, ComplianceServiceClient, AuthServiceClient
- Registers with Eureka as `INTERVIEW-SERVICE`

### ai-service (6003)
- **Claude API** (`claude-haiku-4-5` for questions/rubric, `claude-sonnet-4-5` for assessment and matching)
- **Token limits** — 4000 tokens for assessment (prevents JSON truncation), 1000 for rubric, 300 for questions
- **Rubric generation** (`POST /ai/generate-rubric`) — extracts 4–6 JD-specific categories + candidate profile (YOE, level, difficulty) at interview creation. Uses dedicated `rubric-max-tokens` to prevent JSON truncation.
- **Question generation** (`POST /ai/next-question`) — mode-specific themed questions with difficulty calibration (easy to hard based on interview mode)
- **AI-powered candidate matching** (`POST /ai/match-candidates`) — uses Claude Sonnet to intelligently match candidates to client requirements:
  - **Eligibility filtering**: Only matches RFD candidates with 3+ completed interviews (reduces AI calls by ~90%)
  - **Interview evidence integration**: Uses pros/cons and category scores from recent 3 interviews for evidence-based matching
  - Analyzes 6 weighted criteria: skill alignment (30%), experience level (25%), role complexity (20%), quality indicators (15%), interview performance (10%), frequency concerns (penalty)
  - Provides detailed strengths, concerns, and recommendations (HIGHLY_RECOMMENDED / RECOMMENDED / CONSIDER / NOT_SUITABLE)
  - Considers skill evolution potential, cultural fit indicators, and red flags
  - Returns ranked matches with match scores, rationale, and actionable insights
  - Fallback to rule-based matching when AI unavailable
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
- WebSocket STOMP — admin can observe live interview at `/topic/observer/{interviewId}`
- `POST /observer/inject` — inject follow-up question into live interview (ADMIN, SUPER_ADMIN, RECRUITER)
- `POST /observer/flag` — flag a candidate answer (ADMIN, SUPER_ADMIN)
- **Email notifications** via Gmail SMTP:
  - Interview invite sent to candidate on creation
  - Admin alerted when candidate abandons or time expires
  - Client creation notifications sent to bench admin (if benchB2bCandidatesNeeded > 0) and recruitment admin (if marketCandidatesNeeded > 0)
- Auth-service lookup for admin email on abandon notification
- **Feign clients**: AuthServiceClient, InterviewServiceClient
- Registers with Eureka as `OBSERVER-SERVICE`

### review-service (6008)
- `GET /scores/{interviewId}` — category scores with rationale, evidence, gap, confidence
- `POST /scores` — save/replace scores (called by frontend after assessment)
- `GET /reviews/{interviewId}` — sign-off status `{ signedOff, finalVerdict, note, signedOffAt }`
- `POST /reviews/{interviewId}/sign-off` — ADMIN only, upsertable (can update existing sign-off)
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
- Audit log access for SUPER_ADMIN and ADMIN roles

---

## Database

Single PostgreSQL instance, schema-per-service isolation.

| Schema | Service | Key Tables |
|---|---|---|
| `auth_svc` | auth-service | `users` (with candidate profile: batch, source, status, rating, skill_set, yoe_actual, yoe_portrayed, yop, contact_number, official_email, personal_email, no_of_interviews (external client interviews), system_interview_count (auto-tracked interviews in our application), emp_id, deployed_client_name, deployed_date, mentor), `password_reset_otps` (OTP storage with expiration) |
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

### Candidate Status Values
- **RFD** (Ready for Deployment) — Candidate passed interviews and ready to be deployed
- **WFD** (Waiting for Deployment) — Candidate approved but waiting for client assignment
- **DOB** (Deploy Observe on Bill) — Candidate deployed and being observed on billable project
- **DEPLOYED** — Candidate currently deployed to a client with full deployment details (empId, clientName, deployedDate, mentor)

---

## Prerequisites

- Red Hat build of OpenJDK 21 (download from https://developers.redhat.com/products/openjdk/download)
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

Services share a remote PostgreSQL with limited connections. Each service uses a pool of 3 connections (max 15 total). **Start services one at a time** and wait for each to fully register with Eureka before starting the next.

#### Start Sequence

| Step | Service | Port | Wait for | Why |
|------|---------|------|----------|-----|
| 1 | eureka-server | 6009 | Dashboard at http://localhost:6009 | All services register here — must be up first |
| 2 | compliance-service | 6005 | `Started ComplianceServiceApplication` in logs | ai-service and interview-service depend on it for token tracking |
| 3 | auth-service | 6004 | `Started AuthServiceApplication` in logs | interview-service, observer-service need user lookups |
| 4 | interview-service | 6006 | `Started InterviewServiceApplication` in logs | review-service and observer-service call it via Feign |
| 5 | ai-service | 6003 | `Started AiServiceApplication` in logs | interview-service calls it for rubric generation |
| 6 | observer-service | 6007 | `Started ObserverServiceApplication` in logs | interview-service calls it for email notifications |
| 7 | review-service | 6008 | `Started ReviewServiceApplication` in logs | Calls interview-service for sign-off status updates |
| 8 | api-gateway | 6002 | `Started ApiGatewayApplication` in logs | Routes to all services — start last |

#### Terminal Commands

```bash
# Terminal 1 — Service Registry (MUST START FIRST, wait for dashboard)
cd eureka-server && mvn spring-boot:run

# Terminal 2 — wait ~15s after eureka is up
cd compliance-service && mvn spring-boot:run

# Terminal 3 — wait ~15s after compliance starts
cd auth-service && mvn spring-boot:run

# Terminal 4 — wait ~15s after auth starts
cd interview-service && mvn spring-boot:run

# Terminal 5 — wait ~15s after interview starts
cd ai-service && mvn spring-boot:run

# Terminal 6 — can start alongside ai-service
cd observer-service && mvn spring-boot:run

# Terminal 7 — wait ~15s after interview-service is registered in Eureka
cd review-service && mvn spring-boot:run

# Terminal 8 — start after all services show UP in Eureka dashboard
cd api-gateway && mvn spring-boot:run
```

#### Quick Start Script

```bash
start-all.bat
```

Handles dependency order and wait times automatically.

#### Connection Pool Configuration

All database-connected services use HikariCP with limited pools to avoid exhausting the remote PostgreSQL `max_connections`:

| Service | Pool Size | Idle |
|---------|-----------|------|
| auth-service | 3 | 1 |
| interview-service | 3 | 1 |
| observer-service | 3 | 1 |
| review-service | 3 | 1 |
| compliance-service | 3 | 1 |
| **Total max** | **15** | **5** |

If you see `too many clients already`, stop all services, wait 30s for connections to release, then restart in order.

**Eureka Dashboard**: http://localhost:6009 — verify all 7 services show `UP` before using the platform.

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
# Response: {"ok":true,"token":"eyJ...","role":"ADMIN","name":"Admin"}

# Candidate registration (full profile)
curl -X POST http://localhost:6002/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "password": "secret123",
    "contactNumber": "9876543210",
    "officialEmail": "john@company.com",
    "personalEmail": "john@example.com",
    "batch": "Batch-2026-Q2",
    "source": "MARKET",
    "skillSet": "JAVA_SB",
    "yoeActual": 3.5,
    "yoePortrayed": 5.0,
    "yop": 2022
  }'

# Get candidate profile
curl http://localhost:6002/auth/candidates/<id> \
  -H "Authorization: Bearer <token>"

# Update candidate (ADMIN only — rating, status, no_of_interviews)
curl -X PATCH http://localhost:6002/auth/candidates/<id> \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"rating": "ASSET", "candidateStatus": "DEPLOYED", "noOfInterviews": 3}'

# Bulk import deployment data (ADMIN only)
curl -X POST http://localhost:6002/auth/candidates/deployment/bulk-import \
  -H "Authorization: Bearer <admin-token>" \
  -F "file=@deployment_data.xlsx"
# Excel format: Emp ID | Email | Client Name | Deployed Date (YYYY-MM-DD) | Mentor (optional)
# Response: {"totalRows":50,"successCount":48,"warningCount":1,"failureCount":1,"details":[...]}

# Get deployed candidates
curl http://localhost:6002/auth/candidates/deployed \
  -H "Authorization: Bearer <token>"

# Update deployment fields
curl -X PATCH http://localhost:6002/auth/candidates/<id>/deployment \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"empId":"EMP001","clientName":"TechCorp","deployedDate":"2024-01-15","mentor":"Jane Smith"}'

# Clear deployment (reset to RFD status)
curl -X DELETE http://localhost:6002/auth/candidates/<id>/deployment \
  -H "Authorization: Bearer <admin-token>"

# Candidate login
curl -X POST http://localhost:6002/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john@example.com","password":"secret123","role":"CANDIDATE"}'

# Create staff account (SUPER_ADMIN only, adminSource required for ADMIN role)
curl -X POST http://localhost:6002/auth/staff \
  -H "Authorization: Bearer <super-admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Jane Smith","email":"jane@company.com","password":"Pass@123","role":"ADMIN","adminSource":"BENCH"}'

# List all staff (SUPER_ADMIN only)
curl http://localhost:6002/auth/staff \
  -H "Authorization: Bearer <super-admin-token>"

# Delete staff account (SUPER_ADMIN only)
curl -X DELETE http://localhost:6002/auth/staff/<id> \
  -H "Authorization: Bearer <super-admin-token>"

# Search registered candidates (manager use)
curl http://localhost:6002/auth/candidates?search=john \
  -H "Authorization: Bearer <token>"

# Get current user
curl http://localhost:6002/auth/me \
  -H "Authorization: Bearer <token>"

# Forgot password - Request OTP
curl -X POST http://localhost:6002/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com"}'
# Response: {"ok":true,"message":"OTP sent to your email"}

# Reset password - Verify OTP and set new password
curl -X POST http://localhost:6002/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","otp":"123456","newPassword":"newSecret123"}'
# Response: {"ok":true,"message":"Password reset successful. You can now log in."}
```

### Interviews

```bash
# Preview auto-fill data before creating interview
curl "http://localhost:6002/interviews/auto-fill/preview?candidateId=<candidate-id>&clientId=<client-id>" \
  -H "Authorization: Bearer <admin-token>"
# Response: {"engineerEmail":"john@example.com","engineerName":"John Doe","jdTitle":"Senior Java Developer","suggestedMode":"L3","focusAreas":"Java & Spring Boot, Microservices Architecture","candidateDataFound":true,"clientDataFound":true}

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
# Admin notified by email automatically

# Download candidate review summary PDF (last 5 interviews)
curl -X GET "http://localhost:6002/interviews/candidates/<candidateId>/review-summary/download" \
  -H "Authorization: Bearer <admin-token>" \
  -o candidate_review.pdf
# Response: PDF file with candidate profile, performance overview, interview details, scores, feedback, roadmaps
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

# Sign off (ADMIN only, upsertable)
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

# Notify client created (called automatically by interview-service)
curl -X POST http://localhost:6002/observer/notify/client-created \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"<id>","clientName":"TechCorp","jdRole":"Senior Java Developer","benchB2bCandidatesNeeded":3,"marketCandidatesNeeded":2}'
```

### AI Matching

```bash
# Get all clients with matching overview
curl http://localhost:6002/clients/matching/overview \
  -H "Authorization: Bearer <admin-token>"
# Response: {"clients":[{"clientId":"...","clientName":"TechCorp","jdRole":"Senior Java Developer","benchB2bSummary":{"totalMatches":8,"highlyRecommended":2,"recommended":3,"lastComputedAt":"...","cached":true}}],"totalClients":5}
# Note: Only RFD candidates with 3+ interviews are matched

# Get detailed matches for a specific client
curl "http://localhost:6002/clients/matching/<client-id>?source=BENCH_B2B" \
  -H "Authorization: Bearer <admin-token>"
# Response: {"clientId":"...","clientName":"TechCorp","source":"BENCH_B2B","matches":[...],"summary":{"totalCandidatesAnalyzed":8,"highlyRecommended":2},"computedAt":"...","cacheSource":"cached"}
# Matches include interview evidence: strengths/weaknesses from recent 3 interviews, category scores

# Refresh matches for a client (bypass cache)
curl -X POST http://localhost:6002/clients/matching/<client-id>/refresh \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"source":"BENCH_B2B"}'
# Response: Fresh AI-computed matches with "cacheSource":"ai-fresh"

# Clear all matching caches (SUPER_ADMIN only)
curl -X POST http://localhost:6002/clients/matching/cache/clear \
  -H "Authorization: Bearer <super-admin-token>"

# Get cache statistics
curl http://localhost:6002/clients/matching/cache/stats \
  -H "Authorization: Bearer <super-admin-token>"

# Find matching candidates for a client (generic)
curl -X POST http://localhost:6002/matching/candidates \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"clientId":"<client-id>","source":"BENCH_B2B","maxCandidates":10}'

# Find BENCH/B2B candidates for a client
curl -X POST http://localhost:6002/matching/clients/<client-id>/bench-candidates?maxCandidates=10 \
  -H "Authorization: Bearer <admin-token>"

# Find MARKET candidates for a client
curl -X POST http://localhost:6002/matching/clients/<client-id>/market-candidates?maxCandidates=10 \
  -H "Authorization: Bearer <admin-token>"
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
| 5th+ | Interview terminated, status set to `COMPLETED` with verdict `WITHDRAWN`, admin notified |

Detected patterns include: score manipulation requests, topic restriction commands, prompt injection, identity override attempts.

---

## Interview Timer

- Mode-specific countdown: SCREENING (15min), L1 (20min), L2 (25min), L3 (30min), L4 (30min)
- Custom duration override available during interview creation
- Timer starts when first BOT question is received
- Timer displayed in interview UI, turns red under 5 minutes
- On expiry: AI sends closing message → interview auto-submitted → admin notified with reason `time_expired`

---

## Email Notifications

| Event | Recipient | Content |
|---|---|---|
| Interview created | Candidate | Interview link + login instructions |
| Interview cancelled | Candidate | Beautiful HTML email with cancellation details, interview ID, position, reason |
| Candidate abandons | Admin | Interview ID + review link + reason |
| Time expired | Admin | Interview ID + review link + reason |
| Client created | Bench Admin (if benchB2bCandidatesNeeded > 0) | Client details + action required |
| Client created | Recruitment Admin (if marketCandidatesNeeded > 0) | Client details + action required |
| Daily digest (7 PM) | SUPER_ADMIN + ADMIN | Today's interviews table with status and verdict |

---

## Roles & Permissions

| Role | Who they are | Can do |
|---|---|---|
| `CANDIDATE` | Job candidate | Take interview, view own dashboard, view own feedback |
| `RECRUITER` | Technical recruiter | Create/view/update clients, create/schedule interviews, view all RFD/WFD/DOB candidates, view candidate matches, view interview scores, observe live interviews, inject questions, delete interviews |
| `ADMIN` (BENCH) | Bench hiring manager | Everything RECRUITER can do + sign off interviews, update candidates, manage deployments — only for B2B and BENCH source candidates |
| `ADMIN` (RECRUITMENT) | Recruitment hiring manager | Same as Bench Admin — only for MARKET source candidates |
| `SUPER_ADMIN` | Platform owner | Everything ADMIN can do + create/delete staff accounts (ADMIN, RECRUITER), manage retention policies, platform monitoring, sees all candidates regardless of source |

### Admin Source Segregation

| Admin Source | Manages Candidates With Source |
|---|---|
| `BENCH` | BENCH |
| `BD` | B2B |
| `RECRUITMENT` | MARKET |

When creating an ADMIN via `POST /auth/staff`, the `adminSource` field is required (`BENCH`, `BD`, or `RECRUITMENT`).
SUPER_ADMIN sees all candidates regardless of source.

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
| Upsertable sign-off | Admin can correct a verdict without creating a new record |
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
│   ├── profile/            — View & edit candidate profile
│   ├── resume/             — Resume upload (PDF, DOC, DOCX)
│   ├── notifications/      — In-app notification center
│   └── feedback/[id]/      — Category scores, roadmap, manager review
├── interview/[id]/         — Live interview, timer, abandon button
├── admin/
│   ├── interviews/create/  — Create interview with candidate search, resume upload, auto-fill, matching clients dropdown
│   ├── setup/              — (Redirects to /admin/interviews/create for backward compatibility)
│   ├── review/             — Interview list with status/verdict badges
│   ├── calendar/           — Interview calendar view
│   ├── candidates/         — Candidate management with filters
│   ├── clients/
│   │   └── matching/       — Client matching dashboard with AI-powered candidate recommendations
│   ├── matching/           — AI candidate matching (legacy)
│   ├── staff/              — Staff management (SUPER_ADMIN only)
│   ├── settings/tokens/    — Token usage and limits
│   └── interviews/[id]/review/ — Full review: scores, consistency, signals, sign-off
└── observer/               — Live observer view with WebSocket
```

### Frontend Features

**Layout & Navigation:**
- Collapsible sidebar layout with role-based navigation
- Centralized role configuration with permissions
- Dark/light theme toggle
- Responsive design for mobile and desktop

**UI Components:**
- Toast notification system
- Confirmation dialogs
- Skeleton loading states
- Pagination with search
- Advanced search with filters
- Status timeline component
- Profile completion cards
- Statistics grid widgets
- Calendar widgets

**Candidate Features:**
- Resume upload with drag & drop (PDF preview)
- Profile completion tracking
- Interview status timeline
- In-app notifications
- Feedback and roadmap viewing

**Admin Features:**
- Interview calendar view
- Advanced candidate filtering (source, skill, rating, status)
- Real-time dashboard statistics
- Token usage monitoring
- Staff management (SUPER_ADMIN)
- Bulk operations with confirmation
- Client matching dashboard with AI-powered recommendations
- Cached matching results with refresh capability
- CSV export for candidate matches

**API Integration:**
- Resume upload: `POST /api/candidates/resume`
- Notifications: `GET /api/notifications`
- Mark notifications read: `PATCH /api/notifications/{id}/read`
- Mark all read: `PATCH /api/notifications/read-all`
- Client matching overview: `GET /api/recruiter/clients/matching/overview`
- Client match details: `GET /api/recruiter/clients/matching/{clientId}?source=BENCH_B2B|MARKET`
- Refresh matches: `POST /api/recruiter/clients/matching/{clientId}/refresh`

---

## Production Deployment on Ubuntu Server

### Prerequisites

- Ubuntu 20.04+ server with sudo access
- Java 21 installed
- PostgreSQL 12+ installed and running
- Domain name (optional, for HTTPS)
- Minimum 4GB RAM, 2 CPU cores

### 1. Install Red Hat OpenJDK 21

```bash
sudo apt update
# Option 1: Install from Red Hat RPM (RHEL/Fedora)
sudo yum install java-21-openjdk-devel

# Option 2: Install from tarball (Ubuntu/Debian)
# Download from https://developers.redhat.com/products/openjdk/download
tar -xzf java-21-openjdk-*.tar.gz -C /usr/lib/jvm/
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH=$JAVA_HOME/bin:$PATH

java -version  # Verify: should show "Red Hat" build
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


---

## Docker Deployment Guide

### Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- 4GB RAM minimum, 8GB recommended
- 20GB disk space

### Recent Changes & Fixes

**Critical Updates (April 2026):**

1. **Vague Answer Detection Threshold**: Changed from 15 words to 100 words minimum to reduce false positives for detailed technical answers
2. **Feign PATCH Support**: Added `feign-hc5` dependency to review-service to support PATCH HTTP method for sign-off updates
3. **Interview Status Update**: Fixed sign-off flow to properly update interview status from `REVIEW_PENDING` to `SIGNED_OFF`
4. **Frontend API Routes**: Removed Next.js rewrite rules that were bypassing API route handlers
5. **Proxy Configuration**: Renamed `middleware.ts` to `proxy.ts` for Next.js 16 compatibility
6. **Token Tracking**: Enhanced logging in AI service and compliance service for better debugging
7. **Interview Quality Display**: Fixed frontend to properly map `categoriesCovered` and `categoriesMissed` fields

**Configuration Changes:**

- **review-service/pom.xml**: Added `feign-hc5` dependency
- **review-service/application.yml**: Added `feign.httpclient.hc5.enabled: true`
- **ai-service QuestionService**: Updated `isVagueAnswer()` word count threshold to 100
- **interview-service**: Added `PATCH /interviews/{id}` endpoint and `updateInterview()` method
- **frontend next.config.js**: Removed `/api/:path*` rewrite rule
- **frontend proxy.ts**: Updated matcher to exclude all `/api` routes

### 1. Create Docker Network

```bash
docker network create bench-network
```

### 2. Setup PostgreSQL Container

```bash
# Create volume for data persistence
docker volume create bench-postgres-data

# Run PostgreSQL
docker run -d \
  --name bench-postgres \
  --network bench-network \
  -e POSTGRES_DB=bench_readiness \
  -e POSTGRES_USER=benchuser \
  -e POSTGRES_PASSWORD=your-secure-password \
  -p 5432:5432 \
  -v bench-postgres-data:/var/lib/postgresql/data \
  postgres:15-alpine

# Wait for PostgreSQL to start
sleep 10

# Create schemas
docker exec -it bench-postgres psql -U benchuser -d bench_readiness -c "
CREATE SCHEMA IF NOT EXISTS auth_svc;
CREATE SCHEMA IF NOT EXISTS interview_svc;
CREATE SCHEMA IF NOT EXISTS observer_svc;
CREATE SCHEMA IF NOT EXISTS review_svc;
CREATE SCHEMA IF NOT EXISTS compliance_svc;
"
```
### DB Pre seeding credential

-- 1. Default Super Admin
INSERT INTO auth_svc.users (id, email, name, password, role, created_at, updated_at)
VALUES (gen_random_uuid()::text, 'admin@benchreadiness.com', 'Admin', 'Admin@123', 'SUPER_ADMIN', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

### 3. Create Dockerfiles for Each Service

All services use `registry.access.redhat.com/ubi9/openjdk-21:1.20` as the Docker base image (Red Hat UBI 9 with OpenJDK 21). Dockerfiles are already included in each service directory.

### 4. Create Docker Compose File

Create `docker-compose.yml` in the root directory:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: bench-postgres
    environment:
      POSTGRES_DB: bench_readiness
      POSTGRES_USER: benchuser
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - bench-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U benchuser -d bench_readiness"]
      interval: 10s
      timeout: 5s
      retries: 5

  eureka-server:
    build:
      context: ./eureka-server
      dockerfile: Dockerfile
    container_name: bench-eureka
    ports:
      - "6009:6009"
    networks:
      - bench-network
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:6009/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  compliance-service:
    build:
      context: ./compliance-service
      dockerfile: Dockerfile
    container_name: bench-compliance
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/bench_readiness
      SPRING_DATASOURCE_USERNAME: benchuser
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:6009/eureka/
    ports:
      - "6005:6005"
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    networks:
      - bench-network
    restart: on-failure

  auth-service:
    build:
      context: ./auth-service
      dockerfile: Dockerfile
    container_name: bench-auth
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/bench_readiness
      SPRING_DATASOURCE_USERNAME: benchuser
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      APP_JWT_SECRET: ${JWT_SECRET}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:6009/eureka/
    ports:
      - "6004:6004"
    depends_on:
      postgres:
        condition: service_healthy
      eureka-server:
        condition: service_healthy
    networks:
      - bench-network
    restart: on-failure

  interview-service:
    build:
      context: ./interview-service
      dockerfile: Dockerfile
    container_name: bench-interview
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/bench_readiness
      SPRING_DATASOURCE_USERNAME: benchuser
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:6009/eureka/
    ports:
      - "6006:6006"
    depends_on:
      - postgres
      - eureka-server
      - auth-service
      - compliance-service
    networks:
      - bench-network
    restart: on-failure

  ai-service:
    build:
      context: ./ai-service
      dockerfile: Dockerfile
    container_name: bench-ai
    environment:
      CLAUDE_API_KEY: ${CLAUDE_API_KEY}
      CLAUDE_MODEL: ${CLAUDE_MODEL:-claude-haiku-4-5}
      CLAUDE_ASSESSMENT_MODEL: ${CLAUDE_ASSESSMENT_MODEL:-claude-sonnet-4-5}
      CLAUDE_ASSESSMENT_MAX_TOKENS: ${CLAUDE_ASSESSMENT_MAX_TOKENS:-4000}
      CLAUDE_RUBRIC_MAX_TOKENS: ${CLAUDE_RUBRIC_MAX_TOKENS:-1000}
      CLAUDE_QUESTION_MAX_TOKENS: ${CLAUDE_QUESTION_MAX_TOKENS:-300}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:6009/eureka/
    ports:
      - "6003:6003"
    depends_on:
      - eureka-server
      - compliance-service
    networks:
      - bench-network
    restart: on-failure

  observer-service:
    build:
      context: ./observer-service
      dockerfile: Dockerfile
    container_name: bench-observer
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/bench_readiness
      SPRING_DATASOURCE_USERNAME: benchuser
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_MAIL_HOST: ${MAIL_HOST:-smtp.gmail.com}
      SPRING_MAIL_PORT: ${MAIL_PORT:-587}
      SPRING_MAIL_USERNAME: ${MAIL_USERNAME}
      SPRING_MAIL_PASSWORD: ${MAIL_PASSWORD}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:6009/eureka/
    ports:
      - "6007:6007"
    depends_on:
      - postgres
      - eureka-server
      - auth-service
    networks:
      - bench-network
    restart: on-failure

  review-service:
    build:
      context: ./review-service
      dockerfile: Dockerfile
    container_name: bench-review
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/bench_readiness
      SPRING_DATASOURCE_USERNAME: benchuser
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      APP_JWT_SECRET: ${JWT_SECRET}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:6009/eureka/
      FEIGN_HTTPCLIENT_HC5_ENABLED: true
    ports:
      - "6008:6008"
    depends_on:
      - postgres
      - eureka-server
      - interview-service
    networks:
      - bench-network
    restart: on-failure

  api-gateway:
    build:
      context: ./api-gateway
      dockerfile: Dockerfile
    container_name: bench-gateway
    environment:
      APP_JWT_SECRET: ${JWT_SECRET}
      EUREKA_CLIENT_SERVICEURL_DEFAULTZONE: http://eureka-server:6009/eureka/
      SPRING_CLOUD_GATEWAY_GLOBALCORS_CORSCONFIGURATIONS_[/**]_ALLOWEDORIGINS: ${CORS_ALLOWED_ORIGINS:-http://localhost:6001}
    ports:
      - "6002:6002"
    depends_on:
      - eureka-server
      - auth-service
      - interview-service
      - ai-service
      - observer-service
      - review-service
      - compliance-service
    networks:
      - bench-network
    restart: on-failure

volumes:
  postgres-data:

networks:
  bench-network:
    driver: bridge
```

### 5. Create Environment File

Create `.env` file in the root directory:

```bash
# Database
DB_PASSWORD=your-secure-password

# JWT
JWT_SECRET=production-jwt-secret-min-32-chars-change-this

# Claude AI
CLAUDE_API_KEY=sk-ant-your-actual-key
CLAUDE_MODEL=claude-haiku-4-5
CLAUDE_ASSESSMENT_MODEL=claude-sonnet-4-5
CLAUDE_ASSESSMENT_MAX_TOKENS=4000
CLAUDE_RUBRIC_MAX_TOKENS=1000
CLAUDE_QUESTION_MAX_TOKENS=300

# Email (Gmail SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:6001
```

### 6. Build and Deploy

```bash
# Build all services
mvn clean package -DskipTests

# Build Docker images and start containers
docker-compose up -d --build

# View logs
docker-compose logs -f

# Check service health
docker-compose ps
```

### 7. Initialize Database Schemas

```bash
# Connect to PostgreSQL container
docker exec -it bench-postgres psql -U benchuser -d bench_readiness

# Create schemas (if not already created)
CREATE SCHEMA IF NOT EXISTS auth_svc;
CREATE SCHEMA IF NOT EXISTS interview_svc;
CREATE SCHEMA IF NOT EXISTS observer_svc;
CREATE SCHEMA IF NOT EXISTS review_svc;
CREATE SCHEMA IF NOT EXISTS compliance_svc;

# Exit
\q
```

Flyway migrations will run automatically when services start.

### 8. Verify Deployment

```bash
# Check Eureka Dashboard
curl http://localhost:6009

# Check API Gateway health
curl http://localhost:6002/actuator/health

# Test authentication
curl -X POST http://localhost:6002/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@benchreadiness.com","password":"Admin@123"}'
```

### 9. Frontend Deployment (Docker)

Create `frontend/Dockerfile`:

```dockerfile
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:18-alpine
WORKDIR /app
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json ./package.json
COPY --from=builder /app/public ./public
EXPOSE 6001
CMD ["npm", "start"]
```

Add to `docker-compose.yml`:

```yaml
  frontend:
    build:
      context: ../AiInterviewBot
      dockerfile: Dockerfile
    container_name: bench-frontend
    environment:
      NEXT_PUBLIC_API_URL: http://api-gateway:6002
    ports:
      - "6001:6001"
    depends_on:
      - api-gateway
    networks:
      - bench-network
    restart: on-failure
```

### 10. Management Commands

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes data)
docker-compose down -v

# Restart a specific service
docker-compose restart ai-service

# View logs for a specific service
docker-compose logs -f ai-service

# Scale a service (if needed)
docker-compose up -d --scale interview-service=2

# Update a service
mvn clean package -DskipTests -pl interview-service
docker-compose up -d --build interview-service

# Execute command in container
docker exec -it bench-ai sh

# Check resource usage
docker stats
```

### 11. Production Considerations

**Security:**
- Change all default passwords
- Use Docker secrets for sensitive data
- Enable HTTPS with SSL certificates
- Configure firewall rules
- Use non-root users in containers

**Performance:**
- Adjust JVM heap sizes based on load
- Configure connection pools
- Enable caching where appropriate
- Monitor resource usage

**Monitoring:**
- Add health check endpoints
- Configure log aggregation (ELK stack)
- Set up metrics collection (Prometheus + Grafana)
- Configure alerts for failures

**Backup:**
```bash
# Backup PostgreSQL
docker exec bench-postgres pg_dump -U benchuser bench_readiness > backup_$(date +%Y%m%d).sql

# Restore PostgreSQL
docker exec -i bench-postgres psql -U benchuser bench_readiness < backup_20260429.sql
```

### 12. Troubleshooting

**Service won't start:**
```bash
# Check logs
docker-compose logs service-name

# Check if port is in use
netstat -tlnp | grep 6002

# Restart service
docker-compose restart service-name
```

**Database connection issues:**
```bash
# Check PostgreSQL logs
docker-compose logs postgres

# Test connection
docker exec -it bench-postgres psql -U benchuser -d bench_readiness

# Verify schemas exist
docker exec -it bench-postgres psql -U benchuser -d bench_readiness -c "\dn"
```

**Eureka registration issues:**
```bash
# Check Eureka dashboard
curl http://localhost:6009

# Verify service can reach Eureka
docker exec -it bench-ai wget -O- http://eureka-server:6009/eureka/apps
```

**Memory issues:**
```bash
# Check container memory usage
docker stats

# Increase memory limits in docker-compose.yml
deploy:
  resources:
    limits:
      memory: 2G
```

### 13. CI/CD Integration

**GitHub Actions Example:**

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy to Production

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'  # Use 'temurin' in CI; local dev uses Red Hat OpenJDK 21
      
      - name: Build with Maven
        run: mvn clean package -DskipTests
      
      - name: Build Docker images
        run: docker-compose build
      
      - name: Push to Registry
        run: |
          echo ${{ secrets.DOCKER_PASSWORD }} | docker login -u ${{ secrets.DOCKER_USERNAME }} --password-stdin
          docker-compose push
      
      - name: Deploy to Server
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.SERVER_HOST }}
          username: ${{ secrets.SERVER_USER }}
          key: ${{ secrets.SSH_PRIVATE_KEY }}
          script: |
            cd /opt/bench-readiness
            docker-compose pull
            docker-compose up -d
```

---

## Summary of Critical Fixes

1. **Vague Answer Detection**: Increased threshold to 100 words to prevent false positives
2. **PATCH Method Support**: Added Apache HttpClient 5 to review-service for Feign PATCH support
3. **Sign-off Status Update**: Fixed interview status update from REVIEW_PENDING to SIGNED_OFF
4. **Frontend Routing**: Removed conflicting Next.js rewrite rules
5. **Token Tracking**: Enhanced logging for better debugging
6. **Interview Quality**: Fixed field mapping for categoriesCovered/categoriesMissed

All services now properly communicate via Eureka service discovery with Feign clients supporting all HTTP methods including PATCH.
