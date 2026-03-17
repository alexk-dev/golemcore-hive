# Golemcore Hive Specification

Status: Draft v1
Date: 2026-03-17
Target repo: `golemcore-hive`
Primary runtime: Spring Boot 4.0 + Java 25 + Maven + Lombok
Frontend: React + TypeScript + Tailwind CSS 3

## 1. Summary

`golemcore-hive` is the orchestration and control-plane service for one or more `golemcore-bot` runtimes ("golems").

Recommended system shape:

- `golemcore-hive` is the operator-facing control plane.
- `golemcore-bot` remains the execution plane and owns actual agent sessions, tools, skills, and autonomous work.
- Registration starts from the `golemcore-bot` side.
- Hive manages work through a Kanban-style board, run history, chat switching, budgets, approvals, and audit trails.
- Hive sends commands to any registered golem through a secure outbound control channel initiated by the bot.

This keeps `golemcore-bot` focused on agent execution and lets Hive become the place where a human supervises a fleet.

Deployment model:

- one host,
- one tenant,
- self-hosted only,
- not a SaaS control plane.

## 2. Product Goal

Build a self-hosted orchestration service where an operator can:

- register one or more `golemcore-bot` instances,
- see which golems are online, healthy, and capable,
- create and manage work on a Kanban board,
- assign cards to specific golems,
- open a switchable chat with any golem or card-bound thread,
- inspect runs, logs, costs, artifacts, and decisions,
- enforce approvals, limits, and governance.

## 3. Why This Should Exist

`golemcore-bot` already has strong execution primitives:

- chat sessions,
- goals and tasks,
- schedules and autonomous runs,
- webhook ingress,
- dashboard and live runtime events.

What it does not provide yet is a fleet-level control plane with:

- cross-bot routing,
- one-board coordination,
- operator governance,
- multi-golem visibility,
- card-to-session traceability,
- unified audit and cost controls.

Hive fills that gap.

## 4. Design Principles

- Control plane / execution plane separation: Hive coordinates; bots execute.
- Bot-initiated connectivity: the bot should register and open the long-lived command channel.
- Secure by default: short-lived enrollment, JWT-based auth, scoped claims, rotation, and audit logging.
- Stateful work: cards, chats, runs, and artifacts must stay linked.
- Human override always available: pause, cancel, reassign, approve, escalate.
- Mobile-friendly supervision: the core operator workflows must work on smaller screens.
- Keep v1 operationally simple: local JSON persistence first, local filesystem artifacts second, no mandatory external queue.
- Single-tenant by design: avoid SaaS-style tenant/workspace complexity.

## 5. Approaches Considered

### Approach A. Hive as control plane, bot-initiated outbound registration and control channel

How it works:

- Bot starts with `HIVE_URL` and an enrollment secret.
- Bot registers itself in Hive.
- Bot opens an outbound authenticated WebSocket control channel to Hive.
- Hive pushes commands; bot streams events, heartbeats, chat chunks, and run state back.

Pros:

- Works behind NAT/firewalls.
- Cleaner trust boundary.
- No need to expose every bot publicly.
- Low-latency command and chat delivery.
- Best fit for switchable live chat.

Cons:

- Requires a dedicated Hive integration module in `golemcore-bot`.

Recommendation: use this in v1.

### Approach B. Hive calls bots directly over inbound HTTP APIs

Pros:

- Simpler to prototype.
- Easy to reason about initially.

Cons:

- Worse operator experience when bots are not publicly reachable.
- Harder secret rotation and reverse connectivity.
- Chat and event streaming become awkward.

Recommendation: do not use as the primary architecture.

### Approach C. Shared event bus plus offline workers

Pros:

- Scales well later.
- Good for large fleets.

Cons:

- Too much infrastructure for v1.
- Adds queue semantics before product semantics are proven.

Recommendation: reserve for v2 if fleet size or burst traffic demands it.

## 6. Recommended Architecture

### 6.1 System roles

- `golemcore-hive`
  - operator UI
  - orchestration API
  - board management
  - command routing
  - policy, audit, budgets, approvals
  - fleet health and presence

- `golemcore-bot`
  - agent execution
  - tool use
  - local sessions
  - skills, plans, goals, tasks
  - autonomous runs
  - event streaming back to Hive

### 6.2 Major subsystems in Hive

