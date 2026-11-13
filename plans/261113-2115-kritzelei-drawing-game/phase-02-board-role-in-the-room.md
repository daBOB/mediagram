# Phase 02 — The board role in the room

**Priority:** after the phase 01 gate. **Status:** todo. **Spec:** my calls 1, 4, 6, 9 (board role, board view, strokes to the TV, buzzing).

## Overview

The protocol and `Room` learn a third role, the board. That covers its token, its view, actions only it may send, the drawing relay (`ServerMsg::Draw`) and a buzz the game can ask for. No game uses any of it yet. A test game drives the room tests.

## Key insights

- Views are snapshots, compared as JSON per connection (`room_views.rs` `flush`, `Conn::last_view`). The drawing stays out of them. A per-connection cursor `Conn::drawn: Option<(u32, usize)>` (turn, ops sent) sends only the tail.
- `release(conn)` (`room_seats.rs`) runs on every hello and on disconnect. It is also where a board lets go.
- `room_loop` sends `handle`'s replies first, then `flush`. Within `flush`, a connection's `Draw` must go before its view, so a reconnecting page already holds the drawing when the board screen arrives.
- The board is not a player: no seat, no score, and it doesn't count in `connected_count`.

## Task 1: Wire types

**Files:**
- Modify: `crates/mediagram-game/src/protocol_client.rs` — `Role::Board`; `Action::Board { act: BoardAct }` ("From the drawing board only.")
- Create: `crates/mediagram-game/src/protocol_board.rs` — `BoardAct`, `DrawOp`, `BoardScreen`, `BoardPlayer`, `Clock`, `Buzz`, all `#[ts(export)]`
- Modify: `crates/mediagram-game/src/protocol.rs` — re-exports. Add `ServerMsg::Board { screen: BoardScreen }` and `ServerMsg::Draw { turn: u32, from: u32, ops: Vec<DrawOp> }`. Add `PhoneView.buzz: Option<Buzz>` with `#[serde(default)]`
- Modify: `crates/mediagram-game/src/protocol_tv.rs` — `TvScene::Lobby` gains `board: bool` ("a drawing board is connected")
- Modify: `crates/mediagram-game/src/lib.rs` — `mod protocol_board;`
- Test: `crates/mediagram-game/src/protocol_board_tests.rs`

**Interfaces (produces):**

```rust
#[serde(tag = "t", rename_all = "snake_case")]
pub enum BoardAct { Ready, Skip, Guessed, KeepDrawing, Pick { player: Option<PlayerId> }, Draw { op: DrawOp } }

#[serde(tag = "t", rename_all = "snake_case")]
pub enum DrawOp { Line { color: u8, width: u8, points: Vec<u16> }, More { points: Vec<u16> }, Undo, Clear }

#[serde(tag = "kind", rename_all = "snake_case")]
pub enum BoardScreen {
    /// Rendered by the page: lobby, between turns, the reveal, another game.
    Wait { title: String, text: Option<String>, can_leave: bool },
    /// Native. The word stays hidden until "Ich bin dran".
    Handover { turn: u32, drawer: BoardPlayer },
    Drawing { turn: u32, drawer: BoardPlayer, word: String, clock: Clock },
    /// The who-got-it list. `can_go_back` is false once the time is up.
    Picking { turn: u32, drawer: BoardPlayer, word: String, clock: Clock, guessers: Vec<BoardPlayer>, can_go_back: bool },
}
pub struct BoardPlayer { pub id: PlayerId, pub name: String, pub color: String, pub avatar: Option<String> }
/// Runs while `ends_at` is set; paused with `left_ms` otherwise.
pub struct Clock { pub ends_at: Option<ServerTime>, pub left_ms: f64, pub total_ms: f64 }
#[serde(rename_all = "snake_case")]
pub enum Buzz { Turn, Accepted, Wrong, Won }
```

- [ ] **Step 1: Write the failing tests** in `protocol_board_tests.rs`. Each one asserts exact JSON with `serde_json::to_string` and parses it back:
  - `board_acts_have_the_shapes_the_page_and_the_app_send`:
    - `{"t":"ready"}` and `{"t":"keep_drawing"}`
    - `{"t":"pick","player":3}` and `{"t":"pick","player":null}`
    - `{"t":"draw","op":{"t":"line","color":0,"width":1,"points":[10,20,30,40]}}`
    - `{"t":"draw","op":{"t":"more","points":[50,60]}}`
    - `{"t":"draw","op":{"t":"undo"}}`
  - `a_board_hello_and_action_parse`: `{"t":"hello","role":"board","name":null,"token":null}` → `ClientMsg::Hello { role: Role::Board, .. }`; `{"t":"action","action":{"t":"board","act":{"t":"draw","op":{"t":"clear"}}}}` parses (clear is a `DrawOp`, not a `BoardAct`).
  - `draw_and_board_messages_have_their_shapes`:
    - `ServerMsg::Draw { turn: 2, from: 0, ops: vec![] }` → `{"t":"draw","turn":2,"from":0,"ops":[]}`
    - a `ServerMsg::Board` with a `Handover` → `{"t":"board","screen":{"kind":"handover","turn":1,"drawer":{"id":0,"name":"Ada","color":"#ef476f","avatar":null}}}`
    - a `Drawing` → `{"t":"board","screen":{"kind":"drawing","turn":1,"drawer":{"id":0,"name":"Ada","color":"#ef476f","avatar":null},"word":"Hund","clock":{"ends_at":null,"left_ms":90000.0,"total_ms":90000.0}}}`
  - `a_phone_view_without_buzz_still_parses`: a `PhoneView` JSON with no `buzz` key → `buzz == None`.
