# Game night: awards ceremony with the 3D avatars

User, 2026-11-06: "lets add a awards ceremony after the game with the avatars"; when the game ends, together with the points we already show ("together"); layout **B** chosen from two sketches (`.superpowers/brainstorm/1997754-1793988591/content/awards-layouts.html`); avatar files **"Inside the app, once"**. Earlier decision (2026-11-05): 3D avatars appear on winning/reaction screens, figures on the board stay 2D.

## Decided by the user
- Ceremony and points on one screen, layout B: title on top, podium centre stage, points strip along the bottom.
- 3D avatar files ship inside the app, one copy (not embedded per ABI with the web client).

## My calls (shown on the sketch page, not objected to)
- Replaces today's final ranking for every game (quiz, poster, Heimlauf); stays until "Zurück zur Lobby" (button and its OK behaviour as today).
- Top three in 3D on a podium (gold/silver/bronze blocks, matte); with two players a podium of two; everyone in the strip with head, place and points (Heimlauf: pieces home).
- Animations: winner `victory` then `Happy_Jump` now and then, 2nd `Cheer_With_One_Hand_Up`, 3rd `Shrug`, back to `Idle` between. Confetti in the 3D scene; the winner jingle (`/race/sound/winner-jingle.ogg`) once, through the TV settings (mute, effects level).
- Until a 3D avatar is ready (or if its file is missing) its flat figure (`/avatars/<id>-figure.webp`) stands on the podium.
- Box rules: no HTML over the WebGL canvas (the canvas is the middle band; title and strip sit above/below it), shaders compiled and textures uploaded before the first frame, matte Lambert materials; render scale lowered if 3 skinned avatars cannot hold ~60 fps.
- Ceremony copies: from `assets/game-night/avatars/<id>/<id>-tv.glb` (15k tris, KTX2) keep only `Idle`, `victory`, `Happy_Jump`, `Cheer_With_One_Hand_Up`, `Shrug`, drop the normal map → `android/app/src/main/assets/game-avatars/<id>.glb`, generated (gitignored) like the native libraries; `assets/` is never staged.
- Delivery: the TV app's WebView answers requests for `/game-avatars/<id>.glb` from its APK assets (`shouldInterceptRequest`), same origin as the TV page; the Rust host and the phones never serve them. KTX2 transcoder (`three/examples/jsm/libs/basis`) in `client/public/basis/`.
- Version: next bump skips 0.147/0.148 (main is at 0.148.0) → 0.149.0.

## Phases
- [x] A Avatar files + delivery: `tools/make-ceremony-avatars.mjs` (5 clips, no normal map, meshopt `level: high`, self-check), gitignore, `TvGamesFrame.kt` `AvatarAssetClient` (GET `/game-avatars/*.glb` from APK assets, 404 otherwise), Kotlin compile + `mergeReleaseAssets` ✓. First run 64.8 MB (float32 keyframes) → with meshopt 26.2 MB (1.14–1.33 MB each); fox loads and plays `victory` in three.js 0.170 headless with MeshoptDecoder (24 bones, 5 clips, no errors)
- Merge: user chose (AskUserQuestion, 21:40) "After the ceremony": then commit the game-night work, merge main (038638c0, 0.148.0, artwork progress + Deinterlace), renumber to 0.149.0+, rebuild core, box, tell the main session.
- [ ] B Ceremony screen: `TvFinal` → layout B, lazy three.js chunk (podium, avatars, animations, confetti, jingle, fallback), basis transcoder in public, dev check with request interception
- [ ] C Lead: core + app build, box install, fps with 3 avatars, tune; version 0.149.0, changelog, architecture

## File ownership (A and B in parallel)
- A: `crates/mediagram-game/tools/make-ceremony-avatars.mjs`, `android/app/src/main/assets/game-avatars/**` (generated), `.gitignore`, `android/ui-tv/src/main/kotlin/ui/tv/games/TvGamesFrame.kt`, a script entry if needed (`scripts/`), client `package.json`/`bun.lock` only for gltf-transform dev dependencies
- B: `crates/mediagram-game/client/src/tv/TvFinal.tsx`, `client/src/tv/ceremony/**` (new), `client/src/tv/TvApp.tsx` / `TvRace.tsx` only where TvFinal is used, `client/public/basis/**`, CSS in its own file under `tv/ceremony/`
- Lead: version, docs, plan, box.
