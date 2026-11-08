# Grand Allee: a small town on the town side, and the per-stage hotel bake — report

Not committed, no version bump. Follows `blender-board-261108-2122-grand-allee-board-and-dice-report.md`.

## 1. The small town (`board_houses.py`, new)

What's in it:
- **Houses**: 43 houses on the town side — 31 terraced townhouses (eaves 3.6–5.4 m, gable roofs, clay or slate, 4 plaster tints, chimneys on about a third) and 12 single-storey cottages (4.3 m) where a townhouse would reach too far. The tallest is 7.0 m, within the 8 m limit.
- **Market square**: south-east corner, x 36.5..49.5, y −37.5..−28.5. A cobbled square with a fountain (basin, water, column, bowl) and six stalls with striped awnings.
- **Ground decals** (flat, so they hide and shade nothing; no triangles): front gardens before each house, and a lawn promenade with a gravel walk in front of the three south hotels.
- **Trees**: they now avoid the houses; 95 is the cap and about 76 fit.

Placement:
- Houses fill rows from west to east on the town side. A house is kept only if it stays clear of the road (plus pavement), the water, the plots, the Bank and the Rathaus.
- It must also pass two sight rules. What it hides from the 55° TV camera (ground up to 0.7× its height north of it) must not include road, a plot or a building. Its evening shadow (about 2.9× its height, east-north-east) must not land on a plot, the Bank, the Rathaus or a special field.
- That keeps every south hotel's front and the whole road visible. The result: rows in the south-west and south-east corners and cottages along the south edge.
- The pocket between the Bank and Alpenrose stays paved: any house there would shade special fields 36/38 or hide the road.
- Style: our own design in the hotels' family — `materials.facade` plaster with lit shop windows, CC0 clay and slate, the same light baked.

| `board.glb` (installed in `client/public/grand-allee/`) | Triangles | Atlas |
|---|---|---|
| `board_ground` | 714 | 2048 × 1536 |
| `board_parts` (Bank, Rathaus, bridges, trees) | 6 156 | 1024² |
| `board_town` (houses, market, fountain, stalls) | 1 732 | 1024² (new, own atlas) |
| **total** | **8 602** of ≤ 15 000 | file 1 383 912 B (was 1 057 296) |

**Flagged deviation**: the town has its own 1024² atlas. That adds one draw call (3 for the board) and about 5.6 MB of GPU memory with mips. The alternative was squeezing about 4× more surface into the parts atlas, which would have blurred the Bank and Rathaus. A single 2048² parts atlas would be the other option: 1 draw call, but +17 MB.

Checks:
- Visible coplanar overlaps (`zfight_visible.py` on `board-baked.blend`): **0**.
- All materials are unlit (`KHR_materials_unlit`).
- Review renders, with all ten hotels placed from `board_path`, are in `plans/261108-0455-grand-allee/reports/board/`: `board-tv.jpg` (55°), `board-top.jpg`, `board-fields-0-3.jpg`, `board-fields-20-23.jpg`, `board-bridge-21.jpg`, `board-dice.jpg`.

## 2. Hotel bake: each stage baked with only the stages up to it

The bug: `bake.py` baked every part once, with all stages present. An early stage's ground therefore carried the shadow and occlusion of later wings: the black path in front of Riad Atlas at stage 1, and the black patch on Savanne at stages 1–2.

The fix, in `bake.bake_light(objs, res, samples, stages)`:
- It bakes stage by stage into the one shared atlas.
- While stage k bakes, stages > k are taken out of the render (`hide_render`), so they cast no shadow and no occlusion.
- The image is cleared only on the first pass; later passes add their islands.
- A surface a later stage covers is hidden by that stage anyway.
- The board's single bake is unchanged (no stages). `verify_glb.py` gained `--stages n` to render stages 1..n.

Re-baked with `bake-all.sh` (CPU, about 55 min, sharing the CPU with the board bake). All ten GLBs are copied to `client/public/grand-allee/`.

| Hotel | Triangles (unchanged) | GLB before (B) | GLB after (B) | Visible overlaps |
|---|---|---|---|---|
| lagune | 3294 | 376548 | 375552 | 0 |
| kirschbluete | 1272 | 237932 | 240332 | 0 |
| fjordhus | 2828 | 362712 | 367256 | 0 |
| hacienda_sol | 1856 | 327688 | 326532 | 0 |
| riad_atlas | 3358 | 476500 | 477932 | 0 |
| alpenrose | 2388 | 345196 | 352896 | 0 |
| maharadscha | 3976 | 676788 | 678288 | 0 |
| skyline | 1136 | 343232 | 333648 | 0 |
| savanne | 4112 | 662352 | 684748 | 0 |
| belle_epoque | 3440 | 550492 | 550152 | 0 |

Before/after, stage 1 and 2, rendered from the GLBs as the TV draws them (`verify_glb.py --stages n`), in `plans/261108-0455-grand-allee/reports/hotels-stage-bake/`:
- `riad_atlas-stage-1-before-after.jpg`: the black path and the black west wall are gone, and the roof canopy that was baked black under the later storeys is back.
- `riad_atlas-stage-2-before-after.jpg`
- `savanne-stage-1-before-after.jpg`, `savanne-stage-2-before-after.jpg`: the black patches on the ground are gone.

The generic asset check (`blender-asset-qa`) still reports its usual errors for board pieces (faces pointing down, parts on z = 0). As the README says, those are not faults for a board seen from above.

## Open questions

1. **Town atlas**: a separate 1024² town atlas (+1 draw call, +5.6 MB) or one 2048² parts atlas (+17 MB, no extra draw)? I chose the separate atlas.
2. **Town look**: the plaster tints (cream, ochre, apricot, pale stone) and the stall canvases (red, green, blue) are my picks — the art director should confirm them.
3. **Bank–Alpenrose pocket**: it stays paving. Houses there would shade special fields 36/38 or hide the road. Trees there are limited by the same rule.
