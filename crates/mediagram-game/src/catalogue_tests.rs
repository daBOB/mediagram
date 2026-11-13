use super::*;

#[test]
fn every_game_kind_is_listed_exactly_once() {
    use GameKind::*;
    let kinds = [Quiz, Poster, Race, MauMau, Schwimmen, Durak, Kopfnuss, Dreifelder, GrandAllee, Fluechtig, Preisfrage, Kritzelei];
    for kind in kinds {
        // No wildcard: a new kind stops this test compiling until it joins `kinds`.
        match kind {
            Quiz | Poster | Race | MauMau | Schwimmen | Durak | Kopfnuss | Dreifelder | GrandAllee | Fluechtig | Preisfrage | Kritzelei => {}
        }
        assert_eq!(CATALOGUE.iter().filter(|e| e.kind == kind).count(), 1, "{kind:?}");
    }
    assert_eq!(CATALOGUE.len(), kinds.len());
}

#[test]
fn preisfrage_plays_alone_or_with_a_full_room() {
    assert_eq!(entry(GameKind::Preisfrage), GameEntry { kind: GameKind::Preisfrage, name: "Preisfrage".into(), min: 1, max: 8 });
}

#[test]
fn kritzelei_is_listed_for_two_to_eight() {
    assert_eq!(entry(GameKind::Kritzelei), GameEntry { kind: GameKind::Kritzelei, name: "Kritzelei".into(), min: 2, max: 8 });
}
