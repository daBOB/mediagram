# Bauherr review fixes

Branch feat/bauherr (fast-forwarded onto feat/spieltisch first). Tests in `src/bauherr_visit_tests.rs` unless noted.

| Fix | Change | Test |
|---|---|---|
| Double tap | `chain_ms` (400) in `BauherrConfig`; BuildMenu/Entrances/Hotel/Rathaus-again questions open `now + chain_ms`. Phone lock untouched. | `a_double_tap_cannot_answer_the_question_that_follows_the_first_tap` (2nd tap 200 ms later: Ignored, no entrance bought) |
| Rathaus errands | `Opt::Done` ("Fertig"); after Levels/Place at the Rathaus `errand_done` re-offers the menu (prompt carries what happened) until Fertig or nothing affordable. Absent player: auto-play declines the repeat menu. | `the_rathaus_offers_another_errand_until_fertig` (build, entrance, Fertig), `an_absent_player_does_one_errand_at_the_rathaus_and_is_done` |
| Absent free things | `keeps = cost == 0 || cash - cost >= RESERVE`; BuildMenu/Entrances priced at cheapest real option (`cheapest_build/entrance`). | `an_absent_player_with_little_cash_still_takes_what_is_free`, `at_the_rathaus_an_absent_player_buys_the_entrance_when_building_would_eat_the_reserve` |
| Awards ranking | `TvBauherr.tsx` passes `final.ranking`-filtered players with `ranked` (as TvRace). Host: `Seat.out_n`, standings put later-out seats first. | `bauherr_rules_tests.rs::those_who_lasted_longer_rank_higher_among_the_broke`; client typecheck/build |
| Doppelt unaffordable | Note "X kann sich das Doppelte nicht leisten." instead of silent end. | `a_doubled_permit_nobody_can_afford_says_so` |
| Phone clock | `net.ts`: samples cleared on socket open and on `visibilitychange` to visible (then 6-ping burst); listener removed in `close()`. | typecheck + build green; not device-verified |
| Turn budget | `TURN_BUDGET = 180` (90/60/45 rounds) with tuning comment. | `the_round_limit_comes_from_the_turn_budget`, "1 von 90" sheet test updated |

Checks: `cargo test -p mediagram-game` 271 green; clippy `-D warnings` clean; client typecheck, 21 tests, build green. Reviewer fuzz (300 seeded games, disconnects, stray taps) re-run in the test module temporarily, then removed: all invariants held, 300 finished, 286 with a bankruptcy. No protocol types changed.

Not done: "Gratis"/"Doppelt" readings unchanged as instructed. TV box untouched.
