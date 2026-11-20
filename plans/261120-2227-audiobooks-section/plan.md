# Audiobooks Section Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Audiobooks department on the web player, Android phone/tablet and TV, filled by a new `mediagram add-audiobook` command from MP3s whose embedded cover becomes the poster and whose genre tag becomes the category.

**Architecture:** A new set kind `book`, built the way the standalone `docu` kind was (commit 90546aba): a standalone set (title only), keyed `title-{slug(title)}` for its artwork and category. Every reader plays the MP3 directly. Books add to a title's listening time but never to the day total the kids daily limit reads.

**Tech Stack:** Rust (mlib-spec, mediagram uploader, mediagram-core), Bun/TypeScript server + browser JS (web), Kotlin/Compose/Media3 (Android phone, tablet, TV).

**Spec:** `docs/superpowers/specs/2026-11-20-audiobooks-design.md` (approved 2026-11-20).

## Global Constraints

- Kind spelled `book` everywhere (wire, index `kind` column, typed input). Section key `audiobooks`, label `Audiobooks`, extent `audiobook`, empty line `No audiobooks yet.`, last in nav order (after Tutorials). Android: `Department.AUDIOBOOKS` (name must equal the key), `Kind.AUDIOBOOK`.
- No transcode and no remux of an MP3 on any surface.
- Kids rule: a `book` whose category is exactly `Kinderbücher` is safe for every kids limit; no FSK badge for it.
- Daily limit: a `book` never adds to a profile's day total (`stats_days`) and always opens, at or past the limit. Its title total (`stats_titles`) still grows.
- Covers are shown whole (never cropped) on cards and in the player. No backdrops.
- One card per file. No author, narrator, series, speed, sleep timer or skip buttons.
- The web player is the reference (CLAUDE.md § Surface Parity); Android matches its decisions.
- UI checks run in the stub harness (`cd web && bun run preview`), never the live player.
- Android installs pin a serial (`ANDROID_SERIAL=caad49da` for the tablet); rebuild the native core (`scripts/build-android-core.sh`) after any Rust change before installing.
- Code comments and test names explain the why; they never cite this plan, its phases or the spec.
- Commits: conventional, no AI references, and name only this work's paths. The index already holds unrelated staged files, so commit with `git commit <paths>`, never a bare `git commit`.
- Version: the media-library line moves one minor step from whatever `main` holds when Phase 08 runs (0.191.1 → 0.192.0 today), in `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts` together; bump by pattern (another session commits to `main`).

## Review Focus

1. **A 26-hour book** (Red Rising, 94 137 s, CBR MP3 with no Xing header): duration, a saved position past hour 20 and resume all hold. Pinned in Phase 01 (caption round-trip with `dur: 94137`) and the Phase 09 device walk (seek to 20:00:00, relaunch, resume).
2. **Titles the slug drops letters from** (`Über die Psychologie des Geldes: …`, `ß`, `|`): artwork and category land on the same key in Rust, core and web. Pinned by fixture cases in Phase 01.
3. **Re-running `add-audiobook`** after a complete or an interrupted run: no book uploaded twice. Pinned in Phase 02 (`a_complete_book_is_found_by_its_title`).
4. **An MP3 without a cover or without a genre:** uploads with no poster or no category and shows under the "Other" row. Pinned in Phase 02 (tag tests) and Phase 04 (`a book without a genre sits under Other`).
5. **A kid at the daily limit opening a book**, and a kid seeing a non-Kinderbücher book: the first opens, the second stays hidden. Pinned in Phase 03 and Phases 04/06.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 01 | [The `book` kind and its keys](phase-01-book-kind-and-keys.md) | todo |
| 02 | [`add-audiobook` in the uploader](phase-02-uploader-add-audiobook.md) | todo |
| 03 | [Books outside the daily limit](phase-03-books-outside-daily-limit.md) | todo |
| 03b | [No time-left warning for books](phase-03b-no-time-left-warning-for-books.md) | done |
| 04 | [Web: Audiobooks department, search, kids](phase-04-web-audiobooks-department.md) | todo |
| 05 | [Web: native MP3 playback, cover, lock screen](phase-05-web-mp3-playback.md) | todo |
| 06 | [Android: Audiobooks department on phone and TV](phase-06-android-audiobooks-department.md) | todo |
| 07 | [Android: cover in the player, cast and together start](phase-07-android-audio-only-player.md) | todo |
| 08 | [Docs, changelog, version](phase-08-docs-and-version.md) | todo |
| 09 | [Upload the books and update every app](phase-09-upload-and-release.md) | todo |

Order: 01 first (every other phase reads `book`). 02, 03, 04, 06 can run in parallel after 01; 05 after 04; 07 after 06; 08 after all code; 09 last.

## Check commands

- Rust: `cargo test -p mlib-spec -p mediagram -p mediagram-core -p mediagram-tmdb` and `cargo clippy --workspace -- -D warnings`
- Web: `cd web && bun test && bun run typecheck && bun run lint`
- Android: `cd android && ./gradlew :feature:catalog:testDebugUnitTest :core:model:test :core:data:testDebugUnitTest :feature:player:testDebugUnitTest :ui-common:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest`
- Full gate before merge: `scripts/check.sh`
