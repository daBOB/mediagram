# Lebensweg — board look (prototype)

Question: what should the Lebensweg board look like on the TV?
Prototype: `lebensweg-diorama-look-prototype.html` (`?variant=A|B|C`, `?still=1`), throwaway.

## Verdict (user, 2026-11-12)

- Cartoon toy/paper kit: rejected ("soo ugly").
- Realistic miniature (model-railway diorama on a table): kept.
- Board: **A Lebensberg**, the terraced spiral mountain, one life stage per terrace, snow on top.
  Not chosen: B Inselreich (islands), C Feldflur (fields). Sheet: `reports/lebensweg-realistic-miniature-looks.jpg`.

## What the prototype costs to draw (Lebensberg overview, measured with `boardStats()`)

| | Lebensberg | TV box reference (Mali-G57, three.js) |
|---|---|---|
| Meshes (≈ draw calls) | 1,339, ×2–3 with shadow and AO passes | 50-object board: 51 fps lit, 59.9 fps baked/unlit |
| Triangles | 1.29 M | 8 foxes, 125 k: 33 fps |
| Materials / textures | 119 / 59 (Poly Haven 1K JPG, from the web) | |
| Lighting | live sun, 4096 shadow map, GTAO, bokeh DOF | lit + shadow map: 16 fps |

Live, as prototyped, it will not run on the box. It has to ship the way Grand Allee's board does:
light, shadows and AO baked in Blender, unlit glTF with KTX2 atlases, trees and props merged into a
few meshes, textures bundled instead of fetched. The depth of field must be cheap or go.

## Geometry QA (2026-11-12, user: "check the board for clipping and geometry errors")

Two checks, rerun after every change; scripts in the session scratchpad (`board-qa.js` in the page, Blender `qa_check.py` per model):
- Board: holes (looking down into the hollow mountain), coplanar ground, props floating or sunk, props inside the rock,
  on the road or in the water, road off the ground, ledge normals.
- Models (exported to glTF, Blender asset QA): z-fighting, loose parts, enclosed parts, normals.

Found and fixed: 10 m² hole beside the summit (the last turn had nothing on its inner side), every ledge face inverted,
school/office/chapel/house/family partly inside the cliff above, 15 trees with crowns in the cliff, tent floating 6.8 m up
(ground lookup returned the summit height outside the first turn), 533 road vertices under the ground, 86 discs with a
2 cm gap, a ramp start z-fighting with the table grass, window glass 1 mm from its frame, symbol decals 2 mm over the
discs, wall boxes sharing the plinth's bottom face, a doubled fence picket, headlights and rear bumper inside the car body,
two rooftop AC units inside each other, lamp arm against the pole cap. Now: all board counts 0.

Later, at the road's end on the summit: snow teeth through the road's last half metre. The summit's outline started 0.6 m
short of the road end's outer corner, so it crossed itself and its fill lay over the road; and one flat road quad per
field cut 1 cm under the kink where the ramp levels off. Fixed (outline starts at that corner, road in 20 cm pieces).
The road check was vertex-only, so the board QA now also looks at the road on a 3 cm grid (terrain over road: 0).

Accepted: hairline cutting into the head (below depth precision at focus distance), tent stones and one boulder that
rest on the ground (the model check cannot see it), rocks and bushes partly sunk, houses up to 12 cm into the slope uphill.

Landmarks no longer clip by coming smaller where the 5 m ledge has no room: uni and mine 55 %, workshop, family and farm
70 %, office, chapel and house 85 %; 2 of 14 office-block spots stay empty. A wider ledge would keep them full size but
makes the board larger.

## Added (user, 2026-11-12)

- Organic mountain: wandering cliff lines (world-space noise, same on both sides of a seam), rock faces worn back in
  strata and gullies under a grass lip, boulders along cliff feet and edges.
- Waterfall: spring on the summit, a fall down every wall with a splash pool, a stream across each ledge under the road
  (culvert between two discs), a pond at the foot; the line is picked clear of forks, STOP discs and the district.
  1 m wide (splash pools 1.2 m, pond 2 m) so it reads from the overview; at 44 cm the user could not find it. Each column
  of a falling sheet leaves its own stretch of the wandering edge; pools sit as close to the wall as their round side allows.
- Office district on the Beruf street: 12 blocks (concrete or glass, 2–6 storeys) facing the road, pavements, 10 lamps.
- Softer snow line (user: "make a softer transtion to snow ground"): one ground material blends grass into snow over a
  height band (from 1 m under the last stage's start to 0.2 m over it) broken up by world-space noise, so drifts begin
  halfway up the Goldene Jahre turn and close up early on the last one; was a hard edge where the stage changed.
- The players' own avatars (user: "the choosen avatar are not included"): the lobby's 3D avatar (shipped catalogue, 22)
  drives each car, bones bent into a seated pose with hands at the wheel; the HUD cards show the avatar heads. Prototype
  copies: 4 avatars at 6k triangles, 512 px textures, no clips, base64 in `lebensweg-avatars.js` (file:// cannot fetch a
  .glb); the game loads `/game-avatars/<id>.glb`. The driver no longer ages in looks (an avatar has no hair to grey);
  the age shows on the card, partner and children still age.
- New university (user: "very ugly"): neoclassical hall, six-column portico on three steps, pediment with a clock, two
  wings under slate hip roofs, framed windows, drum with patina dome and gilt lantern, two lamps (Blender asset QA: pass).

Sheets: `reports/lebensweg-geometry-qa-before-after.jpg`, `reports/lebensweg-organic-waterfall-district.jpg`,
`reports/lebensweg-waterfall-one-metre-wide.jpg`, `reports/lebensweg-board-tour.jpg` (whole board as of 2026-11-12 evening), `reports/lebensweg-snow-avatars-university.jpg`.
Draw cost now: 1,798 meshes, 1.05 M triangles (was 1,339 / 1.29 M).

## Open

- [ ] Bake test: one terrace of the Lebensberg baked and measured on the box (fps, look against the prototype)
- [ ] Phone controller and TV screen mockups, shown before the views are written
- [ ] Money values scaled by the field factor 1.3, balance measured with bots
- [x] Prototype committed to `prototype/lebensweg` (2026-11-12, user: "commit"; not merged into main)
