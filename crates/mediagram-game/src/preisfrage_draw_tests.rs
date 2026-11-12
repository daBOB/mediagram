use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;

use super::*;
use crate::articles::{Article, ArticleList};

fn article(n: u32, cents: u32, reviews: u32) -> Article {
    Article { asin: format!("B{n:09}"), name: format!("Ding {n}"), title: format!("Ding {n}"), cents, reviews }
}

/// `per_band` articles in each band, numbered from the band's own hundred.
fn list(per_band: u32) -> ArticleList {
    let prices = [999, 2999, 8999, 29999];
    let articles = (0..4u32).flat_map(|b| (0..per_band).map(move |i| article(100 * b + i, prices[b as usize], 0))).collect();
    ArticleList { fetched: "2026-11-12T19:14:54.484Z".into(), articles }
}

fn bands(drawn: &[Article]) -> Vec<usize> {
    let mut b: Vec<usize> = drawn.iter().map(|a| band_of(a.cents)).collect();
    b.sort();
    b
}

#[test]
fn six_articles_cover_every_band_and_none_twice() {
    for seed in 0..20 {
        let drawn = draw(&list(10), 6, &mut HashSet::new(), &mut StdRng::seed_from_u64(seed));
        assert_eq!(drawn.len(), 6);
        let b = bands(&drawn);
        assert!((0..4).all(|band| b.contains(&band)), "seed {seed}: {b:?}");
        assert_eq!(drawn.iter().map(|a| &a.asin).collect::<HashSet<_>>().len(), 6);
    }
}

#[test]
fn an_article_nobody_knows_comes_before_a_known_one_in_its_band() {
    let mut l = list(0);
    l.articles = vec![article(1, 999, 4000), article(2, 1099, 3), article(3, 2999, 0), article(4, 8999, 0), article(5, 29999, 0)];
    for seed in 0..10 {
        let drawn = draw(&l, 4, &mut HashSet::new(), &mut StdRng::seed_from_u64(seed));
        let cheap: Vec<_> = drawn.iter().filter(|a| band_of(a.cents) == 0).map(|a| a.asin.as_str()).collect();
        assert_eq!(cheap, ["B000000002"], "seed {seed}");
    }
}

#[test]
fn a_night_repeats_no_article_until_the_list_runs_out() {
    let l = list(3); // 12 articles: two games of six
    let mut used = HashSet::new();
    let mut rng = StdRng::seed_from_u64(7);
    let first: HashSet<String> = draw(&l, 6, &mut used, &mut rng).into_iter().map(|a| a.asin).collect();
    let second: HashSet<String> = draw(&l, 6, &mut used, &mut rng).into_iter().map(|a| a.asin).collect();
    assert!(first.is_disjoint(&second));
    assert_eq!(draw(&l, 6, &mut used, &mut rng).len(), 6, "once every article was shown the list starts over");
}

#[test]
fn an_empty_band_is_filled_from_the_nearest_one() {
    let mut l = list(4);
    l.articles.retain(|a| band_of(a.cents) != 3);
    let drawn = draw(&l, 6, &mut HashSet::new(), &mut StdRng::seed_from_u64(1));
    assert_eq!(drawn.len(), 6);
    assert!(bands(&drawn).iter().filter(|&&b| b == 2).count() >= 2, "the top band's pick came from the one below");
}

#[test]
fn a_short_list_gives_what_it_has() {
    let mut l = list(1);
    l.articles.truncate(3);
    assert_eq!(draw(&l, 6, &mut HashSet::new(), &mut StdRng::seed_from_u64(3)).len(), 3);
    assert!(enough_articles(&l));
    l.articles.truncate(2);
    assert!(!enough_articles(&l));
}

#[test]
fn prices_outside_the_bands_count_to_the_nearest() {
    assert_eq!([band_of(1), band_of(1999), band_of(2000), band_of(14999), band_of(15000), band_of(9_999_999)], [0, 0, 1, 2, 3, 3]);
}
