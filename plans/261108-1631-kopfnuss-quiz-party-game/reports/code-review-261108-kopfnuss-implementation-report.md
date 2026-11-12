# Code review: Kopfnuss implementation (feat/kopfnuss, uncommitted)

Date: 2026-11-08, snapshot ~18:00. The working tree changed during the review (ChoiceScreen.tsx and styles.css at 17:53); findings are against that later state.
Work tree: `/home/andre/Workspace/spieltisch/mediagram-kopfnuss/crates/mediagram-game`. Paths below are relative to it unless absolute.

## Scope
- Host: `src/kopfnuss*.rs`, `src/protocol_kopfnuss.rs` (new, about 1,300 LOC including tests). Shared: `factory.rs`, `quiz.rs`, `quiz_config.rs`, `*_views.rs`, `protocol*.rs`, `room_settings.rs`
- Client: `phone/ChoiceScreen.tsx`, `phone/choice-tricks.ts`, `tv/TvKopfnuss.tsx`, `tv/TvQuiz.tsx`, `tv/kopfnuss/*`, `ui/tricks.ts`, `ui/lobby.ts`, `PhoneLobby.tsx`, `styles.css`
- Build checks: `cargo clippy -p mediagram-game --all-targets` gave 0 warnings. `cargo test` passed 324. `bun run typecheck` was clean. `bun test` passed 68. All were re-run after the 17:53 edits.

## Overall
The engine is small, well split, and follows the crate's patterns: views are pure, timers come from `ctx.now`, tests sit in `#[path]` files, and every file is under 200 lines. Every rule in the brief is implemented as described. I found no panics or stuck stages that real games can reach. The shared changes do not break the other games. The real problems are at the edge between phone and host, in how the phone locks itself after a tap.

## High

### H1. In the finale, a phone can freeze for the rest of the climb (up to 90 s)
- `src/kopfnuss_climb.rs:145` sets `closes_at: Some(self.ends_at)`, so `ChoiceScreen.tsx:34` locks the phone locally after one tap (`setLocalPick`).
- `src/kopfnuss_climb.rs:67` silently drops a tap that arrives before `seat.opens_at`. There is no early tolerance; Quiz accepts taps up to `early_tolerance_ms = 150` early (`quiz.rs:116`).
- `room.rs:166` sends nothing back for `Ignored`. The host's screen keeps the same `opens_at`, which is the component key (`PhoneApp.tsx:149`), so the local lock is never cleared.
- **Scenario A:** Ben answers wrong, so the next question opens at T+2000. His clock estimate runs 40 ms ahead of the host and his tap takes 15 ms to arrive. He taps the moment the buttons light up. The host sees T+1975, before `opens_at`, and drops it. His phone shows "Eingeloggt" on a question he has not answered until someone reaches the top or 90 s pass. In a race, players tap right as the lock ends, so this is the likely case, not a rare one.
- **Scenario B:** a tap made while the socket is reconnecting is dropped by `net.ts:92`. The result is the same freeze.
- **How it came about:** the spec (phase-03, lines 20 and 53) had `closes_at: None` and planned that "the player taps again". The implementation changed this to `Some` to stop double taps, which removed that fallback.
- **Fix:** do both.
  1. Accept early taps like Quiz does: `if now < seat.opens_at - early_ms { Ignored }`, with `early_ms` taken from `cfg.quiz.early_tolerance_ms`. Add a test where a tap 100 ms early after a lock is accepted.
  2. Go back to `closes_at: None` and stop double taps on the host with an arm delay (see M1). An untimed screen never locks locally, so a lost tap can never freeze it. The cost is that the header no longer shows the "Gleich … 2" countdown; the buttons still stay dark until `opens_at`.

## Medium

### M1. A double tap answers the next screen without seeing it (pick steps and climb)
- The next screen's `opens_at` is the time of the tap: `kopfnuss_picks.rs:55` (`step_at = now`) and `kopfnuss_climb.rs:77` (`opens_at = now`). So the new screen is live as soon as it arrives, about one round trip later (20–60 ms on a LAN). A human double tap takes 100–250 ms, so the second tap lands on the new screen.
- **Pick:** the trick step shows 2 full-height columns, so the second tap of any double tap on a category picks a trick the player has not read. A triple tap also picks a target.
- **Climb:** the second tap answers the next question blind, which is wrong 75% of the time and costs a 2 s lock.
- The comment at `kopfnuss_climb.rs:143-144` says a double tap cannot answer the next question. That is only true within the same screen.
- **Fix:** add an `arm_ms` of about 400 to `KopfnussConfig`. Use `step_at = now + arm_ms` and, on a right answer, `opens_at = now + arm_ms`. The buttons stay dark for that time (`live` requires `open`). Keep `arm_ms` above the early tolerance from H1. The test `pick_screens_open_at_strictly_later_times` still holds.

