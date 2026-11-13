# Phase 1: Rules ladder, one figure each, names

**Context:** [design.md](design.md) § Decided by the user 1, § Decided by Claude (ladder, naming). Code: `src/fluechtig.rs` (`FluechtigGame::new`, dealing at line 68), `src/fluechtig_rules.rs` (`Rules::big`), `src/fluechtig_play.rs` (news), `src/fluechtig_views.rs` (`who`, `prompt`, pieces).

**Overview:** priority high, status todo. The host deals one figure per hunting player (two to a lone hunter), picks the rules from the ladder, and names players instead of "Jäger N".

**Key insights**
- Figures = hunting players clamped to 2..=5; the existing round-robin `hunters[i % hunters.len()]` already yields `[h, h]` for one hunter and each hunter once otherwise.
- `Rules.hunters` becomes meaningless (the table sets the count): remove it.
- `Chase` is pure and has no players; it gets a `names` list that `Chase::new` fills with "Jäger 1…" (so the rules tests keep their texts) and the game overwrites with player names.
- `FluechtigMover.piece` is today the global piece number and also how the phone tells fugitive from hunter; it becomes the per-player figure number, so the mover needs an explicit `runs` flag.

---

### Task 1: The ladder

**Files:** Modify `src/fluechtig_rules.rs`; Test `src/fluechtig_rules_tests.rs`

**Interfaces:**
- Produces: `pub fn Rules::big_for(figures: u8) -> Rules` — the big city's rules for a table hunting with `figures` (clamped to 2..=5). Replaces `Rules::big()`; `impl Default for Rules` goes (Task 2 stops using it).

- [ ] **Step 1: Write the failing test** `fewer_figures_see_the_fugitive_every_third_round_with_fewer_shadow_rides_and_double_moves`:

```rust
let third: Vec<u8> = (3..=24).step_by(3).collect();
for (figures, reveals, schatten, doppel) in
    [(5, vec![3, 8, 13, 18, 24], 5, 2), (4, third.clone(), 5, 2), (3, third.clone(), 2, 1), (2, third.clone(), 1, 0)]
{
    let r = Rules::big_for(figures);
    assert_eq!((r.rounds, r.reveal_rounds.clone(), r.fugitive_tickets.schatten, r.fugitive_tickets.doppel), (24, reveals, schatten, doppel), "{figures} figures");
    assert_eq!((r.fugitive_tickets.taxi, r.fugitive_tickets.bus, r.fugitive_tickets.ubahn), (4, 3, 3));
    assert_eq!(r.hunter_tickets, Tickets { taxi: 10, bus: 8, ubahn: 4, schatten: 0, doppel: 0 });
    assert!(r.inflow && r.city == CityKind::Big);
}
assert_eq!(Rules::big_for(1), Rules::big_for(2));
assert_eq!(Rules::big_for(9), Rules::big_for(5));
```
Add `two_figures_never_offer_a_double_move`: `chase(Rules::big_for(2), <a big-city fugitive start>, <two hunter starts>)` → `!c.can_double()`.

- [ ] **Step 2:** `cargo test -p mediagram-game fluechtig_rules` → FAIL (`big_for` not found).
- [ ] **Step 3: Implement `Rules::big_for`.** Doc comment carries the bot win rates per row (43.0 / 45.6 / 48.7 / 49.5 % on the bridged city) as the rationale. Keep `hunters: figures` for now; Task 2 removes the field. `Default` → `big_for(5)` until Task 2.
- [ ] **Step 4:** same command → PASS.

### Task 2: One figure per player

**Files:** Modify `src/fluechtig.rs:56-75`, `src/fluechtig_config.rs`, `src/fluechtig_rules.rs` (drop `hunters` field and `Default`); Test `src/fluechtig_tests.rs`

**Interfaces:**
- Consumes: `Rules::big_for` (Task 1).
- Produces: `FluechtigConfig.rules: Option<Rules>` — `None` (default): `Rules::big_for(figures)` for the table; tests pass `Some(Rules::small())`. `FluechtigGame.owners`: each hunting player once in seat order, or the lone hunter twice.

- [ ] **Step 1: Write the failing test** `every_hunter_moves_one_figure_and_a_lone_hunter_moves_two` (replaces `the_room_plays_the_big_city_with_five_hunters_over_24_rounds`; keep its board assertions `(199, 1168, 540)` and river):

