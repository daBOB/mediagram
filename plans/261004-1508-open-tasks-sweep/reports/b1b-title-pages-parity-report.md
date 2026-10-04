# B1 group B — phone/tablet title pages, parity with the web

2026-10-04, worktree subagent, branched from `main` at `dd238475`. Gap report items 6, 7, 8
(`b1-editorial-parity-gap-report.md`). No version bump (lead bumps on merge).

## UniFFI binding changed — rebuild the native core on main

`TitleInfo` gained four fields: `first_air`, `last_air`, `total_seasons`, `total_episodes`
(`crates/mediagram-core/src/dto.rs`), each `#[uniffi(default = None)]` so every existing
Kotlin `TitleInfo(...)` (tests, ui-tv) compiles unchanged. Bindings regenerated with
`scripts/generate-android-bindings.sh` (NDK 28.2.13676358), which also rebuilt all four
ABIs' `libmediagram_core.so` in this worktree. **The `.so` is gitignored: after merging,
the lead must run `ANDROID_NDK_HOME=… scripts/generate-android-bindings.sh` (or
`build-android-core.sh`) on main before installing, or the app dies at launch with
UnsatisfiedLinkError while unit tests still pass.** The regenerated Kotlin must come out
identical to the committed file.

Same source as the web: the web's `showMeta` (`web/src/catalog/shows.ts`) reads
`first_air`/`last_air`/`total_seasons`/`total_episodes` from the index's `shows` row;
the core's `title_info` reads that same row (index first, the device's fetched sidecar as
fallback), so both surfaces print the same dates and totals.

## What changed

| Gap | Android now | Web reference |
|---|---|---|
| 6 film facts | `2021 · 2h 30m · FSK 12 · Sci-Fi, Adventure, Drama` — `factsLine` takes `genres`, first three | `film-page.js:36` |
| 6 film Details | "Audio languages" row from `MediaSet.alang`, no row when empty | `film-page.js#details` |
| 6 series About | "Audio" row before "Subtitles" (`detailRows`), from every episode's `alang` | `series-summary.js:154-158` |
| 6 crew links | each crew name in "Directed by / Created by" is a `LinkAnnotation` opening the person page via the Cast row's own `onOpenPerson` | `cast.js:58-65` |
| 6 spelled seasons | facts line "two seasons" (`seriesFactsLine`, moved to `feature/catalog/SeriesSummary.kt` so TV can share it); "Held" spells too ("one season") | `series-page.js#factsLine`, `countOf` |
| 7 wide spread | EXPANDED width: `WideTitleSpread.kt` — art from 28% across (full width in Artwork mode), web gradients, words + pills bottom-left (copy ≤ 34rem), tagline quote bottom-right at 22% height; min height `clamp(560, 76vh, 780)`; series spread bleeds over its list's margin to the window edge | `title-page.css:52-116` |
| 8 totals / air dates | About "Aired" `2011–2019` from provider dates (episode years as fallback, no dash for a running show); "Held" `8 of 16 episodes · 1 of 2 seasons` when the library holds less; facts line uses the same years | `series-summary.js` `yearLine`/`scaleLine` |

Spelled counts: reused the existing `catalog.spelledCountOf` (`LibraryTally.kt`); no new helper.

Also fixed on the same page (same `countOf` rule): the season picker reads
"Season 1 · one episode" (was "1 episode"), as `series-page.js#episodes` does.

Pills moved into `TitleSpread` as an `actions` slot (the web's `.spread-actions` sits
inside the spread at every width). Phone look is the stacked layout as before; the one
visible phone shift is the series page's pills now aligned with the title (they sat one
margin further left than the words).

Deliberate differences kept and re-worded: `TitleSpread.kt` doc (phone/portrait stacked,
quote wide-only; wide now follows the web); `CollectionScreen.kt` series art inset on the
phone only. `DESIGN.md` › Artwork no longer says Android lacks the wide layout.

## Tests

- `cargo test -p mediagram-core`: 627 passed (new `a_series_reports_when_it_aired_and_how_much_of_it_exists`).
- `cargo clippy --all-targets --all-features -D warnings`: clean.
- Gradle unit tests, all green: `:feature:catalog` 338, `:ui-mobile` 249, `:ui-tv` 436,
  `:core:data` 139; `lint` passes; `:ui-tv:compileDebugAndroidTestKotlin` compiles
  (its `TitleInfo(...)` needed no change). Lint's only notes on touched files are
  `ConfigurationScreenWidthHeight` warnings, the `screenWidthDp` reading `DepartmentHero`
  already uses.
- Commits: `dfd744ae` (core + bindings), `2d6cd9ca` (Android).
- New: `SeriesSummaryTest` (totals, all-held, air dates incl. running/same-year/bad date,
  spelled facts line, Audio+Subtitles rows), `TitleFactsTest` (three genres),
  `FilmPageTest` (genres line, Audio languages present/absent, crew link opens person 3),
  `SeriesPageTest` (two seasons, provider dates + "2 of 73 episodes · 2 of 8 seasons",
  Audio row, creator link), `WideTitleSpreadTest` (w1280dp: art at 28%, pills bottom-left,
  quote bottom-right; series spread meets the window edges; w800dp portrait stays stacked).

## Not done / for the lead

- Device walk not done (no adb by instruction): the wide spread wants a look on the
  Redmi Pad in landscape — overview length, quote vs. copy overlap on narrow EXPANDED
  widths (the web has the same band at 900–1180px).
- Series About "Genres" still reads the first episode's genres; the web's About reads the
  provider's `genres` string. Same data in practice; not in this group's list.
- `FactSheet` label column is 96dp; "Audio languages" likely wraps to two lines (the
  web's ≤900px column is 7.5rem). Left as is; check on the device walk.
