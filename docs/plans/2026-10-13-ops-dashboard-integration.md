# aibot Ops Dashboard - Development & Integration Plan

**Goal:** Integrate the locked lightweight Ops Dashboard into the `aibot` platform (`qa · fireflink` environment) to monitor application health, container runtime lifetimes, and CI/CD deployment activity with **zero load** on running services.

**Architecture:**
- **Zero-Load Exporter:** A lightweight host-level script that interrogates Docker daemon (`docker inspect` / `docker ps`) and writes a static `status.json` (~2 KB). No HTTP requests or JVM probes are sent to the applications.
- **Pipeline Hook:** Jenkins [be-pipeline.v2.groovy](file:///c:/Users/User/Desktop/ai-bot-v1/docs/jenkins/be-pipeline.v2.groovy) updates deployment stage and recent deployment history on build events.
- **Client App:** Standalone static SPA ([aibot ops prototype.html](file:///c:/Users/User/Desktop/ai-bot-v1/ops-dashboard-prototype/aibot%20ops%20prototype.html)) fetching `/status.json` with tab visibility pause (0% background CPU).

---

## 1. System Architecture & Data Flow

```
                      ┌───────────────────────────────────────────────┐
                      │                Docker Host                    │
                      │             (/home/aibot/BE)                  │
                      └───────┬───────────────────────────────┬───────┘
                              │                               │
         [Cron: every 15-30s] │                               │ [On Build/Deploy]
         (Takes ~5ms CPU)     │                               │
                              ▼                               ▼
                     emit-status.sh                 be-pipeline.v2.groovy
                     (docker inspect)               (Deployment stages)
                              │                               │
                              └───────────────┬───────────────┘
                                              │ (Atomic write: .tmp -> mv)
                                              ▼
                                 /var/www/ops/status.json (~2 KB)
                                              │
                                              ▼
                                   Lightweight Web Server
                                (Nginx / Static file server)
                                              │
                                              ▼
                                     Browser Client
                               (aibot Ops Dashboard)
                               - 35 FPS idle / 0% on tab hidden
                               - Auto-refresh: 15-30s
                               - Staleness detector & retry logic
```

---

## 2. Telemetry Contract: `status.json` Schema

```json
{
  "environment": "qa · fireflink",
  "generatedAt": "2026-10-13T16:30:00Z",
  "deployment": {
    "active": {
      "service": "ai-service",
      "build": 142,
      "version": "v2.8.3",
      "stage": "live",
      "status": "success",
      "elapsedSeconds": 0
    },
    "recent": [
      { "id": 142, "svc": "ai-service", "ver": "v2.8.3", "sha": "6b8e1f0", "time": "45m ago", "status": "success" },
      { "id": 141, "svc": "api-gateway", "ver": "v2.4.1", "sha": "8a3d12c", "time": "1d ago", "status": "success" },
      { "id": 140, "svc": "auth-service", "ver": "v1.9.0", "sha": "e490b81", "time": "3d ago", "status": "success" }
    ]
  },
  "containers": [
    {
      "id": "api-gateway",
      "name": "api-gateway",
      "category": "core",
      "port": 6002,
      "version": "v2.4.1",
      "status": "healthy",
      "uptime": "Up 12d 4h",
      "restarts": 0,
      "memory": "320MB"
    },
    {
      "id": "kokoro-tts",
      "name": "kokoro-tts",
      "category": "plat",
      "port": 6014,
      "version": "gpu",
      "status": "healthy",
      "uptime": "Up 3d 18h",
      "restarts": 0,
      "memory": "1.8GB"
    }
  ]
}
```

---

## 3. Latency, Delay & Resilience Engineering

### Latency Profile
* **HTTP Transfer Latency: 5 – 15 ms (Instantaneous):**
  The static `status.json` payload is ~2 KB. In Linux, it resides in filesystem page cache (RAM), so Nginx serves it with sub-millisecond I/O.
* **Data Freshness Window: 15 – 30 seconds:**
  The host exporter runs on a 15–30s schedule. If a container stops or crashes, the UI detects it on the next poll cycle.
* **UI Re-render Latency: < 2 ms:**
  DOM updates occur immediately upon JSON parse without page reloads.

### Production Failure Protections (Guaranteed Zero Corruption)

1. **Atomic File Write (Prevents Race Conditions & Corrupted JSON):**
   * *Problem:* If the browser requests `status.json` at the exact millisecond `emit-status.sh` is writing, reading a partial file would cause `JSON.parse` syntax errors.
   * *Solution:* The script outputs to a temporary file, then performs an atomic rename:
     ```bash
     generate_json > /var/www/ops/status.json.tmp
     mv -f /var/www/ops/status.json.tmp /var/www/ops/status.json
     ```
     In Linux/POSIX filesystems, `mv` (the `rename(2)` syscall) is **atomic**. Readers never observe partial, truncated, or 0-byte states.

2. **Staleness Detection (Prevents Blind Outages):**
   * *Problem:* If cron or Docker daemon stalls, the dashboard could display old "All Healthy" data indefinitely.
   * *Solution:* Dashboard compares `Date.now() - Date.parse(data.generatedAt)`.
     If age exceeds **60 seconds**, the UI displays a warning banner:
     `⚠️ Telemetry Stale (Last seen 2m ago)` and switches the cluster mood to amber.

3. **Graceful Network Degradation & Retry:**
   * *Problem:* Client network drops or transient HTTP 502 during web server reload.
   * *Solution:* Client wraps requests in `try/catch`. On failure, it **retains the last known good state** on screen (never blanking the dashboard) and displays a subtle `⚠️ Reconnecting...` indicator while scheduling the next retry.

---

## 4. Phased Implementation Steps

### Phase 1: Exporter Script (`scripts/emit-status.sh`)
- **Task 1.1:** Create a bash script on `/home/aibot/BE/scripts/emit-status.sh` that queries:
  - `docker inspect --format='{{json .State}}' <container>`
  - Computes human-friendly uptime from `.State.StartedAt`.
  - Determines status: `healthy` (from Healthcheck if present, otherwise Running), `degraded`, or `down`.
- **Task 1.2:** Enforce atomic write pattern (`status.json.tmp` -> `mv -f`).
- **Task 1.3:** Schedule via cron:
  ```bash
  * * * * * /home/aibot/BE/scripts/emit-status.sh > /dev/null 2>&1
  * * * * * (sleep 30; /home/aibot/BE/scripts/emit-status.sh) > /dev/null 2>&1
  ```
- **Overhead:** < 10ms CPU per run; 0 requests hitting Java/Python application processes.

### Phase 2: Jenkins Pipeline Hook
- **Task 2.1:** In [be-pipeline.v2.groovy](file:///c:/Users/User/Desktop/ai-bot-v1/docs/jenkins/be-pipeline.v2.groovy):
  - When deployment begins: update `active.stage` in `/home/aibot/BE/status-deployment.json`.
  - On `post { success }`: append the build to `recent` array (keep top 3) and mark `stage: live`.
  - On `post { failure }`: record failed deployment event.

### Phase 3: Dashboard Integration
- **Task 3.1:** Update [aibot ops prototype.html](file:///c:/Users/User/Desktop/ai-bot-v1/ops-dashboard-prototype/aibot%20ops%20prototype.html) with:
  - `async function fetchLiveStatus()`:
    - Fetches `./status.json?t=` with timestamp cache buster.
    - Evaluates `generatedAt` age for staleness (> 60s threshold).
    - Merges live data into the SV array and triggers `render()` + `announceChanges()`.
  - Seamless fallback: If `./status.json` is unreachable (e.g. opened locally as a file), fallback gracefully to demo simulation mode.
  - Connection indicator badge (`Live Sync` vs `Demo Mode` vs `Reconnecting`).

### Phase 4: Hosting & Access
- **Option A (Recommended - Zero overhead):** Mount the static dashboard and `status.json` folder in an existing Nginx container or lightweight static container on a designated internal port (e.g. `http://103.182.211.219:6080`).
- **Option B:** Route via API Gateway (`/ops-dashboard/`).

### Phase 5: End-to-End Validation & Chaos Test
- **Test 1:** Stop one non-critical container (`docker stop screening-service`) -> Verify dashboard turns amber/red and projects the globe beacon within 30s.
- **Test 2:** Restart container -> Verify green recovery shockwave.
- **Test 3:** Run a test Jenkins deploy -> Verify active pipeline stages light up with running beam.
