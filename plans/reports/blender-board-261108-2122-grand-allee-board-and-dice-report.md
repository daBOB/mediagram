# Grand Allee board and dice (Blender 5.2, sub-phase 4b) — report

Status: DONE_WITH_CONCERNS (items 1–5 built and checked; item 6, the hotel shadow decals, skipped, with the approach below).
Nothing committed, no version bump, hotel builders and Rust/TS game code untouched.

## Built

- **Ground** (`board_ground.py`): grass terrain with the river, pond and beach sunk below a water plane (sloping stone/earth banks), sea deepening northwards, town-side paving (forecourt for the Bank, a square for the Rathaus), two gardens between the town hotels and a lawn strip along the south edge, mown-stripe lawns on the ten plots, gravel paths in the Kurpark (arcs round the pond, spokes to the waist, riverside promenades where plots leave room).
- **Road**: 40 fields from `board_path` corners. Each field has a dark joint band (0.2 m seam), granite setts turned with the road, and on the special fields a coloured inlay with a cream icon and its shadow, upright for the TV. "Bank" and "Rathaus" are lettered on fields 0 and 20. Kerbs run on both edges, and fields 1 and 21 are bridges: stone parapet, iron railing, end pillars.
- **Icons**: Bauherr's own icon language (coin with a T, pediment, staked plot, crane, door with a star), plus a gift for Gratis bauen. The tints are Bauherr's (B #d9ad55, R #8d9fbf, P #8bb174, S #d9824a, E #b48ac0). G is new and keeps `board_layout`'s #4ea8de.
- **Bank** at (-51, -6.5), beside field 0, outside the loop and south of the river: plinth, two lit storeys, slate hip roof, a four-column portico with pediment, "BANK" on the frieze.
- **Rathaus** at (48.5, 13.5), beside fields 19/20, north of the river: clay roof, clock tower with a slate spire, balcony, "RATHAUS".
- Both buildings are our own designs, built at hotel size and shown at 70 %.
- **Plots**: 0.2 m stone edging, 2 cm outside each footprint.
- **Trees**: 70 trees (round crowns, poplars on the town side). They avoid the road, water, plots, paths and buildings, and are placed so that neither their shadow nor their crown (as the TV sees it) lands on a plot, building or special field.
- **Dice** (`dice.py`): an ivory number die and an oak permit die, rounded cubes (1.2 m, 0.11 rounding, as Bauherr's). Faces are painted by a selected-to-active bake from flat decals: Bauherr's pips, and tick/cross/gift/×2.

## Pipeline and deviations (flagged)

1. **Ground atlas is an orthographic top-down Cycles render, not `bpy.ops.object.bake`.**
   - The 3D parts are hidden from that camera but cast their shadows into it.
   - On matte surfaces this gives the same result as a top-down bake, and the render's denoiser and anti-aliasing keep setts, icons and letters clean.
   - All decals exist only in the bake scene, so the GLB ground is one flat quad and has no coplanar layers.
2. **Kerbs and plot edging sample the ground atlas, not the 3D atlas.**
   - UVs are board (x, y); the walls sample the kerb's centre line.
   - Reason: 480 m of 0.25 m strips would ruin the 1024² parts atlas, and the ground and kerbs then share one draw call.
3. **Dice are lit by an even sky with no sun, and saved through the Standard view, not AgX.**
   - A tumbling die with a baked sun would show a face lit from below once it lands.
   - The colours therefore match Bauherr's painted dice.
4. **River depth is painted on a flat ground.** In a lean-in there is no parallax.

## Numbers

| Asset | Triangles | Atlas | File |
|---|---|---|---|
| `board.glb` → `board_ground` (quad + kerbs + edging) | 714 | 2048 × 1536 (6.25 cm/px) | 1 057 296 B |
| `board.glb` → `board_parts` (Bank, Rathaus, bridges, 70 trees) | 5 762 | 1024² | (same file) |
| **board total** | **6 476** of ≤ 15 000 | | |
| `die-number.glb` | 188 of ≤ 1 500 | 256² | 19 184 B |
| `die-permit.glb` | 188 of ≤ 1 500 | 256² | 20 084 B |

- Breakdown of the parts: trees 4 680, bridges 624, Rathaus ~370, Bank ~280.
- Draw calls: 2 for the board and 1 per die.
- Timing: the bake takes about 2 min 15 s at 64 samples on 16 CPU threads; the dice take about 20 s.

## Checks

- **Visible coplanar overlaps** (`zfight_visible.py` on `board-baked.blend`, `die-number-baked.blend`, `die-permit-baked.blend`): **0 / 0 / 0**.
- **Unlit**: every material in all three GLBs carries `KHR_materials_unlit`, read from the GLB JSON by `board_review.py`. The review renders import the GLBs and draw them unlit, as `verify_glb.py` does.
- **Field kinds**: `board_kit.Layout` asserts the kind string equals `KINDS` in `grand_allee/board.rs`.
- **Hotel builder** still builds after `build.light()` was extracted (Kirschblüte: 1 272 tris). Only lighting code moved; the fingerprint hashes geometry only.

## Dice faces (for the TV)

- Empties sit at each face centre and are children of the die mesh.
  - `die-number.glb`: `face_1` … `face_6`.
  - `die-permit.glb`: `face_approved_1`, `face_approved_2`, `face_approved_3`, `face_rejected`, `face_free`, `face_double` (the `GrandAlleePermit` variants; three faces approve, so they carry the die value).
- Box faces are the same as Bauherr's `NUMBER_FACES`: value 1 on glTF +x, 6 −x, 2 +y, 5 −y, 3 +z, 4 −z.
- The permit die shows `permit_for(value)` on the same face.
- Each drawing is upright for the TV once Bauherr's `FACE_UP` turn has brought its face to the top, so 4c can reuse that code.

## Review renders

`plans/261108-0455-grand-allee/reports/board/`:
- `board-tv.jpg`: TV view at 55°, all ten hotels fully built, placed from `board_path`.
- `board-top.jpg`
- `board-fields-0-3.jpg`, which includes the bridge at field 1.
- `board-fields-20-23.jpg`
- `board-bridge-21.jpg`
- `board-dice.jpg`

## Files

New, in `crates/mediagram-game/tools/grand-allee-models/`:
- `board_kit.py`, `board_ink.py`, `board_ground.py`, `board_town.py`, `board_trees.py`
- `board.py`, `board_bake.py`, `board_review.py`, `dice.py`

Small edits to shared tools:
- `build.py`: `light()` extracted from `scene_setup`.
- `bake.py`: `__main__` guard, so it can be imported.
- `board_layout.py`: `__main__` guard and a shared `place_hotels`.
- `zfight_visible.py`: also checks `board_*` / `die_*` meshes.
- `README.md`: table rows, run commands, budgets.
- `client/public/grand-allee/SOURCES.md`: now names the board and dice.

Output: `client/public/grand-allee/board.glb`, `die-number.glb`, `die-permit.glb`.

## Item 6 (skipped): hotel shadow decals per hotel and stage

How I would build them:
- For each hotel and stage, place the hotel's built stages 1..n on a shadow-catcher plane, in the same light, at 0.7.
- Render orthographically top-down over the plot plus the shadow's reach, about 3× the height to the ENE, into a small greyscale "darkness" map (about 256–512 px).
- Ship one quad per stage, drawn with a multiply blend, as Bauherr's `shadowMaterial` does: no alpha test, so it is MSAA-safe. Lift the quad 1–2 cm above the board, or use `polygonOffset`.
- Pack all stages of a hotel into one atlas.
- Cost: one extra draw per built stage. Expect roughly 10 × 5 small textures, or 10 atlases.

## Open questions

1. **Palette (art-director)**: G #4ea8de against R #8d9fbf. Both are blues. Rathaus has its name lettered, but please confirm or pick another tint for Gratis bauen.
2. **Town paving** reads as a large plain beige plaza when the town hotels are not built. More gardens, or a second paving pattern?
3. **Kurpark trees**: only 4 fit inside the loop with the shadow/crown rules, because the park plots fill the lobes. Relax the rules (allow shadows on Allee fields near plots), or accept a mostly open park?
4. **Lettering size**: "Rathaus" fits a 4 m road at about 0.7 m letter height. That reads in a lean-in but is marginal (~7 px) in the whole-board view.
5. **Depth precision (for 4c)**: hotels' ground pieces sit about 2 cm (at 70 %) above the board. Use a camera near plane ≥ 1 m (ideally 5 m) so they don't flicker at 130–170 m.
6. **Bank placement**: the Bank stands about 7 m west of field 0. Its evening shadow falls on its own forecourt, not on field 0. The only shadow-free spot that also clears the river is 21 m away, north of the river.
