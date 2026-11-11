# Phase 5 — The board's life

Priority: medium. Status: pending. Needs phase 2 (`byFinish`). TV only.
Result: 0.175.1.

## Problem

- `TvRace.tsx` (101 lines), `TvCards.tsx` (98) and `TvGrandAllee.tsx` (101)
  share 61 identical lines; the rest differs in names only. Each one loads its
  stage lazily, guards against the stage arriving late, opens the settings on
  OK, cheers for `CHEER_MS = 4000`, swaps the board for the ranking, and
  rebuilds when the same table starts again.
- `TvFluechtig.tsx` types the settings, the cheer (`CHEER_MS = 8000`) and the
  restart again, without a WebGL stage.
- The ranking sort is copied four times (`TvRace:88`, `TvCards:87`,
  `TvGrandAllee:90`, `TvKopfnuss:74`).
- `TvApp.tsx:35,37,41` knows how each board tells a new table of players.
- The races in this code (a stage that finishes loading after the ranking took
  over) have no test.

## Decided

- New `client/src/tv/board-life.ts`, plain TypeScript, no React. It is given a
  start function (optional: Flüchtig has no stage), the cheer length, and
  timers it can be handed in tests. It is told, on every scene, whether the
  game is over and which table of players is seated, and when OK was pressed.
  It says what to show: loading, the board, the failure text or the ranking,
  and whether the settings are open.
- New `client/src/tv/use-board-life.ts`: the thin binding.
- `TvRace.tsx`, `TvCards.tsx`, `TvGrandAllee.tsx`, `TvFluechtig.tsx` keep a
  few lines each: the literal lazy `import()` of their stage, their cheer
  length, their table of players, and their ranking (`TvFinal` with their own
  flags). The ranking order comes from `byFinish`.
- `TvApp.tsx` drops the three `key=` props.
- `TvQuiz`, `TvPoster`, `TvKopfnuss`, `TvDreifelder` are not boards with a
  cheer and stay as they are (`TvKopfnuss` already uses `byFinish` after phase 2).
- `TvFinal`'s `jingle` and `title` flags are not touched.

## The interface, as tests (`board-life.test.ts`, fake timers)

1. A stage that finishes loading after the ranking took over, or after the
   board left the screen, is disposed.
2. A stage that fails to load shows the failure text.
3. The cheer runs once per game for its length, then the stage is disposed
   and the ranking shows.
4. OK opens the settings while playing; not on a held key, not after the
   cheer. The cheer closes them.
5. The same table starting again builds a fresh stage.
6. A new table of players builds a fresh stage.

## Acceptance

- The four board files hold no timer, no `gone` flag and no `keydown` listener.
- `grep -n "key=" client/src/tv/TvApp.tsx` finds nothing for the boards.
- `bun run typecheck`, `bun test`, `bun run build` green.
- On the TV box: a round of Race and of Grand Allee played to the ranking and
  started again from the same table; a screenshot of each ranking kept.

## Risk

- Race and Cards may leak a WebGL context when a stage is disposed
  (`race-stage.ts:52`, `cards-stage.ts:59`; read, not measured). Out of scope
  here; note it if the restart on the box shows it.
