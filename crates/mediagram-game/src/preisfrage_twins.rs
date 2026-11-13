//! Near-twin articles: one product from another seller, in another size or
//! colour. Gift searches bring several of them (six sellers' hot pots, a whiskey
//! gift box for every birthday), and a game that prices two of them is dull.
//! The draw keeps them apart, so the list itself loses nothing.

use std::collections::{HashMap, HashSet};

use crate::articles::Article;

/// Words are compared by their first six letters, so endings and compound tails
/// match: Kupfer and Kupferkohle, Geschenk and Geschenke, Peking and Pekinger.
const STEM: usize = 6;
/// A word more titles of the list use than this (für, Gadget, Geschenk, Camping)
/// came from the searches, not the product, and makes no two articles twins.
const COMMON: usize = 12;

/// The telling words of every article's title, by ASIN.
pub(crate) struct TitleWords<'a>(HashMap<&'a str, HashSet<String>>);

impl<'a> TitleWords<'a> {
    pub(crate) fn of(articles: &'a [Article]) -> Self {
        let mut words: HashMap<&str, HashSet<String>> =
            articles.iter().map(|a| (a.asin.as_str(), title_words(&a.title))).collect();
        let mut uses: HashMap<String, usize> = HashMap::new();
        for word in words.values().flatten() {
            *uses.entry(word.clone()).or_default() += 1;
        }
        for set in words.values_mut() {
            set.retain(|w| uses[w] <= COMMON);
        }
        TitleWords(words)
    }

    /// Twins share at least two telling words, and at least a third of the
    /// shorter title's: one shared word, like Topf or Kupfer, is not enough.
    pub(crate) fn twins(&self, a: &Article, b: &Article) -> bool {
        let (Some(a), Some(b)) = (self.0.get(a.asin.as_str()), self.0.get(b.asin.as_str())) else {
            return false;
        };
        let shared = a.intersection(b).count();
        shared >= 2 && 3 * shared >= a.len().min(b.len())
    }
}

/// A title's words of three letters or more, lower case and cut to [`STEM`] letters.
fn title_words(title: &str) -> HashSet<String> {
    title
        .split(|c: char| !c.is_alphabetic())
        .filter(|w| w.chars().count() >= 3)
        .map(|w| w.to_lowercase().chars().take(STEM).collect())
        .collect()
}

#[cfg(test)]
#[path = "preisfrage_twins_tests.rs"]
mod tests;
