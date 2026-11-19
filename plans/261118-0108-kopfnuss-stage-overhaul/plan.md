# Kopfnuss: stage overhaul with baked Cycles sets

User, 2026-11-18: "overhaul our 3d stages for kopfnuss. everything looks too cheap and equal. get better textures
use blender addons, nice shading effects" (with the blender-director skill).

Status: built, baked, wired into the TV. **Look approved by the user 2026-11-18** ("yes looks right").
Committed on `fix/kopfnuss-full-game` as Spieltisch 0.195.0 (not pushed, not released). Not measured on the
funstation.

## Why it looked cheap

Every surface was one flat Principled colour, nothing cast light or shadow, neon tubes lit nothing, and four of the
six stages shared one dark ring studio.

## What changed

- **Four sets, one per place**: Doors (Pick, Resolve), Quiz (Question, Reveal), Pyramid (Climb), Winner (Fact).
- **Real materials** in Cycles: Poly Haven `marble_01`, `walnut_veneer_02`, `velour_velvet` and the HDRI
  `wooden_studio_05` (all CC0), plus procedural gold, sequin glitter, lacquer, candy, neon.
- **Baked**: each set is joined, unwrapped and baked (COMBINED, from its stage camera, AgX) into one 2K atlas.
  The TV draws it unlit: soft shadow, bounce, glow spill and floor reflections cost one texture lookup.
- **Effects on top** (`arena-fx.ts`, additive, one clock): ring chase, marquee chase, column haze, spot beams, pad
  rings, door sparkle, trick energy, star halo, confetti. Blob shadows ground the cast.
- **Walnut host**: furrows as geometry, colour per vertex, still lit at runtime (it moves between sets).

## Kept from earlier decisions

Studio with answer-colour light rings (2026-11-10), ten candy tiers with rainbow and star (2026-11-16), four
ivory doors with gold leaves, walnut host, the six camera framings, every spot name the engine looks up.

## Changed without being asked

- Climb no longer stands in the dark studio: the pyramid is an island over clouds under a pastel sky.
  **Confirmed 2026-11-18** ("the Climb and the Fact keep their own places").
- Fact has its own stage: velvet curtain, walnut boards, gold sunburst. **Confirmed 2026-11-18**, same words.
- Pyramid has real treads (0.85 m): climbers stand on them instead of floating before a wall.
- Door crowns are lower, so the category names have clear wall above them.
- Fact: the winner's head token is hidden while the 3D winner stands there. Climb: header in dark ink.
- `kopfnuss-studio.blend` is no longer shipped in `public/`; the build saves a `.blend` to the cache instead.
- The MCP addon's Poly Haven library was switched on in the live Blender.

## Files

- Builder: `crates/mediagram-game/tools/kopfnuss-arena/` (`build_arena.py` entry, `kn_*.py`, `fetch_textures.py`).
- Shipped: `client/public/kopfnuss/kopfnuss-arena.glb` (4.5 MB: four WebP atlases, positions and UVs, no
  normals), `showmaster.glb` (0.6 MB). Before: 1.7 MB arena plus a 2 MB `.blend`.
- TV: `client/src/tv/kopfnuss/arena-fx.ts` (new), `arena-cast.ts` (new: avatars and their spots), `arena-engine.ts`,
  `arena-view.ts`, `kopfnuss.css`.
- Test: `client/tests/kopfnuss-arena-assets.test.ts`.
- Cache, out of git: `~/.cache/kopfnuss-arena/` (sources, baked atlases and layouts, `kopfnuss-stages.blend`).
- Before: `~/Workspace/backups/kopfnuss-arena-backup-261118-before-overhaul.tar.gz`.

## Rebuild

```sh
python3 crates/mediagram-game/tools/kopfnuss-arena/fetch_textures.py
cd crates/mediagram-game/tools/kopfnuss-arena && blender -b --python build_arena.py -- --bake   # ~8 min, CPU
blender -b --python build_arena.py -- --bake --rebake Doors    # one set again, the others from the cache
blender -b --python build_arena.py -- --bake --rebake none     # export again from the cache, seconds
```

A set is reused only while its geometry fingerprint, atlas size and sample count match; after changing a lamp
or a material, name the set in `--rebake`. Do not build spheres with `bmesh.ops.create_uvsphere`: it numbers
faces differently on every run, which put clouds and gumdrops on wrong texels (`kn_common.add_sphere` instead).

Preview and screenshots: `reports/preview-source/` (`bun serve-preview.mjs`, then `bun shoot.mjs <dir> pick climb:bare`).

## Verified (2026-11-18)

- Geometry QA (`reports/preview-source/qa_sets.py`, blender-asset-qa): Doors, Quiz, Winner pass. Pyramid: seven
  coplanar triangle pairs where cloud puffs overlap, accepted (white on white, 7 of 49k triangles).
- Triangles: Doors 17.6k, Quiz 47.4k, Pyramid 49.0k, Winner 7.7k (budget 60k per set).
- Client: 192 tests pass, `tsc` clean, production build passes. The asset test's first check fails on the old
  arena (no `Set_` nodes).
- Reuse path: an export from the cache has the full bake's exact size and renders the same; `--rebake Quiz`
  re-rendered only that set (63 s against 8 min).
- Code review: `reports/code-reviewer-261118-0108-kopfnuss-stage-overhaul-report.md`, 17 findings, 13 fixed.
- Looked at in the real `TvKopfnuss` at 1920×1080 and 960×540: `reports/kopfnuss-stages-before-after.jpg`,
  `kopfnuss-stages-all-six-with-game-text.jpg`, `kopfnuss-sets-without-game-text.jpg`.

## Not done, not checked

- Frame rate and memory on the funstation: screenshots are from desktop Chromium with software GL.
- The effects were seen as stills only; nobody has watched them move.
- Atlases are WebP (about 22 MB of GPU memory each uncompressed); KTX2 like Grand Allee's is not done.
- Eight players: sets have six spots, as before.
- From the review, open: the GL context is not force-lost on unmount (needs an engine-owned canvas, as the
  ceremony has).

## Decided by the user, 2026-11-18

"yes looks right.; keep walnet furrowed, the Climb and the Fact keep their own places"

1. The look of all four sets is right: glitter doors, quiz studio, cloud island, velvet stage. The effect
   colours stay as tuned (the review's sRGB question is closed by this).
2. Climb and Fact keep their own places.
3. The walnut keeps its furrowed shell.

After the verdict `arena-engine.ts` (300 lines) was split into engine (184) and `arena-cast.ts` (129), no
change in behaviour: 192 tests, `tsc`, build, and Pick, Question, Climb and Fact shot again in the real game.
