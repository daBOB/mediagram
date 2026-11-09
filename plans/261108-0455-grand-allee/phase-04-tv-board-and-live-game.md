# Phase 04 — TV board and the live game

**Goal:** Grand Allee playable on the TV box end to end: the engine from
phase 02 running in the game-night room, the ten hotels from phase 03 on a
real 40-field boulevard board, animated, at a steady frame rate on the box.

## What exists to build on

- `src/grand_allee/` — pure rules engine (board, rules, building, debts) with
  tests; the simulator's turn order and bots (`play.rs`, `actions.rs`).
- `client/public/grand-allee/<hotel>.glb` — ten hotels, `stage_<n>_body` and
  `stage_<n>_tint` meshes, light baked, unlit.
- Bauherr's live game as the pattern: `bauherr.rs` (turn flow on the `Game`
  trait), `bauherr_asks.rs`, `bauherr_views.rs`, `protocol_bauherr.rs`, and the
  TV client `client/src/tv/bauherr/` (camera, dice, motion, HUD, sound cues,
  timing) — about 1 800 + 1 650 lines.
- Box rules from the look tests: no HTML over the canvas, HUD drawn in the
  canvas and uploaded at load, big flat surfaces unlit with light baked in,
  no alpha cut-outs, 2D pawn sprites (3D avatars only on the win screen).

## Sub-phases

| # | Sub-phase | Delivers |
|---|---|---|
| 4a | Game in the room | `GameKind::GrandAllee`; a turn state machine on the engine (asks on the phone sheet, 20 s clocks, debt auctions on every phone at once, absent players auto-play); `protocol_grand_allee.rs` scene/board types (ts-rs); views; unit tests; a bot smoke run over a whole game. Lobby entry and poster. TV shows a plain debug board. |
| 4b | Board and dice in Blender | The 40-field oval boulevard in the Abendsonne town (granite-sett fields, signs on Bank/Rathaus/Grundstück/Baustelle/Freier Eingang/Gratis bauen, park, plot lines), baked like the hotels into `board.glb`; ground shadows per hotel and stage as baked decals; an ivory number die and an oak permit die. Same tool folder and checks (overlaps, fingerprint). |
| 4c | TV scene | three.js page: board + hotels (stage visibility, owner tint), avatar-sprite pawns that walk and step aside (no shared fields), dice throws, camera that leans in and frames the 40 m Skyline, in-canvas HUD (round countdown, players' cash), animations: stage rising, entrance placed, guest paying, auction countdown, forced purchase, bankruptcy. Sound cues adapted from Bauherr. |
| 4d | Box | Release build on the Skyworth box: load time, fps with all ten hotels built, a full bot-driven game on the TV; screenshots and a short video for review. |
| 4e | Retire Bauherr | Remove `GameKind::Bauherr`, its Rust, client and assets once Grand Allee replaces it; docs, changelog, version. |

Each sub-phase ends with tests green, a version bump and a commit; 4c and 4d
end with screenshots for the user's review.

## Decisions to confirm (user)

1. Bauherr is replaced by Grand Allee (removed in 4e), not kept beside it.
2. The board is built and baked in Blender like the hotels (one light, one
   pipeline), not with Bauherr's headless-Chrome town bake.
3. Pawns are 2D avatar sprites on the board (8 skinned avatars measured
   33 fps on the box); 3D avatars stay on the ceremony.
4. The phone keeps the generic money sheet for now; phase 05 builds the real
   phone controls.

All four confirmed by the user on 2026-11-08 ("start phase 4", defaults approved).

## Board layout decisions (user, 2026-11-08)

Renders in `reports/board-layout/`.

5. **Hotels face the TV** (south), not their road: on the far side of the
   board a hotel facing its road shows the TV its back.
6. **Hotels at 70 %, TV camera 55° down**: at full size and 33° the near row
   hid the near road and the 40 m Skyline covered half the board.
7. **A winding road, not an oval** ("remember this is the board layout", with
   a photo of the classic board): the boulevard bends round the hotels, a
   river runs under the road by the Bank and the Rathaus through a pond in
   the park's waist, the sea lies along the north. Our own path, drawn by
   `tools/grand-allee-models/board_path.py`; the design doc keeps MB's board
   layout out, so the photo gives the qualities (winding road, water, hotels
   in the bends), not the shape. Fields are 6 m long so the four park hotels
   fit inside the loop at 70 %.
8. **Plots follow the road, the road is curved** (user, seeing 0.162.1 on the
   box, relayed by another session: "Each hotel has a plot of land that
   adapts to the course of the route", "make the players road curved"): each
   plot's land runs from the road's edge along its frontage back to the
   hotel; neighbours share their boundary; fields are drawn along the curve.
   `board_land.py`; plan in `reports/board-layout/curved-road-and-plot-land-plan.jpg`.

Open: the tall park hotels in the north row (Skyline, Maharadscha) hide parts
of the north road at 55°; the 4c camera leans in when a pawn walks there.

## Todo

- [x] 4a game in the room (0.160.0)
- [x] 4b board and dice (0.161.0)
- [x] 4c TV scene (0.162.0)
- [x] 4d box run (0.164.0; `plans/reports/box-run-261109-0030-grand-allee-ktx2-and-box-run-report.md`) — `spieltisch-games` is fast-forwarded to `main` 411a7a53
  (0.163.0 on the channel: this branch + Kopfnuss + main's 0.158.0–0.159.1);
  the next release from here is 0.164.0, box test builds say 0.163.1. First: KTX2 for the hotel and board atlases (six 1024² and
  four 2048² WebP atlases are ~117 MB on the GPU with mipmaps and decode on the
  box's main thread; Bauherr ships KTX2)
- [ ] 4e retire Bauherr — move the helpers Grand Allee borrows (`name`,
  `taler`, `grouped` in Rust; glide, HUD font, dice helpers and the `/bauherr/`
  die images in the client) to shared places first
