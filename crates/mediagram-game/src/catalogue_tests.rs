use super::*;

#[test]
fn every_game_kind_is_listed_exactly_once() {
    use GameKind::*;
    let kinds = [Quiz, Poster, Race, MauMau, Schwimmen, Durak, Kopfnuss, GrandAllee];
    for kind in kinds {
        // No wildcard: a new kind stops this test compiling until it joins `kinds`.
        match kind {
            Quiz | Poster | Race | MauMau | Schwimmen | Durak | Kopfnuss | GrandAllee => {}
        }
        assert_eq!(CATALOGUE.iter().filter(|e| e.kind == kind).count(), 1, "{kind:?}");
    }
    assert_eq!(CATALOGUE.len(), kinds.len());
}
