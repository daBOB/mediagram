# Phase 06: Close-out (docs, device walks, release)

## Context links

- [spec.md](spec.md): see Look, Testing and Rollout step 6
- [plan.md](plan.md): see Global Constraints
- Memory notes: `android-device-validation`, `device-test-walks-must-not-change-settings`, `pin-android-installs-to-a-serial`, `android-tv-self-update`, `bump-versions-by-pattern`

## Overview

- **Priority:** required to finish the work.
- **Status:** pending.
- **Depends on:** phases 01–05 merged on `main`.
- **Who does it:** the lead. This phase merges, documents, walks the devices and asks before publishing.

## Key insights

- **Phone/tablet updates by adb only.** Play Protect blocks self-update there. The TV updates itself from the pinned release on the channel, and publishing that release needs the user's word.
- **Device walks only look and navigate.** A past TV walk pressed OK and moved the cache budget from 256 to 8 GB.
- **Stale native library.** `.so` files are gitignored, and a stale one crashes the app at launch. This plan has no Rust changes, but the profile security branch may land in between; if it does, rebuild the native core first.

## Requirements

- The design docs describe the shipped card:
  - Web: a Player section in `web/DESIGN.md`, plus the second blur exception named in the `shell.css` masthead comment.
  - Android: the root `DESIGN.md` records the frosted no-blur card and why. These are §499–504 (the existing no-blur rule, now extended to the player), §790 and §808–811 (the player is no longer unstyled).
- `docs/system-architecture.md`, under "Television differs", records any deliberate TV difference phase 05 chose.
- `check.sh` passes on `main`.
- The changelog has one entry per merged phase. Versions are bumped in all three manifests, in step.
- Tablet and TV box walks are done (look and navigate only), with screenshots in this plan's `reports/`.

## Related code files

- **Modify:**
  - `web/DESIGN.md`
  - `DESIGN.md`
  - `docs/system-architecture.md`
  - `docs/project-changelog.md`
  - `Cargo.toml`
  - `web/package.json`
  - `android/app/build.gradle.kts`
  - this plan's `plan.md` (statuses and review log)
- **Create:** `reports/device-walk-report.md` and its screenshots.

## Tasks

### Task 1: Design docs match the shipped card

- [ ] Read the merged web card (`web/public/styles/playback.css`, `index.html`) and the Android card composables named in phases 04 and 05.
- [ ] `web/DESIGN.md`: add a `## Player` section. It covers:
  - the card (fill, blur, border, radius, width, inset);
  - the three rows;
  - the menus and the sidebar, with the watched/progress/current treatments;
  - the hide rules;
  - the 15 s skip.

  Every value is copied from the merged CSS, not from the spec.
- [ ] `DESIGN.md`:
  - extend the no-blur note (§499–504) to the player card, with the reason (the video surface, HDR/DV);
  - replace "the Android player is unstyled/undocumented" (§790, §808–811) with the card's tokens.
- [ ] `docs/system-architecture.md`: add any TV difference phase 05 recorded, for example the sidebar's focus entry.
- [ ] Commit: `docs: player control card in the design system`.

### Task 2: Gates on main

- [ ] Run `scripts/check.sh` from the repo root. Expect exit 0. If it fails, fix the failure at its source; never by skipping.
- [ ] Run the web suite: `cd web && bun run lint && bun run typecheck && bun test`. Expect 0 failures.

### Task 3: Tablet walk (look and navigate only)

- [ ] Run `ANDROID_SERIAL=caad49da` and install the debug build pinned to that serial.
- [ ] Lock the rotation (`cmd window user-rotation lock 1`). Open a series episode the profile has already part-watched.
- [ ] Screenshot each of these:
  - the card;
  - the Speed menu open (then close it with Back);
  - stats on (then off);
  - the sidebar open on the current season;
  - a season switch;
  - a film (no ☰, ⏮ or ⏭).
- [ ] Never pick a menu value, never pick a sidebar row, and never press Back at the library root.
- [ ] Note in the report: card width, wrapping, and whether every target is at least 48 dp.

### Task 4: TV box walk (look and navigate only)

- [ ] Install the release build (the box runs the self-updating release build, so a debug build would not install over it):
  1. `cd android && ./gradlew -q assembleRelease`.
  2. Check that the APK under `app/build/outputs/apk/release/` is signed with the release key (certificate SHA-256 prefix `5840181d…f576`) using `apksigner verify --print-certs` from the newest build-tools.
  3. `adb -s 192.168.0.35:5555 install -r <apk>`.
- [ ] Open the same kind of title. Check, in this order:
  - focus starts on ▶/❚❚;
  - the D-pad moves along row 3, then up into row 2;
  - a menu opens on its current value, and Back returns to its button;
  - the sidebar opens on the current row, and Back closes it;
  - Back closes stats, then the card.
- [ ] Screenshot each step. Change no setting.

### Task 5: Release

- [ ] Changelog entries exist for each merged phase, and the versions match in `Cargo.toml`, `web/package.json` and `versionName`. Refresh `Cargo.lock` with `cargo metadata -q --format-version 1`.
- [ ] Mark every phase as done in `plan.md` and fill in the review log.
- [ ] **Ask the user** before `scripts/release-android.sh`, which publishes to the channel and updates the TVs. Do not publish without a yes.

## Success criteria

- The docs describe what shipped, with values matching the code.
- `check.sh` and the web suite are green.
- The walk report has screenshots for every step on both devices, and no setting was changed.
- The channel release has the user's yes, or is recorded as deferred.

## Risks

- **A walk changes a setting by accident.** Navigate only, and chain adb key events in one call so the controls don't hide halfway.
- **A test play lands in Continue.** Use the TV box's "TV test" profile for the walk.
