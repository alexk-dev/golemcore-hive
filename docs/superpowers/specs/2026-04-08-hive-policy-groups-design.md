# Hive Policy Groups Design

Status: Draft v2
Date: 2026-04-08
Target repo: `golemcore-hive`
Related runtime: `golemcore-bot`

## 1. Summary

This design adds org-level `Policy Groups` to Hive so an operator can centrally manage LLM provider settings, model catalog entries, routing policy, and runtime policy for connected golems.

Each golem may have exactly one active policy group.

Each policy group publishes immutable versions.

Hive becomes the source of truth for:

- provider profiles and API keys,
- model catalog snapshot,
- tier and routing bindings,
- tool policy and shell environment secrets,
- memory retrieval and disclosure policy,
- MCP server catalog and environment secrets,
- autonomy runtime controls,
- rollout target version for attached golems.

When a golem is attached to a policy group, the corresponding local settings in `golemcore-bot` become read-only and are synchronized from Hive.

If synchronization fails, the golem keeps running on the last successfully applied version and reports drift back to Hive.

## 2. Product Goal

Make Hive the organization-level operating system layer for AI execution policy.

The operator should be able to:

- define one reusable runtime policy template,
- publish versioned changes,
- attach a golem to that policy,
- observe whether the golem applied the intended version,
- roll back to an earlier version when needed,
- manage LLM, tools, memory, MCP, and autonomy configuration from Hive instead of per-bot local settings.

## 3. Problem

Today `golemcore-bot` owns provider config, model catalog, router settings, tool access, memory behavior, MCP catalog, and autonomy settings locally.

This creates four problems for an AI-native organization:

- policy drift across golems,
- manual and error-prone secret rotation,
- weak governance for model, routing, tool, memory, MCP, and autonomy changes,
- no central rollout or rollback workflow.

Hive already acts as the control plane for fleet operations, but it does not yet own the runtime policy package that determines how each golem actually uses models.

## 4. Scope

### In scope

- org-level policy groups in Hive,
- immutable versioning of published policy packages,
- one active policy group per golem,
- Hive-managed synchronization to bot,
- read-only local bot settings for managed sections,
- runtime policy sections for tools, memory, MCP, and autonomy,
- drift reporting and rollout visibility,
- rollback by selecting an earlier published version.

### Out of scope

- multiple policy groups merged on one golem,
- partial local overrides for managed policy sections,
- real-time remote execution against Hive-hosted config without local persistence,
- fine-grained staged rollout percentages or canary groups.

## 5. Design Principles

- Hive is the source of truth for managed runtime policy.
- Bot remains the execution plane and persists the last applied working snapshot locally.
- One golem maps to one active policy group in v1.
- Published policy versions are immutable.
- Synchronization must be observable.
- Failed updates must not brick execution.
- Control channel events should trigger sync, not carry secret payloads.

## 6. Recommended Approach

Use `snapshot policy groups`.

Hive stores a full, versioned runtime policy package:

- `llm.providers` including raw API keys,
- `modelRouter`,
- managed `modelCatalog`,
- `tools`,
- `memory`,
- `mcp`,
- `autonomy`.

Each publish creates a new immutable version.

Each attached golem tracks:

- the target version from Hive,
- the currently applied version on the bot,
- its sync status.

The bot fetches the target package over authenticated machine HTTP, validates it locally, applies it atomically, and reports the result back to Hive.

If apply fails, the bot continues running on the last successfully applied version and Hive marks it out of sync.

## 7. Authority Model

When a golem is attached to a policy group:

- Hive owns `llm.providers`,
- Hive owns `modelRouter`,
- Hive owns the managed model catalog snapshot,
- Hive owns managed tool policy and shell environment secrets,
- Hive owns managed memory retrieval and disclosure policy,
- Hive owns managed MCP catalog and MCP environment secrets,
- Hive owns managed autonomy controls,
- bot dashboard editing for those sections becomes read-only.

The bot still owns:

- locally persisted applied snapshot,
- runtime execution against the applied snapshot,
- temporary operation while out of sync on a previously applied version.

This mirrors the existing managed-configuration pattern already used by Hive bootstrap settings in the bot, but applies it to runtime policy sections instead of only to Hive connectivity metadata.

