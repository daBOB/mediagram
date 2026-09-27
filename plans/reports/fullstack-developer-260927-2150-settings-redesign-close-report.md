# Phase Implementation Report

### Executed Phase
- Phase: phase-05-verify-docs-version (closing phase)
- Plan: /home/andre/Workspace/mediagram-channel-index/plans/260927-1731-android-settings-system-redesign
- Worktree: /home/andre/Workspace/mediagram-channel-index, branch feat/android-settings-redesign, HEAD f7a89f83 (0.69.3) at start
- Status: completed, not committed (lead commits, per instructions)

### Files Modified

Docs / plan (as directed):
- `DESIGN.md` (+51/-6): Top app bar section gained a "Settings/System draw no bar"
  exception; new `### Artwork` component (Default/Blurred/Artwork/Solid, TV answering
  only accent+artwork of Appearance's four choices); "What this system does not yet
  cover" first bullet corrected — Settings/System is composed and shipped (phone,
  tablet, TV), not "token contract, not yet composed as Kotlin".
- `docs/system-architecture.md` (+21/-4): new `### Settings and System` subsection
  (two-pane index, System as a Settings entry with a menu shortcut, Storage merging
  cache + home server, Appearance's three questions); TV bullet corrected from
  "Appearance offers the accent only" to "the accent and artwork".
- `docs/project-changelog.md` (+18): new `## 0.69.4` entry — the scroll-position fix
  under **Fixed**, the doc updates under **Changed**, explicitly not called docs-only
  since real behaviour changed.
- `docs/development-roadmap.md` (+33/-1): new `## Android: Settings/System redesign,
  web tokens and Artwork` section (phase table, all complete, follow-up count);
  one-line note under the older TV plan's own table that its phase 6 (System/Settings
  scope) was superseded rather than built.
- `plans/260926-1330-android-editorial-departments-parity/plan.md:38`: superseded note
  on the "Artwork not ported" decision.
- `plans/260927-1731-android-settings-system-redesign/plan.md` (+107/-4): status →
  complete, phase 05 row → complete, new `## Review` section (what shipped per phase
  with commits, verification, 14-item follow-up list, docs list).
- `plans/260927-1731-android-settings-system-redesign/phase-05-verify-docs-version.md`:
  status → complete, todo list checked.

Version (patch, docs/fix-only release):
- `Cargo.toml`, `Cargo.lock` (via `cargo check -q -p mediagram`, workspace-member
  versions only — diff confirmed clean), `web/package.json`,
  `android/app/build.gradle.kts` (`versionName` only) — `0.69.3` → `0.69.4`.
  `versionCode` left at 18: CLAUDE.md § Versioning excludes it from the semver rule
  entirely ("not part of this... counts builds for Android's own upgrade check"), and
  the phase 01–04 commits themselves never bumped it across four version bumps
  (`git log -p` on `build.gradle.kts` shows `versionCode = 18` unchanged through
  0.69.0→0.69.3) — confirms it tracks distributable builds, not releases.

Code (one fix, found during device verification, in scope per phase spec step 3
"note deltas; fix those inside this redesign"):
- `android/ui-mobile/src/main/kotlin/ui/settings/SettingsPage.kt`: `rememberScrollState()`
  → `remember(section) { ScrollState(0) }`. EXPANDED width shares one call site across
  a section switch, so the bare `rememberScrollState()` carried the outgoing section's
  scroll offset into the incoming one — reproduced on-device (scrolled Appearance down,
  switched to Storage, landed on "Catalogue"/"Cache" instead of the "STORAGE" title) —
  the same bug class `TvSettingsPanes.kt` had already been fixed for in phase 04's own
  review round. Fixed the identical way.
- `android/ui-mobile/src/test/kotlin/ui/settings/SettingsPanesTest.kt` (+14): new test
  `eachSectionOpensScrolledToItsOwnTopRatherThanKeepingAnotherSectionsOffset`, mirroring
  the TV test's own approach (open Storage fresh, capture the title's top bound, scroll
  Appearance, switch back to Storage, assert the top bound is unchanged).

Screenshots (new, `plans/reports/`):
- `settings-storage.png`, `settings-appearance.png`, `settings-appearance-artwork.png`,
  `settings-system.png` — the four Settings sections, tablet landscape.
