# Phase 1 — Arena avatars and the box spike

**Priority:** first; nothing else starts before its numbers. **Status:** pending.

## Goal

Know whether the TV box draws the Studio arena with eight avatars smoothly, and with which avatar copy, before any
Kopfnuss code is written.

## Steps

1. Arena avatar copies: `tools/make-arena-avatars.mjs` (or a `--clips` / `--tris` option on `slim-avatar-for-tv.mjs`,
   whichever is less code), from `<id>.glb` (2K game copy, out of git): ~12k triangles, colour 1024 and normal 512,
   meshopt, and only the clips the arena plays: `Idle_15`, `Happy_Jump`, `Shrug`, `victory`, `Cheer_With_One_Hand_Up`,
   `Headache_Relief` (a trick lands), `Falling_Down` (a trick bounces back), `Angry_Ground_Stomp`, `Catching_Breath`
   (climb). Output `android/app/src/main/assets/game-avatars/arena/<id>.glb` (gitignored like the ceremony copies).
   Record file sizes; WebP or KTX2 by what the box draws faster (the ceremony uses WebP, Grand Allee KTX2).
2. Spike page in the client, dev only: the mockup's Studio set (from `reports/mockup-source/`) with eight arena avatars
   playing idles, the ceremony's matcap look, a frame log like Grand Allee's (`FrameLog`). Built into a test APK.
3. On the box (192.168.0.37, coordinate with the games session first): fps and slow frames over 60 s for 6 and 8
   avatars, at 1920×1080 and at 0.75 render scale, each with the shader effects off and on (and per effect where one
   costs more than 2 ms); load time of eight avatars; GPU memory (`dumpsys gpu --gpumem`).

## Go / no-go

- Go: ≥ 50 fps median and < 2 % frames over 25 ms with eight avatars (Grand Allee's board reached 58.6 fps, 0.4 %).
- Otherwise: fewer triangles (8k), half render scale for the 3D layer, or avatars only for the players in focus
  (the others as the ceremony's flat figures). Report numbers and options to the user before phase 2.

## Output

`reports/box-spike-arena-avatars.md` with the numbers, file sizes and APK growth; the spike page is deleted or kept
dev-only.

## Done so far (2026-11-10)

- `make-ceremony-avatars.mjs` takes `--in` and `--clips`; both arena sets made with the nine clips into
  `game-avatars/arena/` (12k triangles, normal 512: **26.65 MB** for 22) and `game-avatars/arena20/` (20k, normal 1024:
  **31.13 MB**). Six avatars in the arena: ~316k triangles with the ceremony copies, ~75k at 12k, ~125k at 20k.
- Close-up (`reports/arena-avatar-detail-52k-52k-20k-12k.jpg`, fact stage): 20k is hard to tell from the 52k
  ceremony copy; 12k shows facets and a jagged tail. **20k is the default**; the box decides if 12k is needed.
- The edge light uses the smooth surface normal, not the normal map (the reduced map lit patches inside the face).

- Texture quality (`reports/avatar-quality-texture-compression-and-streaming.md`): UASTC 2048 chosen by the user. Then
  tried at the user's request: face and hands ×1.5 denser than the body, the normal map baked against the full mesh,
  ambient occlusion in the colour map, dithering in the Studio. All help; 12k triangles is out. Recommended arena
  copy: `--tris=20000 --detail=1.5 --ao=0.7 --padding=4 --textures=uastc` with the nine clips: 3.1–4.7 MB an avatar, 83 MB for 22.

## Todo

- [x] arena avatar copies (22) and their sizes
- [x] texture compression, face weighting, full-detail normals, AO, dithering tried; UASTC chosen
- [ ] user: confirm the recommended arena copy settings; APK or fetch on first use
- [ ] spike page
- [ ] box numbers (6 and 8 avatars, two render scales), load time, GPU memory
- [ ] go/no-go with the user
