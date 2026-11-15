---
title: "Every 3D avatar in KTX2 UASTC"
status: done on the branch, not merged
created: 2026-11-15
---

# Every 3D avatar in KTX2 UASTC

User, 2026-11-15: "yes make uastc the rule for all avatars". Until now only the Kopfnuss arena's planned copies were
UASTC (decided 2026-11-10); the awards ceremony, which every game ends with, shipped WebP.

## The rule

Every 3D avatar copy the TV draws (the ceremony, the Kopfnuss arena, Zündstoff 3D, any later game) uses KTX2 UASTC,
colour 2048 and normal 1024. UASTC stays compressed in GPU memory on any GPU, the funstation's included, so it is
sharper than WebP at the same memory. Not covered: the 15k `<id>-tv.glb` copies (`slim-avatar-for-tv.mjs`), which
only feed the offline 2D head and figure renders and never ship.

## Done (branch `feat/avatars-uastc`, Spieltisch 0.192.8)

- [x] `tools/make-ceremony-avatars.mjs` writes only UASTC; `--textures=webp` is gone; the default output is
  `android/spieltisch/src/main/assets/game-avatars/` (it still named the media app's folder).
- [x] `tv/ceremony/ceremony-stage.ts` reads them with a KTX2Loader (`/basis/` transcoder, as Grand Allee) and frees
  its workers once every avatar is in or failed. It is the only page that loads avatar GLBs.
- [x] The 22 ceremony copies remade (generated, gitignored): see Sizes.
- [x] Look: WebP and UASTC side by side for fox, panda and granny, full body and face
  (`reports/avatars-webp-vs-uastc.jpg`): PSNR 41–50 dB, no new seams, colour shifts or normal-map dents; the faceted
  patches on the panda's head are in both and come from the normal bake.
- [x] `bun test` 182 pass, `bun run build`, `cargo test -p mediagram-game` 605 pass.

## Sizes

| Avatar | WebP (MB) | UASTC (MB) |
|---|---|---|
| android | 1.8 | 3.8 |
| armour-hero | 1.9 | 3.7 |
| bear | 1.7 | 3.4 |
| bodybuilder | 2.1 | 4.2 |
| bunny | 1.8 | 3.6 |
| cat | 1.7 | 3.6 |
| dark-knight | 2.0 | 4.3 |
| fashion-doll | 1.8 | 3.9 |
| fox | 2.0 | 4.1 |
| frog | 1.8 | 3.7 |
| ghost | 1.6 | 3.2 |
| granny | 2.1 | 4.7 |
| hedgehog | 1.9 | 3.5 |
| mummy | 1.7 | 3.4 |
| panda | 1.7 | 3.3 |
| piglet | 1.7 | 3.4 |
| princess | 1.7 | 3.4 |
| raccoon | 1.8 | 3.6 |
| samurai | 2.1 | 4.3 |
| vampire | 1.8 | 3.5 |
| witch | 1.8 | 3.7 |
| yeti | 1.9 | 4.1 |
| **all 22** | **40.3** | **82.4** |

UASTC is 2.04 times the WebP size, 42.1 MB more. The `.glb` files are stored uncompressed in the APK
(`noCompress`), so the Spieltisch APK grows by the same: the 0.192.5 release APK was 78.4 MB, with UASTC about 120 MB.

## Not done

- Not merged into `main`, not installed on the funstation.
- Not measured on a GPU: the ceremony draws the same meshes, only the texture format changed.
