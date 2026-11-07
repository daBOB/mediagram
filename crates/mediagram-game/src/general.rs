//! The bundled general-knowledge pack.

use serde::Deserialize;

use crate::quiz_config::Question;

/// The categories every pack entry belongs to.
pub const CATEGORIES: [&str; 12] = [
    "Geografie",
    "Natur & Tiere",
    "Geschichte",
    "Wissenschaft & Technik",
    "Sport",
    "Musik",
    "Kunst & Literatur",
    "Essen & Trinken",
    "Sprache",
    "Film & Fernsehen",
    "Kinderwissen",
    "Alltag",
];

/// One pack entry as written in `questions/allgemein.json`.
#[derive(Debug, Clone, Deserialize)]
pub struct GeneralEntry {
    pub category: String,
    pub text: String,
    pub correct: String,
    pub wrong: [String; 3],
    /// Where the fact was checked; kept in the file only.
    pub source: String,
}

pub fn general_pack_entries() -> Vec<GeneralEntry> {
    serde_json::from_str(include_str!("../questions/allgemein.json")).expect("bundled pack parses")
}

pub fn general_pack() -> Vec<Question> {
    general_pack_entries()
        .into_iter()
        .map(|e| Question { category: e.category, text: e.text, correct: e.correct, wrong: e.wrong, image: None })
        .collect()
}

#[cfg(test)]
#[path = "general_tests.rs"]
mod tests;
