# Phase 4 — The ask

Priority: high (three known bugs). Status: pending. Needs phase 3. Result: 0.175.0
(the wire changes). Term: **Ask**, `CONTEXT.md` § Spieleabend.

## Problem

- Which ask is on the phone is guessed in six places: `PhoneApp.tsx:149`
  (`key={screen.opens_at}`), `PhoneSheet.tsx:23`, `PhoneSheet.tsx:68`,
  `CardsDesk.tsx:36`, `buzz-moments.ts:33`, `PhoneFluechtig.tsx:44`.
- When buttons are dead after a tap is five rules: `ChoiceScreen.tsx:34`
  (local pick, timed only), `PhoneSheet.tsx:11` (700 ms), `SheetBid.tsx:17`
  (`sent`), `CardsDesk.tsx` (never), `PhoneFluechtig.tsx:15` (3 s).
- A phone cannot learn that its tap was dropped: `room.rs:166` answers an
  ignored tap with nothing, `room_views.rs:20` re-sends a screen only when it
  changed, and `net.ts:91` drops a send on a closed socket silently.
- Known bugs: two screens with the same open time leave the phone stuck; a
  timed choice stays locked when the host drops the tap; a double tap can
  answer the next ask. None of this has a test.

## Decided — host

- `PhoneView` gets `ask: Option<String>`; `ClientMsg::Action` gets
  `ask: Option<String>` (`#[serde(default)]`, optional in TypeScript, so lobby
  and TV senders stay as they are).
- New `src/room_asks.rs`: `ask_code(&PhoneScreen) -> Option<String>`, a short
  hash of what is asked. Equal content gives an equal code, so a reconnecting
  phone lands in the same ask.

  | Screen | Has a code when | The code covers |
  |---|---|---|
  | Choice | always | prompt, option labels, `opens_at` |
  | Sheet | options or a bid | prompt, option labels, `opens_at`, the bid's `value`, `max`, `step` |
  | Cards | the desk has buttons | prompt, the hand's cards |
  | Flüchtig | `phone.ask` is set | `round`, the ask's `at`, `prompt`, `opens_at` |

  Not covered: cash lines, hotels, `closes_at`, tricks, `picked`, `placed`,
  the table's cards, button states.
- `room.rs`, the game-tap arm of `handle_action`: work out the code of the
  tapper's screen **as of now** (not the last one sent: the game may have moved
  on). A missing or different code is refused without calling the game and
  without an error message. In every case clear the tapper's `last_view`, so
  the flush that follows (`room_loop.rs:61`) re-sends their screen.
- `tools/smoke.mjs` and the helpers in `room_tests.rs:59` and
  `game_log_tests.rs` send the code of the last view.
- Games and their tests are not touched. The 400 ms opening gaps stay.

## Decided — phone

- New `client/src/phone/ask.ts`, plain TypeScript, no React:
  - the ask read off a view: its code, open time and clock's end;
  - state: is a tap waiting for its reply, the mark of the tapped button, when
    the player last tapped;
  - `live(state, ask, now)`: open, clock not run out, no tap waiting, and
    `REST_MS = 400` since the player's own last tap;
  - a tap that could not be sent changes nothing; a screen arriving ends the wait.
- `net.ts`: `send` returns whether the message went out.
- New `client/src/phone/use-ask.ts`: the thin binding. State sits beside the
  view in `PhoneApp`, above the screens. No new per-frame work on the card desk.
- Screens keep only layout and are keyed by the code:
  `ChoiceScreen` (drop `localPick`; "Eingeloggt" comes from `picked`),
  `PhoneSheet` (drop `TAP_LOCK_MS` and its effect), `SheetBid` (drop `sent`),
  `CardsDesk` (reset the pick on a new code), `PhoneFluechtig` (drop
  `TAP_LOCK_MS` and its timer).
- `buzz-moments.ts`: `sameAsk` compares codes for choice, sheet and Flüchtig.
  Cards keep today's rule (same turn while the desk has buttons): by code the
  phone would buzz on every card played. `buzzFor` takes the view; its 11
  tests pass the code along.

## Tests

- Host (`src/room_asks_tests.rs`): a matching code reaches the game; a wrong
  code and a missing code do not; the tapper's screen is re-sent even when
  unchanged; cash changing keeps the code; a new prompt changes it; a phone
  that reconnects gets the same code.
- Phone (`ask.test.ts`): dead before the open time and after the clock; a tap
  waits; an unsent tap does not; the next screen ends the wait; a new ask
  within 400 ms of the tap is dead, after it live; the same ask without an
  answer is live again.

## Acceptance

- The three known bugs each have a failing-then-passing test.
- No `TAP_LOCK_MS`, `localPick` or `key={screen.opens_at}` left in `client/src/phone`.
- ADR 0003 gains its sentence on the ask. A learning (`graft note`) records
  that the phone's rest guards the double tap, so a new tap-driven path no
  longer has to open "a moment later".
- `cargo test -p mediagram-game`, `bun run typecheck`, `bun test`, `bun run build`
  green; one round each of quiz, Grand Allee and a card game played on a phone.

## Risk

- A game that takes a tap without changing the ask or showing the answer would
  look as if it refused it: the buttons come back live. None found by reading
  the views; the played rounds are the check.
