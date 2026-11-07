# Bauherr town, look test on the TV box (2026-11-07)

Three live mockups of the Bauherr TV board: A Modellstadt, B Holzbaukasten, C Abendstadt
(phase `plans/261107-0207-card-games-and-bauherr/phase-04-bauherr-look-test.md`). Nothing here ships.
The user picks one look on the TV.

## Setup

- Box `192.168.0.37:5555`: UHD Google TV STB, Mali-G57, 4×A55, 1920×1080 at density 320. The system WebView is
  **156.0.8078.47**. The brief said 158, but the box reports 156.
- Runs use the spike WebView APK from the 11-05 round. The page is served over `adb reverse`, one page load per step.
  Each step warms up for 5 s and then measures for 20 s. The clock starts at frame 10, and MSAA is on. The display is
  woken before every step. At the end the APK is uninstalled, the reverse is removed and the box goes to HOME. No
  setting was touched.
- **1080p** means the 960×540 CSS page drawn at pixel ratio 2, a 1920×1080 buffer. Heimlauf and the ceremony render
  the same way. **960×540** means the same page at pixel ratio 1, a 960×540 buffer. The HUD scales with the buffer.
- The scene is the same in every look:
  - 36 fields on a rounded loop round a town hall square: Bank, Rathaus, 2 Freier Eingang, a Bauplatz or Baustelle at
    each hotel, the rest plain.
  - 8 plots with their names from phase 03. Alpenrose and Sternwarte are for sale (sign with price). Seeblick,
    Stadtpalais, Kastanienhof and Leuchtturm rise one stage (main, annex, annex, pool/garden) on their owner's turn,
    then drop back to the main building. Kurhaus (pool) and Waldhaus (garden) stay finished.
  - 8 owner-coloured entrance canopies on fields.
  - 4 avatar figure sprites: Heimlauf's `*-figure.webp`, no 3D baking.
  - Number die plus permit die. The permit die has tick ×3, cross, gift (free) and ×2. The dice roll in from opposite
    sides on the table in front of the board, and the drawn face lands upright for the viewer.
  - Pre-drawn tags (head, name, Taler, hotel count), turn banner, and "Runde n / 24".
- Nothing comes from MB/Hasbro *Hotel*: no facades, cardboard pieces, hotel names or board layout. The entrance
  canopies and the permit symbols are our own.

## Measurements

The final version is round 2. Round 1 was the first build of the same pictures.

| Page | Buffer | fps | 1 % low | frames > 20 ms | first frame (from navigation) | draw calls | tris | CPU ms/frame | box shot |
|---|---|---|---|---|---|---|---|---|---|
| A Modellstadt | 1080p | **59.9** | **59.5** | 2 | 4.0 s | 181–195 | 16k | 9.9 | `box/a-modellstadt-a3.png` |
| A Modellstadt | 960×540 | 59.9 | 59.5 | 1 | 2.5 s | 181–195 | 16k | 9.6 | `box/a-modellstadt-a4.png` |
| B Holzbaukasten | 1080p | **59.8** | **54.1** | 2 | 3.4 s | 190–205 | 29k | 10.3 | `box/b-holzbaukasten-b3.png` |
| B Holzbaukasten | 960×540 | 59.9 | 59.5 | 1 | 2.9 s | 190–205 | 29k | 10.1 | `box/b-holzbaukasten-b4.png` |
| C Abendstadt | 1080p | **59.8** | **54.1** | 2 | 3.4 s | 190–203 | 21k | 10.5 | `box/c-abendstadt-c3.png` |
| C Abendstadt | 960×540 | 59.9 | 59.5 | 2 | 2.6 s | 190–203 | 21k | 10.3 | `box/c-abendstadt-c4.png` |

The `box/` paths are under `.superpowers/brainstorm/261107-bauherr-look/content/`. Draw calls rise during a run as
stages are built.

### Round 1, the first build

