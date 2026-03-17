# Golemcore Hive Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap a runnable `golemcore-hive` backend/frontend foundation with operator JWT auth and local JSON persistence.

**Architecture:** Build the backend as a Spring Boot 4.0 WebFlux service in the repository root and the frontend as a Vite React app under `ui/`. Introduce a storage abstraction backed by atomic local JSON files so later phases can add domains without reworking persistence. Match the JWT model used in `golemcore-bot`: short-lived access tokens, refresh tokens, secure cookie refresh for browser sessions, and scoped claims.

**Tech Stack:** Spring Boot 4.0, Java 25, Maven, Lombok, Spring Security WebFlux, Jackson, JJWT, React, TypeScript, Vite, Tailwind CSS 3, React Router, TanStack Query.

---

## Phase Result

At the end of Phase 1:

- the repo builds as a backend + frontend workspace,
- an operator can log in and refresh a session,
- Hive stores state in local JSON files under `data/hive/`,
- the UI has an authenticated app shell ready for fleet and board features,
- the codebase has the core package structure required by later phases.

## File Structure

**Backend root**

- Create: `pom.xml`
- Create: `src/main/java/me/golemcore/hive/HiveApplication.java`
- Create: `src/main/java/me/golemcore/hive/config/HiveProperties.java`
- Create: `src/main/java/me/golemcore/hive/config/JacksonConfig.java`
- Create: `src/main/java/me/golemcore/hive/config/SecurityConfig.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/AuthController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/controller/SystemController.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/security/JwtTokenProvider.java`
- Create: `src/main/java/me/golemcore/hive/adapter/inbound/web/security/RefreshCookieFactory.java`
- Create: `src/main/java/me/golemcore/hive/adapter/outbound/storage/AtomicJsonFileStore.java`
- Create: `src/main/java/me/golemcore/hive/adapter/outbound/storage/LocalJsonStorageAdapter.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/OperatorAccount.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/RefreshSession.java`
- Create: `src/main/java/me/golemcore/hive/domain/model/Role.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/AuthService.java`
- Create: `src/main/java/me/golemcore/hive/domain/service/OperatorBootstrapService.java`
- Create: `src/main/java/me/golemcore/hive/port/outbound/StoragePort.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/java/me/golemcore/hive/...`

**Frontend**

- Create: `ui/package.json`
- Create: `ui/tsconfig.json`
- Create: `ui/vite.config.ts`
- Create: `ui/tailwind.config.cjs`
- Create: `ui/postcss.config.cjs`
- Create: `ui/index.html`
- Create: `ui/src/main.tsx`
- Create: `ui/src/app/App.tsx`
- Create: `ui/src/app/routes.tsx`
- Create: `ui/src/app/providers/AuthProvider.tsx`
- Create: `ui/src/app/providers/QueryProvider.tsx`
- Create: `ui/src/lib/api/httpClient.ts`
- Create: `ui/src/lib/api/authApi.ts`
- Create: `ui/src/features/auth/LoginPage.tsx`
- Create: `ui/src/features/layout/AppShell.tsx`
- Create: `ui/src/features/dashboard/HomePage.tsx`
- Create: `ui/src/styles/index.css`

**Persistence layout**

- Create: `data/hive/operators/<operator-id>.json`
- Create: `data/hive/auth/refresh-sessions/<session-id>.json`
- Create: `data/hive/meta/bootstrap.json`

## Implementation Tasks

### Task 1: Bootstrap backend build and package structure

- [ ] Add Maven parent/build configuration in `pom.xml` for Spring Boot 4.0, Java 25, Lombok, WebFlux, Security, Validation, Jackson, and JJWT.
- [ ] Create `HiveApplication` and a minimal `/api/v1/system/health` endpoint returning version and storage health.
- [ ] Add `HiveProperties` with nested sections for `storage`, `security.jwt`, `security.cookie`, and `bootstrap.admin`.
- [ ] Add Apache 2.0 headers to all new Java files.
- [ ] Configure a consistent base package: `me.golemcore.hive`.

### Task 2: Introduce local JSON persistence primitives

- [ ] Define `StoragePort` with text read/write/list/delete methods returning explicit types.
- [ ] Implement `AtomicJsonFileStore` with temp-file + atomic move semantics.
- [ ] Implement `LocalJsonStorageAdapter` and keep all file system access inside this adapter layer.
- [ ] Standardize UTF-8 JSON serialization with pretty-print disabled for deterministic diffs.
- [ ] Add tests for create, overwrite, list, and concurrent-safe atomic replacement behavior.

### Task 3: Implement operator auth baseline

- [ ] Model `OperatorAccount`, `Role`, and `RefreshSession`.
- [ ] Implement bootstrap logic that creates one local admin operator from configured credentials if no operator records exist.
- [ ] Implement `JwtTokenProvider` with `access` and `refresh` token types and claims aligned with the bot model.
- [ ] Implement `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, and `/api/v1/auth/me`.
- [ ] Store refresh sessions in JSON and invalidate them on logout.
- [ ] Set browser refresh token as `HttpOnly`, `Secure`, `SameSite=Lax` cookie.
- [ ] Protect all non-auth API routes with `JwtAuthenticationFilter`.

### Task 4: Bootstrap React/Tailwind shell

- [ ] Initialize `ui/` with Vite, React, TypeScript, Tailwind 3, React Router, and TanStack Query.
- [ ] Build a login screen that posts to `/api/v1/auth/login`.
- [ ] Add `AuthProvider` that keeps the access token in memory and refreshes automatically through `/api/v1/auth/refresh`.
- [ ] Add an authenticated `AppShell` with top navigation and a placeholder dashboard landing page.
- [ ] Add a simple design system baseline using Tailwind variables, cards, buttons, inputs, and empty-state panels.
- [ ] Keep the layout mobile-safe from the start.

### Task 5: Establish development ergonomics

- [ ] Add backend and frontend README sections to `SPEC.md` or a later top-level README once the app exists.
- [ ] Add `.editorconfig` only if missing and needed for consistent formatting.
- [ ] Add `ui/.eslintrc` or modern equivalent config, plus TypeScript strict mode.
- [ ] Wire a dev proxy so the frontend can talk to the backend without CORS problems.

## Recommended PR Slices

- [ ] PR 1: backend bootstrap, storage primitives, health endpoint
- [ ] PR 2: operator auth backend and refresh cookie flow
- [ ] PR 3: React/Tailwind shell and login flow

## Verification

- [ ] Run: `git diff --check`
- [ ] Run: `./mvnw test`
- [ ] Run: `cd ui && npm install && npm run build`
- [ ] Run: `cd ui && npm run test` if a UI test runner is configured in this phase
- [ ] Run manual flow:
  - start backend,
  - start frontend,
  - log in with bootstrap admin,
  - refresh the page,
  - confirm session survives refresh and `/api/v1/auth/me` works.

## Exit Criteria

- [ ] Backend starts and serves authenticated APIs.
- [ ] Frontend builds and supports login/logout/refresh.
- [ ] Storage is JSON-only and goes through the adapter layer.
- [ ] No database dependency exists.
- [ ] All new Java sources include Apache 2.0 headers.
