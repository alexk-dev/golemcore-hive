# Hive GHCR Publish Design

## Summary

Add container image build and publication for `golemcore-hive` on `ghcr.io`, matching the `golemcore-bot` workflow model where practical while keeping Hive simpler:

- no custom base image workflow,
- application image built with Maven Jib,
- multi-architecture manifests for `linux/amd64` and `linux/arm64`,
- branch pushes outside `main` publish only a short-SHA tag,
- `main` publishes `latest` and short-SHA,
- version tags `v*` publish `<version>`, `latest`, and short-SHA.

## Goals

- Publish production-ready Hive images to GHCR with a predictable tag model.
- Keep release behavior consistent with `golemcore-bot`, including explicit release-triggered image publication.
- Reuse the existing Maven packaging flow so the built frontend is included in the image.

## Non-Goals

- No custom base image pipeline.
- No `dev-latest` tag.
- No change to the existing release artifact publication behavior besides triggering container publication.

## Architecture

`golemcore-hive` gains a dedicated `docker-publish.yml` workflow. The workflow runs Hive's existing validation path (backend tests, frontend tests, frontend build, packaging), then builds per-architecture images through Maven Jib using an `eclipse-temurin` Java 25 JRE base image. After both arch-specific images are pushed, the workflow creates and verifies a multi-architecture manifest in GHCR.

The existing `release.yml` remains the release authority for the GitHub Release and jar assets. After it pushes a new `v*` tag and publishes release assets, it explicitly triggers `docker-publish.yml` for that tag, mirroring the proven `golemcore-bot` pattern.

## Tagging Rules

- Any branch push outside `main`: `${shortSha}`
- `main`: `latest`, `${shortSha}`
- tag `vX.Y.Z`: `X.Y.Z`, `latest`, `${shortSha}`

Architecture-specific intermediary tags remain private implementation details:

- `${shortSha}-amd64`, `${shortSha}-arm64`
- `latest-amd64`, `latest-arm64`
- `${version}-amd64`, `${version}-arm64`

## Files

- Add `.github/workflows/docker-publish.yml`
- Modify `.github/workflows/release.yml`
- Modify `pom.xml`
- Modify `README.md`

## Verification

- Validate workflow YAML with `actionlint` if available.
- Run Maven packaging with frontend assets enabled.
- Run Maven Jib tar build with an explicit target image and `skipTests`.
- Inspect generated git diff and workflow logic for tag coverage and release trigger behavior.
