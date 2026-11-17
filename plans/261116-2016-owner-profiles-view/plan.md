# One owner view on the Profiles page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The owner manages every household (people, access, households) from one view on the Profiles page, with one PIN, and can add a person straight into any household — web, tablet and TV alike.

**Architecture:** One rule change (owner may name a household for a new grown-up, a parent for a new kid) in the three ports held to `profile-rules.json`; the web server and Rust core accept the two optional fields; each surface merges its Manage part and its owner's Households part into one owner view sharing one held PIN. Web first; Android follows its decisions.

**Tech Stack:** Bun + TypeScript server, browser JS (bun:test), Rust `mediagram-core` + uniffi, Kotlin/Compose (Hilt, Robolectric).

**Spec:** `docs/superpowers/specs/2026-11-16-owner-profiles-view-design.md` (approved 2026-11-16)

## Global Constraints

- Non-owner pages (household admins, plain grown-ups, kids) stay as they are.
- Everything stays on the one Profiles page; no new page or screen.
- PINs: the owner's PIN asked once per visit (at the first change, or at *Who are you?* / *Owner*), dropped on leaving or on a wrong PIN.
- Rule held to `web/test/fixtures/watch-state/profile-rules.json` in web, Rust and Kotlin; household words held to `household-rules.json` `words`.
- Old clients that send neither `household` nor `parentId` behave exactly as before.
- Files ≤ 200 lines: split a full file before adding to it.
- No plan references (phase numbers, finding codes) in code comments, test names, file names or commits; conventional commits, no AI references.
- Never run workspace-wide `cargo fmt`; rebuild the native core (`scripts/build-android-core.sh`) after Rust changes.
- Web UI is checked in the stub harness (`cd web && bun run preview`), never the real player.
- Android installs pinned to a serial (tablet `caad49da`); device walks only navigate, never change settings or create profiles.
- Work in worktree `../mediagram-owner-profiles` on `feat/owner-profiles-view`; merge to `main` at the end.
- Version: media library line, minor bump, in step (`Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts`).

## Review Focus

- A wrong owner PIN at the first change: nothing is sent, the PIN is asked again, no stale PIN reused → tests in 4.2 and 5.1.
- A name already taken, added into another household: the refusal shows beside that household's form → tests in 4.2 and 6.1.
- A household added or removed on another device mid-visit: the view follows and keeps the PIN → tests in 4.2 and 5.2.
- Leaving the page (Done, Escape, Back) and returning asks the PIN again → tests in 4.2 and 5.1.
- A client from before this change creating a profile: lands in the maker's household as today → tests in 2.1 and 3.1.

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [Shared rule: the owner adds anywhere](phase-01-shared-rule-add-anywhere.md) | — | todo |
| 02 | [Web server: create in a household / for a parent](phase-02-web-server-create-in-household.md) | 01 | todo |
| 03 | [Rust core and Android calls](phase-03-rust-core-and-android-calls.md) | 01 | todo |
| 04 | [Web: one owner view](phase-04-web-owner-view.md) | 02 | todo |
| 05 | [Android: owner view state](phase-05-android-owner-view-state.md) | 03, 04 | todo |
| 06 | [Tablet/phone owner view](phase-06-tablet-owner-view.md) | 05 | todo |
| 07 | [TV owner view](phase-07-tv-owner-view.md) | 05 | todo |
| 09 | [Web: Who's managing? and the calm view](phase-09-web-who-is-managing-and-calm-view.md) | 04 | todo |
| 10 | [Tablet/phone: Who's managing? and the calm view](phase-10-tablet-who-is-managing-and-calm-view.md) | 09 | todo |
| 11 | [TV: Who's managing? and the calm view](phase-11-tv-who-is-managing-and-calm-view.md) | 09, 07 | todo |
| 08 | [Verify, version, docs, merge](phase-08-verify-version-merge.md) — runs last | 01–07, 09–11 | todo |

02 and 03 can run in parallel; so can 06 and 07.

2026-11-17: after seeing picker → Manage profiles, the user chose *Who's managing?* tiles and a calm owner view
(tap a person to open their controls). Phases 09–11 add that; Phase 08 runs after them.
Spec: `docs/superpowers/specs/2026-11-17-who-is-managing-and-calm-owner-view-design.md`.
