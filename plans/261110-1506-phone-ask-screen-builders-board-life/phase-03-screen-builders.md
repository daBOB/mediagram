# Phase 3 — Screen builders

Priority: medium. Status: pending. Needs phase 2. Host only. Result: 0.174.1.

## Problem

Games assemble the shared phone screens field by field, so a new field on a
screen is an edit in every game.

- `PhoneScreen::Choice` is written out at 7 sites in 5 files
  (`quiz_views.rs:33`, `poster_views.rs`, `kopfnuss_views.rs:37`,
  `kopfnuss_climb.rs:166`, `race_views.rs:66,79`), plus `dreifelder_views.rs:27`
  after phase 2. Each repeats `picked: None, tricks: vec![]`. Dreifelder's two
  new fields cost an edit in five files of other games.
- Kopfnuss takes the quiz's choice apart and puts it together again to add its
  tricks (`kopfnuss_views.rs:52-60`).
- Buttons get their colour by index at 6 sites under two rules.
  `.zip(AnswerColor::ALL)` silently drops a fifth option (`quiz_views.rs:39`,
  `poster_views.rs:31`, `kopfnuss_climb.rs:166`, `race_views.rs:84`); cycling
  keeps it (`kopfnuss_views.rs:12`, `grand_allee/live_views.rs:22`).
- `PhoneScreen::Final` is written out by every game, each with the same rule
  `can_restart: view.leader == Some(player)`.

## Decided

- `src/protocol_choice.rs`: `buttons(labels) -> Vec<ChoiceOption>`, cycling the
  colours. It is the function now private to `kopfnuss_views.rs:12`, moved and
  made `pub(crate)`. All six colour sites call it.
- `impl PhoneScreen` (beside the enum, or in a new `src/phone_screens.rs` if
  `protocol.rs` would pass 200 lines):
  - `choice(prompt, options, opens_at, closes_at)`: no pick, no tricks, not
    numbered, no status.
  - `with_picked`, `with_tricks`, `with_numbered`, `with_status`: each sets one
    field of a choice and passes any other screen through unchanged. Kopfnuss
    calls `quiz.phone_view(..).with_tricks(..)`.
  - `final_place(view, player, rank, of, score)`: fills `can_restart` from the leader.
- Variants, wire format and `client/src/protocol/*.ts` do not change. The 39
  test sites that match variants stay as they are.
- `Wait`, `Outcome`, `Sheet` and `Cards` stay plain literals: a builder there
  would only rename fields. `race_views.rs` keeps its local `wait` helper.
- Tests, in a new `src/phone_screens_tests.rs`: `buttons` keeps a fifth label
  and repeats the first colour; `with_tricks` leaves a `Wait` alone;
  `final_place` lets only the leader restart.

## Acceptance

- `grep -rn "PhoneScreen::Choice {" src` and `"PhoneScreen::Final {"` find only
  the builders, `match` arms and tests.
- No `.zip(AnswerColor::ALL)` left in `src`.
- `git diff` shows no change under `client/src/protocol/`.
- `cargo test -p mediagram-game`, `bun run typecheck`, `bun test` green.