- Identity and access management
- Golem registry and enrollment
- Control channel manager
- Kanban and workflow engine
- Chat and thread service
- Command and run tracking
- Artifact and event timeline
- Audit and governance service
- Budget and quota service
- Notification and automation hooks

### 6.3 Major additions required in golemcore-bot

- Hive enrollment client
- Hive auth-state store
- Heartbeat publisher
- Control-channel client
- Command dispatcher that maps Hive commands to local bot actions
- Event publisher for chat chunks, run progress, usage, and artifacts
- Card/session binding metadata

### 6.4 Data ownership

Hive is the source of truth for:

- users and roles,
- golem registry,
- boards and cards,
- threads as seen by the operator,
- command records,
- run projections,
- budgets,
- approvals,
- audit events.

`golemcore-bot` is the source of truth for:

- local session state,
- local goal/task state,
- skill/runtime execution details,
- tool call internals,
- local autonomous scheduling behavior.

Rule:

- Hive should store projections and correlations from the bot.
- Hive should not write directly into the bot's local storage or database.

## 7. Functional Scope

### 7.1 Golem registration and lifecycle

Hive must support:

- creating short-lived enrollment tokens,
- registering a new golem from the bot side,
- approving or rejecting a registration,
- creating and managing golem roles,
- assigning many roles to one golem,
- removing roles from a golem,
- showing golem metadata:
  - name,
  - host label,
  - runtime version,
  - attached roles,
  - supported channels,
  - model/provider capabilities,
  - enabled tools,
  - enabled autonomous features,
- online/offline detection,
- revoke, rotate, pause, and decommission actions.

Required golem states:

- `PENDING_ENROLLMENT`
- `ONLINE`
- `DEGRADED`
- `OFFLINE`
- `PAUSED`
- `REVOKED`

Golem role model:

- golem roles are free-form slugs,
- one golem may have many roles,
- roles are created in Hive and assigned there,
- roles are used for filtering, board team construction, and assignment policy evaluation.

### 7.2 Secure enrollment and JWT issuance

Minimum acceptable v1 flow:

1. Operator creates an enrollment token in Hive.
2. Bot starts with:
   - `HIVE_URL`
   - `HIVE_ENROLLMENT_TOKEN`
   - optional `GOLEM_DISPLAY_NAME`
3. Bot calls `POST /api/v1/golems/register` with:
   - enrollment token,
   - bot metadata/capabilities,
   - bot version/build info.
4. Hive returns:
   - `golemId`,
   - JWT `accessToken`,
   - JWT `refreshToken`,
   - token expiration metadata,
   - issuer and audience metadata,
   - control-channel URL,
   - heartbeat interval,
   - allowed scopes/claims.
5. Bot stores tokens locally.
6. Bot opens the outbound control channel using the JWT access token.
7. Bot refreshes expired access tokens through a refresh endpoint, similar to `golemcore-bot` dashboard auth.

Important note:

- The bootstrap secret is the short-lived enrollment token.
- Persistent auth after enrollment should be JWT-based, not long-lived static access keys.
- Match the existing `golemcore-bot` model:
  - short-lived access JWT,
  - longer-lived refresh JWT,
  - typed claims,
  - HMAC signing with a configured server secret.
- Browser operator auth should mirror `golemcore-bot`:
  - access token in `Authorization: Bearer`,
  - refresh token in `HttpOnly` secure cookie.
- Bot auth should also use JWT, but refresh token storage remains bot-local and never appears in the UI.
- v1.1 can add mTLS if stronger machine-to-machine trust is needed.

### 7.3 Heartbeats, presence, and telemetry

Each online golem must periodically send:

- status,
- current run state,
- current card/thread bindings,
- model tier in use,
- token/cost counters,
- queue depth,
- health summary,
- last error summary,
- uptime,
- capability snapshot hash.

Hive must:

- mark a golem `DEGRADED` after missed heartbeat threshold,
- mark it `OFFLINE` after a harder timeout,
- surface "stale run" and "stale card assignment" warnings,
- show last-seen and last-successful-command timestamps.

### 7.4 Kanban board

Hive needs a first-class Kanban board, not a flat task list.

Minimum board features:

- multiple boards per installation,
- per-board team definitions,
- board-specific flow definitions,
- per-board custom columns and transition rules,
- starter flow templates, for example:
  - engineering flow,
  - content flow,
  - support flow,
  - research flow,
