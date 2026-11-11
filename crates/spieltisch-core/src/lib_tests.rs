use mediagram_game::CATALOGUE;

use super::*;

#[test]
fn the_catalogue_lists_every_game_in_lobby_order() {
    let games = game_catalogue();
    assert_eq!(games.len(), CATALOGUE.len());
    for (game, entry) in games.iter().zip(CATALOGUE) {
        assert_eq!(game.name, entry.name);
        assert_eq!((game.min_players, game.max_players), (entry.min, entry.max));
        assert_eq!(serde_json::to_value(entry.kind).unwrap(), game.kind.as_str());
    }
}

#[test]
fn kinds_are_the_names_the_tv_page_takes() {
    let kinds: Vec<String> = game_catalogue().into_iter().map(|game| game.kind).collect();
    assert_eq!(kinds[0], "quiz");
    assert!(kinds.contains(&"mau_mau".to_string()));
    assert!(kinds.contains(&"grand_allee".to_string()));
}
