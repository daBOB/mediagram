---
title: Every question a tap opens must open a moment later
author: Andre
date: 2026-11-08
branch: spieltisch-games
cost: { minutes: 56, tokens: 702827 }
id: l-38ca9dd2
scope: repo
status: active
src: agent
touches:
  - Cargo.lock
  - Cargo.toml
  - android/app/build.gradle.kts
  - crates/mediagram-game/client/public/grand-allee/SOURCES.md
  - crates/mediagram-game/client/public/grand-allee/alpenrose.glb
  - crates/mediagram-game/client/public/grand-allee/belle_epoque.glb
  - crates/mediagram-game/client/public/grand-allee/fjordhus.glb
  - crates/mediagram-game/client/public/grand-allee/hacienda_sol.glb
  - crates/mediagram-game/client/public/grand-allee/kirschbluete.glb
  - crates/mediagram-game/client/public/grand-allee/lagune.glb
---

## Decided
- In a live game, a question that opens because the mover just tapped opens `chain_ms` (400 ms) after that tap, never at `now`. The server accepts a tap from `since - early_ms` (150 ms), so a question opened at `now` takes the second tap of a double tap as its answer.
- Once the house has answered a mover's question because the clock ran out, it answers the rest of that turn's questions at once (`idle`). Otherwise a phone left on the table costs a fresh 20 s per follow-up question (Rathaus menu, each entrance, the field), over two minutes a turn.

## Watch out
- Grand Allee missed the first rule on three paths (Skip, Done, an empty Rathaus menu), where a double tap on the last entrance could buy the plot on the field question. Bauherr had it right everywhere. Phase 5's phone controls add new tap-driven paths: give each one `later`.

In the code: `crates/mediagram-game/src/grand_allee/live_choose.rs` (`later`), `live_turn.rs` (`house_at`, `idle`), tests in `live_tap_tests.rs`; Bauherr's equivalent `bauherr_turn.rs` and `bauherr::visit_tests::a_double_tap_cannot_answer_the_question_that_follows_the_first_tap`.
