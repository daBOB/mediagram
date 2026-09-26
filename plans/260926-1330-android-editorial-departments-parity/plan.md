# Android — editorial departments parity

Status: planned 2026-09-26; **build waits for `feat/android-tv-ui` to merge** (user
decision). Owed under CLAUDE.md § Surface Parity. The web player (0.62.x) is the
reference: before each screen, read its web module (named per phase) and match its
decisions. Any deliberate difference is written into the phase that makes it.

Scout: A = `android/`, C = `crates/mediagram-core/src/` (report in session, 2026-09-26).

## Phases

| # | Phase | Surface | Status |
|---|-------|---------|--------|
| 1 | [Core read API: franchise/type on rows, credits, person, franchises, people search, device portraits](phase-01-core-read-api.md) | Rust core + UniFFI | pending |
| 2 | [Kotlin data + pure rules: Similar, SeriesResume, GenreIndex, Franchises, VisiblePeople](phase-02-kotlin-data-and-rules.md) | feature/catalog, core/data | pending |
| 3 | [Navigation: departments in the masthead, new frames](phase-03-navigation.md) | ui-common, feature/catalog | pending |
| 4 | [Phone title pages: film spread + tabs, series page](phase-04-phone-title-pages.md) | ui-mobile | pending |
| 5 | [Phone departments, collections/franchises, person, search, Latest, Genres](phase-05-phone-departments-and-browse.md) | ui-mobile | pending |
| 6 | [TV: the same screens on the television surface](phase-06-tv-surface.md) | ui-tv | pending |
| 8 | [Settings › Appearance: theme + accent](phase-08-appearance.md) | ui-mobile, ui-tv, designsystem | pending |
| 7 | [Verify on devices, docs, version](phase-07-verify-and-ship.md) | all | pending |

Phases 1–2 are surface-free and can start as soon as the TV branch lands. 4–5 and 6 share
the view models from 2–3 and can run in parallel after that.

## Prerequisite (unchanged)

Installed Android builds read schemas 6–8 and refuse a v9 package; any build from main
(0.62+) reads 6–9. Install on phone caad49da, TV emulator and TV box 192.168.0.35:5555
(`ANDROID_SERIAL` pinned — never a bare installDebug) before the first v9 push/export.

## Decisions (user, 2026-09-26 — do not reverse silently)

- **Wait for the TV merge** before building; plan now.
- **TV home gets the magazine layout** (cover story, features), sized for 10-foot/D-pad —
  parity, not a TV exception. (Phase 6.)
- **Settings › Appearance: port theme (Dark/Light/Auto) + the seven accents**, stored per
  device as on the web. Artwork mode is **not** ported — a deliberate difference, recorded
  in phase 8. (Phase 8.)
- **Portraits fetched on device, lazily**: when a Cast row or person page first shows a
  person, fetch `tmdb-person-<id>` (w185) from the index's `credits.profile` and cache it;
  no bulk download. (Phase 1.)
