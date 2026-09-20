# Android system menu, its metadata, and playback stats — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Android app a system menu, show the synopses and artwork it already has or can fetch, and let a viewer see what the player is actually doing.

**Architecture:** Three of the four gaps are reachability, not capability. The uploader already writes synopses into the `shows` table of the `library.db` that `refresh_library` downloads; the poster pipeline is wired end to end and starved of files; every playback statistic is one property access away on the `Player` the UI already holds. So the work is a shared TMDB crate, a handful of new core queries, the chrome the app has never had, and two screens plus an overlay that read what is now reachable.

**Tech Stack:** Rust (reqwest, serde, UniFFI, `mlib-spec`) · Kotlin, Jetpack Compose, Material 3, Media3/ExoPlayer, Hilt, Robolectric, MockK.

**Spec:** [`docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md`](../../docs/superpowers/specs/2026-09-20-android-system-menu-and-playback-stats-design.md)

## Global Constraints

- Worktree `mediagram-android`, branch `android-system-menu`, forked from `main`.
- Every source file stays under **200 lines** (`docs/system-architecture.md` §2). Pre-existing exceptions this work neither caused nor may touch: `CatalogScreen.kt` (208), `web/src/cache/reader.ts`, `web/src/transcode/registry.ts`.
- Media3 `1.10.1`, Compose BOM `2026.06.01`, Material3 `1.4.0`, minSdk `24`, targetSdk `37`, AGP `9.3.1`, Kotlin `2.3.21`. Pins, not floors.
- Rust edition `2024`, rust-version `1.87`. `reqwest` stays `0.13.5`.
- **No Material icons dependency exists and none is added.** Controls are text glyphs, matching the back arrow and the transport bar.
- Files touching a media3 extension API carry `@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)`.
- **The TMDB key is never logged and never appears in a `CoreError`.** The core's rule is that an error names what failed, never the secret that failed to work.
- `grammers_*` must appear nowhere in `crates/mlib-spec/` or the new TMDB crate.
- Nothing transcodes. Android decodes natively.
- After any code change, bump `version` in step across `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts` (`versionName`) — see CLAUDE.md § Versioning.
- Conventional commits. No AI references in commit messages.
- Code comments, test names and file names must **not** reference plan artifacts — no phase numbers, no finding codes. Explain the invariant, not its origin. Files under `plans/` are exempt.
- Live TMDB is never called from a test. CI has no API key and no channel.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [One TMDB client, in a crate both sides share](phase-01-shared-tmdb-crate.md) | Not started |
| 2 | [What the core can already answer](phase-02-core-answers-more-questions.md) | Not started |
| 3 | [Chrome, and the system screen](phase-03-chrome-and-system-screen.md) | Not started |
| 4 | [The TMDB key, and fetching posters](phase-04-tmdb-key-and-posters.md) | Not started |
| 5 | [The title detail screen](phase-05-title-detail-screen.md) | Not started |
| 6 | [Playback stats](phase-06-playback-stats-overlay.md) | Not started |

## Key dependencies

Phases 1 and 2 are Rust-only and change nothing a viewer can see. They can land
in either order.

**Phase 3 is the gate for everything after it.** It introduces the app's first
`Scaffold`, `TopAppBar` and overflow menu — there is no chrome today — and the
cache counters that both the system screen and the stats overlay read. Phases
4, 5 and 6 all hang off it.

Phase 4 needs phase 1's crate and phase 3's menu. Phase 5 needs phase 2's data
and phase 4's posters to be worth looking at. Phase 6 needs only phase 3's
counters, so it can run in parallel with 4 and 5 if anyone wants it sooner.

## Out of scope

Watch state, next-episode autoplay, the television surface, and re-fetching
synopsis text from TMDB. Spec §10 says why for each.

## Review

_Filled in as phases complete._
