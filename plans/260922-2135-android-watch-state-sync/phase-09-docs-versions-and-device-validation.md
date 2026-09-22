# Phase 09 — Docs, versions, device validation

## Context links

- `CLAUDE.md` § Versioning (three manifests, in step; `versionCode` +1 separately), § Surface Parity (write deliberate differences down)
- `Cargo.toml:6`, `web/package.json:3`, `android/app/build.gradle.kts:12-13` — all `0.23.0`, `versionCode = 4` today
- `docs/system-architecture.md:391-451` (§8, "What it does not have yet" says no watch state)
- `scripts/check.sh`
- Memory: never tap "Start over" on the device; never start the real web player for UI checks (auth key contention) — use the running one only as a sync peer

## Overview

- Priority: P1. Status: pending. Blocked by 07 (and 08 if taken).
- Close the docs, bump the version once at merge, prove it end to end on the tablet.

## Requirements

- Docs: §8 gains "Watch state" (state.db in core, sync cadence, profiles); §7 notes the channel's state documents are also written by Android; "What it does not have yet" loses resume/watched/watchlist/lists.
- Parity notes (where readers look — §8): deliberate differences: profile rename/delete not on Android; Play all / Kids run wait for the player queue; sync runs by default on Android (Q3); lists per device if Q1 = no.
- Older plans: mark `260922-0124-android-web-parity` phases 4, 5, 8, 9 and `260920-2221…/phase-03` as superseded by this plan.
- Version: minor bump (new feature) computed against `main` at merge, e.g. `0.23.0 → 0.24.0` in all three manifests; `versionCode` +1.
- `docs/project-changelog.md` entry if the file exists at merge.

## Implementation steps

1. `./scripts/check.sh` with `ANDROID_HOME` set (otherwise it skips Android and proves nothing).
2. `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh`
3. `cd android && ./gradlew :app:installDebug` onto the attached tablet (`adb devices`).
4. Device matrix (below). Watch `adb logcat -s sync` throughout.
5. Docs + plan status edits; version bump; commit.

## Device test matrix (tablet + the already-running web player as peer)

| # | Step | Expect |
|---|---|---|
| 1 | Fresh install, sign-in state kept | Picker lists web viewers within 5 s |
| 2 | Pick viewer, open Home | Continue/Next up equal the web's for that viewer |
| 3 | Watch 4 min, Home button, swipe away, reopen | Resumes ±10 s; web Continue shows it within 5 min |
| 4 | Finish an episode on tablet | Tick on card; Next up moves to next episode; web agrees after a round |
| 5 | Finish on web, tablet had a position | Tablet drops it from Continue (tombstone rule) |
| 6 | Airplane mode, play, pause, leave | Plays from cache/fails as before; position kept locally; one `sync` warning per round, no crash |
| 7 | Watchlist / Kids / list edits | Shelves update immediately; persist across restart; on web too if 08 |
| 8 | Rotate during playback | No restart from resume point, no double save |
| 9 | Two viewers | Switching viewer switches Continue; Kids shelf shared |
| 10 | Push latency, both ways (needs `WatchSync.soon()` on `STATE` — push-updates phase 04 remainder) | Web position → tablet Continue, and tablet → web Continue, each in **seconds**, not at the 5-min timer. Handed on from `plans/260922-2222-telegram-push-updates/reports/validation-260923-0055-push-update-latency-report.md`; core half already measured at 5.0 s |

**Never** tap "Start over" on the tablet.

## Todo

- [ ] check.sh green (Android half actually ran)
- [ ] core rebuilt, app installed
- [ ] matrix 1–9 pass
- [ ] docs §7/§8 + parity notes
- [ ] older plans marked superseded
- [ ] version bump ×3, versionCode +1
- [ ] plan.md statuses + review section

## Success criteria

All matrix rows pass; `check.sh` green; three manifests show one version.

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Web sync off (Q2) → rows 2–5 untestable | M×H | Ask before running; otherwise test phone-only rows and two-install sync via emulator |
| First real push from a new device writes a bad doc | L×M | 03's golden test; inspect the pinned doc in Telegram after row 3; unpin to withdraw |

## Security

Confirm logcat never prints positions of other viewers, auth material, or chat ids — only counts and error text.

## Next steps

Parity phase 7 (autoplay/queue) can now use `playOrder` and the recorder.
