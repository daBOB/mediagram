# Bauherr town, realistic look test round 2 on the TV box (2026-11-07)

Two new live mockups of the Bauherr TV board in a realistic style, **D Abendsonne** (golden hour) and **E Blaue
Stunde** (after sunset). They answer the user's verdict on round 1: "we need a realistic style. B is one but its to
playful and it need a bit of C but with realistic textures. we need to push here the tv box to the limit." Nothing
here ships. The user picks.

## Result

| Page | Buffer | fps | 1 % low | frames > 20 ms | first frame (from navigation) | draw calls | triangles | texture memory (est.) | CPU ms/frame |
|---|---|---|---|---|---|---|---|---|---|
| D Abendsonne | 1080p | **59.6** | **53.5** | 4 | 17.7 s | 53–58 | 92.5k | 169 MB | 6.2 |
| E Blaue Stunde | 1080p | **59.6** | **53.2** | 3 | 15.2 s | 53–58 | 101k | 168 MB | 6.6 |

- Both pages hold the box at a steady 60 fps, far above the ~32 fps floor.
- **960×540** made no meaningful difference: D 59.9 fps / 59.5, E 59.5 / 51.5. It is not worth the loss of sharpness,
  so keep pixel ratio 2.
- **Headroom.** Drawing the whole town twice a frame drops it to 45.5 / 44.4 fps, with a 1 % low of 27. One town pass
  therefore costs about 11 ms of the 16.7 ms frame. That leaves about 5 ms before the cadence breaks into alternating
  16 / 33 ms frames.
- Box screenshots (1080p, taken mid-run):
  - `.superpowers/brainstorm/261107-bauherr-look/content/box/shots/d-abendsonne-d12.png`
  - `.superpowers/brainstorm/261107-bauherr-look/content/box/shots/e-blaue-stunde-e14.png`
- Desktop renders of the same pages, including a stage rising inside its scaffold, are in `content/box/desktop/`.
- The texture memory figure is computed (w × h × 4 × 1.33 for mipmaps, uncompressed). The WebView renderer is a
  separate process, and its GL memory is not in the app's `dumpsys meminfo` (app PSS 114 MB).

## The two looks (neutral)

- **D Abendsonne.** The same town at golden hour.
  - A low warm sun from the left, about 19° up. Walls facing it glow cream-gold; the fronts are softer and warm; the
    far sides are blue-grey.
  - Long soft shadows stretch to the right across lawns, streets, the square and the fields. Shadows are cool, the
    sunlit ground warm.
  - A gold-to-blue sky band over the skyline, with a few faint sunlit clouds, and warm haze on the far houses.
  - About one window in eight is lit; the lanterns are on, faintly.
- **E Blaue Stunde.** The same town after sunset.
  - Deep blue light everywhere, no direct sun.
  - A pink-orange band on the horizon under dark clouds.
  - About 60 % of the windows are lit, each with a soft glow.
  - Lamps and the entrance canopies throw warm pools on the streets and the square, and light falls from the hotel
    ground floors onto their forecourts.
  - The rooftop signs glow in their owners' colours, and the lighthouse lantern is lit.
  - Closest to C.

Both share everything else:

- **Board layout.** B's layout: the loop of 36 fields, 8 plots, the town hall square with its fountain.
- **The board as a real place.** The board is the town centre:
  - The loop is an asphalt street with kerbs and pavements, and the fields are squares of granite setts set into it.
    Special fields are tinted stone with an inlaid sign.
  - The parks are lawn and the square is cobbled.
  - Around it lies a ring street with parked cars, two rows of town houses with chimneys and dormers, a church with a
    copper spire, street trees and lamps.
  - A cobbled square in front holds benches, trees and the dice.
- **Six hotels, each with its own architecture.** All are built in stages: main building, two annexes, a leisure
  facility.
  - Seeblick: white lake hotel with dark-wood balconies on every floor, slate hip roof, pool with a timber deck.
  - Stadtpalais: Gründerzeit palace. Ochre render over a rusticated sandstone ground floor, pediments, a centre
    risalit with balcony, slate mansard with dormers, and a box-hedge parterre.
  - Kurhaus: cream spa house with a portico of six columns under a pediment, a copper cupola and a thermal pool.
  - Leuchtturm: red-brick maritime hotel with a striped lighthouse tower and a lit lantern, steep clay gable, pool.
  - Kastanienhof: half-timbered inn with a stone ground floor, steep clay roof with dormers, and a beer garden under
    chestnut trees.
  - Waldhaus: alpine chalet with a rubble-stone ground floor, larch boarding, green shutters, a wide front gable and
    flower balconies, plus a garden with firs.
