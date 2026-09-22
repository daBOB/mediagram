---
title: "Android watch state, synced with the web player"
description: "Core-side state store + merge + channel sync; Continue, Next up, Watchlist, Collections, Kids on Android, same rules as the web."
status: pending
priority: P1
effort: 44h
branch: main
tags: [android, rust-core, web, sync, watch-state, parity]
created: 2026-09-22
---

# Android watch state, synced with the web player

Locked (user): build Android watch state now, synced through the web's existing state channel.
Supersedes, on landing: `260922-0124-android-web-parity` phases 4, 5, 8, 9 and
`260920-2221-watch-state-across-devices/phase-03`. Parity phase 7 (autoplay) stays open and reuses `playOrder` from 06.

## Phases

| # | Phase | Track | Blocked by | Effort | Status |
|---|---|---|---|---|---|
| 01 | [Shared fixtures from the web's behaviour](phase-01-shared-watch-state-fixtures.md) | web tests | – | 3h | done |
| 02 | [Core state store, record, merge](phase-02-core-state-store-record-and-merge.md) | rust | 01 | 6h | pending |
| 03 | [Core channel sync](phase-03-core-state-channel-sync.md) | rust | 02 | 4h | pending |
| 04 | [Android repository, profiles, sync schedule](phase-04-android-repository-profiles-and-sync-schedule.md) | android | 03 | 6h | pending |
| 05 | [Player records and resumes](phase-05-android-player-records-and-resumes.md) | android | 04 | 5h | pending |
| 06 | [Start page: Continue, Next up, card marks](phase-06-android-start-page-continue-and-next-up.md) | android | 05 | 5h | pending |
| 07 | [Kept shelves: Continue, Watchlist, Collections, Kids](phase-07-android-kept-shelves-and-player-toggles.md) | android | 06 | 7h | pending |
| 08 | [List sync: watchlist, kids, collections in the record](phase-08-list-sync-record-extension.md) | web+rust | 03, 07; **Q1** | 6h | pending |
| 09 | [Docs, versions, device validation](phase-09-docs-versions-and-device-validation.md) | all | 07 (08 if taken) | 2h | pending |

Sequential except: 08's web half can run beside 07 (disjoint files).

## Key facts (verified)

- Record carries **progress + watched only**; lists were left out for lack of tombstones — `web/src/state/sync-record.ts:14-20`. So Watchlist/Collections/Kids are *not* shared today, even between two web players.
- `SYNC_FORMAT = 1`, future formats rejected, unknown keys silently ignored — `sync-record.ts:24,78-117`. Additive fields need no format bump.
- Merge: LWW per title, tie → greater device id, `watched` ≥ position is the tombstone — `web/src/state/merge.ts:98,133`. Import is corrective, never wholesale — `web/src/state/store.ts:379`; unknown viewer names create local profiles — `store.ts:442`.
- Channel: pinned `#mlib-state v=1 device=<uuid>` doc per device, discovered from the pin list (≤100), edited in place, pinned once — `web/src/telegram/state-channel.ts:40,45,70,104,125`. Core's `pick_index` already ignores non-index pins — `crates/mediagram-core/src/api/channel_index.rs:71`.
- Web cadence: awaited at start, every 5 min (min 60 s), on SIGTERM — `web/src/index.ts:201,203,328`, `web/src/config.ts:176`.
- grammers 0.10 can edit a message's media — `grammers-client-0.10.0/src/client/messages.rs:826`; upload+pin pattern in `crates/mediagram/src/commands/push_index.rs:98-135`.
- `client()` clones the Telegram client and drops the core lock — `crates/mediagram-core/src/api/session.rs:125-131`; a sync round cannot stall `read()`.
- Android `openOn` always starts at 0 — `android/feature/player/src/main/kotlin/DefaultPlayerHandle.kt:179`.

## Decisions (recommended; see phase files)

1. **State lives in the Rust core** (`state.db` beside the catalog, rusqlite already bundled). One port of merge/record/sync, pinned to the TS by shared JSON fixtures (lesson 2026-09-18). UI rules (resume thresholds, Next up) are ported to Kotlin, pinned by the same fixtures.
2. **Format:** write format 1 exactly as the web does. Device = random UUID in `state_meta.device_id`, never hostname.
3. **Cadence:** on app start (awaited ≤5 s), every 5 min while foreground, on leaving the player, on activity `onStop`. Never throws; failure = log line + unchanged local DB.
4. **Profiles:** port "Who's watching?" (choose + create). Profiles from other devices arrive via sync. Rename/delete deferred — sync cannot express them.
5. **Shelves:** same names, rules, and See-all targets (Continue → Continue, Next up → Series). Masthead grows to the web's eight entries as a scrollable tab row; kept shelves set apart.
6. **Toggles** live in the player, as on the web: Watchlist / On the list, Kids / For kids, Add to list.

## Answers (user, 2026-09-22)

- **Q1** Yes — share Watchlist/Collections/Kids; phase 08 is in scope.
- **Q2** Was off; `MEDIAGRAM_SYNC_STATE=1` now added to `web/.env` (takes effect on the player's next restart — never restart it for this; uploads may be running).
- **Q3** Android syncs by default (recommended default, user chose to sync).
- **Q4** Yes — "Start over" also deletes this device's watch state.
- **Q5** Settings plan is not running; 08 takes the next free web schema version at the time.
- **Q6** Fix in this work: Android interleaves a level's lessons and folders by number, as the web does (`library.js:182-196`). Goes in phase 06, before Next up depends on page order.
- Branch: commit phase by phase on `main` (user's standing preference this session), not a feature branch.

## Rollback

Each phase is one revertable commit set. `state.db` is a new file; deleting it = pre-feature app. A malformed Android doc is dropped row by row by the web (`sync-record.ts:78`); unpinning the device's message withdraws it from every reader.
