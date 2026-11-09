# Phase 3 — The room answers when the house acts

Priority: medium. Status: pending. Wave 1.

## Facts (verified)

Five copies of one rule — the house acts `grace` after the later of the player
leaving and the question opening; `auto_ms = 1500.0` in all five configs:

- `src/durak_seats.rs:70-74` (used durak.rs:168-172)
- `src/maumau_moves.rs:147-151` (used maumau.rs:149-152)
- `src/race.rs:96-103` (used :137, :139, :153; the rule's only written comment, :96-98)
- `src/schwimmen.rs:166-170` (inline; file at exactly 200 lines)
- `src/grand_allee/live_turn.rs:24-34` `house_at` (adds its 20 s clock and `idle`)

`left_at.unwrap_or(0.0)`: in a real room absence always comes with `left_at`
(room_seats.rs:78-84, 111-112, 129-131); only test fixtures make an absent player
without a time (room_seats.rs:46-56 `for_test(.., false)`; grand_allee
live_debt_tests.rs:91, :126; live_game_tests.rs:8). Keep it, with a one-line comment.

Name helpers ("Jemand"): kopfnuss_views.rs:16 ("?"), race_views.rs:10,
maumau_views.rs:23 (reused by schwimmen_views.rs:4), durak_views.rs:15,
grand_allee/live_text.rs:6 (also used with `GameCtx` players: live_choose.rs:15,
live_debts.rs:45, so the helper takes `&[Player]`).

## Decided

- `src/game.rs`: `pub(crate) const HOUSE_GRACE_MS: ServerTime = 1500.0` and
  `GameCtx::house_at(&self, player: PlayerId, since: ServerTime) -> Option<ServerTime>`:
  `None` while the player is connected or unknown; otherwise
  `max(left_at or 0, since) + HOUSE_GRACE_MS`. Pure; reads no clock. One unit test:
  connected, left before the question, left after it, absent with no time.
- Each game asks `ctx.house_at(..).is_some_and(|t| ctx.now >= t)` (or keeps the time
  where it needs it). Grand Allee's `house_at` keeps its own clock and idle:
  `if idle { since } else { clock.min(ctx.house_at(..).unwrap_or(clock)) }` —
  it must keep returning a time (the simulator will need it).
- Move the rule's comment from race.rs:96-98 to `house_at`.
- Delete the five `auto_ms` config fields; `live_debt_tests.rs:128` uses `HOUSE_GRACE_MS`.
- One `name(players: &[Player], id) -> &str` in game.rs (fallback "Jemand");
  delete the copies. Kopfnuss's "?" becomes "Jemand" (never shown during a game).
- Leave alone: the "do we still wait for this player" checks (quiz.rs:137-141,
  poster.rs:112-113, kopfnuss*.rs, grand_allee/live_debts.rs:84) and score lookups.

## Order (tests green after each)

`house_at` + test → Mau-Mau → Schwimmen → Durak → Race → Grand Allee → delete
`auto_ms` → merge name helpers.

## Acceptance

No behaviour change; every existing game test passes unchanged except the one
`auto_ms` assertion. `cargo test`, `bun run typecheck`, `bun test` green.
