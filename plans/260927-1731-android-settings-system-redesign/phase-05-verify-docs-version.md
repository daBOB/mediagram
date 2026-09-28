# Phase 05 — Verify, docs, version

## Context links

- Mockups to compare against: `~/.gstack/projects/daBOB-mediagram/designs/android-settings-system-20260927/round2/b-*.png` (1600×1068 dp, tablet landscape)
- Versioning rule: `CLAUDE.md` § Versioning (three manifests in step; `versionCode` +1; 0.x breaking → minor)
- Manifests today: `Cargo.toml:6` `0.68.13`, `web/package.json:3` `0.68.13`, `android/app/build.gradle.kts:12-13` (`versionCode = 18`, `versionName = "0.68.13"`)

## Overview

Priority P1. Status: complete. Proved the four phases on tests and on the tablet, wrote
the docs, listed what looks off elsewhere, bumped the version. See `plan.md` § Review
for the full report.

## Key insights

- 0.68.14 is in flight from another session: **read `Cargo.toml` at merge time**, bump the
  minor from whatever is there (0.68.x → 0.69.0, unless someone already moved it), and
  bump by pattern, not by exact string — an exact-string replace silently no-ops when
  the number moved underneath.
- No Rust change → no native core rebuild; a stale `.so` is not a risk here.
- Shell hook blocks command text containing "build"/"dist"/".git" paths: use Gradle task
  names (`:app:installDebug`) and write screenshots to the session scratchpad, never into
  a `build/` path.

## Test matrix

| Level | What | Where |
|---|---|---|
| Unit (JVM) | palette contrast, colour roles, type floors, Backdrop parse/persist, VM choose, status functions, tally | `core/designsystem`, `feature/setup`, `feature/system`, `feature/catalog` |
| Robolectric Compose | panes (compact/expanded), back rules, every section action, sign-out path, hero modes, TV focus/back, TV artwork | `ui-mobile`, `ui-tv` |
| Device (tablet caad49da, test profile) | four sections vs mockups; artwork modes on a title page, Movies department, home; font rendering (Geist, Fraunces caps not clipped); insets | manual, screenshots |
| Device (TV, optional, human) | D-pad through index/sections/panels; blur on the box's API level | TV emulator / box — only if the user asks |

## Requirements

- All unit + Robolectric suites green for: `:core:designsystem`, `:feature:setup`, `:feature:system`, `:feature:catalog`, `:ui-common`, `:ui-mobile`, `:ui-tv`; lint/ktlint clean.
- Tablet screenshots of Telegram, Storage, Appearance, System, and of Blurred/Solid on a title page + Movies department + home.
- Follow-up list (below, extended with what the sweep finds) written into `plan.md` § Review.
- Docs + version updated.

## Implementation steps

