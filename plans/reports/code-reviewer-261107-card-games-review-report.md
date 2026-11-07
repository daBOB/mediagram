# Card games review: card table, Mau-Mau, Schwimmen, Durak

Scope: `git diff f8d37d4c..HEAD -- crates/mediagram-game`, card files only (no bauherr*).
Host: `cards.rs`, `protocol_cards.rs`, `maumau*.rs`, `schwimmen*.rs`, `durak*.rs`, card parts of
`room_settings.rs` / `factory.rs` / `protocol.rs`. Client: `tv/TvCards.tsx`, `tv/cards/*`,
`phone/CardsDesk.tsx`, `ui/card-face.ts`. Specs: phase-01, phase-02, plan.md box rules.

## Verification done

- `cargo test -p mediagram-game`: 264 pass. `cargo clippy --all-targets -D warnings`: clean.
  Client `bun run typecheck`: clean. `bun test`: 21 pass.
- A throwaway fuzz harness, run against a scratch copy of the crate (the repo was not touched).
  - Mau-Mau 2–8 seats, Schwimmen 2–8, Durak 2–6, 150–200 seeds each.
  - Each run: up to 3000 steps of mixed play. 85 % were legal-ish desk presses; the rest were
    crafted `Action::Cards`: random button ids, 0–3 hand indices including duplicates and
    values up to 255, random table index.
  - About 3 % of steps also disconnected or reconnected a random player.
  - After the play phase, every player was made absent and the game had to finish by itself.
  - A second sweep ran only 40 steps before everyone left, so the absent players play almost
    the whole game.
  - Every step built the TV scene and every phone screen. Checks: no open seat cards outside
    Schwimmen, and each phone's hand length equals its TV seat count.
  - Result: about 4.6 M steps, roughly 670 k actions accepted, about 6 000 games finished.
    **0 panics, 0 stalls, 0 view-invariant failures.** Harness:
    `/tmp/claude-1000/-home-andre-Workspace-mediagram/8dd36e6e-a3a8-44bb-84cd-930fe3215400/scratchpad/fuzz/crates/mediagram-game/src/zz_fuzz_tests.rs`.

## Findings

No critical or high findings.

### 1. Hidden information

No hand card reaches the TV or another phone.
- The TV gets only counts, public plays and the trump card. With 6 Durak players the trump card
  is in a hand, and the TV hides it (`trump` is `None` when the deck is empty).
- Each phone gets only its own hand and the public table.
- Schwimmen shows hands only while `revealed`, which is the reveal and the final ranking.
- The TV scene goes only to `local` connections (`room.rs`).

The turn flow does leak some information, though:

| # | Where | Scenario | Sev | Fix |
|---|---|---|---|---|
| 1a | `durak_flow.rs:78-96` (`can_add`/`seek`), shown at `durak_views.rs:51,115` | A player is asked to throw in only if they hold a rank that is on the table; everyone else is skipped silently. The TV turn highlight and other phones ("X darf dazulegen") then tell the table which players hold matching ranks, and that the skipped ones do not. In a real game "can't" and "won't" look the same. This was a deliberate pace choice in the build report. | medium (design) | User decision. Either prompt every non-defender with cards and leave "Dazulegen" disabled when nothing fits, or keep the skip and accept the tell. |
| 1b | `maumau.rs:162-166` | Drawn card fits: the turn stays on the player (Drawn stage). Drawn card does not fit: the turn passes at once. So the TV shows whether the drawn card fits, before the player decides to keep it. | low | Always enter `Drawn` and offer "Behalten", with "Spielen" disabled when the card does not fit. |

### 2. Stalls

None found. Verified by reading the code and by the all-absent fuzz.
- Every stage has a timer, or an actor who plays themselves after `auto_ms`.
- Players are never removed during a game: `room.rs` only runs `retain` while there is no game.
  So `absent_since` always finds the seat.
- Mau-Mau with an empty deck and a single discard cannot livelock. Every card fitting the top
  is then in someone's hand, and absent players always play a legal card.
- Schwimmen: when every alive player ties, nobody loses (`schwimmen_round.rs:18`), so no round
  can knock everyone out. A refresh keeps the deck at 5 or more cards even with 8 seats.
- Durak: the attack limit is the defender's hand when the bout began, so the defender can always
  answer or take. `next_active` is reached only while two or more players are in. The Final
  views never call `defender()`.
- Reconnects: a returning player keeps their token and seat. A phone without its token gets the
  Wait screen and its old seat plays itself. A TV that reconnects skips late sound cues
  (`LATE_S`), and finished flights do not replay.

### 3. Rules against the specs

All spec rules are implemented as written:
- Mau-Mau: 7 stacking, 8 skip (including 2 players), jack wish, Mau penalty, lazy reshuffle
  under the top card, start card effects, ranking, absent play.
- Schwimmen: values in half points, 30½, 31 mid-round and in the deal, knock and its last
  round, lives → schwimmt → out, absent play at 27 or more.
- Durak: beating, throw-in limited to `min(6, defender's hand at the start)`, refill attacker
  first and defender last with the trump card drawn last, Bito/taken and who attacks next,
  safe order, Durak, "keiner ist Durak".

