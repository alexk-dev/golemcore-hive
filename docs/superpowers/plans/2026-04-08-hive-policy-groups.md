# Hive Policy Groups Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build versioned Hive-managed policy groups that centrally own bot LLM providers, model catalog, and routing policy, then synchronize one active policy group to each golem with drift visibility and rollback support.

**Architecture:** Implement policy groups as immutable Hive-side snapshots plus a per-golem binding record with `targetVersion`, `appliedVersion`, and sync status. Land Hive storage and operator APIs first, then add bot-side fetch/apply/report with last-known-good rollback semantics, then add Hive operator UI and bot dashboard read-only enforcement on top of the stabilized APIs.

**Tech Stack:** Spring Boot 4 + WebFlux + Java, React + TypeScript + Tailwind (`golemcore-hive/ui`), Spring Boot + OkHttp + Java + React/Vite dashboard (`golemcore-bot/dashboard`), local JSON persistence

---

## Cross-Repo File Map

### `golemcore-hive` backend

- Create: `src/main/java/me/golemcore/hive/domain/model/PolicyGroup.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/PolicyGroupSpec.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/PolicyGroupVersion.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/GolemPolicyBinding.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/PolicySyncStatus.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/PolicyGroupService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/PolicyRolloutService.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/PolicyGroupsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemPolicyController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/*`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/PolicyApplyResultRequest.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/PolicyPackageResponse.java`
- Modify: `src/main/java/me/golemcore/hive/domain/model/Golem.java`
- Modify: `src/main/java/me/golemcore/hive/domain/model/ControlCommandEnvelope.java`
- Modify: `src/main/java/me/golemcore/hive/domain/service/GolemRegistryService.java`
- Modify: `src/main/java/me/golemcore/hive/domain/service/CommandDispatchService.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemsController.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/HeartbeatRequest.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/GolemSummaryResponse.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/GolemDetailsResponse.java`

### `golemcore-hive` tests and UI

- Create: `src/test/java/me/golemcore/hive/domain/service/PolicyGroupServiceTest.java`
- Create: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/PolicyGroupsControllerIntegrationTest.java`
- Modify: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/FleetControllerIntegrationTest.java`
- Create: `ui/src/lib/api/policyGroupsApi.ts`
- Create: `ui/src/features/policies/PolicyGroupsPage.tsx`
- Create: `ui/src/features/policies/PolicyGroupDetailPage.tsx`
- Create: `ui/src/features/policies/PolicyGroupEditor.tsx`
- Create: `ui/src/features/policies/PolicyVersionsPanel.tsx`
- Create: `ui/src/features/policies/PolicyBindingsPanel.tsx`
- Create: `ui/src/features/policies/PolicyGroupsPage.test.tsx`
- Modify: `ui/src/app/routes.tsx`
- Modify: `ui/src/features/layout/AppShell.tsx`
- Modify: `ui/src/features/layout/AppShell.test.tsx`
- Modify: `ui/src/lib/api/golemsApi.ts`
- Modify: `ui/src/features/golems/GolemsPage.tsx`
- Modify: `ui/src/features/golems/GolemDetailsPanel.tsx`

### `golemcore-bot` backend

- Create: `src/main/java/me/golemcore/bot/domain/model/hive/HivePolicyPackage.java`
- Create: `src/main/java/me/golemcore/bot/domain/model/hive/HivePolicyBindingState.java`
- Create: `src/main/java/me/golemcore/bot/domain/model/hive/HivePolicyApplyResult.java`
- Create: `src/main/java/me/golemcore/bot/domain/service/HivePolicyStateStore.java`
- Create: `src/main/java/me/golemcore/bot/domain/service/HiveManagedPolicyService.java`
- Modify: `src/main/java/me/golemcore/bot/adapter/outbound/hive/HiveApiClient.java`
- Modify: `src/main/java/me/golemcore/bot/domain/model/HiveControlCommandEnvelope.java`
- Modify: `src/main/java/me/golemcore/bot/domain/service/HiveConnectionService.java`
- Modify: `src/main/java/me/golemcore/bot/domain/service/HiveControlCommandDispatcher.java`
- Modify: `src/main/java/me/golemcore/bot/domain/service/RuntimeConfigService.java`
- Modify: `src/main/java/me/golemcore/bot/infrastructure/config/ModelConfigService.java`
- Modify: `src/main/java/me/golemcore/bot/adapter/inbound/web/controller/SettingsController.java`
- Modify: `src/main/java/me/golemcore/bot/adapter/inbound/web/controller/HiveController.java`

### `golemcore-bot` tests and dashboard

- Create: `src/test/java/me/golemcore/bot/domain/service/HiveManagedPolicyServiceTest.java`
- Modify: `src/test/java/me/golemcore/bot/domain/service/HiveConnectionServiceTest.java`
- Modify: `src/test/java/me/golemcore/bot/domain/service/HiveControlCommandDispatcherTest.java`
- Modify: `src/test/java/me/golemcore/bot/domain/service/RuntimeConfigServiceTest.java`
- Modify: `src/test/java/me/golemcore/bot/infrastructure/config/ModelConfigServiceTest.java`
- Modify: `src/test/java/me/golemcore/bot/adapter/inbound/web/controller/SettingsControllerTest.java`
- Modify: `src/test/java/me/golemcore/bot/adapter/inbound/web/controller/HiveControllerTest.java`
- Modify: `src/test/java/me/golemcore/bot/adapter/outbound/hive/HiveApiClientTest.java`
- Create: `dashboard/src/components/common/ManagedByHiveBanner.tsx`
- Create: `dashboard/src/components/common/ManagedByHiveBanner.test.tsx`
- Modify: `dashboard/src/api/settings.ts`
- Modify: `dashboard/src/api/hive.ts`
- Modify: `dashboard/src/hooks/useSettings.ts`
- Modify: `dashboard/src/hooks/useHive.ts`
- Modify: `dashboard/src/pages/SettingsPage.tsx`
- Modify: `dashboard/src/pages/settings/LlmProvidersTab.tsx`
- Modify: `dashboard/src/pages/settings/ModelCatalogTab.tsx`
- Modify: `dashboard/src/pages/settings/ModelsTab.tsx`
- Modify: `dashboard/src/pages/settings/HiveTab.tsx`

## Rollout Order And Compatibility Gates

1. Land Hive domain storage and operator CRUD first without automatic sync delivery to bots.
2. Land bot fetch/apply/report logic next, including a persisted managed-policy state file and rollback-safe application of runtime config plus `models.json`.
3. Add Hive machine endpoints and heartbeat fields, but treat polling and heartbeat-triggered resync as authoritative until the upgraded bot is deployed.
4. Only after the bot can understand `policy.sync_requested` should Hive emit that control event for attached golems.
5. Expose operator UI and bot dashboard read-only banners only after API shapes are stable.

This order matters because older bots currently reject unknown control-channel event types in `HiveControlCommandDispatcher`. Do not emit `policy.sync_requested` fleet-wide until the bot-side dispatcher and fetch/apply path are in place.

### Task 1: Add Hive policy domain, persistence, and version semantics

**Files:**
- Create: `src/main/java/me/golemcore/hive/domain/model/PolicyGroup.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/PolicyGroupSpec.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/PolicyGroupVersion.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/GolemPolicyBinding.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/PolicySyncStatus.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/PolicyGroupService.java`
- Modify: `src/main/java/me/golemcore/hive/domain/model/Golem.java`
- Modify: `src/main/java/me/golemcore/hive/domain/service/GolemRegistryService.java`
- Test: `src/test/java/me/golemcore/hive/domain/service/PolicyGroupServiceTest.java`

- [ ] **Step 1: Write failing Hive domain tests**

Run: `./mvnw -Dtest=PolicyGroupServiceTest test`
Expected: Failure because the policy group domain types and publish/bind/rollback service do not exist yet.

- [ ] **Step 2: Implement immutable policy versions and single-binding persistence**

Add JSON-backed storage for policy groups, versions, and one active binding per golem. Include service methods for create draft, publish new version, rollback by pointer move, attach golem, detach golem, and update `targetVersion` whenever `currentVersion` changes.

- [ ] **Step 3: Re-run the new domain tests**

Run: `./mvnw -Dtest=PolicyGroupServiceTest test`
Expected: PASS with coverage for publish, rollback, attach, detach, and drift metadata defaults.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/golemcore/hive/domain/model src/main/java/me/golemcore/hive/domain/service src/test/java/me/golemcore/hive/domain/service
git commit -m "feat(golems): add policy group domain model"
```

