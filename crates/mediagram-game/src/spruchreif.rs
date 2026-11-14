//! Spruchreif: the start of a proverb on the TV, four endings on the phones,
//! German and English proverbs by turns. It is the buzzer quiz, asked with the
//! proverbs in `questions/sprichwoerter.json`: well-known ones first, rare ones last.

use std::collections::HashSet;
use std::sync::LazyLock;

use rand::Rng;
use rand::seq::IndexedRandom;
use serde::Deserialize;

use crate::grenzfall::tiers;
use crate::quiz_config::{Question, QuizConfig};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub(crate) enum Lang {
    De,
    En,
}

/// One proverb as written in `questions/sprichwoerter.json`.
#[derive(Debug, Deserialize)]
pub(crate) struct Proverb {
    pub lang: Lang,
    /// 1 known to everyone, 2 known to most grown-ups, 3 the rare or old ones.
    pub tier: u8,
    /// What the TV shows, followed by "…".
    pub start: String,
    pub end: String,
    /// Three endings that sound right and are not.
    pub wrong: [String; 3],
}

pub(crate) static PROVERBS: LazyLock<Vec<Proverb>> =
    LazyLock::new(|| serde_json::from_str(include_str!("../questions/sprichwoerter.json")).expect("bundled proverbs parse"));

/// Rounds at normal length, five German and five English.
pub(crate) const ROUNDS: usize = 10;

pub fn spruchreif_config() -> QuizConfig {
    QuizConfig { rounds: ROUNDS, ..QuizConfig::default() }
}

/// A game's `rounds` proverbs, easiest first, the language changing every round.
/// Proverbs asked tonight (`used`) come back only once their language and tier have
/// none left; then those start over, this game's own picks excepted.
pub fn spruchreif_questions(rounds: usize, rng: &mut impl Rng, used: &mut HashSet<String>) -> Vec<Question> {
    let first = usize::from(rng.random_bool(0.5));
    let mut asked: Vec<&'static Proverb> = Vec::new();
    for (i, tier) in tiers(rounds).into_iter().enumerate() {
        let lang = [Lang::De, Lang::En][(i + first) % 2];
        let group = || PROVERBS.iter().filter(move |p| p.lang == lang && p.tier == tier);
        if group().all(|p| used.contains(&shown_key(p))) {
            for p in group().filter(|p| !asked.iter().any(|a| std::ptr::eq(*a, *p))) {
                used.remove(&shown_key(p));
            }
        }
        let fresh: Vec<&'static Proverb> = group().filter(|p| !used.contains(&shown_key(p))).collect();
        let p = *fresh.choose(rng).expect("every language and tier holds more proverbs than a game asks");
        used.insert(shown_key(p));
        asked.push(p);
    }
    asked.into_iter().map(question).collect()
}

fn shown_key(p: &Proverb) -> String {
    format!("spruch:{}", p.start)
}

fn question(p: &Proverb) -> Question {
    let category = match p.lang {
        Lang::De => "🇩🇪 Sprichwort",
        Lang::En => "🇬🇧 Proverb",
    };
    Question {
        category: category.into(),
        text: format!("{} …", p.start),
        correct: p.end.clone(),
        wrong: p.wrong.clone(),
        image: None,
    }
}

#[cfg(test)]
#[path = "spruchreif_tests.rs"]
mod tests;
