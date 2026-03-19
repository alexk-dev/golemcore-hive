# Hive Workbench Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn authenticated Hive pages into a compact, high-density operator workbench with a clearer dispatch workflow.

**Architecture:** Replace the marketing-style authenticated shell with a compact top bar, then refactor the board, card drawer, and thread pages into workspace-oriented layouts. Preserve existing functionality and APIs while changing visual hierarchy, density, and action placement to favor real operational work.

**Tech Stack:** React, TypeScript, Tailwind CSS, React Router, TanStack Query, Vite, Spring Boot packaged frontend

---

### Task 1: Record the new workspace shell

**Files:**
- Modify: `ui/src/features/layout/AppShell.tsx`
- Modify: `ui/src/index.css`

- [ ] **Step 1: Refactor the authenticated shell**

Replace the repeated hero with a compact top bar that holds branding, navigation, and operator controls in a much shorter vertical footprint.

- [ ] **Step 2: Expand page width and tighten base visual tokens**

Adjust shared spacing, panel treatment, and content width defaults so authenticated pages can use the desktop viewport more effectively without losing the current warm visual character.

- [ ] **Step 3: Verify no route loses navigation or auth actions**

Run the app locally and confirm navigation links and sign-out remain present on workspace and data pages.

### Task 2: Redesign the board workspace

**Files:**
- Modify: `ui/src/features/boards/KanbanBoardPage.tsx`
- Modify: `ui/src/features/boards/KanbanColumn.tsx`
- Modify: `ui/src/features/boards/BoardsPage.tsx`

- [ ] **Step 1: Compress board page chrome**

Turn the board detail page into a compact toolbar plus kanban surface, removing the oversized introductory block.

- [ ] **Step 2: Increase kanban density**

Tighten column/card spacing and reduce redundant metadata weight so more columns and cards remain visible at once on common desktop widths.

- [ ] **Step 3: Simplify boards list page hierarchy**

Reduce the visual dominance of the create form and make existing boards easier to scan and open.

### Task 3: Make the card drawer a real work panel

**Files:**
- Modify: `ui/src/features/cards/CardDetailsDrawer.tsx`
- Modify: `ui/src/lib/api/commandsApi.ts`
- Modify: `ui/src/lib/api/cardsApi.ts` (only if the drawer needs additional typed data that already exists in APIs)

- [ ] **Step 1: Reorder drawer sections around execution**

Widen the drawer and move execution control ahead of low-frequency history and archive actions.

- [ ] **Step 2: Add inline dispatch from the drawer**

Reuse the existing thread command API so an operator can send the next instruction directly from the board context without leaving for the thread page.

- [ ] **Step 3: Keep deep thread inspection as a secondary path**

Preserve `Open thread`, but visually demote it below the primary dispatch workflow.

### Task 4: Rebuild the thread page as an execution console

**Files:**
- Modify: `ui/src/features/chat/CardThreadPage.tsx`
- Modify: `ui/src/features/chat/ThreadComposer.tsx`
- Modify: `ui/src/features/chat/ThreadMessageList.tsx`
- Modify: `ui/src/features/runs/RunTimeline.tsx`
- Modify: `ui/src/features/chat/GolemSwitcher.tsx` (if needed for density)

- [ ] **Step 1: Replace the oversized thread intro with a compact context bar**

Keep card title, board return, live state, and cancel control visible without spending a full hero section on them.

- [ ] **Step 2: Rebalance the main layout**

Make the left side clearly primary for messages and composing commands, and move golem/runs/signals into a denser right rail.

- [ ] **Step 3: Tighten secondary components**

Reduce padding, titles, and repeated labels in the composer, message list, and run timeline so they read as tools, not marketing cards.

### Task 5: Verify and package the redesign

**Files:**
- Modify only files changed above as needed during fixes

- [ ] **Step 1: Run frontend verification**

Run: `source ~/.nvm/nvm.sh && nvm use && npm ci && npm run test`
Expected: Frontend tests pass.

- [ ] **Step 2: Run package verification**

Run: `./mvnw test`
Expected: Backend and integration tests pass with the new packaged frontend flow untouched.

- [ ] **Step 3: Run build verification**

Run: `source ~/.nvm/nvm.sh && nvm use && npm run build`
Run: `./mvnw -DskipTests package`
Expected: Frontend build and packaged application succeed.

- [ ] **Step 4: Commit**

```bash
git add ui/src docs/superpowers/specs/2026-03-19-hive-workbench-redesign-design.md docs/superpowers/plans/2026-03-19-hive-workbench-redesign.md
git commit -m "feat(ui): redesign hive as operator workbench"
```
