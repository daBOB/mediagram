---
title: "Benchmark with shipped-quality avatars, not simplified test models"
author: Andre
date: 2026-11-08
standing: true
id: l-86a5a87a
scope: repo
status: active
src: init-sessions
---

## Watch out
- The first tricks sweep used a lighter baseline avatar and looked wrong on the TV. The user stopped it and it was rerun on the real 2K, 52k-triangle avatars.
- In the code: When measuring on the TV box, use the real avatar assets (2K textures, about 52k triangles, matcap, MSAA). Do not use reduced stand-ins. Judge visual quality on the TV, not from fps alone.

_From past Claude sessions here, Nov 8._