- [ ] **Step 2:** `cargo test -p mediagram-game protocol_board` → FAIL (types missing).
- [ ] **Step 3:** Add the types and fields. Every existing `PhoneView { .. }` and `TvScene::Lobby { .. }` construction gets `buzz: None` / `board: false`. The compiler lists them.
- [ ] **Step 4:** `cargo test -p mediagram-game` → PASS. Check that `git status crates/mediagram-game/client/src/protocol` shows the new and changed `.ts` files. Run `cd crates/mediagram-game/client && bun run typecheck`, and fix any client fixture that builds a `PhoneView` or lobby `TvScene` (add `buzz: null` / `board: false`).
- [ ] **Step 5: Commit** — `feat(spieltisch): wire types for a drawing board`.

## Task 2: The board seat, its view and the drawing relay

**Files:**
- Modify: `crates/mediagram-game/src/game.rs` — five trait methods, each with a default (below)
- Create: `crates/mediagram-game/src/room_board.rs` — `BoardSeat` and the board's half of `Room`
- Modify: `crates/mediagram-game/src/room.rs`:
  - `Room.board: Option<BoardSeat>`
  - a `Hello { role: Role::Board, token, .. }` arm that calls `seat_board`
  - dispatch lines only: the logic lives in `room_board.rs`
  - an `Action::Board { act }` arm: only from a `Role::Board` conn, otherwise `not_allowed()`. Without a game it is `Ok(())`. With a game it goes to `game.on_board(ctx, &act)`, with errors mapped like phone actions
- Modify: `crates/mediagram-game/src/lib.rs` — `mod room_board;`
- Modify: `crates/mediagram-game/src/room_seats.rs`:
  - `Conn.drawn: Option<(u32, usize)>`
  - `release` also calls `release_board`
  - `new_token` becomes `pub(crate)`
- Modify: `crates/mediagram-game/src/room_views.rs`:
  - `Role::Board` → `ServerMsg::Board { screen }`, from `game.board_screen` or, in the lobby, `Wait { title: "Zeichenbrett", text: Some("Das iPad ist das Zeichenbrett. Es geht los, sobald die Spielleitung startet."), can_leave: true }`
  - `TvScene::Lobby.board = self.board_connected()`
  - `PhoneView.buzz = game.phone_buzz(..)`
  - in `flush`, `draw_for(id)` is pushed before the view
- Test: `crates/mediagram-game/src/room_board_tests.rs` (`#[path]` from `room.rs`). It uses a test game `Sketch` that:
  - records `on_board` acts and presence calls
  - serves `drawing()` from a `(u32, Vec<DrawOp>)` the test sets
  - returns `Some(Buzz::Turn)` for player 0

**Interfaces:**

```rust
// game.rs — trait Game; defaults keep every other game unchanged
fn on_board(&mut self, _ctx: &mut GameCtx, _act: &BoardAct) -> Result<(), ActionError> { Err(ActionError::Ignored) }
/// The board connected (`true`) or went away (`false`) while this game runs.
fn on_board_presence(&mut self, _now: ServerTime, _present: bool) {}
fn board_screen(&self, _view: &ViewCtx) -> BoardScreen  // default: Wait { "Zeichenbrett", Some("Gerade läuft ein anderes Spiel."), can_leave: true }
/// The current turn's drawing: its turn number and every op so far, append-only within a turn.
fn drawing(&self) -> Option<(u32, &[DrawOp])> { None }
fn phone_buzz(&self, _view: &ViewCtx, _player: PlayerId) -> Option<Buzz> { None }

// room_board.rs
pub(crate) struct BoardSeat { pub(crate) token: String, pub(crate) conn: Option<ConnId> }
impl Room {
    /// Welcome with the board token, or NotAllowed "Ein anderes Gerät ist gerade das Zeichenbrett."
    /// when a game runs and `token` isn't the board's.
    pub(crate) fn seat_board(&mut self, conn: ConnId, token: Option<String>, now: ServerTime) -> ServerMsg;
    /// In the lobby the board is dropped; during a game it keeps its token and the game hears `present = false`.
    pub(crate) fn release_board(&mut self, conn: ConnId, now: ServerTime);
    pub(crate) fn board_connected(&self) -> bool;
    /// The `Draw` message `conn` still needs, if it is a TV or the board; advances its cursor.
    pub(crate) fn draw_for(&mut self, conn: ConnId) -> Option<String>;
}
```