### Task 2: Add Hive operator APIs for policy groups and golem binding

**Files:**
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/PolicyGroupsController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/CreatePolicyGroupRequest.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/UpdatePolicyGroupDraftRequest.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/PublishPolicyGroupRequest.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/PolicyGroupSummaryResponse.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/PolicyGroupDetailResponse.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/PolicyGroupVersionResponse.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/policies/UpdateGolemPolicyBindingRequest.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemsController.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/GolemSummaryResponse.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/GolemDetailsResponse.java`
- Test: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/PolicyGroupsControllerIntegrationTest.java`
- Test: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/FleetControllerIntegrationTest.java`

- [ ] **Step 1: Write failing operator integration tests**

Run: `./mvnw -Dtest=PolicyGroupsControllerIntegrationTest,FleetControllerIntegrationTest test`
Expected: Failure because the operator CRUD endpoints, publish flow, and golem binding endpoints are not implemented.

- [ ] **Step 2: Implement operator CRUD, publish, rollback, and bind endpoints**

Expose:
- `POST /api/v1/policy-groups`
- `GET /api/v1/policy-groups`
- `GET /api/v1/policy-groups/{groupId}`
- `PUT /api/v1/policy-groups/{groupId}/draft`
- `POST /api/v1/policy-groups/{groupId}/publish`
- `GET /api/v1/policy-groups/{groupId}/versions`
- `POST /api/v1/policy-groups/{groupId}/rollback`
- `PUT /api/v1/golems/{golemId}/policy-binding`
- `DELETE /api/v1/golems/{golemId}/policy-binding`

- [ ] **Step 3: Surface binding and sync metadata in fleet responses**

Extend golem summary/detail payloads so the UI can render `policyGroupId`, `targetVersion`, `appliedVersion`, `syncStatus`, and `lastErrorDigest` without making a second machine-only call.

- [ ] **Step 4: Re-run operator integration tests**

Run: `./mvnw -Dtest=PolicyGroupsControllerIntegrationTest,FleetControllerIntegrationTest test`
Expected: PASS for create, publish, bind, list, rollback, and golem detail projections.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/golemcore/hive/adapter/inbound/web src/test/java/me/golemcore/hive/adapter/inbound/web/controller
git commit -m "feat(golems): add policy group operator APIs"
```

