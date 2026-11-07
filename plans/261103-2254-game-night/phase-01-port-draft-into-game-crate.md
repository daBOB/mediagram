# Phase 01: Port the draft into `crates/mediagram-game`

Spec: "Hosting and joining", "Game 1: Buzzer-Quiz" (rules), Constraints. Draft files: `crates/protocol/src/lib.rs`, `crates/core/src/{game,room}.rs`, `crates/game-quiz/src/{lib,questions}.rs`, `crates/server/src/main.rs`, `client/**`, `tools/smoke.mjs` under `/home/andre/Downloads/spieltisch/`.

Outcome: `cargo run -p mediagram-game --bin game-night-dev` hosts the draft's quiz on the LAN, the bun-built client is embedded, and the draft's bot smoke test plays a full game against it.

---

### Task 1.1: Crate, wire protocol, `Game` trait, `Room`

**Files:**
- Modify: `Cargo.toml` (add member `crates/mediagram-game`; workspace deps `rand = "0.9"`, `ts-rs`, `rust-embed`, `qrcode`)
- Create: `.cargo/config.toml` — `[env] TS_RS_EXPORT_DIR = { value = "crates/mediagram-game/client/src/protocol", relative = true }`
- Create: `crates/mediagram-game/Cargo.toml`, `src/lib.rs`, `src/protocol.rs`, `src/game.rs`, `src/room.rs`, `src/room_seats.rs`, `src/room_views.rs`, `src/room_tests.rs`

**Interfaces:**
- Produces: `protocol::{ClientMsg, Action, ServerMsg, ErrorCode, PlayerInfo, PhoneView, PhoneScreen, ChoiceOption, AnswerColor, Tone, TvView, TvScene, Role, PlayerId, ServerTime}` (draft shapes); `game::{Game, GameCtx, ViewCtx, ActionError, GameFactory}`; `room::{Room, RoomConfig, Player, ConnId, Outbox}` with
  - `Room::new(cfg: RoomConfig, factory: GameFactory) -> Room`
  - `Room::connect(&mut self, conn: ConnId, local: bool)` — `local` = the peer is loopback
  - `Room::handle(&mut self, conn: ConnId, text: &str, now: ServerTime) -> Outbox`, `tick`, `flush`, `disconnect`, `in_game`, `leader`, `players`
  - `Room::back_to_lobby(&mut self) -> bool` — ends a running game; `true` if one was running

- [ ] **Step 1: Copy the draft types and room, then port.** Edition 2024; rand 0.9 (`rand::rng().random::<[u8; 16]>()` for tokens); keep the draft's German messages; split `room.rs` so seating/name cleaning/tokens live in `room_seats.rs` and view building in `room_views.rs`.
- [ ] **Step 2: Move the draft's six room tests into `room_tests.rs`** (`reconnect_with_token_keeps_seat`, `lobby_seat_expires_after_grace`, `room_full_and_name_required`, `only_leader_starts`, `flush_only_sends_changes`, `names_are_cleaned`), calling `connect(conn, false)`.
- [ ] **Step 3: Write the new failing tests**

```rust
#[test]
fn tv_hello_from_the_lan_is_refused() {
    let mut r = room(8);
    r.connect(9, false);
    let out = r.handle(9, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    assert!(matches!(decode(&out[0].1), ServerMsg::Error { code: ErrorCode::NotAllowed, .. }));
    r.connect(10, true);
    let out = r.handle(10, r#"{"t":"hello","role":"tv","name":null,"token":null}"#, 0.0);
    assert!(matches!(decode(&out[0].1), ServerMsg::Welcome { player: None, .. }));
}

#[test]
fn back_to_lobby_reports_whether_a_game_ran() {
    let mut r = room(8);
    assert!(!r.back_to_lobby());
    hello(&mut r, 1, "Ana", None);
    action(&mut r, 1, Action::StartGame);
    assert!(r.back_to_lobby());
    assert!(!r.in_game());
}
```

- [ ] **Step 4: Run** `cargo test -p mediagram-game room` — expect the two new tests FAIL, the six ported PASS.
- [ ] **Step 5: Implement** the loopback check in the `Hello { role: Tv }` branch (error text `"Der Fernseher-Bildschirm läuft nur auf der Box."`) and `back_to_lobby`.
- [ ] **Step 6: Run** `cargo test -p mediagram-game` — all PASS; `cargo clippy -p mediagram-game --all-targets -- -D warnings` clean. The ts-rs export tests write `crates/mediagram-game/client/src/protocol/*.ts`.
- [ ] **Step 7: Commit** `feat(game): game room and wire protocol in mediagram-game`

### Task 1.2: The quiz

**Files:**
- Create: `crates/mediagram-game/src/quiz.rs` (state machine), `src/quiz_views.rs` (phone/TV views), `src/quiz_tests.rs`, `src/starter.rs`

**Interfaces:**
- Consumes: Task 1.1 `Game`, `GameCtx`, `Player::for_test`.
- Produces:
  - `pub struct Question { pub category: String, pub text: String, pub correct: String, pub wrong: [String; 3], pub image: Option<String> }`
  - `pub struct QuizConfig` (draft fields and defaults)
  - `Quiz::new(cfg: QuizConfig, questions: Vec<Question>, now: ServerTime) -> Quiz` — asks the first `cfg.rounds` questions **in the given order**; only option order is shuffled (seeded)
  - `starter::starter_questions() -> Vec<Question>` — the draft's 14, `image: None`
  - `protocol::QuizQuestionView` gains `image: Option<String>`

- [ ] **Step 1: Port the draft quiz** with the new owned `Question` and order rule; move its seven tests to `quiz_tests.rs` using `starter_questions()`.
- [ ] **Step 2: Write the failing test**

