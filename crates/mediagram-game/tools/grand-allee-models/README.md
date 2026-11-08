# Grand Allee hotel, board and dice models

The ten Grand Allee hotels, the board and its two dice as Blender 5.2 builder scripts. Every model is made
by code, headless, so any hotel can be rebuilt, changed and re-baked at will.
Design: spieltisch `design/grand-allee-design.md`; plan:
`plans/261108-0455-grand-allee/phase-03-3d-models.md`.

| File | What it does |
|---|---|
| `geometry.py` | Core mesh helpers in plot space (metres; x along the road, y away from it, z up): objects, boxes |
| `roofs.py` | Hip, gable and cone roofs, onion domes |
| `parts.py` | Building parts (balconies, awnings, canopies, pools, parasols, trees, posts, `prism`, `cylinder`); re-exports `geometry` and `roofs`, so builders import only `parts` |
| `materials.py` | Matte materials: CC0 textures from `textures/` box-projected in metres, procedural facades (window grid, timber frame with lit paper screens), owner-colour stripes |
| `<hotel>.py` | One builder per hotel: `build(owner_hex)` returning the `stage_1..n` empties (stage counts as in `grand_allee/board.rs`, leisure wing last) |
| `<hotel>_kit.py` | That hotel's own shapes, materials and constants (`PLOT`, `STOREY`); Kirschblüte's few live in its builder |
| `build.py` | Builds a hotel in the golden-hour preview scene and renders its stages and a close-up (`--no-render` only saves the `.blend`) |
| `bake.py` | Bakes the lit colour into one atlas per hotel and exports `<hotel>.glb` for the TV |
| `verify_glb.py` | Renders an exported GLB the way the TV draws it (unlit, owner tint multiplied) |
| `zfight_visible.py` | Lists coplanar overlaps the TV camera could see (faces pointing down are never visible) |
| `check-overlaps.sh` | `zfight_visible` over every hotel, part by part, in ~15 s |
| `bake-all.sh` | Bake, export, TV render, asset check and overlap count for every hotel |
| `board_path.py` | The board as plain geometry (no Blender): the winding road's 40 fields, the ten plots, river, pond and sea; `python3 board_path.py <dir>` writes `layout.json` and lists any plot on the road, in the water or on another plot |
| `board_layout.py` | Renders that layout with the baked hotels in place, top view and TV view (55°); `place_hotels` is shared with `board_review.py` |
| `board_kit.py` | The board's constants (field tints and icons, kerb, inlay, water, Bank and Rathaus places) and 2D helpers: polyline offsets, distances to road and water, inside the loop; checks the field kinds against `grand_allee/board.rs` |
| `board_ink.py` | Flat decals for the bakes only: polygons on a plane, pen strokes, ribbons, dashes, letters, and the icons (Bauherr's coin, pediment, staked plot, crane, door with star; the gift; the permit die's tick and cross) |
| `board_ground.py` | The ground as the top-down bake sees it: terrain with the river, pond and beach sunk in, water, town paving and gardens, plot lawns, park paths, the road's joints, setts, inlays, icons and names; kerbs and plot edging (also in the GLB) |
| `board_town.py` | The Bank (field 0) and the Rathaus (field 20), our own designs in the hotels' style, and the bridges' parapets and railings over fields 1 and 21 |
| `board_trees.py` | Trees placed off the road, water, plots and paths, where neither their shadow nor their crown (from the TV) covers a plot, building or special field |
| `board.py` | Builds the board in the hotels' evening light (`--preview` renders it lit, before any bake) |
| `board_bake.py` | Bakes the board (ground: orthographic top-down render, 2048 × 1536; 3D parts: 1024² atlas) and exports `board.glb` (`board_ground`, `board_parts`, unlit) |
| `dice.py` | The ivory number die and the oak permit die: rounded cubes painted by a selected-to-active bake, 256² atlases, `die-number.glb` / `die-permit.glb` with `face_*` empties at the face centres |
| `board_review.py` | Renders the exported board with all hotels, close-ups and the dice the way the TV draws them; checks every GLB is unlit and counts triangles |
| `fingerprint.py`, `fingerprint-all.sh` | A hash of every part's name, material and vertices per stage: identical before and after a refactor means the models did not change (no re-bake needed) |

## Run

```
cd crates/mediagram-game/tools/grand-allee-models
blender -b --factory-startup --python-exit-code 1 -P build.py -- --hotel kirschbluete --out /tmp/models
bash check-overlaps.sh /tmp/overlaps          # must print 0 for every hotel
blender -b --factory-startup --python-exit-code 1 -P board_bake.py -- --out /tmp/board   # ~2.5 min
blender -b --factory-startup --python-exit-code 1 -P dice.py -- --out /tmp/board
blender -b /tmp/board/board-baked.blend -P zfight_visible.py   # also die-number-baked, die-permit-baked: must print 0
blender -b --factory-startup --python-exit-code 1 -P board_review.py -- --glbs /tmp/board --out /tmp/board/review
cp /tmp/board/{board,die-number,die-permit}.glb ../../client/public/grand-allee/
bash bake-all.sh /tmp/baked                   # ~50 min on the CPU; GLBs, atlases, TV renders, QA
cp /tmp/baked/*.glb ../../client/public/grand-allee/   # what the game ships
```

## Rules the models keep

- **Owner colour**: parts named `tint_*` only; the export puts them in the
  `owner_tint` mesh of each stage (`stage_<n>_tint`), baked as lit white for
  the TV to multiply by the owner's colour. Everything else is `stage_<n>_body`.
- **Light is baked**: low warm sun from the left front (19° up) and cool sky,
  saved through AgX as sRGB; the GLB materials are unlit
  (`KHR_materials_unlit`), so the TV draws them as they are.
- **No alpha cut-outs** (expensive under MSAA on the TV box): railings,
  fences, fronds and trees are geometry.
- **No visible coplanar overlaps**: nudge one part by a centimetre instead
  (the comments say where and why). The generic asset check
  (`blender-asset-qa`) also flags faces pointing down and parts standing on
  z = 0; for board pieces seen from above those are not faults, which is why
  `zfight_visible.py` exists.
- **Budgets** (TV box, measured): ≤ 6 000 triangles per hotel fully built;
  one atlas per hotel, 1024² for the outer plots and 2048² for the wide park
  plots. The board ≤ 15 000 triangles, a 2048 × 1536 ground atlas and a 1024²
  parts atlas; each die ≤ 1 500 triangles and 256².
- **Own designs**: the hotels are themed on places around the world with our
  own names and architecture; none is a hotel of the classic board game.

Textures: `textures/` (Poly Haven, CC0 1.0; `sources.json`).
