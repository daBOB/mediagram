# Card games: review fixes, two decisions, bigger phone hand

Branch `feat/card-games` (worktree `mediagram-cards`, fast-forwarded to `feat/spieltisch` first). Four commits, nothing pushed.

## Checks
- `cargo clippy -p mediagram-game --all-targets -- -D warnings`: clean.
- `cargo test -p mediagram-game`: 270 pass (was 264).
- Client `bun run typecheck && bun test && bun run build`: green, 23 tests (was 21).
- Reviewer's fuzz harness, rerun on a throwaway copy (scratchpad `fuzz2`, release): Mau-Mau, Schwimmen, Durak, plus the all-absent sweep. 4 tests pass, 0 panics, 0 stalls, 0 view-invariant failures.
- Scripted phones vs `game-night-dev` (GAME_PORT=8091), each game to the end on the new build: Schwimmen (swap, knock, life lost, Feuer, swimming, out, winner), Mau-Mau (7 stack, 8 skip, jack wish, Mau forgotten and called, reshuffle, winner), Durak (attack, beat, throw-in, take, Bito, deck empty, safe, Durak; 0 server errors).
- A headless 412x915 phone played by tapping its own desk in each game, old and new build.

## Decisions
| # | What | Where | Test / proof |
|---|---|---|---|
| 1a | Durak: every non-defender with cards is asked to throw in, in seat order from the attacker. "Dazulegen" is disabled when nothing fits, "Fertig" is always there. Only the public attack limit ends the round early. Absent players answer Fertig after `auto_ms` (unchanged). | `durak_flow.rs` (`is_asked`) | `everyone_with_cards_is_asked_in_seat_order_after_the_attacker_fit_or_not`, `the_throw_in_looks_the_same_to_everyone_whether_a_card_fits_or_not` (TV scene and other phones' prompts equal for fit vs no fit; own desk has the same prompt and buttons), `an_absent_thrower_adds_nothing`; 8 older tests updated for the extra prompts |
| 1b | Mau-Mau: a drawn card always pauses the turn ("Spielen" only enabled when it fits, "Behalten" always). Absent player plays it if it fits, else keeps. Drawing from nothing still just passes. | `maumau.rs`, `maumau_views.rs` | `a_drawn_card_that_does_not_fit_still_pauses_the_turn_and_can_only_be_kept`, `the_tv_and_other_phones_cannot_tell_whether_the_drawn_card_fits`, `an_absent_player_plays_a_drawn_card_that_fits_and_keeps_one_that_does_not` |
| 2 | Schwimmen all tied: nobody loses a life, nobody leaves (kept). | no code change | `a_round_where_everyone_ties_costs_nobody_a_life` now pins lives, `lost`, `out`, banner, no "−1" marker, game goes on, and the all-swimming case |

## Fixes
- **Schwimmen reveal, no new textures.** `CardsSeat` gets `worth: Option<u8>` (hand value in half points, set while the hands are open); the TV draws it in the count badge from the number atlas, last cell reads "30½" (`worthCell`, `HALF_CELL`). Seat markers at the reveal are only coins or coins + "· −1"; those three loss texts are added to the pre-drawn set. Generated `CardsSeat.ts` committed. Proof: `cards-layout.test.ts` (pre-drawn texts, `worthCell`), Rust tests on `worth` and markers, TV screenshot `after-tv-schwimmen-reveal-values-in-badges.png` (badges 26 / 25 / 30½, Cem's marker "●●○ · −1"). Box timing not measured.
- **HUD per frame.** `setTag` calls `place()` only inside the redraw branch (`cards-hud.ts`). Checked by reading; `layout()` and banner/note still place after their own redraw. Per-frame allocations in `cards-motion.ts` left alone.
- **Banners with a dry deck.** `give` returns the cards really dealt; seven-stack banner reads "zieht keine Karte" / "zieht eine Karte" / "zieht N Karten" (the first is new and pre-drawn). Test `the_banners_count_the_cards_really_drawn_when_the_piles_run_dry`.
- **Mau penalty banner.** The penalty case "deck and discards both empty" cannot happen: the played card sits on the old top, so the reshuffle always finds one card. The banner needs no change there; documented in a comment.
- **Penalty or "ruft Mau!" followed by a jack wish.** The wish banner is queued behind the one on show (`queued`) and appears when it has had its `banner_ms`; no new texts. Test `a_forgotten_mau_stays_on_the_banner_until_the_jack_wish_follows_it` (also covers "ruft Mau!" and a wish with nothing before it).

## Phone desk
`CardsDesk.tsx` + `cards-desk.css`. The middle belongs to the table cards (they now fill the empty area, 5.6x7.8 rem, shrink to wrapping rows from 5 cards, so six Durak pairs fit). Hand cards 6.6x9.2 rem (were 5x7), rank 2.2 rem, overlapping only as needed so the row fits the width, a second row from the 8th card (rows overlap vertically; the corner index stays visible), no scrolling. Buttons sit directly above the hand.

Screenshots (412x915 @2x, headless Chrome, driver in session scratchpad) in `plans/reports/card-games-261107-fix-screens/`:
- before: `before-mau-mau-10-cards`, `before-mau-mau-picked`, `before-schwimmen-picked`, `before-durak-defend-picked`, `before-durak-throw-table`
- after: `after-mau-mau-10-cards` (drawn card that fits), `after-mau-mau-picked`, `after-mau-mau-drawn-card`, `after-schwimmen-picked` (3 + 3 table), `after-durak-defend-picked`, `after-durak-10-cards-table`, `after-durak-six-table-cards`

## Notes
- `maumau.rs` is 334 lines (was 309), over the 200 guideline; it was over before. Splitting the banner logic out was not worth the churn here.
- Durak rounds are longer by design now (one "Fertig" per other player per bout); a full 3-player game still ended in about 2 minutes of scripted play.
- Unrelated: headless TV runs 404 on the avatar `.glb` files in a fresh worktree.
- Not touched: Bauherr files, `net.ts`, TV box, versions, changelog.

## Unresolved
- Box timing of the Schwimmen reveal after this change and of the 8-seat Mau-Mau worst case is still unmeasured.
- The phone cannot tell an attack card from a defence card in the flat Durak table list, so the shrunken table shows them in one wrap (as before, only denser). A grouped pair view would need a protocol change.
