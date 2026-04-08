# Card Lifecycle Signals

Status: Draft v1

## 1. Purpose

This document defines how `golemcore-hive` understands card state transitions such as `blocked`, `review`, and `done`.

Core rule:

- Hive must not infer board state by parsing free-form assistant text.
- `golemcore-bot` must emit structured lifecycle signals.
- Hive persists the signal, evaluates the board policy, and then decides whether to move the card.

## 2. Authority Model

The state model has three layers:

1. `Signal`
   - emitted by the bot as a structured fact about work state
2. `Board policy`
   - maps a signal to a transition decision
3. `Board state`
   - the actual current card column stored by Hive

Authority rules:

- Hive owns the authoritative card column.
- A bot may report work state, but it does not directly own board state.
- Operator actions are always valid manual overrides if they satisfy the board flow.

## 3. Signal Types

V1 signal types:

- `WORK_STARTED`
- `PROGRESS_REPORTED`
- `BLOCKER_RAISED`
- `BLOCKER_CLEARED`
- `REVIEW_REQUESTED`
- `WORK_COMPLETED`
- `WORK_FAILED`
- `WORK_CANCELLED`

Semantics:

- `WORK_STARTED`: the assigned golem has actively started work on the card.
- `PROGRESS_REPORTED`: progress update only; normally does not move columns.
- `BLOCKER_RAISED`: the golem cannot continue without external resolution.
- `BLOCKER_CLEARED`: a previously raised blocker no longer applies.
- `REVIEW_REQUESTED`: the golem believes the card is ready for review.
- `WORK_COMPLETED`: the golem believes the execution scope is complete.
- `WORK_FAILED`: the golem cannot complete the work under current conditions.
- `WORK_CANCELLED`: the run was intentionally cancelled.

## 4. Signal Envelope

Every lifecycle signal must carry the following fields:

```json
{
  "schemaVersion": 1,
  "eventType": "card_lifecycle_signal",
  "signalId": "sig_01jxyz...",
  "cardId": "card_01jxyz...",
  "golemId": "golem_01jxyz...",
  "commandId": "cmd_01jxyz...",
  "runId": "run_01jxyz...",
  "threadId": "thread_01jxyz...",
  "signalType": "BLOCKER_RAISED",
  "summary": "Missing staging API credentials",
  "details": "The task requires STAGING_API_KEY to continue deployment verification.",
  "blockerCode": "missing_credentials",
  "evidenceRefs": [
    {
      "kind": "run_log",
      "ref": "run_01jxyz.../logs/step-4.txt"
    }
  ],
  "createdAt": "2026-03-17T18:02:11Z"
}
```

Field notes:

- `schemaVersion`: version for forward compatibility.
- `eventType`: must equal `card_lifecycle_signal`.
- `signalId`: unique immutable id for deduplication and audit.
- `cardId`: required because all work-tracking in Hive is card-bound.
- `golemId`: golem that emitted the signal.
- `commandId`: originating command in Hive.
- `runId`: current execution run.
- `threadId`: canonical card thread when available.
- `signalType`: one of the allowed lifecycle signal enums.
- `summary`: short operator-facing explanation.
- `details`: optional longer description.
- `blockerCode`: optional machine-friendly blocker reason.
- `evidenceRefs`: optional references to artifacts, logs, or reports.
- `createdAt`: event timestamp in UTC.

## 5. Batch Transport

Signals are transported through the existing batched event endpoint:

- `POST /api/v1/golems/{golemId}/events:batch`

Batch envelope example:

```json
{
  "schemaVersion": 1,
  "golemId": "golem_01jxyz...",
  "events": [
    {
      "eventType": "runtime_event",
      "runtimeEventType": "TURN_STARTED",
      "createdAt": "2026-03-17T18:00:00Z"
    },
    {
      "eventType": "card_lifecycle_signal",
      "signalId": "sig_01jxyz...",
      "cardId": "card_01jxyz...",
      "golemId": "golem_01jxyz...",
      "commandId": "cmd_01jxyz...",
      "runId": "run_01jxyz...",
      "signalType": "WORK_COMPLETED",
      "summary": "Implementation and verification passed",
      "evidenceRefs": [
        {
          "kind": "artifact",
          "ref": "artifact_01jxyz..."
        }
      ],
      "createdAt": "2026-03-17T18:04:31Z"
    }
  ]
}
```

## 6. Board Transition Policy

Each board flow definition must contain signal mapping rules.

Recommended v1 shape:

```json
{
  "flowId": "engineering-default",
  "columns": [
    { "id": "inbox", "name": "Inbox" },
    { "id": "ready", "name": "Ready" },
    { "id": "in_progress", "name": "In Progress" },
    { "id": "blocked", "name": "Blocked" },
    { "id": "review", "name": "Review" },
    { "id": "done", "name": "Done" }
  ],
  "signalMappings": [
    {
      "signalType": "BLOCKER_RAISED",
      "decision": "AUTO_APPLY",
      "targetColumnId": "blocked"
    },
    {
      "signalType": "BLOCKER_CLEARED",
      "decision": "SUGGEST_ONLY",
      "targetColumnId": "in_progress"
    },
    {
      "signalType": "WORK_COMPLETED",
      "decision": "AUTO_APPLY",
      "targetColumnId": "review"
    },
    {
      "signalType": "REVIEW_REQUESTED",
      "decision": "AUTO_APPLY",
      "targetColumnId": "review"
    },
    {
      "signalType": "PROGRESS_REPORTED",
      "decision": "IGNORE"
    }
  ]
}
```

