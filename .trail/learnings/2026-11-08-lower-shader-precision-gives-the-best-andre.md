---
title: Lower shader precision gives the best cheap speedup on the TV box
author: Andre
date: 2026-11-08
standing: true
id: l-8a37cde0
scope: repo
status: active
src: init-sessions
---

## Decided
- A one-change-at-a-time sweep on the avatar win screen showed medium precision gave about 15 percent more fps with no visible loss. Lowering render scale gained less and looked softer.
- In the code: Use mediump in pixel shaders only, since vertex precision may make animated avatars wobble. Render scale 0.67 is visibly softer. Verify on the box with the sweep harness in prototypes/tv-box-render-benchmark/.

_From past Claude sessions here, Nov 8._
