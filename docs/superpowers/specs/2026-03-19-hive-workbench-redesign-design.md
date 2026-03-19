# Hive Workbench Redesign Design

## Summary

Redesign `golemcore-hive` from a presentation-heavy dashboard into a compact operator workbench that uses the full desktop viewport efficiently and makes the primary control loop obvious:

- scan fleet and board state quickly,
- open a card without losing context,
- dispatch work to the assigned golem from the card surface,
- inspect thread and run state without navigating through decorative layout.

The redesign should preserve the existing warm visual direction, but shift the product toward dense, operational screens in the spirit of Paperclip rather than a marketing landing page.

## Goals

- Remove the oversized hero/header framing from authenticated workspace pages.
- Expand content width so board and thread screens use desktop space effectively.
- Make the board page the primary workspace instead of a page with a large intro block above the kanban.
- Make card dispatch obvious by moving the first dispatch surface into the card drawer.
- Reduce visual noise from repeated pills, large cards, and explanatory copy.

## Non-Goals

- No change to backend orchestration semantics or dispatch contracts.
- No redesign of the login/auth flow beyond minor inherited layout cleanup.
- No full rewrite of approvals, audit, budgets, or settings information architecture in this iteration.
- No new product behavior such as automatic dispatch from `READY`.

## Problems With The Current UI

- The authenticated shell repeats a large hero and profile card on every page, pushing working content below the fold.
- The app container is capped at `max-w-7xl`, which forces unnecessary horizontal scrolling on six-column kanban boards.
- Board pages hide their primary interaction behind decorative page chrome.
- Dispatch is discoverable only after opening a card and then navigating again into the thread page.
- Card detail and thread views present secondary metadata with nearly the same visual weight as primary actions.

## Architecture

The redesign introduces two page modes:

- `workspace` pages for high-frequency operational work such as boards, fleet, and threads,
- `data` pages for approvals, audit, budgets, and settings.

The common shell becomes a compact top bar with navigation and operator controls. Workspace pages consume most of the viewport width and rely on internal section headers instead of a global hero. The board page centers on the kanban surface, the card drawer becomes a real work panel, and the thread page becomes a two-column execution console with a clear primary composer area.

## UX Model

### App Shell

- Replace the large hero with a compact top navigation bar.
- Keep branding, global navigation, and the signed-in operator menu, but reduce their vertical footprint.
- Use near-full-width content on authenticated pages, with page-level max widths applied only where readability demands it.

### Boards

- Treat the board page as a workspace, not an overview page.
- Keep a compact board toolbar with name, template, and primary actions.
- Put the kanban immediately after the toolbar.
- Reduce per-column and per-card padding so more columns and cards remain visible at once.
- Show useful card control state inline without oversized badges or redundant ids dominating the card.

### Card Drawer

- Widen the drawer for desktop use.
- Reorder sections so execution comes before archival or history concerns.
- Add a dispatch composer directly in the drawer so operators can send the next instruction without leaving the board.
- Keep `Open thread` as a secondary action for deep inspection.

### Thread Page

- Replace the large page introduction with a compact context bar.
- Keep the left column focused on messages and composing the next command.
- Move golem routing and run/signal inspection into a narrower right rail.
- Reduce card-like spacing so the thread page behaves like an execution console rather than a presentation page.

## Files In Scope

- Modify `ui/src/features/layout/AppShell.tsx`
- Modify `ui/src/index.css`
- Modify `ui/src/features/boards/KanbanBoardPage.tsx`
- Modify `ui/src/features/boards/KanbanColumn.tsx`
- Modify `ui/src/features/cards/CardDetailsDrawer.tsx`
- Modify `ui/src/features/chat/CardThreadPage.tsx`
- Modify `ui/src/features/chat/ThreadComposer.tsx`
- Modify `ui/src/features/chat/ThreadMessageList.tsx`
- Modify `ui/src/features/runs/RunTimeline.tsx`
- Modify `ui/src/features/boards/BoardsPage.tsx`
- Add or modify small supporting UI pieces as required by the new layout

## Verification

- Run frontend tests.
- Run backend tests to ensure the packaged UI still integrates with the Spring Boot artifact.
- Build the frontend and backend package.
- Manually inspect the main authenticated pages:
  - overview,
  - boards list,
  - board detail,
  - card drawer,
  - card thread.
