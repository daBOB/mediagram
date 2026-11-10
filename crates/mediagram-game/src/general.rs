//! The bundled general-knowledge pack.

use serde::Deserialize;

use crate::quiz_config::Question;

/// The categories every pack entry belongs to.
pub const CATEGORIES: [&str; 13] = [
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
    "Sternenstaub",
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

/// The 18+ category, kept in its own file: asked only when the lobby's
/// *Matratzensport (ab 18)* switch is on, which kids mode keeps off.
pub const ADULT_CATEGORY: &str = "Matratzensport";

pub fn general_pack_entries() -> Vec<GeneralEntry> {
    serde_json::from_str(include_str!("../questions/allgemein.json")).expect("bundled pack parses")
}

pub fn adult_pack_entries() -> Vec<GeneralEntry> {
    serde_json::from_str(include_str!("../questions/matratzensport.json")).expect("bundled adult pack parses")
}

fn questions(entries: Vec<GeneralEntry>) -> Vec<Question> {
    entries
        .into_iter()
        .map(|e| Question { category: e.category, text: e.text, correct: e.correct, wrong: e.wrong, image: None })
        .collect()
}

pub fn general_pack() -> Vec<Question> {
    questions(general_pack_entries())
}

pub fn adult_pack() -> Vec<Question> {
    questions(adult_pack_entries())
}

#[cfg(test)]
#[path = "general_tests.rs"]
mod tests;