| Page | Buffer | fps | 1 % low | frames > 20 ms | first frame | draw calls (desktop count) | CPU ms/frame |
|---|---|---|---|---|---|---|---|
| A | 1080p / 960×540 | 59.5 / 59.6 | 52.1 / 52.9 | 4 / 2 | 4.3 / 2.9 s | 263 | 10.5 / 11.0 |
| B | 1080p / 960×540 | 58.6 / 58.9 | 50.3 / 51.0 | 10 / 7 | 3.8 / 3.3 s | 285 | 11.8 / 11.4 |
| C | 1080p / 960×540 | 57.0 / 57.0 | **39.7 / 45.9** | **34 / 26** | 3.8 / 3.0 s | **397** | 13.7 / 13.4 |

Load on the box, round 2 at 1080p, measured from script start:

- Images and fonts: 0.1 s.
- Scene built, including the 2048 board print: A 0.57 s, B 0.76 s, C 0.55 s.
- HUD pre-draw: +0.3 s.
- `renderer.compile`: done at about 1.0–1.2 s.
- First frame: 2.6–3.0 s. This includes the first texture uploads.

## What it means

- **The GPU is not the limit.** 1080p and 960×540 give the same fps. The frame time comes from the A55 CPU, about
  10 ms per frame of JS and draw submission, and in practice from the number of draw calls.
- **New box rule: one draw call per material, not per face.** A box with a material per face costs six draw calls.
  The first build had every hotel block like that, and C had 42 skyline and side-street blocks like that, 252 calls
  alone. That pushed C's 1 % low to 40 fps, with 34 frames over 20 ms. Two fixes brought all three to the same
  budget, ~190 calls, 59.8–59.9 fps, 1 % low 54–59.5 and ≤ 2 slow frames, with no visible change:
  - Re-indexing faces so each material is one group (`regroup` in `town-layout.js`).
  - Merging C's static skyline into one mesh, with roofs darkened by vertex colour.
- **960×540 buys nothing here** and costs sharpness. The hotel name signs stop being readable and the HUD text
  softens (compare `b-holzbaukasten-b4.png` with `-b3.png`). Keep pixel ratio 2, as the real TV page does.
- **No look is near the ~32 fps floor.** At ~10 ms CPU per frame against a 16.7 ms budget, each has room for the real
  game's markers, sound and network.

## What each look costs, and which box rule it touched

Every look follows the same rules:

- No HTML over the canvas.
- Every HUD state is pre-drawn and uploaded at load (8 tags, 52 banners, 6 round pills).
- `renderer.compile` runs at load with all stages showing.
- The board print is unlit, with its light baked in.
- Shadows are blurred rounded-rect quads from a 128 px canvas. There are no shadow maps.
- Grain and wood grain are a repeated small tile, never per-dot.
- There are no alpha-tested cut-outs except the avatar sprites, which use alphaTest 0.04 as in Heimlauf.

| Look | Cost | Rules it touched |
|---|---|---|
| A Modellstadt | Cheapest: 16k tris, all Lambert. One 192 px window tile repeats on every facade through world-unit UVs. | Per-face materials on the blocks: fixed by regrouping. |
| B Holzbaukasten | Most triangles (29k): rounded blocks, bevelled gable roofs, round and cone trees on discs. Walls are drawn per face size (160 px a unit), so windows never stretch. Scene build takes about 0.2 s longer on the box CPU. | Per-face materials on rounded blocks, signs and pool tiles: fixed by regrouping. Wood grain is one 512 px tile drawn as a pattern over the painted board. |
| C Abendstadt | Lit windows are emissive maps (free). 13 street lamps and a lamp in each garden are additive glow sprites (blended, not alpha-tested). Light pools are painted into the board print with `lighter`. The sky is a backdrop plane square to the view behind a merged skyline: the steep board camera never sees the horizon, so a screen background would not show. The camera is a little flatter (y 14 / z 18.6 instead of 16.2 / 17.3) to leave a sky band, so the board sits slightly smaller. | Draw calls: the skyline cost 1 % lows of 40 until merged. |

