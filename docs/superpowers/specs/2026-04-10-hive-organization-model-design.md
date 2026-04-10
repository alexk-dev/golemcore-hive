# Hive Organization Model Design

## Summary

Redesign `golemcore-hive` from a board-first product into an organization-first control plane with five primary product entities:

- `Organization`
- `Team`
- `Service`
- `Objective`
- `Golem`

The key product change is that boards stop being the root object. A board remains a useful interaction pattern for queue management, but it becomes a workspace or view owned by a service rather than the thing that defines organizational structure.

Cards, threads, runs, approvals, budgets, and audit trails remain core execution primitives. This redesign changes the product model above them, not the bot-control contract below them.

## Goals

- Represent real organizational structure instead of encoding ownership into board names and templates.
- Separate stable operating capability from temporary work:
  - `Service` is the stable capability and intake surface.
  - `Objective` is the temporary outcome or initiative.
- Preserve fast Kanban-based operations for day-to-day supervision.
- Make routing and assignment depend on explicit ownership and service policy instead of a simplified board team.
- Improve reporting, governance, and fleet management by scoping them to first-class product entities.
- Keep the system single-tenant while still introducing a real organization boundary.

## Non-Goals

- No SaaS multi-tenant workspace model.
- No removal of cards, threads, runs, or the current golem enrollment model.
- No requirement that every work item belong to an objective.
- No attempt to model a full human HR org chart in the first rollout.
- No hard cut-over that immediately removes existing board APIs or URLs.

## Problems With The Current Model

The current product overloads `Board` with too many responsibilities:

- it is the top-level workspace,
- it owns the workflow,
- it pretends to represent a team,
- it acts as the main routing boundary,
- it implicitly stands in for a service or operating domain.

This causes predictable product limitations:

- board templates are hard-coded starter types instead of real operating capabilities,
- board team membership is only `explicitGolemIds + ROLE_SLUG` filters,
- there is no first-class team ownership model,
- there is no service catalog,
- there is no portfolio layer for strategic objectives,
- audit, budgets, approvals, and fleet state have no explicit organization scope,
- work reporting is forced through board-level slices even when the real question is about a team, a service, or an objective.

In practice, this means operators have to treat boards as fake organizations. That is workable for an early prototype, but it becomes the wrong mental model as soon as one team owns multiple capabilities or one objective spans multiple delivery surfaces.

## Design Principles

- Organization first, boards second.
- Single-tenant does not mean structure-free.
- Stable structure and temporary work must be separate concepts.
- Service operations and objective-driven work must coexist.
- Kanban remains a primary operational surface, but not the source of truth for org modeling.
- Migration should preserve current `cards -> threads -> runs -> artifacts` traceability.
- Compatibility matters more than conceptual purity during rollout.

## Product Model

### Organization

`Organization` is the explicit root entity for a Hive installation.

Responsibilities:

- identity for the installation,
- org-wide settings and taxonomy,
- default approval and budget policy scopes,
- top-level ownership for teams, services, objectives, and golems,
- portfolio and fleet rollups.

Important constraint:

- Hive remains single-tenant.
- The new organization model is about explicit structure, not about introducing customer workspaces.

### Team

`Team` is the durable ownership boundary.

A team answers: who is responsible for this capability, who carries the queue, and who should receive accountability signals when work is stuck?

Responsibilities:

- own one or more services,
- contribute to one or more objectives,
- define a stable operating roster,
- provide the default routing pool for owned services,
- expose capacity and load at the team level.

A team is not just a list of golems. It is the accountability container that services and objectives point to.

### Service

`Service` is the stable operating capability and the new replacement for board-as-root.

Examples:

- Platform Engineering
- Customer Support L2
- Growth Content
- Research Operations

Responsibilities:

- intake surface for new work,
- owner team,
- default workflow definition,
- assignment and routing policy,
- service health and queue health,
- SLA or SLO framing where relevant,
- runbooks and automation hooks later.

Current board templates such as `engineering`, `support`, `content`, and `research` move here as service archetypes rather than board templates.

A service owns the primary Kanban queue. This is where the current board interaction model should survive.

### Objective

`Objective` is a time-bounded desired outcome.

Examples:

- Reduce onboarding latency by 30%
- Launch Q3 migration program
- Resolve incident cluster in region EU