```rust
for (n, figures) in [(2u8, 2usize), (3, 2), (4, 3), (5, 4), (6, 5)] {
    let p = table(n, true);
    let g = FluechtigGame::new(FluechtigConfig { seed: Some(1), ..FluechtigConfig::default() }, &p, 0.0);
    let hunters: Vec<PlayerId> = (0..n).filter(|&id| id != g.fugitive).collect();
    let expect = if hunters.len() == 1 { vec![hunters[0]; 2] } else { hunters.clone() };
    assert_eq!(g.owners, expect, "{n} players");
    assert_eq!(scene(&g).hunters.len(), figures);
    assert_eq!(g.chase.rules, Rules::big_for(figures as u8));
}
```
Update `one_player_runs_and_the_others_share_the_hunters` to `[(2, 2, 2), (3, 2, 1), (6, 5, 1)]`; the test helper `game()` passes `rules: Some(Rules::small())`.
- [ ] **Step 2:** `cargo test -p mediagram-game fluechtig::tests` → FAIL.
- [ ] **Step 3: Implement.** In `new`: `figures = hunters.len().clamp(2, 5)`; `rules = cfg.rules.clone().unwrap_or_else(|| Rules::big_for(figures as u8))`; take the city from `rules`. Fix the doc comment of `new` and of `FluechtigScene.hunters` ("one figure per hunting player, two for a lone hunter").
- [ ] **Step 4:** `cargo test -p mediagram-game fluechtig` → PASS (fix other tests that pinned four/five pieces with intent, not by loosening asserts).

### Task 3: Names, not piece numbers

**Files:** Modify `src/fluechtig_rules.rs` (`Chase.names`), `src/fluechtig_play.rs:80,84,120`, `src/fluechtig.rs`, `src/fluechtig_views.rs` (`who`, `prompt`, pieces), `src/protocol_fluechtig_phone.rs` (`FluechtigMover`, `FluechtigPiece` docs); Test `src/fluechtig_tests.rs`

**Interfaces:**
- Produces: `Chase.names: Vec<String>` (what the news calls each hunter figure; `Chase::new` sets `"Jäger {i+1}"`). `FluechtigGame::figure(&self, i: usize) -> Option<u8>` — figure `i`'s number among its player's figures, `None` when that player has one. `FluechtigMover { name, color, piece: Option<u8> /* figure number, only for a player with two */, runs: bool /* the fugitive */, you }`. `FluechtigPiece.number`: 1 or 2 within this phone's player.
- Texts: name = player name, or `"{name} (Figur {k})"` with two figures. News: `"{name} fährt {by} nach {to}{found}"`, `"{name} fährt {by} nach {to} und erwischt den Flüchtigen!"`, `"{name} kann nicht fahren und setzt aus."`. Hunter prompt: `"Wohin fährst du?"`, or `"Wohin fährt Figur {k}?"` with two.

- [ ] **Step 1: Write the failing tests.**
  - `news_and_prompts_name_the_hunting_player`: `table(4)`, `game(..)`; play the fugitive's move; the first hunter's phone: `ask.prompt == "Wohin fährst du?"`, `mover.piece == None`, `mover.runs == false`, `pieces[0].number == 1`; play its move; `g.chase.news[0].starts_with(&format!("{} fährt ", name_of_owner_0))`.
  - `a_lone_hunter_numbers_their_two_figures`: `table(2)`; hunter phone `pieces` numbers `[1, 2]`; after the fugitive's move `ask.prompt == "Wohin fährt Figur 1?"`, `next.piece == Some(2)`; after the hunter's move the news starts with `"{name} (Figur 1) fährt "`.
  - Update `"Wohin fährt Jäger 1?"` (two places) to `"Wohin fährst du?"` and the `[1, 3]` pieces assert to `[1]`. `fluechtig_rules_tests.rs:145` ("Jäger 2 kann nicht fahren …") stays.
- [ ] **Step 2:** `cargo test -p mediagram-game fluechtig` → FAIL on the new asserts.
- [ ] **Step 3: Implement.** `FluechtigGame::new` sets `chase.names` from `players` and `figure(i)`; `who` fills `piece` from `figure(i)` and `runs`; `prompt` uses `figure(i)`; pieces use `figure(i).unwrap_or(1)`.
- [ ] **Step 4:** `cargo test -p mediagram-game` → PASS; `client/src/protocol/FluechtigMover.ts` now has `runs`.
- [ ] **Step 5:** `cd client && bun run typecheck` → PASS (the client still compiles; its texts change in Task 6).

**Success criteria:** all Flüchtig Rust tests green; two-player and six-player tables deal as the table above.

**Risks:** tests seeded for five pieces may hide a real regression when "fixed"; change each with a one-line reason in the test name or assert message.