- drag-and-drop cards,
- board filters by:
  - golem,
  - golem role,
  - status,
  - tag,
  - priority,
  - board,
  - unread activity,
- WIP limits for selected columns,
- card dependencies,
- card priority and due date,
- tags and saved views.

Each card must support:

- title,
- description,
- operator notes,
- acceptance criteria,
- attachments/links,
- priority,
- assignee golem,
- watchers,
- related thread,
- related run history,
- related artifacts,
- related `goalId` / `taskId` / `sessionId` in the bot when present.

Board team model:

- each board may define a `BoardTeam`,
- board team membership supports:
  - explicit golem selection by name,
  - dynamic inclusion by golem role slug,
- board team is the primary source for fast assignment and board-local routing.

Recommended v1 card types:

- `TASK`
- `BUG`
- `INCIDENT`
- `RESEARCH`
- `REVIEW`
- `AUTOMATION`

Board flow rules:

- each board owns its own column set,
- each board may define allowed transitions,
- each board may define its own WIP limits,
- board flow definitions may be edited after cards already exist,
- editing a flow with existing cards must support explicit remapping for affected columns,
- board flow definitions must also define how structured work signals map to columns,
- cards do not rely on one global status model shared by all boards.

Assignment UI rules:

- assignee picker must have two tabs:
  - `Team` for quick selection from the board team,
  - `All` for selection from the complete golem registry,
- `Team` tab should support quick filtering by name and role,
- `All` tab should support the same filters plus global search.

### 7.4.1 Card status interpretation and transition authority

Hive must not infer card state by parsing free-form assistant text.

High-level rule:

- Hive owns the authoritative card column.
- `golemcore-bot` emits structured lifecycle signals.
- board flow policy maps signals to transition decisions.
- operator moves remain manual overrides.

Detailed contract:

- signal schema,
- event batch shape,
- transition resolution algorithm,
- default signal-to-column mappings,
- blocker and completion handling,
- audit model,
- flow remap behavior

are defined in [docs/card-lifecycle-signals.md](docs/card-lifecycle-signals.md).

### 7.5 Chat and switchable conversation UX

Hive must allow the operator to chat with any golem.

Required UX:

- left sidebar with all golems and unread badges,
- thread list scoped to card-bound conversations,
- card thread shown as the canonical thread for that card,
- central chat panel with live streamed replies,
- one composer bound to the currently selected golem/thread,
- fast switch between conversations without losing draft state,
- show whether the thread is:
  - operator-only,
  - card-bound,
  - autonomous run thread,
  - escalated incident thread.

Required chat behavior:

- operator can send a command to any golem,
- operator can resume an existing thread,
- new work-tracking conversations must start from a card,
- operator sees run progress and assistant streaming,
- operator sees system/runtime events inline,
- operator can cancel an in-flight run,
- operator can move from card to thread and back in one click.

Task-tracking rule:

- all work items in Hive go through cards,
- free-form task threads outside a card are not part of v1.

### 7.6 Commands Hive can send to a golem

V1 command set:

- `SEND_MESSAGE`
- `WAKE`
- `START_CARD`
- `CONTINUE_CARD`
- `RUN_TASK`
- `SUMMARIZE_STATUS`
- `CANCEL_RUN`
- `PAUSE_AUTONOMY`
- `RESUME_AUTONOMY`
- `COMPACT_SESSION`
- `SYNC_STATE`

When a command is card-bound, the bot must treat that card as the active work context and emit lifecycle signals against the same `cardId`.

Each command must have:

- `commandId`
- `targetGolemId`
- optional `cardId`
- optional `threadId`
- payload
- requestedBy
- priority
- timeout
- approval policy result
- lifecycle:
  - `QUEUED`
  - `DISPATCHED`
  - `ACKNOWLEDGED`
  - `RUNNING`
  - `SUCCEEDED`
  - `FAILED`
  - `CANCELLED`
  - `TIMED_OUT`

### 7.7 Runs, artifacts, and timelines

Hive should model long-running work explicitly.

Each run must have:

- run id,
- golem id,
- origin command,
- bound card/thread,
- start/end times,
- status,
- token/cost usage,
- summary,
- failure reason,
- artifacts.

Artifact types:

- text summary,
- patch/diff,
- log bundle,
- report,
- screenshot,
- file reference,
- structured JSON output.

