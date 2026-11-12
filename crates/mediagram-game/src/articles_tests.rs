use super::*;

/// A list as `tools/preisfrage-articles` writes it, one article of each kind the reader must judge.
const LIST: &str = r#"{
  "version": 1,
  "fetched": "2026-11-12T19:14:54.484Z",
  "articles": [
    {"asin": "B0GF25D3LF", "name": "2 Stück Phone Holder", "title": "2 Stück Phone Holder, Daumen Hoch", "cents": 899, "reviews": 21, "image": "https://m.media-amazon.com/x._AC_SL800_.jpg"},
    {"asin": "../../etc", "name": "Pfad", "title": "Pfad", "cents": 100, "reviews": 0},
    {"asin": "3831038147", "name": "Buch", "title": "Buch", "cents": 2495, "reviews": 3},
    {"asin": "B0FREE0000", "name": "Gratis", "title": "Gratis", "cents": 0, "reviews": 0},
    {"asin": "B0NONAME00", "name": " ", "title": "x", "cents": 500, "reviews": 0},
    {"asin": "B0HUGE0000", "name": "Yacht", "title": "Yacht", "cents": 10000001, "reviews": 0}
  ]
}"#;

#[test]
fn a_fetched_list_keeps_its_date_and_only_articles_a_round_can_use() {
    let list = ArticleList::from_json(LIST).unwrap();
    assert_eq!(list.fetched, "2026-11-12T19:14:54.484Z");
    assert_eq!(
        list.articles,
        vec![Article {
            asin: "B0GF25D3LF".into(),
            name: "2 Stück Phone Holder".into(),
            title: "2 Stück Phone Holder, Daumen Hoch".into(),
            cents: 899,
            reviews: 21,
        }]
    );
    assert!(list.holds("B0GF25D3LF"));
    assert!(!list.holds("3831038147"));
}

#[test]
fn json_that_is_not_a_list_is_an_error() {
    assert!(ArticleList::from_json("").is_err());
    assert!(ArticleList::from_json("[]").is_err());
    assert!(ArticleList::default().articles.is_empty());
}

#[test]
fn a_photo_is_asked_for_by_asin() {
    assert_eq!(article_photo_url("B0GF25D3LF"), "/img/article/B0GF25D3LF");
}
