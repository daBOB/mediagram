# TV box 3D framework spike — Godot vs Filament vs WebGL (2026-11-05)

Question: which 3D route can render a board game with player avatars smoothly on the TV box?
Box: Skyworth HPR302 Google TV, RTD1325, 4× A55 (32-bit userspace), Mali-G57, Android 14, WebView 158. HDMI was on an HP OMEN 27k at 3840×2160@60 during the runs (wm override 1920×1080; every app surface 1920×1080, upscaled by the compositor).

Scene (same in all engines): ground, 40 tiles, 4 houses, dice, 1 sun + ambient, orbiting camera, 1920×1080, no MSAA, no post-processing. Fox = Meshy avatar, "raw" 52k tris / 4K textures (28 MB glb), "opt" 15.6k tris / 1K textures (6 MB). 8 opt foxes are instances of one asset (one texture set). 5 s warm-up + 30 s measured per config.

## Frames per second (average; target 60)

| Config | Godot 4.6 Vulkan (Mobile) | Godot 4.6 GLES3 (Compat) | Filament 1.77 (GLES) | three.js in WebView 158 |
|---|---|---|---|---|
| 1 board only | 24.2 | **43.0** | 28.7 | **51.1** |
| 2 + 1 raw fox | 23.2 | 39.5 | 25.5 | 39.5 |
| 3 + 4 opt foxes | 21.8 | 36.0 | 24.6 | **41.5** |
| 4 + 8 opt foxes | 20.0 | 30.1 | 20.6 | **33.7** |
| 5 + 8 opt + shadows | 14.4 | **21.7** | 13.0 | 16.8 |

1 % lows: Godot GLES3 34/33/31/26/18; WebView 30/20/30/20/15; Filament 20/20/20/20/12; Godot Vulkan 19/19/18/17/14.
CPU per frame is small everywhere (Godot ~1.5 ms, Filament 0.6–2.4 ms, three.js 1.8–10.6 ms in cfg5) → GPU-bound.

## Memory, load time, size

| | Godot Vulkan | Godot GLES3 | Filament | WebView |
|---|---|---|---|---|
| RSS (MB, cfg4) | 299 | 235 | 197 | 208 app + 226 renderer ≈ 434 |
| PSS (MB, cfg4) | 204 | 141 | 106 | 78 app + renderer (n/a) |
| First frame (board) | ~13–16 s (preloads both foxes) | ~13 s | 0.8 s | 0.36 s after page load |
| Load raw fox / 8 opt foxes | in startup | in startup | 1.7 s / 0.16 s | 5.1 s / 1.1 s |
| APK size | 74.7 MB | 74.7 MB | 39.8 MB (11.4 MB without raw fox) | 0.7 MB host + page |

## Corrections made during the runs

- Godot foxes were first ~100× too big (AABB of the skinned mesh is 1/100 scale): first Godot numbers discarded, rerun with fixed scale 1.6.
- Filament first reported a flat 59.8 fps: it counted every vsync callback, including frames `beginFrame()` dropped. Rerun counts drawn frames only (skipped vsyncs: 1111 at cfg1 … 1659 at cfg5).
- Godot's headless export runs `adb kill-server` on exit (`export/android/shutdown_adb_on_exit`), which broke one WebView run; disabled in the spike-local editor config.

## Read

- No engine holds 60 fps even on the empty board at 1080p; the light scene sits at 24–51 fps. Per-pixel cost (plus 4K scanout) is the ceiling, not triangles or avatars.
- On this box: Godot GLES3 and three.js/WebView lead; Godot Vulkan and Filament trail. Vulkan is ~1.8× slower than GLES3 in Godot.
- Avatars cost little in Godot GLES3/WebView (8 opt foxes −13…−17 fps); shadows are expensive everywhere (−8…−17 fps).
- Lever to test next: render scale (3D at 720p or 960×540, UI at 1080p), cheaper shadows (baked/blob), 30 fps target.

## Decisions (user, 2026-11-05)

- Figures on the board are 2D sprites; 3D avatar models only on reaction, winning and losing screens.
- 3D route: three.js inside the existing TV page (WebView). No native Godot/Filament app.
- Look: matte over shiny ("i prefer the matte over shiny") — Lambert-style shading for board and avatars.

## Filament GPU time (getFrameInfoHistory)

Board 34.8 ms · +1 raw fox 39.1 · +4 opt 40.5 · +8 opt 48.6 · +shadows 76.7 ms per drawn frame. Real GPU cost, not frame pacing.

## Reaction / win screen in three.js (WebView 158, hardware "Mali-G57", WebGL 2 over GLES 3.0)

