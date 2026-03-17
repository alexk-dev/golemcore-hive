# Golemcore Hive

Self-hosted orchestration and control plane for `golemcore-bot` runtimes.

## Stack

- Spring Boot 4.0
- Java 25
- Maven
- React + TypeScript
- Tailwind CSS 3
- Local JSON persistence

## What is implemented

- operator auth with access JWT + refresh JWT cookie
- bot enrollment, machine JWT rotation, fleet registry, roles, and heartbeats
- board flows, board teams, cards, card-bound threads, and command dispatch
- lifecycle signal ingestion from golems
- approval gates for destructive or high-cost commands
- audit history, budget snapshots, notification events, and production guardrails

## Local development

### Backend

```bash
./mvnw test
./mvnw spring-boot:run
```

### Frontend

```bash
cd ui
nvm use
npm ci
npm run test
npm run build
npm run dev
```

The Vite dev server proxies `/api` and `/ws` to the backend on `http://localhost:8080`.

Default bootstrap operator:

- username: `admin`
- password: `change-me-now`

Override bootstrap credentials in `src/main/resources/application.yml` or environment-backed Spring properties before using the app outside local development.

## Storage layout

Hive stores state under `hive.storage.base-path`, defaulting to `./data/hive`.

Important directories:

- `operators/`
- `auth/refresh-sessions/`
- `auth/golem-refresh-sessions/`
- `golems/`
- `golem-roles/`
- `enrollment-tokens/`
- `heartbeats/`
- `boards/`
- `cards/`
- `threads/`
- `thread-messages/`
- `commands/`
- `runs/`
- `lifecycle-signals/`
- `approvals/`
- `audit/`
- `budgets/`
- `notifications/`

## Packaging

Package the backend and built frontend together:

```bash
nvm use
./mvnw package
```

`prepare-package` runs `npm ci`, `npm run build`, and copies `ui/dist` into the Spring Boot jar as static assets.

If you need a backend-only package during local debugging:

```bash
./mvnw -Dskip.frontend=true package
```

## Production checklist

1. Copy `application-prod.example.yml` into your deployment config and replace secrets.
2. Set a non-empty `hive.security.jwt.secret`.
3. Enable `hive.security.cookie.secure=true`.
4. Change the bootstrap admin password or disable bootstrap admin creation.
5. Set `hive.deployment.production-mode=true`.

When production mode is enabled, Hive fails fast if the JWT secret is missing, refresh cookies are not secure, or the bootstrap password still uses the local default.
