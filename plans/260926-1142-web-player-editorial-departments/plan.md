# Web player — editorial departments

Status: phases 3–4 built 2026-09-26 on branch `feat/editorial-departments` (worktree `../mediagram-editorial`), uncommitted; waiting on the checkpoint. Builds on the shipped magazine shell
(`260925-2014-web-player-magazine-redesign`, v0.55.0+). It restructures pages. It
does not change the aesthetic.

Reference: [visuals/reference-mockup.png](visuals/reference-mockup.png). The user's
brief: every department reads like its own magazine section, and every detail page
reads like a feature article. **Movies and Series must not be two poster grids with
different labels.**

## Locked decisions (user, 2026-09-26; do not reverse silently)

- Screens: Home, Movies, Movie detail, Series, Series detail, Collections (+ detail),
  Search, and Settings. **Music is excluded.**
- Top level: **Home · Movies · Series · Documentaries · Tutorials · Collections**, plus Search
  (Documentaries added 2026-09-26, phase 9 — not in 0.62.0; follows it).
  Tutorials stays a department in the same treatment. Utility rail: **My List (=
  Watchlist), Continue Watching, Latest, Genres, Settings**.
- **Add a TMDB credits pipeline.** Cast tabs, People search and circular portraits
  come from real credits.
- **Collections = TMDB franchises (`belongs_to_collection`) + the user's own lists.**
  Large feature cards and a collection detail page with an introduction.
- **Delivery: bounded preview first.** Home, Movie detail and Series detail go to the
  user as desktop and phone screenshots (dark + light) **before** phases 5–7.
- Carried over from 2026-09-25: dark-first with a light variant; editorial copy from
  **real data only**. The mockup's review quote becomes the TMDB **tagline**. No
  invented lines.

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | [Credits, franchises, series type (Rust, schema v9)](phase-01-credits-franchises-pipeline.md) | done (report: plans/reports/fullstack-developer-260926-1215-…) |
| 2 | [Web data: v9 fields, portraits, similar, people search](phase-02-web-data-layer.md) | done |
| 3 | [Shell, nav, and Home](phase-03-shell-nav-and-home.md) | built |
| 4 | [Movie and series feature pages](phase-04-feature-detail-pages.md) | built |
| — | **Checkpoint: screenshots of 3 + 4 to the user** | approved 2026-09-26 ("looks good"); phase 1 next so Similar and Collections get franchises |
| 5 | [Departments: Movies, Series, Tutorials](phase-05-department-pages.md) | built (Completed/Limited rows wait for v9 status/type on sets) |
| 6 | [Collections and Search](phase-06-collections-and-search.md) | done |
| 7 | [Settings: Appearance, Playback, Profile](phase-07-settings-page.md) | built (Appearance + Profile; no Playback tab: playback prefs are per-show, none global) |
| 9 | [Documentaries department and custom artwork](phase-09-documentaries-and-custom-artwork.md) | approved; follow-up after 1–8 ship (0.62.0), in its own worktree — v9 artwork table only if v9 still unpublished, else v10 |
| 8 | [Verify and ship](phase-08-verify-and-ship.md) | pending |

Phase 1 runs in parallel with 3 and 4. The web reads every v9 field as optional,
so the preview can be built before a v9 index exists. Cast and franchises then show
empty until a v9 index does.

## Key facts (verified 2026-09-26)

- TMDB details are fetched with `append_to_response=external_ids`
  (`crates/mediagram-tmdb/src/details.rs:23`). Credits can be added in the same call.
- 910 cached TMDB movie responses already carry `belongs_to_collection`
  (`~/.local/share/mediagram/tmdb-cache`). No cached response carries credits, so a
  re-fetch of details is needed. The disk cache never expires (`disk_cache.rs:44`),
  so the backfill must bypass the cache for credits.
- Schema is v8 (`crates/mlib-spec/src/schema.rs:4`). Readers accept 6–8. Web reads
  optional columns by probing (`web/src/catalog/shows.ts:93`).
- Library: 581 films, 182 episodes, 162 tutorials. `sets.duration` gives runtime.
  `shows.certification` gives the age rating.
- Images are keyed files (`tmdb-{movie|tv}-{id}.jpg`, backdrops `…-bg`). Portraits
  follow as `tmdb-person-{id}.jpg`.

## Out of scope / owed