2 avatars close up (fox + frog, ~75 % of screen height), camera (0, 0.75, 2) fov 40, 2048 PCF shadow map, 1920×1080 backbuffer × pixel ratio. 5 s warm-up + 20 s measured, page reload per step.

| Avatars | Material | MSAA | Shadows | Pixel ratio | fps |
|---|---|---|---|---|---|
| 2K game copy (52k tris) | PBR | on | on | 1 | 8.5 |
| 2K | PBR | off | on | 1 | 9.8 |
| 2K | PBR | off | off | 1 | 15.4 |
| 2K | PBR | on | on | 0.67 | 9.9 |
| 2K | PBR | on | on | 0.5 | 10.9 |
| 2K | PBR | off | off | 0.67 | 19.0 |
| 2K | Lambert | off | off | 1 | 20.9 |
| slim (15.5k tris, 1K) | PBR | on | on | 1 | 14.4 |
| slim | PBR | off | off | 1 | 33.9 |
| slim | Lambert | off | off | 1 | **44.2** |
| slim | Lambert | on | on | 1 | 17.1 |

Read: resolution barely matters here; triangle count (×2.2), real-time shadows (shadow pass re-skins every avatar) and PBR shading do. Slim + Lambert + no shadow map ≈ 44 fps; blob shadow decal instead of a shadow map is the obvious next step. Screenshots `webgl/reaction-step{1..11}.png` (slim Lambert looks flatter, 1K textures slightly soft up close). Slim copies: `filament/assets-src/slim-avatar.mjs` (weld + meshopt simplify 0.3 / error 0.01 + 1024² textures, one clip).

### Reaction round 3: KTX2 copies (mediagram-39) and blob shadow

KTX2 copies = 15.0k tris, 1K KTX2 (ETC1S colour, UASTC normal), no metal/rough map, quantized vertices, all 33 clips (`/home/andre/Workspace/mediagram-stage-spike/results/slim/`, tool `tools/gltf/slim-avatar-for-tv.mjs`).

| Avatars | Material | MSAA | Shadow | fps |
|---|---|---|---|---|
| slim PNG | PBR | off | none | 34.0 |
| KTX2 15k | PBR | off | none | 35.7 |
| KTX2 15k | PBR | off | blob | 35.3 |
| KTX2 15k | Lambert | off | blob | **47.4** |
| KTX2 15k | PBR | on | blob | 32.5 |

Normal map on the matte (Lambert) KTX2 avatars, + blob, no shadow map: without 47.6 fps · with normal map 39.9 · with normal map + MSAA 38.1. The normal map smooths the visible facets of the 15k mesh (frog face, hoodie; `reaction-step17.png` vs `step18.png`) for ~16 % fps.

KTX2 + quantization buys speed only a little (+5 %); its gain is GPU memory (~2 MB vs ~67 MB per avatar, estimated by mediagram-39, not measured). Blob shadow is free. Lambert +34 %. mediagram-39: Meshy's island-heavy UV atlas stops seam-safe simplify at ~12.7k for the fox, and 12.7k looked clearly worse than 15k, so ~15k is the practical floor without a new unwrap + bake.

## Board look test (three.js in WebView, 1080p, 6 sprite figures with blob shadows, orbiting camera)

Sprites rendered once at load from the slim 3D avatars (384×512 render target, ~2.2 s for two). Wooden frame, felt, 40 textured tiles, 4 houses, spinning dice. Screenshots `webgl/board-look-step{1..9}.png`.

| Look | Shadows | MSAA | Pixel ratio | fps |
|---|---|---|---|---|
| basic (PBR) | shadow map every frame, PCF soft 2048 | off | 1 | 19.1 |
| static (PBR) | shadow map drawn once | off | 1 | 28.8 |
| matte (Lambert) | drawn once | off | 1 | 32.7 |
| toon | drawn once | off | 1 | 30.3 |
| rich (PBR + env map + ACES) | drawn once | on | 1 | 17.5 |
| rich | drawn once | on | 0.75 | 28.1 |
| **baked (Lambert)** | **painted into the felt texture, no shadow map** | off | 1 | **59.9** |
| **baked** | painted | **on** | 1 | **59.9** |
| baked | painted | on | 0.85 | 59.9 |

Read: on the board, sampling a shadow map (even a static one) is the big per-pixel cost; painting shadows/contact shading into textures (real version: Blender lightmap bake) gives a locked 60 fps with MSAA at full 1080p. "rich" exposure was not tuned (washed out).

## Race game wooden board, final-look test (three.js in WebView, 1080p, MSAA on)