- `artwork-default-film-page.png`, `artwork-default-movies-department.png`,
  `artwork-blurred-film-page.png`, `artwork-blurred-movies-department.png`,
  `artwork-solid-film-page.png`, `artwork-solid-movies-department.png` — Artwork modes
  on "Der Astronaut – Project Hail Mary" and the Movies department.
- `settings-telegram.png` (account name + 9 session locations/IPs) kept out of
  `plans/reports/` per this plan's own Security note — it is in the session scratchpad
  (`settings-telegram-account-pii.png`) instead. This is a deliberate deviation from
  the "save to plans/reports/" instruction, scoped narrowly to this one screenshot; all
  others are in `plans/reports/` as asked.

### Tasks Completed

All of phase-05's todo items: suites green, tablet install pinned to caad49da/test
profile, four section screenshots vs mockups (one in-scope delta found and fixed),
Artwork-mode screenshots (Default/Blurred/Solid, film page + Movies department, home
skipped per lead), other-screen sweep → 14-item follow-up list in plan.md § Review,
docs updated (DESIGN.md, system-architecture.md, changelog, roadmap, superseded note),
version bumped by pattern with Cargo.lock synced.

### Tests Status

- Type check / compile: pass, clean.
- Unit tests: `./gradlew testDebugUnitTest lint` (whole project) — **BUILD SUCCESSFUL**,
  run twice (before and after the SettingsPage.kt fix + import cleanup). 1526 tests
  across the Android modules plus 9 in `core:model`'s own JVM suite; lint clean
  everywhere, no new warnings.
- Device: tablet `caad49da` only, pinned every command. Fresh `adb install -r -d`
  (gradle's own `installDebug` refused a downgrade against a versionCode 19 build
  already on the device from other work; `adb install -r -d` preserved app data and
  installed cleanly). Test profile confirmed active throughout (top-right "test"
  chip in every screenshot). TV box `192.168.0.35:5555` and any emulator were never
  touched. Artwork reset to Default and the app left on the test profile at the end.

### Deltas vs mockups

- Four Settings sections structurally match round-2 mockups: two-pane layout, page
  title weight (the phase-03 static-font fix holds on a second device pass — no
  wedge-serif regression), left-pane width, spacing, Storage/System ledger columns.
- One regression found and fixed: shared scroll position across Settings sections at
  expanded width (see Files Modified). Confirmed fixed with a before/after screenshot
  pair on-device, plus the new Robolectric regression test.
- Artwork modes render as expected: Blurred softens the hero on both the film page and
  the Movies department; Solid drops the hero art, gradient and pull-quote entirely on
  both (confirmed — Movies department fell back to its plain grid, no cover story).

### Follow-ups

14 items total in `plan.md` § Review, none fixed here per decision 2 (list, don't fix):
the phase-05 spec's original 11 (two annotated as partly addressed by the home
web-parity plan on another branch), plus 3 this session's own sweep and the phase
reports surfaced: prose still in Geist where the web uses Newsreader (4 phone call
sites + TV's own documented-but-disagreeing choice), Storage's index-row status
truncating to "home cache co…", and one stale sentence in `DESIGN.md` itself that
item 12 makes inaccurate.

### Issues Encountered

- `installDebug` refused a downgrade (device carried versionCode 19 from other work on
  the shared tablet); worked around with `adb install -r -d`, which preserves app data
  and does not require the tablet's other builds/profiles to be disturbed.
- The scroll-position bug (see Files Modified) was not anticipated by the phase spec;
  found by following its own step 3 ("note deltas; fix those inside this redesign")
  during the tablet comparison pass. Flagged explicitly here and in the changelog
  rather than silently folded into a "docs-only" release, since the lead's phrasing
  assumed no code would change this phase.

### Next Steps

Lead commits and reviews the diff (branch not merged). No blocking dependents;
follow-up list is ready to file as GitHub issues if wanted (`docs/agents/issue-
tracker.md`).

---

**Status:** DONE
**Summary:** Phase 05 verified and closed the Settings/System redesign — tests and lint
green, tablet screenshots for all four sections and three Artwork modes captured, one
real regression (shared scroll state across sections) found and fixed with a test,
docs updated across DESIGN.md/system-architecture.md/changelog/roadmap, version bumped
to 0.69.4 by pattern, plan.md § Review written with a 14-item follow-up list.
**Concerns/Blockers:** None blocking. One deliberate deviation from instructions: the
Telegram section screenshot (PII) was kept in the scratchpad rather than
`plans/reports/`, per this plan's own Security note — flag if the lead wants it
relocated anyway before commit.
