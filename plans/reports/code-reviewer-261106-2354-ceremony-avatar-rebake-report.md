# Code review: ceremony avatar rebake (correctness only)

Scope: `tools/rebake-texture-atlas.mjs` (new), `tools/pad-texture-islands.mjs` (fillGaps split), `tools/make-ceremony-avatars.mjs`, `client/src/tv/ceremony/ceremony-avatar.ts`, `client/src/tv/ceremony/ceremony-stage.ts` (all under `crates/mediagram-game/`). No code edited.

## Rebake: checked, no defect found

- **xatlas output lifetime.** `destroyAtlas()` runs before `index`/`oldIndexes`/`coords1` are used (rebake-texture-atlas.mjs:55). That is safe: xatlasjs 0.2.0 `getAtlas` copies all three out of the wasm heap (`new Uint16Array(HEAPU32.subarray(...))`, `new Float32Array(HEAPF32.subarray(...))`). Verified against unpkg `xatlasjs@0.2.0/dist/node/api.mjs`. Indices wider than 16 bits would be cut short by that copy, but the `vertexCount > 65535` check at :58 runs before any index is used.
- **`from` = -1.** This cannot happen. xatlas only creates output vertices that its index buffer references. Every corner is visited at :62-65, and the corner order is asserted at :63. If it ever did happen, `subarray(-es, 0)` would be empty and leave zeros silently. An assert would cost one line, but it is not needed.
- **Attribute remap (:94-100).** `old.constructor`, the element size, the type and the normalized flag are all carried over, so Uint8/Uint16 JOINTS and normalized WEIGHTS stay correct. TEXCOORD_0 is checked to be Float32 before the overwrite (:59, :98), and the lengths match (`vertexCount*2`).
- **Old UV per triangle.** The bake reads the old UVs of the triangle's own old corners, `idx[t..t+2]` (:74), not `from[]`. This is the right choice: it is what makes the welded seams correct.
- **Half-texel / v-axis.** Texel centres are at `x+0.5` against `uv*size` (:82), and `sample` uses `u*w-0.5` with clamping (:26). Both follow the same GL convention, so there is no half-texel offset. glTF v=0 is the top row and sharp's raw row 0 is the top row, so no flip is needed. The new UVs are used the same way for writing the map and for TEXCOORD_0, so the result is right whichever way xatlas orients y.
- **fillGaps.** Each texel is enqueued once, the `x` guard stops wrap-around at row ends, and the `q<0 || q>=w*h` check covers the top and bottom rows. Splitting it out of `padImage` changed nothing.
- **Leftovers.** `client/src/tv/` has no references to `"Idle"` or hip correction. `tools/render-avatar-images.mjs:113` still looks for `Idle`, but it reads `<id>-tv.glb`, which still has that clip, so it is unaffected.

## Findings

### 1. Medium: the avatar is rendered while its shaders compile, so the stall `compileAsync` should prevent still happens, and both figures show at once
`client/src/tv/ceremony/ceremony-stage.ts:136-139`
```ts
set.scene.add(holder);
await r.compileAsync(holder, set.camera, set.scene);
```
`holder` joins the scene before the await. Every rAF frame during the async link draws it. In three r170, drawing a program that is not ready reaches `getUniforms()`, then `onFirstUse()`, then a blocking `getProgramParameter` (WebGLProgram.js:1011-1033). The hitch the comment says it avoids just moves to that frame. Until the promise resolves, the flat sprite (`flats[i]`, hidden only at :145) and the 3D avatar are drawn on top of each other. Those stall frames also land in the adaptive-scale window before the `performers.length` reset, which can lower the scale for no reason.
Fix: move `set.scene.add(holder)` below the `if (disposed) return;` that follows the await. `compile(scene, camera, targetScene)` is meant for objects that are not in the scene yet: it collects lights from `targetScene` and builds programs for `holder` (WebGLRenderer.js r170:938-1016).

### 2. Low-Medium: the first adaptive-scale window counts a 0.1 s frame, so scale drops 1.5 → 1.25 when no avatar arrives within ~1.5 s
`client/src/tv/ceremony/ceremony-stage.ts:98-108`
`let last = 0` while `ms` is the page-relative rAF time (seconds or more). The first `dt` is therefore clamped to 0.1. Over 90 frames at 60 Hz that gives (0.1 + 89×0.01667)/90 ≈ 0.0176, which is above `SLOW_FRAME` 0.0172. Any device, however fast, then steps down to `MIN_SCALE`, unless an avatar `push` resets the count first. That happens in practice when no contender has an avatar, when all three load slowly or fail, or on desktop preview at DPR ≥ 1.5. The result is a softer picture than the device can afford.
Fix: `let last = performance.now() / 1000` (rAF timestamps share that timeline), or skip the first frame from the count.

### 3. Info: welding by position merges hard-normal splits
rebake-texture-atlas.mjs:42-48, :64. After `weld()`, a duplicate position kept for a normal crease rather than a UV seam is merged. Each new vertex takes the NORMAL of the first corner that reaches it, so one side of a crease gets the other side's normal. This is invisible on Meshy's smooth avatars under Lambert, and the box and headless renders confirm it. Only worth knowing if a hard-edged model ever goes through this tool.

## Not checked
- I did not re-run generation or the renders; they were already verified.
- I did not check meshopt's seam preservation inside gltf-transform `simplify`. The rebake assumes each triangle stays inside one island, and the renders show it does.
- I did not check `ceremony-set.ts` (`slotX`/`PLACES`) beyond how it is called.

**Status:** DONE_WITH_CONCERNS
**Summary:** I found no defect in the rebake: the remap, the `from` table, the texel-centre maths, the v-axis handling and the xatlas buffer lifetime are all correct. The two client findings are in the stage's draw loop: the avatar is added to the scene before `compileAsync` (a stall frame and both figures on screen at once), and the first frame-time window starts with `last = 0` (scale drops for no reason).
