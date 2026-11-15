# Zündstoff in 3D — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Zündstoff's question-and-throw rules and its flat TV with the 3D circle: tap a name to throw, the target taps Fangen! as the bomb lands, a miss drops it, the hidden fuse decides who loses a life.

**Architecture:** The host keeps its module layout (`zuendstoff.rs` rules, `zuendstoff_config.rs` config/stage/entry, `zuendstoff_views.rs`) with a new stage machine built on main's `Ask`; the question deck leaves. The phone reuses `PhoneScreen::Choice` (names; a one-key Fangen!) and `Wait`/`Outcome`/`Final`. The TV becomes a lazily loaded three.js stage modelled on Grand Allee's (`client/src/tv/grand-allee/`), drawing the ceremony's avatar copies with the four new clips.

**Tech Stack:** Rust (`crates/mediagram-game`, ts-rs → `client/src/protocol`), React + three.js 0.170 TV page, bun.

**Spec:** `docs/superpowers/specs/2026-11-15-zuendstoff-3d-design.md` (camera A, funstation target, Meshy clips). Renders: `plans/261115-2340-zuendstoff-3d/reports/`.

## Global Constraints

- Hidden fuse 18–40 s per round (Tempo stretches it); lives 3 (Dauer 2/3/5); last one with lives wins; the victim starts the next round (a random player still in if the victim is out).
- No question anywhere. No clock or fuse hint on the TV or phones.
- Throw: the holder's phone lists everyone else still in (seat order); one tap throws. Throwing straight back is allowed.
- Flight ≈ 1 s. The target's Fangen! key looks live from the throw (no window hint). A tap from `lands_at − 400 ms − 150 ms` to `lands_at + 400 ms` catches; an earlier tap is a fumble (= miss); no tap = miss. A miss drops the bomb at the target's feet; they may throw again 2000 ms after the landing.
- Fuse out during a flight: the target is hit. A dropped bomb belongs to the player it lies by.
- Away players: the house throws for an away holder after the Ask grace (random target); an away target never catches.
- Kids: no questions any more, so `kids: Kids::Plays`.
- Clip names on the avatars (as `add-gestures.sh` tidied them): `Over_Shoulder_Throw`, `Leap_Right_And_Catch`, `Jump_To_Catch_And_Fall`, `Male_Bend_Over_Pick_Up`; blast victim `Falling_Down`; idle `Idle_15`.
- Every non-test `.rs` under 200 lines; `scripts/check.sh` passes before merge. Spieltisch version: next minor above main at merge time.

## Review Focus

1. The fuse runs out in the same tick as a landing → one outcome only: if `now ≥ fuse_at` the bomb goes off on the target, whether or not they caught it (test `fuse_at_landing_hits_the_target`).
2. A catch tap arriving after the tick already resolved the landing as a miss → ignored, no double state (test `a_late_catch_after_the_drop_is_ignored`).
3. A holder who leaves the room mid-flight as thrower → nothing breaks; the flight lands normally (test `the_thrower_leaving_mid_flight_changes_nothing`).
4. The last two players: a throw always has a target; when one is out at a boom, `Final` comes at once (test `two_players_last_life_ends_the_game`).
5. Taps from non-holders / non-targets, and a second tap by the target, are ignored (test `only_the_target_catches_and_only_once`).

---

### Task 1: Host rules, wire types, views (Rust)

**Files:** Modify `src/zuendstoff.rs`, `src/zuendstoff_config.rs`, `src/zuendstoff_views.rs`, `src/protocol_zuendstoff.rs`, `src/zuendstoff_tests.rs`; split into `src/zuendstoff_flight.rs` if a file passes 200 lines.

**Interfaces — Produces:**
- `ZuendstoffConfig { lives: 3, fuse_min_ms: 18_000, fuse_max_ms: 40_000, intro_ms: 2500, throw_lead_ms: 600, flight_ms: 1000, catch_before_ms: 400, catch_after_ms: 400, pickup_ms: 2000, boom_ms: 4500, early_tolerance_ms: 150, seed }` (`at()` stretches only the fuse; `sized()` the lives). `ZuendstoffGame::new(cfg, players, now)` — no deck.
- `Stage::Hold { ask: Ask, ready_at: ServerTime }` (holder = `ask.who()`, may throw from `ready_at`), `Stage::Flight { from, to, thrown_at, lands_at, catch: Catch }` with `enum Catch { Pending, Caught, Fumbled }`, `Stage::Boom { victim, ends_at }`, `Stage::Final`.
- `lands_at = now + throw_lead_ms + flight_ms`. On tick at `lands_at`: Caught → `Hold { ask: Ask::open(to, lands_at, None), ready_at: lands_at }`; else → `Hold { …, ready_at: lands_at + pickup_ms }`. Fuse check first in every tick.
- Wire (`ZuendstoffStage`, tag `stage`): `Hold { holder, ready_at, dropped: bool }`, `Flight { from, to, thrown_at, lands_at, caught: Option<bool> /* None until resolved */ }`, `Boom { victim, out }`, `Final { order }`; `ZuendstoffScene { round, seats, stage }` unchanged otherwise. Remove `QuizQuestionView` from it.
- Phone: holder ready → `Choice` of target names (`Action::Choose { index }` = target index); holder dropped → `Wait("Heb die Bombe auf …")`; target in flight → `Choice` with one option "Fangen!", `opens_at: thrown_at`, `closes_at: None`; others → `Wait("{from} wirft zu {to}")`; boom → `Outcome`; final → `final_by_order`.
- `ENTRY`: `kids: Kids::Plays`, `start` builds without a question supply.