### M2. Surface parity: Kopfnuss is missing from the Android TV games shelf
`/home/andre/Workspace/spieltisch/mediagram-kopfnuss/android/ui-tv/src/main/kotlin/ui/tv/games/TvGamesShelf.kt:59-67` lists every game kind except `kopfnuss`. On the TV box, Kopfnuss can only be chosen from the leader's phone. The fix is one line: `ShelfGame("kopfnuss", "Kopfnuss", "2–8 players", null, null)`. The plan never mentions it, so this is a scope question for you (see below).

### M3. Kleber in a plain phone browser may not complete (not checked on a device)
A 1 s touch hold passes Android Chrome's long-press threshold (about 500 ms). `-webkit-touch-callout: none` is set only for `html.in-app` (`styles.css:66-69`), and the keys have no `onContextMenu` handler. If the browser treats the hold as a long-press and sends `pointercancel`, `release` cancels the hold every time. **Fix:** add `onContextMenu={(e) => e.preventDefault()}` on the keys and `-webkit-touch-callout: none` on `.key`, then test on a phone browser that is not the controller app.

## Low / nits

| # | Where | Issue and scenario | Suggestion |
|---|---|---|---|
| L1 | `kopfnuss.rs:98-99` | "Last place" includes players who have disconnected. Dana leaves in round 2 with 0 points. From then on every tie is drawn at random, because the last-placed player never votes. | Take the minimum over connected players only. |
| L2 | `ChoiceScreen.tsx` (keyed reorder) | Wechselbad moves DOM nodes with `insertBefore`. Moved buttons lose keyboard focus, restart their `key-light` animation, and probably lose pointer capture (unverified). With Kleber and Wechselbad together, lifting the finger over a gap does not cancel the hold. | Accept, or position the slots with CSS `order` or a transform instead of moving nodes. The spec wanted a 150 ms swap animation; the instant swap is better for aiming taps, so record it as a deliberate change. |
| L3 | `ChoiceScreen.tsx:115` | A pointer-up on any button cancels the hold, including a second finger lifting. | Release only when `e.pointerId` matches the finger that started the hold. |
| L4 | `kopfnuss_climb.rs` `phone()` | A player who joins during the climb has no seat and sees "Keine Fragen mehr / Warte auf die anderen". | Use copy for players without a seat. |
| L5 | `kopfnuss.rs:156-162`, `kopfnuss_climb.rs:92` | The "all done" and "drained" checks are trivially true when no player is connected. If the router drops everyone, rounds run on in about 25 s each and the climb ends at once. Quiz has the same `everyone` check, so this matches the crate. | Optional: also require at least one connected player. |
| L6 | `kopfnuss_supply.rs` `deck()` | The finale marks all 30 deck questions as asked, though players see 10–20 of them. The 311-question pack starts over after about 5 Kopfnuss games. | Accept, or mark questions only when they are served. |
| L7 | spec drift | `resolve_ms` is 4000; the spec says 3000. The winner's phone shows a `Wait` with the fact, not the `Outcome` the spec asks for. The plan said `quiz.rs` stays untouched, but it changed; the behavior is verified identical (see below). "No tricks: identical DOM" does not hold exactly: buttons gain `aria-label`, labels gain `aria-hidden`, `.choice-clock` gains `nowrap`, and held Enter/Space auto-repeat is now ignored in every game (`ChoiceScreen.tsx:69`). Screen readers get the same names, and ignoring auto-repeat is harmless or better. | Record these in the plan. |
| L8 | `choice-tricks.ts:43` | Schneckenpost cuts strings by UTF-16 units, so a film title with an emoji shows a broken character mid-reveal. | `Array.from(label).slice(0, k).join("")` |
| L9 | `production/` (untracked) | Agent session logs inside the crate are not in `.gitignore`. | Do not commit them; add to `.gitignore`. |
| L10 | trust boundary | Tricks are enforced only on the phone; a modified phone can ignore all of them, Kleber included. `at` is still clamped to 400 ms like any tap. | Fine for a LAN party game; note it as a design choice. |
| L11 | `TvFinal` and phone `Final` | The final ranking comes from the climb, but the score shown is quiz points. The winner can show fewer points than second place. | Design question: show the climb step instead, or hide the score. |
| L12 | `kopfnuss_supply.rs:36-40` and `quiz_mix.rs:15-18` | The "start the general pack over" logic is duplicated. | A small shared helper (DRY). |