Every card, thread, and run must expose a unified timeline combining:

- operator messages,
- golem messages,
- command dispatches,
- runtime events,
- approvals,
- board status changes,
- artifacts,
- errors.

### 7.8 Governance, approvals, and budgets

Features to add from day one:

- role-based access for human operators:
  - `ADMIN`
  - `OPERATOR`
  - `VIEWER`
- separate golem work-role model, for example:
  - `developer`
  - `reviewer`
  - `researcher`
  - `ops`
  - `support`
- immutable audit log for sensitive actions,
- per-golem monthly budget,
- optional per-card budget,
- hard stop when limits are exceeded,
- approval gates for:
  - destructive commands,
  - commands above cost threshold.

### 7.9 Search, filters, and history

Hive must support:

- search across cards, threads, golems, and runs,
- filtering by online state and health,
- "show me everything assigned to bot X",
- "show me blocked cards with unread chat",
- archived cards and runs,
- replay of command/run/audit history.

### 7.10 Automation hooks

V1 should include lightweight automations:

- when card moves to `In Progress`, send `START_CARD`,
- when card moves to `Review`, request summary and attach artifacts,
- when golem goes offline, flag assigned cards,
- when budget nears threshold, notify operator.

Integrations like GitHub, Slack, or Jira are useful but should be v1.1 unless one is required immediately.

### 7.11 Assignment policies

Assignment must be modeled as a first-class policy domain, even if manual assignment is the default.

Supported policy modes:

- `MANUAL`
- `SUGGESTED`
- `AUTOMATIC`

V1 runtime behavior:

- manual assignment is the default and always available,
- suggested assignment may recommend candidate golems based on capability, roles, and load,
- automatic assignment must only run when explicitly enabled for a board or card class.

Policy extensibility:

- assignment policies should support skill-like definitions that describe:
  - routing conditions,
  - required capabilities,
  - required or preferred golem roles,
  - exclusions,
  - tie-break rules,
  - fallback behavior.
- this allows future "assignment skills" without redesigning the data model.

## 8. Domain Model

Core entities:

- `InstanceSettings`
- `User`
- `RoleBinding`
- `GolemRole`
- `GolemRoleBinding`
- `EnrollmentToken`
- `Golem`
- `GolemAuthSession`
- `HeartbeatSnapshot`
- `CapabilitySnapshot`
- `Board`
- `BoardTeam`
- `BoardTeamFilter`
- `BoardFlowDefinition`
- `BoardColumn`
- `Card`
- `CardAssignment`
- `CardLifecycleSignal`
- `CardTransitionEvent`
- `AssignmentPolicy`
- `Thread`
- `Message`
- `Command`
- `Run`
- `Artifact`
- `BudgetPolicy`
- `ApprovalRequest`
- `AuditEvent`
- `Notification`

Important cross-links:

- one `Card` owns exactly one canonical `Thread` once work begins,
- one `Golem` may have many `GolemRoleBinding` records,
- one `Board` may have one `BoardTeam`,
- one `Card` may have many `CardLifecycleSignal` records,
- one `Run` belongs to one `Command`,
- one `Run` may produce many `Artifacts`,
- one `Card` may map to one bot `goalId` and/or `taskId`,
- one `Thread` may map to one bot `sessionId`,
- one `Golem` has many `HeartbeatSnapshot` and `CapabilitySnapshot` records.

## 9. API Surface

Suggested API prefix: `/api/v1`

### 9.1 Enrollment and fleet

