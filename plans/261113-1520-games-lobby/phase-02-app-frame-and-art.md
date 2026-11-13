# Phase 2 — App: open the page directly, serve the art

Context: [plan](plan.md). Priority high · Status done

## Requirements
- `TvGamesFrame`: no shelf. Starting → "Starting game night…", Failed → message + "Press Back to
  leave", Ready → the page (`tvUrl`, `?films=0` when the media app did not answer).
- Back: page's `tvBack()` first; else `model.back()`; when nothing ran, leave (stops the host).
- `GamePageClient` answers `/game-art/<name>.webp` from `assets/game-art/` (names checked).
- Art moves: `res/drawable-nodpi/game_{poster,backdrop}_<kind>.webp` →
  `assets/game-art/{poster,backdrop}-<kind>.webp` (git mv). Delete `TvGamesShelf.kt`, `ShelfGames.kt`.
- `TvGamesFrameTest` rewritten for the new flow; `GamePageClient` art test.
- Docs that name `ShelfGames.kt`/`ArtByKind` for new art point at `assets/game-art/` instead.

## Todo
- [x] frame + Back
- [x] art serving + move
- [x] tests, detekt/lint, assemble
