use super::tests::{action, cfg, decode, room};
use super::*;
use crate::avatars::AVATARS;

fn join(r: &mut Room, conn: ConnId, name: &str, token: Option<&str>, avatar: Option<&str>) -> ServerMsg {
    r.connect(conn, false);
    let msg = ClientMsg::Hello {
        role: Role::Phone,
        name: Some(name.into()),
        token: token.map(Into::into),
        avatar: avatar.map(Into::into),
    };
    decode(&r.handle(conn, &serde_json::to_string(&msg).unwrap(), 0.0)[0].1)
}

fn pick(r: &mut Room, conn: ConnId, id: &str) -> bool {
    action(r, conn, Action::PickAvatar { id: id.into() }).is_empty()
}

fn avatar_of(r: &Room, id: PlayerId) -> Option<&'static str> {
    r.player(id).and_then(|p| p.avatar)
}

#[test]
fn every_player_gets_a_distinct_avatar() {
    let mut r = room(8);
    for i in 0..8 {
        join(&mut r, i + 1, &format!("P{i}"), None, None);
    }
    let mut seen: Vec<_> = r.players().iter().map(|p| p.avatar.expect("assigned")).collect();
    seen.sort();
    seen.dedup();
    assert_eq!(seen.len(), 8);
}

#[test]
fn preference_is_honoured_and_falls_back_when_taken_or_unknown() {
    let mut r = room(8);
    join(&mut r, 1, "Ana", None, Some("fox"));
    join(&mut r, 2, "Ben", None, Some("fox"));
    join(&mut r, 3, "Cy", None, Some("nonsense"));
    assert_eq!(avatar_of(&r, 0), Some("fox"));
    assert!(avatar_of(&r, 1).is_some_and(|a| a != "fox"));
    assert!(avatar_of(&r, 2).is_some_and(|a| AVATARS.contains(&a)));
}

#[test]
fn picking_a_free_avatar_swaps_and_frees_the_old_one() {
    let mut r = room(8);
    join(&mut r, 1, "Ana", None, Some("fox"));
    join(&mut r, 2, "Ben", None, Some("cat"));
    assert!(pick(&mut r, 1, "yeti"));
    assert_eq!(avatar_of(&r, 0), Some("yeti"));
    assert!(pick(&mut r, 2, "fox"), "fox was freed");
}

#[test]
fn picking_a_taken_or_unknown_avatar_is_refused() {
    let mut r = room(8);
    join(&mut r, 1, "Ana", None, Some("fox"));
    join(&mut r, 2, "Ben", None, Some("cat"));
    assert!(!pick(&mut r, 2, "fox"));
    assert!(!pick(&mut r, 2, "nonsense"));
    assert_eq!(avatar_of(&r, 1), Some("cat"));
}

#[test]
fn picking_during_a_game_is_refused() {
    let mut r = room(8);
    join(&mut r, 1, "Ana", None, Some("fox"));
    action(&mut r, 1, Action::StartGame);
    assert!(r.in_game());
    assert!(!pick(&mut r, 1, "yeti"));
    assert_eq!(avatar_of(&r, 0), Some("fox"));
}

#[test]
fn reconnect_keeps_the_avatar_whatever_the_hello_asks() {
    let mut r = room(8);
    let ServerMsg::Welcome { token: Some(token), .. } = join(&mut r, 1, "Ana", None, Some("fox")) else {
        panic!()
    };
    r.disconnect(1, 10.0);
    join(&mut r, 2, "Ana", Some(&token), Some("cat"));
    assert_eq!(r.players().len(), 1);
    assert_eq!(avatar_of(&r, 0), Some("fox"));
}

#[test]
fn a_player_who_left_frees_the_avatar() {
    let mut r = Room::new(cfg(8), Box::new(|_, _| unreachable!()));
    join(&mut r, 1, "Ana", None, Some("fox"));
    r.disconnect(1, 10.0);
    r.tick(10.0 + crate::room_seats::LOBBY_GRACE_MS + 1.0);
    assert!(r.players().is_empty());
    join(&mut r, 2, "Ben", None, Some("fox"));
    assert_eq!(avatar_of(&r, 1), Some("fox"));
}

#[test]
fn hello_without_avatar_field_still_parses() {
    let msg: ClientMsg = serde_json::from_str(r#"{"t":"hello","role":"phone","name":"A","token":null}"#).unwrap();
    assert!(matches!(msg, ClientMsg::Hello { avatar: None, .. }));
}