## 8. Versioning Model

### Policy Group

`PolicyGroup` is the mutable operator-facing entity.

It contains:

- `id`
- `slug`
- `name`
- `description`
- `status`
- `currentVersion`
- `draftSpec`
- `createdAt`
- `updatedAt`
- `lastPublishedAt`
- `lastPublishedBy`

### Policy Group Version

`PolicyGroupVersion` is immutable.

It contains:

- `policyGroupId`
- `version`
- `specSnapshot`
- `checksum`
- `changeSummary`
- `publishedAt`
- `publishedBy`

### Policy Group Spec

`PolicyGroupSpec` is the policy payload shape.

It contains:

- `schemaVersion`
- `llmProviders`
- `modelRouter`
- `modelCatalog`
- `tools`
- `memory`
- `mcp`
- `autonomy`
- `checksum`

`draftSpec` is editable.

`specSnapshot` is frozen after publish.

Rollback does not edit history.

Rollback simply points the group back to an earlier published version as the new target.

## 9. Golem Binding Model

Each golem has at most one active binding.

`GolemPolicyBinding` contains:

- `golemId`
- `policyGroupId`
- `targetVersion`
- `appliedVersion`
- `syncStatus`
- `lastSyncRequestedAt`
- `lastAppliedAt`
- `lastErrorDigest`
- `lastErrorAt`
- `driftSince`

### Sync statuses

- `IN_SYNC`
- `SYNC_PENDING`
- `APPLYING`
- `OUT_OF_SYNC`
- `APPLY_FAILED`

`OUT_OF_SYNC` means the golem is still operating on a last known good version while Hive expects a newer target version.

## 10. Policy Package Shape

The policy package should follow bot-native configuration shapes as closely as possible to minimize translation risk.

### `llmProviders`

Provider profiles should mirror the bot provider structure:

- `apiKey`
- `baseUrl`
- `requestTimeoutSeconds`
- `apiType`
- `legacyApi`

### `modelRouter`

Router payload should mirror the bot tier model:

- `routing`
- `tiers`
- `dynamicTierEnabled`
- `temperature`

Each binding includes:

- `model`
- `reasoning`

### `modelCatalog`

This is a versioned snapshot of the model metadata currently managed in the bot catalog.

It remains a separate layer from provider profiles and tier bindings.

### `tools`

Tool policy mirrors bot runtime toggles for:

- filesystem tool access,
- shell tool access,
- skill management and skill transition access,
- tier and goal management access,
- shell environment variables.

Operator read APIs expose only `valuePresent` for shell environment variables.

Machine policy packages include raw shell environment values.

### `memory`

Memory policy mirrors the bot Memory V2 runtime shape:

- retrieval budgets and top-k controls,
- promotion and decay controls,
- retrieval lookback,
- code-aware extraction,
- disclosure mode and prompt style,
- reranking profile,
- diagnostics verbosity.

### `mcp`

MCP policy mirrors the bot MCP server catalog:

- global enablement,
- default startup and idle timeouts,
- server name, description, command, timeouts, and enabled state,
- server environment variables.

Operator read APIs expose only `envPresent` for MCP environment variables.

Machine policy packages include raw MCP environment values.

### `autonomy`

Autonomy policy mirrors the bot auto-mode runtime controls:

- enablement,
- tick interval and task time limit,
- auto-start behavior,
- maximum goals,
- model tier,
- reflection controls,
- milestone notifications.

## 11. Sync and Apply Lifecycle

### 11.1 Publish

When an operator publishes a policy group draft:

1. Hive validates the draft.
2. Hive creates a new immutable `PolicyGroupVersion`.
3. Hive sets the group `currentVersion`.
4. Hive updates `targetVersion` for all attached golems.
5. Hive emits a sync request signal to each connected golem.

### 11.2 Trigger

Hive should send a control-channel event:

- `policy.sync_requested`

The event payload should include only:

- `policyGroupId`
- `targetVersion`
- `checksum`

No secret payload should be sent over WebSocket.

Hive should send this event only to golems that advertise `policy-sync-v1` inside `enabledAutonomyFeatures` and support the `control` channel.

### 11.3 Fetch

