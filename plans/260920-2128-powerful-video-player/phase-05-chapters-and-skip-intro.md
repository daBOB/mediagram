# Phase 05 — Real chapters, and skip intro

**Status:** not started

## Context

- `crates/mlib-spec/src/schema.rs` — `SCHEMA_VERSION`, and the index migrations
- `crates/mediagram/src/commands/metadata.rs` — what runs over a set today
- `crates/mediagram/src/media/` — where ffprobe already lives
- `web/public/lib/seek-model.js` — the bar the markers go on
- `docs/superpowers/specs/2026-09-20-android-player-transport-controls-design.md`
  — the phone's bar, which is owed the same thing

## What is there now

Nothing. `chap` in the index looks like chapters and is not: it holds
`"Basislektionen / Start"`, a breadcrumb for a course. 162 sets have one.

The name is the trap. A column called `chap` that holds a category path will
be read as chapters by whoever comes next, and this phase is the moment to
give the real thing a name that is not that one.

## The change

**In the uploader.** `ffprobe -show_chapters` during `mediagram metadata`,
stored as JSON on a new column. An index schema bump, and a `metadata` re-run
over 566 sets — which is why this is late in the plan and not early.

**In the player.** Markers on the scrub bar, `Skip intro` when the chapter the
playhead is in is named like one, and the next/previous chapter on the
keyboard.

**On the phone**, because Surface Parity says a feature on one surface is owed
to the other, and the phone's bar is the newer one.

## What counts as an intro

A chapter whose title matches a small, written-down list — `intro`,
`opening`, `opening credits`, `main titles`, `recap`, `previously`. Not a
guess from its position or length: a cold open is not an intro, and skipping
one is skipping the programme.

Where a file has no chapters there is no button. Detecting an intro by
fingerprinting audio is a different project.

## Files

**Modify** `crates/mlib-spec/src/schema.rs`, `crates/mediagram/src/commands/metadata.rs`,
`web/src/catalog.ts`, `web/src/routes.ts`, `web/public/lib/seek-model.js`,
`web/public/lib/player.js`, `web/public/style.css`, the Android player

**Create** `crates/mediagram/src/media/chapters.rs`,
`web/public/lib/chapters.js` + `web/test/chapters.test.ts`

## Todo

- [ ] a column that is not called `chap`, and a spec version bump
- [ ] `ffprobe -show_chapters` in `metadata`, with a file that has none
      producing nothing rather than an error
- [ ] index migration, and a test that a pre-bump index still opens
- [ ] catalog and route carry them; the browser is told times and titles only
- [ ] markers on the bar, which must not make the bar harder to hit
- [ ] `isIntro(title)` against the written list, tested with its edge cases
- [ ] `Skip intro`, visible only inside such a chapter
- [ ] previous/next chapter on the keyboard
- [ ] the phone gets the same
- [ ] `./scripts/check.sh`, and a `metadata` re-run

## Success criteria

- A file with chapters shows them; one without is unchanged in every way.
- The markers are decoration: dragging the bar is no harder than before.
- "Skip intro" never appears on a film that has a cold open and no intro
  chapter.
- An index built before this still opens in both players.

## Risks

| Risk | Mitigation |
|---|---|
| A re-run over 566 sets is long and touches the channel | It is a local probe, not an upload; nothing is re-sent |
| `chap` and the new column confused forever | The new one is named for what it holds and the old one's meaning is written down beside it |
| Markers on a 20-minute episode are a picket fence | Drawn only above a minimum spacing, in the bar's own quiet colour |
