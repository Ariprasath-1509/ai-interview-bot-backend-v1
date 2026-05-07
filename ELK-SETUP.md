# ELK Stack for Bench Readiness - Centralized Logging

## Overview

Centralized logging using Elasticsearch, Logstash, and Kibana integrated into the main docker-compose.yml for all 8 microservices with structured JSON logs, distributed tracing, and user context.

## Architecture

```
Microservices (8 services)
    ↓ (Logback → Logstash TCP appender)
Logstash (6010) — parse, enrich, filter
    ↓
Elasticsearch (9200) — index and store
    ↓
Kibana (5601) — visualize and search
```

## Features

✅ **Structured JSON logging** — all logs in JSON format for easy parsing  
✅ **Distributed tracing** — X-Trace-Id propagates across all services  
✅ **User context** — userId, userRole, userEmail in every log  
✅ **Service identification** — serviceName in every log entry  
✅ **Async logging** — non-blocking, won't slow down services  
✅ **Centralized search** — query all services from Kibana  
✅ **Real-time monitoring** — live log streaming  
✅ **90-day retention** — automatic cleanup  

## Setup

### 1. Start All Services (including ELK)

```bash
cd /home/ariprasath/IdeaProjects/BenchReadiness
docker-compose up -d
```

This will start:
- **ELK Stack**: Elasticsearch (9200), Logstash (6010), Kibana (5601)
- **Eureka Server** (6009)
- **All 7 Microservices**: compliance, auth, interview, ai, observer, review, api-gateway

### 2. Verify ELK is Running

```bash
# Check Elasticsearch
curl http://localhost:9200

# Check Kibana (wait 30s for startup)
curl http://localhost:5601/api/status

# Check Logstash
curl http://localhost:9600
```

### 3. Configure Kibana Data View (Index Pattern)

1. Open Kibana: http://localhost:5601
2. Go to **Stack Management** → **Kibana** → **Data Views**
3. Click **Create data view**
4. Name: `bench-logs`, Index pattern: `bench-logs-*`
5. Select timestamp field: `@timestamp`
6. Click **Save data view to Kibana**

### 4. View Logs

1. Go to **Discover** in Kibana
2. Select `bench-logs-*` index pattern
3. Logs from all services will appear in real-time

## Log Structure

Each log entry contains:

```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger_name": "com.benchreadiness.interview.controller.InterviewController",
  "message": "Creating interview for candidate john@example.com",
  "serviceName": "interview-service",
  "traceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": "123",
  "userRole": "ADMIN",
  "userEmail": "admin@benchreadiness.com",
  "thread_name": "http-nio-6006-exec-1",
  "stack_trace": "..." // only for errors
}
```

## Kibana Queries

### Search by Service

```
serviceName: "interview-service"
```

### Search by User

```
userEmail: "admin@benchreadiness.com"
```

### Search by Trace ID (track request across services)

```
traceId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

### Find Errors

```
level: "ERROR"
```

### Find AI Operations

```
serviceName: "ai-service" AND message: *Claude*
```

### Find Authentication Events

```
serviceName: "auth-service" AND (message: *login* OR message: *register*)
```

### Find Interview Lifecycle

```
serviceName: "interview-service" AND (message: *created* OR message: *completed* OR message: *signed*)
```

## Distributed Tracing

Every request gets a unique `X-Trace-Id` generated at the API Gateway. This ID propagates through all services via:

1. **API Gateway** — generates trace ID, adds to MDC, forwards in header
2. **Service MDC Filter** — extracts trace ID from header, adds to MDC
3. **Feign Interceptor** — propagates trace ID to downstream service calls
4. **Logstash** — includes trace ID in every log entry

**Example trace flow:**

```
API Gateway (traceId: abc-123)
  → Interview Service (traceId: abc-123)
    → AI Service (traceId: abc-123)
      → Compliance Service (traceId: abc-123)
