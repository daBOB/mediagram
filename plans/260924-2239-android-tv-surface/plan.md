# Android TV Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On an Android TV, with a remote and nothing else, a viewer can set the app up, pick a profile, browse the same library the web player and phone show, and watch — in place of today's "Mediagram — television surface" placeholder.

**Architecture:** `:ui-tv` becomes a second renderer of the existing `feature:*` ViewModels and UiState, built on `androidx.tv:tv-material` 1.1.0. The pure rules it needs that live `internal` inside `:ui-mobile` today (≈30 formatters, the library-position model, the player lifecycle contract) move out first — into the feature module that owns them, or a small `:ui-common` for the few composable ones — so both surfaces call one copy. No ViewModel changes.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2026.06.01), `androidx.tv:tv-material` 1.1.0, media3 1.10.1 (`ui-compose` state holders), Hilt, Robolectric Compose tests.

**Branch / worktree:** `feat/android-tv-ui` at `/home/andre/Workspace/mediagram-android-tv`.

**Supersedes:** [260919 phase 4](../260919-0034-android-foundation-phone-tablet-tv/phase-04-tv-surface.md), written before setup, profiles, kids, details and transport controls existed, and never built.

## Research

- [Web player map](reports/research-260924-2239-web-player-reference-surface-report.md) — the reference surface (Surface Parity rule)
- [Android surface map](reports/research-260924-2239-android-mobile-surface-reuse-report.md) — what TV can reuse, what is trapped in `ui-mobile`

## Global Constraints

- **Web player is the reference.** Home rows, grouping, what a card is, kids filtering, player behaviour: match it. Where TV must differ, say why in a doc (see phase 6), never silently.
- **No ViewModel changes.** `git diff --stat android/feature/` may show *moved-in* pure files and their tests only; no edits to any `*ViewModel.kt` or `*UiState.kt`. A needed edit means a boundary is wrong — stop and re-plan.
- **Move, don't copy.** Shared logic lives once. `ui-mobile` behaviour is unchanged by phase 1; its test suite passes with only import/package edits.
- `:ui-tv` uses `androidx.tv.material3` components. `androidx.compose.material3` never enters `:ui-tv`; tv artifacts never enter `:ui-mobile`.
- Every TV screen: overscan-safe padding, something focused the moment it appears, focus visible from across a room, Back always leads somewhere.
- Home rows hold at most 6 cards and do **not** scroll sideways — the web start page rules out rails on purpose; 6 plates fit a 960dp-wide TV.
- Device checks run on the **TV emulator only** (`ANDROID_SERIAL=emulator-5554`, AVD `Google_TV_1080p`). Never the phone. Play titles only on a **test profile**, list every title played, reset any preference changed.
- Code comments explain the why, never plan/phase references. Conventional commits, no AI references.
- Minor version bump (today `0.43.0 → 0.44.0`, recomputed after rebasing) in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` — by regex, at the end; `main` moves under this branch.

## Review Focus

1. **Focus after Back** — returning from a title to a shelf lands on the card that was opened, not the first card (phase 4 pins it).
2. **Profile switch to kids and back** — every TV shelf re-filters without a restart (same `CatalogViewModel` path as phone; phase 4 walks it on the emulator).
3. **Player lifecycle** — Home button mid-play saves position; returning resumes; leaving the player stops it (the shared contract, phase 1 + 5).
4. **Remote keys in the player** — centre/play-pause/left/right/back all do something sensible whether or not the controls are showing (phase 5 key table tests).
5. **Phone unchanged** — phase 1 is a pure move; the phone suite and a phone smoke pass prove it.

## Phases

| Phase | File | Delivers | Status |
|---|---|---|---|
| 1 | [phase-01-move-shared-rules-out-of-ui-mobile.md](phase-01-move-shared-rules-out-of-ui-mobile.md) | Pure rules + lifecycle glue reachable by both surfaces | done (4a91c94..ed7e6b2) |
| 2 | [phase-02-tv-foundation-theme-and-shell.md](phase-02-tv-foundation-theme-and-shell.md) | tv-material, 10-foot theme, overscan, `TvApp` shell replaces placeholder | done (ba114e6..f594b5f) |
| 3 | [phase-03-tv-setup-and-profiles.md](phase-03-tv-setup-and-profiles.md) | First-run setup, sign-in, library choice, "Who's watching?" by D-pad | done (6e4bc84..60454d6) |
| 4 | [phase-04-tv-catalog-and-title-pages.md](phase-04-tv-catalog-and-title-pages.md) | Masthead, Home rows, walls, kept shelves, collections, series/season/title pages | done (3af2756..e409d04) |
| 5 | [phase-05-tv-player-and-remote-keys.md](phase-05-tv-player-and-remote-keys.md) | Full-screen player driven by remote keys, marks, stats | pending |
| 7 | [phase-07-tv-catches-up-with-main.md](phase-07-tv-catches-up-with-main.md) | Subtitles, player settings, up next, retry/notes, search/genres, offline/mark finished/profile removal — what main gained | pending (runs before 6) |
| 6 | [phase-06-menus-docs-version-emulator-validation.md](phase-06-menus-docs-version-emulator-validation.md) | System/settings reachable, docs, version, mouse-free emulator pass | pending |

Order: 1 → 2 → 3 → 4 → 5 → 7 → 6. Phase 7 was added after `main` (merged at 2d7dfd8) gained features the TV did not have. Phase 3 and 4 could swap, but setup must work before a real catalog appears on the emulator.

## Open Questions (defaults chosen; override before phase named)

1. **Plates or list on TV shelves?** Web defaults to list; its `shelf-mode.js` comment expects posters on a TV; phone uses walls. Default: **plates (walls)**, no list toggle. (phase 4)
2. **Sign-in by remote** — phone number, code, 2FA, API id/hash typed with the on-screen keyboard. Default: **accept the system IME** this round; pairing from the phone is new machinery and out of scope. (phase 3)
3. **Up-next countdown / autoplay** — web has it; check whether the phone has it before phase 5. Default: TV does exactly what the phone does; a gap is logged, not invented here.
4. **Search** — web has it, Android has none. Out of scope for TV; stays a surface gap for both Android surfaces.
