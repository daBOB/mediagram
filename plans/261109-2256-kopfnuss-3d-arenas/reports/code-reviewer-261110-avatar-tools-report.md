# Code review: avatar build tools (face/hands simplify, AO bake, full-detail normal frames, uastc output)

Scope: crates/mediagram-game/tools/{simplify-favouring-face-and-hands, bake-ambient-occlusion, full-detail-normal-frames}.mjs (new), {rebake-texture-atlas, pad-texture-islands, make-ceremony-avatars}.mjs (diff). Offline pipeline only.
Method: read and checked the math by hand, then ran probes from the scratchpad against the fox (`~/Workspace/mediagram/assets/game-night/avatars/fox`). No project files changed.

## Findings (ranked)

### 1. HIGH, real bug (silent wrong output): `--textures=uastc` turns both maps into 256-colour palettes before KTX2
`make-ceremony-avatars.mjs:119-120`. When the target is `png`, gltf-transform passes `quality` to sharp's `png()`. In sharp, any `quality` turns on `palette: true`, which runs libimagequant and keeps at most 256 colours. The KTX2 encoder then starts from those quantised PNGs.
- Checked: textureCompress png with quality 90 gives PNG colour type 3 (palette). Without `quality` it gives type 6 (RGBA), lossless.
- Fox at --tris=20000 --detail=1.5, rebaked maps with and without `quality`:
  - colour: PSNR 41.3 dB, max error 57/255
  - normal map: mean error 1.03°; 5620 texels off by more than 10°, 231 by more than 30°, worst 158°
- Fix: send `quality` only for webp, e.g. `...(FORMAT === 'webp' && { quality: 90 })` (and 95 for normals). Or leave out `targetFormat` in uastc mode, since the rebake already writes PNG.

### 2. MEDIUM, real bug (crash on plausible input; can leave a wrong file): a relative `--out` breaks `--textures=uastc`
`make-ceremony-avatars.mjs:40, 127, 133`. `io.write(out)` resolves `out` against the process cwd. `spawnSync('bunx', [..., out, out], { cwd: client/ })` resolves the same path against `client/`. So the CLI finds no file, or finds a stale one under client/. Either way the run throws with the misleading hint "is ktx on PATH?". The half-finished GLB with PNG textures stays at `out`. With the default OUT_DIR, that is the Android assets directory the app ships.
- Fix: `OUT_DIR = path.resolve(opt('out', ...)) + '/'`. Better still, write to `out + '.tmp'` and rename only after the read-back checks pass.

### 3. LOW, real but rare (wrong-surface normal texels): a full-frame lookup can land on another UV island
`full-detail-normal-frames.mjs:21-22`, used at `rebake-texture-atlas.mjs:133`. Simplification shortens texture seams, so a simplified triangle can cover UVs outside its own island. Where that lands in a neighbouring island's cell, the grid returns that island's triangle: its normal, T and B belong to an unrelated part of the body.
- Fox at 20k / detail 1.5: 2,629,018 hits. 56 of them are more than 0.02 of the height from the simplified point (worst 0.89, the far end of the body), and 39 of those have normals more than 60° apart. Expect a few isolated specks along chart borders.
- A normal-angle test is the wrong filter: 2664 near hits also differ by more than 60°, and those are the detail this feature exists to keep.
- Fix: have the closure also return the full-mesh point (3 more lines). In the rebake, interpolate the simplified point from `ps` with w0..w2 and fall back to the simplified frame when the two are more than ~1% of the height apart.

### 4. LOW, theoretical (crash): pass-2 target can exceed the index count
`simplify-favouring-face-and-hands.mjs:46-49`. meshoptimizer asserts `target_index_count <= indices.length`. If keepFace = faceTris - 1 and pass 1 undershoots its target by 2 or more triangles, pass 2 throws. Probed 41 targets around the face=1 boundary (boost 1.5) and 150 targets at boost 1.05 on the fox: no throws. Fix: `Math.min(triangles * 3, indices.length)` in `pass`.

### 5. LOW, theoretical: `--padding` is not validated
`make-ceremony-avatars.mjs:31`. A typo gives NaN, which goes to xatlas with no error. Add it to the existing positive-number check.

## Checked and correct (no change needed)
- **full-detail barycentrics:** Cramer solve of (u,v) = a + wb·d1 + wc·d2 is correct. The clamp and renormalise keep the point on the owning triangle. The closure returns false before writing anything, so the fallback never sees half-written frames.
- **Frame math:**
  - `frame(e1,e2,d1,d2)` pairs edges with UV steps the same way in both callers.
  - It equals three's perturbNormal2Arb up to a positive factor N·(e1×e2) for CCW triangles, which the max-length normalisation removes.
  - moveNormal reads with (scale, -scale) in the source frame (To, Bo, nf), solves with Cramer in the destination frame (Tn, Bn, nt) and writes for (1, -1). Sign conventions are consistent, and so is the setNormalScale(1) that follows.
- **BVH:**
  - Fox (51,895 triangles): depth 15, every triangle in exactly one leaf, node count at most 2·nt-1, so the arrays fit. STACK 128 is far more than needed.
  - 3000 random rays checked against brute force, with zero direction components, axis-aligned directions and origins on box planes: 0 mismatches.
  - The NaN from 0·∞ in the slab test makes `>` false, so the node is visited rather than culled (conservative).
  - The tMin/tMax handling and Möller–Trumbore are correct.
- **Budget arithmetic:** keepFace is within [0, faceTris] and keepBody within [0, bodyTris]. Neither target goes negative or above the index count. The fox hits exactly 20000, and pass 1 did not take triangles from the face. At 12k, seams set the floor (13495; the default path stops at 12663). That leaves the face denser than planned, which does no harm.
- **AO:** fillGaps with 1 channel is fine. The weights w0..w2 map to idx[t..t+2] correctly. The sRGB gain g^(1/2.2) is right.
- **uastc pipeline:** a second meshopt() pass (the CLI step) only reorders: the vertex multiset and IBMs are identical. The read-back catches lost meshopt compression, a wrong mime type and missing clips.
- **pad-texture-islands:** the new Float32Array check is safe for both callers (make-ceremony and slim-avatar-for-tv both pad before quantizing).

## Unresolved
- I did not run the full uastc CLI path end to end (ktx); finding 1 was measured on the PNG intermediate.
- Bias from AO rays near the horizon (up ≈ 0.09) hitting neighbouring faces on noisy flat areas was not measured. It is at most 3/64 of the openness, an accepted AO artefact.
