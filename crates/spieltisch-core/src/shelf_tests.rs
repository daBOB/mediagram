use super::*;

#[test]
fn playable_games_are_the_catalogue_in_lobby_order_with_wire_kinds() {
    let games = playable_games();

    assert_eq!(games.len(), mediagram_game::CATALOGUE.len());
    assert_eq!(games[0].kind, "quiz", "the lobby's first game, as the wire spells it");
    assert!(games.iter().any(|g| g.kind == "tien_len"), "snake_case, as `?game=` carries it");
    assert!(games.iter().all(|g| !g.name.is_empty()));
}
