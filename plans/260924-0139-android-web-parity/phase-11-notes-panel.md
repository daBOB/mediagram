# Phase 11: Notes panel (markdown summary, auto-open for lessons)

## Context links
- `web/public/lib/player.js:210-232` (only when `hasSummary`; fetch summary; stale-title guard; **auto-open only for `kind === "tut"`**, films wait for the Notes button)
- `web/public/lib/markdown.js:1-40,43,160,224` (pure parser → tree; subset: headings, nested lists, blockquotes, fenced code, rules, inline strong/em/code/links; unknown survives as text; `SAFE_SCHEME` = `https:`/`http:`/`mailto:`/`#`/`/`)
- `web/public/lib/notes-view.js` (headings floored at h2; links open externally, `noopener`)
- `web/public/index.html:173-179` (a column **beside** the picture, not over it; close ✕)
- `web/test/markdown.test.ts` (cases)
- Shared-fixture pattern for Kotlin: `android/feature/catalog/src/test/kotlin/NextUpFixtureTest.kt:101-103`
- Phase 01: `MediaSet.hasSummary`, `CoreClient.setText(setId, "summary", "")`

## Overview
Priority P2 · Status done.

## Key insights
- Summary text is in the index the phone holds — no network fetch.
- The web parser is pure and returns a tree; port the **parser** to Kotlin and
  render the tree in Compose (`AnnotatedString` + `LinkAnnotation`). Parity of
  the parse is pinned by a shared fixture (`input → tree` JSON) run by both
  `bun test` and the Kotlin test, per lesson 2026-09-18.
- Innerhtml risk does not exist in Compose, but the scheme allow-list does
  (an `intent:` or `file:` link must stay text).
- Layout: beside the picture on a wide window (tablet landscape: video shrinks
  to the left, notes column right ~40 %); phone portrait: below the video; phone
  landscape: a sheet over the video. Web's rule is "beside, not over"; the sheet
  on phone-landscape is the one deliberate difference (no room beside).

## Requirements
- "Notes" button in the top bar when `hasSummary`.
- Auto-open for `TUTORIAL`; closed for others; a panel belongs to the title it was opened on (reset on open, and on up-next switch).
- Rendered subset identical to web; links with safe schemes open via `ACTION_VIEW`; others plain text.
- Scrollable, selectable text.

## Architecture
`Markdown.kt` (`:feature:player` or `:core:model` if a TV surface will want it — choose `:core:model`, pure Kotlin, no Android deps):
`parseMarkdown(text): List<Block>` with `Block`/`Span` sealed types mirroring web.
`NotesPanel.kt` (ui-mobile) renders blocks. VM loads text on open (IO) with a set-id guard.
`PlayerScreen` layout switches on window width class + orientation.

## Related code files
Create:
- `android/core/model/src/main/kotlin/markdown/MarkdownBlocks.kt`, `MarkdownSpans.kt`, `MarkdownParser.kt` (each < 200)
- `web/test/fixtures/markdown/cases.json`, `web/test/markdown-shared-fixtures.test.ts`
- `android/core/model/src/test/kotlin/markdown/MarkdownFixtureTest.kt`
- `android/ui-mobile/src/main/kotlin/NotesPanel.kt`, `NotesLayout.kt`
Modify:
- `PlayerViewModel.kt` (notes text + open state), `PlayerScreen.kt`, `PlayerTopBar.kt` (Notes button), `UpNext` switch path (close/reset notes)

## Implementation steps
1. Build `cases.json` from `markdown.test.ts` scenarios (nesting, unclosed markers, unsafe scheme, fenced code, headings floor); web test asserts `parseMarkdown` against it.
2. Port parser; Kotlin fixture test green.
3. Renderer (headings as `titleMedium`+, lists with indentation, quote bar, code monospace block).
4. VM load + guard; auto-open rule; layout variants.
5. Device: open a course lesson (panel open, video beside it on tablet landscape); a film with a summary (closed, button shows); tap a https link (browser opens).

## Todo
- [x] shared markdown fixture + web test
- [x] Kotlin parser + fixture test
- [x] Compose renderer
- [x] VM + auto-open + layouts
- [x] check.sh, bump, changelog, device run

## Success criteria
- Same fixture passes on both surfaces; an `intent:` link renders as text.
- Lesson auto-opens, film does not; switching title via up-next resets the panel.

## Risks
- Parser port subtleties (nested emphasis) → the fixture is the contract; add cases for any bug found.
- Video resize on panel toggle re-lays out the surface: verify no playback hiccup.

## Security
Scheme allow-list enforced in the parser (not the renderer), as on web; links open in external apps only.

## Next steps
12 closes docs.

## Implementation notes
- Parser in `:core:model` (`model.markdown`: `MarkdownTree.kt`, `MarkdownSpans.kt`,
  `MarkdownParser.kt`), ported line for line. `cases.json` (29 cases) was generated
  by running `markdown.js` itself, so it records the web's actual behaviour —
  including emphasis not nesting inside emphasis and `****` opening a strong run.
  Fixing either means fixing both surfaces and regenerating the fixture.
- `:core:model` is a JVM module: its task is `test`, which `testDebugUnitTest`
  never ran, so `scripts/check.sh` now names `:core:model:test`. The fixture is a
  declared input of that task, or Gradle calls the test up to date after a
  fixture edit (verified: a deliberately wrong level was not caught until it was).
- `SummarySource` (`:core:playback`, beside `SubtitleTrackSource`) reads the
  `summary` asset through `CoreClient.setText`. `PlayerNotesController` follows
  `openSet` like `PlayerHeldController`: every title change drops the panel,
  `collectLatest` abandons a load already overtaken. Parsed in place, not on a
  worker: notes are kilobytes.
- Only `http(s)`/`mailto` links are tappable; `#` and `/` pass the web's
  allow-list but point into the web player's own page.
- `PlayerScreen.kt` was at 199 lines: the controls auto-hide effect moved to
  `PlayerScreenLifecycle.kt` (`ControlsAutoHide`) to make room for `NotesLayout`.

## Device run (2026-09-25, tablet, `test` profile)
- The installed channel index still has 0 `assets` rows, so no title has notes
  on the phone today. Tested with a fixture: this machine's 162 Geldhochschule
  summaries plus a hand-written markdown summary on Justice League, copied into
  the installed index after launch; original restored afterwards.
- Geldhochschule 1 (lesson): panel open by itself, beside the picture (tablet
  landscape). Up-next into lesson 2 swapped in lesson 2's notes; nested lists render.
- Justice League (film): closed, "Notes" in the top bar; opening it resized the
  picture with playback uninterrupted. Heading, nested ordered list, italic,
  quote, code span render; the https link opened Firefox; the `intent:` link is
  plain text. ✕ closes it and the picture returns to full width.
- **Open:** `**strong**` renders at regular weight. The reading face (Newsreader,
  variable) is declared at 400/500/600; neither `Bold` nor `SemiBold` came out
  heavier, which suggests every weight of that face renders as its default
  instance. That would also affect `labelLarge` (Medium) app-wide — a design
  system question, not fixed here.
- Left on the `test` profile: Geldhochschule 1 finished, 2 started, Justice
  League at ~1:30. Device returned to the `andre` profile.
