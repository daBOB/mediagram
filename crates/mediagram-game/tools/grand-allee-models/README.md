# Grand Allee hotel models

The ten Grand Allee hotels as Blender 5.2 builder scripts. Every model is made
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
| `fingerprint.py`, `fingerprint-all.sh` | A hash of every part's name, material and vertices per stage: identical before and after a refactor means the models did not change (no re-bake needed) |

## Run

```
cd crates/mediagram-game/tools/grand-allee-models
blender -b --factory-startup --python-exit-code 1 -P build.py -- --hotel kirschbluete --out /tmp/models
bash check-overlaps.sh /tmp/overlaps          # must print 0 for every hotel
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
  plots.
- **Own designs**: the hotels are themed on places around the world with our
  own names and architecture; none is a hotel of the classic board game.

Textures: `textures/` (Poly Haven, CC0 1.0; `sources.json`).
