---
title: "Rename a profile"
description: "Sync keeps a renamed profile as one person on every device (stage 1); then the owner, a household's admin and a parent can rename (stage 2)."
status: pending
priority: P2
branch: feat/profile-rename
tags: [profiles, sync, web, rust, android]
created: 2026-10-20
---

# Rename a profile — Implementation Plan

> **For agentic workers:** superpowers:subagent-driven-development. Each `### Task N` is one brief: its phase file's header, the Global Constraints below, and the task.

**Request (2026-10-20):** "i cant edit profile celine". Renaming was left out of the owner plan because sync knows a viewer by its name.
**Design (binding):** `docs/superpowers/specs/2026-10-20-profile-rename-design.md`, with the user's answers at its end: rename keeps history; the owner renames anyone, a household's admin its grown-ups, a parent their kids, nobody renames themselves otherwise; ship after devices update; former names stay reserved for ever.

**Shape (user's, 2026-10-20): two stages.**
- **Stage 1** (Tasks 1–5): the wire keys, the merge pre-pass, the import (rename in place, fold two rows) and the reserved names, on web and core. **No way to rename.** Merged, then released on Android: the TV updates itself, the tablets over `adb`.
- **Gate** (below): the controller checks with the user before any stage-2 work.
- **Stage 2** (Tasks 6–9): the `rename` action in the shared rule and its three ports, the web route and Manage control, the core API with bindings.
- **Not in this plan:** Android's Rename UI. It belongs to the Android households/profiles work; until then Android Manage offers no Rename (CLAUDE.md § Surface Parity, a recorded lag).

## Decisions

The design's controller calls are taken as written (hostile parse; pre-pass then the unchanged merge; reservation instead of a merge rule for a reused name; keep the oldest row on a fold; former names reserved for ever; rename in `profile-rules.json` after the owner plan; prompt in its own module). Where the design left something open, this plan settles it:

1. **Full tie in the pre-pass.** Highest `renamedAt`, then greater device id; two entries of one person in one document with both equal keep the one met first, as `mergeStates` keeps the first spelling in a document today. Records are only ever reordered whole, so this stays order-independent.
2. **Name order is UTF-8 byte order** (`Buffer.compare(Buffer.from(a), Buffer.from(b))`, as `listHouseholds` sorts), which is the core's `String` order. Never JS's default sort or `localeCompare`.
3. **"At most 120 characters"** counts code points (`Array.from(x).length` / `chars().count()`) of the entry trimmed and with runs of space collapsed as `cleanName` does, but **not cut**: a longer entry is dropped. Kept entries are stored normalised (`normalName`).
4. **The pre-pass normalises every name it reads** (entry names, former names, parents, household admins). Fixture records and hand-built records never pass through the parser.
5. **Reservation ships in stage 1, on both sides.** Devices run the stage-1 core when stage 2 starts renaming, and they create profiles; a former name must already be refused there. Excluding the target's own names (rename back, case-only) arrives with the rename in stage 2.
6. **"Oldest" on a fold** is the smallest `created_at`, then the smallest id.
7. **A device's own rename keeps at most 32 former names** (sorted, the first 32), as the merge does, because a reader drops a longer list whole.
8. **No uniffi change in stage 1.** The new keys live on the serde record types and two internal columns; `Profile` is unchanged, so the bindings are not regenerated. The release still carries the rebuilt `.so` (`release-android.sh` builds it). Stage 2 adds `rename_profile` and regenerates.
9. **The owner's rename inside their own household** covers a kid whose parent is another grown-up (the user's "the owner renames anyone"). If the owner plan's Manage does not draw that row, `offered()` skips those cases the way it skips `ownerAcross` today, and the gap is listed in the Review for the user. The rule is not narrowed to match the page.
10. **Base.** Main moved from 750b049f to 87f540c5 (0.133.0, Android sections only) while this was planned. Cut from the then-current main; nothing here depends on 750b049f.

## Global Constraints