## The three looks, for the user to choose

- **A Modellstadt.** An architect's model.
  - Off-white hotels with grey window grids and flat roofs with small roof boxes.
  - The hotel name sits as a small white plaque on the facade.
  - Pale grey-green town plan, white streets, fields as finely outlined squares, special fields softly tinted.
  - Model trees (sage balls on thin sticks).
  - White foam-board base on a grey studio table.
  - Owner colour is the subtlest of the three: a thin plot frame, a small roof flag and the canopies.
  - The calmest picture: most of the colour comes from the players.
- **B Holzbaukasten.** A child's wooden building set.
  - Chunky rounded blocks painted cream with blue windows and chimneys, and painted name boards.
  - Gable roofs in the owner's colour, so ownership reads at a glance across the room.
  - Birch plywood board with the ply layers showing at its edge, green paint with the grain showing through, and a
    grey-blue road.
  - Fields are bright painted tiles: orange building site, yellow bank and so on.
  - Toy trees stand on wooden discs.
  - A red toy number die and a beech permit die, on a felt mat.
  - The most colourful and warmest of the three.
- **C Abendstadt.** The town at dusk.
  - Slate-blue streets and dark facades with warm, and a few cool, lit windows.
  - Hotel names glow in the owner's colour on the roofs, with an owner-coloured roof band.
  - Street lamps throw light pools onto the streets, and the entrance canopies are lit.
  - Behind the board, a low lit skyline stands against a deep-blue-to-orange dusk sky.
  - The figures are toned down slightly to sit in the dusk light.
  - The moodiest of the three. Fields read by their stone tint and pale icons.

All three hold ~60 fps at 1080p on the box. None comes close to the 32 fps floor.

## Calls I made (not decided by anyone)

- **Hotel names.** Names printed on the ground were hidden by pools and canopies. They now sit on the main building:
  a plaque in A, a painted board in B, a lit sign in C.
- **Rising stages.** Four plots, one per player, rise on their owner's turn so a stage goes up every ~6 s.
- **Static numbers.** Tags and Taler stay fixed, and the round counter runs 7→12 and wraps.
- **C's camera** is flatter so the sky can show. A and B share one camera.

## Showing it on the TV

`.superpowers/brainstorm/261107-bauherr-look/content/box/show-bauherr-look-on-box.sh 192.168.0.37:5555` shows A, B
and C full screen one after another; press Enter for the next look. The turns keep playing. It cleans up (uninstall,
reverse, HOME) when it ends. Not yet run on the box: only its syntax and the `?show` page load were checked.

Each page also opens on the desktop. Use `?show=1` for full screen. Three.js loads from the CDN without `local`.

## Files

- Pages: `.superpowers/brainstorm/261107-bauherr-look/content/` (gitignored).
  - `a-modellstadt.html`, `b-holzbaukasten.html`, `c-abendstadt.html`.
  - Shared: `town-layout.js` (board data and helpers), `town-print.js` (board print and icons), `town-scene.js`,
    `town-hud.js`, `town-run.js` (turns and measurement).
  - One module per look: `look-*.js`.
  - Avatar images in `assets/`.
- Box material: `content/box/`.
  - Runner `run-bauherr-look-on-box.sh <serial> <page>:<step>:<query>…`, `serve.mjs`, `desktop-shot.mjs`.
  - Logs: `box-round1.log`, `box-round2-run.log`.
  - Every box screenshot: `*-a1…a4`, `b1…b4`, `c1…c4`; 1 and 3 are 1080p, 2 and 4 are 960×540.

## Unresolved questions

- Hotel names on the buildings read at 1080p, but only once a main building stands. Should the real board also carry
  them on a sign at each plot?
- C uses a different camera from A and B. If C is picked, phase 05 should check that the flatter angle keeps every
  field readable with the real move markers.
- The runner depends on the 11-05 spike APK and three.js under `/tmp/…/ad211121…/scratchpad/spike3d/`. A reboot of
  the dev machine would lose them.
