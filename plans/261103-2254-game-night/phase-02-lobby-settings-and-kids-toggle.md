# Phase 02: Lobby settings: game choice and *Nur für Kinder*

Spec: "Lobby", "Which titles the games use" (toggle rules), approved mockup section 1 (`docs/superpowers/specs/2026-11-03-game-night-mockup.html`).

Outcome: the starter's phone picks *Buzzer-Quiz* (Filme / Allgemein / Gemischt) or *Poster-Raten* and the toggle; the TV shows the choice; a locked toggle cannot be switched off.

---

### Task 2.1: Settings in the protocol and the room

**Files:**
- Modify: `crates/mediagram-game/src/protocol.rs`, `src/game.rs`, `src/room.rs`, `src/room_views.rs`, `src/room_tests.rs`, `src/host.rs`, `src/bin/game-night-dev.rs`

**Interfaces:**
- Consumes: Task 1.1 `Room`, Task 1.3 `HostConfig`.
- Produces:
  - `enum GameKind { Quiz, Poster }`, `enum QuizMode { Films, General, Mixed }` (snake_case on the wire)
  - `struct LobbySettings { game: GameKind, quiz_mode: QuizMode, kids_only: bool, kids_locked: bool }`
  - `Action::Configure { game: GameKind, quiz_mode: QuizMode, kids_only: bool }`
  - `PhoneScreen::Lobby { players: u8, min_players: u8, can_start: bool, is_leader: bool, leader_name: Option<String>, settings: LobbySettings, blocked: Option<String> }`
  - `TvScene::Lobby { min_players: u8, settings: LobbySettings, leader_name: Option<String> }`
  - `struct Readiness { poster: bool, poster_kids: bool }` — default both `true`; phase 04 fills it from the pool
  - `RoomConfig` gains `kids_locked: bool`, `readiness: Readiness`; `HostConfig` gains the same two fields
  - `type GameFactory = Box<dyn FnMut(&mut GameCtx, &LobbySettings) -> Box<dyn Game> + Send>`
- Rules: initial settings `{ game: Quiz, quiz_mode: Mixed, kids_only: kids_locked, kids_locked }`. `Configure` only from the leader's phone and only in the lobby (else `NotAllowed`); `kids_only` is stored as `kids_only || kids_locked`. `can_start` = leader ∧ enough players ∧ not blocked. Blocked when `game == Poster` and the matching readiness flag is false: `blocked = Some("Nicht genug Poster für dieses Spiel.")`; `StartGame` then answers `NotAllowed` with that text.

- [ ] **Step 1: Write the failing tests** in `room_tests.rs`:

```rust
#[test]
fn locked_kids_toggle_stays_on() {
    let mut r = room_with(RoomConfig { kids_locked: true, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    action(&mut r, 1, Action::Configure { game: GameKind::Quiz, quiz_mode: QuizMode::Films, kids_only: false });
    assert!(r.settings().kids_only);
}

#[test]
fn poster_game_needs_posters() {
    let mut r = room_with(RoomConfig { readiness: Readiness { poster: true, poster_kids: false }, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    action(&mut r, 1, Action::Configure { game: GameKind::Poster, quiz_mode: QuizMode::Mixed, kids_only: true });
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(_), .. }));
    assert!(!action(&mut r, 1, Action::StartGame).is_empty());
    assert!(!r.in_game());
}
```

  plus `only_the_leader_configures` (Ben's `Configure` → error, settings unchanged), `settings_survive_a_game` (configure → start → `back_to_lobby` → same settings), `factory_sees_the_settings` (factory stores the `&LobbySettings` it got in an `Arc<Mutex<Option<_>>>`; equals what was configured).
- [ ] **Step 2: Run** `cargo test -p mediagram-game room` — new tests FAIL.
- [ ] **Step 3: Implement** the settings, rules and factory signature; `Room::settings(&self) -> &LobbySettings` for tests and views. The dev binary gets `--kids-locked`.
- [ ] **Step 4: Run** `cargo test -p mediagram-game` — PASS; clippy clean.
- [ ] **Step 5: Commit** `feat(game): lobby chooses the game and the kids-only toggle`

### Task 2.2: Lobby screens on phone and TV

**Files:**
- Modify: `crates/mediagram-game/client/src/phone/PhoneApp.tsx` (lobby part may move to `phone/PhoneLobby.tsx`), `client/src/tv/TvApp.tsx` (lobby part may move to `tv/TvLobby.tsx`), `client/src/styles.css`, `crates/mediagram-game/tools/smoke.mjs`

Copy, exactly as in the approved mockup:
- Leader phone: kicker `Du startest`, title `Was spielen wir?`, game `Buzzer-Quiz` with segments `Filme` · `Allgemein` · `Gemischt`, game `Poster-Raten`, toggle `Nur für Kinder` (when locked: switched on, not operable, hint `Auf diesem Profil immer an`), `blocked` text above the button, button `Los geht's`.
- Other phones: `{leader_name} wählt das Spiel …`
- TV: kicker `Spieleabend`, headline `Handy-Kamera auf den Code`, the address without `http://`, badge `Buzzer-Quiz · Filme|Allgemein|Gemischt` or `Poster-Raten`, badge `Nur für Kinder` when on, player chips with `★` on the leader, footer `{n} am Tisch · {leader_name} startet, oder OK auf der Fernbedienung`, a focused start button for the remote's OK.

- [ ] **Step 1: Implement** both screens; every tap on a setting sends one `configure` with all three values.
- [ ] **Step 2: Run** `cd crates/mediagram-game/client && bun run build` — clean.
- [ ] **Step 3: Extend the smoke test:** before starting, Ana sends `{ t: "action", action: { t: "configure", game: "quiz", quiz_mode: "general", kids_only: false } }` and the script prints the TV badge state from `scene.settings`. Run it as in Task 1.4 — expected `final scores:` and `after back_to_lobby: lobby`.
- [ ] **Step 4: Look at it:** dev binary + `http://127.0.0.1:8080/tv` in a desktop browser + `http://127.0.0.1:8080/` in a narrow (phone-size) window; compare against the mockup.
- [ ] **Step 5: Commit** `feat(game): lobby screens with game picker and kids toggle`
