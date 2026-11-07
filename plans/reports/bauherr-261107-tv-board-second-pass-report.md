# Bauherr TV board, second pass (2026-11-07)

All five asks are in, the first frame on the box is about 3.8 s after the board appears (was about 6.2 s), and the stalls are
measured and mostly gone. Every number below is from the TV box (192.168.0.37, the real app, a real game night, 4 LAN bots, three
to five minutes of real play); nothing is estimated unless it says so. Screenshots: `bauherr-261107-tv-board-second-pass-screens/`
(19 desktop shots at 960 x 540 scale 2, from the stand-in host and from a real 45-round game on the dev host; three box shots,
`box-*`: the old board in round 19, the new one in round 18, and the new one waiting for a roll).

## The asks

| # | Ask | What it is now |
|---|---|---|
| 1 | Realistic mountains and rocks | Mountains, hill and coast boulders are **height fields lit texel by texel** (sun marched along the heights so ridges shade gullies and cast shadows, sky occlusion, normals) and coloured from Poly Haven **rock, scree, meadow and snow photographs** laid on by slope and height, triplanar. One image, one mesh (16k triangles), drawn unlit like the town. The coast is 29 lumpy boulders with lichen on the sunward tops and a wet dark foot. Shots 08, 09, 01. |
| 2 | Smaller plaza, closer view, bigger dice | Plaza 8.8 x 2.35 units (was 14 x 8, a quarter of the screen), now a few percent of it, just under the front road and mostly behind the player tags. Dice 1.2 units (were 0.8, 1.5x), faces easy to read. Overview camera: whole road across the frame, back row of lots near the top. The "closer" part is the lean of ask 5, see calls below. |
| 3 | Warmer golden hour like D | Sun lower (about 16 degrees) and from the left and a little from the viewer, so front walls get light too; warmer sun colour and bounce, stronger sun; the far land fades into a **warm orange haze** (fog 18 to 85 units) where the sky would be. One sun direction (`SUN_DIR` in `bauherr-layout.ts`) for the bake, the dice, the pawn discs and their blobs. Shots 01, 03, 18. |
| 4 | Nicer unbuilt lots | **For sale:** a meadow (grass photo, long grass, wild flowers, shrubs, stones, a surveyor's string with red and white stakes) and the agent's board. **Bought:** a building site, mown lawn at the edge, packed earth inside, a gravel track from the gate with tyre marks, the foundation chalked out with stakes, cabin, pallets of bricks, sand, stacked timber, pipes, cones; the **owner's fence** (picket fence in the owner colour with cream caps, a gate in front) and a bigger **owner-coloured sign** ("Hier baut"). The sign stands only until the first stage is built; the fence stays. Shots 02, 11, 15, 17. |
| 5 | Camera follows the turn | `bauherr-camera.ts`. The overview **leans slightly toward the pawn** while its owner rolls and walks (15 % of the way to the pawn, 5 % closer), follows its hops, and **eases back out** when the move is over. Critically damped glides (1.1 s in, 1.5 s out, 0.7 s for the focus), never a jump. Per phase: `roll`, `rolled`, `walking` lean; `guest`, `permit`, `ask` (candidate fields), `note`, `final` are at the overview. HUD is a separate pass and does not move. Shots 04, 05, 13. |

## Box numbers

**First frame** (board appears after the game starts). The 20 to 24 s of the first report came from the PNG screencaps taken every
4 s, which slowed the box; it was never that slow. Raw screencaps once a second (colour of the screen: lobby green, loading page dark,
board warm), and the page's own timing marks over a beacon (a build made with `VITE_BAUHERR_BEACON`):

| | Before (0.153.0) | After |
|---|---|---|
| Board first seen after `start_game` (real app, screencaps about 1.5 s apart, so each figure is good to one step) | 7.6 s and 7.8 s (the screen was still dark at 6.0 s) | 4.8 s, 5.1 s and 6.0 s in the last three runs (dark at 3.4, 3.6 and 4.5 s); 6.5 s in the first KTX2 build, before the scene was built early |
| Page timeline, from the board's scene appearing | about 6.2 s (spike WebView: 7.3 s from navigation; 1.1 s of it page load) | **3.84 s** to the first frame |

Where the time went, before (spike WebView on the box, same page code, seconds from navigation): page load 1.1 | images fetched and
decoded lazily 1.5 to 2.8 | renderer, scene, shader compile call 2.9 to 3.4 | **uploads 3.4 to 7.1: 3.6 s** (22 images, 37 megapixels decoded
by the main thread, uploaded as RGBA8, mipmaps built there) | first frame 7.3. After (real app, seconds from the scene appearing,
`bauherr-timing.ts` marks): chunk 0.17 | renderer 0.31 | geometry 1.11 | scene built 1.62 (0.5 s of JS, while the images are still in
flight) | KTX2 textures arrive 2.6 to 3.6 (fetch, worker start, ETC1S transcode on 4 little cores) | uploaded 3.56 | first frame 3.84.

What did it: (1) the big images are **Basis ETC1S KTX2 with mipmaps**, transcoded in a worker pool to ETC2 / ETC1 (no main-thread decode,
no mipmap building); (2) the scene is built and its **shaders compile while the images are still arriving** (empty texture shells that
fill in; `compileAsync`), and each texture goes up as it lands; (3) renderer, avatars, fonts and images load side by side instead of in
a row; (4) smaller shadow tiles and far land. Not tried: UASTC (a Mali GPU normally takes ASTC, which needs no transcode, but I did not check this box, and the files are 5x
larger, about +20 MB in the APK) and preloading the textures in the lobby; the transcode (about 1 s of the 3.8) is the floor now.

**Play** (`dumpsys gfxinfo`, 4 bots, 380 s measured after the first minute and a half; "frames" are the app's, "p" is gfxinfo's frame
time, which on this box is pipeline latency, two vsyncs, 33 ms, when all is well):

| Build | frames | janky | p50 | p90 | p95 | p99 | missed vsync |
|---|---|---|---|---|---|---|---|
| Before (0.153.0, first report, 453 s) | n/a | 0.61 % | n/a | 36 | n/a | 44 | 10 |
| Before (this pass's re-run) | 22713 | 0.39 % | 25 | 36 | 36 | 42 | 0 |
| New content only (terrain, lots, camera, KTX2), no GPU work yet | 22618 / 22607 | 0.73 % / 0.77 % | 23 | 38 | 42 / 40 | 46 / 46 | 1 / 2 |
| **Final, run 1** | 22709 | **0.37 %** | 25 | **34** | 34 | **40** | 0 |
| **Final, run 2** | 22715 | **0.35 %** | 28 | **34** | 34 | **40** | 1 |

p99 is 40 to 42, not the 33 ms the brief aims at: in gfxinfo's unit 33 ms is the floor, so "p99 <= 33" cannot be met by any frame that is
not early. The page's own view (the board's `requestAnimationFrame` cadence, logged by `FrameLog` every 30 s, final run 1: 28692
frames): **59.8 fps, 0.20 % of frames longer than 25 ms (57 of 28692; run 2: 58 of 28707), none longer than 70 ms in play** (one 256 ms frame in
the first half minute, while the first shaders link), 2 long tasks in 8 minutes. The new content alone had doubled the slow frames (0.44 %).

**What spiked, and what I did.** The log of every slow frame by phase (motion, HUD update, board render, HUD render) and what the game
was doing:
- Not the HUD redraws (a redraw frame costs 1 to 8 ms of script), not stage rises (no rising frame among the worst), not hops, not
  the dice, not React or the socket: every slow frame is **exactly one missed vsync (33 ms) with 3 to 8 ms of script**, and most fall
  while the board is just waiting for a roll (`roll`). The main thread is healthy. It is the GPU: a stand-in host that draws the town
  extra times a frame (`?stress=N`) measures its room: one pass of the town costs 5.35 ms of a 16.7 ms frame before this pass's
  fixes, with about 8.5 ms fixed (HUD, compositing); MSAA is 1.65 ms of each pass.
- Per part, from taking it out (per pass): town 1.35 ms, terrain 1.2, ground print 1.1, lamp glows 0.7, shadow tiles 0.6, owner pieces 0.6.
  Fixes: the two big ground planes are drawn **after** what stands on them (three sorts opaque meshes by material, so the 8-megapixel
  ground print was shaded first and covered afterwards: -0.6 ms), the town's and terrain's triangles are **ordered front to back** at bake
  time (-0.4 ms), **meshes with nothing to draw are left out** (a hotel's owner, shadow and base meshes when empty: 43 draw calls became 27 at the
  start of a game), ground print, shadow tiles and far land are smaller. One pass is now about 4.3 ms (the old board: 4.2 ms, the first build of
  this pass 5.35). KTX2 also takes the GPU texture memory from about 198 MB to about 55 MB (estimated from sizes: w x h x 4 or 8 or 4 bits
  per texel, +1/3 for mipmaps).
- Kept: MSAA. Taking it out would give 1.65 ms a pass back (a calmer p99) at the cost of jagged roofs and fences; that is your call.

## Textures, sizes

| File | Pixels | On disk | On the GPU (est.) |
|---|---|---|---|
| ground | 3456 x 2432 | 1389 KB KTX2 (ETC1S, RGB) | 5.6 MB (ETC1, 4 bpp) |
| hotel 0, 1, 2, 4, 6, 7 | 2048 x 1024 | 277 to 348 KB KTX2 | 2.8 MB each (ETC2 + alpha, 8 bpp) |
| hotel 3, 5 | 2048 x 1536 | 480 / 358 KB | 4.2 MB each |
| town | 2048 x 2048 | 809 KB | 5.6 MB |
| terrain | 2048 x 1472 | 324 KB (RGB) | 2.0 MB |
| 8 shadow tiles | 768 x 384 each | 4 to 6 KB WebP each | 1.2 MB each (RGBA8, no mips) |
| dice (2), canopy, far land | 768 x 512, 512 x 512, 720 x 480 | 1 to 22 KB WebP | 4.2 + 1.4 + 1.8 MB |
| **Total** | | **5.3 MB KTX2 + 76 KB WebP** (before: 5.24 MB WebP) | **about 55 MB** (before about 198 MB) |

**APK:** 103,661,249 bytes before, 107,373,305 after (+3.7 MB): `world.bin` 1.8 to 3.2 MB (terrain, fences, sites), Basis transcoder
0.58 MB, images about the same size as before. Draw calls and triangles (stand-in host): start 27 calls and 52k triangles (was 42 and
20k), bought lots 39 / 46k, every hotel built 45 / 50k, candidates asked 36 / 51k.

## Calls I made

1. **Terrain is baked, not modelled.** A height field mesh with one lit-and-coloured image gives real rock under the town's
   unlit rule, with ridge shadows and sun-lit faces at texel resolution, for one draw call. The cost is stretched texture on the very
   steepest faces if you look from the side (shot 08), which the overview never does.
2. **ETC1S, not UASTC.** The painted atlases look the same in both (compared crop by crop); ETC1S is the size of the old WebP, UASTC is 5x.
3. **The lean is small** (pan 15 %, dolly 5 %, never further forward than field row z = 1): a test projects the dice, every pawn and
   the result pill through the camera at every lean on every field, and the dice's front edges just reach the player tags at the extreme lean.
   Bigger leans push the foreground (dice) behind the tags. The lean stays during `rolled` (the dice must be seen) rather than
   breathing out and in within a turn.
4. **No dwell between turns.** If the next roll follows a walk at once, the camera glides to the next pawn without a full return to the
   overview; a stage between (guest, permit, ask, note) is what brings it all the way out.
5. **The die's result pill moved** from the middle of the right edge to the right corner under the road. At the old place it covered
   the front road and pawns; with a leaning camera that gets worse. It is the only HUD element that moved; the camera does not move the HUD.
6. **Plot sign only before the first stage** (a range of its own at the end of the owner mesh): at 1.35x it hid the facades of built hotels.
7. **Sun, haze and the ground's colour are shared with the bake** (`SUN_DIR`, one fog colour), no copies to drift.
8. **No version bump, no changelog.** The box updated itself to 0.154.0 from the channel while I worked, and Android refuses a lower
   version code, so this pass's last builds were sideloaded as a local 0.154.1 (the manifest change is not committed). **The box now runs
   that build** (with the beacon address of this machine compiled in, harmless without a listener); it is on the app's game lobby.
   The next channel release above 0.154.1 replaces it.

## Open questions

1. MSAA off for headroom (-1.65 ms a pass), or keep the edges?
2. A short dwell at the overview between turns when nothing sits between a walk and the next roll?
3. Preload and transcode the textures in the lobby (the TV knows the game before it starts) to take the last second off the start; or UASTC (+20 MB APK).
4. The Sternwarte's hill still rises into the back half of its lot (as in the first build); moving it clears the lot but the loop's inside is only 2 units deep there.
5. `FrameLog` and the timing marks log to the console every 30 s. Keep them as they are (cheap, silent unless a build sets
   `VITE_BAUHERR_BEACON`), or remove them now that the numbers are in?
6. Real box check of the camera with phones in hand: the lean amplitude is the one thing a person should judge on the TV.

## Verification

- `bash scripts/check.sh`: all checks passed (clippy, cargo test, protocol types, web, game client typecheck and 44 tests, gradle).
- New tests: camera glide (no overshoot), what each turn stage asks of the camera, a walk that leans in, follows and eases out with no
  frame moving the camera more than 0.09 units, dice on screen between banner and tags at every lean, the whole road in the overview,
  no pawn behind the result pill; the sign is the last owner range; the lot ranges.
- Desktop: real 45-round game on the dev host (4 scripted phones, headless TV 960 x 540 scale 2): no page errors; shots 10 to 19.
  Stand-in host: shots 01 to 09.
- Box: spike WebView app uninstalled, no `adb reverse` left, the app is on the game lobby.
