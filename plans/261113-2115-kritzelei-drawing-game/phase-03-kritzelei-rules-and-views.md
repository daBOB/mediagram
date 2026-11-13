# Phase 03 — Kritzelei rules, words and views

**Priority:** after phase 02. **Status:** todo. **Spec:** played-back proposals (turns, points, words, screens); my calls 3, 5, 7, 8, 10.

## Overview

The game itself, following Preisfrage's file pattern:

| File | Holds |
|---|---|
| `kritzelei.rs` | state and `Game` impl |
| `kritzelei_config.rs` | all numbers |
| `kritzelei_clock.rs` | the pausable clock |
| `kritzelei_rules.rs` | turn order, op checks |
| `kritzelei_views.rs` | phone, TV and board views |
| `kritzelei_words.rs` | the word list |
| `protocol_kritzelei.rs` | TV wire types |

Every file gets a `_tests.rs` beside it. Then the game is registered: `GameKind`, catalogue, factory and the lobby block.

## Key insights

- Scores live on `Player.score`, which the game mutates through `GameCtx.players`. The ceremony and `rank_of` (`quiz_views.rs:13`) read them.
- `catalogue::entry` and the factory `match` have no wildcard arm, so `GameKind::Kritzelei` won't compile until both have an arm.
- Turns ≤ 8 (`ROOM_CAP` = 8: 8 players × 1 round, or 4 × 2). The kid deck (≥ 100) never runs out.
- `on_board` gets `&mut GameCtx` (scores). `on_board_presence` gets only `now`. The clock needs nothing else.

## Task 1: Words

**Files:** Create `crates/mediagram-game/src/kritzelei_words.rs` and `kritzelei_words_tests.rs`. Modify `lib.rs`: `mod kritzelei_words;` (like `mod preisfrage;`), so the tests compile and run in this task.

**Interfaces:**
```rust
/// Drawable German nouns; `true` = simple enough for kids mode.
pub(crate) const WORDS: &[(&str, bool)];
/// Every allowed word once, shuffled; only kid words when `kids_only`.
pub(crate) fn deck(kids_only: bool, rng: &mut impl rand::Rng) -> Vec<&'static str>;
```

- [ ] **Step 1: Failing tests:**
  - `the_list_has_300_words_and_100_for_kids`: `WORDS.len() >= 300`, kid count `>= 100`.
  - `no_word_appears_twice`: lowercase set size equals the length.
  - `words_are_tidy_nouns`: no leading or trailing whitespace, and the first char is uppercase.
  - `a_kids_deck_holds_only_kid_words` and `a_deck_never_repeats` (seeded `StdRng`).
- [ ] **Step 2:** `cargo test -p mediagram-game kritzelei_words` → FAIL.
- [ ] **Step 3:** Write about 300 concrete, drawable nouns (Hund, Leuchtturm, Regenschirm …). About 100 are marked kid words: everyday things a 6-year-old draws and names. Nothing abstract and no brand names. Several pairs per line keep the file short.
- [ ] **Step 4:** Tests PASS. **Step 5: Commit** — `feat(spieltisch): Kritzelei's word list`.

## Task 2: Clock, op checks and turn order

**Files:** Create `kritzelei_config.rs`, `kritzelei_clock.rs`, `kritzelei_rules.rs`, `kritzelei_clock_tests.rs`, `kritzelei_rules_tests.rs`. Modify `lib.rs`: `mod kritzelei_config;`, `mod kritzelei_clock;`, `mod kritzelei_rules;`.

