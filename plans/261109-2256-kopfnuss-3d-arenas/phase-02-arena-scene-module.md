# Phase 2 — Arena scene module

**Status:** pending (after phase 1's go).

## Goal

A lazily loaded three.js chunk, like `tv/grand-allee/grand-allee-stage.ts`, that draws the Studio arena and is told
each frame what the stage is. No game rules in it: it reads a small view model, so the quiz or Dreifelder could use
it later.

## Shape

- `client/src/tv/arena/arena-stage.ts` — `startArena(canvas, getView, serverNow) → { dispose, stats }`; renderer,
  camera, the loop, the frame log.
- `arena-set-studio.ts` — floor, light rings, light columns, podiums for signs, the light tower, the pedestal
  (from the mockup's `studio` theme; few draw calls: merge the static set into one or two meshes).
- `arena-cast.ts` — loads the arena avatars (ceremony's `loadAvatar` and studio look reused, not copied), flat
  stand-ins until they arrive, poses with cross-fades, places them by seat.
- `arena-camera.ts` — one camera position per stage, eased moves between them.
- `arena-layout.ts` — pure: seat positions for 2–8 players, step positions on the tower, which pose each player
  takes. Unit-tested.
- `arena-view.ts` — the view model type: stage kind, players (id, avatar, pad colour, pose, tricks), signs,
  chosen sign, trick arcs, climb steps, fact winner.

Every file under 200 lines; each module states in its header comment what it draws.

## Rules carried over

- Matcap studio light only (no environment light, no real-time shadows: blob shadows as in the mockup).
- Text never in 3D: HTML tags are placed from projected positions (`camera.updateMatrixWorld()` first).
- A missing avatar or a WebGL failure falls back to today's flat Kopfnuss screens.

## Todo

- [ ] view model and pure layout with tests
- [ ] set, cast, camera, stage loop
- [ ] HTML tags from projected positions
- [ ] fallback to the flat screens
