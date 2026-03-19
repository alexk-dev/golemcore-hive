# Kanban Background Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the Kanban board and any open card drawer in the background every 10 seconds so remote changes appear without a manual page reload.

**Architecture:** Keep the existing React Query data flow and add per-query polling on the Kanban page instead of introducing a separate live transport. Poll only the board, cards, and open-card queries so the board updates continuously while avoiding unnecessary global traffic for unrelated fleet data.

**Tech Stack:** React 18, TypeScript, @tanstack/react-query v5, Vitest, Testing Library

---

### Task 1: Lock Polling Behavior With A Failing UI Test

**Files:**
- Create: `ui/src/features/boards/KanbanBoardPage.test.tsx`

- [ ] **Step 1: Write a focused failing test that renders the board page, advances 10 seconds, and expects board/cards/card-detail queries to refetch and update visible UI**
- [ ] **Step 2: Run the new Vitest file and confirm it fails for the missing polling behavior**

### Task 2: Add Background Polling To The Kanban Page

**Files:**
- Modify: `ui/src/features/boards/KanbanBoardPage.tsx`

- [ ] **Step 1: Add a shared 10-second polling interval for the board and cards queries**
- [ ] **Step 2: Add the same interval to card detail and assignee queries only while a card drawer is open**
- [ ] **Step 3: Keep the rest of the page behavior unchanged, including manual invalidation after mutations**

### Task 3: Verify The Refresh Loop

**Files:**
- Modify: none

- [ ] **Step 1: Run targeted Vitest coverage for the Kanban board polling change**
- [ ] **Step 2: Inspect the results and summarize actual behavior plus any remaining risk**
