# Card Prompt And First In Progress Dispatch Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require `card.prompt`, expose it in the card UI/API, and auto-dispatch it exactly once on the first transition into `in_progress` while preserving manual follow-up dispatch.

**Architecture:** Extend the card model and card DTOs with prompt and first-auto-dispatch metadata, then trigger the initial command from the card move path after a successful first transition into `in_progress`. Keep assignment routing semantics separate from lifecycle semantics by updating UI copy only, without changing the assignment API contract.

**Tech Stack:** Spring Boot 4, Java 25, WebFlux, local JSON persistence, React 19, TypeScript, Vitest, JUnit 5, WebTestClient

---

### Task 1: Lock Backend Behavior With Failing Tests

**Files:**
- Modify: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/BoardControllerIntegrationTest.java`
- Modify: `src/test/java/me/golemcore/hive/adapter/inbound/web/controller/ThreadControllerIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**
- [ ] **Step 2: Run targeted Maven tests and confirm the new assertions fail for the expected reasons**

### Task 2: Add Prompt Storage And API Surface

**Files:**
- Modify: `src/main/java/me/golemcore/hive/domain/model/Card.java`
- Modify: `src/main/java/me/golemcore/hive/domain/service/CardService.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/boards/CreateCardRequest.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/boards/UpdateCardRequest.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/boards/CardDetailResponse.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/dto/boards/CardSummaryResponse.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/BoardMappingSupport.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/CardsController.java`

- [ ] **Step 1: Implement minimal backend changes to persist and expose required prompt data**
- [ ] **Step 2: Run targeted Maven tests and confirm the prompt validation and serialization tests pass**

### Task 3: Auto-Dispatch On First Entry Into In Progress

**Files:**
- Modify: `src/main/java/me/golemcore/hive/domain/service/CardService.java`
- Modify: `src/main/java/me/golemcore/hive/domain/service/CommandDispatchService.java`
- Modify: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/CardsController.java`

- [ ] **Step 1: Implement the smallest safe hook that creates a card-bound command from `card.prompt` exactly once on first `in_progress` entry**
- [ ] **Step 2: Run targeted Maven tests and confirm the first-entry and no-repeat behaviors pass**

### Task 4: Update Card UX Copy And Form Validation

**Files:**
- Modify: `ui/src/lib/api/cardsApi.ts`
- Modify: `ui/src/features/cards/CardComposerDialog.tsx`
- Modify: `ui/src/features/cards/CardDetailsDrawer.tsx`
- Modify: `ui/src/features/cards/CardDetailsDrawer.test.tsx`

- [ ] **Step 1: Add `prompt` to the card UI/API types and require it in create/edit forms**
- [ ] **Step 2: Rename the assignment copy to `Assignee routing` and clarify that board status follows lifecycle signals**
- [ ] **Step 3: Run targeted Vitest coverage for the updated card UI**

### Task 5: Verify End To End

**Files:**
- Modify: none

- [ ] **Step 1: Run focused backend and frontend verification commands**
- [ ] **Step 2: Inspect the results and summarize actual status, including any remaining risks**
