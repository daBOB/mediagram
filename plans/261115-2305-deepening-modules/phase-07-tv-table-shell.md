# Phase 07 — The TV table shell (Spieltisch, TV client)

## Problem

`client/src/tv/TvRace.tsx`, `TvCards.tsx`, `TvGrandAllee.tsx` are ~100-line
near-copies (same state, lazy `import()` + late-handle dispose, OK opens
settings, `CHEER_MS = 4000`, restart by bumping `round`, inline `byFinish`
ranking → `<TvFinal ranked jingle={false}>`); they differ in where the ranking
comes from (`scene.stage.ranking` / `scene.ranking` / `scene.turn.ranking`),
CSS class and wait text. The stage entry files share one skeleton (canvas,
parallel sound load, avatar + font preload, ResizeObserver, up-front shader
compile, RAF loop, dispose with `forceContextLoss`); Grand Allee's is furthest
(own renderer with `localClippingEnabled`, `compileAsync` texture upload,
`disposeScene`, stats/FrameLog, try/catch cleanup). `RaceSound` already serves
Grand Allee from `race/`; `CardsSound` copies its engine; `KopfnussSound` is a
separate synth engine for the 2D Kopfnuss and Preisfrage. Wrappers, stages and
sound classes have no tests; `bun test` with no DOM.

## Decided

- Scope: the three 3D boards (Race, Cards, Grand Allee). Flüchtig (8 s cheer,
  own jingle, ranks by score) and the 2D scenes keep their wrappers.
- The lifecycle becomes a plain TS controller (load, late-handle dispose,
  4 s cheer, restart, settings on OK) with the stage loader injected; the React
  wrapper only renders. `bun test` drives it with a fake stage and fake clock.
- Until phase 08, the shell reads each game's ranking through a small per-game
  hook; phase 08 removes it.
- One TV sound engine: `RaceSound`'s engine moves out of `race/`; Cards' copy
  becomes cues on it. Kopfnuss's synth engine stays.
- Grand Allee's stage skeleton becomes the shared stage host; shader precision
  and render scale stay per-scene options (ceremony learning: highp kept).
- Race and Cards gaining `compileAsync` and `disposeScene` is its own commit,
  checked on funstation (where Spieltisch runs) against the 30 fps floor.

## Steps

- [x] Shell controller + tests; React shell; three wrappers become thin.
- [x] Shared stage host from Grand Allee's skeleton; per-game world/HUD/camera.
- [x] Shared sound engine; Cards cues on it; `race/` no longer imported by
      Grand Allee for shared parts.
- [x] Commit (refactor, patch bump); merge; check.sh.
- [ ] Commit: Race/Cards adopt compileAsync + disposeScene; fps check on
      funstation (FrameLog/stats; no settings changed); patch bump; merge.

## Done when

A new 3D game supplies its world, HUD, motion and cues; lifecycle, stage host
and sound engine are one module each, the lifecycle under test.