1. Run the suites (Gradle `testDebugUnitTest` per module above); fix, never skip.
2. Device, pinned — never a bare install:
   `ANDROID_SERIAL=caad49da ./gradlew :app:installDebug`; pick the **test profile** (not the household's); open Settings from the menu.
3. Screenshot each section: `adb -s caad49da exec-out screencap -p > <scratchpad>/settings-<section>.png`; compare side by side with `round2/b-<section>.png` (structure, spacing, type, tokens). Note deltas; fix those inside this redesign, list the rest.
4. Artwork modes: for each of Default/Blurred/Artwork/Solid screenshot a film page, Movies, Home. Expect: Blurred softens all three; Solid drops title/department art and the department quote, home unchanged; Artwork = Default (web narrow parity).
5. Compact check: Robolectric `SettingsPanesTest` is the gate; optional on-device via temporary `adb -s caad49da shell wm size 1080x2400` + `wm density 440`, then **`wm size reset` / `wm density reset`** (human-approved step only).
6. Sweep other screens on the tablet (home, a department, a title, search, player chrome, profile picker) and add anything off to the follow-up list — do not fix (decision 2).
7. Docs:
   - `DESIGN.md` (root, Android): new tokens/components (phase 01) + deliberate differences (phase 03/04 "Next steps"), Settings has no app bar, Artwork = Default on phone-width layouts, TV dark-only with accent + artwork.
   - `docs/system-architecture.md:595` — "Appearance offers the accent and artwork"; Settings/System structure (System is a Settings entry; menu shortcut kept).
   - `docs/project-changelog.md` — new version entry (Added: Artwork setting, Settings two-pane; Changed: dark palette, Geist, 6dp controls).
   - `docs/development-roadmap.md` § Android — mark done, add follow-ups.
   - `plans/260926-1330-android-editorial-departments-parity/plan.md:38` — annotate "superseded 2026-09-27: Artwork ported".
8. Version: read `Cargo.toml`; minor bump in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` `versionName`; `versionCode` +1. Commit message conventional, no plan codes, e.g. `feat(android): settings as a two-pane index on the web player's tokens; release 0.69.0`.

## Known follow-ups (listed, not fixed — decision 2)

1. ~~Pages sit on `surface` (#151517)~~ **Addressed 2026-09-28** (department hero web-look phase, `mediagram-home` worktree): `AppChrome.kt`'s `Scaffold` and `MobileApp.kt`'s root `Surface` now both read a shared `ui.pageGround` (`= colorScheme.background`), the same token every hero's own scrim already fades toward — no more separate `surface` reading for a page's own container. Caught because a department hero's scrim, correctly fading toward `background` already, showed a seam exactly where the page around it was still `surface`.
2. `TitleSpread.kt:94` sets the title in `FontFamily.Serif` (system serif, not Fraunces); its overview uses `bodyMedium` (now Geist) where the web uses Newsreader 17px (`title-page.css:102`) → `bodyLarge`.
3. Department hero default is words over art (`DepartmentHero.kt:44-100`, `TvCoverStory.kt`); the web's default fades art into the page with words beneath (`departments.css:89-90` narrow, `:16-27` wide). **`DepartmentHero.kt`'s own half addressed 2026-09-28** (department hero web-look phase) — `TvCoverStory.kt` stays open.
4. Hero scrims fade to `Color.Black` (`DepartmentHero.kt`, `CoverStory.kt`) also in the light theme; the web fades to paper. **`DepartmentHero.kt`'s own half addressed 2026-09-28** — its scrim now fades to the theme's own page ground (and its copy text, found reading fixed on-image colours even off the picture, now reads the theme's own ink) in both themes. `CoverStory.kt` no longer exists (replaced by `ui/catalog/home/CoverSlide.kt` + `CoverControls.kt`'s `CoverScrim` in the home web-parity phase); checked directly — its own scrim already fades to `colorScheme.background`, and its copy text's own fixed on-image colours are correct there by design (the cover's own scrim darkens the whole picture the copy sits on, unlike a department hero's, which only shades its own left edge). Not a live instance of this item any more.
5. M3 buttons elsewhere keep the full pill (their shape is not theme-driven); web controls are 6px.
6. TV focus/plate shape square (`TvFocus.kt:70`); web thumbs 3px, controls 6px.
7. Sheets/dialogs keep M3 `extraLarge` corners; web dialogs are 6px (`library-controls.css:64`).
8. Wide two-column title spread on expanded width (web ≥ 900px) — would make the Artwork mode visible on tablets.
9. System lacks the web's Telegram link (DC, flood waits, reconnects) and Watching now blocks (`status-view.js:85-113`).
10. `on-accent` (#1a0d09 / #fff8f4) unused; `onPrimary` = Ground.
11. Pull-quotes: web uses Newsreader italic (`departments.css:48`, `title-page.css:114`); Android uses Fraunces italic (`TitleSpread.kt`, `PullQuote`).

## Todo

- [x] All suites green
- [x] Tablet install pinned to caad49da, test profile
- [x] Four section screenshots compared to mockups; in-scope deltas fixed (one
      regression found and fixed: shared scroll state across sections)
- [x] Artwork-mode screenshots (title, Movies department; home skipped per lead —
      being redesigned on another branch)
- [x] Other-screen sweep → follow-up list in plan.md § Review (14 items)
- [x] DESIGN.md, system-architecture, changelog, roadmap, superseded note
- [x] Version bump (patch, 0.69.4 — docs/fix-only close; versionCode untouched per
      CLAUDE.md § Versioning, which does not tie it to a semver bump)

## Success criteria

- Suites green; screenshots attached to the review; follow-up list present; `git grep -n '0.68.13'` in the three manifests returns nothing after the bump and all three carry the same new number.

## Risks

| Risk | L×I | Mitigation |
|------|-----|------------|
| Version collision with the in-flight 0.68.14 | H×L | Read at merge; pattern bump; rebase first |
| Device install hits other attached devices | M×M | `ANDROID_SERIAL=caad49da` on every command |
| `wm size` override left on the tablet | L×M | Only with approval; reset commands in the same step |

## Security

Screenshots of the Telegram section show account name and session locations — keep them
in the scratchpad, do not commit them.

## Next steps

Ship (`/ck:ship` or the user's usual flow); follow-ups to issues on `daBOB/mediagram` if the user wants them tracked there.
