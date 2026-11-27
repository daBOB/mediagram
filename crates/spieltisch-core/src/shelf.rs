//! The games the lobby's shelf offers, for the TV home screen's Spieltisch row.

use mediagram_game::CATALOGUE;

/// One game a room can start: [kind] as the lobby's `?game=` and the wire spell it, [name] as players read it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ShelfGame {
    pub kind: String,
    pub name: String,
}

/// Every game a room can start, in the order the lobby lists them.
#[uniffi::export]
pub fn playable_games() -> Vec<ShelfGame> {
    CATALOGUE
        .iter()
        .map(|entry| ShelfGame {
            kind: serde_json::to_value(entry.kind).ok().and_then(|v| v.as_str().map(str::to_owned)).unwrap_or_default(),
            name: entry.name.to_string(),
        })
        .collect()
}

#[cfg(test)]
#[path = "shelf_tests.rs"]
mod tests;
