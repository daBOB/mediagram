# Phase 6: Menus, docs, version, mouse-free validation

**Context:** [plan.md](plan.md) · phone refs: `ui/OverflowMenu.kt`, `ui/settings/*`, `ui/system/*` · docs: `docs/system-architecture.md` (~553–561, 725), `docs/development-roadmap.md` (~164), `docs/project-changelog.md`

## Overview

- **Priority:** Medium — needed before merge.
- **Status:** pending
- **Deliverable:** everything in the phone's overflow menu is reachable on TV; docs describe the TV surface and its deliberate differences; version bumped; one full mouse-free pass on the emulator.

## Key insights

- Phone overflow has five items: System, Settings, Update library, TMDB key…, Start over. TV has no overflow; the masthead's **System** entry opens a TV menu page listing the same five, in the same order (`MenuActions`), reusing `SystemViewModel`, `SettingsViewModel`, `CacheBudgetViewModel`, `FetchViewModel` and the moved row builders.
- Web System page uses label + figure rows in the page's type, not a monospace dashboard — TV does the same.
- TMDB key entry via `TvTextQuestion`, masked.
- Deliberate differences must be written down (Surface Parity): no list/plates toggle, text entry by IME, no MediaSession, no search (shared Android gap), anything found in phase 5 about up-next.

## Related code files

- Create: `android/ui-tv/src/main/kotlin/ui/tv/system/{TvMenuPage.kt,TvSystemScreen.kt,TvSettingsScreen.kt,TvTmdbKeyScreen.kt}`
- Modify: `TvLibrary.kt`, `docs/system-architecture.md`, `docs/development-roadmap.md`, `docs/project-changelog.md`, `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts`, `plans/260919-0034-android-foundation-phone-tablet-tv/phase-04-tv-surface.md` (status → superseded, link here)

## Implementation steps

### Task 1: Menu page and screens
- [ ] **1.1** `TvMenuPage` with the five actions; first focused. Start over → `TvConfirmDialog` (Cancel focused).
- [ ] **1.2** `TvSystemScreen`, `TvSettingsScreen` (incl. cache budget choices, sign out behind confirm), `TvTmdbKeyScreen`; completions handled by shared `SettingsOutcomes`.
- [ ] **1.3** Robolectric: each screen has initial focus; Back returns to menu page, then masthead.
- [ ] **1.3a** Carried from phase 7: after coming back to Play all, a Remove on the list leaves nothing focused — `TvList.kt` `takesArrivalFocus = !backFromPlayAll || afterRemoval != null`; test it.
- [ ] **1.3b** The phone's menu changed on main (merged at 2d7dfd8): read `ui-mobile/.../AppChrome.kt` / `OverflowMenu.kt` for the current items and mirror them, not the list above if it differs.
- [ ] **1.4** Commit — `feat(android): system and settings on a television`.

### Task 2: Docs
- [ ] **2.1** `system-architecture.md`: `ui-tv` and `ui-common` modules, what moved into `feature:*` and why, TV key model, focus-restoration rule.
- [ ] **2.2** `development-roadmap.md`: "Later: the television surface" → done/in review, with remaining gaps.
- [ ] **2.3** A "Television differs from the web player" section (in `system-architecture.md` next to the surface section) listing each deliberate difference with its reason.
- [ ] **2.4** `project-changelog.md` entry. Mark 260919 phase 4 superseded.

### Task 3: Version
- [ ] **3.1** Merge `main` first (as at 2d7dfd8 — merge, not rebase; the phone's behaviour wins, the TV branch's shared-code locations win). Bump by regex, not exact string (memory: another session commits to main): current version + minor in `Cargo.toml` `[workspace.package]`, `web/package.json`, `android/app/build.gradle.kts` `versionName`; `versionCode` + 1. `cargo check` to refresh `Cargo.lock`; confirm all three agree.

### Task 4: Final validation
- [ ] **4.1** `scripts/check.sh` with `ANDROID_HOME` set → all green (clippy, cargo test, bun, gradle test + lint).
- [ ] **4.2** Emulator, fresh `pm clear` (**emulator only**), key events only: setup → profile `TV test` → every masthead section → series/season/episode → play/seek/pause/back → System menu → every item → Back out of the app. Screenshots into `reports/`.
- [ ] **4.2a** Real box `192.168.0.35:5555` (signed in — never pm clear/Start over/sign out): the same walk on the `TV test` profile, plus a DTS film with sound and the Movies wall scroll on the benchmark build.
- [ ] **4.3** Phone smoke on the real device **without** clearing data (memory: never start over on it): launch, browse, open a title, back. Confirms phase 1 changed nothing visible.
- [ ] **4.4** Write `reports/validation-<date>-android-tv-emulator-walk-report.md`: what was walked, titles played, preferences touched and reset, open gaps.
- [ ] **4.5** Code review (`code-reviewer` agent) over the branch diff; fix; commit — `docs: describe the television surface` and version commit.

## Todo list
- [ ] Menu page + system/settings/TMDB screens
- [ ] Docs incl. deliberate-difference list
- [ ] Version bumped in all three manifests
- [ ] check.sh green
- [ ] Emulator mouse-free walk + phone smoke
- [ ] Review done

## Success criteria
A fresh TV reaches every screen the phone has, using only a remote; docs name every place TV differs from the web and why; version agrees in all three manifests.

## Risk assessment
| Risk | Mitigation |
|---|---|
| Main moved during the branch | Rebase before version bump; regex bump |
| Phone regression from phase 1 moves unnoticed | Phone suite + real-device smoke |

## Security considerations
TMDB key and sign-out behind masked field / confirm; nothing secret in screenshots committed to `reports/` (crop or skip the TMDB screen).

## Next steps
Merge `feat/android-tv-ui` → `main` after user review. Candidates after: sign-in handover from phone, MediaSession, search on both Android surfaces.
