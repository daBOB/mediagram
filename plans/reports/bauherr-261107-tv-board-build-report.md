# Bauherr TV board, built (2026-11-07)

The plain 2D board is replaced by a three.js board in look D (golden hour, matte, baked light). Eight hotel complexes
dominate it, each with its own architecture, landscape and stages, on a winding road of 36 fields with a bridge.
Nothing was measured on the TV box; the lead does that.

## What was built

- **Bake tool** `crates/mediagram-game/tools/bauherr-bake/` (`bake.mjs`, README). It paints in headless Chrome and writes
  `client/public/bauherr/` (WebP images, `world.bin`, `world.json`, `SOURCES.md`). About 3 s. Textures: Poly Haven CC0, inputs in `textures/`.
  `preview.mjs` is a stand-in host: the real board code on a made-up scene.
- **Layout** `client/src/tv/bauherr/bauherr-layout.ts`: one place that turns the rules' fields and hotels into positions
  (road, lots, plaza, camera). Field indices, entrance fields and start are unchanged; nothing in Rust changed.
- **Board** `client/src/tv/bauherr/*` and `TvBauherr.tsx`, driven by `TvScene::Bauherr`. Kept: candidate fields marked in the answer
  colours, permit result, guest payment, round counter, player tags, end banner, awards ranking (same order logic as 6eebe212).
- **Reused from Heimlauf**: `RaceSound` (effects, music; it gained a `tick(plan, final, now)` that any board can feed, `update` unchanged
  in behaviour), the settings panel (OK key), `hop_ms`, die throw formula, avatar sprites, `HUD_FONT`, `TvFinal`.
- Old `bauherr-draw.ts` and the `.tv-bauherr-*` CSS are gone.

## The decisions, and how each shows

| Decision | Result |
|---|---|
| D Abendsonne | Same light model, sun and sky values as D (a little brighter), long baked shadows, lit windows and lamps, matte only |
| Hotels fill the board | Hotel scale 2.0, lots 6.3 x 5.6 world units, town houses dropped |
| Hotel types | Kastanienhof half-timbered inn; Seeblick white lake hotel with balconies; Waldhaus chalet; Stadtpalais ochre palace, mansard, roof garden; Alpenrose white mountain hotel with timber balconies; Sternwarte observatory tower complex; Kurhaus portico and rotunda; Leuchtturm red brick with striped lighthouse |
| Landscapes | Lake and jetty/boat (Seeblick), forest (Waldhaus), city blocks (Stadtpalais), snow mountains (Alpenrose), hill (Sternwarte), spa park with hedges, walk and pond (Kurhaus), orchard and chestnuts (Kastanienhof), sea with boulders (Leuchtturm) |
| Winding road | Two S-curved runs with rounded caps; creek and stone bridge between Waldhaus and Stadtpalais (fields 11 / 12) |
| Upgrade visible | For sale: sign, raw gravel lot with site cabin, sand, bricks, cones. Bought: owner-coloured property line and plot sign. Each stage: scaffold, walls rise in 2.8 s, shadow fades in. Last stage: pool, deck, garden, planetarium and so on laid out left to right |
| Owner colour on real things | Awnings (striped), flags, lit name signs, property line, plot sign, entrance canopies. Baked white and flagged; tinted per vertex at run time, one baked town for all players |
| Dice | Plaza in front; ivory number die and oak permit die, faces as in round 2 |
| Own architecture | No domed palace (the Sternwarte has one slim tower with one dome, the Kurhaus a roof lantern), no gold hexagonal towers, no crescent castle, no twin skyscrapers, no row of chalets |

## Numbers (stand-in host, same board code; desktop headless, so fps is not reported)

| State | Draw calls | Triangles |
|---|---|---|
| Start, 8 lots for sale | 42 | 20.1k |
| All 8 hotels fully built | 42 | 27.3k |
| Asking with 3 marks and one canopy | 47 | 24k |

Round 2 ran 53 to 58 calls and about 100k triangles at 60 fps, so there is headroom. Worst case I expect is about 52 calls
(4 canopy meshes, rings, 4 badges, a stage rising). Calls: floor, outer land, town, halos, per hotel base, owner pieces and shadow
decals (24), 2 dice, pawns, discs, blobs, HUD labels. No render targets; the HUD is a second draw into the same frame, as in Heimlauf.

### Textures (decoded sizes, estimated GPU memory: w x h x 4, plus a third for mipmaps except shadows)

