---
title: "Android home on the web player's layout: rail, departments bar, cover, features, bands"
description: "Rebuild the phone/tablet library chrome and home screen to match the web player's home; add the Documentaries department."
status: completed
priority: P2
effort: 20h
branch: feat/android-home-web-parity
tags: [android, home, chrome, parity, documentaries]
created: 2026-09-27
---

# Android — home screen to the web player's layout

Base: branch `feat/android-settings-redesign` after its phase 04 commit (the web tokens,
Geist, `PageHead`, `LocalBackdrop`, `SettingsIndex` rail precedent all live there).
`web/public` is the reference (CLAUDE.md § Surface Parity). Reference shots (web vs
tablet, 1164×777 dp; web at 412 dp): scratchpad `home-web-{1,2,3}.png`,
`home-android-{1,2,3}.png`, `home-web-phone-{1..4}.png` — copied to
`reports/reference/` in phase 01.

## Decisions (user, 2026-09-27 — do not reverse silently)

1. **Chrome = the web's rail + departments pill bar.** Tablet (EXPANDED): left rail
   (wordmark, My List + count, Continue watching + count, Latest, Genres, Settings,
   System, tally) and a Geist pill bar with counts (Home, Movies, Series,
   Documentaries, Tutorials, Collections, search, avatar) laid over the cover.
   Phone: the web's ≤900px shape (wordmark + icon row, scrolling department pills,
   search field + avatar). Replaces the Material top app bar and serif tab row.
2. **Scope: phone + tablet now, TV after** (TV home is its own later plan).
3. **Documentaries tab included** — it needs the department that Android lacks.

## Deliberate differences (write them where the code is)

- Android-only actions (Update library, TMDB key…, Start over) keep a small ⋮ menu
  beside the avatar: the web has no counterpart, the web server does these itself.
- No backdrop blur behind the bar (Compose cannot blur what is behind a node without a
  new dependency): the bar's scrolled state is more opaque instead.
- Pushed pages (title, genre, latest…) keep their back bar inside the content column;
  the rail stays beside them on tablet. Android has a back stack; the web has URLs.
- Phone header hides on scroll down and returns on scroll up (the web's is static and
  scrolls away; a fixed three-row header would eat a phone screen).

## Phases

| # | Phase | Owns | Status |
|---|-------|------|--------|
| 01 | [Library chrome: rail + departments bar](phase-01-library-chrome-rail-departments-bar.md) | ui-mobile `ui/` shell, catalog tabs, new `ui/chrome/*`, rail icons | completed (0.71.0, `a0ab730f`) |
| 02 | [Home body: cover, features, bands, shelves](phase-02-home-cover-features-bands-shelves.md) | ui-mobile `catalog/Home*`, `CoverStory`, `FeatureStrip`, `ResumeStrip`, `PullQuote`, new `catalog/home/*` | completed |
| 03 | [Documentaries department](phase-03-documentaries-department.md) | core/model `Kind`, core/data kind mapping, feature/catalog shelves/tabs, ui-mobile department page | completed |
| 03b | [Department hero in the web's look](phase-03b-department-hero-web-look.md) | ui-mobile `DepartmentHero`, department tabs' top chrome | completed |
| 04 | [Verify on tablet, docs, version](phase-04-verify-docs-version.md) | tests sweep, docs/, DESIGN.md, manifests | completed (0.99.9) |

## Dependencies

01 → 02 → 03 → 04, one agent at a time in the worktree
`/home/andre/Workspace/mediagram-home` (branch `feat/android-home-web-parity`, off the
settings branch at 0.69.2; rebased onto it once its 0.69.3 lands). 02 needs 01's seam (the bar's
height handed to the home so the cover draws under it). 03 is data-first and touches
TV through `shelvesOf`/`catalogTabsOf` — TV must keep building and passing its tests.

## Versioning

One commit per phase, bumped by regex in `Cargo.toml`, `web/package.json`,
`android/app/build.gradle.kts` `versionName` (memory: bump versions by pattern).
Phase 01 is a feature → minor (`0.70.0`); later phases patch unless a phase adds a
feature (03 does → `0.71.0`).

## Rollback

`git revert` per phase. No persisted-data change; Documentaries only re-files sets
the index already calls `docu`.

## Result (2026-10-04)

Verified on the tablet against the live web player, landscape and portrait:
[reports/home-web-parity-tablet-report.md](reports/home-web-parity-tablet-report.md).
The home's section order, the chrome and all six departments match. Two defects
the walk found were fixed in 0.99.9 (Back on a reopened profile chooser closed the
app; My List was titled "Watchlist"). Remaining differences are listed there as
deliberate or owned by `260926-1330` (Genres, Latest, Franchises) and `260928-0047`
(profile chooser).

