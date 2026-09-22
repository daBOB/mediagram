# Phase 8: Watchlist, kids and lists

**Status:** Not started — sketch

**Deliverable:** The three shelves the web player has and the phone does not —
Watchlist, Kids, and hand-built collections — plus the controls in the player
that fill them.

## Context

- Phase 4 — the rows; they are stored the moment that phase lands
- `web/public/lib/player.js:858-897` — the watchlist and kids toggles, and that
  each carries its own pressed state
- `web/public/lib/collections-view.js:33-79` — create, rename, delete, and
  "Play all"
- `web/public/lib/watch-state.js:264-283` — **kids marks are shared across
  profiles, deliberately.** Whatever phase 4 settles about profiles, this rule
  survives it
- `android/ui-mobile/src/main/kotlin/AppChrome.kt` — where new destinations go

## The shape

Mostly screens over rows that already exist. Two things are not:

**`window.prompt` has no counterpart.** The web creates and renames a list with
a browser modal. The phone needs real dialogs, and `AppChrome`'s menu is where
the actions hang.

**"Play all" is a queue, not a filter.** `app.js:326-348` threads a fixed
snapshot rather than re-deriving the next title from a live collection, so that
editing a list mid-run does not move the ground. Port the snapshot, not the
shortcut.

## Todo

- [ ] watchlist and kids toggles in the player
- [ ] the three shelves, reachable from the chrome
- [ ] create, rename, delete a list, in real dialogs
- [ ] "Play all", as a snapshot queue
- [ ] `./scripts/check.sh`