User picked one world per board game from 4 live style mockups (wood / toy island / craft paper / low-poly candy): race game = wooden board on a table, life-path game = craft paper, party board = toy island; candy dropped. Test scene: classic 11×11 cross, 40 fields + home lanes + yards printed into one 2048² board texture on a Poly Haven cherry veneer (CC0), dark wood frame, lacquered table, 16 animal sprites on lathe bases, rolling die, blob shadows, 4 player tags + turn banner. Page `webgl/race-board.html`, runner `run-race-board-on-box.sh`, box shot `webgl/race-board-step44.png`.

| Step | Change | fps | 1 % low | frames > 20 ms |
|---|---|---|---|---|
| 31 | first version: HTML vignette + HTML tags/banner | 52.6 | 29.9 | 139 |
| 32 | same, MSAA off | 55.9 | 29.9 | 79 |
| 33 | no HTML vignette | 57.8 | 29.9 | 36 |
| 34 | no vignette, no tags/banner | **59.7** | **59.5** | 4 |
| 35 | step 34 + anisotropy 2 | 59.0 | 30 | 17 |
| 36/37 | vignette baked into table vertex colours, HTML tags without shadow/transitions | 56.8 / 57.3 | 29.9 | 61 / 52 |
| 38/39 | tags/banner drawn in the WebGL canvas, new texture per change | 55.7 / 55.4 | 29.9 | 74 / 82 |
| 40 | + table and board face unlit (MeshBasic, light baked into colour) | 57.1 | 29.9 | 43 |
| 43 | step 40, MSAA off | 58.8 | 30 | 17 |
| 45 | every HUD state pre-drawn + initTexture at load, Lambert flat surfaces | 57.2 | 29.9 | 54 |
| **44** | **pre-drawn HUD + unlit flat surfaces** | **59.8** | **59.5** | **2** |
| 46 | step 44 at pixel ratio 0.85 | 59.8 | 59.5 | 3 |
| 47 | step 44, die rolls on the table beside the board (user: it clipped through the figures in the middle) | 59.8 | 59.5 | 2 |

(41/42 lost: display dozed off mid-run; runner now sends KEYCODE_WAKEUP before each step.)

Read: the 3D board alone locks 60. The box GPU sits just under 16.7 ms, so anything extra tips frames over: a full-screen HTML layer over the canvas (−5 fps), HTML tags (−2 to −3 fps plus drops), a texture created mid-game (stall per change), Lambert on big flat surfaces (−2.6 fps vs unlit). Rules for the board scenes: no HTML over the canvas during play, HUD states pre-drawn and uploaded at load, big flat static surfaces unlit with the light baked in (matches Lambert within 0.2 % brightness). Load: first frame ~7.5–10.6 s after page start (4 animal sprites baked from the KTX2 avatars + textures).

## Life-path game craft paper board, final-look test (three.js in WebView, 1080p, MSAA on)

Road start → fork (university loop / workshop shortcut) → dream house, ~60 printed spaces, paper landscape + lake + river in one 2048×1180 board texture, folded-card houses/uni/office/villa, 30 crossed tree standees (instanced), 2 mountain layers, flat paper spinner, 6 sticker standee animals, banner + 6 tags along the bottom. Page `webgl/paper-board.html`, box shot `webgl/paper-board-step60.png`. Built with the race-board rules from the start.

| Step | Change | fps (stall-corrected) | frames > 20 ms |
|---|---|---|---|
| 50 | first version: mountains + trees as alpha-tested textures | ~49.6 (37.7 raw) | 156 |
| 51 | same, MSAA off | ~59.2 (45 raw) | 7 |
| 52 | no mountains | ~55 | 85 |
| 53 | no trees | ~57 | 52 |
| 54 | neither | ~59.8 | 5 |
| 55/56 | mountains + trees as vertex-coloured shapes (no alpha test), clock starts at frame 10 | 58.5 / 58.4 | 27 / 30 |
| 57 | step 55 without trees | 59.8 | 3 |
| 58 | step 55 without mountains | 58.6 | 27 |
| 59 | step 55, MSAA off | 59.8 | 3 |
| **60/61** | **trees at 16 points per crown circle (was 48), ~16k tris instead of ~42k** | **59.7 / 59.7** | **4 / 5** |

Read: every run 50–54 had one ~6 s first-frame stall (shader compiles + uploads) inside the measured window because the clock started at frame 1; the runner pages now start it at frame 10 and log SECONDFRAME. Alpha-tested cut-outs are the expensive thing under MSAA on this Mali (big mountain planes −4.6 fps, 60 tree quads −2.6); as geometry the mountains cost nothing and the trees only their triangle count. Load: LOAD (6 sprites + textures) ~12 s, first frame ~20 s after page start: the 2048 canvas print with blur filters + tree scatter run on the box CPU; a real game would bake the board print once (or ship it as an image) and keep the sprites.

