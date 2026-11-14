//! Grenzfall: a country's outline on the TV, four names on the phones. It is
//! the buzzer quiz, asked with questions made from the bundled outlines:
//! well-known shapes first, small or look-alike ones last.
//!
//! The outlines in `questions/laender.json` come from Natural Earth (public
//! domain) through `scripts/build_country_shapes.py`.

use std::collections::HashSet;
use std::sync::LazyLock;

use rand::Rng;
use rand::seq::SliceRandom;
use serde::Deserialize;

use crate::quiz_config::{Question, QuizConfig};

/// One country as written in `questions/laender.json`.
#[derive(Debug, Deserialize)]
pub(crate) struct Country {
    pub code: String,
    pub name: String,
    pub continent: String,
    pub subregion: String,
    /// 1 known at a glance, 2 known to a geography-minded player, 3 the rest.
    pub tier: u8,
    pub w: u16,
    pub h: u16,
    /// The outline as an SVG path in a `w` by `h` box.
    pub d: String,
}

pub(crate) static COUNTRIES: LazyLock<Vec<Country>> =
    LazyLock::new(|| serde_json::from_str(include_str!("../questions/laender.json")).expect("bundled outlines parse"));

/// The tier of each round, in the order they are asked.
const TIERS: [u8; 10] = [1, 1, 1, 2, 2, 2, 2, 3, 3, 3];

pub fn grenzfall_config() -> QuizConfig {
    QuizConfig { rounds: TIERS.len(), ..QuizConfig::default() }
}

/// A game's questions, easiest first. Countries shown tonight (`used`) come
/// back only once their tier has none left; then the tier starts over, the
/// ones not yet shown first.
pub fn grenzfall_questions(rng: &mut impl Rng, used: &mut HashSet<String>) -> Vec<Question> {
    let mut asked: Vec<&Country> = Vec::new();
    for tier in 1..=3 {
        let wanted = TIERS.iter().filter(|&&t| t == tier).count();
        let in_tier = || COUNTRIES.iter().filter(move |c| c.tier == tier);
        let mut fresh: Vec<&Country> = in_tier().filter(|c| !used.contains(&shown_key(c))).collect();
        fresh.shuffle(rng);
        if fresh.len() < wanted {
            for c in in_tier() {
                used.remove(&shown_key(c));
            }
            let mut rest: Vec<&Country> = in_tier().filter(|c| !fresh.iter().any(|f| f.code == c.code)).collect();
            rest.shuffle(rng);
            fresh.extend(rest);
        }
        asked.extend(fresh.into_iter().take(wanted));
    }
    asked
        .into_iter()
        .map(|c| {
            used.insert(shown_key(c));
            question(c, rng)
        })
        .collect()
}

fn shown_key(c: &Country) -> String {
    format!("land:{}", c.code)
}

fn question(c: &Country, rng: &mut impl Rng) -> Question {
    let wrong = look_alikes(c, rng).map(|w| w.name.clone());
    Question {
        category: "Länder".into(),
        text: "Welches Land ist das?".into(),
        correct: c.name.clone(),
        wrong,
        image: Some(outline_url(c)),
    }
}

/// Three other countries to choose from, the likeliest mix-ups by `closeness`.
fn look_alikes(c: &Country, rng: &mut impl Rng) -> [&'static Country; 3] {
    let mut others: Vec<&'static Country> = COUNTRIES.iter().filter(|o| o.code != c.code).collect();
    others.shuffle(rng);
    // Stable, so equally close countries stay in shuffled order.
    others.sort_by_key(|o| closeness(c, o));
    [others[0], others[1], others[2]]
}

/// Lower is closer: other famous shapes in an easy round (India beside
/// Lithuania is no question), the same continent in a middle one, the
/// neighbours first in a hard one.
fn closeness(c: &Country, o: &Country) -> u8 {
    match c.tier {
        1 => u8::from(o.tier > 1),
        2 => u8::from(o.continent != c.continent),
        _ => u8::from(o.continent != c.continent) + u8::from(o.subregion != c.subregion),
    }
}

/// The outline as an SVG data URL, felt green, for the quiz card's picture.
/// Only `<`, `>` and `#` need escaping; the markup uses single quotes.
pub(crate) fn outline_url(c: &Country) -> String {
    format!(
        "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 {} {}'%3E\
         %3Cpath fill='%230f5a43' d='{}'/%3E%3C/svg%3E",
        c.w, c.h, c.d
    )
}

#[cfg(test)]
#[path = "grenzfall_tests.rs"]
mod tests;
