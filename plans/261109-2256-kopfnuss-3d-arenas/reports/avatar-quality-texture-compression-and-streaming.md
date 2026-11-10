# Avatar quality: packing, GPU texture compression, streaming (2026-11-10)

Tested on the fox (`make-ceremony-avatars.mjs`, nine arena clips, 20k triangles), rendered in the Studio mockup's fact
close-up in headless Chrome, which reports ASTC like most TV GPUs. Not yet on the box (offline).

## Results

| Version | Colour / normal | Charts cover | File | GPU memory | Look (`avatar-texture-webp1k-uastc2k-etc1s2k.jpg`) |
|---|---|---|---|---|---|
| Arena set today | WebP 1024 / 1024, gap 6 | 43 % | 1.53 MB | ~10.7 MB (RGBA8) | baseline |
| UASTC | KTX2 UASTC 2048 / 1024, gap 4 | 64 % | 3.64 MB | ~6.7 MB (ASTC 4×4) | sharpest: drawstrings, stitching, tail fur |
| UASTC, RDO λ 4 | as above | 64 % | 3.46 MB | ~6.7 MB | not compared; 5 % smaller is not worth it |
| ETC1S colour | KTX2 ETC1S 2048 / UASTC 1024, gap 4 | 64 % | ~2.9 MB (est. with meshopt) | ~4 MB (ETC1) | about today's level, softer than UASTC |

Ceremony set today: WebP 2048 / 1024 at 52k triangles, ~26.7 MB GPU memory each (decoded to RGBA8).

## What it means

1. **Packing**: the 6-texel gap between charts wasted most of a 1024 map. 4 texels at 2048 (block-aligned for KTX2):
   64 % coverage instead of 43 %, half again as much texture on the avatar for free. Larger xatlas charts
   (`maxCost`, weights) changed nothing. `--padding` is now an option of the tool.
2. **GPU compression**: KTX2 UASTC at 2048 is visibly sharper than WebP at 1024 and still takes ~40 % less GPU memory;
   compressed textures are also cheaper to sample, which matters on the box's GPU. Grand Allee already uses KTX2
   (transcoder in `client/public/basis/`).
3. **Size**: 22 avatars in UASTC 2048 ≈ 77 MB, against 31 MB for the WebP 1024 arena set and 41 MB for today's
   ceremony set. Putting both sets in the APK in UASTC would grow it by ~125 MB.
4. **Streaming** answers the size: the box needs at most eight avatars a night. Ship none (or the flat figures only)
   and fetch each avatar file once, the first time a player picks it, from the household's server or the update
   channel, cached on the box; the flat stand-in shows until it is there (the ceremony already does this). Streaming
   inside a file (smallest mip levels first) is possible with KTX2 but needs a custom loader; not worth it for files
   read from local storage or the LAN.

**Decided (user, 2026-11-10): UASTC 2048 for the arena** ("take UASTC"). `make-ceremony-avatars.mjs --textures=uastc`
now writes it itself (gltf-transform's command line in a second process, then meshopt again: its write drops it).

## Three more techniques, tried (2026-11-10 afternoon, user: "try these out")

Fox, 20k triangles, UASTC 2048 / 1024, gap 4, nine arena clips; renders in the Studio mockup's fact stage
(`?zoom=5.5&clip=Idle_15` face close-up, `?zoom=2.2` full figure).

| Technique | Option | Result | Cost |
|---|---|---|---|
| Face and hands denser than the body | `--detail=1.5` | uniform 20k: dark smudges by the eyes, a dented nose; ×1.5 and ×2 close to the 52k copy. ×2 lost the hoodie pocket (body 4.7k triangles); ×1.5 keeps it (face and hands 14.2k, body 5.8k) | none: same triangle count |
| Normal map baked against the full mesh | always, when simplifying | the old rebake applied the 4K normal map to the simplified surface; now each texel reads it in the full mesh's own frame, found through texture space (simplifying keeps the seams). The pocket and the hem come back; the uniform copy's nose and eye smudges mostly go | ~2 s a build, 64 MB while building |
| Ambient occlusion in the colour map | `--ao=0.7` | per vertex on the 52k mesh, 64 rays reaching 12 % of the height, drawn into the 4K map before the rebake: shade under the chin on the hoodie, inside the ears, round the eyes, between fingers, under the bucket. Subtle at play size, gives depth | ~2.5 s a build, +0.1 MB a file |
| Dithering in the Studio | mockup `?dither=1` | the backdrop as a shader gradient (an 8-bit canvas texture is stepped already) and half an 8-bit step of screen noise on every material: the bands in the backdrop and floor become a smooth ramp, invisible at normal contrast | one line a shader |
| 12k triangles | `--tris=12000` | faceted ears, light slivers, a broken nose, with or without weighting: **12k is out** | — |

Sheets: `avatar-face-weighting-and-full-detail-normals.jpg`, `avatar-12k-faces.jpg`, `avatar-ambient-occlusion.jpg`,
`studio-dithering-banding.jpg`. All 22 avatars built with the recommended options in ~5 minutes: 19,996–20,000
triangles each; after the review's palette fix 3.1–4.7 MB, **83.2 MB** together (77.1 before it) (face and hands from 19 % of the triangles on the bodybuilder to 67 %
on the witch, whose hat counts as head).

Watch: the AO reports a fifth of the fox's vertices fully enclosed (mean openness 0.51): hidden inner surfaces of
Meshy's mesh (popcorn inside the bucket, overlapping shells), not visible; nothing visible came out black.

## Code review (2026-11-10, `code-reviewer-261110-avatar-tools-report.md`) and fixes

- **Fixed, mattered:** in UASTC mode the maps went to the KTX2 encoder as PNG written with a `quality` setting, which
  sharp turns into a 256-colour palette (colour PSNR 41 dB, normal texels up to 158° off). Every UASTC file above,
  including the first UASTC-vs-WebP test, carried it; the comparisons stay fair (same handicap on every UASTC
  variant) but understate UASTC. Full-colour maps now: smoother cheeks and hoodie (`avatar-uastc-palette-fix.jpg`),
  the fox 4.35 MB instead of 3.98.
- Fixed: a relative `--out` broke the KTX2 step (run in the client's folder) and left a half-made file; the output
  folder is absolute now and each avatar is built as `<id>.part.glb` and renamed only after the read-back checks.
- Fixed: the full-mesh normal lookup could land in a neighbouring island of another body part where simplifying
  shortened a seam (56 of 2.6M texels on the fox); it now falls back when the two surfaces are more than 1 % of the
  height apart. Also: the second simplify pass's target is capped at the index count; `--padding` is validated.
- Checked correct by the reviewer: barycentrics, tangent frames and normal-scale signs, the BVH (3,000 rays against
  brute force), the triangle budgets, one-channel gap filling.
- After the fixes the default ceremony build is still byte-identical to the shipped files.

## Decisions for the user

- Arena copies: `--tris=20000 --detail=1.5 --ao=0.7 --padding=4 --textures=uastc` (recommended)?
- Ship avatars in the APK, or fetch them on first use?
- Switch the ceremony's textures to UASTC too (its geometry stays at 52k: the user found 30k low-poly)? AO would
  work there as well (`--ao=0.7`); the full-detail normal bake only matters for reduced copies.
