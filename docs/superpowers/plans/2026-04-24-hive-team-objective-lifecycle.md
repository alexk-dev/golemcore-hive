# Hive Team/Objective Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:executing-plans to implement this plan. Steps use checkbox syntax for tracking. Keep scope focused on team/objective lifecycle. Treat board/card lifecycle validation against archived linked entities as a follow-up, not as part of this PR.

**Goal:** Add first-class lifecycle support for teams and objectives so operators can archive and restore them safely, complete and reopen objectives without deleting history, and hide archived entities from default organization views while still allowing explicit inspection.

**Architecture:** Extend the Team and Objective domain models with a shared lifecycle state and archived timestamp, normalize older stored records on read, enforce lifecycle invariants in application services, expose lifecycle actions and archived filtering via REST, and surface the new behavior in the UI with explicit lifecycle controls and archived visibility toggles.

**Tech Stack:** Spring Boot WebFlux, Java 25, React, TypeScript, TanStack Query, Vitest

---

### Task 1: Extend Team and Objective domain lifecycle

**Files:**
- Create: `src/main/java/me/golemcore/hive/domain/model/EntityLifecycleState.java`
- Modify: `src/main/java/me/golemcore/hive/domain/model/Team.java`
- Modify: `src/main/java/me/golemcore/hive/domain/model/Objective.java`
- Modify: `src/main/java/me/golemcore/hive/workflow/application/port/in/TeamWorkflowUseCase.java`
- Modify: `src/main/java/me/golemcore/hive/workflow/application/port/in/ObjectiveWorkflowUseCase.java`
- Modify: `src/main/java/me/golemcore/hive/workflow/application/service/TeamService.java`
- Modify: `src/main/java/me/golemcore/hive/workflow/application/service/ObjectiveService.java`

- [x] Add a shared `EntityLifecycleState` enum with `ACTIVE` and `ARCHIVED`.
- [x] Bump Team and Objective schema versions, add `lifecycleState` and `archivedAt`, and default new records to `ACTIVE`.
- [x] Normalize older persisted records on read so missing lifecycle fields are treated as active instead of failing at runtime.
- [x] Prevent archived teams/objectives from being updated.
- [x] Add objective lifecycle application actions for `complete`, `reopen`, `archive`, and `restore`.
- [x] Add team lifecycle application actions for `archive` and `restore`.
- [x] Enforce restore-time invariants so objectives cannot be restored into invalid archived owner-team scope.

### Task 2: Expose lifecycle API and archived filtering

**Files:**
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/TeamsController.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/ObjectivesController.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/organization/TeamResponse.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/organization/ObjectiveResponse.java`

- [x] Add `includeArchived=false` query support to team/objective list endpoints so active views stay clean by default.
- [x] Add team archive/restore endpoints.
- [x] Add objective complete/reopen/archive/restore endpoints.
- [x] Return `lifecycleState` and `archivedAt` in organization DTOs.

### Task 3: Add UI lifecycle controls for teams and objectives

**Files:**
- Modify: `ui/src/lib/api/teamsApi.ts`
- Modify: `ui/src/lib/api/objectivesApi.ts`
- Modify: `ui/src/features/teams/TeamsPage.tsx`
- Modify: `ui/src/features/objectives/ObjectivesPage.tsx`
- Modify: `ui/src/features/dashboard/HomePage.tsx`
- Modify: `ui/src/features/cards/CardComposerDialog.test.tsx`

- [x] Extend UI API clients with lifecycle fields, archived filtering, and lifecycle actions.
- [x] Update Teams page to show lifecycle badges, archive/restore controls, and a “Show archived” toggle.
- [x] Update Objectives page to show status plus lifecycle state, complete/reopen/archive/restore actions, and a “Show archived” toggle.
- [x] Keep creation/editing flows pointed at active teams by default while still allowing pages that need historical context to fetch archived data explicitly.
- [x] Update impacted UI tests/fixtures for the enriched Team/Objective payload shape.

### Task 4: Verify behavior and document follow-up scope

**Files:**
- Modify: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/OrganizationControllerIntegrationTest.java`
- Create: `ui/src/features/teams/TeamsPage.test.tsx`
- Create: `ui/src/features/objectives/ObjectivesPage.test.tsx`

- [ ] Run targeted backend verification for lifecycle API behavior.
- [ ] Run targeted UI tests for lifecycle actions and archived filtering.
- [ ] Run UI build verification.
- [x] Record follow-up gap explicitly: board/card/review flows still need lifecycle validation for archived linked Team/Objective references.

### Follow-up intentionally excluded from this PR

- [ ] Reject card create/update when `teamId` or `objectiveId` points at an archived entity.
- [ ] Reject reviewer team selection when the reviewer team is archived.
- [ ] Surface archived-linked card context in board/card UI so existing cards referencing archived entities are clearly labeled.
- [ ] Add lifecycle regression tests for cards/reviews against archived team/objective references.
