//! The fun fact the Kopfnuss winner takes home, from `questions/fakten.json`.

use rand::rngs::StdRng;
use rand::seq::IndexedRandom;
use serde::Deserialize;

/// One fact. The file also files each under a category, for whoever reviews it.
#[derive(Debug, Clone, Deserialize)]
pub struct Fact {
    pub text: String,
}

pub fn fact_pack() -> Vec<Fact> {
    serde_json::from_str(include_str!("../questions/fakten.json")).expect("bundled facts parse")
}

pub(crate) fn pick_fact(rng: &mut StdRng) -> String {
    fact_pack().choose(rng).map(|f| f.text.clone()).unwrap_or_default()
}