Deviations and loose ends:

| # | Where | Scenario | Sev | Fix |
|---|---|---|---|---|
| 3a | `schwimmen_round.rs:18-20` | The spec says "ties: all lowest". The code spares everyone when *all* alive players tie. It is flagged as unconfirmed in the build report. It is a sound call: otherwise an all-swimming tie ends with nobody left. | needs user OK | Keep it; get the user's confirmation and add it to the spec. |
| 3b | `maumau.rs:193-199`, `:156-158` | With the deck and the discards under the top both empty, the Mau penalty deals nothing but the banner still says "Strafkarte!". A 7-stack draw says "zieht {owed} Karten" even when fewer were drawn. | low | Use `give`'s real count (return it) in the banner text. |
| 3c | `maumau.rs:190,197-198` + `choose_suit` | A jack played as the second-to-last card without Mau: the "vergisst Mau" banner is replaced by "wünscht …" as soon as the suit is picked. The spec says "the TV says so". Known to the builder. | low | Keep the penalty banner until `banner_ms` has passed, or put the penalty in the note. |

### 4. Validation at the trust boundary

Sound; nothing to fix.
- Every handler takes exactly one hand index (`let [i] = hand[..]`), so duplicates and extra
  indices are ignored.
- Indices go through `.get()` or explicit bounds checks: `pick`, `card_at`, `pick_swap`,
  `attack_at`.
- The turn is checked with `seat_of(player) == turn/actor`; non-seated players are ignored.
- Wrong stage or unknown button gives `Ignored`. Rule breaks give `NotAllowed` with a German text.
- Every `expect` is guarded by invariants that the fuzz never broke: discard never empty,
  drawn card still in hand, two or more active Durak seats, deck holds at least the deal.
- The seat gate (`blocked()` on `connected_count`, then `retain(connected)`) caps every deal
  below its deck size.
- Frames are size-capped (`MAX_FRAME_BYTES`).

### 5. TV box rules

| # | Where | Scenario | Sev | Fix |
|---|---|---|---|---|
| 5a | `cards-texts.ts:48`, `cards-hud.ts:81-91` (`paint` makes a texture on a cache miss), `schwimmen_views.rs:75-77` | At every Schwimmen reveal, all seat markers change at once to `"●●○ · 27 · −1"`-style text, which is not pre-drawn. That makes up to 8 canvas draws and `initTexture` uploads in a single frame, at the climax of the round. The header of `cards-hud.ts` itself says one mid-game texture stalled the box for a frame or two. The value combinations (about 26 values × 4 coin states × loss × 8 seats) are too many to pre-draw. | medium | Show the hand value in the seat's count badge from the number atlas, which reads 0 during the reveal anyway; add a `30½` cell. Keep the marker to pre-drawable `coins` / `coins · −1`. |
| 5b | `cards-hud.ts:123` | `setTag` calls `place()` every frame for every seat. `place()` loops over all seats, allocates a `Vector2` and a `Vector3` through `project`, and sets every mesh position. That is O(n²) per frame, about 16 allocations at 8 seats. Heimlauf's `race-hud.ts` places only after a redraw. | low | Move `this.place()` inside the `if (this.tagState[seat] !== state)` branch. |
| 5c | `cards-motion.ts:40-75,127-128` | Per-frame garbage: `flights()` array, a `filter`+`sort` per seat, a fresh `Placed` per fan card (up to about 130), the spread array and `blobs.slice` in `shade`. The benchmark report says the box is GPU-bound, so the practical cost is small. Unmeasured. | low | Only if the box measurement shows GC hitches: reuse scratch arrays and write into pooled `Placed` objects. |

No HTML over the canvas during play: only the pre-ready wait text and the OK settings panel,
the same as Heimlauf. `renderer.compile` runs at load with every pool card and badge visible.
Faces come from one atlas and are opaque, with no alpha test and no shadow maps.

## Positive observations

- Thin phones: `CardDesk` carries everything; the phone clears its pick on every new prompt or
  hand and after each press, which also blocks double-tap replays.
- Rules are pure functions with their own tests. The views are pure, and `settle` writes scores
  once.
- The TV state comes only from the scene and the server clock, so a TV that reconnects mid-game
  is correct. `TvApp` keys `TvCards` by game and seats.

## Recommended actions (priority)

1. 5a: draw Schwimmen reveal values from the number atlas, so no textures are created mid-game.
2. Ask the user about 1a (Durak throw-in tell) and 3a (all-tied rounds spared).
3. 5b: one-line move of `place()`.
4. 1b, 3b, 3c when convenient.

## Unresolved questions

- Durak: keep silently skipping players who cannot throw in (fast, but leaks information), or
  ask everyone?
- Schwimmen: should a tie between all players spare everyone? (This is the current code; the
  spec text says the lowest all lose.)
- Mau-Mau: jack as the last card is allowed now; is that the house rule? (Open in the build
  report.)
- Box timing of the 8-seat Mau-Mau worst case is still unmeasured, and phase 06 owes it.
