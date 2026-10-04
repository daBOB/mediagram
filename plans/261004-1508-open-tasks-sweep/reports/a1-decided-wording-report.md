# A1 — decided wording + stale department pill (web, phone/tablet, TV)

Branch `worktree-agent-a3e0e6b4446e2f604` (based on `a999faea`). No version bump, not pushed.

## Commits

| Hash | What |
|------|------|
| `d1187e9a` | feat(web): rail reads Continue, player says My List, stats count 0 min since a date (+ contract §6) |
| `114931e5` | fix(web): a nav link that no longer names the page lets go of focus |
| `5b569c51` | feat(android): same three wordings on phone/tablet and TV |
| `577e8f93` | docs: the rail's resume row is Continue (DESIGN.md, web/DESIGN.md, system-architecture, web-player route table) |
| (this file) | docs(plan): this report |

## 1. Rail label "Continue watching" → "Continue"

- Web: `web/public/index.html` rail link label. Accessible name = link text ("Continue" + count), so it follows. The `#/continue` page heading already said "Continue".
- Android: `RailItem.CONTINUE_WATCHING` label (`ui-common/.../RailItem.kt`) — one source for the tablet rail, the compact header icon's content description and the TV rail. `UtilityDestination.CONTINUE_WATCHING` (`feature/catalog/CatalogTabs.kt`). Also the narrow overflow menu item (`ui-mobile/.../OverflowMenu.kt`), because on compact/medium pushed frames that menu *is* the rail's stand-in.
- Not changed (deliberately, matching the web): the home band "Continue Watching" and the Anime/Documentaries department rows "Continue watching" — those are page rows, not the rail row; the web keeps them too (`home-view.js:81`, `department-pages.js:110`, `anime-department.js:56`).
- Rail widths untouched (fixed constants: web `--rail-width`, tablet 184/224 dp, TV 96/288 dp). Counts unchanged. Identifiers (`CONTINUE_WATCHING`, `onContinueWatching`) unchanged. Comments that listed the rail rows were updated so they don't name a label that no longer exists.

## 2. Player list button → "My List" / "On My List"

- Web: `player-library-marks.js` and the button's initial text in `index.html` (it read "Watchlist" before the first title opened).
- Android: new `feature/player/.../ListLabel.kt` `listLabel(marks)` — one copy used by both `ui-mobile/.../PlayerMarks.kt` and `ui-tv/.../TvMarksRail.kt` (they had the string twice). Identifiers (`onToggleWatchlist`, `watchlisted`) unchanged.

## 3. Stats: "0 min" and "Counting since <date>"

- `watchTime` / `durationText`: below one minute → "0 min" (history lines, bar labels and totals alike).
- New `countingSince(summary, now)` (web `stats-format.js`) / `countingSinceText(summary, now)` (Android `StatsFormat.kt`), rendered directly under the totals: web `<p class="stats-since">`, phone its own lazy item, TV inside the totals' focus stop (so the remote/TalkBack reads it with the figures). Empty state untouched (the line only exists in `Ready`).
- **Decision — which date.** The brief said "earliest day this profile has any stats activity (the oldest history entry / day row)". I date it by the **earliest `started` history entry**, not the oldest entry of any kind: `finished` entries include finishes from before stats existed (contract §4: "finishes from before stats existed are real history"), so the oldest entry overall could say "Counting since 21 Sep 2025" when counting began 3 Oct. A `started` entry comes from a title stats row, written by the same position write that counts minutes, so it is the first counted activity (and it is ≤ the earliest day row). Consequence: a profile whose only history is pre-stats finishes shows no line (nothing has been counted, so there is no honest "since"). Both are pinned by tests and written into contract §6. Flip to "oldest entry of any kind" is a one-line change per surface if the user prefers.
- Date form: the Stats page's own date form — "3 Oct", with the year only when it is another year ("30 Dec 2025"), i.e. `whenLabel`'s date branch, factored out as `dateLabel` / `dateText` so both share it. Never "today …" or a weekday.
- Condition: `allSeconds < 60` (i.e. the All time total would read "0 min").
- Contract `plans/260928-0306-viewing-stats-web-and-android/shared-contract.md` §6: new "Counting since" bullet, Durations bullet now says "0 min" (both marked amended 2026-10-04, user decision).

