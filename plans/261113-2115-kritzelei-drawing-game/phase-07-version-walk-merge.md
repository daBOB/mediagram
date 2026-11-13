# Phase 07 — Version, device walk, merge

**Priority:** last. **Status:** todo. **Spec:** Success; "The iOS app joins the Spieltisch version line"; my call 12.

## Overview

Bump Spieltisch one minor in every manifest, including the iOS app. Walk a full game on the real TV box. Check the latency there if phase 01 asked for it. Then merge to `main`.

## Key insights

- Another session commits to `main` and bumps versions. Bump **by pattern**: read the current Spieltisch version on `main` at merge time and take the next minor. Never replace an exact old string, because a stale one silently changes nothing.
- When this plan was written, Spieltisch was 0.179.0 and the iOS app 0.153.3 / 153003. CLAUDE.md now names `ios/controller/Info.plist` in the Spieltisch line (approved with the spec).
- Sending a build to the TV boxes (`scripts/release-spieltisch.sh`) publishes to the channel. That reaches others, so ask the user first. A device walk can sideload instead (pin the serial, never a bare `installDebug`).

## Task 1: Version and docs

**Files:**
- `crates/mediagram-game/Cargo.toml`
- `crates/spieltisch-core/Cargo.toml`
- `crates/mediagram-game/client/package.json`
- `android/spieltisch/build.gradle.kts` (`versionName`)
- `ios/controller/Info.plist`: `CFBundleShortVersionString` and `CFBundleVersion` = major·1,000,000 + minor·1,000 + patch, e.g. 0.180.0 → 180000
- `Cargo.lock` (regenerated)
- `docs/project-changelog.md`

- [ ] **Step 1:** Merge `main` into `feat/kritzelei` first. Then set every file above to the next Spieltisch minor.
- [ ] **Step 2:** Changelog entry: Kritzelei, the native drawing board on the iPad, and the iOS app joining the Spieltisch version.
- [ ] **Step 3:** `scripts/check.sh` → green. `(cd ios/controller/ControllerCore && swift test)` → green. `(cd ios/controller && xtool dev build)` → builds. `trail check` → no conflict, or the reason written in the commit message.
- [ ] **Step 4: Commit** — `chore(spieltisch): Kritzelei is Spieltisch <version>; the iOS app follows the line`.

## Task 2: The full walk on the real devices

Before this step, ask the user: walk on the TV box (sideload) or on funstation, and who holds the phones.

- [ ] **Step 1:** Put the build on the chosen box and refresh the iPad (`scripts/refresh-ios-controllers.sh`).
- [ ] **Step 2:** Play a full game with at least 3 players on phones: a browser, the app, and an iPhone if one is at hand. Every turn should go hand-over → drawing → who-got-it → reveal, through to the ceremony. Expected:
  - the points are right
  - the word never shows on the TV or a guessing phone before the reveal
  - the drawer's phone buzzes at their turn and the guesser's at „Richtig!“
- [ ] **Step 3:** A second game with „Nur für Kinder“ on: only kid words.
- [ ] **Step 4:** Latency on the box, only if phase 01 landed between 120 and 200 ms. The user films the iPad and the TV together in slow motion (240 fps) during a long stroke; count frames from the finger to the line. Target: under 0.2 s.
- [ ] **Step 5:** Write `plans/261113-2115-kritzelei-drawing-game/reports/device-walk-report.md`. Fix findings, each with its own commit, then re-run `scripts/check.sh`.

## Task 3: Merge and wrap up

- [ ] **Step 1:** Use superpowers:finishing-a-development-branch. Merge `feat/kritzelei` into `main` (the user keeps finished work on `main`). Remove the worktree only after `git status` there is clean.
- [ ] **Step 2:** Set every phase in `plan.md` to done and add a short review section.
- [ ] **Step 3:** Update the memory notes `game-night-backlog.md` (drawing game done) and `game-night-controller-apps.md` (iOS board shipped, version line).
- [ ] **Step 4:** Ask the user whether to push and whether to release Spieltisch to the boxes. Do neither unasked.

## Success criteria

Everything in the spec's Success section holds on the real devices: a full game, the TV keeping up, correct results, recovery, and green tests. Spieltisch and the iOS app carry the same version, and the work is on `main`.
