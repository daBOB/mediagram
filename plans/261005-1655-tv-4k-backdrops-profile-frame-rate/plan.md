# TV 4K follow-ups: backdrops, Baseline Profile, frame-rate matching

**Goal:** close the three gaps found by `plans/reports/researcher-261005-0051-4k-tv-ui-best-practices-report.md`:
1. **Backdrops:** the TV gets w780 backdrops stretched across a 1920 px hero.
2. **Baseline Profile:** the app ships none of its own.
3. **Frame-rate matching:** none is done on TV.

**Source:** research report above (claims checked against their sources); scout 2026-10-05.

## User decisions (2026-10-05)

| Question | Answer |
|---|---|
| Scope | All three: TV backdrops at w1280, an app Baseline Profile, frame-rate matching |
| Existing w780 backdrops | **Re-fetch when narrower.** Record the width each backdrop was fetched at; one narrower than the device now wants counts as missing |
| Frame-rate matching on the current monitor | Build it anyway. The HP OMEN is 59.94 Hz only, so it gains nothing today; it is for a future 24 Hz-capable display |

## Phases

| # | Phase | Depends on | Status |
|---|---|---|---|
| 01 | [TV backdrops at w1280, re-fetch narrower ones](phase-01-tv-backdrops-w1280-refetch-narrower.md) | — | done (merged, review fixes applied) |
| 02 | [App Baseline Profile](phase-02-app-baseline-profile.md) | — | done (merged, review fixes applied) |
| 03 | [TV frame-rate matching](phase-03-tv-frame-rate-matching.md) | — | done (merged, review fixes applied) |
| 04 | [Close-out: version, docs, device walk](phase-04-close-out.md) | 01–03 | in progress (profile shipped in 0.117.1; refresh-rate walk waits for the TV to be on) |

Phases 01, 02 and 03 touch no shared files, so they can run in parallel worktrees. The exception is `android/gradle/libs.versions.toml`, which only phase 02 edits.

## Acceptance criteria

- **Backdrops:**
  - On a TV (`UI_MODE_TYPE_TELEVISION`), `DeviceBackdropWidth` returns 1280. Phones stay at 780; tablets (sw ≥ 600dp) stay at 1280.
  - A backdrop recorded at a narrower width than requested is downloaded again. One at an equal or wider width is left alone. One with no record counts as narrower and is downloaded once, then recorded.
  - A failed re-download keeps the old image and does not count the title as failed.
- **Hero after upgrade:** on the TV box, the Home hero shows the new w1280 image once enrichment has run. A stale Coil cache entry fails this.
- **Baseline Profile:**
  - The release APK contains `assets/dexopt/baseline.prof`, generated from an app journey and committed under `android/app/src/release/generated/baselineProfiles/`.
  - `profileinstaller` is a direct dependency of `:app`.
- **Frame-rate matching:**
  - On TV, playing a title with a known frame rate picks the display mode with the same resolution whose refresh rate is the closest whole multiple of it, if one exists. The previous mode comes back when the player screen closes.
  - With no such mode (the OMEN), nothing changes.
  - Phones and tablets are untouched.
- **Checks:** `cargo test` (workspace), `./gradlew testDebugUnitTest lint :app:assembleRelease` and `scripts/check.sh` (if present) pass.

## Out of scope

- An in-app toggle for frame-rate matching. Add one if a display's mode switch proves annoying.
- Posters, which stay at their current size.
- Any change to the web player. It already asks w1280 and cannot switch display modes.
- Macrobenchmark suites beyond the one profile-generating journey.

## Deliberate differences (Surface Parity)

- **Frame-rate matching is TV-only.** A browser cannot change the display mode. Phones and tablets keep Media3's default `ONLY_IF_SEAMLESS`, which already does the seamless switches their panels allow. Write this down in the code that applies it.
- **The Baseline Profile is an Android build artefact.** It has no web counterpart.

## Risks

- **Desktop backdrop re-fetch.** The desktop `mediagram posters` command shares `poster_files.rs`. Its existing backdrops have no width record, so they are re-fetched once at 1280. This is a one-off cost, accepted.
- **Device for profile generation.** It is an open question (phase 02); the user chooses at the plan gate.

## Review

### 2026-10-05

- **Phases 01–03** were built in parallel worktrees and merged into main.
- **Checks:** `scripts/check.sh` passed on the merged tree. The release APK contains `assets/dexopt/baseline.prof`.
- **Code review:** `plans/reports/code-reviewer-261005-1957-tv-4k-followups-review-report.md`. It found 1 critical, 1 high and 3 medium issues; all are fixed in `plans/reports/fullstack-developer-261005-2011-tv-4k-review-fixes-report.md`.
  - The critical issue was that profile generation would have uninstalled the box's app.
  - The high issue was custom backdrops being overwritten by TMDB's.
- **Design change, made by Claude and not a user decision:** a display mode that already fits the frame rate is kept instead of switching to the smallest multiple.
- **Found on the box:**
  - It now drives a 2024 **Hisense** TV with 23.976/24/25/50 Hz modes and HDR, so frame-rate matching is live there. The plan's "no gain on the OMEN" premise no longer holds.
  - It ran app **0.109.1**.
  - User decision (2026-10-05): generate on the box, then release so it self-updates. Released as 0.117.1, since the generation build itself installs 0.117.0.

### 2026-10-06

- **Profile generated on the box** after three generator fixes. The journey still misses the bar on most iterations, but merged iterations cover Home, the Movies page, the wall and the title page. Details in phase 04.
- **The box sleeps through HDMI-CEC.** `stay_on_while_plugged_in` was already 3, so the earlier sleep came from CEC standby when the TV went off or switched input. For this session it was set to 15; the original value is 3.
- **Refresh-rate walk is not done.** With the TV off, the box sees no EDID and offers only a fallback 1080p60 mode.