### Task 3: Add Hive machine sync endpoints, rollout state, and control-event delivery

**Files:**
- Create: `src/main/java/me/golemcore/hive/domain/service/PolicyRolloutService.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemPolicyController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/PolicyPackageResponse.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/PolicyApplyResultRequest.java`
- Modify: `src/main/java/me/golemcore/hive/domain/model/ControlCommandEnvelope.java`
- Modify: `src/main/java/me/golemcore/hive/domain/service/CommandDispatchService.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/golems/HeartbeatRequest.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/GolemsController.java`
- Test: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/PolicyGroupsControllerIntegrationTest.java`
- Test: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/FleetControllerIntegrationTest.java`

- [ ] **Step 1: Write failing machine-flow tests**

Run: `./mvnw -Dtest=PolicyGroupsControllerIntegrationTest,FleetControllerIntegrationTest test`
Expected: Failure because the bot-facing `policy-package` and `policy-apply-result` APIs, heartbeat drift fields, and sync trigger flow do not exist.

- [ ] **Step 2: Implement machine endpoints and rollout state transitions**

Add:
- `GET /api/v1/golems/{golemId}/policy-package`
- `POST /api/v1/golems/{golemId}/policy-apply-result`

Require machine JWT scopes, update `syncStatus` transitions, persist `appliedVersion` and `lastErrorDigest`, and keep `targetVersion` authoritative in Hive.

- [ ] **Step 3: Add safe sync triggering**

Add `policy.sync_requested` to the control envelope, but gate delivery so it is only sent to bots that already support the event. Until that support signal exists, rely on pull-on-heartbeat and reconnect resync, not WebSocket-only delivery.

- [ ] **Step 4: Extend heartbeat request and response projections**

Add `policyGroupId`, `targetPolicyVersion`, `appliedPolicyVersion`, `syncStatus`, and `lastPolicyErrorDigest` to heartbeat handling so Hive can render drift without waiting for a separate apply callback.

- [ ] **Step 5: Re-run the machine-flow tests**

