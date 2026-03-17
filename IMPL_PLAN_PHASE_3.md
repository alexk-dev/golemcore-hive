# Golemcore Hive Phase 3 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Kanban boards, board-specific flows, board teams, cards, and assignee selection.

**Architecture:** Make Hive the sole owner of card state. Boards own their own columns, transitions, WIP limits, and team definitions. Cards always belong to a board and always imply a canonical Hive thread identity, even if real bot-thread binding is added later. Assignment policies should support `MANUAL`, `SUGGESTED`, and `AUTOMATIC`, but automatic dispatch can remain local recommendation logic in this phase.

**Tech Stack:** Spring Boot 4.0, Java 25, Maven, Lombok, React, TypeScript, Tailwind CSS 3, TanStack Query, `@dnd-kit`.

---

## Phase Result

At the end of Phase 3:

- operators can create multiple boards with different flows,
- each board can define a team using explicit golems and role-based filters,
- cards can be created, moved, and assigned,
- the assignee picker supports `Team` and `All` tabs,
- the UI shows a real Kanban board rather than placeholders.

## File Structure

**Backend**

- Create: `src/main/java/me/golemcore/hive/domain/model/Board.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/BoardColumn.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/BoardFlowDefinition.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/BoardTeam.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/BoardTeamFilter.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/Card.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/CardAssignmentPolicy.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/AssignmentSuggestion.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/BoardService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/CardService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/AssignmentService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/FlowRemapService.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/BoardsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/CardsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/AssignmentsController.java`

**Frontend**

- Create: `ui/src/features/boards/BoardsPage.tsx`
- Create: `ui/src/features/boards/BoardEditorPage.tsx`
- Create: `ui/src/features/boards/BoardTeamEditor.tsx`
- Create: `ui/src/features/boards/FlowEditor.tsx`
- Create: `ui/src/features/boards/KanbanBoardPage.tsx`
- Create: `ui/src/features/boards/KanbanColumn.tsx`
- Create: `ui/src/features/cards/CardComposerDialog.tsx`
- Create: `ui/src/features/cards/CardDetailsDrawer.tsx`
- Create: `ui/src/features/cards/AssigneePicker.tsx`
- Create: `ui/src/features/cards/AssignmentPolicyBadge.tsx`
- Create: `ui/src/lib/api/boardsApi.ts`
- Create: `ui/src/lib/api/cardsApi.ts`

**Persistence layout**

- Create: `data/hive/boards/<board-id>.json`
- Create: `data/hive/cards/<card-id>.json`
- Create: `data/hive/threads/<thread-id>.json`

## Implementation Tasks

### Task 1: Implement board and flow models

- [ ] Implement `Board`, `BoardColumn`, `BoardFlowDefinition`, and `BoardTeam` models.
- [ ] Support multiple boards with independent columns, transitions, and WIP limits.
- [ ] Support flow remapping when columns are edited after cards already exist.
- [ ] Record board defaults for assignment policy and default assignee filters.

### Task 2: Implement board-team semantics

- [ ] Support explicit golem membership by golem id.
- [ ] Support dynamic membership by role slug filters.
- [ ] Resolve a board team view into concrete golem candidates at query time.
- [ ] Keep the filter model simple: include by name and include by role, without hidden rule DSL in this phase.

### Task 3: Implement cards and assignment policies

- [ ] Cards must include board id, canonical thread id, title, description, current column id, assignee id, assignment policy, and audit metadata.
- [ ] Implement card create/update/move endpoints.
- [ ] Add assignment policy support for `MANUAL`, `SUGGESTED`, and `AUTOMATIC`.
- [ ] In this phase, `AUTOMATIC` only resolves a recommended assignee and applies if the operator requested auto-assignment during creation.
- [ ] Keep pure Hive cards valid; goal/task creation in the bot remains optional.

### Task 4: Build Kanban UI

- [ ] Implement a boards index and board creation/edit flow.
- [ ] Implement the Kanban board using `@dnd-kit` for card moves.
- [ ] Implement a card drawer that shows description, assignee, board, thread id placeholder, and activity summary.
- [ ] Implement `AssigneePicker` with `Team` and `All` tabs.
- [ ] Show team filter provenance so the operator understands why a golem appears in `Team`.

### Task 5: Add flow editing and remap UX

- [ ] Implement board flow editor for columns and transitions.
- [ ] Require explicit column remap when a board edit would strand existing cards.
- [ ] Show how many cards are affected by each remap.
- [ ] Persist remap decisions as transition events to preserve history.

## Recommended PR Slices

- [ ] PR 1: backend board/card models and persistence
- [ ] PR 2: board team filters, assignment policies, and remap logic
- [ ] PR 3: Kanban UI, assignee picker, and board editors

## Verification

- [ ] Run: `git diff --check`
- [ ] Run: `./mvnw test`
- [ ] Run: `cd ui && npm run build`
- [ ] Run manual flow:
  - create two boards with different flows,
  - create board team filters by name and by role,
  - create cards,
  - assign from `Team` and `All`,
  - drag cards across valid columns,
  - edit the flow and remap impacted cards.

## Exit Criteria

- [ ] Multiple boards exist with independent flows.
- [ ] Board teams can be defined by explicit golems and role filters.
- [ ] Card creation, assignment, and movement work end-to-end.
- [ ] The assignee picker exposes `Team` and `All`.
- [ ] Cards always have canonical Hive thread ids.