- `POST /api/v1/enrollment-tokens`
- `GET /api/v1/enrollment-tokens`
- `POST /api/v1/golems/register`
- `POST /api/v1/golems/{golemId}/heartbeat`
- `POST /api/v1/golems/{golemId}/events:batch`
- `POST /api/v1/golems/{golemId}/auth:rotate`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/golems/{golemId}:pause`
- `POST /api/v1/golems/{golemId}:resume`
- `POST /api/v1/golems/{golemId}:revoke`
- `GET /api/v1/golems`
- `GET /api/v1/golems/{golemId}`

### 9.2 Boards and cards

- `GET /api/v1/boards`
- `POST /api/v1/boards`
- `GET /api/v1/boards/{boardId}`
- `PATCH /api/v1/boards/{boardId}`
- `PUT /api/v1/boards/{boardId}/flow`
- `PUT /api/v1/boards/{boardId}/team`
- `POST /api/v1/cards`
- `GET /api/v1/cards/{cardId}`
- `PATCH /api/v1/cards/{cardId}`
- `POST /api/v1/cards/{cardId}:move`
- `POST /api/v1/cards/{cardId}:transition`
- `POST /api/v1/cards/{cardId}:assign`
- `POST /api/v1/cards/{cardId}:archive`

### 9.3 Chat and threads

- `GET /api/v1/threads`
- `POST /api/v1/threads`
- `GET /api/v1/threads/{threadId}`
- `GET /api/v1/threads/{threadId}/messages`
- `POST /api/v1/threads/{threadId}/messages`
- `POST /api/v1/threads/{threadId}:bind-card`

### 9.4 Commands and runs

- `POST /api/v1/commands`
- `GET /api/v1/commands/{commandId}`
- `POST /api/v1/commands/{commandId}:cancel`
- `GET /api/v1/runs`
- `GET /api/v1/runs/{runId}`
- `GET /api/v1/runs/{runId}/artifacts`

Signal ingestion is part of golem event ingestion:

- `POST /api/v1/golems/{golemId}/events:batch`
  - accepts runtime events and card lifecycle signals in one envelope

### 9.5 Golem roles and assignment policies

- `GET /api/v1/golem-roles`
- `POST /api/v1/golem-roles`
- `PATCH /api/v1/golem-roles/{roleId}`
- `POST /api/v1/golems/{golemId}/roles:assign`
- `POST /api/v1/golems/{golemId}/roles:unassign`
- `GET /api/v1/boards/{boardId}/team`
- `GET /api/v1/assignment-policies`
- `POST /api/v1/assignment-policies`
- `PATCH /api/v1/assignment-policies/{policyId}`

### 9.6 Realtime channels

- Browser realtime:
  - `GET /ws/operator`
- Golem control channel:
  - `GET /ws/golems/control`

WebSocket is preferred for:

- operator chat streaming,
- command delivery,
- heartbeat acknowledgements,
- runtime event fan-out.

## 10. Bot-Side Contract

The integration should be implemented inside `golemcore-bot`, not as an external sidecar.

Required local responsibilities in the bot:

- persist Hive auth state and registration state,
- open and maintain the Hive control channel,
- map incoming Hive commands to existing bot primitives:
  - sessions,
  - goals/tasks,
  - auto mode,
  - webhook-style agent execution,
- publish message chunks and run events back to Hive,
- expose stable metadata so Hive can correlate:
  - `sessionId`
  - `goalId`
  - `taskId`
  - `runId`
  - current model tier
  - current card binding

Recommended bot configuration:

- `HIVE_ENABLED`
- `HIVE_URL`
- `HIVE_ENROLLMENT_TOKEN`
- `HIVE_HEARTBEAT_INTERVAL`
- `HIVE_CONTROL_CHANNEL_RECONNECT_BACKOFF`
- `HIVE_JWT_REFRESH_SKEW_SECONDS`

## 11. Frontend Specification

Recommended frontend stack:

- React 19
- TypeScript
- Vite
- Tailwind CSS 3
- React Router
- TanStack Query
- Zustand for transient UI state
- `dnd-kit` for board drag-and-drop

Required screens:

- Login
- Fleet overview
- Golem detail
- Boards
- Card detail drawer/page
- Chat workspace
- Runs and artifacts
- Audit log
- Settings and enrollment tokens

Required card assignment UX:

- assignee selector with `Team` and `All` tabs,
- `Team` tab optimized for fast board-local assignment,
- `All` tab for fallback selection from the full registry,
- filters by golem name and golem role in both tabs.

Required card status UX:

- show the current board column and the last lifecycle signal separately,
- show whether the latest move was manual or signal-driven,
- show blocker reason inline when the latest effective signal is `BLOCKER_RAISED`,
- show completion evidence inline when the latest effective signal is `WORK_COMPLETED`.

Required UI qualities:

- desktop-first but mobile-usable,
- unread indicators,
- optimistic board moves,
- live chat streaming,
- sticky command composer,
- keyboard switcher for golem/thread search,
- clear online/offline/blocked visual states.

## 12. Backend Specification

Recommended backend stack:

- Spring Boot 4.0
- Java 25 with virtual threads enabled
- Maven
- Lombok
- Spring WebFlux for REST and realtime alignment with `golemcore-bot`
- Spring WebSocket for realtime channels
- Spring Security
- Bean Validation
- Jackson-based JSON persistence layer
- local filesystem storage adapter

Storage:

- no database in v1,
- local JSON files for durable state, following the same operational model as `golemcore-bot`,
- local filesystem directories for artifacts and attachments,
- append-only JSONL where it is useful for audit/event timelines.

Persistence requirements:

- atomic writes via temp-file + replace,
- optimistic versioning or file locks for concurrent updates,
- startup validation and repair for malformed or partially written files,
- explicit separation by domain folder, for example:
  - `auth/`
  - `golems/`
  - `boards/`
  - `threads/`
  - `runs/`
  - `audit/`

Why not add a DB immediately:

- the target deployment is one host / one tenant,
- `golemcore-bot` already proves the local JSON storage model is workable,
- v1 complexity is orchestration semantics, not distributed storage.

## 13. Security Requirements

- TLS only.
- Enrollment tokens must be one-time or short TTL.
- Bot auth tokens must be scoped and rotatable.
- Hive commands must be signed or nonce-protected.
- Heartbeat/event ingestion must reject replayed or stale envelopes.
- Audit events must be append-only.
- Secrets must never be rendered in operator UI after creation.
- Public API and golem API scopes must be separated.
- All sensitive actions must record actor, target, timestamp, and reason.
- JWT secrets must be explicitly configurable in production.
- Access tokens should be short-lived; refresh tokens should be longer-lived.
- Golem JWTs must carry claim/scopes sufficient for machine authorization without a separate machine-RBAC model.

Authentication recommendation:

- operator auth: JWT access token plus refresh JWT, matching `golemcore-bot` dashboard behavior,
- golem auth: service JWT access token plus refresh JWT minted by Hive after enrollment,
- authorization: RBAC for users and claim-based scopes for golems,
- future hardening: mTLS for golem channels.

## 14. Observability

Hive must expose:

- golem presence dashboard,
- command latency,
- run latency,
- failed command rate,
- missed heartbeat count,
- per-golem token/cost trends,
- board throughput metrics,
- audit trail explorer.

Useful derived metrics:

- lead time by card type,
- cycle time by board,
- time spent blocked,
- cost per card,
- cost per golem,
- operator-to-run approval delay.

## 15. Non-Goals for v1

- full workflow builder,
- arbitrary multi-step DAG engine,
- direct code editing IDE,
- bring-your-own-ticket-system synchronization,
- multi-tenant SaaS hosting,
- multi-region active/active deployment,
- enterprise SSO beyond a clean future extension point.

## 16. Phased Delivery

### Phase 1. Fleet registration and visibility

- enrollment tokens
- bot registration
- heartbeats
- golem list/detail
- token rotation

### Phase 2. Board and command MVP

- one board
- cards
- assignment
- `START_CARD` / `SEND_MESSAGE`
- golem chat
- run tracking

### Phase 3. Governance and polish

- budgets
- approvals
- audit explorer
- artifacts
- mobile-friendly layouts

### Phase 4. Automation and integrations

- card automations
- notifications
- GitHub/Slack/Jira class integrations

## 17. Success Criteria

- A new golem can enroll in under 5 minutes.
- An operator can create a card and assign it in under 30 seconds.
- Live chat starts streaming within 1 second after command dispatch in healthy conditions.
- Offline golems are detected within 2 missed heartbeat intervals.
- Every card shows its related thread, latest run, and last artifact without manual lookup.
- Every destructive or high-cost action is auditable.

## 18. Inputs That Informed This Spec

- current `golemcore-bot` capabilities:
  - sessions,
  - goals/tasks,
  - dashboard auth,
  - webhooks,
  - runtime event streaming,
- common patterns from current open-source orchestration and agent-control systems:
  - persistent threads and runs,
  - heartbeats and fleet presence,
  - governance and audit trails,
  - board-driven supervision,
  - budget enforcement,
  - approval gates.

## 19. Recommendation

Build v1 around:

- Hive-managed boards, threads, runs, and audit,
- bot-initiated secure registration,
- outbound bot control channel,
- a minimal but strong command set,
- explicit card/thread/run linking,
- budgets and approvals from the start.

That gives Hive a clear product identity: not another chatbot UI, and not a duplicate of `golemcore-bot`, but the operational cockpit for a fleet of golems.