## Regression checks (b), all verified OK
- **`Round::shuffled`:** the body is the same as before, and it calls the same `&mut StdRng` in the same order (`options.shuffle(rng)`). Seeded quiz tests still pass.
- **Factory lock:** the guard is released (`factory.rs:42`) before `QuestionSupply::new` takes the lock again, so there is no self-deadlock on the non-reentrant std `Mutex`. Every other `history()` call is a separate, short lock. Only in-memory work runs under the guard; the poster prefetch is a non-blocking `tokio::spawn`, and there is no `.await` while it is held. Both lock sites recover from a poisoned lock with `into_inner`. One factory and one room, so no contention between threads.
- **Choice constructors in quiz, poster and race:** each only gains `tricks: vec![]`.
- **ChoiceScreen with no tricks:** the same pointerdown-to-pick path, the same class string (`key-holding` is absent), identity order, and no badge row. The remaining differences are listed in L7.
- **TvQuiz:** moved verbatim, plus two optional props. Without them it renders the same DOM.
- **`.keys { grid-auto-rows: 1fr }`:** affects only extra rows, so nothing changes for screens with 4 or fewer buttons.

## Public contracts (c)
- **Added:** `GameKind::Kopfnuss`, `TvScene::Kopfnuss`, `PhoneScreen::Choice.tricks`. On the wire, every Choice in every game now carries `"tricks":[]`. Nothing outside the client decodes `PhoneScreen`; the Android controller is a WebView.
- **Also shared, not new types:** `.choice-clock` nowrap, `.keys` auto rows, keyboard auto-repeat ignored, and the smoke deadline raised from 300 to 420 s for every game. All are harmless.

## Crash and stuck-stage scan (a)
- **Indexing is safe:**
  - `categories[chosen]`: chosen is below the category count, and Pick only runs with at least 1 category.
  - `counts[v]`: `tap` checks the index.
  - `rank - 1`: `rank_of` is at least 1.
  - `deck.get(idx)`: uses `get`, so no panic.
- **Empty supply is handled:** no categories goes straight to the climb. An empty `take` ends the round. An empty deck rings the bell at once and the head-start order decides.
- **No sheet:** a player without one sees a Wait screen and counts as done.
- **Disconnected players:** they are not targets and do not block a stage.
- **Climb with zero seats:** it would never end, because `max_by` returns None. This cannot happen: a game needs at least 2 players to start, and players are never removed mid-game.

## Positives
- Picks and resolution are pure functions with focused tests.
- Contained `Quiz` reuse avoids a scoring refactor.
- `step_at` keys for each pick step.
- Global `prefers-reduced-motion` covers the new animations.
- `aria-label` keeps the real label for screen readers under Spiegel and Schneckenpost.
- The trick badge row has an `aria-label`.
- Kleber works with Enter/Space.

## Recommended actions
1. H1: add early tolerance in `Climb::on_choose`, and either go back to `closes_at: None` for the climb or accept the lost-tap freeze knowingly.
2. M1: add `arm_ms` to pick steps and to the climb after a right answer. Fix the misleading comment.
3. M3: suppress the context menu during a Kleber hold and check on a real phone browser.
4. M2: add the TV shelf entry if Kopfnuss is in scope for the box.
5. L1, L9, then the remaining nits as time allows.

## Plan follow-ups (do not edit the plan; for the lead)
| Item | Status |
|---|---|
| Lobby (2+ players, blocked in kids mode) | done |
| Rust tests listed in the plan | done |
| bun tests | done |
| Builds green | done |
| Screenshots | present except a Wechselbad shot |
| Version bump (still 0.159.0 in all 3 manifests) and changelog | open, expected at commit |

## Unresolved questions
1. Is the Android TV shelf (M2) in scope for this feature, or does it get its own follow-up?
2. Should the final ranking show quiz points or the climb step (L11)?
3. For the climb (H1), which do you prefer: the untimed screen as the spec has it (lost taps can never freeze; no lock countdown in the header), or the timed screen with early tolerance (shows the countdown; a tap lost during a reconnect still freezes the phone)?

**Status:** DONE_WITH_CONCERNS
**Summary:** The rules are implemented correctly, other games are unaffected, and lint, types and tests are clean. One high bug: a finale tap the host drops early or a lost tap freezes that phone for up to 90 s. Two medium issues: a double tap answers the next pick or climb screen unseen, and Kleber may not complete in a plain phone browser (not checked on a device).
**Concerns:** H1 and M1 need host changes before a real game night. M3 needs a test on a real device.