- **Plots for sale.** Alpenrose and Sternwarte are raw gravel lots with a surveyor's string and an estate sign.
- **Owner colour as real things.**
  - A painted property line round each plot.
  - Striped awnings over the ground-floor windows.
  - Flags.
  - The hotel name sign in the owner's colour: lit, on the roof, or on the frieze or facade.
  - Striped entrance canopies on the path, lit in E.
- **A building rising.** On its owner's turn an annex is built over 2.8 s:
  - A scaffold goes up first: standards, ledgers, planked decks and bracing.
  - The walls then rise from the ground inside it, cut by a clipping plane, and the scaffold is taken down from the
    top.
  - A pool or garden is laid out from left to right.
  - A tower crane slews slowly over the Stadtpalais.
  - Its shadow fades in as it rises.
- **Small figures.** People as in an architect's model, 1.75 m to scale, stand on the town square and the square in
  front, with their own long shadows in D.
- **Dice.** An ivory number die with drilled pips and an oiled-oak permit die with burnt-in signs. They are the only
  run-time-lit objects besides the figure bases. In E a warm lamp lights them.
- **Pawns.** 2D sprites, as in round 1.
- **Materials.** Everything is matte. There is no specular term anywhere, and water is a still, matte tiled basin.

Nothing comes from the board game *Hotel* (MB/Hasbro): the architectures, names, signs and board are our own.

## How it is built, and what each choice costs on the box

- **One draw call per stage.** Every hotel has its own 2048×1024 canvas atlas at 256 px per unit. Every visible face
  gets its own painted rect: plaster, brick, stone, timber and roof tiles from the photos, windows floor by floor, and
  signs. Trim, cornices, scaffolding and the crane use flat-colour swatches in the same atlas. As a result each stage
  of a hotel is one mesh with one material, and the whole scene is about 55 draw calls (round 1 needed about 190 for
  a much simpler town).
- **Light baked into vertex colours.** A small light model (sun, sky dome, ground bounce, soft highlight roll-off,
  contact darkening at the foot of walls) runs on the CPU and is stored in vertex colours. The town is drawn unlit
  (MeshBasic).
- **Self-lit texels.** Lit windows, signs, lanterns and light pools are marked in the atlas alpha, and a one-line
  shader change shows those texels self-lit.
- **Shadows baked into the 4096×2066 board print** and a 2048 outer-ground print.
  - Every static building, tree, lamp, car and figure is projected along the sun.
  - The projections are softened by down- and up-scaling, not blur filters.
  - The prints also carry contact shadows under every footprint.
  - Annexes that rise get their own shadow quad, blended as dst × (1 − src) and faded in.
  - There is no shadow map.
- **Roof relief from the Poly Haven normal maps, baked.** For each roof material and orientation, a 256 px tile is lit
  texel by texel through the normal map under the look's sun, relative to the flat roof. This costs no GPU time and
  gives the tile rows their raking-light relief in D.
- **Halos.** Each lit window, sign, lamp, canopy and the lantern gets a soft additive disc: about 1,600 discs in D
  and 6,300 in E. All of them are merged into one mesh, as fake bloom.
- **Kept from round 1.** Every program is compiled and every texture uploaded at load; the HUD is pre-drawn; there
  is no HTML over the canvas; MSAA is on at pixel ratio 2.

### Steps on the box (1080p, 5 s warm-up, 20 s measured, clock from frame 10)