After receiving the trigger, the bot fetches the full package through an authenticated machine endpoint using its existing machine JWT.

This fetch step also provides pull-based convergence if the trigger is missed.

### 11.4 Apply

The bot:

1. downloads the policy package,
2. validates the schema and references,
3. materializes the package into bot-local runtime config and managed model catalog files,
4. persists the update atomically,
5. reports success or failure back to Hive.

### 11.5 Drift behavior

If apply fails:

- the bot keeps the last successfully applied version,
- the bot continues operating,
- Hive marks the binding as `OUT_OF_SYNC` or `APPLY_FAILED`,
- the operator can inspect the failure and either fix the new version or roll back.

This is intentional v1 behavior.

The system does not fail closed.

## 12. API Contract

### Operator API

- `POST /api/v1/policy-groups`
- `GET /api/v1/policy-groups`
- `GET /api/v1/policy-groups/{groupId}`
- `PUT /api/v1/policy-groups/{groupId}/draft`
- `POST /api/v1/policy-groups/{groupId}/publish`
- `GET /api/v1/policy-groups/{groupId}/versions`
- `GET /api/v1/policy-groups/{groupId}/versions/{version}`
- `POST /api/v1/policy-groups/{groupId}/rollback`
- `PUT /api/v1/golems/{golemId}/policy-binding`
- `DELETE /api/v1/golems/{golemId}/policy-binding`

### Machine API

- `GET /api/v1/golems/{golemId}/policy-package`
- `POST /api/v1/golems/{golemId}/policy-apply-result`

### `policy-package` response

- `policyGroupId`
- `targetVersion`
- `checksum`
- `llmProviders`
- `modelRouter`
- `modelCatalog`
- `tools`
- `memory`
- `mcp`
- `autonomy`

### `policy-apply-result` request

- `policyGroupId`
- `targetVersion`
- `appliedVersion`
- `syncStatus`
- `checksum`
- `errorDigest`
- `errorDetails`

### `policy-apply-result` response

- `policyGroupId`
- `targetVersion`
- `appliedVersion`
- `syncStatus`
- `lastSyncRequestedAt`
- `lastAppliedAt`
- `lastErrorDigest`
- `lastErrorAt`
- `driftSince`

## 13. Heartbeat Extensions

Heartbeat should be extended so Hive can see convergence without a separate polling view.

Add these fields:

- `policyGroupId`
- `targetPolicyVersion`
- `appliedPolicyVersion`
- `syncStatus`
- `lastPolicyErrorDigest`

This keeps rollout state visible in the same fleet surface that already carries queue depth, current run, and cost telemetry.

## 14. Bot-Side Enforcement

When a golem has an active policy binding:

- bot `LLM Providers` becomes read-only,
- bot `Model Router` becomes read-only,
- bot managed `Model Catalog` becomes read-only.
- bot managed `Tools` policy becomes read-only.
- bot managed `Memory` policy becomes read-only.
- bot managed `MCP` catalog becomes read-only.
- bot managed `Autonomy` policy becomes read-only.

The bot UI should display:

- managing group name,
- applied version,
- target version if different,
- sync status banner when drift exists.

If the golem is detached from the policy group:

- those sections become editable again,
- the last applied snapshot remains as the local baseline.

The bot should not erase the last applied config on detach.

## 15. Security Model

Hive stores raw provider API keys and runtime environment secrets because the chosen product direction is full central authority.

That requires three rules:

1. Secrets must be encrypted at rest in Hive storage.
2. Operator read APIs must never return raw secret values after initial write and instead expose presence metadata such as `apiKeyPresent`, `valuePresent`, or `envPresent`.
3. Full policy packages including secrets must be available only to machine-scoped bot endpoints.

The control channel must never transport raw API keys, shell environment values, or MCP environment values.

Secret delivery should happen only over the machine-authenticated HTTPS package fetch.

If an operator updates a draft policy and omits an existing provider `apiKey`, shell environment value, or MCP environment value, Hive preserves the stored secret instead of clearing it.

## 16. Validation Rules

### Publish-time validation in Hive

- every router model reference exists in the model catalog,
- every catalog model references an existing provider profile,
- every provider profile has required fields,
- every provider `apiType` is supported,
- the package checksum matches the normalized payload,
- no invalid or unknown tier ids are used.