- [ ] Rewrite tests first (Table fixture as today): throw lists everyone else in; catch in window → holds; early tap → fumble → dropped → can throw only after pickup; no tap → dropped; fuse during hold hits holder; fuse in flight hits target; boom → life lost → out at zero → last one wins; away holder → house throws after the grace; away target never catches; Tempo/Dauer still scale; the five Review Focus tests.
- [ ] `cargo test -p mediagram-game zuendstoff` fail → implement → pass; `cargo test -p mediagram-game` all; clippy `-D warnings`. Commit `feat(spieltisch): Zündstoff throws and catches instead of asking questions`.

### Task 2: Avatar copies carry the new clips

**Files:** `crates/mediagram-game/tools/make-ceremony-avatars.mjs` (`KEEP` default list).

- [ ] Add `Over_Shoulder_Throw, Leap_Right_And_Catch, Jump_To_Catch_And_Fall, Male_Bend_Over_Pick_Up, Falling_Down` to `KEEP`; regenerate into `android/spieltisch/src/main/assets/game-avatars` (gitignored) and check each copy lists all 10 clips; note the size growth. Commit.

### Task 3: The TV stage (three.js), on the shared TV table shell

**First:** rebase onto main (0.193.5+). Main has `client/src/tv/table/` from the peer session's refactor: `TableShell` (lifecycle: lazy stage load, cheer, restart, settings on OK), `TvTableShell`, `stage-host.ts` (`startStage(container, live, now, game)`; a board passes its canvas class, sounds, fonts and `build()` → scene/camera/layout/move/cue/dispose) and `tv-sound.ts` (`TvSound`). Zündstoff's wrapper is ~15 lines like `TvGrandAllee.tsx`; its stage implements the `build()` contract. If the Room already owns the game's end on main (peer's next phase), report seats and the final ranking instead of building Zündstoff's own Final screen.

**Files:** Create `client/src/tv/zuendstoff/zuendstoff-stage.ts` (renderer, felt table, lights, camera A), `zuendstoff-circle.ts` (avatar seats, labels, out look), `zuendstoff-bomb.ts` (bomb model, hand attach, parabola, drop, blast), `zuendstoff-motion.ts` (which clip plays when, from the scene's times; pure, unit-tested with `bun test`); rewrite `client/src/tv/TvZuendstoff.tsx` as the thin React wrapper like `TvGrandAllee.tsx`; replace `zuendstoff.css`.

- [ ] Look per the approved render `reports/circle-camera-a.jpg`: round felt table (radius 7.2, wood rim), avatars on a 4.5 circle facing the middle, names and hearts under each, out players see-through, spot from above.
- [ ] Motion: holder idles with the bomb on `RightHand`; at `thrown_at` the thrower plays `Over_Shoulder_Throw` started so its release lands at `thrown_at + throw_lead_ms`; bomb parabola until `lands_at`; target plays `Leap_Right_And_Catch` (caught) or `Jump_To_Catch_And_Fall` (miss, bomb rolls to its feet), then `Male_Bend_Over_Pick_Up` near `ready_at`; boom: flash + smoke, victim `Falling_Down`.
- [ ] `bun test` for `zuendstoff-motion.ts`; `bunx tsc --noEmit`; `bun run build`. Commit.

### Task 4: Run it, measure, ship

- [ ] Browser walk with the dev server (TV + 3 phones): throw, catch, fumble, drop and pick-up, boom, out, final; screenshots to `reports/`.
- [ ] Frame rate with 8 avatars on the funstation (WayDroid), ≥ 30 fps.
- [ ] Version bump (minor), changelog, `scripts/check.sh`, fresh review, merge, install on the funstation when the TV is idle.