## 4. Web: department pill lit on rail pages — root cause

The `active` class was never wrong: reproduced in the stub preview (`bun run preview`, copies, no Telegram) + headless browser, `#/collections` → rail My List leaves only `watchlist` with `.active`. What does stay lit is **focus**: a pill clicked to get somewhere keeps focus when the viewer leaves by a way that doesn't move it — Back/Forward (mouse button, Alt+Left), or a non-focusable card — and the next key press makes `:focus-visible` draw the accent ring around it. Reproduced exactly: click Collections, Back to My List, press a key → Collections pill ringed (and hover-tinted while the pointer rests there) on My List.

Fix: the nav-marking loop moved out of `app.js` (which sits at its line ceiling) into `web/public/lib/nav-current.js` `markCurrent(links, section)`; any nav link that does not name the page and holds focus is blurred; the current one keeps focus (keyboard users pressing Enter on a pill are unaffected). Applies to every route, rail and department alike — a ring on Movies while on Series is the same defect. Verified in the browser after the fix: same sequence leaves focus on `<body>`, no ring. `code-standards.test.ts` ceiling for `app.js` lowered 699 → 695.

Not a bug, left alone: hover tint on a pill while the mouse pointer still rests on it after a Back.

## Tests

- Web: `bun run lint` clean; `bun run typecheck` clean; `bun test` **2644 pass, 0 fail** (196 files). New/updated: `stats-format.test.ts` (0 min ×3, `countingSince`: first start, pre-stats finish ignored / none → null, other year, ≥ 1 min → null), `stats-page.test.ts` (line shown directly after totals with all-time 30 s; hidden at exactly 60 s; empty state test unchanged and still exact), `player-features.test.ts` ("On My List" / "My List"), new `nav-current.test.ts` over the shipped `index.html` (all seven rail routes after Collections: no pill lit, own row lit + `aria-current`; department sub-pages keep their pill; focused stale pill is blurred, the current one is not; rail label reads "Continue").
- Android: `./gradlew -q :feature:catalog:testDebugUnitTest :feature:stats:testDebugUnitTest :feature:player:testDebugUnitTest :ui-common:testDebugUnitTest :ui-mobile:testDebugUnitTest :ui-tv:testDebugUnitTest` green — catalog 330, stats 44, player 252, ui-common 73, ui-mobile 237, ui-tv 436 (**1372**, 0 failures/errors/skipped). New: `PlayerListLabelTest`, `TvPlayerListedMarksTest` ("On My List"), `StatsFormatTest` ×3 counting-since cases, `StatsUiStateTest.whileNothingIsCountedALineUnderTheTotalsSaysSinceWhen` (+ null assertion when ≥ 1 min), `StatsScreenTest` (line between totals and chart; absent when null and on Empty), `TvStatsPageTest` (line merged into the focused totals stop; absent otherwise/Empty). Old-wording assertions updated in `CatalogTabsTest`, `LibraryRailTest`, `OverflowUtilitiesTest`, `TvKeptWallStateTest`, `TvHousekeepingTest`, `TvPlayerMarksTest`, `StatsFormatTest`, `StatsUiStateTest`.
- Android lint (`:feature:catalog :feature:stats :feature:player :ui-common :ui-mobile :ui-tv :core:designsystem` `:lint`): exit 0.
- Spotless/detekt convention plugins exist in `build-logic` but no module applies them, so there is no ktlint gate to run.

## Left / for the lead

- No device check (no adb, by instruction): the Counting-since line on tablet/TV and "Continue" in the TV rail are verified by Robolectric only.
- Version bump and `docs/project-changelog.md` left to the lead.
- `android/core/rust/src/main/jniLibs` copied into the worktree (gitignored) for the unit tests.
