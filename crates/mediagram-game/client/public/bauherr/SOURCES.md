# Sources of the Bauherr board's images

Every image here is made by `crates/mediagram-game/tools/bauherr-bake/bake.mjs` and is derived from photographic
textures from **Poly Haven**, all under **CC0 1.0** (public domain; no attribution required, given here anyway).
The 1k maps the bake reads, and how they were prepared, are in `tools/bauherr-bake/textures/` (`sources.json` lists
the exact files). Diffuse x ambient occlusion were baked into one JPEG per texture; the two roof normal maps
(clay_roof_tiles_02, roof_slates_02) are used as they are, to bake roof relief under the evening sun.

| Texture | Authors | Page | Licence |
|---|---|---|---|
| plastered_wall_04 | Rob Tuytel | [polyhaven.com/a/plastered_wall_04](https://polyhaven.com/a/plastered_wall_04) | CC0 1.0 |
| large_red_bricks | Rob Tuytel | [polyhaven.com/a/large_red_bricks](https://polyhaven.com/a/large_red_bricks) | CC0 1.0 |
| sandstone_blocks_05 | Rob Tuytel | [polyhaven.com/a/sandstone_blocks_05](https://polyhaven.com/a/sandstone_blocks_05) | CC0 1.0 |
| stone_wall | Dario Barresi, Charlotte Baglioni | [polyhaven.com/a/stone_wall](https://polyhaven.com/a/stone_wall) | CC0 1.0 |
| clay_roof_tiles_02 | Amal Kumar | [polyhaven.com/a/clay_roof_tiles_02](https://polyhaven.com/a/clay_roof_tiles_02) | CC0 1.0 |
| roof_slates_02 | Rob Tuytel | [polyhaven.com/a/roof_slates_02](https://polyhaven.com/a/roof_slates_02) | CC0 1.0 |
| weathered_plank_siding | Dimitrios Savva | [polyhaven.com/a/weathered_plank_siding](https://polyhaven.com/a/weathered_plank_siding) | CC0 1.0 |
| wood_floor_deck | Dimitrios Savva | [polyhaven.com/a/wood_floor_deck](https://polyhaven.com/a/wood_floor_deck) | CC0 1.0 |
| cobblestone_square | Rob Tuytel | [polyhaven.com/a/cobblestone_square](https://polyhaven.com/a/cobblestone_square) | CC0 1.0 |
| cobblestone_floor_001 | Rob Tuytel | [polyhaven.com/a/cobblestone_floor_001](https://polyhaven.com/a/cobblestone_floor_001) | CC0 1.0 |
| asphalt_02 | Rob Tuytel | [polyhaven.com/a/asphalt_02](https://polyhaven.com/a/asphalt_02) | CC0 1.0 |
| sparse_grass | Amal Kumar | [polyhaven.com/a/sparse_grass](https://polyhaven.com/a/sparse_grass) | CC0 1.0 |
| forest_leaves_02 | Rob Tuytel | [polyhaven.com/a/forest_leaves_02](https://polyhaven.com/a/forest_leaves_02) | CC0 1.0 |
| gravel_floor | Jenelle van Heerden, Matterfield | [polyhaven.com/a/gravel_floor](https://polyhaven.com/a/gravel_floor) | CC0 1.0 |
| square_tiles | Charlotte Baglioni | [polyhaven.com/a/square_tiles](https://polyhaven.com/a/square_tiles) | CC0 1.0 |

What the files are:

- `ground.webp`, `outer.webp`: the ground print (lawn, road, water, plaza, baked sun shadows) and the land beyond it.
- `hotel-N.webp`: the facade, roof and prop atlas of hotel N (plot order of the rules); `hotel-N-shadow.webp`: its stage shadows.
- `town.webp`, `canopy.webp`: the landscape (trees, mountains, rocks, city blocks, bridge, lamps) and the entrance canopy.
- `die-ivory.webp`, `die-oak.webp`: the faces of the two dice.
- `world.json`, `world.bin`: the geometry (positions, atlas coordinates, baked light) and the index of stages.

The buildings, signs and the landscape are our own designs, drawn for this board.
