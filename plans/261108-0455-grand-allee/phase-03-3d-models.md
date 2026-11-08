# Phase 03 — 3D models in Blender

**Goal:** the ten Grand Allee hotels as real models, built in stages, in the
"Abendsonne" look the user picked on 2026-11-07
(`plans/reports/look-test-261107-bauherr-realistic-round-2-report.md`):
realistic, golden hour, everything matte, owner colour on real things.

## Art direction (from the look test, kept)

- Low warm sun from the left (~19° up), long soft cool shadows; light is
  baked into the textures, so the TV draws the hotels unlit.
- Matte everywhere, no specular; water is a still matte basin.
- Owner colour on awnings, flags, the roof or facade name sign, the property
  line and entrance canopies: modelled as separate parts named `tint_*`,
  textured white, tinted on the TV.
- Every hotel is built in stages: main building, annexes, leisure wing last
  (the stage counts of `grand_allee/board.rs`).

## The ten hotels

World-themed, our own names and designs (user, 2026-11-08: "we need the types
from the original" — the classic's idea of hotels from around the world, not
its hotels: none of its names or designs is used).

| Id | Hotel | Stages | Theme and look |
|---|---|---|---|
| N1 | Lagune | 3 | South Seas: bungalows on stilts, thatched roofs, lagoon pool |
| N2 | Kirschblüte | 4 | Japan: ryokan with layered roofs, lit paper screens, stone garden, cherry trees |
| N3 | Fjordhus | 5 | Scandinavia: red timber lodge, white trim, sauna house by the water |
| S1 | Hacienda Sol | 3 | Mexico: terracotta and white render, arcaded courtyard, cacti |
| S2 | Riad Atlas | 4 | Morocco: clay-red walls, horseshoe arches, tiled courtyard fountain |
| S3 | Alpenrose | 5 | Alps: chalet, wide balconies, geraniums, wellness garden |
| P1 | Maharadscha | 6 | India: palace with onion domes, pavilion, water garden |
| P2 | Skyline | 6 | New York: art-deco tower, a storey band per stage, rooftop bar |
| P3 | Savanne | 5 | Safari lodge: thatched huts, watering-hole pool |
| P4 | Belle Époque | 6 | Paris: mansard palace, glass-and-iron winter garden |

A first prototype in the European-town style (Seeblick, renders in
`reports/seeblick-prototype/`) proved the pipeline; the user then chose the
themed set above.

## Budgets (TV box, measured)

- The look test's whole town (92.5 k triangles, baked light) held 59.6 fps on
  the box at 1080p. Target per hotel fully built: ≤ 6 k triangles; all ten
  ≤ 60 k.
- One texture atlas per hotel, 1024², baked colour × light, shipped as KTX2
  ETC1S (memory, not speed). No alpha-tested cut-outs (expensive under MSAA
  on the Mali); fences, trees and railings as geometry.
- Scale 1 unit = 1 m. Field pitch 4 m: outer plots 20–24 m wide × 16 m deep,
  park plots 32 m × 14 m (final positions in phase 04).

## Pipeline

1. `crates/mediagram-game/tools/grand-allee-models/`: one Python (bpy)
   builder per hotel plus shared parts (walls with window insets, roofs,
   balconies, awnings); runs headless, so every model is reproducible.
2. Procedural matte materials in Blender → UV atlas → Cycles bake of
   colour × golden-hour light into the atlas.
3. Export one GLB per hotel: a node per stage (`stage_1` … `stage_n`, leisure
   last) and `tint_*` nodes; plus a preview render per hotel.
4. QA with `blender-asset-qa` (normals, floating parts, scale) and a
   triangle/texture report.

## Order

1. **Prototype: Kirschblüte, all four stages** → renders → user sign-off on the look
   (the Seeblick prototype proved the pipeline first).
2. The other nine, in two batches.
3. Board pieces (field setts, special-field signs), dice (ivory number die,
   oak permit die) — after the board layout in phase 04.

## Todo

- [x] shared parts library + Seeblick prototype (pipeline), renders
- [x] Kirschblüte prototype; user sign-off on the look (2026-11-08)
- [x] remaining nine hotels (three agents in parallel, review sheets in `reports/hotels/`); user approved the set (2026-11-08)
- [x] Skyline height: user kept it at 40 m (compared with a 24 m variant, `reports/hotels/skyline-compare.jpg`); phase 04's board camera must frame it, and it can hide the fields behind it
- [x] bake, export, QA: all ten in `client/public/grand-allee/` (2026-11-08)

## Result

| Hotel | Stages | Triangles | GLB |
|---|---|---|---|
| Lagune | 3 | 3 294 | 367 KB |
| Kirschblüte | 4 | 1 272 | 232 KB |
| Fjordhus | 5 | 2 828 | 354 KB |
| Hacienda Sol | 3 | 1 856 | 320 KB |
| Riad Atlas | 4 | 3 358 | 465 KB |
| Alpenrose | 5 | 2 388 | 337 KB |
| Maharadscha | 6 | 3 976 | 660 KB |
| Skyline | 6 | 1 136 | 335 KB |
| Savanne | 5 | 4 112 | 646 KB |
| Belle Époque | 6 | 3 440 | 537 KB |

About 28 k triangles and 4.2 MB for all ten fully built. Atlases 1024² (outer
plots) and 2048² (park plots), WebP inside the GLB; KTX2 is phase 04's
loader decision. Every GLB rendered unlit as the TV draws it matches its
approved preview (`reports/hotels/all-hotels-glb-tv.jpg`).

**Z-fighting:** the first bake had 211 coplanar overlaps the TV camera could
see (trim on roofs, rails and posts sharing top faces, ground pieces flush
at the plot edge); each was fixed by a 1–2 cm nudge, and all ten are at 0.
The generic asset check still "fails" every hotel on faces pointing down and
parts standing on the ground, which board pieces seen from above may have.

**Refactor:** the builders were split under 200 lines (`<hotel>_kit.py`,
`geometry.py`, `roofs.py`; shared `prism` and `cylinder`); fingerprints of
every part were identical before and after, so the baked GLBs stand.

**For phase 04:** shadows on the ground are not in the GLBs (the plot ground
belongs to the board; stage-dependent shadows need their own solution);
Skyline is 40 m tall and the board camera must frame it.