- **Android parity** (§ Surface Parity): department pages, feature pages, Cast, and
  franchise collections are owed to Android. A follow-up plan is created in phase 8.
  Android readers must tolerate v9 (additive columns and table) before any v9 push.
- The Telegram, cache and library tabs of Settings belong to
  `260922-2105-settings-menu-telegram-and-cache` phase 06. This plan builds the
  Settings shell that those tabs slot into.
- Trailers, reviews, chapters and "viewing orders": no data source. Not built.

## Checkpoint (2026-09-26)

Screenshots: `visuals/cp-{home,film,series}-{desktop,phone}-{dark,light}.png`, from the
preview (real catalog copy, no Telegram). No horizontal overflow at 375/768/1024/1440 on
home, film, series, genres and latest. Web: lint clean, 1936 tests pass.

Decisions made while building (not yet confirmed by the user):
- The rail stays full height on home too. The home-only "rail beside the cover" layout was removed.
- On phones the rail row keeps only the utilities; the departments are the bar below it.
  **No bottom tab bar** (phase 3 said one): the existing two-row header already fits.
- Series seasons: a `<select>` replaces the season-poster wall. Season URLs are unchanged.
- The film button keeps "Resume from 2:20". The series button reads Resume / Continue / Play SxEy.
- ⋯ holds "Make editor's choice" (hidden on kids profiles, as before).
- Removed `series-header.js`, `title-band.js`, `season-wall.js`, `seasonGrid`.

## Built after the checkpoint (2026-09-26)

- Phase 5: `#/movies` is the department page; the paged shelf moved to `#/movies/page/N`
  (page 1 included). Series/Tutorials pages list every show/course at the foot (6 shows,
  1 course); Popular/New rows appear only above 12 shows.
- Phase 6 (search half): results grouped (Movies posters, Series cards, Episodes, Lessons),
  filter pills in memory (not in the hash — deviation from phase text).
- Phase 7: theme now keyed on `data-theme` set by `lib/appearance-boot.js` (blocking
  classic script in <head>); appearance stored per browser, not per profile (deviation:
  simpler, and a theme belongs to a screen). Accents contrast-tested.
- **Conflict to resolve:** another session's `feat/settings-menu` (worktree
  `../mediagram-settings`) adds its own `#/settings` route, rail link and `settings-view.js`
  (admin-gated Telegram/cache). Plan: one Settings page — this shell hosts theirs as an
  admin-only tab. Whoever merges second resolves; both branches edit app.js/index.html.

## v9 end to end (2026-09-26)

Scratch copy of `library.db` + tmdb-cache + posters (real index mtime unchanged), new
binary `metadata` then `posters`: 908/908 titles credited, 11,706 credit rows, 202
franchises, 6,886 portraits. Preview over the copy: Collections shows 70 franchises (≥2
held films); Star Trek splits as TMDB does — Raumschiff Enterprise (6), Das nächste
Jahrhundert (3), Kelvin (1, no card). Cast tab, person pages, People in search work.
Screenshots: `visuals/v9-*.png`.

## Review fixes (code-reviewer, 2026-09-26)

Report: `plans/reports/code-reviewer-260926-1300-editorial-departments-branch-review-report.md`.
- High, fixed: people in search/person pages leaked across kids profiles → search sends each
  person's title keys; `visiblePeople` keeps only people with a visible title; a person page
  with none visible says "nobody" (tested).
- High, fixed: "Part of" linked franchises with <2 held films → link only when
  `franchisesIn` has it.
- High, fixed: title tabs reset on every watch-state redraw → `tabs.js` remembers the chosen
  tab and focus per title; a late Cast tab respects a newer choice (tested, mutation-checked).
- Medium, fixed: person/franchise pages cache their fetch; season picker keeps focus
  (`focusWhenAttached`); credits written atomically in a savepoint (Rust test).
- Low, fixed: people match either umlaut spelling; null TMDB franchise/creator names no
  longer fail a details parse; Home/End in tabs; versions 0.62.0; Android parity plan
  `plans/260926-1330-android-editorial-departments-parity/`.
- Low, left: a title whose credits come back empty is asked again each run.

## Merge with main (0.61.0, feat/settings-menu)

`main` merged into the branch (fast-forward + reapply). Their Settings page is now the
admin-only "Library & Telegram" tab of this Settings page (shown when `/api/settings`
answers 200/401, as their rail link was); their separate rail link removed. Web 2140 +
Rust 1268 tests pass, lint/clippy/tsc clean.
