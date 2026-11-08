---
title: "Board pieces seen from above: judge z-fighting by what the TV camera can see, and prove refactors by fingerprint"
author: Andre
date: 2026-11-08
branch: spieltisch-games
cost: { minutes: 1, tokens: 3093 }
id: l-fd13a036
scope: repo
status: active
src: agent
touches:
  - crates/mediagram-game/tools/grand-allee-models/zfight_visible.py
  - crates/mediagram-game/tools/grand-allee-models/fingerprint-all.sh
  - crates/mediagram-game/tools/grand-allee-models/check-overlaps.sh
  - crates/mediagram-game/tools/grand-allee-models/bake-all.sh
  - crates/mediagram-game/tools/grand-allee-models/build.py
---

## Decided
- The Grand Allee hotels are checked with `crates/mediagram-game/tools/grand-allee-models/zfight_visible.py`, which counts only same-facing coplanar overlaps whose faces do not point down. The board camera always looks down, so undersides, soffits and bottoms on the ground can never show or flicker.
- The generic Blender asset check (`blender-asset-qa` qa_check.py, profile game) "fails" every hotel. That is expected for board pieces: it counts down-facing overlaps and calls parts standing on z = 0 "floating", because the ground belongs to the board, not to the hotel GLB.
- A refactor of the builders is proven with `crates/mediagram-game/tools/grand-allee-models/fingerprint-all.sh`, which hashes every part's name, material and vertex positions per stage. Identical output before and after means the baked GLBs stay valid, saving a ~50 min CPU bake. The builds are deterministic (two clean runs match).

## Tried and ruled out
- Fixing z-fighting from the baked GLBs: the bake joins parts per stage, so the names are lost. Check the built `.blend` (`build.py --no-render`, ~15 s for all ten) to name the exact parts.

## Watch out
- The first bake had 211 visible overlaps. The usual causes were trim flush with roof edges, posts ending exactly at a beam's top, rails sharing a corner, and ground pieces flush with the plot edge. A 1–2 cm nudge fixes each one.

In the code: `crates/mediagram-game/tools/grand-allee-models/check-overlaps.sh`, `crates/mediagram-game/tools/grand-allee-models/bake-all.sh`, `crates/mediagram-game/tools/grand-allee-models/build.py`.