Responsibilities:

- define outcome and success criteria,
- identify owner team,
- link participating teams and services,
- collect related work items,
- expose progress and risk at the portfolio layer.

An objective is not the same thing as a queue. It is possible for one objective to span multiple services, and it is also valid for routine service work to exist without any objective.

This is a critical product decision:

- `serviceId` should be required on new work items,
- `objectiveId` should usually be optional.

That keeps recurring operational work, support queues, and incidents usable without inventing fake objectives.

### Golem

`Golem` stays the execution entity, but now it lives inside explicit org structure.

Responsibilities:

- belong to an organization,
- optionally belong to one or more teams,
- be eligible for one or more services,
- advertise roles, capabilities, and current state,
- execute commands and report lifecycle signals exactly as today.

The golem model does not need a conceptual rewrite. It needs better product context.

### Work Item

The current `Card` remains the canonical work item and thread anchor.

Under the new model, each card should resolve to:

- `organizationId`
- `serviceId`
- `teamId`
- optional `objectiveId`
- optional `assigneeGolemId`
- existing `threadId`
- existing run and artifact correlations

This preserves the proven execution model while moving ownership and routing to first-class entities.

### Board / Workspace View

A board becomes a workspace surface, not a root product entity.

Recommended interpretation:

- the primary Kanban board is the `Service Queue`,
- a team can have an aggregated `Team Queue`,
- an objective can have an `Objective Work` view,
- fleet-level work can have a cross-service queue view.

If Hive keeps a persisted board-like view entity for saved filters or layout state, it must reference `serviceId`, `teamId`, or `objectiveId`. It must not be the place where ownership, workflow, or team semantics originate.

## Relationship Model

| Concern | Current home | New home |
|---|---|---|
| Installation identity | implicit | `Organization` |
| Queue ownership | `Board` | `Service` + `Team` |
| Workflow definition | `Board.flow` | `Service.workflow` |
| Starter template | `Board.templateKey` | `Service.archetype` |
| Team-like routing | `Board.team` | `Team` + `Service.routingPolicy` |
| Strategic outcome | missing | `Objective` |
| Execution target | `Golem` | `Golem` |
| Kanban surface | `Board` | `Service Queue` view |

Recommended cardinality:

- one organization has many teams, services, objectives, and golems,
- one team owns many services,
- one team can contribute to many objectives,
- one service has one owner team,
- one service can support many objectives,
- one objective can involve many teams and many services,
- one golem can serve many services and belong to many teams,
- one card belongs to one service and optionally to one objective.

## Product Information Architecture

### Primary Navigation

Recommended top-level navigation:

- `Organization`
- `Objectives`
- `Services`
- `Teams`
- `Fleet`
- `Approvals`
- `Audit`
- `Budgets`
- `Settings`

Recommended entry behavior:

- new operators land on `Organization Home`,
- returning operators can resume their last service workspace for speed.

### Organization Home

Purpose:

- top-level operational summary for the installation.

Primary modules:

- active objectives and at-risk objectives,
- service health and queue depth,
- fleet health,
- approval backlog,
- budget risk,
- unassigned or unrouted work.

### Services

`Services` becomes the main daily-work directory and replaces `Boards` in the navigation.

Service list should show:

- owner team,
- archetype,
- queue depth,
- blocked work,
- SLA risk,
- active golems.

Service detail should include:

- `Queue`
- `Routing`
- `Workflow`
- `Objectives`
- `Health`
- `Settings`

The `Queue` tab is the successor to the current board detail page.

### Objectives

`Objectives` is a portfolio surface, not a queue-first screen.

Objective list should show:

- owner team,
- status,
- confidence or risk,
- due date,
- linked services,
- active work count.

Objective detail should include:

- summary and success metric,
- linked services and teams,
- active work,
- timeline and major decisions,
- blockers and risks.

If an objective spans multiple services with different workflows, the objective page should prefer a grouped list or lane-based aggregation instead of forcing one synthetic Kanban column model.

### Teams

`Teams` is the ownership and capacity directory.

Team detail should show:

- owned services,
- participating objectives,
- assigned golems,
- queue load and blocked work,
- recent delivery and incident signals.

### Fleet

`Fleet` remains the golem operational surface, but each golem now shows:

