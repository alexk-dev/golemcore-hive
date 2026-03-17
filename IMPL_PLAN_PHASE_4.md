# Golemcore Hive Phase 4 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement card-bound chat, command dispatch, inbound bot events, and lifecycle-driven board automation.

**Architecture:** Every operator conversation goes through a card-bound thread. Hive keeps the operator-facing thread and command history, while `golemcore-bot` executes work and reports back through the control/event contracts. Lifecycle state changes are driven by structured signals defined in `docs/card-lifecycle-signals.md`, not by text parsing. Hive remains the only owner of card columns.

**Tech Stack:** Spring Boot 4.0 WebFlux, Java 25, Maven, Lombok, WebSocket support, React, TypeScript, Tailwind CSS 3, TanStack Query.

---

## Phase Result

At the end of Phase 4:

- operators can open a card thread and send commands to the assigned golem,
- Hive persists commands, messages, and run projections,
- bots can push events and lifecycle signals into Hive,
- board status updates can be auto-applied or suggested from structured signals,
- operators can see live thread/runs context in the UI.

## File Structure

**Backend**

- Create: `src/main/java/me/golemcore/hive/domain/model/ThreadRecord.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/ThreadMessage.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/CommandRecord.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/RunProjection.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/CardLifecycleSignal.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/CardTransitionEvent.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/ThreadService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/CommandDispatchService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/EventIngestionService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/CardTransitionService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/SignalResolutionService.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/ThreadsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/CommandsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemEventsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/ws/GolemControlChannelHandler.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/ws/OperatorUpdatesHandler.java`

**Frontend**

- Create: `ui/src/features/chat/CardThreadPage.tsx`
- Create: `ui/src/features/chat/ThreadMessageList.tsx`
- Create: `ui/src/features/chat/ThreadComposer.tsx`
- Create: `ui/src/features/chat/GolemSwitcher.tsx`
- Create: `ui/src/features/runs/RunTimeline.tsx`
- Create: `ui/src/features/runs/SignalBadge.tsx`
- Create: `ui/src/features/runs/TransitionSuggestionBanner.tsx`
- Create: `ui/src/lib/api/threadsApi.ts`
- Create: `ui/src/lib/api/commandsApi.ts`
- Create: `ui/src/lib/api/eventsApi.ts`

**Persistence layout**

- Create: `data/hive/threads/<thread-id>.json`
- Create: `data/hive/thread-messages/<message-id>.json`
- Create: `data/hive/commands/<command-id>.json`
- Create: `data/hive/runs/<run-id>.json`
- Create: `data/hive/lifecycle-signals/<signal-id>.json`
- Create: `data/hive/card-transitions/<transition-id>.json`

## Implementation Tasks

### Task 1: Implement card-bound thread and command models

- [ ] Model `ThreadRecord`, `ThreadMessage`, `CommandRecord`, and `RunProjection`.
- [ ] Ensure every card can resolve exactly one canonical thread id.
- [ ] Implement operator message posting to a card thread.
- [ ] Persist command intent, target golem id, related card id, and status.

### Task 2: Implement transport contracts

- [ ] Implement operator-facing thread APIs for fetching messages and posting commands.
- [ ] Implement `POST /api/v1/golems/{golemId}/events:batch` with mixed runtime events and lifecycle signals.
- [ ] Implement the outbound control-channel contract surface in Hive so a bot can maintain a WebSocket session and receive commands later.
- [ ] Keep the initial command delivery abstraction internal so the transport can evolve without changing UI APIs.

### Task 3: Implement lifecycle resolution

- [ ] Mirror the schema from `docs/card-lifecycle-signals.md` in backend models and DTO validation.
- [ ] Persist raw signals before any transition logic runs.
- [ ] Apply board-specific mapping rules with `AUTO_APPLY`, `SUGGEST_ONLY`, and `IGNORE`.
- [ ] Generate `CardTransitionEvent` records with source origins such as `MANUAL`, `GOLEM_SIGNAL`, `BOARD_AUTOMATION`, and `FLOW_REMAP`.
- [ ] Prevent free-form message text from directly changing card columns.

### Task 4: Build chat and run UI

- [ ] Build a card-bound thread page with message timeline and command composer.
- [ ] Show the currently assigned golem and allow switching target only through card reassignment, not ad-hoc thread retargeting.
- [ ] Add run timeline panels for commands, signals, and transition suggestions.
- [ ] Surface blocker and completion signals in the card UI.

### Task 5: Add real-time updates

- [ ] Add operator-facing live updates for new thread messages, command state, and lifecycle suggestions.
- [ ] Add reconnect handling for temporary disconnects.
- [ ] Keep UI state resilient when the golem is offline or the control channel is absent.

## Recommended PR Slices

- [ ] PR 1: backend thread/command/event persistence and APIs
- [ ] PR 2: lifecycle signal ingestion and card transition engine
- [ ] PR 3: chat UI, run timeline, and live updates

## Cross-Repo Dependency Note

- [ ] This phase defines the minimum Hive contract that `golemcore-bot` must implement for registration, command consumption, event streaming, and lifecycle signaling.
- [ ] Do not invent a second unofficial event format in tests; use the documented batch envelope only.

## Verification

- [ ] Run: `git diff --check`
- [ ] Run: `./mvnw test`
- [ ] Run: `cd ui && npm run build`
- [ ] Run manual flow:
  - create a card,
  - open its thread,
  - post an operator command,
  - ingest a fake bot event batch with `WORK_STARTED`, `BLOCKER_RAISED`, and `WORK_COMPLETED`,
  - verify the board shows the expected suggestion or auto-move behavior,
  - verify the thread and run timeline render the new records.

## Exit Criteria

- [ ] Cards have live threads and command history.
- [ ] Event batches from bots are accepted and persisted.
- [ ] Lifecycle signals drive board automation through policy, not text parsing.
- [ ] Operators can inspect thread, run, and transition context from one UI.
