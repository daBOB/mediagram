# Phase 2: Rules-cards stage on the host

**Context:** [design.md](design.md) § three rules cards, § Approaches (host-driven stage). Pattern to copy: `src/grand_allee/live_intro.rs` and `live_intro_tests.rs` (cards, leader skip with `chain_ms`, `room_seats::leader`), `live_turn.rs:57-75` (tick).

**Overview:** priority high, status todo. Before round 1 the host runs three cards of `intro_ms` each; the room's leader may skip from their phone. TV and phones learn the card from new protocol fields; the content is drawn by the client (Phase 3).

**Key insights**
- Flüchtig has no stage enum: an `Option<Intro>` on the game is enough. While it is `Some`, every tap is a skip attempt and `on_tick` does nothing else (so the house cannot move).
- When the cards end the fugitive's question opens: `ask = Ask { opens_at: now, step: Field }`; on a skip `now + chain_ms`.
- `tv_view` has no `ViewCtx` today; it needs one for the leader's name.

---

### Task 4: The intro stage

**Files:** Create `src/fluechtig_intro.rs`, `src/fluechtig_intro_tests.rs`; Modify `src/lib.rs` (module), `src/fluechtig.rs` (field, `on_action`, `on_tick`), `src/fluechtig_config.rs` (`intro_ms`); Test helper in `src/fluechtig_tests.rs` sets `intro_ms: 0.0`.

**Interfaces:**
- Produces: `pub(crate) struct Intro { pub card: u8 /* 0-based */, pub at: ServerTime }`; `FluechtigGame.intro: Option<Intro>`; `FluechtigConfig.intro_ms: f64` (default `10_000.0`; `0` starts the chase at once). In `fluechtig_intro.rs`: `const CARDS: u8 = 3`; `pub(crate) fn next_card(&mut self, now: ServerTime)`; `pub(crate) fn skip_intro(&mut self, ctx: &GameCtx, player: PlayerId) -> Result<(), ActionError>`.

- [ ] **Step 1: Write the failing tests** in `fluechtig_intro_tests.rs` (fixture: `table(4)` connected, `FluechtigConfig { seed: Some(1), ..default }`, so `intro_ms` is 10 s; leader = seat 0 "Ana"):
  - `three_cards_of_ten_seconds_then_the_fugitive_is_asked`: tick every 50 ms; cards open at `[(0, 0.0), (1, 10_000.0), (2, 20_000.0)]`; at `30_000.0` `g.intro == None`, `g.ask.opens_at == 30_000.0`, `g.chase.turn == Turn::Fugitive`.
  - `the_leaders_tap_skips_and_a_double_tap_does_not_move`: leader taps at `3_000.0` → `Ok`, `intro None`, `ask.opens_at == 3_400.0`; the fugitive taps at `3_080.0` → `Err(Ignored)`, `g.ask.step == Step::Field`, `g.chase.log.is_empty()`.
  - `nobody_but_the_leader_skips_and_the_next_connected_player_leads_when_the_first_is_gone`: a non-leader tap → `Ignored`, intro unchanged; set seat 0 disconnected → seat 1's tap skips.
  - `no_house_move_while_the_cards_run`: the fugitive disconnected; tick to `29_950.0` → `log` empty, round 1; tick on past `30_000.0 + HOUSE_GRACE_MS` → the fugitive has moved.
  - `no_intro_when_intro_ms_is_0`: `intro_ms: 0.0` → `g.intro == None` at once.
- [ ] **Step 2:** `cargo test -p mediagram-game fluechtig_intro` → FAIL (module missing).
- [ ] **Step 3: Implement.** `new`: `intro: (cfg.intro_ms > 0.0).then_some(Intro { card: 0, at: now })`. `on_action`: if `self.intro.is_some()`, return `self.skip_intro(ctx, player)` before the mover check. `on_tick`: if an intro runs, call `next_card(ctx.now)` once `ctx.now >= at + intro_ms`, then return. `skip_intro`: leader only (`room_seats::leader(ctx.players)`), then `intro = None`, `ask = Ask { opens_at: ctx.now + chain_ms, step: Field }`.
- [ ] **Step 4:** `cargo test -p mediagram-game fluechtig` → PASS (older tests run with `intro_ms: 0.0` via the helper).

### Task 5: What TV and phones see during the cards

**Files:** Modify `src/protocol_fluechtig.rs` (`FluechtigIntro`, `FluechtigScene.intro`), `src/protocol_fluechtig_phone.rs` (`FluechtigPhone` fields), `src/protocol.rs` (re-export), `src/fluechtig_views.rs`, `src/fluechtig_intro.rs` (view helper), `src/fluechtig.rs` (`tv_scene` passes the view); Test `src/fluechtig_intro_tests.rs`, helper `scene()` in `src/fluechtig_tests.rs`.

**Interfaces:**
- Produces (wire):
  - `FluechtigIntro { card: u8 /* 1-based */, cards: u8, opened_at: ServerTime, closes_at: ServerTime, leader: Option<String> }`.
  - `FluechtigScene.intro: Option<FluechtigIntro>`.
  - `FluechtigPhone.intro: Option<FluechtigIntro>`; `may_skip: bool` (the leader's phone while the cards run); `reveal_rounds: Vec<u8>`; `partners: Vec<String>` (the other hunting players' names in turn order, each once; empty on the fugitive's phone).
  - While the cards run, `FluechtigPhone.ask` is `None` on every phone.
- `pub(crate) fn tv_view(&self, view: &ViewCtx) -> TvScene`; `pub(crate) fn intro_view(&self, view: &ViewCtx) -> Option<FluechtigIntro>`.

- [ ] **Step 1: Write the failing tests.**
  - `the_tv_gets_the_card_its_times_and_the_leaders_name`: `scene.intro == Some(FluechtigIntro { card: 1, cards: 3, opened_at: 0.0, closes_at: 10_000.0, leader: Some("Ana".into()) })`; after the cards `scene.intro == None`.
  - `phones_get_their_role_and_only_the_leaders_may_skip`: the fugitive's phone: `intro.is_some()`, `hiding == Some(g.chase.fugitive.at)`, `ask == None`, `reveal_rounds == g.chase.rules.reveal_rounds`, `partners` empty; each hunter's phone: `hiding == None`, `pieces.len() == 1`, `partners` = the other two hunters' names; `may_skip` true on seat 0's phone only.
- [ ] **Step 2:** `cargo test -p mediagram-game fluechtig_intro` → FAIL (fields missing).
- [ ] **Step 3: Implement** the fields and fill them in `phone_view`/`tv_view`; `leader` from `room_seats::leader(view.players)`.
- [ ] **Step 4:** `cargo test -p mediagram-game` → PASS; `client/src/protocol/FluechtigIntro.ts` exists.
- [ ] **Step 5:** `cd client && bun run typecheck` → PASS.

**Success criteria:** cards time out, skip works for the leader only, no move and no house move during the cards, hunters' phones and the TV never carry the fugitive's field.

**Risks:** a restart of the same table must show the cards again — `new` runs per start, so it does; check in the Phase 5 live run.

**Security:** the skip is the only action accepted during the cards, from the leader only.