Run: `./mvnw -Dtest=PolicyGroupsControllerIntegrationTest,FleetControllerIntegrationTest test`
Expected: PASS with package fetch, apply result ingestion, and drift projection coverage.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/golemcore/hive/domain/service src/main/java/me/golemcore/hive/adapter/inbound/web src/test/java/me/golemcore/hive/adapter/inbound/web/controller
git commit -m "feat(golems): add policy sync machine APIs"
```

### Task 4: Add bot policy package models, persistent state, and atomic apply/rollback

**Files:**
- Create: `src/main/java/me/golemcore/bot/domain/model/hive/HivePolicyPackage.java`
- Create: `src/main/java/me/golemcore/bot/domain/model/hive/HivePolicyBindingState.java`
- Create: `src/main/java/me/golemcore/bot/domain/model/hive/HivePolicyApplyResult.java`
- Create: `src/main/java/me/golemcore/bot/domain/service/HivePolicyStateStore.java`
- Create: `src/main/java/me/golemcore/bot/domain/service/HiveManagedPolicyService.java`
- Modify: `src/main/java/me/golemcore/bot/domain/service/RuntimeConfigService.java`
- Modify: `src/main/java/me/golemcore/bot/infrastructure/config/ModelConfigService.java`
- Test: `src/test/java/me/golemcore/bot/domain/service/HiveManagedPolicyServiceTest.java`
- Test: `src/test/java/me/golemcore/bot/domain/service/RuntimeConfigServiceTest.java`
- Test: `src/test/java/me/golemcore/bot/infrastructure/config/ModelConfigServiceTest.java`

- [ ] **Step 1: Write failing bot apply-service tests**

Run: `./mvnw -Dtest=HiveManagedPolicyServiceTest,RuntimeConfigServiceTest,ModelConfigServiceTest test`
Expected: Failure because the managed policy state store and atomic package-apply service do not exist.

- [ ] **Step 2: Persist managed policy state outside runtime config**

Store binding state in a dedicated `preferences/hive-policy-state.json` file so machine sync metadata stays separate from operator-editable runtime config and separate from `hive-session.json`.

- [ ] **Step 3: Implement atomic apply with rollback across runtime config and `models.json`**

Snapshot current `llm`, `modelRouter`, and model catalog, validate the incoming package, write both config layers, and restore the last working snapshot if any write or validation step fails.

- [ ] **Step 4: Re-run the bot apply-service tests**

Run: `./mvnw -Dtest=HiveManagedPolicyServiceTest,RuntimeConfigServiceTest,ModelConfigServiceTest test`
Expected: PASS with coverage for success, validation failure, and rollback to the previous applied version.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/golemcore/bot/domain/model/hive src/main/java/me/golemcore/bot/domain/service src/main/java/me/golemcore/bot/infrastructure/config src/test/java/me/golemcore/bot/domain/service src/test/java/me/golemcore/bot/infrastructure/config
git commit -m "feat(llm): add hive-managed policy apply flow"
```

### Task 5: Extend bot transport, sync polling, and apply-result reporting

**Files:**
- Modify: `src/main/java/me/golemcore/bot/adapter/outbound/hive/HiveApiClient.java`
- Modify: `src/main/java/me/golemcore/bot/domain/model/HiveControlCommandEnvelope.java`
- Modify: `src/main/java/me/golemcore/bot/domain/service/HiveConnectionService.java`
- Modify: `src/main/java/me/golemcore/bot/domain/service/HiveControlCommandDispatcher.java`
- Test: `src/test/java/me/golemcore/bot/adapter/outbound/hive/HiveApiClientTest.java`
- Test: `src/test/java/me/golemcore/bot/domain/service/HiveConnectionServiceTest.java`
- Test: `src/test/java/me/golemcore/bot/domain/service/HiveControlCommandDispatcherTest.java`

- [ ] **Step 1: Write failing transport tests**

Run: `./mvnw -Dtest=HiveApiClientTest,HiveConnectionServiceTest,HiveControlCommandDispatcherTest test`
Expected: Failure because the client cannot fetch policy packages or report apply results, and the dispatcher does not understand `policy.sync_requested`.

- [ ] **Step 2: Add bot-facing HTTP methods and heartbeat payload extensions**

Extend `HiveApiClient` with `getPolicyPackage(...)`, `reportPolicyApplyResult(...)`, and heartbeat fields for current binding state. Make the package fetch authenticated with the machine access token.

- [ ] **Step 3: Wire sync triggers into connection maintenance**

During startup connect, reconnect, heartbeat maintenance, and explicit `policy.sync_requested`, have `HiveConnectionService` ask `HiveManagedPolicyService` whether a newer target exists and apply it if needed. Polling remains the safety net if the control event is lost.