- organization context,
- team memberships,
- service coverage,
- current load and queue affinity.

## Core User Flows

### 1. Bootstrap Organization

An operator creates or confirms the single organization record, then defines initial teams and services.

### 2. Define Service

An operator creates a service with:

- owner team,
- archetype,
- workflow,
- routing policy,
- optional SLA/SLO framing.

This replaces creating a board with a template and later trying to interpret the board as a team.

### 3. Create Objective

An operator creates an objective with:

- outcome statement,
- owner team,
- success metric,
- target date,
- linked services.

### 4. Create Work

Work creation starts from a service or from an objective.

Required flow:

- choose service,
- optionally choose objective,
- create card,
- route to eligible golem or leave unassigned,
- keep thread and run lifecycle exactly as today.

### 5. Operate Queue

Operators manage work from the service queue.

They still need:

- drag-and-drop movement,
- quick assignment,
- dispatch from the card surface,
- thread handoff,
- run visibility,
- approval awareness.

The difference is that the queue now belongs to a service with explicit ownership, not to a board pretending to be the org model.

## Routing And Assignment Model

The routing model should be split cleanly:

- `Team` defines durable ownership,
- `Service` defines routing and eligibility,
- `Golem` provides capability and availability,
- `Objective` influences priority and context,
- operator override always remains available.

Recommended routing inputs:

- team ownership,
- explicit service membership,
- role slugs,
- capability tags,
- supported channels,
- golem health/state,
- current queue depth or load,
- optional service-specific preferences.

This is where the current board team model should evolve. The old `ROLE_SLUG` filter is useful, but it is only one routing ingredient, not the whole team model.

## Migration Strategy

### Compatibility Rules

- keep cards, threads, runs, approvals, budgets, and audit records intact,
- keep board URLs and APIs working during the migration window,
- avoid forcing users to classify every existing card into an objective on day one.

### Recommended Data Migration

1. Create one explicit `Organization` record for the installation.
2. Convert every existing board into a `Service`.
3. Map board fields as follows:
   - `board.name` -> `service.name`
   - `board.slug` -> `service.slug`
   - `board.templateKey` -> `service.archetype`
   - `board.flow` -> `service.workflow`
   - `board.defaultAssignmentPolicy` -> `service.defaultAssignmentPolicy`
4. Migrate every card to include `serviceId`.
5. Leave `objectiveId` empty for migrated cards unless a later user action links them.

### Important Migration Decision

Do not auto-promote `Board.team` into a real `Team`.

Reason:

- current board team data is too simplified,
- it describes routing hints, not durable accountability,
- converting it directly into a canonical team model would fossilize the prototype approximation.

Instead:

- use migrated board-team data as initial `Service.routingPolicy`,
- create real teams explicitly,
- attach migrated services to a default owner team such as `Core Operations` until the operator assigns real ownership.

### URL And API Migration

Recommended phased behavior:

- `/boards` becomes a compatibility alias for `/services`,
- `/boards/{boardId}` redirects or resolves to the owning service queue,
- new APIs should introduce `/organizations`, `/teams`, `/services`, and `/objectives`,
- old board APIs can proxy to service-backed implementations until removal is safe.

## Rollout Plan

### Phase 1. Introduce Organization And Service

- add explicit organization record,
- add services as the new backing model for board-like queues,
- keep existing board UI terminology for compatibility if needed.

### Phase 2. Replace Board Navigation With Services

- swap `Boards` for `Services` in primary navigation,
- migrate board detail into service queue and service settings.

### Phase 3. Add Teams And Objective Portfolio

- introduce team directory and ownership flows,
- introduce objective portfolio and objective detail,
- allow cards to optionally link objectives.

### Phase 4. Retire Board-Root Semantics

- treat board as a saved or derived workspace only,
- deprecate board-specific storage and APIs once parity is complete.

## Success Criteria

- Operators can answer "which team owns this?" without encoding the answer in a board name.
- Operators can answer "which service is carrying this work?" without inferring it from a template.
- Objectives can span multiple services without inventing duplicate boards.
- Service operations continue to work even when a card has no objective.
- Golem assignment is explainable in terms of service policy and ownership, not only board-local filters.
- Existing work remains traceable from card to thread to run after migration.
