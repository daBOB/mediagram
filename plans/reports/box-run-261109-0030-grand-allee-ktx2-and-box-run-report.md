# Grand Allee 4d: KTX2 atlases and the run on the TV box

Phase: `plans/261108-0455-grand-allee/phase-04-tv-board-and-live-game.md` (4d). Nothing committed, no version bump in the
manifests except the coordinator's test `versionName 0.163.1` in `android/app/build.gradle.kts`.

## Part A: KTX2 textures

**What changed**

| File | Change |
|---|---|
| `crates/mediagram-game/tools/grand-allee-models/glb-to-ktx2.mjs` (new, 50 lines) | Reads GLBs with gltf-transform, turns every WebP atlas into a PNG without alpha (sharp), runs `toktx --t2 --encode etc1s --clevel 4 --qlevel 255 --genmipmap --assign_oetf srgb --assign_primaries bt709 --target_type RGB` (Bauherr's settings, quality 255), writes `KHR_texture_basisu` GLBs and drops `EXT_texture_webp`. `KTX_QLEVEL` / `TOKTX` to override. |
| `tools/grand-allee-models/README.md` | Table row, run section (the board and hotels now go through the script into `client/public/grand-allee/`; the dice are still copied), toktx location, a "KTX2 on the TV" rule with the GPU numbers. |
| `client/src/tv/grand-allee/grand-allee-assets.ts` | `loadAssets(renderer)`: `KTX2Loader` with Bauherr's `/bauherr/basis/` transcoder and `detectSupport(renderer)`, handed to `GLTFLoader.setKTX2Loader`; disposed (workers stopped) once the models are in or one failed. Comment: transcoder and setup move to a shared place before Bauherr is retired. |
| `client/src/tv/grand-allee/grand-allee-stage.ts` | passes the renderer (one line). |
| `client/tests/grand-allee-assets.test.ts` | Reads KTX2 headers too; new test: every hotel and board atlas is KTX2, BasisLZ (ETC1S), one colour slice (RGB, so the box transcodes to ETC1, not ETC2+alpha), sRGB transfer, full mip chain, and `KHR_texture_basisu` is the only required extension. Budget tests now read either format. Checked it fails on a WebP hotel. |
| `client/public/grand-allee/SOURCES.md` | "KTX2 in the hotels and the board, WebP in the dice". |
| `client/public/grand-allee/{10 hotels, board}.glb` | Converted. The board is the coordinator's re-bake with the curved road (`scratchpad/curve2/board.glb`), converted the same way. Dice unchanged (WebP, 256²). |

`tsc --noEmit` clean; `bun test` 122 pass, 0 fail (with the curved board and the other agent's client changes in the tree).

**Sizes** (atlas WebP -> KTX2, GLB before -> after, KB)

| GLB | Atlas | Atlas KB | GLB KB |
|---|---|---|---|
| alpenrose | 1024² | 158 -> 192 | 345 -> 378 |
| fjordhus | 1024² | 153 -> 181 | 359 -> 385 |
| hacienda_sol | 1024² | 184 -> 211 | 319 -> 345 |
| kirschbluete | 1024² | 120 -> 171 | 235 -> 285 |
| lagune | 1024² | 141 -> 180 | 367 -> 405 |
| riad_atlas | 1024² | 229 -> 214 | 467 -> 451 |
| belle_epoque | 2048² | 312 -> 462 | 537 -> 687 |
| maharadscha | 2048² | 422 -> 499 | 662 -> 738 |
| savanne | 2048² | 346 -> 393 | 669 -> 715 |
| skyline | 2048² | 243 -> 449 | 326 -> 531 |
| board (curved re-bake) | 2048×1536 + 2×1024² | 209+175+168 -> 374+189+187 | 1444 -> 1643 |
| **all eleven** | | | **~5.9 -> ~6.7 MB** (+0.85 MB; the APK grows ~1.7 MB, the client is embedded once per ABI) |

Quality 192 saved only 5-10 % of the file and the GPU memory is the same at any level, so 255 stays.

**GPU memory estimate** (full mip chain, ×4/3)

| | WebP -> RGBA8 | KTX2 -> ETC1 (0.5 B/texel) |
|---|---|---|
| six 1024² hotels | 33.6 MB | 4.2 MB |
| four 2048² hotels | 89.5 MB | 11.2 MB |
| board 2048×1536 + two 1024² | 28.0 MB | 3.5 MB |
| **total** | **151 MB** (144 MiB) | **18.9 MB** (18 MiB) |

The dice's two 256² WebP add 0.7 MB either way. With WebP, three's GLTFLoader also kept each decoded ImageBitmap as
`texture.image`; KTX2 has no such copy.

**Look**: the stand-in host (`tools/grand-allee-preview/shoot.mjs`, headless Chrome, 1920×1080) before and after, all six
presets: PSNR 33-39 dB, and the difference image shows only the pulsing markers. 1:1 crops of "Bank", "Rathaus", the field
icons, the facades and the dice show no smearing (review folder, `ktx2-preview-*`). The curved board renders with KTX2 in the
preview too (31 164 triangles in the midgame preset).

## Part B: the box

Skyworth HPR302 (`UHD Google TV STB`), display mode 1920×1080 at **59.0 Hz** (the only mode). Note: `top` reports 3.75 GiB
RAM, not ~2 GB.

**Builds installed** (all with `build-apk-no-publish.sh`, all with `VITE_BAUHERR_BEACON=http://192.168.0.118:8787/beacon` so the
page's timing marks reach this machine; the box's WebView has no devtools in release):

1. 0.162.1 + KTX2 (8690b8d6): run 1. It dropped Kopfnuss from the box; superseded.
2. 0.163.0 + KTX2, straight board (159bc40a): load probes and run 2 (stopped at round 11).
3. 0.163.0 + KTX2 + curved board (0ca84c8d + the coordinator's curved-board client): load probes, run 4 (the full game).
4. **0.163.1 (versionCode 163001) + KTX2 + curved board, from 411a7a53**: installed last, load probes. **This is what is on the box now.**

Builds 3 and 4 have identical Grand Allee code (411a7a53 changes no file under `crates/mediagram-game` since 0ca84c8d).

**Load** (seconds from the leader's `start_game` to the page's second frame, wall clock; in brackets the page's own marks from
the board's mount: models in / shaders compiled / first frame)

| Build | Cold (first board since app start) | Warm |
|---|---|---|
| **0.163.1, curved, installed** | **3.55** (2.26 / 3.00 / 3.49) | **2.79, 2.58** (1.87 / 2.31 / 2.75; 1.72 / 2.14 / 2.53) |
| 0.163.0, curved | 3.63 | 2.62, 2.62; 2.86 at the start of run 4 |
| 0.163.0, straight | 3.25 | 2.85, 2.78, 2.60 |
| 0.162.1, straight (run 1) | 3.42 (2.03 / 2.80 / 3.36) | |

Mount follows `start_game` within ~0.05 s. Inside the warm load: dice GLBs at 0.3-0.6 s, the ten hotels and the board at
1.3-1.9 s (fetch, worker start, ETC1S transcode), compile 0.4-0.5 s, first frame 0.4 s later. Bauherr's board, for reference,
took 3.84 s to its first frame.

**Frames** (the board's own `FrameLog`, 30 s windows, display 59.0 Hz)

- Run 4, the whole game (56 rounds, 29 min, five bots, the curved board): the 47 windows without any measurement of mine in
  them: **82 634 frames = 58.6 fps (99.3 % of vsyncs), 341 frames over 25 ms (0.41 %)**, at most 14 slow frames in a window.
  The 10 windows with a screencap, `dumpsys meminfo` or `screenrecord` in them: 1.43 % slow, up to 72 in a window (the
  measuring costs frames; `screenrecord` the most).
- 0.163.1, 4 minutes of play: 1 760-1 770 frames per window after the first, 1-5 slow.
- Most slow frames are 33-68 ms (one or two missed vsyncs). The worst, not caused by me:
  - **373 ms right after the board appears**, in every run (534 ms in run 1): the third frame or so. Likely the first
    programs finishing their link, as in Bauherr's report; it happens while the board has just faded in.
  - **254 + 294 ms around a stage rise in round 46**, and **203 ms just after a purchase in round 4**: once each in 29
    minutes. The frame phases (motion, HUD, board, HUD-GL) stay under 16 ms in those frames, so the time goes outside the
    page's frame code (GC, a first upload of a stage's geometry, or the compositor). Other rises: 33-55 ms (83 ms once in run 1).
  - Hops, dice throws and walks: nothing above 105 ms; walking frames are among the ordinary 33-53 ms ones.

**Memory** (run 4 and 0.163.1; `dumpsys gpu --gpumem` works on this box, `dumpsys meminfo` reports Graphics 0)

| | App GPU | App PSS | WebView renderer PSS |
|---|---|---|---|
| Lobby (no board) | 88-106 MiB | 186 MB | 74 MB |
| Board loaded | 188-202 MiB | 189 MB | 117 MB |
| Round 39 / 48, all ten hotels standing | 196.6 MiB (flat all game) | 202 MB | 147 MB |
| Final ceremony (3D podium) | 273 MiB | 197 MB | 199 MB |

The board adds ~100 MiB of GPU memory, flat for the whole game (every texture goes up before the first frame). Of that, the
hotel and board atlases are ~18 MiB now; the rest is the 1920×1080 drawing buffers (MSAA), the HUD and the avatars. The box
has 3.75 GiB in all; GPU memory over all processes stayed at 237-251 MiB during play.

**WebP vs KTX2 on the box: not measured.** The only WebP builds I made were from before Kopfnuss (discarded per the
coordinator) or picked up another agent's half-finished curved-board client (discarded). From the numbers above the WebP
atlases would add ~125 MiB of GPU memory (app GPU ~320 MiB instead of ~196) plus RGBA uploads and mipmap building for 28 megapixels at the start.

**Game**: run 4 played all 56 rounds to the final ranking and the ceremony: Lena 59 075, Jonas 37 325, Mia 36 700, Papa and
Gerda bankrupt. All ten hotels stood from round 48; seven were fully built (Alpenrose, Fjordhus, Hacienda Sol, Kirschblüte,
Lagune, Riad Atlas, Skyline), Maharadscha 4/6, Belle Époque 1/6, Savanne 1/5. Never all ten complete: the bots are poor
builders (two went bankrupt), so the overview with every stage of every hotel was not seen on the box. Auctions happened (one
shot).

**Screenshots and video** (`plans/261108-0455-grand-allee/reports/box-run/`; all from the box, the curved board):
`box-curved-01-opening.jpg`, `-02-midgame.jpg` (round 10), `-03-round39-nine-hotels-standing.jpg`,
`-04-round48-all-ten-standing.jpg`, `-05-auction.jpg`, `-06-final-board.jpg`, `-07-final-ranking-ceremony.jpg`,
`box-curved-bend-closeup-1to1.jpg` (1:1 crop of the north bend where the yellow, purple and pink plots meet the road),
`box-play-curved-board-round24.mp4` (53 s, 960×540, 3.5 MB). `screenrecord` on this box captures only ~8 frames a second, so
the video looks choppier than the TV does. Also `box-tv-games-shelf-grand-allee-card.jpg` and the two `ktx2-preview-*`
comparisons.

## What looked wrong on the TV

1. **Games shelf**: the Grand Allee card (no art yet) is drawn as a tall black panel over the right end of the row, covering
   Durak and Bauherr, and the D-pad cannot focus it (focus stops at Bauherr). Grand Allee cannot be chosen from the TV; the
   leader's phone can still pick it in the lobby, which is how every run here started.
2. The ~0.4 s stall right after the board appears, and the two 0.2-0.3 s stalls in 29 minutes (above).
3. The timing helper (`bauherr-timing.ts`, Bauherr's) never clears its performance marks: each later game's start report
   repeats every earlier game's marks. Harmless, the line just grows.
4. The box runs its HDMI mode at 59.0 Hz (the only mode it lists).

Nothing wrong seen in the KTX2 textures on the box: lettering, field icons, facades and dice are as in the preview.

## Box state now

**0.163.1 (versionCode 163001)** from 411a7a53 + the uncommitted KTX2 and curved-board work, Kopfnuss included, game night open
in the lobby. It has `VITE_BAUHERR_BEACON=http://192.168.0.118:8787/beacon` compiled in (the board POSTs its timing marks to
this machine; nobody listens now, so they fail silently). Replace it with the commit's build. The beacon listener and the
local dev host are stopped.

## What's left

- Commit, with the full version bump (coordinator). `android/app/build.gradle.kts` holds the test `versionName 0.163.1`.
- The phase-04 todo line for 4d (the plan file is being edited by another agent; I did not touch it).
- After any later board or hotel re-bake: `glb-to-ktx2.mjs` into `client/public/grand-allee/` (the new assets test fails on a
  WebP hotel or board, so it cannot slip).
- 4e: move the KTX2Loader setup and `/bauherr/basis/` to a shared place before Bauherr goes (comment in `grand-allee-assets.ts`).
- The games-shelf card bug (Android, not Grand Allee code).
- If wanted: WebP vs KTX2 on the box from one clean tree; the 0.4 s stall after the board appears; a box run with all ten
  hotels complete (needs a scripted scene, not bots).

## Scratch (not in the repo)

`/tmp/claude-1000/-home-andre-Workspace-spieltisch/e6a9c23c-c11a-465a-9550-b0c68a91b0e0/scratchpad/box-run/`: the bots
(`grand-allee-bots.mjs`), the load probe (`load-probe.mjs`), `gpumem.sh`, the beacon listener, every run's beacon and bot logs,
meminfo dumps and full-size PNGs; the installed APK `mediagram-0.163.1-curved-ktx2-beacon.apk`.