- [ ] **Step 4: Add support for the new control event without breaking command flow**

Teach `HiveControlCommandDispatcher` to accept `policy.sync_requested` as a non-thread, non-command event that triggers package refresh instead of enqueuing a user turn.

- [ ] **Step 5: Re-run the transport tests**

Run: `./mvnw -Dtest=HiveApiClientTest,HiveConnectionServiceTest,HiveControlCommandDispatcherTest test`
Expected: PASS for package fetch, apply result reporting, heartbeat-based resync, and control-event sync trigger.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/golemcore/bot/adapter/outbound/hive src/main/java/me/golemcore/bot/domain/model src/main/java/me/golemcore/bot/domain/service src/test/java/me/golemcore/bot/adapter/outbound/hive src/test/java/me/golemcore/bot/domain/service
git commit -m "feat(hive): add managed policy sync transport"
```

### Task 6: Enforce bot-side read-only settings and expose policy status in the dashboard

**Files:**
- Modify: `src/main/java/me/golemcore/bot/adapter/inbound/web/controller/SettingsController.java`
- Modify: `src/main/java/me/golemcore/bot/adapter/inbound/web/controller/HiveController.java`
- Test: `src/test/java/me/golemcore/bot/adapter/inbound/web/controller/SettingsControllerTest.java`
- Test: `src/test/java/me/golemcore/bot/adapter/inbound/web/controller/HiveControllerTest.java`
- Create: `dashboard/src/components/common/ManagedByHiveBanner.tsx`
- Create: `dashboard/src/components/common/ManagedByHiveBanner.test.tsx`
- Modify: `dashboard/src/api/settings.ts`
- Modify: `dashboard/src/api/hive.ts`
- Modify: `dashboard/src/hooks/useSettings.ts`
- Modify: `dashboard/src/hooks/useHive.ts`
- Modify: `dashboard/src/pages/SettingsPage.tsx`
- Modify: `dashboard/src/pages/settings/LlmProvidersTab.tsx`
- Modify: `dashboard/src/pages/settings/ModelCatalogTab.tsx`
- Modify: `dashboard/src/pages/settings/ModelsTab.tsx`
- Modify: `dashboard/src/pages/settings/HiveTab.tsx`

- [ ] **Step 1: Write failing controller and dashboard tests**

Run: `./mvnw -Dtest=SettingsControllerTest,HiveControllerTest test`
Run: `cd dashboard && npm run test -- ManagedByHiveBanner`
Expected: Failure because bot APIs do not yet expose managed-policy status and the dashboard has no read-only banner or disabled editing flow.

- [ ] **Step 2: Reject local edits to Hive-managed sections**

Extend the existing `rejectManagedHiveMutation(...)` pattern so `llm`, `modelRouter`, and model catalog writes return `409 CONFLICT` while a policy binding is active. Keep the last applied values visible but non-editable.

- [ ] **Step 3: Surface managed-policy status in API responses**

Add `policyGroupId`, `targetVersion`, `appliedVersion`, `syncStatus`, and `lastErrorDigest` to the bot Hive status response and runtime-config response metadata so the dashboard can show where settings authority lives.

- [ ] **Step 4: Add dashboard read-only indicators**

Render a clear `Managed by Hive` banner in the relevant settings tabs, disable destructive editing controls, and link the operator to the Hive group/version currently controlling the bot.

- [ ] **Step 5: Re-run backend and dashboard tests**

Run: `./mvnw -Dtest=SettingsControllerTest,HiveControllerTest test`
Run: `cd dashboard && npm run test`
Expected: PASS for API conflict behavior and UI rendering of the managed-policy state.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/golemcore/bot/adapter/inbound/web/controller src/test/java/me/golemcore/bot/adapter/inbound/web/controller dashboard/src/components/common dashboard/src/api dashboard/src/hooks dashboard/src/pages
git commit -m "feat(hive): lock bot settings under managed policy"
```

### Task 7: Add Hive operator UI for policy groups, versions, and rollout state

