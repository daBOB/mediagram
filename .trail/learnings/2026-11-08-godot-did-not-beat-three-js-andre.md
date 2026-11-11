---
title: "Godot did not beat three.js on the TV box, only steadier"
author: Andre
date: 2026-11-08
standing: true
id: l-658166f3
scope: repo
status: active
src: init-sessions
---

## Decided
- A rerun on the current Skyworth box matched the earlier benchmark within 1.5 fps. three.js in the system WebView led on average fps for most scenes, while Godot GLES3 won only with real-time shadows.
- In the code: Harness is in prototypes/tv-box-render-benchmark/. Box is Skyworth HPR302 (Mali-G57) at 192.168.0.37. Don't propose a Godot rewrite for speed. The Ryzen 3 4300U mini PC is a planned second box, not the current one.

_From past Claude sessions here, Nov 8._