**Interfaces:**
```rust
pub struct KritzeleiConfig {
    pub draw_ms: ServerTime,      // 90_000
    pub reveal_ms: ServerTime,    // 6_000
    pub guesser_points: i32,      // 2
    pub drawer_points: i32,       // 1
    /// Fewer players than this draw twice each.
    pub two_rounds_below: usize,  // 5
    /// Points one turn's drawing may hold; ops beyond are dropped.
    pub max_points: usize,        // 20_000
    /// Values (x and y each count) one op may carry.
    pub max_batch: usize,         // 512
}
pub(crate) struct TurnClock { left_ms: f64, since: Option<ServerTime> }
impl TurnClock {
    pub(crate) fn new(ms: f64) -> Self;
    pub(crate) fn left(&self, now: ServerTime) -> f64;     // never below 0
    pub(crate) fn start(&mut self, now: ServerTime);       // no-op if running or at 0
    pub(crate) fn pause(&mut self, now: ServerTime);
    pub(crate) fn wire(&self, total_ms: f64) -> Clock;     // ends_at = since + left_ms while running
}
/// Connected players at the start, in seat order, twice when fewer than `two_rounds_below`.
pub(crate) fn turn_order(players: &[Player], two_rounds_below: usize) -> Vec<PlayerId>;
/// Points `op` adds, given the turn's `ops` so far and the `held` point count.
/// Malformed → NotAllowed("Das Zeichenbrett hat etwas Unlesbares geschickt."); over the cap, or `more` without a line before → Ignored.
pub(crate) fn check_op(op: &DrawOp, ops: &[DrawOp], held: usize, cfg: &KritzeleiConfig) -> Result<usize, ActionError>;
```

- [ ] **Step 1: Failing tests:**
  - Clock:
    - `paused_it_keeps_what_was_left`: new(90_000), start(0), pause(10_000) → left(50_000) == 80_000.
    - `starting_twice_does_not_reset_it`.
    - `the_wire_shows_ends_at_only_while_running`: running → `ends_at == Some(since + left)`; paused → `None` and `left_ms` set.
    - `it_never_goes_below_zero`.
  - Turn order:
    - `turns_go_round_the_table_twice_below_five`: players [0,1,2] → `[0,1,2,0,1,2]`.
    - Five players → once each.
    - A disconnected player at the start is left out.
  - `check_op`:
    - `a_line_needs_a_known_colour_width_and_whole_points`: each of these is `NotAllowed`: colour 6, width 3, odd length, empty, a value of 10001, 514 values. `Line{0,0,[1,2,3,4]}` → `Ok(2)`; colour 5 (eraser) is ok.
    - `more_only_continues_a_line`: after `[]` or after `[Undo]` → `Ignored`; after `[Line…]` or `[Line…, More…]` → `Ok`.
    - `a_turn_holds_at_most_max_points`: held 19_999, an op of 2 points → `Ignored`.
    - `undo_and_clear_always_pass`: → `Ok(0)`.
- [ ] **Step 2:** FAIL. **Step 3:** Implement. **Step 4:** PASS. **Step 5: Commit** — `feat(spieltisch): Kritzelei's clock, turn order and stroke checks`.

## Task 3: The game

**Files:**
- Create `kritzelei.rs` and `kritzelei_tests.rs`
- Create `protocol_kritzelei.rs`
- Modify `lib.rs`: `mod kritzelei;`, `mod protocol_kritzelei;`
- Modify `protocol.rs` (re-exports) and `protocol_tv.rs` (`TvScene::Kritzelei { scene: KritzeleiScene }`)
- Modify `client/src/tv/TvApp.tsx`: a `kritzelei` branch rendering `<p className="tv-wait">Kritzelei</p>` so the client typechecks. The real page replaces it next phase.

**Interfaces:**
```rust
// protocol_kritzelei.rs
pub struct KritzeleiScene { pub turn: u32 /*1-based*/, pub turns: u32, pub drawer: PlayerId, pub board: bool, pub stage: KritzeleiStage }
#[serde(tag = "stage", rename_all = "snake_case")]
pub enum KritzeleiStage { Handover, Drawing { clock: Clock }, Picking { clock: Clock }, Reveal { word: String, guesser: Option<PlayerId>, ends_at: ServerTime }, Final }

// kritzelei.rs
pub(crate) enum Stage { Handover, Drawing, Picking, Reveal { guesser: Option<PlayerId>, ends_at: ServerTime }, Final }
pub struct KritzeleiGame { cfg, turns: Vec<PlayerId>, words: Vec<&'static str>, index: usize, stage: Stage,
                           clock: TurnClock, ops: Vec<DrawOp>, held: usize, board_present: bool }
impl KritzeleiGame { pub(crate) fn new(cfg: KritzeleiConfig, deck: Vec<&'static str>, players: &[Player], now: ServerTime) -> Self; }
```

