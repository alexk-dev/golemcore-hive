# Hive GHCR Publish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GHCR image publication for `golemcore-hive` using Maven Jib with multi-arch manifests and release-triggered tag publication.

**Architecture:** Add a standalone Docker publish workflow that reruns Hive validation jobs, builds arch-specific images with Jib from an `eclipse-temurin` Java 25 JRE base image, then stitches and verifies a multi-arch manifest. Update the release workflow to trigger that publish workflow for newly created release tags.

**Tech Stack:** GitHub Actions, Maven, Spring Boot, Jib, GHCR

---

### Task 1: Add Jib configuration to Maven

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Write the failing verification command**

Run: `./mvnw -DskipTests -Dskip.frontend=true -Djib.target.image=example.invalid/golemcore-hive:test jib:buildTar`
Expected: Failure or unusable defaults that prove Hive is not yet configured for the intended GHCR image build path.

- [ ] **Step 2: Add minimal Jib properties and plugin configuration**

Add Jib version/property defaults, Temurin Java 25 JRE base image, platform architecture property, container port, and current-timestamp image metadata.

- [ ] **Step 3: Run targeted verification**

Run: `./mvnw -DskipTests -Dskip.frontend=true -Djib.target.image=example.invalid/golemcore-hive:test jib:buildTar`
Expected: Success and a generated image tarball path reported by Maven.

### Task 2: Add Docker publish workflow

**Files:**
- Create: `.github/workflows/docker-publish.yml`

- [ ] **Step 1: Mirror the Bot workflow shape with Hive-specific validation steps**

Include triggers for branch pushes, `v*` tags, pull requests to `main`, and manual runs. Add the existing Hive validation path from CI: backend tests, frontend tests, frontend build, and packaging.

- [ ] **Step 2: Add image build and manifest publication jobs**

Build `amd64` and `arm64` images with Jib, push SHA-tagged arch images, optionally push `latest-*` and `<version>-*`, then create the multi-arch manifest without `dev-latest`.

- [ ] **Step 3: Add manifest verification and job summary**

Verify both architectures exist in the pushed manifest and summarize the published tags in the GitHub Actions job summary.

### Task 3: Wire release workflow and docs

**Files:**
- Modify: `.github/workflows/release.yml`
- Modify: `README.md`

- [ ] **Step 1: Trigger container publication from release**

After release assets are published, invoke `gh workflow run docker-publish.yml --ref <release-tag>` using `GITHUB_TOKEN`.

- [ ] **Step 2: Update user-facing documentation**

Document the new GHCR behavior, including branch SHA tags, `main` `latest`, and release tag version publication.

- [ ] **Step 3: Run end-to-end verification commands**

Run:
- `./mvnw -DskipTests package`
- `./mvnw -DskipTests -Djib.target.image=example.invalid/golemcore-hive:test jib:buildTar`
- `actionlint .github/workflows/docker-publish.yml .github/workflows/release.yml` (if available)

Expected: Maven commands succeed; workflow lint succeeds if the tool exists locally.
