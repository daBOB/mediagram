---
title: "Ceremony shader precision: only vertex mediump pays, so it waits for a motion check"
author: Andre
date: 2026-11-08
branch: spieltisch-games
cost: { minutes: 52, tokens: 280895 }
id: l-090e8666
scope: repo
status: active
src: agent
touches:
  - crates/mediagram-game/client/src/tv/ceremony/ceremony-set.ts
  - crates/mediagram-game/client/src/tv/ceremony/ceremony-curtain.ts
  - crates/mediagram-game/client/src/tv/ceremony/ceremony-stage.ts
  - crates/mediagram-game/client/tests/ceremony-curtain.test.ts
  - docs/system-architecture.md
  - docs/project-changelog.md
  - crates/mediagram-game/client/src/tv/ceremony/ceremony-studio-look.ts
---

## Decided
- The awards ceremony keeps three.js's default highp precision for now. Measured on the TV box (Skyworth HPR302, Mali-G57, WebView 156) with the real ceremony (fox, frog, panda, 51k tris, matcap, MSAA), two rounds each: highp 43.6/43.7 fps, `precision: 'mediump'` 47.3/47.3 (+8 %), fragment-only mediump 44.1/44.3 (+1 %).
- The whole gain is in the vertex (skinning) shaders, which is where reduced precision can make animated avatars wobble. Stills showed no difference; adopting it needs someone to watch the animation on the TV first.

## Tried and ruled out
- Fragment-only mediump (a `precision mediump float;` line injected into `ShaderChunk.color_pars_fragment`): safe for skinning but worth ~1 % on the matcap ceremony.
- Render scale on the ceremony: the 2K/52k-triangle avatars are vertex-bound on this box; 0.85 and 0.75 gave +6 % in the avatar test scene while softening the picture.

## Watch out
- three.js 0.170 defines `WebGLRenderer.render` per instance in its constructor, so a subclass override never runs: wrap the instance's method instead.

In the code: `crates/mediagram-game/client/src/tv/ceremony/ceremony-stage.ts` (renderer creation). The harness and raw numbers live outside this repo, in the spieltisch project's `prototypes/tv-box-render-benchmark/` (`run-ceremony-on-box.sh`, `ceremony/three-shim.mjs`).
