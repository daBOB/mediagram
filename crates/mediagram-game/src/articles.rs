//! Preisfrage's articles: the list `tools/preisfrage-articles` keeps on the
//! home cache server, read once as a night starts. Only articles a round can
//! use are kept, so a hand-edited or half-understood list never reaches one.

use serde::Deserialize;

/// The most an article may cost, in cents (100 000 €): the keypad stops there too.
pub const MAX_CENTS: u32 = 10_000_000;

/// A night's articles and when their prices were read.
#[derive(Debug, Clone, Default, PartialEq, Deserialize)]
pub struct ArticleList {
    /// When the fetch tool read the prices, as it wrote it (RFC 3339).
    pub fetched: String,
    pub articles: Vec<Article>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct Article {
    /// Amazon's product id, which also names the photo.
    pub asin: String,
    /// The short name the TV and the phones show.
    pub name: String,
    pub title: String,
    pub cents: u32,
    /// 0 for none: few reviews stands for an article nobody knows.
    pub reviews: u32,
}

impl ArticleList {
    /// The list in `json`, keeping the articles with a product's ASIN, a name
    /// and a price above 0 and at most [`MAX_CENTS`].
    pub fn from_json(json: &str) -> serde_json::Result<ArticleList> {
        let mut list: ArticleList = serde_json::from_str(json)?;
        list.articles.retain(|a| {
            valid_asin(&a.asin) && !a.name.trim().is_empty() && (1..=MAX_CENTS).contains(&a.cents)
        });
        Ok(list)
    }

    pub fn holds(&self, asin: &str) -> bool {
        self.articles.iter().any(|a| a.asin == asin)
    }
}

/// `B` and nine of `[0-9A-Z]`: every product but a book, whose ASIN is its ISBN.
fn valid_asin(asin: &str) -> bool {
    asin.len() == 10
        && asin.starts_with('B')
        && asin
            .bytes()
            .all(|b| b.is_ascii_digit() || b.is_ascii_uppercase())
}

/// Where the TV and the phones load an article's photo.
pub fn article_photo_url(asin: &str) -> String {
    format!("/img/article/{asin}")
}

#[cfg(test)]
#[path = "articles_tests.rs"]
mod tests;
