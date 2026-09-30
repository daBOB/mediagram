---
title: "TV on the web player's look: rail, departments bar, magazine home, department pages"
description: "Port the tablet's web-parity chrome, Home and department pages to android/ui-tv, remote-first, and stop Home rebuilding on every return."
status: pending
priority: P2
effort: 32h
branch: feat/tv-web-look
tags: [android, tv, chrome, home, departments, parity, focus, performance]
created: 2026-09-29
---

# TV — chrome, home and departments in the web player's look

Reference = web player (CLAUDE.md § Surface Parity) through its proven Android port, the tablet
(plan `260927-2050-android-home-web-parity`; code `android/ui-mobile/.../ui/chrome/`,
`ui/catalog/home/`, `ui/catalog/Department*`). TV viewport is 960×540dp (every TV Robolectric test
runs `w960dp-h540dp`) — inside the web's own 900–1180px band, i.e. the tablet's EXPANDED layout.
ui-tv has no material3 (`TvTheme.kt:26-32`), so TV composables are its own; **models, pure
helpers, scrims and tokens are shared, never re-derived**.

## User decisions (2026-09-29, locked)

1. TV follows the tablet look, remote-friendly: rail (wordmark, My List n, Continue watching n,
   Latest, Genres, Settings, System, tally), pills with counts (Home, Movies, Series, Anime,
   Documentaries, Tutorials, Collections), magazine home, department heroes + rows (Anime
   Series/Films, category rows); D-pad order, visible focus rings, overscan-safe.
2. Scope: chrome + Home + department pages. Title/person/genre pages and player unchanged.
3. Remove TV's written-down differences the new look makes unnecessary (Anime/Documentaries walls).

## Planner decisions (details + reasons in phase files; user may overrule)

- **Rail collapsed-until-focused**: icons only (96dp) while the remote is elsewhere, the tablet's
  full rail (wordmark, counts, tally; 288dp) drawn *over* the content when focused (phase 01).
- **Focus**: launch → cover Watch now; pill press selects and keeps the remote on the pill;
  Down → page's own first/last stop; Left at a left edge → rail; Right from rail → where it came
  from. **Back**: content → selected pill (or active rail row) → rail → app exits.
- **Cover**: focus anywhere on it holds rotation; Watch now · + My List · Details · dots; a
  focused dot shows its film; no pause button (phase 02).
- **Overscan**: rail bg to the edge, its content ≥48dp in; bar top 27dp; right gutter 48dp;
  full-bleed art may crop, text/focus stops never (phase 01).
- **Pushed frames** (title, player, search, Latest, Genres, Settings…): full screen, no rail/bar;
  Back lands on the stop that opened them.
- **Home survives a round trip**: restore keys land the remote back on the stop that opened a
  pushed frame, as before phase 04. Keeping Home itself composed under the pushed frame to avoid
  a rebuild was tried in phase 04 and withdrawn (2026-09-30) after five box rounds chasing the
  same symptom through five different Compose focus-reset mechanisms — see phase 04's own file.

## Phases

| # | Phase | Effort | Status |
|---|-------|--------|--------|
| 01 | [Chrome: rail, departments bar, focus model](phase-01-tv-chrome-rail-departments-bar-focus.md) | 8h | completed |
| 02 | [Home body: cover, features, bands, shelves](phase-02-tv-home-cover-features-bands.md) | 10h | completed |
| 03 | [Department pages: heroes, rows, Anime, Documentaries](phase-03-tv-department-pages-heroes-rows.md) | 8h | completed |
| 04 | [Home kept alive, measured; device walk; docs](phase-04-tv-home-kept-alive-measure-docs.md) | 6h | withdrawn (kept `9affd36e`'s stable focus requesters) |

## Dependencies and ownership

01 → 02 → 03 → 04, one agent at a time in worktree `/home/andre/Workspace/mediagram-tv`
(`feat/tv-web-look` off `main` 0.81.1). Each phase is shippable to the box on its own. Pending
plan `260928-0047-profile-roles-pins-kids-age-limits` also edits `TvCatalogBody.kt`,
`TvCatalogScreenStateTest.kt`, `TvAppFixture.kt` — whichever lands second rebases.
Shared-module moves (refactors; phone/tablet behaviour unchanged, their tests move and stay
green): `ChromeCounts` → feature/catalog, `RailItem` → ui-common (01); `CoverBlend` + scrims →
ui-common (02); `heroArtOf`, department lines, `resumeCardsOf` → feature/catalog (03).

## Gate, versioning, rollback

Every phase: `scripts/check.sh` green; on-box screenshots vs tablet in `reports/`; version bumped
by regex in `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` versionName (01–03 next
minor, 04 patch; renumber if another release landed); changelog entry; one commit. Rollback:
`git revert` per phase — no persisted data, schema or saved restore-key string changes.

## Resolved with the user (2026-09-29)

- Rail: icons-only (96dp) until the remote reaches it, then opens over the content in full.
- Pill press: the remote stays on the pill; Down enters the page.
- Anime films on OK: open the title page on both Android surfaces (the web's direct play
  stays a small written-down difference).
- DESIGN.md's "no horizontally-scrolling rail" line: fixed in phase 04 here.
- Build now, phase by phase; the box baseline (phase 01 step 0) is measured by the lead on
  the box, not by the implementing agent.
