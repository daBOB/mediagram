# Phase 08 — The Room owns the end of a game (Spieltisch)

## Problem

`Game::is_finished` (`game.rs:65`) is never called by the Room (only by the
simulator `grand_allee/play.rs:62` and Kopfnuss's inner quiz). Each game builds
the phone's Final and the late-joiner wait ("Du spielst in der nächsten Runde
mit", seven copies) itself, with three ideas of "of": seats (`self.ids.len()`
in the card games, `race_views.rs:47`), everyone in the room
(`view.players.len()` in quiz, poster, kritzelei, preisfrage, dreifelder,
kopfnuss, zündstoff), or 1 + hunters (`fluechtig_views.rs:35-39`). Shared
places: `rank_of` by equal score (quiz, poster, kritzelei, preisfrage),
positional (`final_by_order`), winning side shares 1 (Flüchtig), tie-broken
(card games). The TV gets ranking from the scene for 3D games and
`byFinish(stage.order)` for some 2D ones; `TvFinal` re-sorts by score when not
`ranked` (`TvFinal.tsx:36-37`: Quiz, Poster, Kritzelei, Preisfrage, Flüchtig).

## Decided

- The Room owns the end for every game: phone Final, late-joiner wait screen,
  and one final-ranking fact on `TvView`. No game builds end screens.
- The game's own final ranking (order + shared places, optional title as
  Flüchtig's) is the single source; TV and phone show it as given; `TvFinal`
  stops re-sorting by score.
- "of" counts the players ranked: everyone seated when the game ended,
  absent ones included; late joiners waiting for the next round are not.
- The TV shell (phase 07) and the 2D scenes run cheer → TvFinal from the one
  ranking fact; the per-game ranking hook goes away.
- ADR 0002 holds: facts from the host, wording per client.

## Steps

- [ ] Commit 1 (refactor): `Game` reports seats and, at the end, its ranking
      (replacing `is_finished`); the Room builds Final/Wait and `TvView`'s final
      fact, preserving each game's current "of" and order; TV reads the fact.
      Tests in `room_tests` for game over; per-game Final tests replaced.
- [ ] Commit 2 (behaviour): "of" = players ranked everywhere; `TvFinal` shows
      the given order. Changelog entry.
- [ ] Patch bumps; merge; check.sh.

## Done when

Phone place and TV place come from one ranking the Room sends; game over is
tested once in the Room.