Transitions, all in `on_board` unless noted:

| From | Event | To |
|---|---|---|
| Handover | `Ready` | Drawing (clock starts) |
| Handover | `Skip` | next turn |
| Drawing | `Guessed` | Picking (pause) |
| Picking | `KeepDrawing`, if `left > 0` | Drawing (start) |
| Drawing or Picking | `Pick(Some(p))`, `p` seated and ≠ drawer, else NotAllowed("Wer soll das sein?") | guesser `+guesser_points`, drawer `+drawer_points`, Reveal (`ends_at = now + reveal_ms`) |
| Drawing or Picking | `Pick(None)` | Reveal, no points |
| Drawing | `Draw{op}` | `check_op`, then append |

- Any other act/stage pairing → `Ignored`.
- `on_tick`: Drawing at `left == 0` → Picking, with the clock paused (`left_ms` is 0). Reveal past `ends_at` → next turn: clear ops and `held`, fresh clock, Handover, or Final after the last turn.
- `on_board_presence`: in Drawing, start or pause the clock. Always set `board_present`.
- `drawing()`: `Some((index+1, &[]))` in Handover, `Some((index+1, &ops))` in Drawing, Picking and Reveal, `None` in Final.

- [ ] **Step 1: Failing tests** (`kritzelei_tests.rs`, players via `Player::for_test`):
  - `ich_bin_dran_starts_the_clock`: Ready at 1_000 → the TV stage is `Drawing` with `clock.ends_at == Some(91_000.0)`.
  - `the_clock_stands_while_the_list_is_open`: Ready@0, Guessed@10_000 → `left_ms == 80_000`, `ends_at None`; KeepDrawing@20_000 → `ends_at == Some(100_000.0)`.
  - `the_clock_stands_while_the_board_is_gone`: Ready@0, presence false@10_000, true@30_000 → `ends_at == Some(110_000.0)`.
  - `time_up_opens_the_list_for_good`: tick@90_001 → Picking; KeepDrawing → `Err(Ignored)`, still Picking; the Picking clock has `ends_at None` and `left_ms == 0`. Picking always carries a paused clock.
  - `a_pick_gives_two_and_one`: guesser score 2, drawer 1. `niemand_gives_nothing`: all 0.
  - `the_drawer_or_a_stranger_cannot_be_picked` → `Err(NotAllowed(_))`.
  - `a_second_pick_scores_nothing`: Pick again in Reveal → `Err(Ignored)`, scores unchanged.
  - `six_seconds_later_the_next_drawer_is_up`: tick at reveal + 6_000 → Handover, drawer = turns[1], `drawing() == Some((2, &[][..]))`.
  - `the_last_reveal_ends_the_game` → Final, `drawing() == None`.
  - `skip_passes_the_turn_without_points`.
  - `strokes_count_only_while_drawing`: Draw in Handover or Picking → `Err(Ignored)`; in Drawing → appended.
