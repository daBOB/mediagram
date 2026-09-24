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
Priority P2 · Status pending.

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
- [ ] shared markdown fixture + web test
- [ ] Kotlin parser + fixture test
- [ ] Compose renderer
- [ ] VM + auto-open + layouts
- [ ] check.sh, bump, changelog, device run

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
