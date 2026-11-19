use serde::Deserialize;
use serde::de::IntoDeserializer;
use serde::de::value::Error;

use super::*;

/// Every kind there is, read off serde's variant indices, so no list here has to keep up with new games.
fn every_kind() -> Vec<GameKind> {
    (0u32..).map_while(|i| GameKind::deserialize(IntoDeserializer::<Error>::into_deserializer(i)).ok()).collect()
}

#[test]
fn the_lobby_lists_every_game_exactly_once() {
    let kinds = every_kind();
    assert!(kinds.contains(&GameKind::Quiz) && kinds.contains(&GameKind::Durak), "{kinds:?}");
    for kind in &kinds {
        assert_eq!(CATALOGUE.iter().filter(|e| e.kind == *kind).count(), 1, "{kind:?}");
    }
    assert_eq!(CATALOGUE.len(), kinds.len());
}

#[test]
fn a_game_any_room_plays_is_listed_for_one_to_a_full_room() {
    assert_eq!(GameEntry::of(GameKind::Quiz), GameEntry { kind: GameKind::Quiz, name: "Buzzer-Quiz".into(), min: 1, max: 8, timed: true, sized: true });
}

#[test]
fn preisfrage_plays_alone_or_with_a_full_room() {
    assert_eq!(GameEntry::of(GameKind::Preisfrage), GameEntry { kind: GameKind::Preisfrage, name: "Preisfrage".into(), min: 1, max: 8, timed: true, sized: true });
}

#[test]
fn kritzelei_is_listed_for_two_to_eight() {
    assert_eq!(GameEntry::of(GameKind::Kritzelei), GameEntry { kind: GameKind::Kritzelei, name: "Kritzelei".into(), min: 2, max: 8, timed: true, sized: true });
}