`seat_board` order:
1. Refuse a wrong token while a game runs.
2. `release(conn)` (frees a player seat).
3. Clear the role of a different conn that held the board.
4. Keep the token if it matched, otherwise `new_token()`.
5. Set role `Board` and `drawn = None`.
6. Call `on_board_presence(now, true)` if a game runs.

`release_board(conn)` acts only if `conn` still holds the board. A late close from a socket the board has since left must not mark the new one absent. When the room creates a game it calls `on_board_presence(now, board_connected())` right after, so a game started with the board seated knows it.

`draw_for`: `from` is the conn's count when the turn matches **and** count ≤ ops.len(), else 0. Send nothing when the cursor already equals `(turn, len)`. With no game or no drawing, reset the cursor to `None`.

- [ ] **Step 1: Write the failing tests** (`room_board_tests.rs`):
  - `a_board_gets_a_token_and_no_seat`: board hello → `Welcome { player: None, token: Some(_) }`; `players()` unchanged.
  - `a_phone_that_becomes_the_board_gives_up_its_seat`: phone hello, then board hello on the same conn → that player has `conn == None` and `left_at == Some(_)`.
  - `in_the_lobby_another_device_takes_the_board_over`: board on conn 1, board hello without a token on conn 2 → conn 2 welcomed with a different token; after `flush`, conn 1 gets no `board` message.
  - `during_a_game_only_the_token_holder_gets_the_board_back`: start a game; the board disconnects; conn 2 without a token → `Error { code: NotAllowed }`; conn 3 with the token → `Welcome` with the same token.
  - `the_game_hears_the_board_go_and_come_back`: during a game, disconnect then re-hello with the token → Sketch saw presence `[false, true]`.
  - `board_acts_count_only_from_the_board`: `Action::Board` from a phone → `Error { code: NotAllowed }`; from the board → Sketch got the act.
  - `tv_and_board_get_the_drawing_once_then_only_what_was_added`:
    - Sketch holds turn 1 with 2 ops → `flush` gives TV and board `Draw { turn: 1, from: 0, ops: [2 ops] }`, and phones none.
    - Add 1 op → `Draw { from: 2, ops: [1 op] }`.
    - Unchanged → no `draw` message.
  - `a_tv_that_connects_mid_turn_gets_the_whole_drawing`: a second TV conn after 3 ops → `Draw { from: 0 }` with all 3.
  - `a_new_turn_starts_the_drawing_afresh`: turn 1 → 2 with no ops → `Draw { turn: 2, from: 0, ops: [] }`.
  - `a_cursor_past_the_drawing_starts_afresh`: TV saw `(1, 5)`, the game now has turn 1 with 2 ops (a new game) → `Draw { from: 0 }` with 2 ops, no panic.
  - `draw_goes_out_before_the_view`: in one `flush`, a conn's `draw` precedes its `tv`/`board` message.
  - `the_phone_view_carries_the_buzz_the_game_asks_for`: player 0's `PhoneView.buzz == Some(Buzz::Turn)`, player 1's `None`.
  - `a_late_close_of_the_old_board_keeps_the_new_one`: board on conn 1 during a game, same token re-hellos on conn 2, then conn 1 closes → Sketch saw no `false` after the second `true`, and `board_connected()` is still true.
  - `a_game_started_with_the_board_seated_hears_it`: board hello in the lobby, then the room creates a game → Sketch's first presence call is `true`; with no board it is `false`.
  - `leaving_the_board_in_the_lobby_frees_it`: the board conn says phone hello → the TV lobby scene has `board == false`.
  - `the_tv_lobby_shows_a_connected_board`: board hello → `board == true`.
- [ ] **Step 2:** `cargo test -p mediagram-game room_board` → FAIL.
- [ ] **Step 3:** Implement as specified above.
- [ ] **Step 4:** `cargo test -p mediagram-game` → PASS (all old room tests too). `cargo clippy -p mediagram-game --all-targets -- -D warnings` → clean.
- [ ] **Step 5: Commit** — `feat(spieltisch): a drawing board joins the room beside phones and the TV`.

## Success criteria

All room tests pass. The board is invisible to every existing game: default hooks, and no change to phone or TV views except `board: false` / `buzz: None`.

## Security

Only a `Role::Board` conn reaches `on_board`. During a game, only the token holder retakes the board, so no other device can grab it and read the word. The role isn't tied to the iPad on the box side (the box can't tell an iPad). The page only offers it where the app says it is an iPad.
