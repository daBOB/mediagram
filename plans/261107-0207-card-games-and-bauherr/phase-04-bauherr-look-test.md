# Phase 04 — Bauherr look test

**Status:** done — user picked D Abendsonne (12:55). The user picks the look on the TV box; nothing here ships.

Three live town mockups as standalone three.js pages, built to the box rules from
the start, measured on the box, shown to the user. Same method as the board-world
round of 2026-11-05 (`plans/reports/benchmark-261105-1611-tv-box-3d-framework-spike-report.md`,
pages in `.superpowers/brainstorm/3391110-1793908363/content/`, runners in
`/tmp/claude-1000/-home-andre-Workspace-mediagram-spieltisch/ad211121-b6dd-4d66-a985-9f61f1c25990/scratchpad/spike3d/run-*-on-box.sh`).

## Every mockup shows the same scene

A closed path of ~36 fields through a town, 8 hotel plots beside it: two empty
(for-sale sign), the rest at different stages — main building only, with annexes,
fully built with a leisure facility (pool, garden). Entrances on a few fields,
owner-coloured. 4 player pawns as 2D sprites (Heimlauf's figures or avatar
sprites). A number die and a permit die (tick / cross / free / ×2 faces, own
symbols) rolling on the table beside the board. A hotel visibly rising by one
stage every few seconds. Player tags + turn banner + "Runde 7 / 24", pre-drawn.

## The three directions

- **A Modellstadt** — an architect's model: matte off-white buildings with window
  grids, light grey-green town plan with printed streets, small round trees, soft
  baked contact shadows. Calm, elegant.
- **B Holzbaukasten** — painted wooden toy blocks on a wooden town board (distinct
  from Heimlauf's veneer board), chunky, warm, a child's building set.
- **C Abendstadt** — the town at dusk: warm lit windows baked into the facades,
  street lamps glowing, deep blue sky gradient. Cosy game evening.

Never Hotel's (MB/Hasbro) facades, cardboard building pieces, hotel names or board.

## Do

- Pages in `.superpowers/brainstorm/261107-bauherr-look/content/` (gitignored).
- Quality first; report fps, 1 % low, frames > 20 ms and first-frame time per page
  at 1080p and at the 960×540 page scale. Floor ~32 fps.
- Box: `adb -s 192.168.0.37:5555`; wake the display before each run; never
  change settings on the box; no other agent uses the box during this phase.
- Box screenshots of each page; report in `plans/reports/look-test-261107-…-bauherr-town-report.md`.
- Stop after measuring: the user picks, the lead asks.

## Round 1 result and the user's verdict (2026-11-07 10:19, after the TV cycle)

Round 1 (A/B/C, all ~60 fps on the box): report `plans/reports/look-test-261107-bauherr-town-report.md`.
User: "we need a realistic style. B is one but its to playful and it need a bit of C but with
realistic textures. we need to push here the tv box to the limit."

## Round 2 — realistic town, two evening variants

- B's layout, board and owner-colour readability; nothing toy-like (no chunky blocks, no plywood).
- Realistic materials from real CC0 textures (Poly Haven / ambientCG): plaster, brick, timber, roof
  tiles, cobblestone, asphalt, grass, water. Matte; no glossy look, no reflections (user, 2026-11-07).
- Some of C: evening mood, lit windows, street lamps, owner colour shown as real things (awnings,
  flags, lit hotel signs in the owner's colour).
- Push the box: spend the budget on detail; ~32 fps is the floor (1 % low reported).
- Variants: **D Abendsonne** (golden hour, long warm light, first windows lit) and **E Blaue Stunde**
  (after sunset, most windows and lamps lit, closest to C).

## Round 2 result and the user's pick (2026-11-07 12:55, on the TV)

Round 2 (D/E, both ~59.6 fps, 1 % low ~53, first frame 15–18 s on the box): report
`plans/reports/look-test-261107-bauherr-realistic-round-2-report.md`.
User picked **D Abendsonne** (golden hour) and kept the dice **on the cobbled square** (no table).
Load time is the open cost: the TV board must ship the painted surfaces as images instead of
painting them on the box.