- **Branch** `feat/profile-rename`, worktree `../mediagram-rename`, cut from main. The main checkout runs the live player: **never edit code there.** Never `git stash`. Never run `cargo fmt` (there is no rustfmt config).
- **The web is the reference** (CLAUDE.md § Surface Parity). The core ports the web's behaviour; a case that only passes after changing the core belongs in neither.
- **Shared fixtures** (`web/test/fixtures/watch-state/`) are run by the web (`web/test/shared-watch-state-fixtures.test.ts`, `shared-profile-fixtures.test.ts`) and the core (`crates/mediagram-core/tests/shared_watch_state_fixtures.rs`). `profile-rules.json` is also run by Kotlin (`ProfileRolesFixtureTest`, `ManageOffersFixtureTest`). **Every merge case runs in both orders.**
- **Line limits:** new files ≤200. Ratcheted web files (`CEILINGS` in `web/test/code-standards.test.ts`) grow only with a dated sentence there and the new ceiling. Rust `src/` files ≤200 (`_tests.rs` and `tests/` exempt; `cargo test -p mediagram --test code_standards`). Kotlin files over 200 lines must not grow. Re-measure every count quoted in a task before editing; the counts are main's at planning time.
- **Commits:** conventional, no AI references, no plan labels (task numbers, stage names, decision numbers) in code, tests, file names or messages. The controller commits; one task is roughly one commit.
- **Version:** one minor bump per stage, at its merge, in `Cargo.toml`, `web/package.json` and `android/app/build.gradle.kts` `versionName`, by pattern rather than exact string (another session commits to main), to the next minor above what main carries then. `versionCode` is derived; never edit it.
- **Concurrency:** `plans/261020-1535-owner-manages-every-profile/plan.md` (branch `feat/owner-manages-all`) is changing `profile-rules.json`, the three rule ports, `profile-api.js`, `profile-manage.js` and possibly `profiles-manage.ts`. Stage 1 touches only `profiles-manage.ts` line 145 of these (one line, a trivial merge). **Stage 2 starts only after that plan merges.**
- **Checks before each commit:** the task's commands, then `cd web && bun test && bun run typecheck && bun run lint` for web tasks, `cargo test -p mediagram-core && cargo clippy -p mediagram-core --all-targets -- -D warnings` for core tasks. At each merge: `scripts/check.sh`.
- **UI checks** in the preview harness only (`cd web && bun run preview`, a scratch copy, gstack `/browse`); never the live player.
- **Devices:** pin the serial (tablet `caad49da`, Pad 5 Pro `d54bc94f`, box `192.168.0.56:5555`, which moves on DHCP); test profiles only; navigate, never change settings. Do not remove a stale name on an older build meanwhile: removing a grown-up there takes their kids with it locally.
- **Bindings (stage 2 only):** `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh`.

## Tasks

| # | Task | Phase file | Depends on |
|---|---|---|---|
| 1 | Web: the wire keys, the fixture, the merge pre-pass | [phase-01](phase-01-stage-1-sync-keeps-a-renamed-profile.md) | — |
| 2 | Web: schema v17, export, take a rename in, fold, reserved names | phase-01 | 1 |
| 3 | Core: the wire keys and the merge pre-pass, held to the fixture | phase-01 | 1 |
| 4 | Core: schema v13, export, take a rename in, fold, reserved names | phase-01 | 2, 3 |
| 5 | Both: two devices agree; docs, version, merge, Android release | phase-01 | 2, 4 |
| — | **Gate** (below) | | 5 |
| 6 | The `rename` rule in the fixture and its three ports | [phase-02](phase-02-stage-2-rename-a-profile.md) | gate |
| 7 | Web: the route and the Manage control | phase-02 | 6 |
| 8 | Core: `rename_profile`, Android bindings, `FakeCore` | phase-02 | 6 |
| 9 | Docs, version, merge | phase-02 | 7, 8 |

**Order:** 1, then 2 ∥ 3 (disjoint files), then 4, then 5. In stage 2: 6, then 7 ∥ 8, then 9.

## Gate before stage 2

The controller asks the user, and starts Task 6 only when **both** hold:
1. `feat/owner-manages-all` is merged to main.
2. The user confirms the TV box and every tablet in use run the stage-1 release or later, e.g. `adb -s <serial> shell dumpsys package com.mediagram.android | grep versionName` for the box and for `caad49da` / `d54bc94f`. A device that cannot be checked counts as not updated.

Then, in the worktree, `git merge main` (bringing the owner plan in) and continue on `feat/profile-rename`.

## Review

(Filled in at each stage's close-out: checks, review findings and fixes, the release and the device versions, the walk.)
