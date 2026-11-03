use crate::protocol::{Action, GameKind, LobbySettings, PhoneScreen, QuizMode, ServerMsg};
use crate::room::tests::{Idle, action, cfg, decode, hello, room, room_with};
use crate::room::{ConnId, Outbox, Room, RoomConfig};
use crate::room_settings::Readiness;
use std::sync::{Arc, Mutex};

fn lobby_of(room: &mut Room, conn: ConnId) -> PhoneScreen {
    match room.view_for(conn, 0.0) {
        Some(ServerMsg::Phone { view }) => view.screen,
        other => panic!("expected a phone view, got {other:?}"),
    }
}

fn configure(room: &mut Room, conn: ConnId, game: GameKind, quiz_mode: QuizMode, kids_only: bool) -> Outbox {
    action(room, conn, Action::Configure { game, quiz_mode, kids_only })
}

#[test]
fn locked_kids_toggle_stays_on() {
    let mut r = room_with(RoomConfig { kids_locked: true, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    assert!(r.settings().kids_only, "starts on");
    configure(&mut r, 1, GameKind::Quiz, QuizMode::Films, false);
    assert!(r.settings().kids_only);
}

#[test]
fn poster_game_needs_posters() {
    let mut r = room_with(RoomConfig { readiness: Readiness { poster: true, poster_kids: false }, ..cfg(8) });
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Poster, QuizMode::Mixed, true);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: false, blocked: Some(_), .. }));
    let out = action(&mut r, 1, Action::StartGame);
    let ServerMsg::Error { message, .. } = decode(&out[0].1) else { panic!("expected an error") };
    assert_eq!(message, "Nicht genug Poster für dieses Spiel.");
    assert!(!r.in_game());
    configure(&mut r, 1, GameKind::Poster, QuizMode::Mixed, false);
    assert!(matches!(lobby_of(&mut r, 1), PhoneScreen::Lobby { can_start: true, blocked: None, .. }));
}

#[test]
fn only_the_leader_configures() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    let before = r.settings().clone();
    assert!(!configure(&mut r, 2, GameKind::Poster, QuizMode::Films, true).is_empty());
    assert_eq!(*r.settings(), before);
}

#[test]
fn settings_survive_a_game() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Quiz, QuizMode::General, true);
    let chosen = r.settings().clone();
    action(&mut r, 1, Action::StartGame);
    assert!(r.in_game());
    assert!(!configure(&mut r, 1, GameKind::Quiz, QuizMode::Films, false).is_empty(), "not mid-game");
    r.back_to_lobby();
    assert_eq!(*r.settings(), chosen);
}

#[test]
fn factory_sees_the_settings() {
    let seen: Arc<Mutex<Option<LobbySettings>>> = Arc::default();
    let sink = seen.clone();
    let mut r = Room::new(
        cfg(8),
        Box::new(move |_, s| {
            *sink.lock().unwrap() = Some(s.clone());
            Box::new(Idle)
        }),
    );
    hello(&mut r, 1, "Ana", None);
    configure(&mut r, 1, GameKind::Quiz, QuizMode::Films, true);
    action(&mut r, 1, Action::StartGame);
    assert_eq!(seen.lock().unwrap().as_ref(), Some(r.settings()));
    assert_eq!(r.settings().quiz_mode, QuizMode::Films);
}
