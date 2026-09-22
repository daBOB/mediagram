# Phase 9: A start page

**Status:** Not started — sketch

**Deliverable:** The phone opens on a start page rather than the three shelves:
Continue, Next up, and the latest films, series and courses.

## Context

- Phases 5, 7 and 8 — everything the rows read
- [`260922-0009-web-player-start-page/`](../260922-0009-web-player-start-page/)
  — shipped 2026-09-22, and its plan states outright that nothing in it touches
  the Android app. This phase is that sentence coming due
- `web/public/lib/home-shelves.js:31-61` — **the model, pure and tested.** Port
  it; do not re-derive it
- `web/public/lib/home-view.js:42-100` — how it is drawn, and the decision that
  watch-state rows are plates rather than full-width list rows

## The shape

The rules in `home-shelves.js` are the substance and they are subtle: Next up
offers one card per show underway — the episode in progress, else the first
unwatched episode after the one most recently finished — skips an episode
watched out of order, drops a show with nothing left, and does not repeat what
Continue is already showing. Latest ranks by arrival rather than release, so a
series still being uploaded keeps its place.

Two payload facts the web needed are already on the phone's side of the
boundary or are not: `addedAt` on a catalog row came from the index, so the
core has it; `finishedAt` comes from watch state, which is phase 4's store.
Check both before building the rows.

`shelvesOf` gains a sibling rather than growing: this is a different question
over the same catalog.

## Todo

- [ ] confirm `addedAt` reaches Kotlin, and `finishedAt` comes from the store
- [ ] port `home-shelves.js` as a pure module, with its tests
- [ ] the page, and the app opening on it
- [ ] `./scripts/check.sh`