### Apply-time validation on bot

- package schema version is supported,
- providers can be materialized into runtime config,
- router bindings pass the same validation rules as local settings updates,
- model catalog snapshot can be persisted safely,
- resulting config normalization succeeds before final commit.

## 17. Failure Handling

### Golem offline during publish

Hive sets `targetVersion`.

The binding remains `SYNC_PENDING` until the golem reconnects or fetches the package.

### Trigger delivery loss

Bot still converges through pull-based sync against `targetVersion`.

### Validation failure

Bot stays on `appliedVersion`.

Hive marks drift and shows the failure digest.

### Runtime readiness failure

If a provider or model is not usable after apply, the attempt is reported as failed and the bot returns to the previous working state.

### Rollback

Rollback uses the same sync flow as a forward publish.

### Detach

Detach removes central authority but preserves the last applied snapshot locally.

## 18. Operator Workflow

### Create and publish

1. Operator creates a policy group.
2. Operator edits the group draft in runtime policy sections:
   - `Providers`
   - `Model Catalog`
   - `Model Router`
   - `Tools`
   - `Memory`
   - `MCP`
   - `Autonomy`
3. Operator publishes a new version with a `changeSummary`.

### Attach

1. Operator opens a golem detail page or policy group golem list.
2. Operator attaches the golem to the group.
3. Hive sets `targetVersion` to the group `currentVersion`.
4. Bot synchronizes to that version.

### Update

1. Operator edits the draft.
2. Operator publishes a new version.
3. Hive marks all attached golems with the new `targetVersion`.
4. Sync status updates as each golem applies the version.

### Rollback

1. Operator selects an older version.
2. Hive promotes it as the new target.
3. Attached golems synchronize back to that version.

## 19. Rollout UX

Hive UI should expose:

- policy group list,
- policy group detail,
- draft editor,
- published versions,
- per-golem rollout table,
- diff preview before publish,
- drift and failure states,
- rollback action from version history.

The implemented v1 operator UI uses:

- a policy list plus detail layout,
- a JSON draft editor over the normalized `draftSpec`,
- published version history with rollback,
- bound golem attach and detach actions,
- rollout counts on the policy list,
- policy status visibility inside fleet rows and golem detail.

### Policy group list columns

- name
- current version
- attached golems
- in sync count
- out of sync count
- last published at

### Golem rollout table columns

- golem
- policy group
- target version
- applied version
- sync status
- last heartbeat
- drift since
- last error digest

### Diff behavior

- secrets show only `changed` or `unchanged`,
- router diff shows `from -> to` by tier,
- catalog diff shows added, removed, or updated models,
- provider diff shows added, removed, or updated profiles.

## 20. Acceptance Criteria

### Functional

- operator can create a policy group,
- operator can save a draft,
- operator can publish immutable versions,
- operator can attach a golem to exactly one active group,
- online golems converge to the target version automatically,
- offline golems converge after reconnect,
- bot local LLM policy settings become read-only when managed,
- bot local tool, memory, MCP, and autonomy settings become read-only when managed,
- Hive shows target version, applied version, and sync status,
- operator can roll back to an earlier version,
- full package delivery includes raw API keys and runtime environment secrets only on machine endpoints.

### Failure behavior

- failed apply does not remove the last good version,
- failed apply produces visible drift in Hive,
- missed sync trigger still converges through pull,
- detach restores local editability without erasing the last applied snapshot.

### Security

- operator read APIs redact secrets,
- machine APIs can access full policy packages,
- control channel never carries raw secret values.

## 21. Open Questions

These questions are intentionally deferred beyond runtime policy groups:

- Should Hive support staged rollout percentages or canary groups?
- Should the bot keep an explicit local history of previously applied Hive policy versions for forensics?

## 22. Recommendation

Build v1 with:

- one active policy group per golem,
- immutable published versions,
- drift-allowed rollout,
- Hive-owned secrets and runtime policy package,
- read-only local bot editing for managed policy sections,
- pull-based convergence with WebSocket-triggered sync.

This is the narrowest version that still makes Hive the real operating system layer for organization-wide LLM policy.