- [ ] **Step 2:** FAIL. **Step 3:** Implement (views stubbed via Task 4's functions; write them together if simpler). **Step 4:** PASS.
- [ ] **Step 5: Commit** — `feat(spieltisch): Kritzelei's turns, clock and scoring`.

## Task 4: Views

**Files:** Create `kritzelei_views.rs` and `kritzelei_views_tests.rs`. Modify `lib.rs`: `mod kritzelei_views;`.

**Interfaces** (on `KritzeleiGame`, called by the `Game` impl):
`phone_view(&self, &ViewCtx, PlayerId) -> PhoneScreen`, `buzz(&self, PlayerId) -> Option<Buzz>`, `tv_view(&self, &ViewCtx) -> TvScene`, `board_view(&self, &ViewCtx) -> BoardScreen`.

| Stage | Drawer's phone | Others' phones | Board |
|---|---|---|---|
| Handover, Drawing, Picking | `Wait { "Du zeichnest – hol dir das iPad", None }`, buzz `Turn` | `Wait { "Rate laut!", Some("{drawer} zeichnet.") }` | Handover hides the word. Drawing and Picking carry it. Picking lists every **seated** player but the drawer, with `can_go_back = left > 0`. Drawing and Picking also carry `drawer` (the same `BoardPlayer` Handover carries) |
| Reveal | `Outcome { word, "{name} hat's erraten" / "Niemand hat's erraten", Good/Neutral, 1/0 }` | guesser: `Outcome { "Richtig!", Some(word), Good, 2 }`, buzz `Accepted`. The rest: `Outcome { word, same line, Neutral, 0 }`. All `show_score: true` | `Wait { word, Some(same line), can_leave: false }` |
| Final | `Final` from `rank_of`, `of = players.len()`, `can_restart = leader` | same | `Wait { "Spiel vorbei", Some("Die Siegerehrung läuft auf dem Fernseher."), false }` |

- [ ] **Step 1: Failing tests:**
  - `the_word_reaches_only_the_board_until_the_reveal`: in Handover, Drawing and Picking, the JSON of every phone screen and of the TV scene doesn't contain the word. The board JSON doesn't in Handover and does in Drawing and Picking. In Reveal, the TV JSON does.
  - `the_drawer_is_buzzed_for_the_turn_and_the_guesser_for_the_point`: only those two, and only in those stages.
  - `the_drawer_fetches_the_ipad_and_the_rest_guess_out_loud`: exact titles.
  - `picking_lists_everyone_seated_but_the_drawer`: includes a disconnected player.
  - `drawing_and_picking_name_the_drawer`: the board's Drawing and Picking screens carry the drawer as a `BoardPlayer`, also after the board reconnects mid-turn.
  - `after_time_up_the_list_cannot_go_back`.
  - `the_reveal_says_who_got_it`: Richtig! +2 Good; the drawer gets 1; others get „Bo hat's erraten“. With nobody: „Niemand hat's erraten“ and the drawer gets 0.
  - `the_final_ranks_by_score`.
  - `the_tv_shows_when_the_board_is_gone`: presence false → `scene.board == false`.
- [ ] **Step 2–4:** FAIL → implement → PASS. **Step 5: Commit** — `feat(spieltisch): Kritzelei's phone, TV and board views`.

## Task 5: Register the game

**Files:** Modify:
- `protocol_settings.rs`: `GameKind::Kritzelei`, with the doc comment "Kritzelei: one draws on the iPad, the room guesses out loud."
- `catalogue.rs`: `("Kritzelei", 2, ROOM_CAP)` with the comment "One draws, at least one guesses." It goes into `CATALOGUE` after `Preisfrage`.
- `factory.rs`: `GameKind::Kritzelei => Box::new(KritzeleiGame::new(KritzeleiConfig::default(), deck(settings.kids_only, &mut StdRng::from_os_rng()), ctx.players, ctx.now))`
- `room_settings.rs`: `pub(crate) const KRITZELEI_NEEDS_BOARD: &str = "Kritzelei braucht das iPad als Zeichenbrett.";` and an arm before the min/max check: `GameKind::Kritzelei if !self.board_connected() => return Some(KRITZELEI_NEEDS_BOARD.into())`. Kritzelei has no kids-mode block.
- Tests: `catalogue_tests.rs` and `room_settings_tests.rs`.

- [ ] **Step 1: Failing tests:**
  - `kritzelei_is_listed_for_two_to_eight`.
  - `kritzelei_waits_for_the_board`: 2 players without a board → `blocked() == Some(KRITZELEI_NEEDS_BOARD)`. After a board hello → `None`.
  - `kritzelei_plays_in_kids_mode`: kids on, a board, 2 players → `None`.
  - `kritzelei_needs_two_players`: a board and 1 player → „Kritzelei braucht mindestens 2 Spieler.“
- [ ] **Step 2–4:** FAIL → implement → PASS. Also run `cargo clippy -p mediagram-game --all-targets -- -D warnings` and `cd crates/mediagram-game/client && bun run typecheck`.
- [ ] **Step 5: Commit** — `feat(spieltisch): Kritzelei in the lobby, blocked until a board is there`.

## Success criteria

`cargo test -p mediagram-game` is green. A room started with a board connected runs Kritzelei from the first turn to Final in tests. The word never appears in a phone or TV view before its reveal.