**Files:**
- Create: `ui/src/lib/api/policyGroupsApi.ts`
- Create: `ui/src/features/policies/PolicyGroupsPage.tsx`
- Create: `ui/src/features/policies/PolicyGroupDetailPage.tsx`
- Create: `ui/src/features/policies/PolicyGroupEditor.tsx`
- Create: `ui/src/features/policies/PolicyVersionsPanel.tsx`
- Create: `ui/src/features/policies/PolicyBindingsPanel.tsx`
- Create: `ui/src/features/policies/PolicyGroupsPage.test.tsx`
- Modify: `ui/src/app/routes.tsx`
- Modify: `ui/src/features/layout/AppShell.tsx`
- Modify: `ui/src/features/layout/AppShell.test.tsx`
- Modify: `ui/src/lib/api/golemsApi.ts`
- Modify: `ui/src/features/golems/GolemsPage.tsx`
- Modify: `ui/src/features/golems/GolemDetailsPanel.tsx`

- [ ] **Step 1: Write failing Hive UI tests**

Run: `cd ui && npm run test -- PolicyGroupsPage`
Expected: Failure because there is no policy groups route, API client, or policy detail workflow in the operator UI.

- [ ] **Step 2: Add navigation and list/detail routes**

Add a `Policies` entry to the Hive shell, a list page with current version and sync counts, and a detail page with tabs or sections for draft editing, versions, bound golems, and rollout health.

- [ ] **Step 3: Add draft editing, publish diff, and binding controls**

Implement provider, model catalog, and router editors on the draft view; show version diff summaries; and allow golem attach/detach from both the policy page and golem details.

- [ ] **Step 4: Render rollout drift data in fleet views**

Show policy group, `targetVersion`, `appliedVersion`, and `syncStatus` in the golem detail modal and fleet list so rollout health is visible without leaving the fleet context.

- [ ] **Step 5: Re-run the Hive UI tests and build**

Run: `cd ui && npm run test`
Run: `cd ui && npm run build`
Expected: PASS for UI tests and a successful production build.

- [ ] **Step 6: Commit**

```bash
git add ui/src
git commit -m "feat(ui): add hive policy group operator views"
```

### Task 8: Update integration docs and run cross-repo verification

**Files:**
- Modify: `docs/bot-integration-contract.md`
- Modify: `docs/superpowers/specs/2026-04-08-hive-policy-groups-design.md`
- Modify: `/Users/alex/Projects.sandbox/golemcore/.worktrees/bot-ai-native-os-review/docs/HIVE_INTEGRATION.md`
- Modify only files changed above as needed during fixes

- [ ] **Step 1: Update written contracts after APIs settle**

Document the final machine endpoints, heartbeat fields, control event semantics, rollout gate for `policy.sync_requested`, and the authority rule that Hive owns `llm`, `modelRouter`, and managed model catalog when bound.

- [ ] **Step 2: Run backend verification in `golemcore-hive`**

Run:
- `./mvnw test`
- `cd ui && npm run test`
- `cd ui && npm run build`

Expected: Hive backend tests, UI tests, and UI build all pass.

- [ ] **Step 3: Run backend verification in `golemcore-bot`**

Run:
- `./mvnw -Dtest=HiveManagedPolicyServiceTest,HiveApiClientTest,HiveConnectionServiceTest,HiveControlCommandDispatcherTest,SettingsControllerTest,HiveControllerTest,RuntimeConfigServiceTest,ModelConfigServiceTest test`
- `cd dashboard && npm run test`
- `cd dashboard && npm run lint`
- `cd dashboard && npm run build`

Expected: Targeted bot backend suites and dashboard verification pass in the worktree. If `./mvnw test` still fails in the worktree because of `git-commit-id-maven-plugin`, perform the full Maven verification from a standard checkout before merge instead of weakening the plugin contract in feature code.

- [ ] **Step 4: Commit**

```bash
git add docs/bot-integration-contract.md docs/superpowers/specs/2026-04-08-hive-policy-groups-design.md
git -C /Users/alex/Projects.sandbox/golemcore/.worktrees/bot-ai-native-os-review add docs/HIVE_INTEGRATION.md
git commit -m "docs(hive): update policy group integration contracts"
```

## Execution Notes

- Keep Hive as the source of truth for target policy versions and rollout status. The bot must never mutate `targetVersion` locally.
- Do not store raw policy package secrets in operator read APIs. Secrets should remain redacted in Hive operator responses and bot dashboard responses.
- Keep one active binding per golem in v1. Do not implement layered inheritance or merge semantics in this plan.
- Reuse the existing managed-settings pattern in the bot rather than introducing a second authority mechanism.
- Prefer new tests around the added behavior instead of widening unrelated integration fixtures.

Plan complete and saved to `docs/superpowers/plans/2026-04-08-hive-policy-groups.md`. Ready to execute?