Paper look confirmed final by the user 2026-11-06.

## Party game toy island board, final-look test (three.js in WebView, 1080p, MSAA on) — 2026-11-06

Big island + lighthouse island + 2 palm islets + sandbank, ~50 stepping stones (instanced per kind, icon in the opaque cap texture), loop over 2 plank bridges (instanced planks/posts/ropes) + shortcut, painted ground 2048×1098 + painted sea 1024×655 (both unlit), cliff rings, 15 round trees + 14 palms (real leaf shapes, instanced), 4 houses, lighthouse, chest + turning gem, toy die landing on the sandbank, boat, 6 animal sprites on lacquered bases, floating "+3" sprite on landing, banner + 6 tags. Page `webgl/island-board.html`, box shot `webgl/island-board-step72.png`. Built to the race/paper rules from the start; soft shadows as radial gradients, no blur filters.

| Step | Change | fps | 1 % low | frames > 20 ms | first frame after page start |
|---|---|---|---|---|---|
| 70/71 | first version: grain as ~140k single dots into the 2048 ground canvas (through a clip path) | — (no frame within 120 s; LOAD at 9.4 s) | — | — | > 120 s |
| **72/73** | **grain as one 256 px dot tile filled as a pattern; MARK timings** | **59.8 / 59.9** | **59.5 / 59.5** | **3 / 1** | **12.8 / 12.0 s** |

Read: the island scene costs the GPU nothing beyond the other two boards (CPU 8 ms per frame). Load after the sprites (LOAD 9.3 s): sea print 0.06 s, ground print + cliffs 0.4 s, props 0.1 s, HUD pre-draw 0.4 s, then ~2 s to the first frame (shader compiles + uploads). The single-dot grain was the whole stall: per-dot fillStyle parsing + clipping on the A55 CPU. Rule: grain/noise on big canvases as a repeated small tile, never per-dot over the whole canvas; gradients instead of blur filters.

Island look confirmed final by the user 2026-11-06 — all three board worlds (wood race, paper life-path, toy island party) are settled and each locks 60 on the box.

## Heimlauf, the playable race game (real game night `/tv` page) — 2026-11-06

The race look test turned into the game (plan `plans/261106-0907-race-game/`): host rules + turn flow in Rust, the TV board as a lazily loaded three.js chunk in the game client, turned pawns instead of animal sprites. Measured with the real page from the dev host in the spike WebView (adb reverse, so it is loopback), three scripted phones playing, 30 s after a 5 s warm-up, frame times read inside the page over WebView remote debugging:

| Build | fps | 1 % low | frames > 20 ms | board up after start |
|---|---|---|---|---|
| first | 59.9 | 59.5 | 3 (max 33 ms) | 3.4 s |
| marker shaders compiled at load (`renderer.compile`) | 60.0 | 59.5 | 0 (max 20 ms) | 3.5 s |

Read: without sprite baking the board comes up in ~3.5 s (the look test needed 7.5–10.6 s for 4 baked animals). Compile every material's shader at load, including ones only shown later (move markers), or the first use stalls the Mali.

## Pending
- mediagram-39 heavy case (8 *different* avatars = 8 texture sets, 52k tris, 2K uncompressed, MSAA 4×, 2048 shadows), board → 1 → 2 → 4 → 8 avatars:
  - Godot Vulkan: 38.9 / 35.5 / 32.5 / 27.6 / 20.8 fps, PSS 195 MB
  - Godot GLES3: 45.0 / 39.6 / 35.8 / 29.6 / 22.8 fps, PSS 153 MB (~2.5 ms per 52k avatar; CPU 13–16 ms with avatars)
  - Filament (shadows were broken, sweep pending): 25.4 / 16.4 / 13.6 / 8.4 / 4.9 fps
  - Filament sweep incl. GPU time and 0.5 render scale: pending.

## Files

Scratchpad `…/ad211121-…/scratchpad/spike3d/`: `godot/` (logcat-*, shot-*, mem-*), `filament/out/` (logcat-1608.txt = drawn-frame run), `webgl/` (run.log, webview-cfg*.png, mem-*), runners `run-*-on-box.sh`. mediagram-39: `/home/andre/Workspace/mediagram-stage-spike/results/`.

## Unresolved questions

- Is Filament's ~33 ms per frame real GPU time or FrameSkipper being cautious on this Mali?
- Does a 0.5–0.67 render scale bring 8 avatars + shadows to 60 (or a steady 30)?
- The mediagram app is no longer installed on the box (not by either session); reinstall is the user's call.