```

Search Kibana with `traceId: "abc-123"` to see the complete request flow.

## Service Startup Order

Docker Compose handles dependencies automatically:

1. **Elasticsearch** starts first (with health check)
2. **Logstash** waits for Elasticsearch to be healthy
3. **Kibana** waits for Elasticsearch to be healthy
4. **Eureka Server** starts (with health check)
5. **All microservices** wait for Eureka + Logstash to be ready

## Troubleshooting

### Logs not appearing in Kibana

```bash
# Check all containers are running
docker-compose ps

# Check Logstash logs
docker logs logstash

# Check if services can connect to Logstash
docker exec -it interview-service nc -zv logstash 6010
```

### Elasticsearch out of disk space

```bash
# Check disk usage
curl http://localhost:9200/_cat/indices?v

# Delete old indices manually
curl -X DELETE http://localhost:9200/bench-logs-2024.01.01
```

### Restart specific service

```bash
# Restart just Logstash
docker-compose restart logstash

# Restart all ELK services
docker-compose restart elasticsearch logstash kibana

# Restart a microservice
docker-compose restart interview-service
```

### View service logs

```bash
# View logs from a specific service
docker-compose logs -f interview-service

# View logs from all services
docker-compose logs -f

# View only ELK logs
docker-compose logs -f elasticsearch logstash kibana
```

## Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (deletes all logs)
docker-compose down -v
```

## Configuration

### Logstash Host Configuration

Services connect to Logstash via Docker network. The logback-spring.xml uses:

```xml
<springProperty scope="context" name="logstashHost" source="logstash.host" defaultValue="localhost"/>
<springProperty scope="context" name="logstashPort" source="logstash.port" defaultValue="6010"/>
```

For Docker deployment, add to each service's environment in docker-compose.yml:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
  LOGSTASH_HOST: logstash
  LOGSTASH_PORT: 6010
```

### Change Log Levels

Update `logback-spring.xml` in each service:

```xml
<root level="INFO">
  <appender-ref ref="CONSOLE"/>
  <appender-ref ref="ASYNC_LOGSTASH"/>
</root>

<logger name="com.benchreadiness" level="DEBUG"/>
```

### Disable Logstash Logging

Remove or comment out the `ASYNC_LOGSTASH` appender reference in `logback-spring.xml`:

```xml
<root level="INFO">
  <appender-ref ref="CONSOLE"/>
  <!-- <appender-ref ref="ASYNC_LOGSTASH"/> -->
</root>
```

## Performance

- **Async appenders** — logs sent to Logstash asynchronously, no blocking
- **Queue size**: 512 entries per service
- **Discard threshold**: 0 (never discard logs)
- **Keep-alive**: 5 minutes (persistent TCP connections)
- **Elasticsearch memory**: 512MB heap
- **Logstash memory**: 256MB heap

## Network Architecture

All services communicate via `bench-network` Docker bridge network:

```
bench-network (bridge)
  ├── elasticsearch:9200
  ├── logstash:6010
  ├── kibana:5601
  ├── eureka-server:6009
  ├── compliance-service:6005
  ├── auth-service:6004
  ├── interview-service:6006
  ├── ai-service:6003
  ├── observer-service:6007
  ├── review-service:6008
  └── api-gateway:6002
```

Services can reference each other by container name (e.g., `http://logstash:6010`).

## Dashboard Examples

### Service Health Dashboard

- Error rate per service (last 24h)
- Request count per service
- Average response time

### User Activity Dashboard

- Requests by user role
- Top active users
- Authentication events timeline

### Interview Flow Dashboard

- Interviews created vs completed
- Average interview duration
- Sign-off rate

### AI Operations Dashboard

- Claude API calls per hour
- Token usage trends
- Assessment duration distribution

Create these dashboards in Kibana → **Dashboard** → **Create dashboard** → **Add visualization**.

## Quick Commands

```bash
# Start everything
docker-compose up -d

# View all logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f interview-service

# Restart a service
docker-compose restart interview-service

# Stop everything
docker-compose down

# Stop and delete all data
docker-compose down -v

# Check service health
docker-compose ps
```
