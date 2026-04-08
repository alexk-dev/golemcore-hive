# Bot Integration Contract

Status: Draft v1
Parent spec: [SPEC.md](../SPEC.md)

## 1. Purpose

This document freezes the bot-facing Hive integration contract used by `golemcore-hive` and `golemcore-bot`.

It defines:

- how a bot joins Hive,
- which configuration layer is authoritative,
- which HTTP and WebSocket contracts Hive exposes to bots.

## 2. Configuration Authority

The bot-side model has three layers:

1. `RuntimeConfig.HiveConfig`
   - the effective runtime and UI-visible configuration
2. `bot.hive.*`
   - optional managed bootstrap override from application properties
3. `HiveSessionState`
   - separate machine auth/session storage for golem identity and JWTs

Rules:

- `RuntimeConfig.HiveConfig` is the source of truth for runtime behavior when managed bootstrap is not active.
- If `bot.hive.*` is present, the bot must materialize those values into the effective Hive config and render the Hive settings UI as read-only.
- `HiveSessionState` must never be stored inside UI-editable runtime config.

## 3. Join Code

Join flow uses a single copyable string:

- format: `<TOKEN>:<URL>`

Examples:

- `et_abc123.secretvalue:https://hive.example.com`
- `et_abc123.secretvalue:http://localhost:8080`

Parsing rule:

- the bot splits on the first `:` only,
- everything before the first `:` is the enrollment token,
- everything after the first `:` is the Hive base URL.

## 4. Enrollment Token Semantics

Enrollment tokens are:

- reusable,
- revocable,
- TTL-bound,
- operator-created in Hive.

Rules:

- a token may register multiple golems until it is revoked or expires,
- revoke blocks new joins only,
- revoke must not invalidate already-issued machine JWT sessions,
- Hive reveals the token secret only at creation time,
- Hive should expose a ready-made join code immediately after token creation.

## 5. Bot Startup Behavior

Required behavior:

1. If `HiveSessionState` already exists and is valid, the bot reconnects using the stored machine session.
2. If no machine session exists and a join code is available, the bot performs enrollment.
3. If managed `bot.hive.*` values are present, the bot treats the Hive settings UI as read-only.
4. Leaving Hive clears `HiveSessionState` but does not necessarily clear non-secret Hive config such as display name or host label.

## 6. Hive Bot-Facing Endpoints

### 6.1 Operator-side enrollment token issuance

- `POST /api/v1/enrollment-tokens`
- response includes:
  - `token`
  - `joinCode`
  - `expiresAt`

### 6.2 Bot registration

- `POST /api/v1/golems/register`
- request contains:
  - enrollment token
  - bot display metadata
  - runtime/build metadata
  - capability snapshot
- response contains:
  - `golemId`
  - `accessToken`
  - `refreshToken`
  - expiration metadata
  - `controlChannelUrl`
  - heartbeat interval
  - scopes

Registration response scopes currently include:

- `golems:heartbeat`
- `golems:events:write`
- `golems:control:connect`
- `golems:policy:read`
- `golems:policy:write`

### 6.3 Machine token rotation

- `POST /api/v1/golems/{golemId}/auth:rotate`

### 6.4 Heartbeats

- `POST /api/v1/golems/{golemId}/heartbeat`

Heartbeat request contains the operational telemetry already used by Hive fleet views:

- `status`
- `currentRunState`
- `currentCardId`
- `currentThreadId`
- `modelTier`
- `inputTokens`
- `outputTokens`
- `accumulatedCostMicros`
- `queueDepth`
- `healthSummary`
- `lastErrorSummary`
- `uptimeSeconds`
- `capabilitySnapshotHash`

Heartbeat also carries policy convergence state so rollout drift is visible without a second poll:

- `policyGroupId`
- `targetPolicyVersion`
- `appliedPolicyVersion`
- `syncStatus`
- `lastPolicyErrorDigest`

### 6.5 Policy package fetch

- `GET /api/v1/golems/{golemId}/policy-package`
- requires machine scope `golems:policy:read`
- returns:
  - `policyGroupId`
  - `targetVersion`
  - `checksum`
  - `llmProviders`
  - `modelRouter`
  - `modelCatalog`

Rule:

- this endpoint may return raw provider API keys because it is machine-only.

### 6.6 Policy apply reporting

- `POST /api/v1/golems/{golemId}/policy-apply-result`
- requires machine scope `golems:policy:write`
- request contains:
  - `policyGroupId`
  - `targetVersion`
  - `appliedVersion`
  - `syncStatus`
  - `checksum`
  - `errorDigest`
  - `errorDetails`
- response returns the normalized Hive binding state:
  - `policyGroupId`
  - `targetVersion`
  - `appliedVersion`
  - `syncStatus`
  - `lastSyncRequestedAt`
  - `lastAppliedAt`
  - `lastErrorDigest`
  - `lastErrorAt`
  - `driftSince`

### 6.7 Event ingestion

- `POST /api/v1/golems/{golemId}/events:batch`

### 6.8 Control channel

- `GET /ws/golems/control`
- outbound WebSocket from bot to Hive

Control command envelope event types:

- `command`
  - regular operator instruction
  - carries `body`
- `command.cancel`
  - stop/cancel request for an already issued command/run
  - carries the same `commandId`, `threadId`, `cardId`, `runId`, and `golemId`
  - `body` may be omitted
- `policy.sync_requested`
  - requests that the bot refresh Hive-managed policy state
  - carries `policyGroupId`, `targetVersion`, and `checksum`
  - never carries raw provider secrets

Rule:

- Hive may locally cancel commands that were never delivered.
- Once a command is delivered, Hive must request cancellation through `command.cancel` and wait for runtime/lifecycle events from the bot before marking the run terminal.
- Hive must emit `policy.sync_requested` only to bots that advertise feature `policy-sync-v1` in `enabledAutonomyFeatures` and support the `control` channel.
- Pull-based convergence through `policy-package` remains required even when WebSocket delivery is enabled, because the trigger may be missed while the bot is offline or reconnecting.

## 7. Security Rules

- Enrollment token is bootstrap-only; ongoing machine auth is JWT-based.
- Access JWTs are short-lived.
- Refresh JWTs are longer-lived and bot-local.
- Operator browser refresh tokens remain `HttpOnly` cookies.
- Bot machine scopes must stay separate from operator scopes.
- Operator policy read APIs must redact provider secrets and expose only presence metadata such as `apiKeyPresent`.
- If an operator updates a draft policy and omits an existing provider `apiKey`, Hive preserves the stored secret instead of clearing it.
- Raw provider API keys are allowed only on machine-scoped policy package fetches and must never travel over the control channel.
