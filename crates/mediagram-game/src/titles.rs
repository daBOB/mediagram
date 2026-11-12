//! The films and series the games draw on, and which of them a night may use.

use std::path::{Path, PathBuf};

use serde::Deserialize;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TitleKind {
    Film,
    Series,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ImageKind {
    Poster,
    Still,
    /// A Preisfrage article's photo, asked for by ASIN rather than by title.
    Article,
}

/// A film, or one show of a series. Image files are local paths on the box.
#[derive(Debug, Clone, Deserialize)]
pub struct GameTitle {
    pub key: String,
    pub kind: TitleKind,
    pub title: String,
    pub year: Option<u16>,
    pub poster: Option<PathBuf>,
    pub still: Option<PathBuf>,
    pub directors: Vec<String>,
    /// In billing order.
    pub cast: Vec<String>,
    pub kids_ok: bool,
}

#[derive(Debug, Default)]
pub struct TitlePool {
    titles: Vec<GameTitle>,
}

impl TitlePool {
    /// A locked night is a kids profile's: nothing unsafe may ever be drawn,
    /// not even as a wrong option, so unsafe titles are dropped up front.
    pub fn new(mut titles: Vec<GameTitle>, kids_locked: bool) -> TitlePool {
        if kids_locked {
            titles.retain(|t| t.kids_ok);
        }
        TitlePool { titles }
    }

    pub fn eligible(&self, kids_only: bool) -> Vec<usize> {
        (0..self.titles.len()).filter(|&i| !kids_only || self.titles[i].kids_ok).collect()
    }

    pub(crate) fn key_of(&self, i: usize) -> Option<&str> {
        self.titles.get(i).map(|t| t.key.as_str())
    }

    pub fn get(&self, i: usize) -> &GameTitle {
        &self.titles[i]
    }

    /// `None` for an unknown index or a title without that image.
    pub fn image_path(&self, i: usize, which: ImageKind) -> Option<&Path> {
        let t = self.titles.get(i)?;
        match which {
            ImageKind::Poster => t.poster.as_deref(),
            ImageKind::Still => t.still.as_deref(),
            ImageKind::Article => None,
        }
    }
}

pub fn image_url(i: usize, which: ImageKind) -> String {
    let which = match which {
        ImageKind::Poster => "poster",
        ImageKind::Still => "still",
        // No title has one: the route answers 404. Articles have `article_photo_url`.
        ImageKind::Article => "article",
    };
    format!("/img/{i}/{which}")
}

#[cfg(test)]
#[path = "titles_tests.rs"]
pub(crate) mod tests;
