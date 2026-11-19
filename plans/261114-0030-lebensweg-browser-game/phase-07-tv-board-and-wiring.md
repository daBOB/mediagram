# Phase 7 — TV board and wiring

Status: pending · Depends on phases 3–6

## Context
- Prototype: one 1,117-line HTML file; split by concern into `client/src/tv/lebensweg/` modules under 200 lines,
  like `client/src/tv/grand-allee/`.
- Dispatch: `client/src/tv/TvApp.tsx`, `src/protocol.rs` (`TvScene`), `src/factory.rs`, `src/catalogue.rs`
  (2–6 players), `GameKind`. Phone: existing `Sheet` (`client/src/phone/PhoneSheet.tsx`).

## Steps
- [ ] First rebase `feat/lebensweg` onto main (0.191.0 or later: lobby Tempo and length): this phase touches `catalogue.rs`
      and `factory.rs`, and main also changed `Cargo.toml` and `lib.rs`, which the branch modifies.
- [ ] Tempo: Lebensweg goes in the `true` arm of `timed()` (catalogue.rs, an exhaustive match), like Grand Allee.
      `LebenswegConfig::at(speed)` runs only the phone decision clock through `speed.stretch(ms)`; spin, walk and
      news timings stay. factory.rs builds `LebenswegConfig::default().at(speed)`; add its clock to `clocks()` in
      `src/game_speed_tests.rs`. Model: `grand_allee/live_config.rs`.
- [ ] Lobby length (main 0.191.0): `sized()` in catalogue.rs is a second exhaustive match. Lebensweg is played to
      its end, so `false`, unless the user wants Short / Normal / Long to pick the board's stretch (`more`).
- [ ] `GameKind::Lebensweg`, catalogue entry, factory, `TvScene::Lebensweg`, ts-rs types exported.
- [ ] TV modules: terrain + cliffs, road + fields (from the host's field list), waterfall, district, landmarks,
      cars with the seated avatar, camera (overview / focus on the player to move), HUD, spinner.
- [ ] Textures and the sky bundled under `client/public/lebensweg/` (the prototype fetched them from Poly Haven).
- [ ] `/game-avatars/<id>.glb` in a desktop browser: the Android app serves them from its APK today
      (`GamePageClient.kt`); `game-night-dev` gets the same route from the generated folder.
- [ ] Fix the prototype's open flaws while porting: overview label overlaps, the brown cliff-foot line near Start,
      plain tent/mine/chapel (Blender, asset QA).
- [ ] Geometry QA (board + Blender asset QA) before showing anything.
- [ ] Version: next free Spieltisch minor in all five manifests (CLAUDE.md § Versioning).

## Success
`bun run build`, `cargo test -p mediagram-game`, typecheck green; the TV page draws the board from a live game.
