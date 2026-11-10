# Kopfnuss in a 3D studio arena

Every Kopfnuss stage on the TV plays in one 3D arena, the **Studio** look **with the shader effects** (user,
2026-11-10: "keep studio but with nice GLSL shaders"; `reports/arena-mockup-4-studio-with-shader-effects.jpg` and the
Studio clips): a dark stage, light rings and light columns in the four answer
colours, the players' avatars standing on pads. The text stays HTML on top (question card, answers, clock), as in the
mockups; the 3D is behind it.

Branch: `feat/dreifelder-on-games` (has Kopfnuss, Dreifelder and the games line's catalogue).

## Stages → arena

| Stage | Arena | HTML on top |
|---|---|---|
| Pick | category signs on podiums; a player who has voted gets a lit yellow pad | prompt card, clock |
| Resolve | the chosen sign rises into a spotlight, vote chips stack; tricks fly as pink arcs to their target, who reacts | trick names at the target |
| Question | avatars on their pads, trick badges over their heads | question card, four answers |
| Reveal | pads light in the colour each player picked; right: cheer or victory, wrong: shrug | answers with the right one marked, points under names |
| Climb | a stepped light tower to a flag; climbers stand on their step | Gipfelsturm and the clock |
| Fact | the fastest player on a pedestal in a spotlight, the others dimmed around | the fact card |
| Final | unchanged: the shared 3D awards ceremony | — |

## Neon variant (asked for 2026-11-10: "slightly a TRON arena")

The Studio with a neon grid, in a general neon-grid style (no designs from that film): cyan grid floor with waves of
light, a ring wall of dark panels with lit seams and a bright top edge, dark podiums, pads and tower steps with lit
edges, cyan edge light on the avatars; the four answer colours stay. Mockups 6–7 and two clips in `reports/`. Fewer
draw calls than Studio (fact 20 against 34). **Not chosen**: the user kept Studio with the shader effects (2026-11-10).

## Shader effects (mockups 4–5 and two clips in `reports/`, 2026-11-10)

Seven GLSL effects, each a few arithmetic operations per pixel, one shared clock uniform
(`reports/mockup-source/arena-fx.js`): LED floor with a light sweep, light chasing round the rings, light bands up the
columns, spotlights with dust, energy pulses along trick arcs, pulsing rings under lit pads, edge light on the avatars
(added to the matcap shader), confetti as one draw call moved in the vertex shader. Draw calls went down (53 → 44 in
the reveal) because the rings are one mesh each.

Dithering (`?dither=1` in the mockup): the backdrop is a shader gradient and every material ends with half an 8-bit
step of screen noise, which turns the bands in the dark backdrop and floor into a smooth ramp
(`reports/studio-dithering-banding.jpg`).

Rules, from what the box measured: no extra render passes (no bloom, blur, reflections, shadows, depth of field), no
half-float targets, no environment light; glow is drawn as additive geometry, not post-processing. Watch overdraw
from large additive planes; the spike measures the effects on and off.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [Arena avatars and the box spike](phase-01-arena-avatars-and-box-spike.md) — go/no-go on the box before any game code | pending |
| 2 | [Arena scene module](phase-02-arena-scene-module.md) | pending |
| 3 | [Kopfnuss on the arena](phase-03-kopfnuss-on-the-arena.md) | pending |
| 4 | [Box run, docs, version](phase-04-box-run-docs-version.md) | pending |

## Key facts (measured or read, 2026-11-09/10)

- Ceremony avatars: ~52k triangles each; three on the box run at ~42–44 fps with the matcap studio light
  (`ceremony-studio-look.ts`; real environment light ran 14–17 fps). Six of them in the mockup: ~316k triangles.
- TV copies exist out of git: `~/Workspace/mediagram/assets/game-night/avatars/<id>/<id>-tv.glb`, 15k triangles,
  KTX2 1K textures, but all 33 clips (4.4 MB each). `tools/slim-avatar-for-tv.mjs` makes them.
- Grand Allee shows 3D on the box at 58.6 fps (board, hotels, KTX2), its pawns are flat figures.
- Kopfnuss today (box, 361 s, bots): 2.5 % janky frames, p99 36 ms; Dreifelder 1.5 %, 28 ms.
- Mockup source: `reports/mockup-source/` (three 0.170, `arena.js`, `arena-themes.js`): start from it.

## Decided

- Arena avatar textures: KTX2 UASTC, colour 2048, normal 1024 (user, 2026-11-10: "take UASTC";
  `reports/avatar-quality-texture-compression-and-streaming.md`).

## Decisions needed (before phase 2)

1. Eight players: one arc of eight, or two rows (front four, back four)?
2. APK growth: 22 arena copies in UASTC are 83 MB (the APK is 106 MB). Ship them, or fetch each on first use?
3. Final: keep the shared awards ceremony (recommended), or a Kopfnuss podium in the arena?

## Risks

- Box frame rate with 6–8 skinned avatars plus the set: phase 1 measures it before anything else is built.
- Load time: up to eight avatar files when the game starts; load during the first Pick, flat figures until then
  (the ceremony's stand-ins).
- Text over a busy, bright background: the light columns sit behind the answers; dim or blur them under the HUD.