```rust
#[test]
fn question_order_is_kept() {
    let qs = starter_questions();
    let quiz = Quiz::new(QuizConfig { rounds: 3, seed: Some(1), ..Default::default() }, qs.clone(), 0.0);
    let texts: Vec<_> = quiz.round_texts();
    assert_eq!(texts, qs.iter().take(3).map(|q| q.text.clone()).collect::<Vec<_>>());
}
```

  (`round_texts()` is a `#[cfg(test)]` helper on `Quiz`.)
- [ ] **Step 3: Run** `cargo test -p mediagram-game quiz` — new test FAILS until the order rule is in; then implement; all eight PASS.
- [ ] **Step 4: Commit** `feat(game): buzzer quiz with timed, fair answers`

### Task 1.3: Server, host handle, LAN address, dev binary

**Files:**
- Create: `crates/mediagram-game/build.rs` (`cargo:rerun-if-changed=client/dist`), `src/server.rs`, `src/host.rs`, `src/lan.rs`, `src/server_tests.rs`, `src/bin/game-night-dev.rs`
- Modify: `crates/mediagram-game/Cargo.toml` — `axum = { workspace = true, features = ["ws"] }`, `tokio` (`rt-multi-thread, macros, sync, time, net`), `futures` (workspace), `rust-embed` with `debug-embed` + `mime-guess` (debug builds must embed too: the Android `.so` cannot read the dev machine's disk), `qrcode` (`svg`, no defaults), `tracing`

**Interfaces:**
- Produces:
  - `pub struct HostConfig { pub preferred_port: u16, pub min_players: u8, pub max_players: u8 }` — defaults 8080, 1, 8
  - `Host::start(cfg: HostConfig, factory: GameFactory) -> std::io::Result<Host>` (async) — binds `0.0.0.0:preferred_port`, falls back to an OS-chosen port if taken
  - `Host::join_url(&self) -> String` — `http://{lan_ip}:{port}/`; `Host::tv_url(&self) -> String` — `http://127.0.0.1:{port}/tv`; `Host::port(&self) -> u16`
  - `Host::back(&self) -> bool` (async) — `Room::back_to_lobby` through the room task
  - `Host::stop(self)` (async) — returns once the listener is closed and the room task ended
  - `lan::lan_ip() -> Option<IpAddr>` — UDP socket `connect` to a public address, read `local_addr`; sends nothing
- Routes: `/ws` (peer from `ConnectInfo<SocketAddr>`; `local = peer.ip().is_loopback()`), `/qr.svg` (encodes `join_url`), static fallback from `client/dist` with the draft's app-shell rule. Draft limits: 50 ms tick, 16 KiB frames. The room loop is the draft's, plus an `Event::Back(oneshot::Sender<bool>)`.

- [ ] **Step 1: Write the failing tests** in `server_tests.rs` (tokio, no new dev-deps; HTTP by hand over `TcpStream`):
  - `stop_frees_the_port` — start on a free port, `TcpStream::connect` succeeds; `stop().await`; connect now fails.
  - `falls_back_when_preferred_port_is_taken` — hold a `TcpListener` on port P; `Host::start(HostConfig { preferred_port: P, .. })` succeeds with `port() != P`.
  - `qr_svg_is_served` — `GET /qr.svg` → status line `200`, header `content-type: image/svg+xml`, body contains `<svg`.
- [ ] **Step 2: Run** `cargo test -p mediagram-game server` — FAIL (no `Host`).
- [ ] **Step 3: Implement** `server.rs`, `host.rs`, `lan.rs` (axum 0.8: `Message::Text(Utf8Bytes)`), and the dev binary: starts a `Host` with a factory building `Quiz::new(QuizConfig::default(), starter_questions(), now)`, prints the terminal QR, phone URL and TV URL (the draft's banner), runs until Ctrl-C.
- [ ] **Step 4: Run** `cargo test -p mediagram-game` — PASS; clippy clean.
- [ ] **Step 5: Commit** `feat(game): LAN host with QR join, WebSocket room and dev binary`

### Task 1.4: Web client built with bun, smoke test, checks

**Files:**
- Create: `crates/mediagram-game/client/**` from the draft's `client/` (not `package-lock.json`; add `bun.lock`), `crates/mediagram-game/tools/smoke.mjs`
- Modify: `.gitignore` (`crates/mediagram-game/client/node_modules/`, `crates/mediagram-game/client/dist/`), `scripts/check.sh` (new step after "bun test")

- [ ] **Step 1: Copy the client** and point it at the generated protocol files from Task 1.1 (`QuizQuestionView.image` exists now; the TV quiz shows `<img>` above the question text when set).
- [ ] **Step 2: Run** `cd crates/mediagram-game/client && bun install && bun run build` — expect `tsc --noEmit` clean and `dist/` written.
- [ ] **Step 3: Add the check step:**

```bash
step "game client typecheck"
if [ -d crates/mediagram-game/client/node_modules ]; then
  (cd crates/mediagram-game/client && bun run typecheck)
else
  echo "skipping: game client dependencies are not installed (run 'cd crates/mediagram-game/client && bun install')"
fi
```

- [ ] **Step 4: Smoke test.** Rebuild (`cargo build -p mediagram-game`, so the fresh `dist/` is embedded), run `cargo run -p mediagram-game --bin game-night-dev`, then `bun crates/mediagram-game/tools/smoke.mjs ws://127.0.0.1:8080/ws`. Expected output ends with `final scores: Ana=…, Ben=…` and `after back_to_lobby: lobby`. Open `http://127.0.0.1:8080/tv` in a desktop browser: the lobby with QR shows.
- [ ] **Step 5: Run** `scripts/check.sh` — `all checks passed`.
- [ ] **Step 6: Commit** `feat(game): phone and TV web client, built with bun and embedded`
