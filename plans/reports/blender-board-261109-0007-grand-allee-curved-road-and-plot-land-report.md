# Grand Allee board: curved road and plot land — report

The board is re-baked from `board_path.layout()`'s new `outline` (fields) and `land` (plots). `board_path.py` and `board_land.py` were not touched. Nothing is committed, versions are unchanged, and the new `board.glb` was written **only to scratch** (not installed):
`/tmp/claude-1000/-home-andre-Workspace-spieltisch/e6a9c23c-c11a-465a-9550-b0c68a91b0e0/scratchpad/curve2/board.glb`

## What changed

- **Fields** (`board_ground.road`): joints, setts and inlays are drawn from `f["outline"]`. The seams (0.1 m inset each side) and the inlay inset (another 0.4 m) work on the curved outlines through the mitred `inset()`. Icons and the two names stay centred on the field's centre, upright for the TV.
- **Kerbs**: they follow the curved edges at 6 points a field, from the outlines (`Layout.outer/inner`, sliced by field with the new `Layout.edge(line, a, b)`). They stop at fields 1 and 21, where the bridges' parapets and railings now follow the curved edges too.
- **Plot land**: each plot's land polygon is its striped lawn, reaching from the kerb (0.3 m off the road's edge, so 5 cm off the kerb) back past the hotel.
  - The stone edging runs only where the land does not meet the road; the kerb is the land's edge along the frontage.
  - Neighbouring lands share their straight dividing line, so each side carries its own edging.
  - The hotels' ground plates sit inside the land.
- **Keeping off the land**:
  - Trees, houses and park paths now test against the land polygons (`Layout.in_land`, with margins).
  - The shadow and TV-sight rules still use the hotels' footprints, since that is what a hotel covers.
  - Park paths are now cut where they reach land; pieces under 8 m are dropped (they read as debris). What remains is mainly two riverside promenades. The pond's arcs and spokes fall inside the park lands.
  - The town's market square looks for the first clear spot in the south-east corner, because Hacienda Sol's land now reaches its old place.
  - The gardens between the town hotels are gone; the lands cover that ground now.
- **Review**: `board_review.py` takes `--hotels <dir>`, so it no longer reads `client/public` while that is being converted to KTX2. It also adds the close-up `plots-meet-11-12`. `board_layout.place_hotels` takes the folder.

## Numbers

| Mesh | Triangles | Atlas |
|---|---|---|
| `board_ground` (quad, curved kerbs, land edging) | 3 094 (was 714) | 2048 × 1536 |
| `board_parts` (Bank, Rathaus, curved bridges, trees) | 5 804 | 1024² |
| `board_town` | 1 700 | 1024² |
| **total** | **10 598** of ≤ 15 000 | GLB 1 478 400 B (was 1 383 912) |

- The extra ground triangles are the kerbs, now at 1 m steps along the curves.
- Visible coplanar overlaps (`zfight_visible.py` on `board-baked.blend`): **0**.
- Every material is unlit (`KHR_materials_unlit`).
- The dice are unchanged.

## Review renders

`plans/261108-0455-grand-allee/reports/board-curved/`:
- `board-tv.jpg`: TV view, 55°, all ten hotels (from the re-baked scratch GLBs).
- `board-top.jpg`
- `board-plots-meet-11-12.jpg`: curved waist; Maharadscha's and Skyline's lands meet at the field 11/12 line, and Kirschblüte's land runs along fields 9–13.
- `board-fields-0-3.jpg`, `board-fields-20-23.jpg`, `board-bridge-21.jpg`, `board-dice.jpg`

## Notes and open points

1. **The lands now cover most of the Kurpark and the middle of the town side.** The town keeps to the south-west/south-east corners and the south edge, and the Kurpark has almost no free ground for trees or paths. If the park should breathe more, a smaller `margin` or `reach` in `board_land.plot_land` would do it. I did not change those files.
2. **To install**: copy the scratch `board.glb` above to `client/public/grand-allee/` once the KTX2 agent is done.
