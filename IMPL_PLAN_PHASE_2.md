# Golemcore Hive Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement golem enrollment, fleet registry, role assignment, and operator-visible fleet management screens.

**Architecture:** Add a dedicated golem identity domain in Hive with short-lived enrollment tokens, machine JWT issuance, refresh rotation, and heartbeat-driven presence. Keep registration bot-initiated and JSON-backed. The frontend should expose a fleet console with golem details, roles, and enrollment token creation.

**Tech Stack:** Spring Boot 4.0, Java 25, Maven, Lombok, Spring Security WebFlux, React, TypeScript, Tailwind CSS 3, TanStack Query.

---

## Phase Result

At the end of Phase 2:

- Hive can mint enrollment tokens for bots,
- `golemcore-bot` has a stable registration contract to call,
- Hive stores and displays registered golems and their roles,
- Hive can track online/offline/degraded states from heartbeats,
- operators can pause or revoke golems from the UI.

## File Structure

**Backend**

- Create: `src/main/java/me/golemcore/hive/domain/model/Golem.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/GolemCapabilitySnapshot.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/GolemRole.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/GolemRoleBinding.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/EnrollmentToken.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/HeartbeatPing.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/GolemRegistryService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/EnrollmentService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/GolemPresenceService.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemRolesController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/EnrollmentController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/...`
- Create: `src/test/java/me/golemcore/hive/...`

**Frontend**

- Create: `ui/src/features/golems/GolemsPage.tsx`
- Create: `ui/src/features/golems/GolemDetailsPanel.tsx`
- Create: `ui/src/features/golems/GolemStatusBadge.tsx`
- Create: `ui/src/features/golems/EnrollmentTokenDialog.tsx`
- Create: `ui/src/features/golems/GolemRolesPage.tsx`
- Create: `ui/src/features/golems/RoleEditorDialog.tsx`
- Create: `ui/src/lib/api/golemsApi.ts`

**Persistence layout**

- Create: `data/hive/golems/<golem-id>.json`
- Create: `data/hive/golem-roles/<role-slug>.json`
- Create: `data/hive/enrollment-tokens/<token-id>.json`
- Create: `data/hive/heartbeats/<golem-id>.json`

## Implementation Tasks

### Task 1: Define fleet identity models and persistence

- [ ] Add `Golem`, `GolemRole`, `GolemRoleBinding`, `EnrollmentToken`, and `HeartbeatPing` models with schema version fields.
- [ ] Store golem roles as free-form slugs with optional description and capability tags.
- [ ] Persist each golem and role as separate JSON documents to avoid whole-collection rewrites.
- [ ] Add lookup indexes only if they can also remain JSON-backed and deterministic.

### Task 2: Implement enrollment and machine JWT issuance

- [ ] Implement operator endpoint to create and revoke short-lived enrollment tokens.
- [ ] Implement `POST /api/v1/golems/register` that accepts enrollment token plus bot metadata.
- [ ] Issue machine `accessToken` and `refreshToken` with machine scopes instead of human RBAC roles.
- [ ] Return `golemId`, issuer, audience, heartbeat interval, and control channel URL metadata.
- [ ] Implement `POST /api/v1/golems/refresh` for bot-side token rotation.
- [ ] Add authorization rules separating operator JWT scopes from machine JWT scopes.

### Task 3: Implement heartbeat and presence resolution

- [ ] Implement `POST /api/v1/golems/{golemId}/heartbeat`.
- [ ] Resolve presence states `ONLINE`, `DEGRADED`, `OFFLINE`, `PAUSED`, `REVOKED`.
- [ ] Add scheduled evaluation of stale heartbeats.
- [ ] Surface pause, resume, revoke, and decommission actions for operators.

### Task 4: Implement role catalog and assignments

- [ ] Implement CRUD for golem roles.
- [ ] Implement binding/unbinding many roles to one golem.
- [ ] Expose role filters in list APIs for later board-team reuse.
- [ ] Keep role creation permissive on slug shape but validate for filesystem-safe persistence.

### Task 5: Build fleet UI

- [ ] Add a `GolemsPage` with list, search, status filters, and last-seen timestamps.
- [ ] Add a details panel with metadata, roles, capabilities, and lifecycle actions.
- [ ] Add a roles management page and enrollment token creation dialog.
- [ ] Make the fleet view usable on mobile and tablet widths.

## Recommended PR Slices

- [ ] PR 1: backend golem models, enrollment, and machine JWT
- [ ] PR 2: heartbeat/presence and golem role management
- [ ] PR 3: fleet UI and role assignment flows

## Cross-Repo Dependency Note

- [ ] Keep the registration and heartbeat API contract stable enough for `golemcore-bot` to implement later.
- [ ] Add request/response examples to controller tests so the contract is executable documentation.
- [ ] Do not block the Hive PR on bot implementation; treat bot integration as a follow-up dependency.

## Verification

- [ ] Run: `git diff --check`
- [ ] Run: `./mvnw test`
- [ ] Run: `cd ui && npm run build`
- [ ] Run manual flow:
  - log in as operator,
  - create enrollment token,
  - call registration endpoint with a fixture payload,
  - verify golem appears in UI,
  - send heartbeat,
  - verify status changes to `ONLINE`,
  - revoke or pause the golem and confirm UI updates.

## Exit Criteria

- [ ] Operator can create enrollment tokens.
- [ ] Bot registration returns machine JWT credentials.
- [ ] Golems, roles, and presence are visible in the UI.
- [ ] Pause/revoke actions work and are persisted.
- [ ] All persistence remains local JSON.
