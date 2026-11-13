//! Which articles a game prices: one from each price band and the rest from
//! bands at random, articles nobody knows first, none twice in a night and no
//! two near-twins in one game.

use std::collections::HashSet;

use rand::Rng;
use rand::seq::{IndexedRandom, SliceRandom};

use crate::articles::{Article, ArticleList};
use crate::preisfrage_config::{FEW_REVIEWS, MIN_ARTICLES};
use crate::preisfrage_twins::TitleWords;

/// Where the bands end, in cents: 20, 50 and 150 €; the last band takes the rest.
const BAND_TOPS: [u32; 3] = [2_000, 5_000, 15_000];
const BANDS: usize = BAND_TOPS.len() + 1;

/// 0 for up to 20 €, 1 to 50 €, 2 to 150 €, 3 above: the fetch tool's bands, so prices a
/// search band let slip count to the nearest.
pub(crate) fn band_of(cents: u32) -> usize {
    BAND_TOPS.iter().take_while(|&&top| cents >= top).count()
}

pub(crate) fn enough_articles(list: &ArticleList) -> bool {
    list.articles.len() >= MIN_ARTICLES
}

/// Up to `count` articles, in the order they are played. `used` holds the night's shown ASINs
/// and is forgotten once too few fresh ones are left.
pub(crate) fn draw(list: &ArticleList, count: usize, used: &mut HashSet<String>, rng: &mut impl Rng) -> Vec<Article> {
    let fresh = list.articles.iter().filter(|a| !used.contains(&a.asin)).count();
    if fresh < count.min(list.articles.len()) {
        used.clear();
    }
    let mut wants: Vec<usize> = (0..BANDS).chain((BANDS..count).map(|_| rng.random_range(0..BANDS))).collect();
    wants.shuffle(rng);
    wants.truncate(count);
    let words = TitleWords::of(&list.articles);
    let mut drawn: Vec<Article> = Vec::new();
    for band in wants {
        let left: Vec<&Article> = list
            .articles
            .iter()
            .filter(|a| !used.contains(&a.asin) && !drawn.iter().any(|d| words.twins(a, d)))
            .collect();
        // The wanted band, else the nearest one that has any left.
        let Some(near) = (0..BANDS).map(|d| nearest(&left, band, d)).find(|c| !c.is_empty()) else { break };
        let unknown: Vec<&Article> = near.iter().copied().filter(|a| a.reviews < FEW_REVIEWS).collect();
        let pick = if unknown.is_empty() { &near } else { &unknown }.choose(rng).copied();
        if let Some(article) = pick {
            used.insert(article.asin.clone());
            drawn.push(article.clone());
        }
    }
    drawn
}

/// The articles of `left` exactly `d` bands from `band`, either side.
fn nearest<'a>(left: &[&'a Article], band: usize, d: usize) -> Vec<&'a Article> {
    left.iter().copied().filter(|a| band_of(a.cents).abs_diff(band) == d).collect()
}

#[cfg(test)]
#[path = "preisfrage_draw_tests.rs"]
mod tests;