| Step | Change | D fps / 1 % low | E fps / 1 % low | First frame | Read |
|---|---|---|---|---|---|
| 1 | First build: hotels, baked light, shadows, skyline | 59.6 / 53.8 | 59.7 / 53.8 | 21.3 s | 58 calls, 72k tris, CPU 7.0 ms. Far above the floor; the 19 s load is the problem. |
| 2 | Load marks inside the build | 59.7 / 54.6 | – | 21.4 s | Board print 6.8 s, texture upload 4.4 s. |
| 3 | Tinted surfaces composed once into 512 px tiles, laid as plain patterns (no full-canvas `overlay`, `soft-light` or `multiply`) | 59.7 / 54.1 | – | 16.0 s | Board print 6.8 → 1.9 s, upload 4.4 → 0.6 s. |
| 3b | Same, with CPU-backed canvases (`willReadFrequently`) | 59.8 / 59.5 | – | 20.5 s | Slower overall. Dropped. |
| 4 | Surroundings reworked (cars, varied skyline, front square), clouds, **real bloom** (quarter-size glow pass, 4 blur passes, additive composite, 8-bit targets) | **41.7 / 26.1** | **42.0 / 26.1** | 15.1 s | Fails the floor's 1 % low. Same build without bloom: 59.6 / 53.5 and 59.6 / 53.8. |
| 5 | Bloom passes moved before the main pass | 36.8 / 27.3 | 37.0 / 27.5 | – | Worse. |
| 6 | Bloom split: only the blurs and composite / only the glow scene pass | – | 39.0 / 27.2 and 45.5 / 26.7 | – | Any render-target pass costs 14–20 fps here. Real bloom dropped (kept behind `?bloom=1`). |
| 7 | Halo discs at lit windows instead of bloom | 59.7 / 59.5 | 59.6 / 54.1 | 14.8 s | The glow is free. |
| 8 | Figures, roof relief (first version read the whole atlas back) | 59.6 / 52.9 | 59.4 / 52.6 | 20.4 s | 960×540: 59.9 / 59.5 and 59.5 / 51.5. Readback costs about 1 s per atlas. |
| 9 | Relief baked into small cached tiles instead | 59.8 / 54.1 | 59.5 / 54.3 | 22.2 s | Per-texel light-model calls cost about 3 s of JS. |
| **10** | **Relief reduced to the sun term with a sqrt gamma; none on the distant houses** | **59.6 / 53.5** | **59.6 / 53.2** | **17.7 / 15.2 s** | Final. |
| 11 | Headroom: the town drawn twice a frame (`?repeat=2`) | 45.5 / 27.1 | 44.4 / 26.5 | – | One town pass ≈ 11 ms of GPU. |

Load of the final D, measured from page start (A55 CPU, plus the GPU catching up on canvas raster):

- Photos: 0.8 s.
- Six hotels, including relief tiles: 4.1 s.
- Surrounding town: 2.5 s.
- Board print: 2.4 s.
- Uploads: 0.8 s.
- HUD plus GPU catch-up: 4.3 s.
- First frame: 15.9 s.

The game would bake these prints and atlases offline and ship them as images (see below); the box would then only
decode and upload.

### New box lessons (WebView 156, Mali-G57)

- **Keep the frame in one render pass.** Any render-target pass inside a frame costs 14–23 fps here, even small,
  quarter-size 8-bit ones. Reordering them makes it worse. Do glow and bloom with geometry: additive discs.
- **No blend modes over big canvases.** Compose tinted surfaces once into small tiles, then lay them as plain
  patterns. This is the canvas counterpart of round 1's "grain as a tile" rule.
- **Do not read a big canvas back** (`getImageData`) to post-process it: about 1 s per 2048 atlas. Post-process the
  small tile instead.
- **Custom blends must keep destination alpha.** A darkening blend with `OneMinusSrcColor` also zeroes the
  framebuffer alpha, and the page background (#222) then shows through (+34 levels; it looked like light boxes under
  the dice). Set `blendSrcAlpha: Zero` and `blendDstAlpha: One`.
- **Spending the last ~5 ms is a trade.** Past 16.7 ms the cadence drops to alternating 16 / 33 ms frames: about
  45 fps with a 1 % low of about 27. That is legal under the floor, but the dice and pawn moves judder. Both pages
  stay at 60 instead, and the margin is left for the real game's markers, sound and network.

## Textures: sources, licences, size

All textures are **Poly Haven, CC0 1.0** (public domain, no attribution required). They were downloaded at 1k, then
diffuse × AO (AO lifted to 35–100 %) was baked into one 1024 px JPEG at quality 82. The two roof normal maps are
used as they are.

| Asset | Authors | Licence | File in `content/tex/` |
|---|---|---|---|
| plastered_wall_04 | Rob Tuytel | CC0 1.0 | 21 KB |
| large_red_bricks | Rob Tuytel | CC0 1.0 | 227 KB |
| sandstone_blocks_05 | Rob Tuytel | CC0 1.0 | 283 KB |
| stone_wall | Dario Barresi, Charlotte Baglioni | CC0 1.0 | 220 KB |
| clay_roof_tiles_02 | Amal Kumar | CC0 1.0 | 203 KB + normal 303 KB |
| roof_slates_02 | Rob Tuytel | CC0 1.0 | 235 KB + normal 439 KB |
| weathered_plank_siding | Dimitrios Savva | CC0 1.0 | 111 KB |
| wood_floor_deck | Dimitrios Savva | CC0 1.0 | 203 KB |
| cobblestone_square | Rob Tuytel | CC0 1.0 | 262 KB |
| cobblestone_floor_001 | Rob Tuytel | CC0 1.0 | 254 KB |
| asphalt_02 | Rob Tuytel | CC0 1.0 | 335 KB |
| sparse_grass | Amal Kumar | CC0 1.0 | 236 KB |
| forest_leaves_02 | Rob Tuytel | CC0 1.0 | 400 KB |
| gravel_floor | Jenelle van Heerden, Matterfield | CC0 1.0 | 445 KB |
| square_tiles | Charlotte Baglioni | CC0 1.0 | 319 KB |

