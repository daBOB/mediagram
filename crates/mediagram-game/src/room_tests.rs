use super::*;
use crate::game::ViewCtx;
use crate::protocol::{PhoneScreen, TvScene};
use crate::room_seats::{LOBBY_GRACE_MS, MAX_NAME_CHARS, clean_name};
use crate::room_settings::Readiness;

pub(crate) struct Idle;
impl Game for Idle {
    fn on_action(&mut self, _: &mut GameCtx, _: PlayerId, _: &Action) -> Result<(), ActionError> {
        Ok(())
    }
    fn on_tick(&mut self, _: &mut GameCtx) {}
    fn phone_screen(&self, _: &ViewCtx, _: PlayerId) -> PhoneScreen {
        PhoneScreen::Wait { title: "idle".into(), text: None }
    }
    fn tv_scene(&self, _: &ViewCtx) -> TvScene {
        TvScene::Lobby {
            min_players: 0,
            settings: crate::room_settings::initial_settings(false),
            leader_name: None,
            blocked: None,
        }
    }
    fn is_finished(&self) -> bool {
        false
    }
}

pub(crate) fn cfg(max: u8) -> RoomConfig {
    RoomConfig {
        join_url: "http://x/".into(),
        min_players: 1,
        max_players: max,
        kids_locked: false,
        readiness: Readiness::default(),
    }
}

pub(crate) fn room_with(cfg: RoomConfig) -> Room {
    Room::new(cfg, Box::new(|_, _| Box::new(Idle)))
}

pub(crate) fn room(max: u8) -> Room {
    room_with(cfg(max))
}

pub(crate) fn decode(json: &str) -> ServerMsg {
    serde_json::from_str(json).unwrap()
}

pub(crate) fn hello(room: &mut Room, conn: ConnId, name: &str, token: Option<&str>) -> ServerMsg {
    room.connect(conn, false);
    let msg = ClientMsg::Hello { role: Role::Phone, name: Some(name.into()), token: token.map(Into::into) };
    let out = room.handle(conn, &serde_json::to_string(&msg).unwrap(), 0.0);
    decode(&out[0].1)
}

pub(crate) fn action(room: &mut Room, conn: ConnId, action: Action) -> Outbox {
    let msg = ClientMsg::Action { action };
    room.handle(conn, &serde_json::to_string(&msg).unwrap(), 0.0)
}

#[test]
fn reconnect_with_token_keeps_seat() {
    let mut r = room(8);
    let ServerMsg::Welcome { player: Some(id), token: Some(token), .. } = hello(&mut r, 1, "Ana", None)
    else {
        panic!()
    };
    r.disconnect(1, 10.0);
    assert!(!r.players()[0].connected());
    let ServerMsg::Welcome { player: Some(again), .. } = hello(&mut r, 2, "Ana", Some(&token)) else {
        panic!()
    };
    assert_eq!(id, again);
    assert_eq!(r.players().len(), 1);
    assert!(r.players()[0].connected());
}

#[test]
fn lobby_seat_expires_after_grace() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    r.disconnect(1, 0.0);
    r.tick(LOBBY_GRACE_MS - 1.0);
    assert_eq!(r.players().len(), 1);
    r.tick(LOBBY_GRACE_MS + 1.0);
    assert!(r.players().is_empty());
}

#[test]
fn room_full_and_name_required() {
    let mut r = room(1);
    hello(&mut r, 1, "Ana", None);
    assert!(matches!(hello(&mut r, 2, "Ben", None), ServerMsg::Error { code: ErrorCode::RoomFull, .. }));
    assert!(matches!(hello(&mut r, 3, "   ", None), ServerMsg::Error { code: ErrorCode::NameRequired, .. }));
}

#[test]
fn only_leader_starts() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    assert!(!action(&mut r, 2, Action::StartGame).is_empty(), "Ben gets an error");
    assert!(!r.in_game());
    assert!(action(&mut r, 1, Action::StartGame).is_empty());
    assert!(r.in_game());
}

#[test]
fn flush_only_sends_changes() {
    let mut r = room(8);
    hello(&mut r, 1, "Ana", None);
    assert_eq!(r.flush(0.0).len(), 1);
    assert!(r.flush(0.0).is_empty());
    hello(&mut r, 2, "Ben", None);
    assert_eq!(r.flush(0.0).len(), 2, "player count changed for Ana too");
}

#[test]
fn names_are_cleaned() {
    assert_eq!(clean_name("  Ana \n  Maria  "), "Ana Maria");
    assert_eq!(clean_name("abcdefghijklmnopqrstuvwxyz").chars().count(), MAX_NAME_CHARS);
}

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

#[test]
fn unreadable_frame_gets_a_german_error_without_parser_detail() {
    let mut r = room(8);
    r.connect(1, false);
    let out = r.handle(1, "{not json", 0.0);
    let ServerMsg::Error { code: ErrorCode::BadMessage, message } = decode(&out[0].1) else {
        panic!("expected BadMessage")
    };
    assert_eq!(message, "Nachricht nicht lesbar.");
    assert!(!message.contains("expected"), "no serde wording");
}

fn hello_again(room: &mut Room, conn: ConnId, role: &str, name: &str) {
    let msg = format!(r#"{{"t":"hello","role":"{role}","name":"{name}","token":null}}"#);
    room.handle(conn, &msg, 0.0);
}

#[test]
fn a_second_hello_on_one_socket_frees_the_first_seat() {
    let mut r = room(8);
    r.connect(1, false);
    hello_again(&mut r, 1, "phone", "Ana");
    hello_again(&mut r, 1, "phone", "Ben");
    let ana = r.players().iter().find(|p| p.name == "Ana").unwrap();
    assert!(!ana.connected(), "the socket Ben took no longer holds Ana's seat");
    let ben = r.players().iter().find(|p| p.name == "Ben").unwrap().id;
    assert_eq!(r.leader(), Some(ben));
}

#[test]
fn a_tv_hello_on_a_seated_socket_frees_the_seat() {
    let mut r = room(8);
    r.connect(1, true);
    hello_again(&mut r, 1, "phone", "Ana");
    hello_again(&mut r, 1, "tv", "");
    assert!(!r.players()[0].connected());
    assert_eq!(r.leader(), None);
}