Allowed decisions:

- `AUTO_APPLY`
- `SUGGEST_ONLY`
- `IGNORE`

Decision meaning:

- `AUTO_APPLY`: Hive moves the card immediately if the transition is valid.
- `SUGGEST_ONLY`: Hive records the signal and shows a suggested move to the operator.
- `IGNORE`: Hive records the signal but does not propose a move.

## 7. Resolution Algorithm

When Hive receives a lifecycle signal:

1. Validate the envelope.
2. Verify the emitting golem has permission to publish signals for the card.
3. Persist the raw `CardLifecycleSignal`.
4. Load the card, board, and current board flow definition.
5. Find the matching `signalMapping`.
6. Validate that the target column exists and the transition is allowed from the current column.
7. Apply one outcome:
   - create a `CardTransitionEvent` and move the card,
   - create a `CardTransitionEvent` as a suggestion only,
   - store only the signal with no transition.
8. Append an audit event with origin metadata.

If no mapping exists:

- default to `IGNORE` for `PROGRESS_REPORTED`
- default to `SUGGEST_ONLY` for unknown transition-bearing signals

## 8. Special Cases

### 8.1 Blocker

`BLOCKER_RAISED` is the strongest structured signal in v1.

Recommended behavior:

- if the board has a blocker column, auto-move there
- store `blockerCode`
- surface summary/details inline in the card view
- keep the run and thread linked for operator follow-up

### 8.2 Done

`WORK_COMPLETED` does not always mean the card should enter `Done`.

This depends on the board flow:

- engineering board:
  - usually goes to `Review`
- support board:
  - may go straight to `Done`
- research board:
  - may go to `Review` or `Decision`

The board flow definition, not the signal type alone, determines the actual resulting column.

### 8.3 Flow Changes After Cards Already Exist

Board flows may be edited after cards already exist.

If a flow edit removes or renames a column that currently contains cards:

- Hive must require explicit remapping from old column id to new column id
- remap actions must create `CardTransitionEvent` records with origin `FLOW_REMAP`
- signal mappings must also be revalidated against the new column set

## 9. Manual Override Model

Operator actions remain first-class:

- drag-and-drop card move
- explicit transition action
- blocker clearance decision

Manual overrides:

- may supersede a signal-driven transition
- must be recorded as separate `CardTransitionEvent` records
- should not delete the original lifecycle signal

## 10. Data Model Additions

Suggested persisted model for `CardLifecycleSignal`:

```json
{
  "id": "sig_01jxyz...",
  "cardId": "card_01jxyz...",
  "golemId": "golem_01jxyz...",
  "commandId": "cmd_01jxyz...",
  "runId": "run_01jxyz...",
  "threadId": "thread_01jxyz...",
  "signalType": "WORK_COMPLETED",
  "summary": "All acceptance checks passed",
  "details": null,
  "blockerCode": null,
  "evidenceRefs": [],
  "createdAt": "2026-03-17T18:10:00Z"
}
```

Suggested persisted model for `CardTransitionEvent`:

```json
{
  "id": "transition_01jxyz...",
  "cardId": "card_01jxyz...",
  "fromColumnId": "in_progress",
  "toColumnId": "review",
  "resolutionType": "AUTO_APPLY",
  "origin": "GOLEM_SIGNAL",
  "sourceSignalId": "sig_01jxyz...",
  "performedBy": "system",
  "createdAt": "2026-03-17T18:10:01Z"
}
```

Recommended origins:

- `MANUAL_OPERATOR`
- `GOLEM_SIGNAL`
- `BOARD_AUTOMATION`
- `FLOW_REMAP`
- `SYSTEM_RECOVERY`

## 11. Minimal V1 Defaults

If a board does not define explicit mappings, recommended bootstrap defaults are:

| Signal | Default decision | Default target |
|---|---|---|
| `WORK_STARTED` | `SUGGEST_ONLY` | first active-work column |
| `PROGRESS_REPORTED` | `IGNORE` | none |
| `BLOCKER_RAISED` | `AUTO_APPLY` | blocker column if present |
| `BLOCKER_CLEARED` | `SUGGEST_ONLY` | previous active-work column |
| `REVIEW_REQUESTED` | `AUTO_APPLY` | review column if present |
| `WORK_COMPLETED` | `AUTO_APPLY` | review column, else done column |
| `WORK_FAILED` | `SUGGEST_ONLY` | blocker-like column if present |
| `WORK_CANCELLED` | `SUGGEST_ONLY` | current column or cancelled-like column |

## 12. What Hive Should Never Do

- Never use free-form transcript parsing as the primary transition source.
- Never move a card to a column that is not valid in the current board flow.
- Never discard the raw lifecycle signal after applying a transition.
- Never collapse manual and automatic moves into one indistinguishable history record.