Pages and authors are listed in `content/tex/sources.json`.

**APK size.** There are two ways to ship this:

| Shipping | Adds to the APK | Box load |
|---|---|---|
| The 17 JPEGs, with the town painted on the box as the mockups do | **4.4 MB** (4,603,798 bytes) | 15–18 s |
| The painted results baked offline as WebP (q 0.85, alpha kept for the self-lit texels) | **~4.5 MB (D) / ~4.9 MB (E)** per look | Decode and upload only. Estimated a few seconds; not measured. |

The baked sizes are measured from the live pages:

- Board print: 1.3 MB (D) / 1.7 MB (E).
- Town atlas: 0.9 MB.
- Six hotel atlases: 0.29–0.42 MB each.
- Houses atlas: 0.2 MB.

Lit windows differ per look, so one look ships one set.

## Calls I made (not decided by anyone)

- **Composition.** Both looks use C's camera and C's sky band behind the skyline, so they differ only in light. E is
  "closest to C"; giving D a different camera would have mixed composition into the choice of light. The board is
  the town centre in a living town. It is not a board on a table.
- **Dice on the cobbled square in front, not on a table.** A table in front of an outdoor town at sunset read wrong.
  The ring street is narrow in front, so the dice land on cobbles. See question 1.
- **Owner colour.** Owner colour is carried by real things only. No coloured roofs, which is what made B readable
  but would not be realistic. The property line is the strongest at a glance.
- **Six architectures, not eight.** The two plots for sale stay empty lots, as the scene requires. No buildings were
  designed for them.
- **No real bloom.** It did not fit (steps 4–6); window halos replace it.
- **People and cars.** Added for scale and life. They are tiny on the TV.

## Showing it on the TV

```
.superpowers/brainstorm/261107-bauherr-look/content/box/show-bauherr-look-on-box.sh 192.168.0.37:5555
```

- With no page names it shows D and E full screen; add page names, or `all`, for A, B, C, D and E.
- Each look takes about 15 s to come up; press Enter for the next one.
- At the end it uninstalls the test app, removes the reverse port and goes to HOME.
- Only its syntax and the page loads were checked; it was not run end to end on the box.

On the desktop, any page opens with `?show=1` (three.js from the CDN).

The runner now serves three.js r170 from `content/three-r170/` and uses `content/box/webview-spike.apk`, both copied
from the 11-05 scratchpad, so the look test no longer depends on that session's `/tmp`. Measurement switches:
`?bloom=1`, `?bloomx=noscene|nopost`, `?cpu`, `?repeat=2`.

## Box state

The test app is uninstalled, there is no reverse port, and the launcher is on HOME (checked after the last run). No
setting was touched.

## Files

- Pages, in `.superpowers/brainstorm/261107-bauherr-look/content/` (gitignored): `d-abendsonne.html`,
  `e-blaue-stunde.html`, `look-abendsonne.js`, `look-blaue-stunde.js`.
- Builder:

  | File | What it holds |
  |---|---|
  | `real-light.js` | Light model |
  | `real-atlas.js` | Atlas and part geometry, town material, window halos |
  | `real-paint.js` | Photo tiles, facades, windows, roofs and roof relief, signs |
  | `real-props.js` | Roofs, trim, balconies, awnings, flags, scaffold, crane, trees, lamps, figures |
  | `real-hotels.js` | The six hotels and the town hall |
  | `real-ground.js` | Board print and shadow projection |
  | `real-world.js` | Houses, cars, outer ground, sky card |
  | `real-town.js` | Scene, stages, dice, figures |
  | `real-glow.js` | Real bloom, opt-in only |

- Shared with round 1: `town-run.js` gained a few optional hooks (async build, custom stage animation,
  `pre`/`post`/`update`, `?repeat`). A, B and C are unchanged and were re-checked on the desktop.
- Textures: `content/tex/`.
- Logs: `content/box/box-r2-step*.log` and `run-before-*.log`. Memory dumps: `mem-*.txt`.

## Unresolved questions

1. Should the dice roll on a table rather than on the town's square? The table would bring back B's board-on-a-table
   framing, but it does not fit an outdoor sky band.
2. If D or E is chosen, phase 05 should bake the prints and atlases offline (about 4.5–5 MB WebP per look) and
   measure the load on the box. The mockups' 15–18 s comes from painting on the box.
3. As in round 1: the flatter C camera both looks use should be checked for field readability with the real move
   markers.
4. Should the empty plots (Alpenrose, Sternwarte) get their own architecture now, or when a game first builds there?