| File | Size | WebP | GPU |
|---|---|---|---|
| ground | 3456 x 2432 | 1193 KB | 42.6 MB |
| outer | 1440 x 960 | 9 KB | 7.0 MB |
| town | 2048 x 2048 | 799 KB | 21.3 MB |
| hotel 0, 1, 2, 4, 6, 7 | 2048 x 1024 each | 304 to 429 KB | 10.6 MB each |
| hotel 3 and 5 (6 stages) | 2048 x 1536 | 615 / 478 KB | 16.0 MB each |
| 8 shadow atlases | 1024 x 512 (no mips) | 4 to 8 KB | 2.0 MB each |
| 2 dice, canopy | 768 x 512, 512 x 512 | 32 KB | 5.3 MB |
| Total | 22 images | 5.24 MB | about 188 MB |

Round 2 estimated 169 MB. If the box minds, the ground print is the lever (64 px per unit now).

**APK:** adds about 7.1 MB: 5.24 MB of images, `world.bin` 1.82 MB, `world.json` 10 KB. They are embedded in the host binary (`client/dist`),
and WebP does not compress further.

**Load:** the page decodes about 37 megapixels and uploads 188 MB; nothing is painted. On the desktop stand-in the board is ready in about
1 s. First frame on the box is unmeasured; I expect it well under round 2's 15 to 18 s, but 5 s is the lead's to confirm.
Everything is compiled (`renderer.compile`, with rising stages, markers and canopies showing) and uploaded (`initTexture`) at load.

## Verification

- `bash scripts/check.sh`: all checks passed (clippy, cargo test, protocol types, game client typecheck and tests, gradle).
- Client tests added (37 in total pass): the layout against `bauherr_board.rs` (field kinds, hotel fields), all 36 fields placed evenly and
  inside the print, entrance fields within 5.5 units of their lot and the nearest within 2, no lot on the road or overlapping; every hotel's
  stage count in `world.json` equals the rules' `costs` length (3, 4, 4, 6, 4, 6, 5, 5), `parseWorld` refuses a mismatch; stage ranges are
  contiguous and `shownRanges` for for-sale, bought, each stage; pawn hop positions; sound cues.
- Full games on the real dev host (`game-night-dev`) with scripted phones and a headless TV page at 960 x 540, scale 2: a 4-player game
  of 45 rounds to the awards ceremony (no errors on any phone), and a 2-player greedy game (build-heavy) until I stopped it.
  Screens in `bauherr-261107-tv-board-screens/`. Each hotel by stage: contact sheets 16 to 19 (first stage, middle, last incl. leisure).
- Screens 03, 04, 06 and 07 are from the first game, built before the HUD fix below; the second game (05, 08) has the fix.

### Bugs found and fixed on the way

- The die-result pill never showed in the real game: its canvas texture was uploaded at one size and drawn at another, and a texture cannot
  grow after upload. Every HUD canvas now gets its final size before upload.
- Pawns on one field overlapped; their slots are wider.
- Hotels overlapped the road at the caps; lots were moved by a search (see `bauherr-layout.ts`) and a test keeps them clear.

## Calls I made that you may want to see

1. **Which lot is where.** The rules' field order forces it: back row left to right Seeblick, Waldhaus, Stadtpalais, Alpenrose; front row
   Kastanienhof, Leuchtturm, Kurhaus, Sternwarte. Seeblick and Alpenrose are turned about 35 degrees to face their caps; some of their far
   entrance fields (Seeblick 3, Alpenrose 20) are 4 to 5 units from the lot.
2. **No sky.** At about 36 degrees pitch the ground fills the frame to the top edge; warm haze stands in for sky.
3. **Pawns are always avatar sprites.** The settings panel's "Spielfiguren" row has no effect on Bauherr.
4. **The Sternwarte was redrawn** to avoid a "domed palace" reading: slim tower, one copper dome, white telescope dome on one wing, masts and
   dishes, planetarium dome as the leisure stage. The Kurhaus lost its main cupola (the rotunda keeps one).
5. **The hotel's raw lot ground stays until the first stage is finished**, so the building rises among the site cabin and sand heap.
6. **Tags** show cash as "12.400 T" and the number of hotels; "raus" for a broke player.
7. **Sea cliff is boulders.** Leuchtturm has sea, beach and boulders, not a cliff face.
8. Shadows of rising stages overlap those of standing ones, so overlaps are a little darker.

## Open questions

1. Is the board too busy at the top (mountains, city blocks) behind the HUD banner? The banner could move.
2. Should the "Spielfiguren" row be hidden for Bauherr?
3. Lots are rotated at both ends; do you want the road reshaped so all eight face the viewer?
4. Box check: ground print resolution versus load time, and the 188 MB total.
