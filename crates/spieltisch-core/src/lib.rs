//! Spieltisch's native core: the game night host behind a uniffi surface, and
//! the game catalogue the app builds its shelf from, so a game added to the
//! host shows up on the shelf with no Kotlin change.

uniffi::setup_scaffolding!();

mod night;

pub use night::{NightError, NightUrls, SpieltischConfig, night_back, start_night, stop_night};

/// One game on the shelf, as the host's lobby lists it.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct GameEntry {
    /// The host's name for the game (`GameKind` on the wire), which the TV page takes as `?game=`.
    pub kind: String,
    /// The name players read.
    pub name: String,
    /// The fewest players the game starts with.
    pub min_players: u8,
    /// The most players the game starts with.
    pub max_players: u8,
}

/// Every game the host can start, in the order its lobby lists them.
#[uniffi::export]
pub fn game_catalogue() -> Vec<GameEntry> {
    mediagram_game::CATALOGUE
        .iter()
        .map(|game| GameEntry {
            kind: wire_name(game.kind),
            name: game.name.to_string(),
            min_players: game.min,
            max_players: game.max,
        })
        .collect()
}

/// The kind as serde writes it for the pages, so the shelf and the TV page name a game the same way.
fn wire_name(kind: mediagram_game::protocol::GameKind) -> String {
    serde_json::to_value(kind)
        .ok()
        .and_then(|value| value.as_str().map(String::from))
        .expect("a GameKind serialises to its bare name")
}

#[cfg(test)]
#[path = "lib_tests.rs"]
mod tests;
