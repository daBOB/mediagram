//! Grenzfall: a country's outline on the TV, four names on the phones. It is
//! the buzzer quiz, asked with questions made from the bundled outlines:
//! well-known shapes first, small or look-alike ones last.
//!
//! The outlines in `questions/laender.json` come from Natural Earth (public
//! domain) through `scripts/build_country_shapes.py`.

use std::collections::HashSet;
use std::sync::LazyLock;

use rand::rngs::StdRng;
use rand::seq::SliceRandom;
use rand::{Rng, SeedableRng};
use serde::Deserialize;

use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::factory::Night;
use crate::game::{Game, GameCtx};
use crate::host::ROOM_CAP;
use crate::protocol::LobbySettings;
use crate::quiz::Quiz;
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

/// Rounds at normal length: three well-known shapes, four middle ones, three hard ones.
const ROUNDS: usize = 10;

pub fn grenzfall_config() -> QuizConfig {
    QuizConfig { rounds: ROUNDS, ..QuizConfig::default() }
}

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    name: "Grenzfall", seats: Seats::Range(1, ROOM_CAP), timed: true, sized: true,
    kids: Kids::Plays, board: false, needs: Need::Nothing, start,
};

fn start(night: &mut Night, ctx: &mut GameCtx, settings: &LobbySettings) -> Box<dyn Game> {
    let cfg = grenzfall_config().at(settings.speed).sized(settings.length);
    let questions = grenzfall_questions(cfg.rounds, &mut StdRng::from_os_rng(), &mut night.history());
    Box::new(Quiz::new(cfg, questions, ctx.now))
}

/// The tier of each of `rounds` rounds, easiest first: three tenths well-known,
/// three tenths hard, the middle ones between.
pub(crate) fn tiers(rounds: usize) -> Vec<u8> {
    let outer = (rounds * 3 + 5) / 10;
    let middle = rounds.saturating_sub(2 * outer);
    [(1, outer), (2, middle), (3, outer)].into_iter().flat_map(|(tier, n)| std::iter::repeat_n(tier, n)).collect()
}

/// A game's `rounds` questions, easiest first. Countries shown tonight (`used`) come
/// back only once their tier has none left; then the tier starts over, the
/// ones not yet shown first.
pub fn grenzfall_questions(rounds: usize, rng: &mut impl Rng, used: &mut HashSet<String>) -> Vec<Question> {
    let tiers = tiers(rounds);
    let mut asked: Vec<&Country> = Vec::new();
    for tier in 1..=3 {
        let wanted = tiers.iter().filter(|&&t| t == tier).count();
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
