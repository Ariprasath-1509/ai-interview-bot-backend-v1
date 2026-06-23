# API Contracts

The backend is the **single source of truth** for domain models. The frontend must not duplicate models via Prisma or local DB access.

## OpenAPI documentation

| Service | Swagger UI (local) | OpenAPI JSON |
|---------|-------------------|--------------|
| auth-service | http://localhost:6004/swagger-ui.html | http://localhost:6004/v3/api-docs |
| questionbank-service | http://localhost:6016/swagger-ui.html | http://localhost:6016/v3/api-docs |

Via gateway, auth routes are prefixed: `http://localhost:6002/api/auth/...` (rewritten to `/auth/...`).

## Auth API contract (public)

### POST `/auth/login`

**Request:** `LoginRequest` — `username` (email), `password`, optional `role`

**Success (200):** `AuthTokenResponse`

```json
{
  "ok": true,
  "token": "<access-jwt>",
  "refreshToken": "<refresh-jwt>",
  "expiresIn": 1800,
  "role": "CANDIDATE",
  "name": "Jane Doe",
  "adminSource": "B2B"
}
```

### POST `/auth/refresh`

**Request:** `RefreshTokenRequest` — `refreshToken`

**Success (200):** `AuthTokenResponse` (role/name may be omitted)

### POST `/auth/logout`

**Headers:** `Authorization: Bearer <access-token>` (optional)

**Body:** `LogoutRequest` — optional `refreshToken`

**Success (200):** `ApiSuccessResponse` — `{ "ok": true }`

## Contract tests

Backend contract tests live in `auth-service/src/test/.../AuthApiContractTest.java` and validate response DTOs against `contracts/auth-public-api.json`.

## Frontend type generation (recommended)

```bash
# From a running auth-service
npx openapi-typescript http://localhost:6004/v3/api-docs -o src/types/auth-api.ts
```

Regenerate types when backend DTOs change. Do not maintain parallel Prisma models for backend-owned data.
