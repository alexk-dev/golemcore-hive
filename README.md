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
- approval gates for `SelfEvolving` promotion decisions
- audit history, budget snapshots, notification events, and production guardrails
- readonly per-golem `SelfEvolving` inspection fed by connected bot projections

## SelfEvolving inspection

When a connected `golemcore-bot` has `SelfEvolving` enabled, Hive exposes readonly per-golem inspection for:

- projected runs and judge verdict summaries
- candidate queue and promotion states
- lineage nodes
- artifact workspace catalog, lineage rail, diff, evidence, and impact tabs mirrored from the bot
- benchmark campaigns tied to the selected artifact stream
- promotion approvals

`golemcore-bot` stays the primary working screen. Hive keeps these views inside the existing inspection surface instead of creating a second golem dashboard and mirrors the bot workspace as readonly state.

Artifact identity and compare rules:

- `artifactStreamId` is the canonical identity across bot and Hive
- `artifactKey` and aliases are display metadata only
- per-golem workspace projections are mirrored verbatim from the bot
- Hive derives only the fleet-level same-stream compare read model
- bounded rollout diffs are mirrored when available, while arbitrary revision compare may fall back to on-demand content diff from mirrored normalized revisions

Hive also exposes fleet-level artifact search and same-stream compare across golems, while keeping approval actions inside the existing governance flow.

Relevant APIs:

- `GET /api/v1/self-evolving/golems/{golemId}/runs`
- `GET /api/v1/self-evolving/golems/{golemId}/runs/{runId}`
- `GET /api/v1/self-evolving/golems/{golemId}/candidates`
- `GET /api/v1/self-evolving/golems/{golemId}/lineage`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}/lineage`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}/diff`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}/transition-diff`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}/evidence`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}/compare-evidence`
- `GET /api/v1/self-evolving/golems/{golemId}/artifacts/{artifactStreamId}/transition-evidence`
- `GET /api/v1/self-evolving/artifacts/search`
- `GET /api/v1/self-evolving/artifacts/compare`
- `GET /api/v1/approvals?golemId={golemId}`

Promotion approvals are generalized through the normal approvals system with `subjectType=SELF_EVOLVING_PROMOTION`.

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

## Releases

Pushes to `main` run the conventional release workflow. When releasable commits are present, Hive:

- creates the next `v*` tag with `cocogitto`,
- builds the packaged Spring Boot jar,
- publishes `hive-*.jar` and `sha256sums.txt` to the GitHub Release for that tag,
- triggers container publication to `ghcr.io`.

Published image tags:

- branch pushes outside `main`: short SHA only
- `main`: `latest` and short SHA
- release tags `v*`: `<version>`, `latest`, and short SHA

Example:

```bash
docker pull ghcr.io/<owner>/golemcore-hive:latest
```

## Production checklist

1. Copy `application-prod.example.yml` into your deployment config and replace secrets.
2. Set a non-empty `hive.security.jwt.secret`.
3. Enable `hive.security.cookie.secure=true`.
4. Change the bootstrap admin password or disable bootstrap admin creation.
5. Set `hive.deployment.production-mode=true`.

When production mode is enabled, Hive fails fast if the JWT secret is missing, refresh cookies are not secure, or the bootstrap password still uses the local default.
