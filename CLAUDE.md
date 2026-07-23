# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What squill-rider Is

A JetBrains **Rider / IntelliJ plugin** providing IDE support for [Squill](https://github.com/paulirwin/squill) database projects (squill issue #57). It should:

- Detect the provider from the `.squillproj` file and apply the matching SQL dialect syntax highlighting (PostgreSQL, MariaDB/MySQL) to the `.sql` files in the project.
- At minimum, let users build and edit Squill projects from the IDE.
- (Stretch goal, may be split out) support publishing/deploying from the IDE.

Squill itself is a separate .NET project in the sibling repo; this plugin only needs to understand its project format (`.squillproj`, provider selection) and shell out to / integrate with the Squill CLI as needed.

## Issue tracking & PRs — IMPORTANT

- **Issues for this project are tracked in the `paulirwin/squill` repo**, NOT in `squill-rider`.
  - Read them explicitly: `gh issue view <num> --repo paulirwin/squill` (the plugin is issue #57).
  - Do **not** run `gh repo set-default paulirwin/squill` in this checkout — it would redirect `gh pr create` to the wrong repo.
- **PRs and code live in `paulirwin/squill-rider`** (the local `origin`). Create PRs normally with `gh pr create`.

## Workflow

- **Never commit or push directly to `main`.** Create a feature branch, commit there, push, and open a PR.
- Branch naming: when working from a GitHub issue, use `issue/{num}` (e.g. issue #57 → `issue/57`), using the **squill** issue number.
- **TDD where possible:** write the failing test first, confirm it fails, then implement until it passes.

## Stack & build

JetBrains platform plugin: **Kotlin**, built with the **IntelliJ Platform Gradle Plugin 2.x**, targeting **Rider**. Use the Gradle wrapper (`./gradlew`); a JDK 21 toolchain is required.

- `./gradlew build` — compile, assemble the plugin, and run `verifyPlugin` structural checks.
- `./gradlew test` — run the unit tests.
- `./gradlew runIde` — launch a sandbox Rider with the plugin loaded (first run downloads Rider; slow).
- `./gradlew verifyPlugin` — run plugin-structure verification on its own.

Key files: `build.gradle.kts` / `settings.gradle.kts` (build + pinned versions), `gradle.properties` (plugin coordinates, `pluginSinceBuild`, target IDE knobs), `src/main/resources/META-INF/plugin.xml` (plugin descriptor). Source lives under `io.github.paulirwin.squill.rider`. CI (`.github/workflows/build.yml`) runs `build test verifyPlugin` on push/PR.

**Roadmap:** PR #1 is the scaffold only. Follow-ups: (2) `.squillproj` provider detection + SQL dialect mapping via `com.intellij.database` (bundled in Rider); (3+) build/publish integration. Provider names in `.squillproj`'s `<SquillProviderName>` are case-insensitive: `Postgresql`/`PostgreSQL` → Postgres dialect; `MariaDb`/`MySql` → MariaDB/MySQL dialect.
