# Golemcore Hive Phase 5 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add approvals, audit trails, budgets, packaging, and release hardening so Hive can be operated as a self-hosted control plane.

**Architecture:** Build governance on top of the command/card/run foundation from earlier phases. Approval gates should apply only to destructive or high-cost actions in v1. Audit data, budget usage, and notifications remain JSON-backed, but the code should keep stable boundaries for later migration to object storage or a database if product needs change. Finish by packaging frontend and backend into one deployable artifact and documenting operational workflows.

**Tech Stack:** Spring Boot 4.0, Java 25, Maven, Lombok, React, TypeScript, Tailwind CSS 3, Maven frontend integration, GitHub Actions.

---

## Phase Result

At the end of Phase 5:

- Hive can gate destructive/high-cost operations behind approvals,
- operators can inspect audit history and budget usage,
- frontend and backend build as one releasable application,
- the repo has CI checks, docs, and operational defaults suitable for self-hosting.

## File Structure

**Backend**

- Create: `src/main/java/me/golemcore/hive/domain/model/ApprovalRequest.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/AuditEvent.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/BudgetSnapshot.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/NotificationEvent.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/ApprovalService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/AuditService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/BudgetService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/NotificationService.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/ApprovalsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/AuditController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/BudgetsController.java`

**Frontend**

- Create: `ui/src/features/approvals/ApprovalsPage.tsx`
- Create: `ui/src/features/approvals/ApprovalDecisionDialog.tsx`
- Create: `ui/src/features/audit/AuditPage.tsx`
- Create: `ui/src/features/audit/AuditTimeline.tsx`
- Create: `ui/src/features/budgets/BudgetsPage.tsx`
- Create: `ui/src/features/settings/SystemSettingsPage.tsx`

**Build and docs**

- Modify: `pom.xml`
- Create: `README.md`
- Create: `.github/workflows/ci.yml`
- Modify: `SPEC.md`

**Persistence layout**

- Create: `data/hive/approvals/<approval-id>.json`
- Create: `data/hive/audit/<event-id>.json`
- Create: `data/hive/budgets/<budget-id>.json`
- Create: `data/hive/notifications/<event-id>.json`

## Implementation Tasks

### Task 1: Add approval gates

- [ ] Define approval request types for destructive or high-cost actions only.
- [ ] Intercept command dispatch when an action requires approval.
- [ ] Implement approve/reject APIs and UI workflows.
- [ ] Keep non-destructive actions approval-free in v1 to avoid slowing the core loop.

### Task 2: Add audit and budget domains

- [ ] Record operator auth events, golem lifecycle changes, card transitions, command dispatches, and approval decisions as audit events.
- [ ] Track budget usage from run projections and command metadata, even if early cost data is partial.
- [ ] Add filtering by actor, golem, board, card, and time range.

### Task 3: Add notifications and operational signals

- [ ] Implement lightweight notification hooks for blocker raised, approval requested, golem offline, and command failed events.
- [ ] Keep notification delivery behind a port so integrations can remain optional.
- [ ] Add a settings page exposing notification and retention defaults.

### Task 4: Package the application

- [ ] Decide on frontend bundling into the Spring Boot artifact and wire it in `pom.xml`.
- [ ] Serve built frontend assets from the backend for self-hosted deployment.
- [ ] Add a production config template with storage path, JWT secret, and secure cookie settings.
- [ ] Add startup validation for required production secrets.

### Task 5: Harden CI and documentation

- [ ] Add CI workflow running backend tests, frontend build, and frontend tests if present.
- [ ] Keep the conventional-commit PR title guardrail already added.
- [ ] Write a root `README.md` covering local dev, storage layout, auth bootstrap, and deployment.
- [ ] Update `SPEC.md` with implementation status markers or a follow-up roadmap section.

## Recommended PR Slices

- [ ] PR 1: approvals and audit backend
- [ ] PR 2: approval/audit/budget UI
- [ ] PR 3: packaging, docs, and CI hardening

## Verification

- [ ] Run: `git diff --check`
- [ ] Run: `./mvnw test`
- [ ] Run: `cd ui && npm run build`
- [ ] Run: `cd ui && npm run test` if configured
- [ ] Run a production-style build from a clean checkout.
- [ ] Run manual flow:
  - create a destructive or high-cost command,
  - confirm an approval request is created,
  - approve it,
  - confirm command dispatch proceeds,
  - inspect audit trail and budget counters.

## Exit Criteria

- [ ] Destructive/high-cost actions can require approval.
- [ ] Audit and budget views are usable from the UI.
- [ ] Frontend and backend package together for self-hosting.
- [ ] CI covers the core build/test flow.
- [ ] Repo docs are sufficient for another engineer to run and deploy Hive.
